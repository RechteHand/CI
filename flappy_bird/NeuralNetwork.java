package flappy_bird;

import java.util.Random;

public class NeuralNetwork {
    private final int inputSize;
    private final int hiddenSize;
    private final int outputSize;
    
    private final double[][] w1;
    private final double[] b1;
    private final double[][] w2;
    private final double[] b2;
    
    private final Random random = new Random();

    public NeuralNetwork(int inputSize, int hiddenSize, int outputSize) {
        this.inputSize = inputSize;
        this.hiddenSize = hiddenSize;
        this.outputSize = outputSize;
        
        w1 = new double[inputSize][hiddenSize];
        b1 = new double[hiddenSize];
        w2 = new double[hiddenSize][outputSize];
        b2 = new double[outputSize];
        
        // He initialization
        double limit1 = Math.sqrt(2.0 / inputSize);
        for (int i = 0; i < inputSize; i++) {
            for (int j = 0; j < hiddenSize; j++) {
                w1[i][j] = random.nextGaussian() * limit1;
            }
        }
        
        double limit2 = Math.sqrt(2.0 / hiddenSize);
        for (int i = 0; i < hiddenSize; i++) {
            for (int j = 0; j < outputSize; j++) {
                w2[i][j] = random.nextGaussian() * limit2;
            }
        }
    }
    
    public double[] feedForward(double[] inputs) {
        double[] hidden = new double[hiddenSize];
        for (int i = 0; i < hiddenSize; i++) {
            double sum = b1[i];
            for (int j = 0; j < inputSize; j++) {
                sum += inputs[j] * w1[j][i];
            }
            // ReLU activation for hidden layer
            hidden[i] = Math.max(0, sum);
        }
        
        double[] outputs = new double[outputSize];
        for (int i = 0; i < outputSize; i++) {
            double sum = b2[i];
            for (int j = 0; j < hiddenSize; j++) {
                sum += hidden[j] * w2[j][i];
            }
            // Sigmoid for output (0 to 1)
            outputs[i] = 1.0 / (1.0 + Math.exp(-sum));
        }
        return outputs;
    }
    
    public NeuralNetwork copy() {
        NeuralNetwork clone = new NeuralNetwork(inputSize, hiddenSize, outputSize);
        for (int i = 0; i < inputSize; i++) System.arraycopy(this.w1[i], 0, clone.w1[i], 0, hiddenSize);
        System.arraycopy(this.b1, 0, clone.b1, 0, hiddenSize);
        for (int i = 0; i < hiddenSize; i++) System.arraycopy(this.w2[i], 0, clone.w2[i], 0, outputSize);
        System.arraycopy(this.b2, 0, clone.b2, 0, outputSize);
        return clone;
    }
    
    public void mutate(double rate, double strength) {
        for (int i = 0; i < inputSize; i++) {
            for (int j = 0; j < hiddenSize; j++) {
                if (random.nextDouble() < rate) w1[i][j] += random.nextGaussian() * strength;
            }
        }
        for (int i = 0; i < hiddenSize; i++) {
            if (random.nextDouble() < rate) b1[i] += random.nextGaussian() * strength;
        }
        for (int i = 0; i < hiddenSize; i++) {
            for (int j = 0; j < outputSize; j++) {
                if (random.nextDouble() < rate) w2[i][j] += random.nextGaussian() * strength;
            }
        }
        for (int i = 0; i < outputSize; i++) {
            if (random.nextDouble() < rate) b2[i] += random.nextGaussian() * strength;
        }
    }
}
