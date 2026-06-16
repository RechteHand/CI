import numpy as np
from nn_zur_klassifikation.network import NeuralNetwork
from nn_zur_klassifikation.dense import DenseLayer
from nn_zur_klassifikation.conv2d import Conv2DLayer
from nn_zur_klassifikation.maxpooling2d import MaxPooling2DLayer
from nn_zur_klassifikation.reshape import ReshapeLayer
from nn_zur_klassifikation.activations import ReLU, Sigmoid
from nn_zur_klassifikation.losses import binary_cross_entropy, binary_cross_entropy_prime
from nn_zur_klassifikation.data_loader import load_and_prepare_data

def calculate_accuracy(network, X, Y):
    """
    Berechnet die Erfolgsrate (Accuracy) für gegebene Daten.
    """
    predictions = network.predict(X)
    # Runden auf 0 oder 1
    predicted_classes = np.round(predictions)
    accuracy = np.mean(predicted_classes == Y) * 100
    return accuracy

def main():
    print("Starte Vorbereitung der Daten...")
    # Da ein CNN mit reinen Python-Schleifen selbst mit scipy extrem lange läuft,
    # laden wir hier das gesamte Datenset (64x64). 
    X_train, Y_train, X_test, Y_test = load_and_prepare_data('dataset', img_size=(32, 32))
    
    print(f"Daten geladen! Trainings-Set: {len(X_train)} Bilder, Test-Set: {len(X_test)} Bilder.")
    
    nn = NeuralNetwork()
    nn.set_loss_function(binary_cross_entropy, binary_cross_entropy_prime)
    
    # 1. Convolutional Layer (Input: 1x32x32)
    # Nutzt 4 Filter (Kernels) der Größe 3x3. Output: 4x30x30
    nn.add_layer(Conv2DLayer(in_channels=1, out_channels=4, kernel_size=3))
    nn.add_layer(ReLU())
    
    # 2. Max Pooling Layer (Input: 4x30x30, Output: 4x15x15)
    nn.add_layer(MaxPooling2DLayer(pool_size=2))
    
    # 3. Reshape Layer (Flatten: aus 4x15x15 wird ein 1D Vektor mit 900 Elementen)
    nn.add_layer(ReshapeLayer(input_shape=(4, 15, 15), output_shape=(4 * 15 * 15,)))
    
    # 4. Dense (Fully Connected) Layer (Input: 900, Output: 64)
    nn.add_layer(DenseLayer(4 * 15 * 15, 64))
    nn.add_layer(ReLU())
    
    # 5. Output Layer (Input: 64, Output: 1)
    nn.add_layer(DenseLayer(64, 1))
    nn.add_layer(Sigmoid())

    epochs = 10
    learning_rate = 0.01
    batch_size = 16

    print("Starte Training (das kann einen Moment dauern)...")
    
    for epoch in range(epochs):
        epoch_loss = 0
        
        # Mini-Batch Training für bessere Performance und Stabilität
        indices = np.arange(len(X_train))
        np.random.shuffle(indices)
        
        for i in range(0, len(X_train), batch_size):
            batch_indices = indices[i:i + batch_size]
            X_batch = X_train[batch_indices]
            Y_batch = Y_train[batch_indices]
            
            loss = nn.train_step(X_batch, Y_batch, learning_rate)
            epoch_loss += loss
            
        average_loss = epoch_loss / (len(X_train) / batch_size)
        print(f"Epoche {epoch + 1}/{epochs} - Durchschnittlicher Fehler (Loss): {average_loss:.4f}")

    print("\nTraining abgeschlossen!")
    
    # Erfolgsrate berechnen (wie vom User gewünscht)
    train_accuracy = calculate_accuracy(nn, X_train, Y_train)
    test_accuracy = calculate_accuracy(nn, X_test, Y_test)
    
    print("-" * 50)
    print("ERFOLGSRATE (ACCURACY)")
    print("-" * 50)
    print(f"Erste 50% (Trainingsdaten): {train_accuracy:.2f}%")
    print(f"Restliche 50% (Testdaten):  {test_accuracy:.2f}%")
    print("-" * 50)

if __name__ == "__main__":
    main()
