# Dokumentation: Drohnen Schwarm (Arrival Behavior Simulation)

Dieses Modul simuliert die Choreografie eines Drohnenschwarms, der sich zu spezifischen grafischen Formationen am Himmel anordnet. Das System parst Bilddateien und nutzt Steering Behaviors, um autonome Agenten (Drohnen) zielgerichtet, fließend und organisch an ihre Zielkoordinaten zu steuern.

## 1. Programmaufruf und Parameter

Die Klasse `FinaleSimulation` ermöglicht es, eigene Bilder (im PNG- oder JPG-Format) und Texte über die Kommandozeilenargumente zu übergeben. Die Argumente müssen in dieser strikten Reihenfolge vorliegen:

```bash
java FinaleSimulation <Logo_Links_Pfad> <Logo_Rechts_Pfad> <Team_Links> <Team_Rechts> <Score_Links> <Score_Rechts> <Titel>
```

**Beispielaufruf:**
```bash
java FinaleSimulation "bayern.png" "napoli.png" "FC BAYERN" "SSC NAPOLI" "1" "1" "CHAMPIONS LEAGUE 2025"
```
Wird das Programm ohne Argumente aufgerufen, lädt es den Standarddatensatz (Napoli vs. VfB Stuttgart 1989). Wenn eine Bilddatei nicht im Verzeichnis gefunden wird, zeichnet die Engine als Fallback einen farbigen Kreis mit dem Namenskürzel.

## 2. Konfigurationsparameter (Quellcode)

Die wesentlichen Parameter der Engine können direkt in den Quelldateien angepasst werden.

**FinaleSimulation.java:**
- `WIDTH = 1280`, `HEIGHT = 720`: Festgelegte Auflösung des Fensters.
- `TARGET_FPS = 60`: Ziel-Bildwiederholrate der Hauptschleife.
- `SAMPLE_STEP = 5`: Bestimmt die Dichte der Drohnen. Nur jeder fünfte Pixel des Originalbildes wird als Drohne instanziiert.
- `alphaThreshold = 80`: Nur Bildpixel mit einem Alpha-Wert (Deckkraft) über 80 erzeugen eine Zielkoordinate. Vollkommen transparente Pixel werden ignoriert.

**Drohne.java:**
- `maxSpeed = 3.5`: Maximale Fluggeschwindigkeit in Pixel pro Frame.
- `maxForce = 0.12`: Maximale Beschleunigung/Lenkkraft pro Frame. Niedrige Werte sorgen für ein sanfteres Flugverhalten (Trägheit).
- `arrivalRadius = 100.0`: Der Radius um die Zielkoordinate, ab dem die Drohne mit dem Abbremsen beginnt.

## 3. Mathematische Modelle der Agenten

Die Logik zur Steuerung der Agenten befindet sich in der Klasse `Drohne.java`. Das Flugverhalten basiert auf dem Arrival-Steering nach Craig Reynolds.

### 3.1 Vektorberechnung und Abbremsung (Arrival)
In der `update(int frame)`-Methode wird der Euklidische Abstand zum Ziel berechnet:
`dist = sqrt((targetX - x)^2 + (targetY - y)^2)`

Anhand dieser Distanz wird die Zieldrehzahl (`desiredSpeed`) gedrosselt, falls sich die Drohne innerhalb des `arrivalRadius` befindet:
```java
if (dist < arrivalRadius) {
    desiredSpeed = maxSpeed * (dist / arrivalRadius);
} else {
    desiredSpeed = maxSpeed;
}
```

### 3.2 Krafteinwirkung und Begrenzung
Der resultierende Steuerungsvektor (Steer) ist die Differenz zwischen gewünschter und aktueller Geschwindigkeit:
`steer_x = desired_vx - vx`
`steer_y = desired_vy - vy`

Um unnatürlich abrupte Wendemanöver zu vermeiden, wird der Steer-Vektor auf `maxForce` limitiert:
`steerMag = sqrt(steerX^2 + steerY^2)`
Ist `steerMag > maxForce`, wird der Vektor normiert und mit `maxForce` multipliziert. Die finale Geschwindigkeit ist auf `maxSpeed` limitiert.

### 3.3 Visuelle Effekte (Shimmering)
Befindet sich die Drohne am Ziel (`dist < 0.8`), stoppt sie fast vollständig (`vx *= 0.85`). Um die Illusion von leuchtenden Dioden am Himmel aufrechtzuerhalten, wird der RGB-Farbwert per Sinus-Funktion dynamisch moduliert:
```java
double shimmer = 0.88 + 0.12 * Math.sin(frame * 0.04 + shimmerPhase);
r = (int) (r * shimmer);
```
Die `shimmerPhase` wird bei der Instanziierung per `Math.random() * Math.PI * 2` initialisiert, damit die Drohnen nicht synchron, sondern asynchron blinken.
