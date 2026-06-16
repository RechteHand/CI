import numpy as np
from .layer import Layer

class ActivationLayer(Layer):
    """
    Basisklasse für Aktivierungsfunktionen.
    """
    def __init__(self, activation_function, activation_prime):
        super().__init__()
        self.activation_function = activation_function
        self.activation_prime = activation_prime

    def forward(self, input_data):
        self.input = input_data
        self.output = self.activation_function(self.input)
        return self.output

    def backward(self, output_gradient, learning_rate):
        # Elementweise Multiplikation des eingehenden Gradienten mit der Ableitung der Aktivierungsfunktion
        return output_gradient * self.activation_prime(self.input)


class ReLU(ActivationLayer):
    """
    Rectified Linear Unit (ReLU) Aktivierungsfunktion.
    """
    def __init__(self):
        def relu(x):
            return np.maximum(0, x)
        
        def relu_prime(x):
            return (x > 0).astype(float)
            
        super().__init__(relu, relu_prime)


class Sigmoid(ActivationLayer):
    """
    Sigmoid Aktivierungsfunktion (oft für den Output bei binärer Klassifikation genutzt).
    """
    def __init__(self):
        def sigmoid(x):
            # Clip Werte um Overflows (np.exp) zu vermeiden
            x_clipped = np.clip(x, -500, 500)
            return 1 / (1 + np.exp(-x_clipped))
        
        def sigmoid_prime(x):
            s = sigmoid(x)
            return s * (1 - s)
            
        super().__init__(sigmoid, sigmoid_prime)
