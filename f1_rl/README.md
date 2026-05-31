# F1 AI Racing Simulation (Neuroevolution)

Ein hochleistungsfähiges Multi-Agenten-System, in dem virtuelle Formel-1-Autos durch **Reinforcement Learning (Neuroevolution)** das Fahren von Grund auf erlernen. Die Fahrzeuge haben keine einprogrammierte Lenk- oder Bremslogik, sondern entwickeln ihr Fahrverhalten über Tausende Generationen durch Evolution, Mutation und Survival of the Fittest.

---

## Wie man es startet

Voraussetzung: Java Development Kit (JDK) muss installiert sein.

1. Öffne das Terminal in deinem Projektordner (z. B. `CI/`).
2. Kompiliere den gesamten Java-Code:
   ```bash
   javac -d . f1_rl/*.java
   ```
3. Starte die Simulation:
   ```bash
   java f1_rl.Main
   ```

**UI-Steuerung während des Rennens:**
- **Dashboard (◀ / ▶):** Blendet die Live-Telemetrie und Fitness-Graphen ein oder aus.
- **Turbo (⚡):** Schaltet den Fast-Forward-Modus ein (berechnet 25 Ticks pro Frame). Perfekt, um Hunderte Generationen in Sekunden zu trainieren.
- **Speed-Slider:** Bestimmt die FPS (Frames per Second) im normalen Modus.

---

## Wie die KI lernt (Das Konzept)

Das System nutzt einen Genetischen Algorithmus gepaart mit Neuronalen Netzen (ähnlich wie NEAT).

1. **Generation 1:** Die Autos haben ein Gehirn mit rein zufälligen Gewichten (Zahlen). Sie lenken und bremsen komplett erratisch und crashen sofort.
2. **Die Note (Fitness):** Am Ende eines Rennens wird berechnet, wie gut jedes Auto war (siehe Formel unten).
3. **Selektion (80/20-Regel):** Die schlechtesten 80 % der Population werden gnadenlos aus dem Speicher gelöscht. Nur die 20 % "Eliten" dürfen überleben.
4. **Fortpflanzung (Mutation):** Die leeren Startplätze werden aufgefüllt, indem die Gehirne der Eliten kopiert werden. Dabei wird ein mathematischer Zufallsfehler (**Mutation**) hinzugefügt. So probieren die Kind-Autos in der nächsten Generation automatisch neue Brems- und Lenkmanöver aus.

---

## Mathematische Kernkomponenten & Formeln

Hier sind die wichtigsten mathematischen Modelle, die der Simulation ihre Tiefe verleihen.

### 1. Das Neuronale Netz (Feed-Forward)
Das Gehirn jedes Autos. Es bekommt 16 Sensor-Inputs, verrechnet sie durch eine versteckte Schicht (Hidden Layer) und gibt 2 Outputs (Gas und Lenkung).
* **Aktivierungsfunktionen:** 
  * Für das Lenkrad (Wertebereich `-1.0` bis `1.0`):  
    `tanh(x) = (e^x - e^-x) / (e^x + e^-x)`
  * Für das Gaspedal (Wertebereich `0.0` bis `1.0`):  
    `sigmoid(x) = 1 / (1 + e^-x)`

### 2. Fahrphysik (Grip & Reibung)
Die Bewegung basiert auf realistischer Trägheit.
* **Luftwiderstand / Reibung:** Bei jedem Frame wird die aktuelle Geschwindigkeit durch die Reibung leicht reduziert.  
  `Speed = Speed * 0.974`
* **Untersteuern (Grip):** Je schneller das Auto fährt, desto schwerer lässt es sich physikalisch drehen. Die Lenkgeschwindigkeit verringert sich prozentual zur Höchstgeschwindigkeit:  
  `Grip = 1.0 - (Speed / Max_Speed) * 0.32`

### 3. Der Windschatten (Slipstream & Dirty Air)
Das Auto berechnet einen "Sog", wenn es hinter einem Gegner fährt.
* **Distanz & Sichtkegel:** Per Trigonometrie wird geprüft, ob sich ein Auto in einem 22-Grad-Sichtkegel direkt vor der eigenen Schnauze befindet.
* **Dirty Air (Corner-Penalty):** In Kurven wird der Sog stark abgeschwächt, da die Luft dort verwirbelt ist.  
  `Corner_Penalty = 1.0 - min(|Steer_Winkel| * 1.5, 0.6)`
* **Boost-Formel:**  
  `Boost = DRAFT_BOOST * Max_Speed * Intensität * Corner_Penalty`
* **Momentum (Auslaufen):** Der Boost bricht beim Ausscheren nicht sofort ab, sondern wird sanft abgebaut.  
  `Intensität(Neu) = Intensität(Alt) * 0.96`

### 4. Das Radar (Spatial Checkpoint Tracking)
Die Autos orientieren sich im Raum nicht nur über Laser-Sensoren für Wände, sondern peilen mathematisch den nächsten Checkpoint an.
* **Distanz:** Satz des Pythagoras zur Berechnung der Luftlinie zum Ziel.  
  `Distanz = √((Target_X - Car_X)² + (Target_Y - Car_Y)³)`
* **Relativer Winkel:** Trigonometrie, um zu wissen, wie stark das Lenkrad gedreht werden muss.  
  `Winkel = arctan2(Target_Y - Car_Y, Target_X - Car_X) - Car_Heading`

### 5. Die Laser-Sensoren (Raycasting)
Jedes Auto feuert 7 Vektoren (Strahlen) ab, um zu "sehen".
* Jeder Strahl wächst in 3-Pixel-Schritten (`StepX = cos(Winkel) * 3`, `StepY = sin(Winkel) * 3`).
* Bei jedem Schritt prüft der Code das `Track-Grid`: *"Ist Pixel(X,Y) noch Asphalt?"*
* Sobald Gras getroffen wird, wird der Wert normalisiert. Das Netz bekommt `1.0` (komplett frei) bis `0.0` (Wand berührt Nase).

### 6. Die Fitness-Funktion (Die ultimative Bewertung)
Diese Formel bestimmt, wer stirbt und wer seine Gene weitergibt. Sie belohnt Fortschritt und bestraft riskantes Fahren.
```java
Fitness = (Checkpoints * 600.0) 
        + (Überholmanöver * 2500.0) 
        + (Positions_Bonus) 
        - (Abgelaufene_Zeit * 0.6) 
        - (Crashs * 2000.0) 
        + (Überlebenszeit * 0.02)
```
**(Anmerkung: Der kleine Bonus für die Überlebenszeit hilft dem Algorithmus in Generation 1, überhaupt die allerersten Autos zu finden, die länger als eine halbe Sekunde auf der Strecke bleiben, ohne in die Wand zu krachen).*
