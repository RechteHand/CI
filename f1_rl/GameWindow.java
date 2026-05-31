package f1_rl;

import javax.swing.*;
import java.awt.*;

/**
 * Main application window.
 *
 * <p>The track always fills the full available space. The dashboard can be
 * toggled open/closed via the "Dashboard" button — when hidden the track
 * expands to the full window width.</p>
 */
public class GameWindow extends JFrame {

    private final RaceEngine        engine;
    private final PopulationManager pop;
    private final TrackPanel        trackPanel;
    private final DashboardPanel    dashPanel;

    private boolean dashVisible = true;

    // ── Construction ──────────────────────────────────────────────────────────

    public GameWindow() {
        super("F1 AI Racing Simulation");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        System.out.println("Building track (baking grid)...");
        Track track = new Track();
        System.out.printf("Track ready — %d checkpoints%n", track.getNumCheckpoints());

        pop    = new PopulationManager();
        engine = new RaceEngine(track);
        engine.startRace(pop.getBrains());

        trackPanel = new TrackPanel(engine, pop);
        dashPanel  = new DashboardPanel(engine, pop);
        dashPanel.setPreferredSize(new Dimension(Config.DASHBOARD_WIDTH, 0));

        // Main layout: track centre, dashboard east (collapsible)
        JPanel content = new JPanel(new BorderLayout());
        content.add(trackPanel, BorderLayout.CENTER);
        content.add(dashPanel,  BorderLayout.EAST);
        content.add(buildControlBar(), BorderLayout.SOUTH);

        setContentPane(content);

        // Fill the usable screen area (accounts for macOS menu bar and dock)
        java.awt.Rectangle screen = java.awt.GraphicsEnvironment
                .getLocalGraphicsEnvironment().getMaximumWindowBounds();
        setSize(screen.width, screen.height);
        setLocation(screen.x, screen.y);
        setResizable(true);
        setVisible(true);

        startRepaintTimer();
        startSimulationThread();
    }

    // ── Control bar ───────────────────────────────────────────────────────────

    private JPanel buildControlBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 5));
        bar.setBackground(new Color(12, 12, 18));

        // ── Dashboard toggle ──────────────────────────────────────────────────
        JButton dashBtn = styledButton("◀ Dashboard");
        dashBtn.addActionListener(e -> {
            dashVisible = !dashVisible;
            dashPanel.setVisible(dashVisible);
            dashBtn.setText(dashVisible ? "◀ Dashboard" : "▶ Dashboard");
            revalidate();
        });

        // ── Camera toggle ─────────────────────────────────────────────────────
        JButton camBtn = styledButton("📷 Follow Leader");
        camBtn.addActionListener(e -> {
            Config.FOLLOW_LEADER = !Config.FOLLOW_LEADER;
            camBtn.setText(Config.FOLLOW_LEADER ? "🗺  Track View" : "📷 Follow Leader");
        });

        // ── Turbo mode ────────────────────────────────────────────────────────
        JCheckBox turbo = new JCheckBox("⚡ Turbo");
        turbo.setForeground(new Color(255, 200, 0));
        turbo.setBackground(new Color(12, 12, 18));
        turbo.setFont(new Font("SansSerif", Font.BOLD, 12));
        turbo.addActionListener(e -> Config.FAST_FORWARD = turbo.isSelected());

        // ── Speed slider ──────────────────────────────────────────────────────
        JLabel speedLbl = new JLabel("Speed:");
        speedLbl.setForeground(Color.LIGHT_GRAY);
        speedLbl.setFont(new Font("SansSerif", Font.PLAIN, 12));

        JSlider slider = new JSlider(10, 240, Config.FPS);
        slider.setBackground(new Color(12, 12, 18));
        slider.setForeground(Color.WHITE);
        slider.setMajorTickSpacing(60);
        slider.setPaintTicks(true);
        slider.setPaintLabels(true);
        slider.addChangeListener(e -> Config.FPS = slider.getValue());

        bar.add(dashBtn);
        bar.add(camBtn);
        bar.add(turbo);
        bar.add(speedLbl);
        bar.add(slider);
        return bar;
    }

    private JButton styledButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("SansSerif", Font.BOLD, 12));
        btn.setBackground(new Color(35, 35, 55));
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createEmptyBorder(5, 12, 5, 12));
        return btn;
    }

    // ── Timers & threads ──────────────────────────────────────────────────────

    private void startRepaintTimer() {
        new Timer(16, e -> {
            trackPanel.repaint();
            if (dashVisible) dashPanel.repaint();
        }).start();
    }

    private void startSimulationThread() {
        Thread sim = new Thread(() -> {
            while (true) {
                int batch = Config.FAST_FORWARD ? 25 : 1;
                for (int i = 0; i < batch; i++) {
                    engine.tick();
                    if (engine.isRaceOver()) {
                        pop.evolve(engine);
                        engine.startRace(pop.getBrains());
                        break;
                    }
                }
                if (!Config.FAST_FORWARD) {
                    try { Thread.sleep(1000L / Config.FPS); }
                    catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
                }
            }
        }, "SimulationThread");
        sim.setDaemon(true);
        sim.start();
    }
}
