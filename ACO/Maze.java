// Labyrinth als Gitter aus Zellen.
// '#' = Wand, sonst begehbar. Zusaetzlich: BFS-Distanzen zum Ziel als Heuristik.
import java.util.ArrayDeque;
import java.util.Arrays;

public class Maze {
    public static final int CELL_SIZE = 38;
    public static final int COLS      = 30;
    public static final int ROWS      = 13;

    public char[][]   grid       = new char[ROWS][COLS];
    public double[][] distToEnd  = new double[ROWS][COLS];   // Heuristik fuer ACO
    public int        startR = 1, startC = 1;
    public int        endR,   endC;

    public Maze(){
        // Alles erstmal Wand
        for(int r=0; r<ROWS; r++) for(int c=0; c<COLS; c++) grid[r][c] = '#';

        // Schlangen-Korridor: 4 Spuren a 2 Zeilen, dazwischen Wand mit Luecke abwechselnd rechts/links
        int LANES = 4, LANE_H = 2, GAP = 3;
        for(int i=0; i<LANES; i++){
            int rowStart = 1 + i*(LANE_H + 1);
            for(int r=rowStart; r<rowStart+LANE_H; r++)
                for(int c=1; c<COLS-1; c++)
                    grid[r][c] = '.';
        }
        for(int i=0; i<LANES-1; i++){
            int wallRow = 1 + i*(LANE_H + 1) + LANE_H;
            if(i % 2 == 0){
                for(int c=COLS-1-GAP; c<COLS-1; c++) grid[wallRow][c] = '.';   // Luecke rechts
            } else {
                for(int c=1; c<=GAP; c++)            grid[wallRow][c] = '.';   // Luecke links
            }
        }

        // Letzte Spur (index 3, ungerade -> Luecke war links) endet links
        endR = 1 + 3*(LANE_H + 1);
        endC = 1;

        grid[startR][startC] = 'S';
        grid[endR][endC]     = 'E';

        // Heuristik: Manhattan funktioniert hier auch, aber BFS-Distanz respektiert die Waende
        bfs(endR, endC);
    }

    private void bfs(int sr, int sc){
        for(double[] row : distToEnd) Arrays.fill(row, Double.MAX_VALUE);
        distToEnd[sr][sc] = 0;
        ArrayDeque<int[]> q = new ArrayDeque<>();
        q.add(new int[]{sr, sc});
        int[][] dirs = {{-1,0},{1,0},{0,-1},{0,1}};
        while(!q.isEmpty()){
            int[] cur = q.poll();
            for(int[] d : dirs){
                int nr = cur[0]+d[0], nc = cur[1]+d[1];
                if(nr<0||nr>=ROWS||nc<0||nc>=COLS) continue;
                if(grid[nr][nc] == '#')           continue;
                if(distToEnd[nr][nc] > distToEnd[cur[0]][cur[1]] + 1){
                    distToEnd[nr][nc] = distToEnd[cur[0]][cur[1]] + 1;
                    q.add(new int[]{nr, nc});
                }
            }
        }
    }

    public boolean isPath(int r, int c){
        if(r<0||r>=ROWS||c<0||c>=COLS) return false;
        return grid[r][c] != '#';
    }
}
