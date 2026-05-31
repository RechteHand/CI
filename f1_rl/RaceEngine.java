package f1_rl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages one race: sensor computation, physics stepping, car-car collisions,
 * drafting, overtake detection, and race position ranking.
 *
 * <p>Uses a thread-safe {@link CopyOnWriteArrayList} so the rendering thread
 * can iterate over cars without a {@code ConcurrentModificationException}.</p>
 */
public class RaceEngine {

    private final Track track;
    private final List<Car> cars = new CopyOnWriteArrayList<>();

    private int  raceTick       = 0;
    private boolean raceOver    = false;

    // Sorted race positions (updated each tick)
    private final List<Car> positions = new ArrayList<>();

    // Race-wide statistics
    private int totalCrashes   = 0;
    private int totalOvertakes = 0;

    // ── Construction ──────────────────────────────────────────────────────────

    public RaceEngine(Track track) {
        this.track = track;
    }

    // ── Race lifecycle ────────────────────────────────────────────────────────

    /** Populates the grid with fresh cars built from the supplied brains. */
    public void startRace(List<NeuralNetwork> brains) {
        cars.clear();
        positions.clear();
        raceTick     = 0;
        raceOver     = false;
        totalCrashes  = 0;
        totalOvertakes = 0;

        for (int i = 0; i < brains.size(); i++) {
            float[] slot = track.getGridSlot(i);
            cars.add(new Car(i, slot[0], slot[1], slot[2], brains.get(i)));
        }
        positions.addAll(cars);
    }

    /**
     * Advances the simulation by one tick:
     * <ol>
     *   <li>Compute opponent sensors for every car</li>
     *   <li>Run each car's neural network (think)</li>
     *   <li>Apply physics and checkpoint logic (move)</li>
     *   <li>Resolve car-car collisions</li>
     *   <li>Detect drafting</li>
     *   <li>Detect overtakes</li>
     *   <li>Update race rankings</li>
     * </ol>
     */
    public void tick() {
        if (raceOver) return;

        raceTick++;

        // 1+2. Compute sensors then think
        for (Car car : cars) {
            double[] inputs = buildInputs(car);
            car.think(inputs);
        }

        // 3. Move
        for (Car car : cars) {
            car.move(track);
        }

        // 4. Car-car collisions
        resolveCollisions();

        // 5. Drafting
        updateDrafting();

        // 6. Overtake detection
        detectOvertakes();

        // 7. Update rankings
        updatePositions();

        // 8. Check race over
        if (allFinishedOrTimeout()) {
            raceOver = true;
        }
    }

    // ── Sensor computation ────────────────────────────────────────────────────

    /**
     * Builds the 16-element input vector:
     * 0-6:  wall raycasts (normalised distance)
     * 7:    distance to car directly ahead
     * 8:    distance to car directly behind
     * 9:    distance to nearest car left
     * 10:   distance to nearest car right
     * 11:   speed delta to car ahead (tanh)
     * 12:   in slipstream draft? (0 or 1)
     * 13:   own normalised speed
     * 14:   signed lateral angle to next checkpoint (tanh) — teaches braking zones
     * 15:   normalised distance to next checkpoint — teaches when to brake
     */
    private double[] buildInputs(Car car) {
        double[] inputs = new double[Config.NN_INPUTS];

        // Wall rays
        float cos = (float) Math.cos(car.getHeading());
        float sin = (float) Math.sin(car.getHeading());
        for (int i = 0; i < Config.NUM_WALL_RAYS; i++) {
            float ang = car.getHeading() + (float) Math.toRadians(Config.RAY_ANGLES[i]);
            float rdx = (float) Math.cos(ang), rdy = (float) Math.sin(ang);
            float dist = track.castRay(car.getX(), car.getY(), rdx, rdy);
            inputs[i] = dist;
            car.setRayHit(i,
                    car.getX() + rdx * dist * Config.RAY_MAX_LEN,
                    car.getY() + rdy * dist * Config.RAY_MAX_LEN);
        }

        // Opponent sensors
        float nearAhead = Float.MAX_VALUE, nearBehind = Float.MAX_VALUE;
        float nearLeft  = Float.MAX_VALUE, nearRight  = Float.MAX_VALUE;
        float speedDeltaAhead = 0;

        for (Car other : cars) {
            if (other == car) continue;
            float dx = other.getX() - car.getX();
            float dy = other.getY() - car.getY();
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            if (dist > 250f) continue;

            float forward = dx * cos + dy * sin;
            float lateral = -dx * sin + dy * cos;

            if (forward > 0 && dist < nearAhead) {
                nearAhead = dist;
                speedDeltaAhead = car.getSpeed() - other.getSpeed();
            }
            if (forward < 0 && dist < nearBehind) nearBehind = dist;
            if (lateral > 0 && dist < nearRight) nearRight = dist;
            if (lateral < 0 && dist < nearLeft)  nearLeft  = dist;
        }

        float maxOpp = 250f;
        inputs[7]  = nearAhead  < maxOpp ? 1.0 - nearAhead  / maxOpp : 0;
        inputs[8]  = nearBehind < maxOpp ? 1.0 - nearBehind / maxOpp : 0;
        inputs[9]  = nearLeft   < maxOpp ? 1.0 - nearLeft   / maxOpp : 0;
        inputs[10] = nearRight  < maxOpp ? 1.0 - nearRight  / maxOpp : 0;
        inputs[11] = Math.tanh(speedDeltaAhead);
        inputs[12] = car.getDraftIntensity();  // 0..1 continuous (better NN signal)
        inputs[13] = car.getSpeed() / Config.CAR_MAX_SPEED;

        // Next-checkpoint spatial sensors (the key inputs for learning braking zones)
        float[] cp  = track.getCheckpoint(car.getNextCheckpoint());
        float   cpDx = cp[0] - car.getX();
        float   cpDy = cp[1] - car.getY();
        float   cpDist   = (float) Math.sqrt(cpDx * cpDx + cpDy * cpDy);
        // Project checkpoint direction onto car's local frame
        float   cpLateral = -cpDx * sin + cpDy * cos;   // negative=left, positive=right
        float   cpForward =  cpDx * cos + cpDy * sin;
        float   relAngle  = (float) Math.atan2(cpLateral, Math.max(cpForward, 1f));
        inputs[14] = Math.tanh(relAngle * 2.5);          // -1..1: which way to steer
        inputs[15] = Math.min(cpDist / (Config.RAY_MAX_LEN * 1.5f), 1.0); // 0..1

        return inputs;
    }

    // ── Collision resolution ──────────────────────────────────────────────────

    private void resolveCollisions() {
        Car[] arr = cars.toArray(new Car[0]);
        for (int i = 0; i < arr.length; i++) {
            for (int j = i + 1; j < arr.length; j++) {
                Car a = arr[i], b = arr[j];
                float dx = b.getX() - a.getX();
                float dy = b.getY() - a.getY();
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                float minDist = Config.CAR_COLLISION_RADIUS * 2;

                if (dist < minDist && dist > 0.01f) {
                    // Separation vector
                    float overlap = (minDist - dist) / 2f;
                    float nx = dx / dist, ny = dy / dist;

                    if (a.getCollisionCooldown() == 0 || b.getCollisionCooldown() == 0) {
                        a.applyCollision(-nx * overlap, -ny * overlap);
                        b.applyCollision( nx * overlap,  ny * overlap);
                        totalCrashes++;
                    }
                }
            }
        }
    }

    // ── Drafting detection ────────────────────────────────────────────────────

    private void updateDrafting() {
        for (Car car : cars) {
            float maxIntensity = 0f;
            float cos = (float) Math.cos(car.getHeading());
            float sin = (float) Math.sin(car.getHeading());

            for (Car other : cars) {
                if (other == car) continue;
                float dx = other.getX() - car.getX();
                float dy = other.getY() - car.getY();
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                if (dist > Config.DRAFT_DISTANCE) continue;

                float forward = dx * cos + dy * sin;
                if (forward < 0) continue; // car is behind

                float lateral = Math.abs(-dx * sin + dy * cos);
                float angle   = (float) Math.toDegrees(Math.atan2(lateral, forward));
                if (angle <= Config.DRAFT_HALF_ANGLE) {
                    // Intensity: 1.0 when touching, 0.0 at DRAFT_DISTANCE
                    float intensity = 1f - dist / Config.DRAFT_DISTANCE;
                    maxIntensity = Math.max(maxIntensity, intensity);
                }
            }
            car.setDraftIntensity(maxIntensity);
        }
    }

    // ── Overtake detection ────────────────────────────────────────────────────

    private final int[] previousRank = new int[20]; // max drivers

    private void detectOvertakes() {
        for (int i = 0; i < positions.size(); i++) {
            Car car = positions.get(i);
            int prev = previousRank[car.id];
            // Only count genuine overtakes once cars are racing (≥1 CP collected).
            // This prevents phantom overtakes from the unstable initial sort.
            if (prev > i && car.getTotalCheckpoints() > 0) {
                int overtakes = prev - i;
                for (int o = 0; o < overtakes; o++) car.recordOvertake();
                totalOvertakes += overtakes;
            }
            previousRank[car.id] = i;
        }
    }

    // ── Position ranking ──────────────────────────────────────────────────────

    private void updatePositions() {
        positions.sort(Comparator
                .comparingInt(Car::getTotalCheckpoints).reversed()
                .thenComparingDouble(c -> -c.getSpeed()));
    }

    // ── Race over condition ───────────────────────────────────────────────────

    /** Race is over when every car has either finished or is DNF, or timeout hit. */
    private boolean allFinishedOrTimeout() {
        if (raceTick >= Config.RACE_TIMEOUT_TICKS) return true;
        for (Car c : cars) if (c.isActive()) return false;
        return true;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public List<Car>  getCars()          { return cars; }
    public List<Car>  getPositions()     { return positions; }
    public Track      getTrack()         { return track; }
    public int        getRaceTick()      { return raceTick; }
    public boolean    isRaceOver()       { return raceOver; }
    public int        getTotalCrashes()  { return totalCrashes; }
    public int        getTotalOvertakes(){ return totalOvertakes; }

    /** Returns the race leader (P1). */
    public Car getLeader() {
        return positions.isEmpty() ? null : positions.get(0);
    }

    /**
     * Returns the finish position (0-based) of each car in {@code cars} order.
     * Used by {@link PopulationManager} to compute fitness.
     */
    public int[] computeFinishPositions() {
        int[] pos = new int[cars.size()];
        for (int i = 0; i < positions.size(); i++) {
            pos[positions.get(i).id] = i;
        }
        return pos;
    }
}
