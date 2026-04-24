# RL — Reinforcement Learning mit Q-Learning

Lernendes Auto fährt durch einen Schlangen-Korridor. Lernverfahren:
**tabellarisches Q-Learning** mit ε-greedy-Exploration und Bellman-Update.

## Starten

```bash
javac *.java
java Simulation
```

## Was passiert

Ein Schwarm von Autos teilt sich **eine** Q-Tabelle und sammelt parallel
Erfahrungen. Jedes Auto:

1. **Wahrnehmung** — 5 Sensor-Strahlen messen Abstand zur Wand
2. **State** — die Sensor-Werte werden in 3 Bins (nah/mittel/fern) diskretisiert
   → 3⁵ = 243 mögliche Zustände
3. **Aktion** — links / geradeaus / rechts (ε-greedy)
4. **Reward** — Checkpoint = +10, Ziel = +500, Crash = −100, Zeitstrafe = −0,05/Schritt
5. **Q-Update** — `Q(s,a) += α · (r + γ·max Q(s',·) − Q(s,a))`

Bei Crash, Ziel oder Timeout startet das Auto neu, ε wird etwas verkleinert
(weniger Erkundung, mehr Ausnutzung). Vergangene Versuche bleiben als
gefärbte Trails sichtbar (rot = Crash, orange = Timeout, grün = Ziel).

## Konfiguration

Alle Stellschrauben im Kopf der Klassen:

| Datei | Konstante | Bedeutung |
|---|---|---|
| `Simulation.java` | `N_CARS` | 1 = sequenziell, >1 = Schwarm |
| `Simulation.java` | `DECISION_INTERVAL` | Physikschritte zwischen zwei Entscheidungen |
| `Simulation.java` | `MAX_STEPS` | Timeout pro Episode |
| `Simulation.java` | `SLEEP_MS` | Frame-Pause (kleiner = schneller) |
| `QLearning.java` | `alpha` | Lernrate |
| `QLearning.java` | `gamma` | Discount für zukünftige Belohnungen |
| `QLearning.java` | `epsilonDecay` | wie schnell die Exploration zurückgefahren wird |
| `Car.java` | `TURN` | Lenkwinkel pro Entscheidung (Bogenmaß) |
| `Car.java` | `SPEED` | Pixel pro Physikschritt |

## Dateien

- `Track.java` — Strecke (Wandsegmente + Checkpoints + Ziellinie)
- `Car.java` — Auto mit Sensoren, Lenkung, State-Diskretisierung
- `QLearning.java` — Q-Tabelle, ε-greedy, Bellman-Update
- `Trail.java` — Datenklasse für vergangene Episoden
- `Simulation.java` — Trainings-Loop und Konfigurationsblock
- `Canvas.java` — Swing-Visualisierung

## Wann ist Q-Learning hier sinnvoll?

Wenn das Auto die Karte **nicht** kennt und nur Sensorwerte hat. Für eine
fest bekannte Strecke ist A\* oder ACO (siehe `../ACO`) deutlich effizienter
— Q-Learning ist hier didaktisch wertvoll, aber nicht das schnellste Werkzeug.
