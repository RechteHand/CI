# Genetischer Algorithmus: Inselmodell

Dieses Projekt implementiert einen Genetischen Algorithmus basierend auf dem **Inselmodell** in Python. 

## Grundidee
Anstatt einer einzigen großen Population werden mehrere unabhängige Populationen ("Inseln") simuliert. Jede Insel besitzt eine eigene Kultur (eigene Konfiguration für Selektion, Crossover und Mutation) und entwickelt sich eigenständig.

In festen Intervallen kommt es zu einer **Migration** (Austausch von Individuen zwischen den Inseln) und **Olympischen Spielen**, bei denen die Champions aller Inseln gegeneinander antreten. Zusätzlich gibt es Mechanismen zur Selbstanpassung wie Inselsterben und Wiedergeburt bei Stagnation.

## Starten des Projekts

1. Stelle sicher, dass Python installiert ist.
2. Führe das Hauptskript aus:
   ```bash
   python main.py
   ```
