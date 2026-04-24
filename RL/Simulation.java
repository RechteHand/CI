// Trainings-Loop. Alle Autos teilen sich EINE Q-Tabelle und lernen parallel.
// Pro Auto eigene Schritt- und Trail-Verwaltung; Episode endet bei Crash, Ziel oder Timeout.
import java.util.ArrayList;
import javax.swing.JFrame;

public class Simulation extends JFrame implements Runnable {
    // ===========================================================
    //                     KONFIGURATION
    // ===========================================================
    static final int N_CARS            = 15;    // 1 = sequenziell, >1 = Schwarm
    static final int DECISION_INTERVAL = 5;     // Physikschritte zwischen zwei Entscheidungen (groesser = ruhigere Fahrt)
    static final int SLEEP_MS          = 8;     // Pause pro Frame in ms (kleiner = schneller)
    static final int MAX_STEPS         = 2500;  // Timeout pro Episode pro Auto
    static final int MAX_TRAILS        = 30;    // wie viele alte Spuren angezeigt werden
    // ===========================================================

    Track             track  = new Track();
    QLearning         agent  = new QLearning();
    ArrayList<Car>    cars   = new ArrayList<>();
    ArrayList<Trail>  active = new ArrayList<>();   // pro Auto: aktuell wachsende Spur
    int[]             steps;
    ArrayList<Trail>  trails = new ArrayList<>();   // abgeschlossene Spuren (faded angezeigt)

    int     episodes        = 0;
    int     bestCheckpoints = 0;
    int     finishCount     = 0;
    String  lastOutcome     = "-";

    Canvas  canvas;

    Simulation(){
        setTitle("RL Maze - Q-Learning  (N=" + N_CARS + ")");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(null);

        steps = new int[N_CARS];
        for(int i=0; i<N_CARS; i++){
            Car c = new Car(track.startPos[0], track.startPos[1], track.startAngle);
            if(i==0) c.isLeader = true;     // rotes Auto fuer Sensor-Visualisierung
            c.reset(track);                 // initialisiert Sensoren
            cars.add(c);
            active.add(new Trail());
        }

        canvas = new Canvas(track, cars, this);
        canvas.setBounds(0, 0, 1100, 650);
        add(canvas);
        setSize(1120, 690);
        setVisible(true);
    }

    public static void main(String[] args){
        new Thread(new Simulation()).start();
    }

    public void run(){
        while(true){
            for(int i=0; i<cars.size(); i++){
                Car   car = cars.get(i);
                Trail tr  = active.get(i);

                // 1) ENTSCHEIDUNG nur alle DECISION_INTERVAL Schritte
                if(car.stepsSinceDecision == 0){
                    car.lastState  = car.getState();
                    car.lastAction = agent.chooseAction(car.lastState);
                    car.turn(car.lastAction);     // Lenkung anpassen
                    car.accumReward = 0;
                }

                // 2) BEWEGUNG jeden Physikschritt
                double prevX = car.x, prevY = car.y;
                car.move();
                car.updateSensors(track);
                tr.points.add(new double[]{car.x, car.y});

                // 3) Reward dieses Physikschritts -> in akkumulierten Reward der Entscheidungsphase
                double  r       = -0.05;
                boolean done    = false;
                int     outcome = -1;
                if(car.checkCrash()){
                    r = -100;  done = true;  outcome = 0;
                } else if(car.checkFinish(track, prevX, prevY)){
                    r = 500;   done = true;  outcome = 1;  finishCount++;
                } else if(car.checkCheckpoint(track, prevX, prevY)){
                    r = 10;
                }
                car.accumReward += r;
                car.stepsSinceDecision++;
                steps[i]++;
                if(!done && steps[i] >= MAX_STEPS){
                    done = true;  outcome = 2;
                }

                // 4) Q-Update am Ende einer Entscheidungsphase ODER wenn Episode endet
                if(car.stepsSinceDecision >= DECISION_INTERVAL || done){
                    int sNext = car.getState();
                    agent.update(car.lastState, car.lastAction, car.accumReward, sNext, done);
                    car.stepsSinceDecision = 0;
                }

                // 5) Episodenende: Trail abschliessen, Auto zuruecksetzen
                if(done){
                    if(car.checkpointsPassed > bestCheckpoints) bestCheckpoints = car.checkpointsPassed;
                    tr.outcome = outcome;
                    trails.add(tr);
                    while(trails.size() > MAX_TRAILS) trails.remove(0);
                    active.set(i, new Trail());

                    lastOutcome = (outcome==1 ? "ZIEL" : outcome==2 ? "Timeout" : "Crash");
                    episodes++;
                    agent.decayEpsilon();
                    car.reset(track);
                    steps[i] = 0;
                }
            }

            try { Thread.sleep(SLEEP_MS); } catch(InterruptedException e) {}
            repaint();
        }
    }
}
