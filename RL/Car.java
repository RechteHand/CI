// Auto = Position + Winkel + Sensoren.
// Der Sensor-Vektor wird zum diskreten "State" fuer Q-Learning.
public class Car {
    public static final int      N_SENSORS     = 5;
    // Sensor-Strahlen relativ zur Fahrtrichtung: links, halb-links, vorne, halb-rechts, rechts
    public static final double[] SENSOR_ANGLES = {-Math.PI/2, -Math.PI/4, 0, Math.PI/4, Math.PI/2};
    public static final double   MAX_RANGE     = 120;     // maximale Sensor-Reichweite
    public static final double   SPEED         = 2.0;     // Pixel pro Physikschritt
    public static final double   TURN          = 0.45;    // Lenkwinkel PRO ENTSCHEIDUNG (Bogenmass, ~26 Grad)
    public static final double   CRASH_DIST    = 4;       // Abstand zur Wand, ab dem es als Crash gilt

    public double  x, y, angle;
    public double[] sensors  = new double[N_SENSORS];
    public int     nextCheckpoint = 0;
    public int     checkpointsPassed = 0;
    public boolean isLeader = false;     // rotes Auto fuer Visualisierung

    // Trainings-Bookkeeping (vom Loop verwendet, damit jedes Auto seinen
    // eigenen Entscheidungs-Takt und akkumulierten Reward hat):
    public int    lastState         = 0;
    public int    lastAction        = 1;
    public int    stepsSinceDecision= 0;
    public double accumReward       = 0;

    public Car(double x, double y, double angle){
        this.x = x;  this.y = y;  this.angle = angle;
    }

    // --- Sensoren: Strahl gegen alle Waende und kuerzeste Distanz behalten ---
    public void updateSensors(Track track){
        for(int i=0; i<N_SENSORS; i++){
            double a = angle + SENSOR_ANGLES[i];
            sensors[i] = castRay(x, y, a, track);
        }
    }
    private double castRay(double rx, double ry, double a, Track track){
        double dx = Math.cos(a), dy = Math.sin(a);
        double minDist = MAX_RANGE;
        for(double[] w : track.walls){
            double d = raySegment(rx, ry, dx, dy, w[0], w[1], w[2], w[3]);
            if(d > 0 && d < minDist) minDist = d;
        }
        return minDist;
    }
    // Schnittpunkt Strahl <-> Liniensegment, gibt t (Distanz) oder -1 zurueck
    private static double raySegment(double rx, double ry, double dx, double dy,
                                     double x1, double y1, double x2, double y2){
        double sx = x2-x1, sy = y2-y1;
        double denom = dx*sy - dy*sx;
        if(Math.abs(denom) < 1e-9) return -1;
        double t = ((x1-rx)*sy - (y1-ry)*sx) / denom;
        double u = ((x1-rx)*dy - (y1-ry)*dx) / denom;
        if(t >= 0 && u >= 0 && u <= 1) return t;
        return -1;
    }

    // Lenken (nur am Entscheidungsschritt aufrufen): 0=links, 1=geradeaus, 2=rechts
    public void turn(int action){
        if(action == 0)      angle -= TURN;
        else if(action == 2) angle += TURN;
    }
    // Bewegung um SPEED nach vorne (jeden Physikschritt aufrufen)
    public void move(){
        x += SPEED * Math.cos(angle);
        y += SPEED * Math.sin(angle);
    }

    public boolean checkCrash(){
        for(double s : sensors) if(s < CRASH_DIST) return true;
        return false;
    }

    // Checkpoint passiert? (Liniensegment zwischen prev- und neuer Position schneidet die CP-Linie)
    public boolean checkCheckpoint(Track track, double prevX, double prevY){
        if(nextCheckpoint >= track.checkpoints.size()) return false;
        double[] cp = track.checkpoints.get(nextCheckpoint);
        if(segmentsIntersect(prevX, prevY, x, y, cp[0], cp[1], cp[2], cp[3])){
            nextCheckpoint++;
            checkpointsPassed++;
            return true;
        }
        return false;
    }

    // Ziellinie ueberquert?
    public boolean checkFinish(Track track, double prevX, double prevY){
        double[] f = track.finishLine;
        return segmentsIntersect(prevX, prevY, x, y, f[0], f[1], f[2], f[3]);
    }
    private static boolean segmentsIntersect(double x1, double y1, double x2, double y2,
                                             double x3, double y3, double x4, double y4){
        double d = (x2-x1)*(y4-y3) - (y2-y1)*(x4-x3);
        if(Math.abs(d) < 1e-9) return false;
        double t = ((x3-x1)*(y4-y3) - (y3-y1)*(x4-x3)) / d;
        double u = ((x3-x1)*(y2-y1) - (y3-y1)*(x2-x1)) / d;
        return t >= 0 && t <= 1 && u >= 0 && u <= 1;
    }

    // --- State = jeder Sensor wird in 3 Bins gepackt -> 3^5 = 243 moegliche Zustaende ---
    public int getState(){
        int state = 0;
        for(int i=0; i<N_SENSORS; i++){
            int bin;
            if      (sensors[i] < 25) bin = 0;   // nah
            else if (sensors[i] < 70) bin = 1;   // mittel
            else                      bin = 2;   // weit
            state = state*3 + bin;
        }
        return state;
    }

    public void reset(Track track){
        x = track.startPos[0];
        y = track.startPos[1];
        angle = track.startAngle;
        nextCheckpoint     = 0;
        checkpointsPassed  = 0;
        stepsSinceDecision = 0;
        accumReward        = 0;
        lastAction         = 1;
        updateSensors(track);
    }
}
