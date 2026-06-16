from abc import ABC, abstractmethod

class Layer(ABC):
    """
    Abstrakte Basisklasse für alle Netzwerkschichten.
    Fördert Local Reasoning und klare Schnittstellen (Clean Code).
    """

    def __init__(self):
        self.input = None
        self.output = None

    @abstractmethod
    def forward(self, input_data):
        """
        Berechnet den Output der Schicht basierend auf dem Input.
        """
        pass

    @abstractmethod
    def backward(self, output_gradient, learning_rate):
        """
        Berechnet den Gradienten bezüglich des Inputs (für die vorherige Schicht).
        Aktualisiert ggf. eigene Parameter (Weights/Biases).
        """
        pass
