import numpy as np
from .layer import Layer

class MaxPooling2DLayer(Layer):
    """
    Max Pooling Layer zur Reduzierung der räumlichen Dimensionen.
    """
    def __init__(self, pool_size=2):
        super().__init__()
        self.pool_size = pool_size

    def forward(self, input_data):
        self.input = input_data
        batch_size, c, h, w = input_data.shape
        
        out_h = h // self.pool_size
        out_w = w // self.pool_size
        
        self.output = np.zeros((batch_size, c, out_h, out_w))
        
        # Max-Pooling Operation
        for b in range(batch_size):
            for i in range(c):
                for h_out in range(out_h):
                    for w_out in range(out_w):
                        h_start = h_out * self.pool_size
                        h_end = h_start + self.pool_size
                        w_start = w_out * self.pool_size
                        w_end = w_start + self.pool_size
                        
                        window = self.input[b, i, h_start:h_end, w_start:w_end]
                        self.output[b, i, h_out, w_out] = np.max(window)
                        
        return self.output

    def backward(self, output_gradient, learning_rate):
        batch_size, c, h, w = self.input.shape
        input_gradient = np.zeros_like(self.input)
        
        out_h = h // self.pool_size
        out_w = w // self.pool_size
        
        # Gradient wird nur an das Pixel weitergeleitet, das im Forward-Pass das Maximum war
        for b in range(batch_size):
            for i in range(c):
                for h_out in range(out_h):
                    for w_out in range(out_w):
                        h_start = h_out * self.pool_size
                        h_end = h_start + self.pool_size
                        w_start = w_out * self.pool_size
                        w_end = w_start + self.pool_size
                        
                        window = self.input[b, i, h_start:h_end, w_start:w_end]
                        max_val = np.max(window)
                        
                        # Finde Maske für das Maximum
                        mask = (window == max_val)
                        
                        # Verteile den Gradienten
                        input_gradient[b, i, h_start:h_end, w_start:w_end] = mask * output_gradient[b, i, h_out, w_out]
                        
        return input_gradient
