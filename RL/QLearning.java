// Tabellarisches Q-Learning. Q[state][action] ist der erwartete Gesamt-Reward,
// wenn man im Zustand "state" die Aktion "action" waehlt (und danach optimal weiter spielt).
//
// Bellman-Update:   Q(s,a) <- Q(s,a) + alpha * ( reward + gamma * max_a' Q(s', a')  -  Q(s,a) )
//                                      ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
//                                       "TD-Error" = Realitaet - bisherige Schaetzung
import java.util.Random;

public class QLearning {
    public static final int N_STATES  = 243;   // 3^5 (siehe Car.getState)
    public static final int N_ACTIONS = 3;     // links, geradeaus, rechts

    public double[][] Q       = new double[N_STATES][N_ACTIONS];
    public double alpha       = 0.15;          // Lernrate: wie stark zieht ein Update Q in Richtung Realitaet
    public double gamma       = 0.95;          // Discount: wie wichtig sind zukuenftige Belohnungen
    public double epsilon     = 1.0;           // Exploration: Wahrscheinlichkeit fuer Zufallsaktion
    public double epsilonMin  = 0.02;
    public double epsilonDecay= 0.995;         // pro Episode multiplizieren
    public Random rnd         = new Random();

    // epsilon-greedy: meistens beste bekannte Aktion, manchmal Zufall (zum Lernen)
    public int chooseAction(int state){
        if(rnd.nextDouble() < epsilon) return rnd.nextInt(N_ACTIONS);
        return argmax(Q[state]);
    }

    public void update(int s, int a, double reward, int sNext, boolean done){
        double target = reward;
        if(!done) target += gamma * max(Q[sNext]);
        Q[s][a] += alpha * (target - Q[s][a]);
    }

    public void decayEpsilon(){
        epsilon = Math.max(epsilonMin, epsilon * epsilonDecay);
    }

    private static int argmax(double[] a){
        int best = 0;
        for(int i=1; i<a.length; i++) if(a[i] > a[best]) best = i;
        return best;
    }
    private static double max(double[] a){
        double m = a[0];
        for(int i=1; i<a.length; i++) if(a[i] > m) m = a[i];
        return m;
    }
}
