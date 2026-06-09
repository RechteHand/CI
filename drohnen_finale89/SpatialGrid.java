import java.util.ArrayList;

/**
 * Spatial Partitioning System (Grid) fuer Kollisionserkennung in O(N).
 * Teilt die 2D Welt in Zellen ein, um nicht jede Drohne mit jeder anderen vergleichen zu muessen.
 */
public class SpatialGrid {
    private int cols;
    private int rows;
    private int cellSize;
    private ArrayList<Drohne>[][] cells;
    
    // Offset, um negative Koordinaten zu verarbeiten, ohne ArrayOutOfBounds zu bekommen
    private static final int OFFSET = 500;

    @SuppressWarnings("unchecked")
    public SpatialGrid(int width, int height, int cellSize) {
        this.cellSize = cellSize;
        this.cols = width / cellSize;
        this.rows = height / cellSize;
        this.cells = new ArrayList[cols][rows];
        
        for (int i = 0; i < cols; i++) {
            for (int j = 0; j < rows; j++) {
                cells[i][j] = new ArrayList<Drohne>();
            }
        }
    }

    public void clear() {
        for (int i = 0; i < cols; i++) {
            for (int j = 0; j < rows; j++) {
                cells[i][j].clear();
            }
        }
    }

    public void insert(Drohne d) {
        int x = (int)((d.x + OFFSET) / cellSize);
        int y = (int)((d.y + OFFSET) / cellSize);
        
        // Clamping (Sicherheitshalber, falls Drohnen weit weg fliegen)
        if (x < 0) x = 0;
        if (x >= cols) x = cols - 1;
        if (y < 0) y = 0;
        if (y >= rows) y = rows - 1;
        
        cells[x][y].add(d);
    }

    public ArrayList<Drohne> getNeighbors(double xPos, double yPos) {
        ArrayList<Drohne> neighbors = new ArrayList<>();
        int x = (int)((xPos + OFFSET) / cellSize);
        int y = (int)((yPos + OFFSET) / cellSize);
        
        // 3x3 Block um die aktuelle Zelle abfragen
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                int cx = x + i;
                int cy = y + j;
                if (cx >= 0 && cx < cols && cy >= 0 && cy < rows) {
                    neighbors.addAll(cells[cx][cy]);
                }
            }
        }
        return neighbors;
    }
}
