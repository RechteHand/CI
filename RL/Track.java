// Strecke = Korridor in Schlangenform mit Start und Ziel.
// Aufbau: Aussenrahmen + horizontale Trennwaende, abwechselnd mit Luecke rechts/links
// -> der Weg geht erst nach rechts, dann runter, dann nach links, dann runter, ...
import java.util.ArrayList;

public class Track {
    public static final double X0 = 60,  Y0 = 80;
    public static final double X1 = 1040, Y1 = 520;
    public static final double LANE_H   = 110;     // Hoehe einer Spur
    public static final int    LANES    = 4;       // Anzahl Spuren -> Anzahl Kurven = LANES-1
    public static final double TURN_GAP = 110;     // Breite des Verbindungs-Korridors

    public ArrayList<double[]> walls       = new ArrayList<>();   // {x1,y1,x2,y2}
    public ArrayList<double[]> checkpoints = new ArrayList<>();   // {x1,y1,x2,y2}
    public double[] finishLine;
    public double[] startPos;
    public double   startAngle;

    public Track(){
        // 1) Aussenrahmen (4 Waende)
        walls.add(new double[]{X0,Y0, X1,Y0});
        walls.add(new double[]{X0,Y1, X1,Y1});
        walls.add(new double[]{X0,Y0, X0,Y1});
        walls.add(new double[]{X1,Y0, X1,Y1});

        // 2) Innere Trennwaende mit Luecke fuer die Kurve
        for(int i=0; i<LANES-1; i++){
            double y = Y0 + (i+1)*LANE_H;
            if(i % 2 == 0){
                // gerade Spur faehrt nach rechts -> Luecke rechts
                walls.add(new double[]{X0, y, X1 - TURN_GAP, y});
            } else {
                // ungerade Spur faehrt nach links -> Luecke links
                walls.add(new double[]{X0 + TURN_GAP, y, X1, y});
            }
        }

        // 3) Start: links oben in der ersten Spur, schaut nach rechts
        startPos   = new double[]{X0 + 60, Y0 + LANE_H/2};
        startAngle = 0;

        // 4) Checkpoints entlang des Wegs (zwingt das Auto, in der richtigen Reihenfolge zu fahren)
        double margin = 90;
        int    cpsPerLane = 4;
        for(int i=0; i<LANES; i++){
            double yTop = Y0 + i*LANE_H;
            double yBot = Y0 + (i+1)*LANE_H;
            boolean rightward = (i % 2 == 0);
            for(int j=1; j<=cpsPerLane; j++){
                double t = (double)j / (cpsPerLane+1);
                double x = rightward
                    ? X0 + margin + t*(X1 - X0 - 2*margin)
                    : X1 - margin - t*(X1 - X0 - 2*margin);
                checkpoints.add(new double[]{x, yTop+5, x, yBot-5});  // vertikale Linie quer durch die Spur
            }
            // Verbindungs-Checkpoint zwischen zwei Spuren (in der Kurve)
            if(i < LANES-1){
                double cx = rightward ? (X1 - TURN_GAP/2) : (X0 + TURN_GAP/2);
                double cy = yBot;
                checkpoints.add(new double[]{cx-30, cy, cx+30, cy});  // horizontale Linie
            }
        }

        // 5) Ziel-Linie am Ende der letzten Spur
        boolean lastRight = ((LANES-1) % 2 == 0);
        double yEndTop = Y0 + (LANES-1)*LANE_H;
        double yEndBot = Y0 + LANES*LANE_H;
        finishLine = lastRight
            ? new double[]{X1-30, yEndTop+15, X1-30, yEndBot-15}
            : new double[]{X0+30, yEndTop+15, X0+30, yEndBot-15};
    }
}
