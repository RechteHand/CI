import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * Feuerwerk-Partikelsystem fuer den Hintergrund der Drohnen-Show.
 * Verwaltet mehrere gleichzeitig aktive Feuerwerke mit Raketen-Aufstieg,
 * Explosion und Partikel-Regen.
 */
public class Feuerwerk {

    // === Globale Verwaltung aller Feuerwerke ===
    static ArrayList<Feuerwerk> alle = new ArrayList<>();
    static int nextLaunchFrame = 30;

    // Farbpalette fuer Feuerwerke
    static final Color[] PALETTE = {
        new Color(255, 215, 0),    // Gold
        new Color(255, 60, 60),    // Rot
        new Color(60, 120, 255),   // Blau
        new Color(60, 255, 120),   // Gruen
        new Color(180, 60, 255),   // Violett
        new Color(255, 255, 230),  // Weiss
        new Color(60, 255, 255),   // Cyan
        new Color(255, 160, 50),   // Orange
        new Color(255, 100, 180),  // Pink
    };

    // === Instanz-Felder ===
    double rocketX, rocketY;       // Raketenposition
    double rocketVy;               // Aufstiegsgeschwindigkeit
    double peakY;                  // Explosionshoehe
    Color color;
    boolean exploded = false;
    boolean dead = false;
    ArrayList<Partikel> partikel = new ArrayList<>();

    Feuerwerk(int screenWidth, int screenHeight) {
        this.rocketX = 100 + Math.random() * (screenWidth - 200);
        this.rocketY = screenHeight;
        this.rocketVy = -(3.0 + Math.random() * 3.0);
        this.peakY = 100 + Math.random() * (screenHeight * 0.5);
        this.color = PALETTE[(int) (Math.random() * PALETTE.length)];
    }

    /**
     * Globales Update: Neue Feuerwerke starten, bestehende updaten, tote entfernen.
     */
    static void updateAll(int frame, int screenWidth, int screenHeight) {
        // Neues Feuerwerk starten?
        if (frame >= nextLaunchFrame) {
            alle.add(new Feuerwerk(screenWidth, screenHeight));
            // Manchmal 2 gleichzeitig fuer mehr Spektakel
            if (Math.random() < 0.3) {
                alle.add(new Feuerwerk(screenWidth, screenHeight));
            }
            nextLaunchFrame = frame + 40 + (int) (Math.random() * 80);
        }

        // Alle updaten
        Iterator<Feuerwerk> it = alle.iterator();
        while (it.hasNext()) {
            Feuerwerk f = it.next();
            f.tick();
            if (f.dead) it.remove();
        }
    }

    /**
     * Update einer einzelnen Feuerwerk-Instanz.
     */
    void tick() {
        if (!exploded) {
            // Rakete steigt auf
            rocketY += rocketVy;

            if (rocketY <= peakY) {
                // EXPLOSION!
                exploded = true;
                int count = 40 + (int) (Math.random() * 60); // 40-100 Partikel

                for (int i = 0; i < count; i++) {
                    double angle = Math.random() * Math.PI * 2;
                    double speed = 1.0 + Math.random() * 3.5;

                    // Variation in Farbe pro Partikel
                    int dr = (int) ((Math.random() - 0.5) * 60);
                    int dg = (int) ((Math.random() - 0.5) * 60);
                    int db = (int) ((Math.random() - 0.5) * 60);
                    Color pc = new Color(
                        Math.max(0, Math.min(255, color.getRed() + dr)),
                        Math.max(0, Math.min(255, color.getGreen() + dg)),
                        Math.max(0, Math.min(255, color.getBlue() + db))
                    );

                    partikel.add(new Partikel(
                        rocketX, rocketY,
                        Math.cos(angle) * speed,
                        Math.sin(angle) * speed,
                        pc,
                        60 + (int) (Math.random() * 60) // Lebenszeit 60-120 Frames
                    ));
                }
            }
        } else {
            // Partikel updaten
            Iterator<Partikel> it = partikel.iterator();
            while (it.hasNext()) {
                Partikel p = it.next();
                p.tick();
                if (p.dead) it.remove();
            }

            if (partikel.isEmpty()) {
                dead = true;
            }
        }
    }

    // === Partikel (innere Klasse) ===

    static class Partikel {
        double x, y, vx, vy;
        Color color;
        int age = 0;
        int maxAge;
        boolean dead = false;

        static final double GRAVITY = 0.025;
        static final double FRICTION = 0.985;

        Partikel(double x, double y, double vx, double vy, Color color, int maxAge) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.color = color;
            this.maxAge = maxAge;
        }

        void tick() {
            vy += GRAVITY;        // Schwerkraft
            vx *= FRICTION;       // Luftwiderstand
            vy *= FRICTION;
            x += vx;
            y += vy;
            age++;

            if (age >= maxAge) {
                dead = true;
            }
        }

        /**
         * Gibt die Farbe mit Fade-Out basierend auf Alter zurueck.
         */
        Color getColor() {
            float life = 1.0f - (float) age / maxAge;
            int alpha = Math.max(0, (int) (255 * life * life)); // quadratisches Fade
            return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
        }
    }
}
