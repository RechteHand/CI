# Katzen vs. Hunde: Neuronales Netz "From Scratch"

Dieses Projekt demonstriert die Klassifikation von Bildern (Katzen vs. Hunde) mithilfe eines **komplett selbst programmierten neuronalen Netzes** in Python. Es werden bewusst keine Machine Learning Frameworks (wie TensorFlow, Keras oder PyTorch) verwendet, um die interne Logik und Mathematik transparenter zu machen.

Der Code wurde nach Prinzipien des **Clean Code** strukturiert (Local Reasoning, klare Namensgebung, Trennung von Command und Query).

## Architektur des Modells
Wir nutzen ein voll funktionsfähiges **Convolutional Neural Network (CNN)**, das speziell für Bilderkennung entwickelt wurde. Um die extrem rechenintensive Matrixfaltung in akzeptabler Zeit zu berechnen, nutzen wir die C-optimierte Bibliothek `scipy.signal` (während die logische Verschaltung und die Backpropagation komplett "from scratch" geschrieben sind).

1. **Eingabe (Input):** Die `.jpg`-Bilder werden auf 32x32 Pixel skaliert und in Graustufen konvertiert. Dies ergibt eine 3D-Matrix (Channels=1, Height=32, Width=32).
2. **Convolutional Layer:** Führt eine Faltung (Cross-Correlation) durch. Wir nutzen 4 Filter (Kernels) der Größe 3x3. Output: 4x30x30. Danach folgt **ReLU** als Aktivierungsfunktion.
3. **Max Pooling Layer:** Reduziert die räumlichen Dimensionen (Downsampling) mit einem 2x2 Fenster. Die wichtigsten Features (Max-Werte) bleiben erhalten. Output: 4x15x15.
4. **Reshape Layer (Flatten):** Wandelt den 3D-Tensor (4x15x15) in einen flachen 1D-Vektor mit 900 Elementen um.
5. **Hidden Dense Layer:** Verdichtet die 900 Merkmale auf 64 Neuronen (mit **ReLU**).
6. **Ausgabe (Output):** Ein letzter Dense Layer verdichtet auf **1 Neuron** mit **Sigmoid**-Funktion (z.B. < 0.5 = Katze, > 0.5 = Hund).

## Mathematische Logik (Backpropagation)
Ein neuronales Netz lernt durch die Minimierung eines Fehlers (Loss). Hier ist die interne Logik unseres Netzes:

- **Forward Propagation (`forward`):** Die Eingabedaten werden mit den Gewichten (`weights`) der Schicht multipliziert und ein Bias (`bias`) wird addiert: `Y = X * W + B`. Danach durchlaufen die Werte die Aktivierungsfunktion.
- **Fehlerberechnung (Loss):** Wir nutzen den **Binary Cross Entropy** Loss, der die Wahrscheinlichkeits-Vorhersage (Sigmoid Output) gegen die echte Klasse (0 oder 1) vergleicht.
- **Backward Propagation (`backward`):** Über die Kettenregel der Differentialrechnung leiten wir den Fehler Schicht für Schicht rückwärts ab. Jeder Layer bekommt einen `output_gradient` (wie sich der Fehler ändert, wenn sich sein Output ändert) und berechnet daraus drei Dinge:
  1. Den Gradienten bezüglich der eigenen Gewichte (`dE/dW`).
  2. Den Gradienten bezüglich des eigenen Bias (`dE/dB`).
  3. Den Gradienten bezüglich seines Inputs (`dE/dX`). Diesen gibt er an die vorherige Schicht weiter.
- **Gradient Descent:** Die Schichten passen am Ende des Backpropagation-Schrittes ihre Gewichte in die entgegengesetzte Richtung des Gradienten an (`weights = weights - learning_rate * weights_gradient`).

*Hinweis:* Wir nutzen `numpy` für diese Matrix-Multiplikationen. Dies dient ausschließlich der Performance. In nativem Python mit verschachtelten For-Schleifen würde das Training über den gesamten Datensatz Tage statt Minuten dauern.

## Struktur des Codes
Der Code befindet sich im Ordner `nn_zur_klassifikation` und ist streng in kleine, gut verständliche Module unterteilt:

- `layer.py`: Enthält die abstrakte Basisklasse für alle Netzwerkschichten.
- `dense.py`: Enthält die Implementierung der vollvernetzten Schichten inklusive Gewichts-Updates.
- `activations.py`: Implementiert ReLU und Sigmoid.
- `losses.py`: Beinhaltet die Binary Cross Entropy Formel und ihre Ableitung.
- `network.py`: Orchestriert das gesamte Netz (hält die Liste der Schichten, macht den Forward-Pass und triggert den Backward-Pass).
- `data_loader.py`: Lädt die rohen Bilder von der Festplatte und splittet sie in Trainings- und Testdaten.

## Wie man das Projekt startet

**1. Datensatz herunterladen:**
Dieses Netz basiert auf dem bekannten "Cats vs Dogs" Datensatz (wir nutzen das offiziell gefilterte Subset von Google/Kaggle mit ca. 3000-4000 Bildern, da es schneller lädt). Da dieser Datensatz zu groß ist, wird er nicht mit in das Repository gepusht.

Um die Bilder zu laden und korrekt zu strukturieren, führe einfach folgende Befehle in deinem Terminal (im Ordner `cat_vs_dog_classification`) aus:

```bash
curl -o cats_and_dogs.zip https://storage.googleapis.com/mledu-datasets/cats_and_dogs_filtered.zip
unzip -q cats_and_dogs.zip
mkdir -p dataset/cats dataset/dogs
mv cats_and_dogs_filtered/train/cats/* dataset/cats/
mv cats_and_dogs_filtered/train/dogs/* dataset/dogs/
mv cats_and_dogs_filtered/validation/cats/* dataset/cats/
mv cats_and_dogs_filtered/validation/dogs/* dataset/dogs/
rm -rf cats_and_dogs_filtered cats_and_dogs.zip
```
*(Das Skript schiebt alle Bilder in die Ordner `dataset/cats` und `dataset/dogs`. Unser eigener Daten-Loader mischt die Bilder danach ohnehin wieder komplett neu für Training und Test).*

**2. Training starten:**
Das Hauptskript lädt den Datensatz, initialisiert das neuronale Netz und startet das Training.
```bash
python3 train_custom_nn.py
```

Das Skript teilt die Daten automatisch zu **50% in Trainingsdaten** und zu **50% in Testdaten** auf. Nach dem Training (standardmäßig 20 Epochen) evaluiert das Skript die Erfolgsrate (Accuracy) auf beiden Datensätzen und gibt diese im Terminal aus.
