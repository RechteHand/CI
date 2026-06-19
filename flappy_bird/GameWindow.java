package flappy_bird;

import javax.swing.*;
import java.awt.*;

public class GameWindow extends JFrame {
    private SimulationEngine engine;
    private PopulationManager pop;
    private GamePanel panel;
    
    public GameWindow() {
        setTitle("Flappy Bird AI Swarm");
        setSize(Config.WINDOW_WIDTH, Config.WINDOW_HEIGHT);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        
        engine = new SimulationEngine();
        pop = new PopulationManager();
        engine.reset(pop.getPopulation());
        
        panel = new GamePanel(engine, pop);
        add(panel, BorderLayout.CENTER);
        
        // Controls panel
        JPanel controls = new JPanel();
        controls.setBackground(Color.DARK_GRAY);
        
        JCheckBox fastForward = new JCheckBox("Turbo Mode");
        fastForward.setForeground(Color.WHITE);
        fastForward.setBackground(Color.DARK_GRAY);
        fastForward.addActionListener(e -> Config.FAST_FORWARD = fastForward.isSelected());
        controls.add(fastForward);
        
        JSlider speedSlider = new JSlider(10, 240, Config.FPS);
        speedSlider.setBackground(Color.DARK_GRAY);
        speedSlider.setForeground(Color.WHITE);
        speedSlider.setMajorTickSpacing(60);
        speedSlider.setPaintLabels(true);
        speedSlider.addChangeListener(e -> Config.FPS = speedSlider.getValue());
        
        JLabel fpsLabel = new JLabel(" FPS:");
        fpsLabel.setForeground(Color.WHITE);
        controls.add(fpsLabel);
        controls.add(speedSlider);
        
        add(controls, BorderLayout.SOUTH);
        
        setVisible(true);
        startGameLoop();
    }
    
    private void startGameLoop() {
        new Thread(() -> {
            while (true) {
                engine.update();
                
                if (engine.isAllDead()) {
                    pop.evolve(engine.getBirds());
                    engine.reset(pop.getPopulation());
                }
                
                if (!Config.FAST_FORWARD) {
                    panel.repaint();
                    try {
                        Thread.sleep(1000 / Config.FPS);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                } else {
                    // Update UI occasionally during turbo
                    if (engine.getBirds().get(0).getScore() % 100 == 0) {
                        panel.repaint();
                    }
                }
            }
        }).start();
    }
}
