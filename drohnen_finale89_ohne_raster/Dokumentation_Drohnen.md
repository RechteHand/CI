# Detailbeschreibung: Drohnen-Lichtershow (FinaleSimulation)

Dieses Dokument bietet eine tiefgehende, detaillierte technische und mathematische Analyse der Drohnen-Lichtershow aus dem Projekt `drohnen_finale89`.

## 1. Ablauf der Programmausführung (Schaubild)

Der gesamte Ablauf des Programms lässt sich vom Start bis zur Darstellung auf dem Bildschirm wie folgt visualisieren:

```mermaid
flowchart TD
    Start([Start des Programms]) --> CheckArgs{Werden Argumente übergeben?}
    
    CheckArgs -- Nein (0 Argumente) --> DefaultConfig[Fallback auf historisches Ergebnis:\nSSC Napoli 2-1 VfB Stuttgart (1989)]
    CheckArgs -- Ja (< 7 Argumente) --> PrintUsage[Gebe Nutzungshinweise aus] --> Exit([Programm beenden])
    CheckArgs -- Ja (>= 7 Argumente) --> CustomConfig[Lade übergebene Argumente:\nLogos, Teams, Tore, Titel]
    
    DefaultConfig --> CreateTemplate[Erstelle unsichtbares Zielbild (Template) 1280x720]
    CustomConfig --> CreateTemplate
    
    CreateTemplate --> LoadLeft{Logo Links gefunden?}
    LoadLeft -- Ja --> DrawImageL[Zeichne Bild auf Template]
    LoadLeft -- Nein --> FallbackCircleL[Zeichne Fallback-Kreis mit 3-Buchstaben-Kürzel]
    
    DrawImageL --> LoadRight{Logo Rechts gefunden?}
    FallbackCircleL --> LoadRight
    
    LoadRight -- Ja --> DrawImageR[Zeichne Bild auf Template]
    LoadRight -- Nein --> FallbackCircleR[Zeichne Fallback-Kreis mit 3-Buchstaben-Kürzel]
    
    DrawImageR --> DrawText[Zeichne Texte: Titel, Untertitel, Ergebnis, Teams]
    FallbackCircleR --> DrawText
    
    DrawText --> SampleTemplate[Abtasten des Templates (Sampling)]
    
    SampleTemplate --> FilterAlpha[Filtere Pixel mit Alpha > 80]
    FilterAlpha --> CreateDrones[Erzeuge Drohnen-Objekte anhand der extrahierten Pixel]
    CreateDrones --> LaunchDelay[Ordne Drohnen zufällige Startverzögerungen (Launch Delay) zu]
    
    LaunchDelay --> StartSim[Starte Animations-Schleife (60 FPS)]
    
    StartSim --> UpdateDrones[Für jeden Frame:\nAktualisiere Drohnen-Positionen]
    UpdateDrones --> Separation[Abstand zu Nachbarn berechnen & ggf. abstoßen]
    Separation --> SignalCheck{Ist Drohne blockiert?}
    SignalCheck -- Ja (Speed<0.5 für >30 Frames) --> SendSignal[Notsignal senden & rot/orange pulsieren]
    SignalCheck -- Nein --> ReceiveSignal{Signal in der Nähe?}
    SendSignal --> ReceiveSignal
    ReceiveSignal -- Ja --> Evade[Ausweichvektor (Evade) berechnen um Platz zu machen]
    ReceiveSignal -- Nein --> Move[Steering Behavior anwenden & Bewegen]
    Evade --> Move
    Move --> DrawCanvas[Zeichne Nachthimmel, Feuerwerk und Drohnen]
    DrawCanvas --> UpdateDrones
```

## 2. Einlesen von Bildern und Schriften (Sampling)

Um die Drohnen so anzuordnen, dass sie Logos und Text bilden, generiert das Programm zunächst ein 2D-Bild (`BufferedImage`) im Speicher. Dieses Template-Bild ist für den Nutzer unsichtbar und dient nur als Blaupause.

### 2.1. Template-Konstruktion
Texte werden mit der Methode `fitFontSize` so lange verkleinert, bis sie in den definierten horizontalen Bereich (`maxWidth`) passen. Falls Logos nicht existieren, wird stattdessen über die Methode `drawFallbackCircle` ein farbiger Doppel-Kreis mit den ersten 3 Buchstaben des Vereinsnamens als Text gezeichnet.

### 2.2. Mathematisches Abtasten (Sampling) auf Pixelebene
Nachdem das Bild im Speicher (1280x720 Pixel) gerendert wurde, wird es abgetastet. Dies passiert in `sampleTemplate(BufferedImage img)`.

1. **Gitter-Iteration:** 
   Das Bild wird nicht Pixel für Pixel, sondern in einem festen Raster iteriert. Der Schrittweiten-Parameter ist `SAMPLE_STEP = 5`. Es wird also nur jeder 5. Pixel auf der x- und y-Achse überprüft. Dies definiert die Dichte der Drohnen.
   $x_i = i \times 5, \quad y_j = j \times 5$

2. **Farb- und Alpha-Extraktion (Bit-Shifting):**
   Für jeden abgetasteten Pixel $(px, py)$ wird der 32-Bit Integer-Farbwert (RGBA) ausgelesen.
   Mithilfe von Bit-Shifting und Bitweiser-UND-Verknüpfung (`& 0xFF`) werden die Kanäle isoliert:
   $$Alpha = (rgba \gg 24) \wedge 255$$
   $$Red = (rgba \gg 16) \wedge 255$$
   $$Green = (rgba \gg 8) \wedge 255$$
   $$Blue = rgba \wedge 255$$

3. **Schwellenwert-Filterung:**
   Nur wenn $Alpha > 80$ ist, wird der Pixel als "sichtbar" erachtet. Für jeden solchen Pixel wird eine Drohne generiert, deren Zielposition genau $(px, py)$ ist.
   *Sonderregel:* Ist der Pixel fast schwarz ($R, G, B < 30$), wird er künstlich auf eine grau-blaue Farbe $(50, 50, 70)$ aufgehellt, da er sonst am Nachthimmel nicht sichtbar wäre.

## 3. Bildkonstruktion & Drohnenflug (Einfach erklärt & Vektor-Mathematik)

### Wie wissen die Drohnen, wohin sie müssen? (Einfach erklärt)
Stell dir vor, das Programm zeichnet das komplette Zielbild (Logos und Texte) zunächst virtuell und unsichtbar im Hintergrund. Dann legt es ein Raster darüber. Für jeden Rasterpunkt, der nicht durchsichtig ist (also dort, wo das Logo/der Text ist), wird genau **eine** Drohne erzeugt. 
Dieser spezifische Punkt auf dem Raster wird als festes Ziel (`targetX` und `targetY`) in der Drohne gespeichert. Jede Drohne weiß also von der ersten Sekunde an ihr exaktes Ziel und fliegt stur nur dorthin.

### Die Mathematik dahinter (Arrival Steering Behavior)
Der Kern der Simulation ist die Physik-Engine. Jede Drohne berechnet ihren Weg dorthin selbständig. Hierfür wird das **Arrival Steering Behavior** (nach Craig Reynolds) verwendet, welches dafür sorgt, dass die Drohne am Anfang schnell fliegt und weich abbremst, sobald sie dem Ziel nahe kommt.

### 3.1. Initialisierung
Jede Drohne erhält bei ihrer Erstellung einen leichten zufälligen Offset auf der x-Achse von ihrem Ziel und startet am unteren Bildschirmrand:
$$x_{start} = targetX + (Random(-0.5, 0.5) \times 300)$$
$$y_{start} = Höhe + 20 + Random(0, 80)$$
Der initiale Geschwindigkeitsvektor $\vec{v} = (vx, vy)$ ist sehr klein und zufällig nach oben gerichtet.

### 3.2. Vektorberechnungen pro Frame (Arrival Behavior)
In jedem Frame wird der Vektor von der aktuellen Position $(x,y)$ zur Zielposition $(targetX, targetY)$ ermittelt.

1. **Distanzvektor und euklidische Distanz:**
   $$dx = targetX - x$$
   $$dy = targetY - y$$
   $$dist = \sqrt{dx^2 + dy^2}$$

2. **Gewünschte Geschwindigkeit (Desired Speed):**
   Abhängig davon, wie weit die Drohne vom Ziel entfernt ist, wird sie abgebremst. Der Radius hierfür ist `arrivalRadius = 100.0`.
   - **Wenn $\text{dist} < \text{arrivalRadius}$:**
     $$desiredSpeed = maxSpeed \times \left(\frac{dist}{arrivalRadius}\right)$$
   - **Sonst:**
     $$desiredSpeed = maxSpeed \quad (\text{wobei } maxSpeed = 3.5)$$

3. **Gewünschter Geschwindigkeitsvektor ($\vec{v}_{desired}$):**
   Der Vektor wird normalisiert und mit $desiredSpeed$ multipliziert:
   $$desiredVx = \left(\frac{dx}{dist}\right) \times desiredSpeed$$
   $$desiredVy = \left(\frac{dy}{dist}\right) \times desiredSpeed$$

4. **Steuerkraftvektor ($\vec{F}_{steer}$):**
   Dies ist die Kraft, die benötigt wird, um die aktuelle Geschwindigkeit auf die gewünschte Geschwindigkeit zu korrigieren.
   $$steerX = desiredVx - vx$$
   $$steerY = desiredVy - vy$$
   Die Länge dieses Vektors wird auf die maximale Manövrierfähigkeit `maxForce = 0.12` begrenzt:
   $$steerMag = \sqrt{steerX^2 + steerY^2}$$
   Wenn $steerMag > maxForce$:
   $$steerX = \left(\frac{steerX}{steerMag}\right) \times maxForce$$
   $$steerY = \left(\frac{steerY}{steerMag}\right) \times maxForce$$

5. **Integration (Euler-Integration):**
   Die Kraft wird auf die Geschwindigkeit addiert, die Geschwindigkeit dann auf die maximale Fluggeschwindigkeit $maxSpeed$ begrenzt und schließlich auf die Position addiert.
   $$vx = vx + steerX$$
   $$vy = vy + steerY$$
   $$x = x + vx$$
   $$y = y + vy$$

### 3.3. Zielfindung (Arrival)
Wenn die Distanz sehr klein wird ($dist < 0.8$), gilt die Drohne als angekommen. Ihre Rest-Geschwindigkeit wird stark gedämpft (Reibung von $85\%$) und mit minimalem Rauschen (Brownsche Bewegung) versehen, damit die Drohnen am Ziel leicht auf der Stelle "schweben".
$$vx = vx \times 0.85 + Random(-0.01, 0.01)$$
$$vy = vy \times 0.85 + Random(-0.01, 0.01)$$

### 3.4. Optische Konstruktion & Shimmer-Effekt
Während des Fluges wird die Farbe der Drohne interpoliert (von warmweiß beim Start zur Zielfarbe).
Wenn die Drohne das Ziel erreicht, fängt sie an zu pulsieren (Shimmer-Phase). Die Helligkeit folgt einer Sinuswelle, die mit einer zufälligen Phase (`shimmerPhase`) pro Drohne versehen ist, damit sie asynchron funkeln:
$$shimmer = 0.88 + 0.12 \times \sin(frame \times 0.04 + shimmerPhase)$$
Jeder Farbkanal (R,G,B) wird am Ziel mit diesem Faktor multipliziert.
Zusätzlich zeichnet die `SkyCanvas` die Drohnen in zwei Schichten: Einem leicht transparenten Glow (großer Radius, niedriger Alpha-Wert) und einem harten Kern (kleiner Radius, voller RGB-Wert), was die additive Lichtmischung des Leuchtens simuliert.

### 3.5. Kollisionsvermeidung & Platz machen (Separation & Signaling)
Damit die Drohnen in der Luft nicht kollidieren und sich gegenseitig blockieren, wurde eine **Kollisionsvermeidung (Separation)** und eine **Signal-Mechanik (Evade/Yield)** integriert. Jede Drohne kennt die Positionen aller anderen Drohnen.

#### A. Separation (Abstoßung) & Fade-Out
Wenn eine Drohne einer anderen zu nahe kommt (Distanz $d < COLLISION\_RADIUS$, z.B. 3.5 Pixel), berechnet sie einen Abstoßungsvektor. Die Stärke der Abstoßung ist umgekehrt proportional zur Distanz:
$$ \vec{F}_{sep} = \frac{\vec{pos}_{this} - \vec{pos}_{other}}{d^2} $$
Um ein endloses "Zittern" (Oszillation) in Zielnähe zu vermeiden, wurde ein dynamischer **Fade-Out** integriert. Die maximale Abstoßungskraft ($maxForce \times 2.5$) wird linear weicher, je näher die Drohne ihrem Ziel kommt ($distToTarget < 25$ Pixel). Wenn die Drohne unter 2.5 Pixel von ihrem Ziel entfernt ist, bremst sie extrem ab, rastet sanft auf die exakten Koordinaten ein (Snap-to-Target) und schaltet die Abstoßung zu anderen geparkten Drohnen ab (`arrived = true`).

#### B. Blockade-Erkennung & "Platz machen"-Signal
Wenn eine Drohne noch weit von ihrem Ziel entfernt ist ($dist > 5.0$), aber ihre Fluggeschwindigkeit extrem gering wird ($speed < 0.5$), bedeutet dies, dass sie von anderen Drohnen versperrt wird (z.B. durch Drohnen, die schon an ihrem Ziel stehen). 
1. **Signal senden:** Wenn sie länger als 30 Frames in diesem Zustand ("stuck") feststeckt, fängt sie an, ein Notsignal (`broadcastingSignal = true`) zu senden. Optisch pulsiert die blockierte Drohne dann in einem auffälligen Rot-Orange.
2. **Signal empfangen (Ausweichen):** Jede Drohne im Umkreis von $SIGNAL\_RADIUS$ (12 Pixel) prüft, ob eine benachbarte Drohne dieses Signal sendet. Wenn ja, berechnet sie einen starken Flucht-Vektor (Evade-Force), um **aktiv Platz zu machen**:
$$ \vec{F}_{evade} = \left(\frac{\vec{pos}_{this} - \vec{pos}_{broadcaster}}{d}\right) \times 2.5 $$
Das Besondere hierbei: Auch Drohnen, die bereits fest an ihrem Ziel geparkt haben (`arrived = true`), "wachen" bei diesem Notsignal sofort wieder auf (`arrived = false`). Sie lassen sich von dem Ausweich-Vektor temporär wegschieben, sodass eine Lücke entsteht. Sobald die blockierte Drohne durchgeflogen ist und nicht mehr feststeckt, erlischt das Signal, und die ausweichenden Drohnen fliegen sanft wieder auf ihre ursprünglichen Rasterplätze zurück.
