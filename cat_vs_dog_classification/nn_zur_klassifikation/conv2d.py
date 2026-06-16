import numpy as np
from scipy import signal
from .layer import Layer

class Conv2DLayer(Layer):
    """
    Convolutional Layer mit SciPy für Performance (C-optimierte Faltung),
    während die neuronale Logik (Gradienten/Backprop) selbst programmiert bleibt.
    """
    def __init__(self, in_channels, out_channels, kernel_size):
        super().__init__()
        self.in_channels = in_channels
        self.out_channels = out_channels
        self.kernel_size = kernel_size
        
        # He Initialization
        self.weights = np.random.randn(out_channels, in_channels, kernel_size, kernel_size) * np.sqrt(2.0 / (in_channels * kernel_size * kernel_size))
        self.bias = np.zeros((out_channels, 1))

    def forward(self, input_data):
        self.input = input_data
        batch_size, c, h, w = input_data.shape
        out_h = h - self.kernel_size + 1
        out_w = w - self.kernel_size + 1
        
        self.output = np.zeros((batch_size, self.out_channels, out_h, out_w))
        
        # Forward pass für jedes Bild im Batch
        for b in range(batch_size):
            for i in range(self.out_channels):
                for j in range(self.in_channels):
                    # Valid Cross-Correlation (scipy.signal.correlate2d)
                    self.output[b, i] += signal.correlate2d(self.input[b, j], self.weights[i, j], mode='valid')
                self.output[b, i] += self.bias[i]
                
        return self.output

    def backward(self, output_gradient, learning_rate):
        batch_size, c, h, w = self.input.shape
        
        weights_gradient = np.zeros_like(self.weights)
        input_gradient = np.zeros_like(self.input)
        
        # Bias Gradient: Summe über Batch und räumliche Dimensionen
        bias_gradient = np.sum(output_gradient, axis=(0, 2, 3)).reshape(-1, 1)

        for b in range(batch_size):
            for i in range(self.out_channels):
                for j in range(self.in_channels):
                    # Gradient bezüglich Weights: Valid Cross-Correlation von Input und Output-Gradient
                    weights_gradient[i, j] += signal.correlate2d(self.input[b, j], output_gradient[b, i], mode='valid')
                    
                    # Gradient bezüglich Input: Full Convolution von Output-Gradient und rotierter Weight
                    input_gradient[b, j] += signal.convolve2d(output_gradient[b, i], self.weights[i, j], mode='full')
                    
        # Update weights and bias
        self.weights -= learning_rate * weights_gradient
        self.bias -= learning_rate * bias_gradient
        
        return input_gradient
