package flappy_bird;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Image;
import java.awt.RadialGradientPaint;
import java.awt.MultipleGradientPaint;
import java.awt.geom.Point2D;
import java.awt.AlphaComposite;
import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;

public class GamePanel extends JPanel {
    private SimulationEngine engine;
    private PopulationManager pop;
    
    // Assets
    private Image birdImg;
    private Image pipeImg;
    private Image bgImg;
    private int bgX = 0;
    
    public GamePanel(SimulationEngine engine, PopulationManager pop) {
        this.engine = engine;
        this.pop = pop;
        setBackground(new Color(135, 206, 235)); // Fallback
        
        // Load assets
        try {
            birdImg = ImageIO.read(new File("flappy_bird/res/bird.png"));
            pipeImg = ImageIO.read(new File("flappy_bird/res/pipe.png"));
            bgImg = ImageIO.read(new File("flappy_bird/res/background.png"));
        } catch (IOException e) {
            System.err.println("Fehler beim Laden der Grafiken aus flappy_bird/res/");
        }
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // Draw Parallax Background
        if (bgImg != null) {
            int bgWidth = Config.WINDOW_WIDTH; // Or image width
            if (!Config.FAST_FORWARD) bgX -= 1; // Scroll speed
            if (bgX <= -bgWidth) bgX = 0;
            
            g2d.drawImage(bgImg, bgX, 0, bgWidth, Config.WINDOW_HEIGHT, null);
            g2d.drawImage(bgImg, bgX + bgWidth, 0, bgWidth, Config.WINDOW_HEIGHT, null);
        }
        
        // Draw Pipes
        for (Pipe p : engine.getPipes()) {
            int px = (int) p.getX();
            int py = (int) p.getGapY();
            
            if (pipeImg != null) {
                // Top pipe (flipped vertically)
                g2d.drawImage(pipeImg, px, py, px + Config.PIPE_WIDTH, 0, 
                              0, 0, pipeImg.getWidth(null), pipeImg.getHeight(null), null);
                              
                // Bottom pipe
                g2d.drawImage(pipeImg, px, py + Config.PIPE_GAP, Config.PIPE_WIDTH, Config.WINDOW_HEIGHT - (py + Config.PIPE_GAP), null);
            } else {
                g2d.setColor(new Color(34, 139, 34));
                g2d.fillRect(px, 0, Config.PIPE_WIDTH, py);
                g2d.fillRect(px, py + Config.PIPE_GAP, Config.PIPE_WIDTH, Config.WINDOW_HEIGHT - (py + Config.PIPE_GAP));
            }
        }
        
        // Draw Birds
        Bird bestBird = null;
        int bestScore = -1;
        
        // First pass: find best bird
        for (Bird b : engine.getBirds()) {
            if (!b.isAlive()) continue;
            if (b.getScore() > bestScore) {
                bestScore = b.getScore();
                bestBird = b;
            }
        }
        
        // Second pass: Draw all alive birds (semi-transparent)
        AlphaComposite alpha = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f);
        for (Bird b : engine.getBirds()) {
            if (!b.isAlive() || b == bestBird) continue;
            
            if (birdImg != null) {
                g2d.setComposite(alpha);
                g2d.drawImage(birdImg, Config.BIRD_X, (int) b.getY(), Config.BIRD_SIZE, Config.BIRD_SIZE, null);
                g2d.setComposite(AlphaComposite.SrcOver);
            } else {
                g2d.setColor(new Color(255, 255, 0, 100));
                g2d.fillOval(Config.BIRD_X, (int) b.getY(), Config.BIRD_SIZE, Config.BIRD_SIZE);
            }
        }
        
        // Draw best bird completely opaque and with a glowing halo
        if (bestBird != null) {
            // Draw Halo
            int cx = Config.BIRD_X + Config.BIRD_SIZE/2;
            int cy = (int) bestBird.getY() + Config.BIRD_SIZE/2;
            RadialGradientPaint halo = new RadialGradientPaint(
                new Point2D.Float(cx, cy), Config.BIRD_SIZE * 1.5f,
                new float[]{0.0f, 1.0f}, new Color[]{new Color(255, 255, 255, 200), new Color(255, 255, 255, 0)}
            );
            g2d.setPaint(halo);
            g2d.fillOval(cx - Config.BIRD_SIZE*2, cy - Config.BIRD_SIZE*2, Config.BIRD_SIZE*4, Config.BIRD_SIZE*4);
            
            if (birdImg != null) {
                g2d.drawImage(birdImg, Config.BIRD_X, (int) bestBird.getY(), Config.BIRD_SIZE, Config.BIRD_SIZE, null);
            } else {
                g2d.setColor(Color.RED);
                g2d.fillOval(Config.BIRD_X, (int) bestBird.getY(), Config.BIRD_SIZE, Config.BIRD_SIZE);
            }
        }
        
        // Draw HUD
        g2d.setColor(Color.BLACK);
        g2d.setFont(g2d.getFont().deriveFont(16f));
        g2d.drawString("Generation: " + pop.getGeneration(), 10, 20);
        g2d.drawString("Alive: " + engine.getAliveCount() + " / " + Config.POPULATION_SIZE, 10, 40);
        g2d.drawString("Score: " + (bestBird != null ? bestBird.getScore() : 0), 10, 60);
    }
}
