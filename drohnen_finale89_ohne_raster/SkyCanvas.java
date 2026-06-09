import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import javax.swing.JPanel;

/**
 * Zeichenflaeche: Rendert den Nachthimmel mit Sternen, Feuerwerk
 * und allen Drohnen inklusive Glow-Effekten.
 */
public class SkyCanvas extends JPanel {

    ArrayList<Drohne> drohnen;
    int width, height;
    int frame = 0;

    // Vorberechnete Sterne
    int[][] stars;

    // Double-Buffer
    BufferedImage buffer;
    Graphics2D bufferG;

    SkyCanvas(ArrayList<Drohne> drohnen, int width, int height) {
        this.drohnen = drohnen;
        this.width = width;
        this.height = height;
        setPreferredSize(new Dimension(width, height));
        setBackground(Color.BLACK);

        // Sterne generieren
        stars = new int[350][3];
        for (int i = 0; i < stars.length; i++) {
            stars[i][0] = (int) (Math.random() * width);
            stars[i][1] = (int) (Math.random() * height);
            stars[i][2] = 30 + (int) (Math.random() * 130);
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        if (buffer == null || buffer.getWidth() != getWidth()) {
            buffer = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_ARGB);
            bufferG = buffer.createGraphics();
            bufferG.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        }

        Graphics2D g2 = bufferG;

        // === 1. Nachthimmel-Gradient ===
        GradientPaint sky = new GradientPaint(
                0, 0, new Color(3, 3, 18),
                0, height, new Color(10, 12, 40));
        g2.setPaint(sky);
        g2.fillRect(0, 0, width, height);

        // === 2. Sterne mit Funkeln ===
        for (int[] star : stars) {
            int twinkle = (int) (star[2] + 30 * Math.sin(frame * 0.02 + star[0] * 0.1));
            twinkle = Math.max(15, Math.min(200, twinkle));
            g2.setColor(new Color(255, 255, 255, twinkle));
            g2.fillRect(star[0], star[1], 1, 1);
        }


        // === 4. Drohnen-Glow (erster Durchlauf) ===
        for (Drohne d : drohnen) {
            if (!d.launched) continue;
            Color glow = d.getGlowColor(frame);
            g2.setColor(glow);
            int glowSize = d.arrived ? 10 : 7;
            g2.fillOval((int) (d.x - glowSize / 2.0), (int) (d.y - glowSize / 2.0), glowSize, glowSize);
        }

        // === 5. Drohnen-Kern (zweiter Durchlauf) ===
        for (Drohne d : drohnen) {
            if (!d.launched) continue;
            Color core = d.getCurrentColor(frame);
            g2.setColor(core);
            int coreSize = d.arrived ? 3 : 2;
            g2.fillOval((int) (d.x - coreSize / 2.0), (int) (d.y - coreSize / 2.0), coreSize, coreSize);
        }

        // === 6. Info-Overlay (blendet ein und wieder aus) ===
        if (frame < 180) {
            int alpha = frame < 120 ? Math.min(200, frame * 3) : Math.max(0, 200 - (frame - 120) * 4);
            if (alpha > 0) {
                g2.setColor(new Color(255, 215, 0, alpha));
                g2.setFont(new Font("SansSerif", Font.BOLD, 16));
                FontMetrics fm = g2.getFontMetrics();
                String info = "DROHNEN-SHOW STARTET...";
                g2.drawString(info, (width - fm.stringWidth(info)) / 2, height - 30);
            }
        }

        // === 7. Fortschrittsanzeige ===
        long arrivedCount = 0;
        long launchedCount = 0;
        for (Drohne d : drohnen) {
            if (d.arrived) arrivedCount++;
            if (d.launched) launchedCount++;
        }
        if (launchedCount > 0 && arrivedCount < drohnen.size()) {
            int percent = (int) (100.0 * arrivedCount / drohnen.size());
            g2.setColor(new Color(255, 255, 255, 80));
            g2.setFont(new Font("SansSerif", Font.PLAIN, 12));
            g2.drawString("Formation: " + percent + "%  |  Drohnen: " + drohnen.size(), 10, height - 10);
        }

        g.drawImage(buffer, 0, 0, null);
        frame++;
    }
}
