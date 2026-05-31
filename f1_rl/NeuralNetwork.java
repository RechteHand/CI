package f1_rl;

import java.util.Random;

/**
 * Fixed-architecture Multi-Layer Perceptron.
 *
 * <p>Architecture: {@code INPUT_SIZE → HIDDEN_SIZE → OUTPUT_SIZE}</p>
 * <ul>
 *   <li>Hidden layer: ReLU activation</li>
 *   <li>Output 0 (gas):   Sigmoid  → [0, 1]</li>
 *   <li>Output 1 (steer): Tanh     → [-1, 1]</li>
 * </ul>
 */
public class NeuralNetwork {

    private final int inputSize, hiddenSize, outputSize;

    private final double[][] w1; // [inputSize][hiddenSize]
    private final double[]   b1; // [hiddenSize]
    private final double[][] w2; // [hiddenSize][outputSize]
    private final double[]   b2; // [outputSize]

    private static final Random RNG = new Random();

    // ── Construction ──────────────────────────────────────────────────────────

    public NeuralNetwork(int inputSize, int hiddenSize, int outputSize) {
        this.inputSize  = inputSize;
        this.hiddenSize = hiddenSize;
        this.outputSize = outputSize;

        w1 = new double[inputSize][hiddenSize];
        b1 = new double[hiddenSize];
        w2 = new double[hiddenSize][outputSize];
        b2 = new double[outputSize];

        heInit(w1, inputSize);
        heInit(w2, hiddenSize);
    }

    /** He initialisation: weights ~ N(0, sqrt(2/fanIn)). */
    private void heInit(double[][] w, int fanIn) {
        double std = Math.sqrt(2.0 / fanIn);
        for (double[] row : w)
            for (int j = 0; j < row.length; j++)
                row[j] = RNG.nextGaussian() * std;
    }

    // ── Inference ────────────────────────────────────────────────────────────

    /**
     * Forward pass.
     *
     * @param inputs array of length {@code inputSize}
     * @return [gas ∈ [0,1], steer ∈ [-1,1]]
     */
    public double[] feedForward(double[] inputs) {
        double[] hidden = new double[hiddenSize];
        for (int j = 0; j < hiddenSize; j++) {
            double sum = b1[j];
            for (int i = 0; i < inputSize; i++) sum += inputs[i] * w1[i][j];
            hidden[j] = Math.max(0, sum); // ReLU
        }

        double[] output = new double[outputSize];
        for (int j = 0; j < outputSize; j++) {
            double sum = b2[j];
            for (int i = 0; i < hiddenSize; i++) sum += hidden[i] * w2[i][j];
            output[j] = (j == 0) ? sigmoid(sum) : Math.tanh(sum);
        }
        return output;
    }

    private double sigmoid(double x) {
        return 1.0 / (1.0 + Math.exp(-x));
    }

    // ── Genetics ─────────────────────────────────────────────────────────────

    /** Returns a deep copy of this network. */
    public NeuralNetwork copy() {
        NeuralNetwork clone = new NeuralNetwork(inputSize, hiddenSize, outputSize);
        for (int i = 0; i < inputSize;  i++) System.arraycopy(w1[i], 0, clone.w1[i], 0, hiddenSize);
        for (int i = 0; i < hiddenSize; i++) System.arraycopy(w2[i], 0, clone.w2[i], 0, outputSize);
        System.arraycopy(b1, 0, clone.b1, 0, hiddenSize);
        System.arraycopy(b2, 0, clone.b2, 0, outputSize);
        return clone;
    }

    /**
     * Applies Gaussian mutation in-place.
     *
     * @param rate     probability each weight is mutated
     * @param strength standard deviation of the Gaussian noise
     */
    public void mutate(double rate, double strength) {
        for (double[] row : w1) mutateArray(row, rate, strength);
        for (double[] row : w2) mutateArray(row, rate, strength);
        mutateArray(b1, rate, strength);
        mutateArray(b2, rate, strength);
    }

    private void mutateArray(double[] arr, double rate, double strength) {
        for (int i = 0; i < arr.length; i++)
            if (RNG.nextDouble() < rate)
                arr[i] += RNG.nextGaussian() * strength;
    }
}
