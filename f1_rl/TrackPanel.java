package f1_rl;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.util.List;

/**
 * Renders the race track and all cars.
 *
 * <p>The track coordinate space is automatically scaled and centred to fill
 * the panel regardless of its size. This means the whole circuit is always
 * visible in Track View mode.</p>
 *
 * <p>Two camera modes (toggled via {@link Config#FOLLOW_LEADER}):
 * <ul>
 *   <li><b>Track View</b> – entire circuit fits the panel (auto-scaled).</li>
 *   <li><b>Follow Leader</b> – 1:1 scale, camera centred on P1.</li>
 * </ul>
 * </p>
 */
public class TrackPanel extends JPanel {

    // ── F1 team colours ───────────────────────────────────────────────────────
    private static final Color[] DRIVER_COLORS = {
        new Color(220,   0,  40),  // Ferrari red
        new Color( 30, 130, 255),  // Mercedes blue
        new Color(255, 128,   0),  // McLaren orange
        new Color(  0, 100, 200),  // Red Bull blue
        new Color(  0, 164, 139),  // Aston Martin green
        new Color(105,   0, 210),  // Alpine purple
        new Color( 20,  90, 170),  // Williams navy
        new Color(150,   0,   0),  // Haas dark red
        new Color(220, 200,   0),  // Renault yellow
        new Color(180,  40, 120),  // Alfa Romeo magenta
    };

    private static final Color COL_GRASS  = new Color(46,  95, 46);
    private static final Color COL_ASPHALT= new Color(52,  52, 58);
    private static final Color COL_KERB_R = new Color(200, 35, 35);
    private static final Color COL_KERB_W = new Color(240, 240, 240);
    private static final Color COL_DASHES = new Color(255, 255, 255, 95);
    private static final Color COL_RAY    = new Color(0,  220, 255, 80);
    private static final Color COL_HIT    = new Color(255,  60,  60, 180);

    private final RaceEngine      engine;
    private final PopulationManager pop;

    // Cached scale/offset for the track-fit transform (recomputed each paint)
    private float trackScale   = 1f;
    private float trackOffsetX = 0f;
    private float trackOffsetY = 0f;

    // ── Construction ──────────────────────────────────────────────────────────

    public TrackPanel(RaceEngine engine, PopulationManager pop) {
        this.engine = engine;
        this.pop    = pop;
        setBackground(COL_GRASS);
    }

    // ── Painting ──────────────────────────────────────────────────────────────

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,    RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,  RenderingHints.VALUE_STROKE_PURE);

        // Save the original transform to preserve Retina/HiDPI scaling
        AffineTransform originalTransform = g2.getTransform();

        // Compute camera transform and apply it ON TOP of the existing scaling
        AffineTransform camera = buildCameraTransform();
        g2.transform(camera);

        drawTrack(g2);
        drawCars(g2);

        // Restore original transform to draw HUD in screen-space
        g2.setTransform(originalTransform);
        drawHud(g2);
    }

    // ── Camera ────────────────────────────────────────────────────────────────

    /**
     * Builds an {@link AffineTransform} that maps track coordinates to
     * panel pixels. In Track View the whole circuit fits; in Follow Leader
     * the leader is centred at 1:1 scale.
     */
    private AffineTransform buildCameraTransform() {
        computeTrackFitTransform(); // updates trackScale / trackOffsetX / trackOffsetY

        if (Config.FOLLOW_LEADER) {
            Car leader = engine.getLeader();
            if (leader != null) {
                float cx = getWidth()  / 2f - leader.getX() * trackScale;
                float cy = getHeight() / 2f - leader.getY() * trackScale;
                AffineTransform at = new AffineTransform();
                at.translate(cx, cy);
                at.scale(trackScale, trackScale);
                return at;
            }
        }

        // Default: fit whole track
        AffineTransform at = new AffineTransform();
        at.translate(trackOffsetX, trackOffsetY);
        at.scale(trackScale, trackScale);
        return at;
    }

    /**
     * Computes scale and offset so the whole circuit fits the panel
     * with a comfortable margin, automatically adjusting to any track shape.
     */
    private void computeTrackFitTransform() {
        float[] b      = engine.getTrack().getBounds();
        float   margin = Config.TRACK_WIDTH / 2f + 20; // 20px padding
        float   bx     = b[0] - margin;
        float   by     = b[1] - margin;
        float   bw     = b[2] - b[0] + 2 * margin;
        float   bh     = b[3] - b[1] + 2 * margin;

        float scaleX = getWidth()  / bw;
        float scaleY = getHeight() / bh;
        trackScale   = Math.min(scaleX, scaleY);

        // Centre the track inside the panel
        trackOffsetX = (getWidth()  - bw * trackScale) / 2f - bx * trackScale;
        trackOffsetY = (getHeight() - bh * trackScale) / 2f - by * trackScale;
    }

    // ── Track rendering ───────────────────────────────────────────────────────

    private void drawTrack(Graphics2D g2) {
        Track t      = engine.getTrack();
        int   n      = t.centreX.length;
        int   tw     = Config.TRACK_WIDTH;
        Stroke saved = g2.getStroke();

        // Kerb layers
        setRoundStroke(g2, tw + 14);
        g2.setColor(COL_KERB_R);
        g2.drawPolyline(t.centreX, t.centreY, n);
        closePath(g2, t);

        setRoundStroke(g2, tw + 6);
        g2.setColor(COL_KERB_W);
        g2.drawPolyline(t.centreX, t.centreY, n);
        closePath(g2, t);

        // Asphalt
        setRoundStroke(g2, tw);
        g2.setColor(COL_ASPHALT);
        g2.drawPolyline(t.centreX, t.centreY, n);
        closePath(g2, t);

        // Dashed centre line
        g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT,
                BasicStroke.JOIN_ROUND, 1f, new float[]{14f, 18f}, 0f));
        g2.setColor(COL_DASHES);
        g2.drawPolyline(t.centreX, t.centreY, n);
        closePath(g2, t);

        g2.setStroke(saved);
        drawFinishLine(g2, t);
        drawDirectionArrows(g2, t);
    }

    /**
     * Draws small triangular arrows along the track every ~120px to show
     * driving direction. Subtle but helps viewers understand the racing line.
     */
    private void drawDirectionArrows(Graphics2D g2, Track t) {
        int n       = t.centreX.length;
        int spacing = Math.max(1, n / 18); // ~18 arrows around the circuit
        g2.setColor(new Color(255, 255, 255, 35));
        for (int i = spacing; i < n; i += spacing) {
            int prev = i - 2;
            float dx = t.centreX[i] - t.centreX[prev];
            float dy = t.centreY[i] - t.centreY[prev];
            float len = (float) Math.sqrt(dx*dx + dy*dy) + 1e-6f;
            float ux = dx/len, uy = dy/len;
            float px = -uy, py = ux; // perpendicular
            float ax = t.centreX[i], ay = t.centreY[i];
            int[] xs = {(int)(ax + ux*7), (int)(ax - ux*5 + px*5), (int)(ax - ux*5 - px*5)};
            int[] ys = {(int)(ay + uy*7), (int)(ay - uy*5 + py*5), (int)(ay - uy*5 - py*5)};
            g2.fillPolygon(xs, ys, 3);
        }
    }

    private void setRoundStroke(Graphics2D g2, int width) {
        g2.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    }

    private void closePath(Graphics2D g2, Track t) {
        int n = t.centreX.length;
        g2.drawLine(t.centreX[n - 1], t.centreY[n - 1], t.centreX[0], t.centreY[0]);
    }

    private void drawFinishLine(Graphics2D g2, Track t) {
        AffineTransform saved = g2.getTransform();
        g2.translate(t.finishX, t.finishY);
        g2.rotate(Math.atan2(t.finishNY, t.finishNX));

        float hw    = Config.TRACK_WIDTH / 2f;
        int   sq    = 8;
        float sqW   = hw * 2f / sq;
        float sqH   = sqW;

        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < sq; col++) {
                g2.setColor(((row + col) % 2 == 0) ? Color.WHITE : Color.BLACK);
                g2.fillRect((int)(-hw + col * sqW), (int)(-sqH + row * sqH),
                            (int) sqW + 1, (int) sqH + 1);
            }
        }
        g2.setTransform(saved);
    }

    // ── Car rendering ─────────────────────────────────────────────────────────

    private void drawCars(Graphics2D g2) {
        List<Car> cars   = engine.getCars();
        Car       leader = engine.getLeader();

        // All non-leader cars first
        for (Car car : cars) {
            if (car != leader) drawCar(g2, car, false);
        }
        // Leader last (always on top)
        if (leader != null) {
            drawLeaderGlow(g2, leader);
            drawCar(g2, leader, true);
            drawRays(g2, leader);
        }
    }

    private void drawCar(Graphics2D g2, Car car, boolean isLeader) {
        Color teamCol = DRIVER_COLORS[car.id % DRIVER_COLORS.length];

        AffineTransform saved = g2.getTransform();
        g2.translate(car.getX(), car.getY());
        g2.rotate(car.getHeading());

        int len = (int) Config.CAR_LENGTH, wid = (int) Config.CAR_WIDTH;

        if (car.isDnf()) {
            // DNF: grey semi-transparent wreckage
            g2.setColor(new Color(80, 80, 80, 110));
            g2.fillRoundRect(-len/2, -wid/2, len, wid, 3, 3);
            g2.setColor(new Color(200, 200, 200, 90));
            g2.setFont(new Font("SansSerif", Font.BOLD, 8));
            g2.drawString("DNF", -len/2 + 2, 3);
            g2.setTransform(saved);
            return;
        }

        // Speed tint: interpolate team colour → brighter as gas increases
        float gasFrac = Math.max(0, Math.min(1, car.getGas()));
        int r = (int)(teamCol.getRed()   + (255 - teamCol.getRed())   * gasFrac * 0.35f);
        int g = (int)(teamCol.getGreen() + (255 - teamCol.getGreen()) * gasFrac * 0.35f);
        int b = (int)(teamCol.getBlue()  + (255 - teamCol.getBlue())  * gasFrac * 0.35f);
        int alpha = isLeader ? 255 : 190;
        Color col = new Color(Math.min(r,255), Math.min(g,255), Math.min(b,255), alpha);

        g2.setColor(col);
        g2.fillRoundRect(-len / 2, -wid / 2, len, wid, 4, 4);

        // Windscreen
        g2.setColor(new Color(100, 200, 255, 130));
        g2.fillRect(1, -wid / 2 + 1, len / 3, wid - 2);

        // Draft glow
        if (car.isInDraft()) {
            g2.setColor(new Color(0, 255, 150, 55));
            g2.fillRoundRect(-len / 2 - 5, -wid / 2 - 5, len + 10, wid + 10, 6, 6);
        }

        // Crash flash
        if (car.getCollisionCooldown() > 0) {
            g2.setColor(new Color(255, 200, 0, 130));
            g2.fillRoundRect(-len / 2 - 3, -wid / 2 - 3, len + 6, wid + 6, 4, 4);
        }

        // Leader: show car number
        if (isLeader) {
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("SansSerif", Font.BOLD, 7));
            g2.drawString(String.valueOf(car.id + 1), -2, 3);
        }

        g2.setTransform(saved);
    }

    private void drawLeaderGlow(Graphics2D g2, Car car) {
        AffineTransform saved = g2.getTransform();
        g2.translate(car.getX(), car.getY());
        g2.rotate(car.getHeading());
        int len = (int) Config.CAR_LENGTH + 14, wid = (int) Config.CAR_WIDTH + 14;
        g2.setColor(new Color(255, 255, 255, 35));
        g2.fillRoundRect(-len / 2, -wid / 2, len, wid, 8, 8);
        g2.setTransform(saved);
    }

    private void drawRays(Graphics2D g2, Car car) {
        Stroke saved = g2.getStroke();
        g2.setStroke(new BasicStroke(0.8f));
        float[] hx = car.getRayHitX(), hy = car.getRayHitY();
        for (int i = 0; i < Config.NUM_WALL_RAYS; i++) {
            g2.setColor(COL_RAY);
            g2.drawLine((int) car.getX(), (int) car.getY(), (int) hx[i], (int) hy[i]);
            g2.setColor(COL_HIT);
            g2.fillOval((int) hx[i] - 3, (int) hy[i] - 3, 6, 6);
        }
        g2.setStroke(saved);
    }

    // ── HUD (screen space) ────────────────────────────────────────────────────

    private void drawHud(Graphics2D g2) {
        String mode = Config.FOLLOW_LEADER ? "📷 Follow Leader" : "🗺  Track View";
        FontMetrics fm = g2.getFontMetrics(new Font("SansSerif", Font.BOLD, 12));
        int tw = fm.stringWidth(mode) + 16;

        g2.setColor(new Color(0, 0, 0, 150));
        g2.fillRoundRect(8, 8, tw, 24, 8, 8);
        g2.setFont(new Font("SansSerif", Font.BOLD, 12));
        g2.setColor(Color.WHITE);
        g2.drawString(mode, 16, 24);
    }

    // ── Static helper (used by DashboardPanel for colour swatches) ────────────

    public static Color getDriverColor(int id) {
        return DRIVER_COLORS[id % DRIVER_COLORS.length];
    }
}
