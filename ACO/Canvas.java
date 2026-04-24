// Visualisierung: Pheromon-Heatmap als Hintergrund + Ameisen + bisher bester Pfad.
import java.awt.*;
import java.util.ArrayList;
import javax.swing.JPanel;

public class Canvas extends JPanel {
    Maze            maze;
    ACO             aco;
    ArrayList<Ant>  ants;
    Simulation      sim;

    Canvas(Maze maze, ACO aco, ArrayList<Ant> ants, Simulation sim){
        this.maze = maze;  this.aco = aco;  this.ants = ants;  this.sim = sim;
        setBackground(Color.WHITE);
    }

    public void paintComponent(Graphics g){
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int CS = Maze.CELL_SIZE;

        // --- Zellen: Wand schwarz, Pfad mit Pheromon-Farbe ---
        double max = aco.maxPheromone;
        for(int r=0; r<Maze.ROWS; r++){
            for(int c=0; c<Maze.COLS; c++){
                if(maze.grid[r][c] == '#'){
                    g2.setColor(new Color(45, 45, 45));
                } else {
                    double t = Math.min(1.0, Math.log(1 + aco.pheromone[r][c]) / Math.log(1 + max));
                    g2.setColor(pheromoneColor(t));
                }
                g2.fillRect(c*CS, r*CS, CS, CS);
            }
        }

        // Gitterlinien
        g2.setColor(new Color(220, 220, 220));
        for(int r=0; r<=Maze.ROWS; r++) g2.drawLine(0,    r*CS, Maze.COLS*CS, r*CS);
        for(int c=0; c<=Maze.COLS; c++) g2.drawLine(c*CS, 0,    c*CS,         Maze.ROWS*CS);

        // --- Bisher bester Pfad als blaue Linie ---
        if(sim.bestPathCells != null){
            g2.setColor(new Color(20, 80, 220, 220));
            g2.setStroke(new BasicStroke(3));
            for(int i=1; i<sim.bestPathCells.size(); i++){
                int[] p1 = sim.bestPathCells.get(i-1);
                int[] p2 = sim.bestPathCells.get(i);
                g2.drawLine(p1[1]*CS + CS/2, p1[0]*CS + CS/2,
                            p2[1]*CS + CS/2, p2[0]*CS + CS/2);
            }
            g2.setStroke(new BasicStroke(1));
        }

        // --- Start (gruen) und Ziel (rot) ---
        g2.setColor(new Color(0, 160, 0));
        g2.fillOval(maze.startC*CS + CS/4, maze.startR*CS + CS/4, CS/2, CS/2);
        g2.setColor(new Color(200, 0, 0));
        g2.fillOval(maze.endC*CS + CS/4,   maze.endR*CS + CS/4,   CS/2, CS/2);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.BOLD, 11));
        g2.drawString("S", maze.startC*CS + CS/2 - 4, maze.startR*CS + CS/2 + 4);
        g2.drawString("E", maze.endC*CS   + CS/2 - 4, maze.endR*CS   + CS/2 + 4);

        // --- Ameisen ---
        g2.setColor(new Color(20, 20, 20));
        for(Ant ant : ants){
            if(!ant.alive) continue;
            int x = ant.c*CS + CS/2;
            int y = ant.r*CS + CS/2;
            g2.fillOval(x-4, y-4, 8, 8);
        }

        // --- Statistik unter dem Labyrinth ---
        g2.setColor(Color.BLACK);
        g2.setFont(new Font("Monospaced", Font.PLAIN, 13));
        int sy = Maze.ROWS * CS + 22;
        g2.drawString(String.format("Ameisen fertig:  %d", sim.completed),                    20, sy);
        g2.drawString(String.format("Erfolge:         %d", sim.successCount),                 20, sy+18);
        g2.drawString(String.format("Bester Weg:      %s",
            sim.bestPath == Integer.MAX_VALUE ? "-" : (sim.bestPath + " Zellen")),            20, sy+36);
        g2.drawString(String.format("Max Pheromon:    %.2f", aco.maxPheromone),              420, sy);
        g2.drawString(String.format("alpha=%.1f  beta=%.1f  rho=%.3f  Q=%.0f",
            aco.alpha, aco.beta, aco.rho, aco.Q),                                            420, sy+18);
        g2.drawString("Heatmap: weiss = wenig, rot = viel Pheromon",                         420, sy+36);
    }

    // Farbverlauf weiss -> gelb -> orange -> rot, abhaengig von t in [0,1]
    private Color pheromoneColor(double t){
        if(t < 0.33){
            float f = (float)(t / 0.33);
            return new Color(255, 255, (int)(255*(1-f)));
        } else if(t < 0.66){
            float f = (float)((t - 0.33) / 0.33);
            return new Color(255, (int)(255 - 100*f), 0);
        } else {
            float f = (float)((t - 0.66) / 0.34);
            return new Color(255, (int)(155 * (1-f)), 0);
        }
    }
}
