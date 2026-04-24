// Hauptloop: viele Ameisen laufen parallel. Jede die das Ziel erreicht, legt Pheromon ab.
// Nach jedem Frame verdunsten die Pheromone ein bisschen -> kurze Wege gewinnen sich
// gegenueber langen, weil sie haeufiger benutzt werden.
import java.util.ArrayList;
import javax.swing.JFrame;

public class Simulation extends JFrame implements Runnable {
    // ===========================================================
    //                     KONFIGURATION
    // ===========================================================
    static final int N_ANTS    = 25;     // wie viele Ameisen gleichzeitig laufen
    static final int SLEEP_MS  = 30;     // Pause pro Frame in ms
    static final int MAX_STEPS = 250;    // Timeout pro Ameise
    // ===========================================================

    Maze              maze = new Maze();
    ACO               aco  = new ACO();
    ArrayList<Ant>    ants = new ArrayList<>();

    int               completed     = 0;            // wie viele Ameisen insgesamt fertig sind
    int               successCount  = 0;
    int               bestPath      = Integer.MAX_VALUE;
    ArrayList<int[]>  bestPathCells = null;

    Canvas canvas;

    Simulation(){
        setTitle("ACO Maze - Ameisenalgorithmus  (N=" + N_ANTS + ")");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(null);
        for(int i=0; i<N_ANTS; i++) ants.add(new Ant(maze));

        int W = Maze.COLS * Maze.CELL_SIZE;
        int H = Maze.ROWS * Maze.CELL_SIZE + 90;
        canvas = new Canvas(maze, aco, ants, this);
        canvas.setBounds(0, 0, W, H);
        add(canvas);
        setSize(W + 20, H + 40);
        setVisible(true);
    }

    public static void main(String[] args){
        new Thread(new Simulation()).start();
    }

    public void run(){
        while(true){
            // 1) Alle lebenden Ameisen machen einen Schritt
            for(Ant a : ants) a.step(maze, aco);

            // 2) Fertige Ameisen ablegen / respawnen
            for(int i=0; i<ants.size(); i++){
                Ant a = ants.get(i);
                if(a.alive && a.steps >= MAX_STEPS){
                    a.alive = false;  a.outcome = 2;
                }
                if(!a.alive){
                    if(a.outcome == 1){
                        aco.deposit(a.path);
                        successCount++;
                        if(a.path.size() < bestPath){
                            bestPath      = a.path.size();
                            bestPathCells = new ArrayList<>(a.path);
                        }
                    }
                    ants.set(i, new Ant(maze));
                    completed++;
                }
            }

            // 3) Pheromone verdunsten
            aco.evaporate();

            try { Thread.sleep(SLEEP_MS); } catch(InterruptedException e) {}
            repaint();
        }
    }
}
