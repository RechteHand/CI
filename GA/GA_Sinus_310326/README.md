# Genetischer Algorithmus — Maximierung von z(x) = x·sin(10πx) + 1

## Zielfunktion

Maximiere die Funktion:

```
z(x) = x · sin(10π · x) + 1,0
```

mit der Nebenbedingung: **x ∈ [-1, 2]**

---

## Grundidee

Ein Genetischer Algorithmus (GA) simuliert das Prinzip der natürlichen Evolution.
Eine Population von Individuen (= mögliche Lösungen) wird über viele Generationen hinweg durch **Selektion**, **Crossover** und **Mutation** verbessert, bis ein (nahezu) optimales Ergebnis gefunden wird.

### Codierung der Lösung

Da die Zielfunktion eine **reellwertige** Variable `x` erwartet, der GA aber intern mit **Bitstrings** (Listen aus 0en und 1en) arbeitet, muss eine Dekodierung stattfinden:

1. **Bitstring → Natürliche Zahl:** Der Bitstring wird als Binärzahl interpretiert.
   - Beispiel: `[1, 0, 1, 1]` → `1·2³ + 0·2² + 1·2¹ + 1·2⁰ = 11`

2. **Natürliche Zahl → Reelle Zahl x:** Normierung ins Intervall [-1, 2]:
   ```
   x = x_min + (zahl / (2^L - 1)) · (x_max - x_min)
   x = -1   + (zahl / (2^L - 1)) · 3
   ```
   Wobei `L` = Bitstring-Länge (hier: 22) und `2^L - 1` = größte darstellbare Zahl (alle Bits = 1).

### Beispiel mit L = 4

| Bitstring   | Zahl | x-Wert                      |
|-------------|------|------------------------------|
| `[0,0,0,0]` | 0    | -1 + 0/15 · 3 = **-1.0**    |
| `[1,0,0,0]` | 8    | -1 + 8/15 · 3 = **0.6**     |
| `[1,1,1,1]` | 15   | -1 + 15/15 · 3 = **2.0**    |

Mit **L = 22** Bits gibt es 2²² = 4.194.304 mögliche Werte → sehr feine Auflösung.

---

## Dateistruktur

| Datei            | Beschreibung                                                    |
|------------------|-----------------------------------------------------------------|
| `problem.py`     | Definiert Intervall, Bitstring-Länge, Dekodierung und Fitness   |
| `individual.py`  | Repräsentiert ein Individuum (Bitstring + Fitness)              |
| `ga.py`          | Hauptprogramm mit Populationsverwaltung und GA-Schleife        |

---

## Ablauf des Algorithmus

1. **Initialisierung:** 100 Individuen mit zufälligen Bitstrings erzeugen
2. **Fitness berechnen:** Jeden Bitstring dekodieren → x berechnen → z(x) auswerten
3. **Selektion:** Turnierselektion — 2 zufällige Individuen vergleichen, das mit dem **höheren** Fitness-Wert gewinnt
4. **Crossover:** Ein-Punkt-Crossover — Elternpaare erzeugen 2 Kinder
5. **Mutation:** Jedes Bit wird mit Wahrscheinlichkeit `1/L` geflippt
6. **Wiederholung:** Schritte 2–5 für 10.000 Generationen

---

## Umbau vom Standort-Problem zur Sinus-Maximierung

Der Code basierte ursprünglich auf einem **Facility Location Problem** (Standortoptimierung mit Fixkosten und Transportkosten). Folgende Änderungen wurden durchgeführt:

### Schritt 1–6: `problem.py`

1. **`import math` hinzugefügt** — wird für `math.sin` und `math.pi` benötigt
2. **Alte Klassen-Attribute entfernt** (`n`, `m`, `f`, `t`) — diese beschrieben das Standort-Problem (Anzahl Standorte, Kunden, Fixkosten, Transportkostenmatrix)
3. **Neue Attribute definiert:**
   - `bitstring_length = 22` — Länge der Bitstrings (bestimmt die Auflösung)
   - `x_min = -1` — untere Intervallgrenze
   - `x_max = 2` — obere Intervallgrenze
4. **`read_instance()` entfernt** — es gibt keine Problemdatei mehr einzulesen
5. **`print_cost()` entfernt** — keine Kosten mehr
6. **Neue Methode `decode()` geschrieben:**
   ```python
   @classmethod
   def decode(cls, gene: list[int]) -> float:
       zahl = 0
       for bit in gene:
           zahl = zahl * 2 + bit
       x = cls.x_min + zahl / (2**cls.bitstring_length - 1) * (cls.x_max - cls.x_min)
       return x
   ```
7. **`fitness()` komplett umgeschrieben:**
   ```python
   @classmethod
   def fitness(cls, gene: list[int]) -> float:
       x = cls.decode(gene)
       z = x * math.sin(10 * math.pi * x) + 1
       return z
   ```
   Statt Fixkosten + Transportkosten wird jetzt die Zielfunktion ausgewertet.

### Schritt 7–8: `individual.py`

7. **`__init__()` angepasst** — `problem_size` Parameter entfernt, stattdessen `Problem.bitstring_length` direkt verwendet:
   ```python
   def __init__(self):
       self.bits = [0] * Problem.bitstring_length
       self.fitness_value = 0
       self.p_mut = 1.0 / Problem.bitstring_length
   ```
8. **Sicherung in `initialize()` entfernt** — die Bedingung `if count == 0` (mindestens ein Bit muss 1 sein) war nur für das Standort-Problem nötig. Beim Sinus-Problem ist der Bitstring `[0,0,...,0]` ein gültiger x-Wert (= -1.0).

### Schritt 9–12: `ga.py`

9. **`Problem.read_instance(...)` entfernt** — keine Datei mehr einzulesen
10. **Alle `Individual(Problem.n)` Aufrufe zu `Individual()` geändert** — da `__init__` keinen Parameter mehr nimmt (4 Stellen: Zeilen 32, 41, 55, 56)
11. **Selektion umgedreht** (Minimierung → Maximierung):
    - `selection()`: `<` zu `>` geändert — bevorzugt jetzt das Individuum mit dem **höheren** Fitness-Wert
12. **`best_individual()` umgedreht:**
    - `<` zu `>` geändert — aktualisiert `best` nur noch wenn ein Individuum einen **höheren** Wert hat

---

## Ergebnis

```
Maximaler Fitness-Wert:  z ≈ 2.8503
Bester Bitstring:        1111001100111111001001
Entspricht ca.:          x ≈ 1.8506
```

Das theoretische Maximum von z(x) = x·sin(10πx) + 1 im Intervall [-1, 2] liegt bei ca. **2.85** bei x ≈ 1.85. Der GA findet dieses Optimum zuverlässig.

---

## Ausführung

```bash
python3 ga.py
```

Keine externen Abhängigkeiten nötig — nur Python-Standardbibliothek (`math`, `random`).
