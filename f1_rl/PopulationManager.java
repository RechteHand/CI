package f1_rl;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Genetic Algorithm that evolves the neural-network brains after each race.
 *
 * <p>Strategy:
 * <ul>
 *   <li>Top {@link Config#ELITE_COUNT} drivers carry over unchanged.</li>
 *   <li>Remaining slots are filled by mutating parents drawn from the top 30%.</li>
 * </ul>
 * </p>
 *
 * <p>Also tracks cross-generation statistics for the fitness chart.</p>
 */
public class PopulationManager {

    private List<NeuralNetwork> brains;
    private int generation = 1;
    private int stagnationCount = 0;  // races without fitness improvement
    private double lastBestFitness = Double.NEGATIVE_INFINITY;

    // History for the dashboard fitness chart
    private final List<Double> bestFitnessHistory = new ArrayList<>();
    private final List<Double> avgFitnessHistory  = new ArrayList<>();

    // All-time records
    private double bestAllTimeFitness  = Double.NEGATIVE_INFINITY;
    private long   bestAllTimeLap      = Long.MAX_VALUE;
    private int    bestAllTimeGen      = 0;

    private static final Random RNG = new Random();

    // ── Construction ──────────────────────────────────────────────────────────

    public PopulationManager() {
        brains = new ArrayList<>(Config.NUM_DRIVERS);
        for (int i = 0; i < Config.NUM_DRIVERS; i++) {
            brains.add(new NeuralNetwork(Config.NN_INPUTS, Config.NN_HIDDEN, Config.NN_OUTPUTS));
        }
    }

    // ── Evolution ─────────────────────────────────────────────────────────────

    /**
     * Evaluates fitness for each car, evolves the population, and prepares
     * brains for the next race.
     *
     * @param engine the completed race engine
     */
    public void evolve(RaceEngine engine) {
        List<Car> cars      = engine.getCars();
        int[]     positions = engine.computeFinishPositions();
        int       n         = cars.size();

        // ── Compute fitness ───────────────────────────────────────────────────
        double[] fitness = new double[n];
        for (Car car : cars) {
            fitness[car.id] = car.getFitness(positions[car.id], n);
        }

        // Sort by fitness descending
        Integer[] idx = new Integer[n];
        for (int i = 0; i < n; i++) idx[i] = i;
        java.util.Arrays.sort(idx, (a, b) -> Double.compare(fitness[b], fitness[a]));

        double bestFit = fitness[idx[0]];
        double sumFit  = 0;
        for (double f : fitness) sumFit += Math.max(0, f);
        bestFitnessHistory.add(bestFit);
        avgFitnessHistory.add(sumFit / n);

        // Update all-time records
        if (bestFit > bestAllTimeFitness) {
            bestAllTimeFitness = bestFit;
            bestAllTimeGen     = generation;
        }
        for (Car car : cars) {
            if (car.getBestLapTicks() < bestAllTimeLap)
                bestAllTimeLap = car.getBestLapTicks();
        }

        System.out.printf("Race %3d | Best: %.0f | Avg: %.0f | Leader: CAR_%02d%n",
                generation, bestFit, sumFit / n, idx[0]);

        // ── Stagnation detection ──────────────────────────────────────────────
        if (bestFit > lastBestFitness + 500) {
            stagnationCount = 0;
            lastBestFitness = bestFit;
        } else {
            stagnationCount++;
        }
        boolean stagnating = stagnationCount >= Config.STAGNATION_THRESHOLD;
        if (stagnating) {
            System.out.printf("  *** STAGNATION (%d races) — boosting mutation%n", stagnationCount);
        }

        // ── Reproduce ─────────────────────────────────────────────────────────
        List<NeuralNetwork> next = new ArrayList<>(n);

        double mutRate  = Config.MUTATION_RATE;
        double mutStr   = Config.MUTATION_STRENGTH;
        // Stagnation: escalate mutation; after 2× threshold inject fresh brains
        if (stagnationCount >= Config.STAGNATION_THRESHOLD * 2) {
            mutRate = 0.40; mutStr = 0.90;
            System.out.println("  *** HARD RESET — injecting fresh brains");
            stagnationCount = 0;
        } else if (stagnationCount >= Config.STAGNATION_THRESHOLD) {
            mutRate = 0.30; mutStr = 0.70;
        }

        // Elitism
        for (int i = 0; i < Math.min(Config.ELITE_COUNT, n); i++) {
            next.add(brains.get(idx[i]).copy());
        }

        // Selection pool: top 30%
        int poolSize = Math.max(1, (int) (n * 0.30));
        // If hard-stagnating, replace bottom 30% with fresh random brains
        int freshStart = stagnationCount == 0 ? n : (int)(n * 0.70);
        while (next.size() < freshStart) {
            NeuralNetwork parent = brains.get(idx[RNG.nextInt(poolSize)]).copy();
            parent.mutate(mutRate, mutStr);
            next.add(parent);
        }
        while (next.size() < n) {
            next.add(new NeuralNetwork(Config.NN_INPUTS, Config.NN_HIDDEN, Config.NN_OUTPUTS));
        }

        brains = next;
        generation++;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public List<NeuralNetwork> getBrains()            { return brains; }
    public int                 getGeneration()         { return generation; }
    public List<Double>        getBestFitnessHistory() { return bestFitnessHistory; }
    public List<Double>        getAvgFitnessHistory()  { return avgFitnessHistory; }
    public double              getBestAllTimeFitness() { return bestAllTimeFitness; }
    public long                getBestAllTimeLap()     { return bestAllTimeLap; }
    public int                 getBestAllTimeGen()     { return bestAllTimeGen; }
}
