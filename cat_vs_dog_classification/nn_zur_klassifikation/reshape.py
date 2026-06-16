import numpy as np
from .layer import Layer

class ReshapeLayer(Layer):
    """
    Wandelt z. B. (Batch_Size, Channels, Height, Width) in (Batch_Size, Vektor) um.
    Verbindet Convolutional Layer mit Dense Layern.
    """
    def __init__(self, input_shape, output_shape):
        super().__init__()
        self.input_shape = input_shape
        self.output_shape = output_shape

    def forward(self, input_data):
        self.input = input_data
        batch_size = input_data.shape[0]
        self.output = np.reshape(input_data, (batch_size, *self.output_shape))
        return self.output

    def backward(self, output_gradient, learning_rate):
        batch_size = output_gradient.shape[0]
        return np.reshape(output_gradient, (batch_size, *self.input_shape))
