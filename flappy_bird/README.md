# Dokumentation: Flappy Bird RL (Neuroevolution)

Dieses Modul ist eine Neuroevolutions-Simulation, in der ein Künstliches Neuronales Netz (KNN) über einen Genetischen Algorithmus lernt, das Hindernisspiel "Flappy Bird" autonom zu meistern. Die Evolution erfolgt durch das fortwährende Testen einer Population von Agenten und der stochastischen Mutation der besten Netze (Überlebenden).

## 1. Programmaufruf und Ausführung

Das Projekt benötigt keine zusätzlichen Parameter beim Programmaufruf. 

```bash
java flappy_bird.Main
```

## 2. Konfigurationsparameter (Quellcode)

Sämtliche Variablen, welche die Schwierigkeit des Spiels und das Lernverhalten des Algorithmus definieren, sind in der Klasse `Config.java` zentralisiert:

**Spieldynamik (Umgebung):**
- `GRAVITY`: Erdbeschleunigung, die pro Tick zur vertikalen Geschwindigkeit addiert wird.
- `JUMP_STRENGTH`: Negativer Geschwindigkeitsimpuls beim Ausführen eines Sprungs.
- `MAX_VELOCITY`: Begrenzung der maximalen Fallgeschwindigkeit.
- `PIPE_SPEED`: Horizontale Pixel-Verschiebung der Rohre pro Frame.
- `PIPE_GAP`: Vertikaler Abstand (in Pixeln) zwischen oberem und unterem Rohr.

**Künstliche Intelligenz (Architektur):**
- `POPULATION_SIZE`: Anzahl der Vögel (Agenten) pro Generation.
- `MUTATION_RATE`: Wahrscheinlichkeit für die mathematische Modifikation (Random-Jitter) von Netz-Gewichten beim Reproduktionsprozess (z. B. 0.05 für 5 %).
- `INPUT_SIZE`, `HIDDEN_SIZE`, `OUTPUT_SIZE`: Dimensionen der Schichten des Feed-Forward-Netzes.

## 3. Mathematische Modelle der Neuroevolution

### 3.1 Das Neuronale Netz (Sensoren & Aktivierung)
Jeder Agent besitzt ein dediziertes neuronales Netz. In `SimulationEngine.java` werden in der Methode `getSensors()` drei Werte auf das Intervall `[0.0, 1.0]` normalisiert, um das Training zu stabilisieren:
1. `(Pipe_X - BIRD_X) / WINDOW_WIDTH`: Relative Distanz zum nächsten Hindernis.
2. `(Bird_Y - GapCenter_Y) / WINDOW_HEIGHT`: Relative Y-Distanz zur sicheren Durchflugszone.
3. `Velocity / MAX_VELOCITY`: Eigene kinematische Fallgeschwindigkeit.

Das Feed-Forward-Netz transformiert diese Inputs zu einem skalaren Output-Wert unter Verwendung der Sigmoid-Funktion:
`f(x) = 1 / (1 + e^-x)`
Liegt der Outputwert nach der Propagation über `0.5`, wird die Methode `bird.jump()` aufgerufen.

### 3.2 Fitness-Funktion und Selektion
Die Fitness-Zielfunktion ist rein zeitbasiert definiert:
`score++`
Für jeden Frame, den der Vogel überlebt, erhöht sich seine Fitness um 1. Sobald alle Agenten einer Population aufgrund von Kollisionen (`isAlive = false`) ausscheiden, wertet der `PopulationManager` die Ergebnisse aus. Die Agenten mit der längsten Überlebenszeit (Eliten) vererben ihre Matrizen an die Folgegeneration, modifiziert um den Gaussschen Rausch-Faktor der `MUTATION_RATE`.

### 3.3 Kinematische Integration
Die physikalische Repräsentation des Agenten bedient sich des Euler-Integrationsverfahrens zur diskreten Ermittlung von Ort und Geschwindigkeit in der Methode `update()`:
```java
velocity += Config.GRAVITY;
if (velocity > Config.MAX_VELOCITY) velocity = Config.MAX_VELOCITY;
y += velocity;
```
