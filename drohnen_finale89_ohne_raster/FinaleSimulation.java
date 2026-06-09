import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import javax.imageio.ImageIO;
import javax.swing.*;

/**
 * Konfigurierbare Drohnen-Lichtershow.
 *
 * VERWENDUNG:
 *   java FinaleSimulation <logo_links> <logo_rechts> <team_links> <team_rechts> <tore_links> <tore_rechts> <titel> [untertitel]
 *
 * BEISPIEL (UEFA Cup 1989):
 *   java FinaleSimulation napoli.png vfb.png "SSC NAPOLI" "VfB STUTTGART" 2 1 "UEFA CUP FINALE 1988/89" "Stadio San Paolo, Neapel"
 *
 * BEISPIEL (Champions League 2025):
 *   java FinaleSimulation bayern.png psg.png "FC BAYERN" "PARIS SG" 3 2 "CHAMPIONS LEAGUE FINALE 2025" "Wembley Stadium, London"
 *
 * Ohne Argumente wird das historische Napoli vs VfB 1989 Ergebnis angezeigt.
 */
public class FinaleSimulation extends JFrame implements Runnable {

    static final int WIDTH = 1280;
    static final int HEIGHT = 720;
    static final int SAMPLE_STEP = 5;
    static final int TARGET_FPS = 60;

    // Konfigurierbare Parameter
    String logoLinksPath;
    String logoRechtsPath;
    String teamLinks;
    String teamRechts;
    String toreLinks;
    String toreRechts;
    String titel;
    String untertitel;

    ArrayList<Drohne> drohnen = new ArrayList<>();
    SkyCanvas canvas;
    int frame = 0;

    FinaleSimulation(String logoLinksPath, String logoRechtsPath,
                     String teamLinks, String teamRechts,
                     String toreLinks, String toreRechts,
                     String titel, String untertitel) {

        this.logoLinksPath = logoLinksPath;
        this.logoRechtsPath = logoRechtsPath;
        this.teamLinks = teamLinks;
        this.teamRechts = teamRechts;
        this.toreLinks = toreLinks;
        this.toreRechts = toreRechts;
        this.titel = titel;
        this.untertitel = untertitel;

        setTitle("Drohnen-Show: " + teamLinks + " " + toreLinks + ":" + toreRechts + " " + teamRechts);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);

        System.out.println("=============================================");
        System.out.println("  DROHNEN-LICHTERSHOW");
        System.out.println("=============================================");
        System.out.println("  " + titel);
        System.out.println("  " + teamLinks + "  " + toreLinks + " - " + toreRechts + "  " + teamRechts);
        if (!untertitel.isEmpty()) System.out.println("  " + untertitel);
        System.out.println("---------------------------------------------");
        System.out.println("  Logo links:  " + logoLinksPath);
        System.out.println("  Logo rechts: " + logoRechtsPath);
        System.out.println("---------------------------------------------");
        System.out.println("  Erstelle Zielformation...");

        // 1. Template-Bild erzeugen
        BufferedImage template = createTemplate();

        // 2. Template abtasten -> Drohnen erzeugen
        sampleTemplate(template);

        // Launch-Reihenfolge shufflen fuer organischen Start
        Collections.shuffle(drohnen);
        for (int i = 0; i < drohnen.size(); i++) {
            drohnen.get(i).launchDelay = (int) (Math.random() * 200);
        }

        System.out.println("  Drohnen erzeugt: " + drohnen.size());
        System.out.println("  Starte Lichtershow...");
        System.out.println("=============================================\n");

        // 3. Canvas & Fenster
        canvas = new SkyCanvas(drohnen, WIDTH, HEIGHT);
        add(canvas);
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    /**
     * Erzeugt das Zielbild dynamisch aus den konfigurierten Parametern.
     */
    BufferedImage createTemplate() {
        BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);

        int logoSize = 230;
        int logoY = HEIGHT / 2 - logoSize / 2 + 20;

        // --- Logo links ---
        BufferedImage logoLinks = loadImage(logoLinksPath);
        if (logoLinks != null) {
            g.drawImage(logoLinks, 50, logoY, logoSize, logoSize, null);
            System.out.println("  Logo links geladen (" + logoLinks.getWidth() + "x" + logoLinks.getHeight() + ")");
        } else {
            drawFallbackCircle(g, 50, logoY, logoSize, new Color(0, 100, 200), teamLinks);
            System.out.println("  Logo links: Fallback (Datei nicht gefunden: " + logoLinksPath + ")");
        }

        // --- Logo rechts ---
        BufferedImage logoRechts = loadImage(logoRechtsPath);
        if (logoRechts != null) {
            g.drawImage(logoRechts, WIDTH - 50 - logoSize, logoY, logoSize, logoSize, null);
            System.out.println("  Logo rechts geladen (" + logoRechts.getWidth() + "x" + logoRechts.getHeight() + ")");
        } else {
            drawFallbackCircle(g, WIDTH - 50 - logoSize, logoY, logoSize, new Color(200, 30, 30), teamRechts);
            System.out.println("  Logo rechts: Fallback (Datei nicht gefunden: " + logoRechtsPath + ")");
        }

        // --- Titel ---
        g.setColor(new Color(255, 215, 0)); // Gold
        int titleFontSize = fitFontSize(g, titel, WIDTH - 200, 52, 28);
        g.setFont(new Font("SansSerif", Font.BOLD, titleFontSize));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(titel, (WIDTH - fm.stringWidth(titel)) / 2, 85);

        // --- Untertitel ---
        if (!untertitel.isEmpty()) {
            g.setColor(new Color(200, 200, 200));
            g.setFont(new Font("SansSerif", Font.ITALIC, 24));
            fm = g.getFontMetrics();
            g.drawString(untertitel, (WIDTH - fm.stringWidth(untertitel)) / 2, 125);
        }

        // --- Spielergebnis ---
        String score = toreLinks + " - " + toreRechts;
        g.setColor(Color.WHITE);
        int scoreFontSize = fitFontSize(g, score, 500, 160, 80);
        g.setFont(new Font("SansSerif", Font.BOLD, scoreFontSize));
        fm = g.getFontMetrics();
        int scoreX = (WIDTH - fm.stringWidth(score)) / 2;
        int scoreY = HEIGHT / 2 + fm.getAscent() / 3;
        g.drawString(score, scoreX, scoreY);

        // --- Teamnamen ---
        g.setFont(new Font("SansSerif", Font.BOLD, 36));
        fm = g.getFontMetrics();

        // Teamname links anpassen falls zu lang
        int teamFontSize = fitFontSize(g, teamLinks, logoSize + 40, 36, 20);
        g.setFont(new Font("SansSerif", Font.BOLD, teamFontSize));
        fm = g.getFontMetrics();
        g.setColor(new Color(100, 180, 255));
        int teamLinksX = 50 + (logoSize - fm.stringWidth(teamLinks)) / 2;
        g.drawString(teamLinks, teamLinksX, logoY + logoSize + 45);

        // Teamname rechts
        teamFontSize = fitFontSize(g, teamRechts, logoSize + 40, 36, 20);
        g.setFont(new Font("SansSerif", Font.BOLD, teamFontSize));
        fm = g.getFontMetrics();
        g.setColor(new Color(255, 120, 120));
        int teamRechtsX = WIDTH - 50 - logoSize + (logoSize - fm.stringWidth(teamRechts)) / 2;
        g.drawString(teamRechts, teamRechtsX, logoY + logoSize + 45);

        g.dispose();
        return img;
    }

    /**
     * Findet die groesste Schriftgroesse die in die verfuegbare Breite passt.
     */
    int fitFontSize(Graphics2D g, String text, int maxWidth, int maxSize, int minSize) {
        for (int size = maxSize; size >= minSize; size--) {
            g.setFont(new Font("SansSerif", Font.BOLD, size));
            if (g.getFontMetrics().stringWidth(text) <= maxWidth) {
                return size;
            }
        }
        return minSize;
    }

    /**
     * Tastet das Template-Bild ab und erzeugt Drohnen fuer jeden sichtbaren Pixel.
     */
    void sampleTemplate(BufferedImage img) {
        int alphaThreshold = 80;

        for (int py = 0; py < img.getHeight(); py += SAMPLE_STEP) {
            for (int px = 0; px < img.getWidth(); px += SAMPLE_STEP) {
                int rgba = img.getRGB(px, py);
                int alpha = (rgba >> 24) & 0xFF;

                if (alpha > alphaThreshold) {
                    int r = (rgba >> 16) & 0xFF;
                    int gr = (rgba >> 8) & 0xFF;
                    int b = rgba & 0xFF;

                    // Dunkle Pixel leicht aufhellen (sonst unsichtbar am Nachthimmel)
                    if (r < 30 && gr < 30 && b < 30) {
                        r = 50; gr = 50; b = 70;
                    }

                    Color targetColor = new Color(r, gr, b);
                    int delay = (int) (Math.random() * 200);
                    drohnen.add(new Drohne(px, py, targetColor, delay, HEIGHT));
                }
            }
        }
    }

    /**
     * Laedt ein Bild (PNG/JPG) aus dem aktuellen oder absoluten Pfad.
     */
    BufferedImage loadImage(String filename) {
        try {
            File f = new File(filename);
            if (!f.exists()) {
                f = new File(System.getProperty("user.dir"), filename);
            }
            if (f.exists() && f.length() > 1000) { // >1KB = wahrscheinlich ein echtes Bild
                BufferedImage img = ImageIO.read(f);
                if (img != null) return img;
            }
        } catch (Exception e) {
            System.err.println("  Warnung: " + filename + " konnte nicht geladen werden: " + e.getMessage());
        }
        return null;
    }

    /**
     * Generischer Fallback: Farbiger Kreis mit Kurzname.
     */
    void drawFallbackCircle(Graphics2D g, int x, int y, int size, Color color, String label) {
        g.setColor(color);
        g.fillOval(x, y, size, size);
        g.setColor(new Color(
                Math.min(255, color.getRed() + 60),
                Math.min(255, color.getGreen() + 60),
                Math.min(255, color.getBlue() + 60)));
        g.fillOval(x + 20, y + 20, size - 40, size - 40);
        g.setColor(Color.WHITE);
        // Maximal 3 Buchstaben als Kuerzel
        String shortLabel = label.length() > 3 ? label.substring(0, 3) : label;
        int fontSize = fitFontSize(g, shortLabel, size - 60, size / 3, 40);
        g.setFont(new Font("SansSerif", Font.BOLD, fontSize));
        FontMetrics fm = g.getFontMetrics();
        g.drawString(shortLabel, x + (size - fm.stringWidth(shortLabel)) / 2,
                y + size / 2 + fm.getAscent() / 3);
    }

    // === MAIN ===

    public static void main(String[] args) {
        // Standardwerte (Napoli vs VfB 1989)
        String logoL = "napoli.png";
        String logoR = "vfb.png";
        String teamL = "SSC NAPOLI";
        String teamR = "VfB STUTTGART";
        String scoreL = "2";
        String scoreR = "1";
        String title = "UEFA CUP FINALE 1988/89";
        String subtitle = "Stadio San Paolo, Neapel";

        if (args.length >= 7) {
            logoL = args[0];
            logoR = args[1];
            teamL = args[2];
            teamR = args[3];
            scoreL = args[4];
            scoreR = args[5];
            title = args[6];
            subtitle = args.length >= 8 ? args[7] : "";
        } else if (args.length > 0 && args.length < 7) {
            System.out.println("VERWENDUNG:");
            System.out.println("  java FinaleSimulation <logo_links> <logo_rechts> <team_links> <team_rechts> <tore_links> <tore_rechts> <titel> [untertitel]");
            System.out.println();
            System.out.println("BEISPIEL:");
            System.out.println("  java FinaleSimulation napoli.png vfb.png \"SSC NAPOLI\" \"VfB STUTTGART\" 2 1 \"UEFA CUP FINALE 1988/89\" \"Stadio San Paolo, Neapel\"");
            System.out.println();
            System.out.println("Ohne Argumente: Napoli 2-1 VfB Stuttgart (1989)");
            return;
        }

        final String fLogoL = logoL, fLogoR = logoR, fTeamL = teamL, fTeamR = teamR;
        final String fScoreL = scoreL, fScoreR = scoreR, fTitle = title, fSub = subtitle;

        SwingUtilities.invokeLater(() -> {
            FinaleSimulation sim = new FinaleSimulation(fLogoL, fLogoR, fTeamL, fTeamR, fScoreL, fScoreR, fTitle, fSub);
            Thread t = new Thread(sim);
            t.start();
        });
    }

    @Override
    public void run() {
        long frameTime = 1000 / TARGET_FPS;

        while (true) {
            long start = System.currentTimeMillis();
            frame++;
            
            for (Drohne d : drohnen) {
                d.update(frame, drohnen);
            }

            canvas.repaint();
            Toolkit.getDefaultToolkit().sync();

            long elapsed = System.currentTimeMillis() - start;
            long sleep = frameTime - elapsed;
            if (sleep > 0) {
                try { Thread.sleep(sleep); } catch (InterruptedException e) { break; }
            }
        }
    }
}
