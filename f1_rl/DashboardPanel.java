package f1_rl;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * F1-style timing tower displayed in a side panel.
 *
 * <p>Layout:
 * <pre>
 * ┌──────────────────────────┐
 * │ 🏁 F1 AI Sim — Race 42   │
 * │ Lap X / Y   Tick 12345   │
 * ├──────────────────────────┤
 * │ POS ■ ID   LAP    GAP    │
 * │  1  ■ 07   3/3   LEADER  │
 * │  2  ■ 12   3/3   +2.1s   │
 * │  3  ■ 03   2/3   +1 Lap  │
 * │  ...                     │
 * ├──────────────────────────┤
 * │ BEST LAP   CAR 07  840t  │
 * │ AVG LAP    912 ticks     │
 * │ CRASHES    7             │
 * │ OVERTAKES  12            │
 * ├──────────────────────────┤
 * │ [FITNESS CHART]          │
 * └──────────────────────────┘
 * </pre>
 * </p>
 */
public class DashboardPanel extends JPanel {

    // ── Colour palette ────────────────────────────────────────────────────────
    private static final Color BG        = new Color(12,  12,  18);
    private static final Color CARD_BG   = new Color(22,  24,  34);
    private static final Color HEADER_BG = new Color(30,  30,  45);
    private static final Color ACCENT    = new Color(220, 30,  30);   // F1 red
    private static final Color GREEN     = new Color(0,   210, 100);
    private static final Color YELLOW    = new Color(255, 200,  0);
    private static final Color BLUE      = new Color(60,  150, 255);
    private static final Color TEXT_DIM  = new Color(130, 130, 155);
    private static final Color DIVIDER   = new Color(45,  45,  65);

    // ── Fonts ─────────────────────────────────────────────────────────────────
    private static final Font F_HEADER = new Font("SansSerif", Font.BOLD, 13);
    private static final Font F_LABEL  = new Font("SansSerif", Font.PLAIN, 10);
    private static final Font F_VALUE  = new Font("SansSerif", Font.BOLD, 13);
    private static final Font F_MONO   = new Font("Monospaced", Font.PLAIN, 12);
    private static final Font F_SMALL  = new Font("SansSerif", Font.PLAIN, 11);

    private final RaceEngine       engine;
    private final PopulationManager pop;

    // ── Construction ──────────────────────────────────────────────────────────

    public DashboardPanel(RaceEngine engine, PopulationManager pop) {
        this.engine = engine;
        this.pop    = pop;
        setBackground(BG);
        setPreferredSize(new Dimension(Config.DASHBOARD_WIDTH, Config.WINDOW_HEIGHT));
    }

    // ── Painting ──────────────────────────────────────────────────────────────

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int x = 8, y = 8, w = getWidth() - 16;

        y = drawHeader(g2, x, y, w);
        y += 6;
        y = drawTimingTower(g2, x, y, w);
        y += 6;
        y = drawRaceStats(g2, x, y, w);
        y += 6;
        drawFitnessChart(g2, x, y, w);
    }

    // ── Header ────────────────────────────────────────────────────────────────

    private int drawHeader(Graphics2D g2, int x, int y, int w) {
        g2.setColor(HEADER_BG);
        g2.fillRoundRect(x, y, w, 44, 8, 8);
        g2.setColor(ACCENT);
        g2.setFont(F_HEADER);
        g2.drawString("🏁  F1 AI — Race " + pop.getGeneration(), x + 10, y + 16);
        g2.setColor(TEXT_DIM);
        g2.setFont(F_SMALL);
        Car leader = engine.getLeader();
        int curLap = leader != null ? Math.min(leader.getLapsCompleted() + 1, Config.NUM_LAPS) : 1;
        g2.drawString(String.format("Lap %d / %d     Tick %,d", curLap, Config.NUM_LAPS, engine.getRaceTick()),
                x + 10, y + 34);
        return y + 44;
    }

    // ── Timing Tower ──────────────────────────────────────────────────────────

    private int drawTimingTower(Graphics2D g2, int x, int y, int w) {
        // Column header
        g2.setColor(DIVIDER);
        g2.fillRect(x, y, w, 18);
        g2.setColor(TEXT_DIM);
        g2.setFont(F_LABEL);
        g2.drawString("POS", x + 4,  y + 13);
        g2.drawString("DRIVER", x + 38, y + 13);
        g2.drawString("LAP", x + 112, y + 13);
        g2.drawString("GAP",  x + 162, y + 13);
        g2.drawString("SPEED", x + 210, y + 13);
        y += 20;

        List<Car> positions = engine.getPositions();
        Car leader          = positions.isEmpty() ? null : positions.get(0);

        for (int i = 0; i < positions.size(); i++) {
            Car car = positions.get(i);
            y = drawPositionRow(g2, x, y, w, i + 1, car, leader);
        }
        return y;
    }

    private int drawPositionRow(Graphics2D g2, int x, int y, int w,
                                 int pos, Car car, Car leader) {
        int h = 22;
        // Row background (alternate shading)
        g2.setColor(pos % 2 == 0 ? CARD_BG : new Color(18, 20, 30));
        g2.fillRect(x, y, w, h);

        // Position number
        g2.setFont(F_VALUE);
        g2.setColor(pos == 1 ? YELLOW : pos <= 3 ? GREEN : Color.WHITE);
        g2.drawString(String.valueOf(pos), x + 4, y + 15);

        // Colour swatch
        g2.setColor(TrackPanel.getDriverColor(car.id));
        g2.fillRect(x + 28, y + 4, 8, 14);

        // Driver ID
        g2.setFont(F_MONO);
        g2.setColor(Color.WHITE);
        g2.drawString(String.format("CAR%02d", car.id), x + 40, y + 15);

        // Lap
        g2.setFont(F_SMALL);
        g2.setColor(TEXT_DIM);
        g2.drawString(car.getLapsCompleted() + "/" + Config.NUM_LAPS, x + 112, y + 15);

        // Gap to leader
        String gap = computeGap(car, leader);
        g2.setColor(leader == car ? YELLOW : Color.WHITE);
        g2.drawString(gap, x + 162, y + 15);

        // Speed indicator bar
        float speedFrac = car.getSpeed() / Config.CAR_MAX_SPEED;
        g2.setColor(new Color(40, 40, 60));
        g2.fillRect(x + 210, y + 7, 60, 8);
        Color barCol = speedFrac > 0.8f ? GREEN : speedFrac > 0.5f ? YELLOW : ACCENT;
        g2.setColor(barCol);
        g2.fillRect(x + 210, y + 7, (int)(speedFrac * 60), 8);

        y += h;
        // Thin separator
        g2.setColor(DIVIDER);
        g2.drawLine(x, y, x + w, y);
        return y + 1;
    }

    private String computeGap(Car car, Car leader) {
        if (car == leader) return "LEADER";
        int cpDiff = leader.getTotalCheckpoints() - car.getTotalCheckpoints();
        if (cpDiff >= leader.getTotalCheckpoints() / Math.max(Config.NUM_LAPS, 1))
            return "+1 Lap";
        if (cpDiff > 8) return "+" + cpDiff + " CPs";
        // Approximate gap in ticks
        long ticksPerCp = leader.getSurvivedTicks() / Math.max(1, leader.getTotalCheckpoints());
        return String.format("+%dt", cpDiff * ticksPerCp);
    }

    // ── Race stats panel ──────────────────────────────────────────────────────

    private int drawRaceStats(Graphics2D g2, int x, int y, int w) {
        // Find best lap and avg lap across all drivers
        long bestLap = Long.MAX_VALUE;
        int  bestLapCar = -1;
        long totalLapSum = 0;
        int  lapCount = 0;
        for (Car car : engine.getCars()) {
            if (car.getBestLapTicks() < bestLap) {
                bestLap    = car.getBestLapTicks();
                bestLapCar = car.id;
            }
            if (car.getAvgLapTicks() != Long.MAX_VALUE) {
                totalLapSum += car.getAvgLapTicks();
                lapCount++;
            }
        }

        String bestLapStr = bestLap == Long.MAX_VALUE ? "—" : bestLap + " ticks  (CAR" + String.format("%02d", bestLapCar) + ")";
        String avgLapStr  = lapCount > 0 ? (totalLapSum / lapCount) + " ticks" : "—";

        y = drawStatRow(g2, x, y, w, "BEST LAP",    bestLapStr,  GREEN);
        y = drawStatRow(g2, x, y, w, "AVG LAP",     avgLapStr,   BLUE);
        y = drawStatRow(g2, x, y, w, "CRASHES",     String.valueOf(engine.getTotalCrashes()),   ACCENT);
        y = drawStatRow(g2, x, y, w, "OVERTAKES",   String.valueOf(engine.getTotalOvertakes()), YELLOW);
        y = drawStatRow(g2, x, y, w, "BEST FITNESS",
                pop.getBestAllTimeFitness() == Double.NEGATIVE_INFINITY ? "—"
                        : String.format("%.0f (Race %d)", pop.getBestAllTimeFitness(), pop.getBestAllTimeGen()),
                GREEN);
        return y;
    }

    private int drawStatRow(Graphics2D g2, int x, int y, int w, String label, String value, Color col) {
        int h = 26;
        g2.setColor(CARD_BG);
        g2.fillRoundRect(x, y, w, h, 4, 4);
        g2.setColor(TEXT_DIM);
        g2.setFont(F_LABEL);
        g2.drawString(label, x + 6, y + 10);
        g2.setFont(F_VALUE);
        g2.setColor(col);
        // Truncate long values
        String display = value.length() > 22 ? value.substring(0, 22) + "…" : value;
        g2.drawString(display, x + 6, y + 22);
        return y + h + 2;
    }

    // ── Fitness chart ─────────────────────────────────────────────────────────

    private void drawFitnessChart(Graphics2D g2, int x, int y, int w) {
        int h = getHeight() - y - 8;
        if (h < 50) return;

        g2.setColor(CARD_BG);
        g2.fillRoundRect(x, y, w, h, 8, 8);
        g2.setColor(DIVIDER);
        g2.drawRoundRect(x, y, w, h, 8, 8);

        g2.setFont(F_LABEL);
        g2.setColor(TEXT_DIM);
        g2.drawString("FITNESS PER RACE", x + 6, y + 12);

        List<Double> best = pop.getBestFitnessHistory();
        List<Double> avg  = pop.getAvgFitnessHistory();
        if (best.size() < 2) return;

        int pad = 14;
        int cw = w - 2 * pad, ch = h - 2 * pad - 4;

        double maxVal = 1;
        for (double v : best) if (v > maxVal) maxVal = v;
        for (double v : avg)  if (v > maxVal) maxVal = v;

        // Grid
        g2.setColor(new Color(45, 45, 65));
        g2.setStroke(new BasicStroke(0.5f));
        for (int i = 1; i < 4; i++) {
            int gy = y + pad + 4 + (int)(ch * i / 4.0);
            g2.drawLine(x + pad, gy, x + pad + cw, gy);
        }

        plotSeries(g2, best, x + pad, y + pad + 4, cw, ch, maxVal, GREEN,  1.8f);
        plotSeries(g2, avg,  x + pad, y + pad + 4, cw, ch, maxVal, YELLOW, 1.2f);

        g2.setFont(F_LABEL);
        g2.setColor(GREEN);  g2.drawString("▪ Best", x + pad, y + h - 3);
        g2.setColor(YELLOW); g2.drawString("▪ Avg",  x + pad + 44, y + h - 3);
    }

    private void plotSeries(Graphics2D g2, List<Double> data, int ox, int oy,
                             int w, int h, double maxVal, Color col, float stroke) {
        if (data.size() < 2) return;
        g2.setColor(col);
        g2.setStroke(new BasicStroke(stroke));
        int n = data.size();
        for (int i = 1; i < n; i++) {
            int x1 = ox + (int)((i-1) * (double) w / (n-1));
            int y1 = oy + h - (int)(data.get(i-1) / maxVal * h);
            int x2 = ox + (int)(i * (double) w / (n-1));
            int y2 = oy + h - (int)(data.get(i)   / maxVal * h);
            g2.drawLine(x1, y1, x2, y2);
        }
    }
}
