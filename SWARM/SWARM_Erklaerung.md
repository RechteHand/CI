# Das SWARM-Projekt – einfach erklärt

Das Projekt simuliert **Schwarmverhalten** – also wie sich Vögel, Fische oder Fahrzeuge in einer Gruppe bewegen, ohne dass einer der Chef ist. Jedes "Fahrzeug" entscheidet nur nach **einfachen Regeln mit seinen Nachbarn**, und trotzdem entsteht ein schwarmartiges Gesamtverhalten.

## Das Ziel dieser Variante

Der Schwarm soll dem **Anführer (rotem Punkt)** folgen und dabei **innerhalb des roten Kreises** (Radius `rad_zus`) bleiben. Innerhalb des Kreises übernehmen die klassischen Boids-Regeln, außerhalb zieht es die Fahrzeuge zurück zum Anführer.

## Die 3 Dateien

### [Simulation.java](Simulation.java) – der Motor

- Öffnet ein Fenster (1000×800) und erstellt 100 Fahrzeuge ([Simulation.java:11](Simulation.java#L11))
- Eines davon ist der "Anführer" (rot, `type=1`), alle anderen sind Verfolger ([Simulation.java:24](Simulation.java#L24))
- In einer Endlosschleife passiert pro Tick:
  1. Jedes Fahrzeug überlegt, wohin es will (`steuerparameter_festlegen`)
  2. Jedes Fahrzeug rechnet seinen nächsten Schritt aus (`steuern`)
  3. Alle bewegen sich gleichzeitig (`bewegen`)
  4. Neu zeichnen ([Simulation.java:74-88](Simulation.java#L74-L88))

### [Vehicle.java](Vehicle.java) – das Gehirn eines Fahrzeugs

Jedes Fahrzeug hat Position, Geschwindigkeit, Beschleunigung (als 2D-Vektoren) und zwei Wahrnehmungsradien:

- `rad_sep = 5` → Abstandsradius (zu nah? → wegdrücken)
- `rad_zus = 30` → Gruppenradius / **roter Kreis** um den Anführer

Die Schwarmregeln sind klassische **Boids-Regeln** von Craig Reynolds, erweitert um ein Folgeverhalten:

| Methode | Bedeutung |
|---|---|
| `zusammenbleiben()` ([Vehicle.java:114](Vehicle.java#L114)) | **Kohäsion** – steuere zum Mittelpunkt deiner Nachbarn innerhalb `rad_zus` |
| `separieren()` ([Vehicle.java:161](Vehicle.java#L161)) | **Separation** – halte Mindestabstand `rad_sep`, dränge dich weg |
| `ausrichten()` ([Vehicle.java:213](Vehicle.java#L213)) | **Alignment** – gleiche dich an die durchschnittliche Geschwindigkeit der Nachbarn an |
| `folgen()` ([Vehicle.java:76](Vehicle.java#L76)) | Verfolger zieht zum Anführer, **nur wenn außerhalb** des roten Kreises |
| `zufall()` ([Vehicle.java:223](Vehicle.java#L223)) | Der Anführer bewegt sich zufällig (Random-Walk) |

#### Wie `ausrichten()` funktioniert

1. Alle Nachbarn innerhalb `rad_zus` einsammeln.
2. Deren Geschwindigkeitsvektoren mitteln.
3. Den Mittelwert auf `max_vel` skalieren → Zielgeschwindigkeit.
4. Differenz zur eigenen Geschwindigkeit ergibt die Zielbeschleunigung.

#### Wie `folgen()` funktioniert

1. Nur Verfolger (`type == 0`) rechnen; der Anführer wird gesucht.
2. Entfernung zum Anführer berechnen.
3. Ist die Entfernung **größer** als `rad_zus` (außerhalb des roten Kreises), wird ein Geschwindigkeitsvektor in Richtung Anführer erzeugt und auf `max_vel` skaliert.
4. Innerhalb des Kreises gibt `folgen()` **Null** zurück – dort greifen nur Kohäsion, Separation und Alignment.

Das Ergebnis: Fahrzeuge werden von außen zurück in den Kreis gezogen und schwärmen innerhalb frei.

#### Die Überlagerung

Die vier Kräfte werden **gewichtet addiert** ([Vehicle.java:244-263](Vehicle.java#L244-L263)):

```
acc_dest = f1 * zusammenbleiben
         + f2 * separieren
         + f3 * ausrichten
         + f4 * folgen

mit f1 = 0.2, f2 = 0.4, f3 = 0.4, f4 = 0.8
```

- `f4` ist absichtlich höher, damit der Rückholeffekt zum Anführer dominiert, sobald ein Fahrzeug den Kreis verlässt.
- Innerhalb des Kreises ist `folgen = 0`, dort balancieren sich die drei Boids-Kräfte aus.

Anschließend wird die Beschleunigung auf `max_acc` gekappt, die Geschwindigkeit auf `max_vel`, und bei Wand-Kontakt abgeprallt ([Vehicle.java:286-301](Vehicle.java#L286-L301)).

Wichtig: Es wird **erst alles neu berechnet (`pos_new`), dann übernommen (`bewegen`)** – so sieht jedes Fahrzeug den gleichen Zustand, egal in welcher Reihenfolge gerechnet wird.

### [Canvas.java](Canvas.java) – die Anzeige

- Zeichnet jedes Fahrzeug als Rechteck, das in Fahrtrichtung gedreht wird ([Canvas.java:21-46](Canvas.java#L21-L46))
- Der Anführer bekommt zwei Kreise dazu: den großen **roten Kreis** (`rad_zus`) als Aufenthaltsbereich und den kleinen (`rad_sep`) als Mindestabstand ([Canvas.java:73-79](Canvas.java#L73-L79))

## Parameter zum Experimentieren

| Parameter | Wirkung bei Erhöhung |
|---|---|
| `f1` (Kohäsion) | Schwarm zieht sich stärker zusammen |
| `f2` (Separation) | Fahrzeuge halten größeren Abstand |
| `f3` (Alignment) | Gleichgerichtete Bewegung, wirkt "fließender" |
| `f4` (Folgen) | Schwarm klebt enger am Anführer / reagiert schneller wenn er den Kreis verlässt |
| `rad_zus` | Roter Kreis wird größer, Schwarm darf sich weiter verteilen |
| `rad_sep` | Mindestabstand zwischen Fahrzeugen wächst |
| `max_vel` / `max_acc` | Tempo bzw. Reaktionsschnelligkeit |

## Die Kernidee in einem Satz

Aus **vier simplen lokalen Regeln** pro Fahrzeug (Abstand halten, zur Gruppe hin, Richtung angleichen, zum Anführer zurück) entsteht global ein **Schwarmverhalten**, das dem roten Punkt folgt und innerhalb des roten Kreises zusammenhält – ohne zentrale Steuerung.
