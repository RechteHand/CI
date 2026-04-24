// Eine Ameise: laeuft Zelle fuer Zelle, vermeidet bereits besuchte Zellen
// (sonst wuerde sie im Kreis laufen). Endet bei: Ziel erreicht / festgelaufen / Timeout.
import java.util.ArrayList;

public class Ant {
    public int     r, c;
    public ArrayList<int[]> path = new ArrayList<>();
    public boolean[][]      visited = new boolean[Maze.ROWS][Maze.COLS];
    public boolean alive    = true;
    public int     outcome  = -1;      // 0 = festgelaufen, 1 = Ziel erreicht, 2 = Timeout
    public int     steps    = 0;

    public Ant(Maze maze){
        r = maze.startR;  c = maze.startC;
        visited[r][c] = true;
        path.add(new int[]{r, c});
    }

    // Ein Schritt: Nachbarn anschauen, gewichtet sampeln, hingehen
    public void step(Maze maze, ACO aco){
        if(!alive) return;

        // 1) gueltige Nachbarn (4-zusammenhaengend, Pfadzelle, noch nicht besucht)
        int[][] dirs = {{-1,0},{1,0},{0,-1},{0,1}};
        ArrayList<int[]> cand = new ArrayList<>();
        for(int[] d : dirs){
            int nr = r + d[0], nc = c + d[1];
            if(!maze.isPath(nr, nc)) continue;
            if(visited[nr][nc])      continue;
            cand.add(new int[]{nr, nc});
        }
        if(cand.isEmpty()){
            alive = false;  outcome = 0;     // Sackgasse
            return;
        }

        // 2) Wahrscheinlichkeiten: tau^alpha * eta^beta
        double[] w = new double[cand.size()];
        double sum = 0;
        for(int i=0; i<cand.size(); i++){
            int[] cell = cand.get(i);
            double tau  = aco.pheromone[cell[0]][cell[1]];
            double dist = maze.distToEnd[cell[0]][cell[1]];
            double eta  = 1.0 / (dist + 0.1);
            w[i] = Math.pow(tau, aco.alpha) * Math.pow(eta, aco.beta);
            sum += w[i];
        }

        // 3) Roulette-Wheel-Auswahl
        double pick = aco.rnd.nextDouble() * sum;
        int chosen = cand.size() - 1;
        double acc = 0;
        for(int i=0; i<w.length; i++){
            acc += w[i];
            if(pick <= acc){ chosen = i; break; }
        }

        // 4) Bewegen
        int[] next = cand.get(chosen);
        r = next[0];  c = next[1];
        visited[r][c] = true;
        path.add(new int[]{r, c});
        steps++;

        // 5) Ziel erreicht?
        if(r == maze.endR && c == maze.endC){
            alive = false;  outcome = 1;
        }
    }
}
