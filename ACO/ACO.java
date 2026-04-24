// Pheromon-Tabelle + Parameter des Ameisenalgorithmus.
// Auswahl-Wahrscheinlichkeit fuer eine Nachbarzelle:
//     p_i  ~  tau_i^alpha  *  eta_i^beta
//   tau = Pheromon der Zelle, eta = 1 / Distanz_zum_Ziel (Heuristik)
import java.util.ArrayList;
import java.util.Random;

public class ACO {
    public double alpha = 1.0;     // Gewicht des Pheromons
    public double beta  = 2.5;     // Gewicht der Heuristik (groesser = direkter Richtung Ziel)
    public double rho   = 0.004;   // Verdunstungsrate pro Frame (groesser = altes vergisst schneller)
    public double Q     = 80;      // Pheromon-Menge die ein erfolgreicher Pfad ablegt
    public double tau0  = 1.0;     // Anfangs-Pheromon (gleich auf allen Zellen)

    public double[][] pheromone = new double[Maze.ROWS][Maze.COLS];
    public double     maxPheromone = 1.0;          // fuer die Heatmap-Skalierung
    public Random     rnd = new Random();

    public ACO(){
        for(int r=0; r<Maze.ROWS; r++)
            for(int c=0; c<Maze.COLS; c++)
                pheromone[r][c] = tau0;
    }

    // Pheromone "altern" - jeden Frame ein bisschen weniger
    public void evaporate(){
        double m = 0;
        for(int r=0; r<Maze.ROWS; r++)
            for(int c=0; c<Maze.COLS; c++){
                pheromone[r][c] *= (1 - rho);
                if(pheromone[r][c] > m) m = pheromone[r][c];
            }
        maxPheromone = Math.max(m, 1.0);
    }

    // Erfolgreicher Pfad legt Pheromon ab - kuerzer Pfad = mehr Pheromon pro Zelle
    public void deposit(ArrayList<int[]> path){
        double amount = Q / path.size();
        for(int[] cell : path)
            pheromone[cell[0]][cell[1]] += amount;
    }
}
