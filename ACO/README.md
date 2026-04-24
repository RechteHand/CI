# ACO — Ant Colony Optimization

Schwarm aus Ameisen sucht den kürzesten Weg durch ein Gitter-Labyrinth.
Das Verfahren ist ein klassischer **Ameisenalgorithmus** (Marco Dorigo, 1992):
gute Wege werden über Pheromonspuren von der gesamten Kolonie verstärkt.

## Starten

```bash
javac *.java
java Simulation
```

## Was passiert

Auf jeder Zelle des Labyrinths liegt eine **Pheromonmenge** τ.
Pro Schritt wählt eine Ameise einen unbesuchten Nachbarn nach
Wahrscheinlichkeit:

```
p_i  ∝  τ_i^α  ·  η_i^β
```

- **τ** = Pheromon der Zelle (lernt sich über die Zeit)
- **η** = `1 / Distanz_zum_Ziel` — Heuristik, vorab per BFS berechnet
- **α** = wie stark wird das Pheromon gewichtet
- **β** = wie stark zieht das Ziel

Erreicht eine Ameise das Ziel, **legt sie Pheromon ab** auf allen Zellen
ihres Pfades — `Q / Pfadlänge`, kürzer = mehr Pheromon. Pro Frame
**verdunstet** ein Anteil ρ des Pheromons überall. Dadurch konvergiert
der Schwarm sichtbar auf den kürzesten Pfad.

Die Heatmap zeigt das Pheromon (weiß = wenig, rot = viel), die blaue
Linie den bisher kürzesten gefundenen Weg.

## Konfiguration

Alle Algorithmus-Parameter in `ACO.java`:

| Konstante | Bedeutung |
|---|---|
| `alpha` | Gewicht des Pheromons (höher → Ameisen folgen Spuren stärker) |
| `beta`  | Gewicht der Heuristik (höher → direkter Richtung Ziel) |
| `rho`   | Verdunstung pro Frame (höher → vergisst altes schneller) |
| `Q`     | Pheromon-Menge pro erfolgreichem Pfad |
| `tau0`  | Anfangs-Pheromon auf allen Zellen |

Simulations-Parameter in `Simulation.java`:

| Konstante | Bedeutung |
|---|---|
| `N_ANTS`    | wie viele Ameisen gleichzeitig laufen |
| `MAX_STEPS` | Timeout pro Ameise |
| `SLEEP_MS`  | Frame-Pause (kleiner = schneller) |

## Dateien

- `Maze.java` — Gitter-Labyrinth, BFS-Distanzen als Heuristik
- `Ant.java` — eine Ameise: gewichtete Auswahl der Nachbarn
- `ACO.java` — Pheromon-Tabelle, `evaporate()`, `deposit()`, alle Parameter
- `Simulation.java` — Hauptloop, Konfigurationsblock
- `Canvas.java` — Heatmap + Ameisen + bester Pfad

## Wann ist ACO sinnvoll?

Für **bekannte** Wegfindungs-Probleme (Labyrinth, TSP, Routenplanung)
mit sehr vielen Knoten/Kanten. ACO ist meist langsamer als A\*, dafür
sehr anschaulich (man sieht die Spur entstehen), gut parallelisierbar
und robust gegen sich ändernde Karten.

Vergleich zu RL (siehe `../RL`): ACO weiß die Karte und kann das
gemeinsame Wissen direkt über Pheromone teilen — konvergiert dadurch
in Sekunden statt Minuten.
