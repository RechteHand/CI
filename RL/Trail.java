// Speichert die Position-Spur eines abgeschlossenen Versuchs
// und wie er geendet hat (Crash / Ziel / Timeout) - fuer die Visualisierung.
import java.util.ArrayList;

public class Trail {
    public ArrayList<double[]> points = new ArrayList<>();
    public int outcome;   // 0 = Crash (rot), 1 = Ziel (gruen), 2 = Timeout (orange)
}
