import java.awt.Color;

/**
 * Repraesentiert eine einzelne beleuchtete Drohne in der Schwarm-Show.
 * Jede Drohne startet am Boden und fliegt per Arrival-Steering-Behavior
 * (nach Craig Reynolds) sanft zu ihrer Zielposition.
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

    // Zustand
    int launchDelay;
    boolean launched = false;
    boolean arrived = false;
    double arrivalProgress = 0.0;

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
     * Steering-Update pro Frame: Arrival-Behavior.
     * Drohne steuert auf Zielposition zu und bremst sanft ab.
     */
    void update(int frame) {
        if (frame < launchDelay) return;
        launched = true;

        double dx = targetX - x;
        double dy = targetY - y;
        double dist = Math.sqrt(dx * dx + dy * dy);

        if (dist < 0.8) {
            arrived = true;
            vx *= 0.85;
            vy *= 0.85;
            vx += (Math.random() - 0.5) * 0.02;
            vy += (Math.random() - 0.5) * 0.02;
            x += vx;
            y += vy;
            arrivalProgress = 1.0;
            return;
        }

        double desiredSpeed;
        if (dist < arrivalRadius) {
            desiredSpeed = maxSpeed * (dist / arrivalRadius);
            arrivalProgress = Math.min(1.0, 1.0 - (dist / arrivalRadius) + 0.3);
        } else {
            desiredSpeed = maxSpeed;
            arrivalProgress = Math.min(0.3, 0.3 * (1.0 - dist / 800.0));
        }
        if (arrivalProgress < 0) arrivalProgress = 0;

        double desiredVx = (dx / dist) * desiredSpeed;
        double desiredVy = (dy / dist) * desiredSpeed;

        double steerX = desiredVx - vx;
        double steerY = desiredVy - vy;

        double steerMag = Math.sqrt(steerX * steerX + steerY * steerY);
        if (steerMag > maxForce) {
            steerX = (steerX / steerMag) * maxForce;
            steerY = (steerY / steerMag) * maxForce;
        }

        vx += steerX;
        vy += steerY;

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
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
    }
}
