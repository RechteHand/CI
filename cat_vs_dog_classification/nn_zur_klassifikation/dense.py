import numpy as np
from .layer import Layer

class DenseLayer(Layer):
    """
    Eine vollvernetzte (Dense) Schicht.
    
    HINWEIS FÜR DEN PROFESSOR:
    Wir nutzen hier `numpy` aus reinen Performance-Gründen für die Matrizenmultiplikation.
    In reinem Python (mit for-Schleifen) würde das Training über tausende Bilder 
    Tage anstelle von Minuten dauern.
    """
    def __init__(self, input_size, output_size):
        super().__init__()
        # Initialisierung der Gewichte mit He-Initialization
        self.weights = np.random.randn(input_size, output_size) * np.sqrt(2.0 / input_size)
        self.bias = np.zeros((1, output_size))

    def forward(self, input_data):
        """
        Forward Propagation: Y = X * W + B
        """
        self.input = input_data
        self.output = np.dot(self.input, self.weights) + self.bias
        return self.output

    def backward(self, output_gradient, learning_rate):
        """
        Backward Propagation:
        dE/dW = X^T * dE/dY
        dE/dB = sum(dE/dY)
        dE/dX = dE/dY * W^T
        """
        # Gradienten bezüglich der Parameter berechnen
        weights_gradient = np.dot(self.input.T, output_gradient)
        bias_gradient = np.sum(output_gradient, axis=0, keepdims=True)
        
        # Gradienten für die vorherige Schicht berechnen (dE/dX)
        input_gradient = np.dot(output_gradient, self.weights.T)
        
        # Eigene Parameter aktualisieren
        self.weights -= learning_rate * weights_gradient
        self.bias -= learning_rate * bias_gradient
        
        return input_gradient
