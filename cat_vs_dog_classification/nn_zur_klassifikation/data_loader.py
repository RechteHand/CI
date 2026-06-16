import os
import numpy as np
from PIL import Image

def load_and_prepare_data(base_dir, img_size=(64, 64), max_images_per_class=None):
    """
    Lädt Bilder, skaliert sie und teilt sie exakt 50/50 in Training und Test auf.
    Gibt die Bilder als 4D-Tensoren (Batch, Channels, Height, Width) zurück, 
    was für Convolutional Layer nötig ist.
    
    Returns:
        X_train, Y_train, X_test, Y_test
    """
    categories = ['cats', 'dogs']
    all_data = []

    for category in categories:
        folder_path = os.path.join(base_dir, category)
        label = 0 if category == 'cats' else 1
        
        if not os.path.exists(folder_path):
            continue
            
        filenames = os.listdir(folder_path)
        # Nur Bilder verarbeiten
        filenames = [f for f in filenames if f.endswith('.jpg')]
        
        # Optional: Begrenzung der Bilderanzahl für schnelleres Testen
        if max_images_per_class:
            filenames = filenames[:max_images_per_class]

        for file in filenames:
            img_path = os.path.join(folder_path, file)
            try:
                # Bild laden, in Graustufen umwandeln (1 Channel) und auf die Zielgröße resizen
                img = Image.open(img_path).convert('L')
                img = img.resize(img_size)
                
                # In numpy Array umwandeln und normalisieren (Werte zwischen 0 und 1)
                img_array = np.array(img) / 255.0
                
                # Reshape auf (Channels, Height, Width) -> (1, 64, 64)
                img_3d = np.reshape(img_array, (1, img_size[0], img_size[1]))
                
                all_data.append((img_3d, label))
            except Exception as e:
                pass

    # Mischen der Daten
    np.random.shuffle(all_data)

    # In X (Features) und Y (Labels) trennen
    X = np.array([item[0] for item in all_data])
    Y = np.array([[item[1]] for item in all_data])

    # Exakt 50% / 50% Aufteilung wie vom User gefordert
    split_index = len(X) // 2
    
    X_train = X[:split_index]
    Y_train = Y[:split_index]
    
    X_test = X[split_index:]
    Y_test = Y[split_index:]

    return X_train, Y_train, X_test, Y_test
