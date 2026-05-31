package f1_rl;

/**
 * One AI-controlled racing car.
 *
 * <p>After STUCK_TICKS without a checkpoint the car is marked DNF — it stops
 * consuming simulation time and lets the race end faster so evolution cycles
 * are short.</p>
 */
public class Car {

    // ── Identity ──────────────────────────────────────────────────────────────
    public final int id;

    // ── Physics ───────────────────────────────────────────────────────────────
    private float x, y, heading, speed;
    private float gas, steer;
    private float draftIntensity = 0f; // 0=no draft, 1=full draft, fades with momentum

    // ── Race state ────────────────────────────────────────────────────────────
    private boolean finishedRace = false;  // completed NUM_LAPS
    private boolean dnf          = false;  // did-not-finish (stuck / timed out)

    private int  nextCheckpoint   = 0;
    private int  totalCheckpoints = 0;   // cumulative CPs ever collected
    private int  stuckTimer       = 0;
    private int  lapsCompleted    = 0;
    private long survivedTicks    = 0;
    private long lapStartTick     = 0;
    private long bestLapTicks     = Long.MAX_VALUE;
    private long totalLapTicks    = 0;
    private int  crashCount       = 0;
    private int  overtakeCount    = 0;
    private int  totalCPs         = -1;  // cached from track

    // ── Collision ─────────────────────────────────────────────────────────────
    private int collisionCooldown = 0;

    // ── Brain ─────────────────────────────────────────────────────────────────
    private final NeuralNetwork brain;

    // ── Sensor cache (for rendering the leader's rays) ────────────────────────
    private final float[] rayHitX = new float[Config.NUM_WALL_RAYS];
    private final float[] rayHitY = new float[Config.NUM_WALL_RAYS];

    // ── Constructor ───────────────────────────────────────────────────────────

    public Car(int id, float sx, float sy, float heading, NeuralNetwork brain) {
        this.id      = id;
        this.x       = sx;
        this.y       = sy;
        this.heading = heading;
        this.speed   = Config.CAR_INIT_SPEED;
        this.brain   = brain;
    }

    // ── Think (NN decision) ───────────────────────────────────────────────────

    public void think(double[] inputs) {
        double[] out = brain.feedForward(inputs);
        gas   = (float) out[0];   // sigmoid [0,1]
        steer = (float) out[1];   // tanh [-1,1]
    }

    // ── Move (physics + checkpoint) ───────────────────────────────────────────

    public void move(Track track) {
        if (finishedRace || dnf) return;
        if (totalCPs < 0) totalCPs = track.getNumCheckpoints();

        // Physics
        if (gas > 0.5f) speed += Config.CAR_ACCEL * gas;
        else            speed -= Config.CAR_BRAKE * (1f - gas);
        speed = Math.max(0.5f, Math.min(speed, Config.CAR_MAX_SPEED));
        speed *= Config.CAR_FRICTION;

        // Draft: proportional boost based on proximity, reduced in corners (dirty air)
        if (draftIntensity > 0.01f) {
            float cornerPenalty = 1f - Math.min(Math.abs(steer) * 1.5f, 0.6f);
            float boost = Config.DRAFT_SPEED_BOOST * Config.CAR_MAX_SPEED * draftIntensity * cornerPenalty;
            speed = Math.min(speed + boost, Config.CAR_MAX_SPEED * (1f + draftIntensity * 0.12f));
        }

        float grip = 1f - (speed / Config.CAR_MAX_SPEED) * 0.32f;
        heading += steer * Config.CAR_STEER_SPEED * grip;

        float nx = x + (float) Math.cos(heading) * speed;
        float ny = y + (float) Math.sin(heading) * speed;

        if (!track.isOnTrack(nx, ny)) {
            speed *= 0.55f;  // wall scrub
            // Attempt a gentle correction instead of hard-freeze
            nx = x + (float) Math.cos(heading) * speed * 0.3f;
            ny = y + (float) Math.sin(heading) * speed * 0.3f;
            if (!track.isOnTrack(nx, ny)) { nx = x; ny = y; }
        }

        x = nx;
        y = ny;
        survivedTicks++;
        if (collisionCooldown > 0) collisionCooldown--;

        // Checkpoint
        float[] cp  = track.getCheckpoint(nextCheckpoint);
        float   cdx = cp[0] - x, cdy = cp[1] - y;

        if (cdx * cdx + cdy * cdy < Config.CHECKPOINT_REACH * Config.CHECKPOINT_REACH) {
            totalCheckpoints++;
            stuckTimer = 0;
            if (totalCheckpoints % totalCPs == 0) {
                lapsCompleted++;
                long lapTime = survivedTicks - lapStartTick;
                bestLapTicks  = Math.min(bestLapTicks, lapTime);
                totalLapTicks += lapTime;
                lapStartTick  = survivedTicks;
                if (lapsCompleted >= Config.NUM_LAPS) finishedRace = true;
            }
            nextCheckpoint = (nextCheckpoint + 1) % totalCPs;
        } else {
            // DNF if stuck for too long — keeps race cycles short
            if (++stuckTimer > Config.STUCK_TICKS) dnf = true;
        }
    }

    // ── Collision response ────────────────────────────────────────────────────

    public void applyCollision(float pushX, float pushY) {
        if (collisionCooldown > 0) return;
        speed *= Config.COLLISION_SPEED_PENALTY;
        x += pushX;
        y += pushY;
        crashCount++;
        collisionCooldown = Config.COLLISION_COOLDOWN;
    }

    public void recordOvertake() { overtakeCount++; }

    // ── Sensor setters ────────────────────────────────────────────────────────

    public void setRayHit(int i, float hx, float hy) { rayHitX[i] = hx; rayHitY[i] = hy; }
    public void setDraftIntensity(float target) {
        // Ramp up instantly, fade slowly (slipstream momentum)
        if (target > draftIntensity) {
            draftIntensity = target;
        } else {
            draftIntensity = Math.max(target, draftIntensity * Config.DRAFT_MOMENTUM);
        }
    }
    public boolean isInDraft()        { return draftIntensity > 0.05f; }
    public float   getDraftIntensity() { return draftIntensity; }

    // ── Fitness ───────────────────────────────────────────────────────────────

    public double getFitness(int finishPosition, int totalDrivers) {
        double posBonus   = (totalDrivers - finishPosition) * 60_000.0;
        double cpScore    = totalCheckpoints * 600.0;
        double overtakes  = overtakeCount * 2_500.0;
        double timePen    = survivedTicks * 0.6;
        double crashPen   = crashCount * 2_000.0;
        double survival   = survivedTicks * 0.02; // gradient for cars with 0 CPs
        return posBonus + cpScore + overtakes - timePen - crashPen + survival;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public float   getX()                { return x; }
    public float   getY()                { return y; }
    public float   getHeading()          { return heading; }
    public float   getSpeed()            { return speed; }
    public float   getGas()              { return gas; }
    public boolean isFinished()          { return finishedRace; }
    public boolean isDnf()               { return dnf; }
    public boolean isActive()            { return !finishedRace && !dnf; }
    public int     getLapsCompleted()    { return lapsCompleted; }
    public int     getTotalCheckpoints() { return totalCheckpoints; }
    public int     getNextCheckpoint()   { return nextCheckpoint; }
    public int     getCrashCount()       { return crashCount; }
    public int     getOvertakeCount()    { return overtakeCount; }
    public long    getSurvivedTicks()    { return survivedTicks; }
    public long    getBestLapTicks()     { return bestLapTicks; }
    public long    getAvgLapTicks()      { return lapsCompleted > 0 ? totalLapTicks / lapsCompleted : Long.MAX_VALUE; }
    public int     getCollisionCooldown(){ return collisionCooldown; }
    public float[] getRayHitX()          { return rayHitX; }
    public float[] getRayHitY()          { return rayHitY; }
    public NeuralNetwork getBrain()      { return brain; }
}
