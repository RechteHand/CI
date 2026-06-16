class NeuralNetwork:
    """
    Das eigentliche Netzwerk, das Schichten verwaltet und Training/Prediction steuert.
    """
    def __init__(self):
        self.layers = []
        self.loss_function = None
        self.loss_prime_function = None

    def add_layer(self, layer):
        self.layers.append(layer)

    def set_loss_function(self, loss_function, loss_prime_function):
        self.loss_function = loss_function
        self.loss_prime_function = loss_prime_function

    def predict(self, input_data):
        """
        Query: Sagt das Ergebnis für gegebene Eingabedaten voraus.
        """
        result = input_data
        for layer in self.layers:
            result = layer.forward(result)
        return result

    def train_step(self, x, y, learning_rate):
        """
        Command: Führt einen einzelnen Trainingsschritt (Forward + Backward) für einen Batch aus.
        Gibt den berechneten Loss zurück.
        """
        # Forward pass
        output = self.predict(x)
        
        # Fehler berechnen
        loss = self.loss_function(y, output)
        
        # Backward pass
        gradient = self.loss_prime_function(y, output)
        for layer in reversed(self.layers):
            gradient = layer.backward(gradient, learning_rate)
            
        return loss
