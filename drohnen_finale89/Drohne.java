import java.awt.Color;
import java.util.ArrayList;

/**
 * Repraesentiert eine einzelne beleuchtete Drohne in der Schwarm-Show.
 * Jede Drohne startet am Boden und fliegt per Arrival-Steering-Behavior
 * sanft zu ihrer Zielposition.
 * Inklusive Kollisionsvermeidung und Signal-Broadcasting bei Blockaden.
 */
public class Drohne {

    static int nextId = 0;
    int id;

    // Aktuelle Position & Geschwindigkeit
    double x, y;
    double vx, vy;

    // Zielkoordinaten & Zielfarbe
    double targetX, targetY;
    Color targetColor;

    // Steuerungsparameter
    double maxSpeed = 3.5;
    double maxForce = 0.12;
    double arrivalRadius = 100.0;

    // Kollisions- & Signalparameter
    static final double COLLISION_RADIUS = 3.5;
    static final double SIGNAL_RADIUS = 12.0;

    // Zustand
    int launchDelay;
    boolean launched = false;
    boolean arrived = false;
    double arrivalProgress = 0.0;

    int stuckFrames = 0;
    boolean broadcastingSignal = false;

    // Shimmer-Phase (individueller Offset pro Drohne)
    double shimmerPhase;

    Drohne(double targetX, double targetY, Color targetColor, int launchDelay, int windowHeight) {
        this.id = nextId++;
        this.targetX = targetX;
        this.targetY = targetY;
        this.targetColor = targetColor;
        this.launchDelay = launchDelay;
        this.shimmerPhase = Math.random() * Math.PI * 2;

        // Startposition: zufaellig am unteren Rand verteilt
        this.x = targetX + (Math.random() - 0.5) * 300;
        this.y = windowHeight + 20 + Math.random() * 80;
        this.vx = (Math.random() - 0.5) * 0.5;
        this.vy = -Math.random() * 0.5;
    }

    /**
     * Steering-Update pro Frame: Arrival-Behavior, Separation & Signaling.
     */
    void update(int frame, SpatialGrid grid) {
        if (frame < launchDelay) return;
        launched = true;

        double dx = targetX - x;
        double dy = targetY - y;
        double distToTarget = Math.sqrt(dx * dx + dy * dy);

        if (distToTarget < 2.5) {
            arrived = true;
            stuckFrames = 0;
            broadcastingSignal = false;
            
            // Sehr starkes Abbremsen (Handbremse)
            vx *= 0.5;
            vy *= 0.5;
            
            // Sanftes Einrasten (Snap-to-Target), wenn sie extrem langsam sind
            if (Math.abs(vx) < 0.1 && Math.abs(vy) < 0.1) {
                x += dx * 0.1;
                y += dy * 0.1;
            }
        } else {
            arrived = false;
        }

        double desiredSpeed;
        if (distToTarget < arrivalRadius) {
            desiredSpeed = maxSpeed * (distToTarget / arrivalRadius);
            arrivalProgress = Math.min(1.0, 1.0 - (distToTarget / arrivalRadius) + 0.3);
        } else {
            desiredSpeed = maxSpeed;
            arrivalProgress = Math.min(0.3, 0.3 * (1.0 - distToTarget / 800.0));
        }
        if (arrivalProgress < 0) arrivalProgress = 0;

        double desiredVx = (dx / distToTarget) * desiredSpeed;
        double desiredVy = (dy / distToTarget) * desiredSpeed;

        double steerX = desiredVx - vx;
        double steerY = desiredVy - vy;

        // --- 1. Kollisionsvermeidung (Separation) ---
        double sepX = 0, sepY = 0;
        int sepCount = 0;

        // --- 2. Signal Reaktion (Evade) ---
        double evadeX = 0, evadeY = 0;

        for (Drohne other : grid.getNeighbors(x, y)) {
            if (other == this || !other.launched) continue;
            
            double ox = x - other.x;
            double oy = y - other.y;
            double d = Math.sqrt(ox * ox + oy * oy);
            
            // Separation: Nur wenn nicht beide geparkt sind
            if (d > 0 && d < COLLISION_RADIUS) {
                if (!this.arrived || !other.arrived) {
                    sepX += (ox / d) / d; // Weight by distance
                    sepY += (oy / d) / d;
                    sepCount++;
                }
            }

            // Evade Signals from blocked drones (IMMER checken, auch wenn geparkt!)
            if (other.broadcastingSignal && d > 0 && d < SIGNAL_RADIUS) {
                // Push away from broadcasting drone
                evadeX += (ox / d) * 2.5; 
                evadeY += (oy / d) * 2.5;
                arrived = false; // Aufwachen, falls wir geparkt waren!
            }
        }

        if (sepCount > 0) {
            sepX /= sepCount;
            sepY /= sepCount;
            // Normalize and apply max force
            double sepMag = Math.sqrt(sepX * sepX + sepY * sepY);
            if (sepMag > 0) {
                // Fade-Out: Abstoßung wird sanfter, je näher wir dem Ziel sind (ab 25 Pixeln)
                double fadeOut = Math.min(1.0, distToTarget / 25.0);
                double currentSepForce = maxForce * 2.5 * fadeOut;
                
                sepX = (sepX / sepMag) * currentSepForce;
                sepY = (sepY / sepMag) * currentSepForce;
            }
            steerX += sepX;
            steerY += sepY;
        }

        // Apply Evade force
        steerX += evadeX;
        steerY += evadeY;

        // Limit steering force
        double steerMag = Math.sqrt(steerX * steerX + steerY * steerY);
        if (steerMag > maxForce) {
            steerX = (steerX / steerMag) * maxForce;
            steerY = (steerY / steerMag) * maxForce;
        }

        vx += steerX;
        vy += steerY;

        // --- 3. Blockade Erkennung (Signal senden) ---
        if (!arrived) {
            double currentSpeed = Math.sqrt(vx * vx + vy * vy);
            if (distToTarget > 5.0 && currentSpeed < 0.5) {
                stuckFrames++;
                if (stuckFrames > 30) {
                    broadcastingSignal = true;
                }
            } else {
                stuckFrames = 0;
                broadcastingSignal = false;
            }
        }

        // Limit speed
        double speed = Math.sqrt(vx * vx + vy * vy);
        if (speed > maxSpeed) {
            vx = (vx / speed) * maxSpeed;
            vy = (vy / speed) * maxSpeed;
        }

        x += vx;
        y += vy;
    }

    /**
     * Aktuelle Anzeigefarbe: Interpoliert von warmem Weiss zur Zielfarbe.
     */
    Color getCurrentColor(int frame) {
        if (!launched) return new Color(255, 240, 200, 60);

        float progress = (float) Math.min(1.0, arrivalProgress);
        int startR = 255, startG = 240, startB = 210;

        int r = (int) (startR + (targetColor.getRed() - startR) * progress);
        int g = (int) (startG + (targetColor.getGreen() - startG) * progress);
        int b = (int) (startB + (targetColor.getBlue() - startB) * progress);

        if (arrived) {
            double shimmer = 0.88 + 0.12 * Math.sin(frame * 0.04 + shimmerPhase);
            r = Math.min(255, (int) (r * shimmer));
            g = Math.min(255, (int) (g * shimmer));
            b = Math.min(255, (int) (b * shimmer));
        }
        
        // Visual indicator if broadcasting signal (pulsing red/orange)
        if (broadcastingSignal) {
             r = 255; g = 100 + (int)(100 * Math.sin(frame * 0.2)); b = 0;
        }

        r = Math.max(0, Math.min(255, r));
        g = Math.max(0, Math.min(255, g));
        b = Math.max(0, Math.min(255, b));
        return new Color(r, g, b);
    }

    /**
     * Glow-Farbe (semi-transparent fuer den Leuchteffekt).
     */
    Color getGlowColor(int frame) {
        Color c = getCurrentColor(frame);
        int alpha = launched ? (arrived ? 50 : 30) : 15;
        if (broadcastingSignal) alpha = 100;
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
    }
}
