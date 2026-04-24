// Visualisierung: Strecke + alte Trails + aktuelles Auto + Statistik.
import java.awt.*;
import java.util.ArrayList;
import javax.swing.JPanel;

public class Canvas extends JPanel {
    Track          track;
    ArrayList<Car> cars;
    Simulation     sim;

    Canvas(Track track, ArrayList<Car> cars, Simulation sim){
        this.track = track;
        this.cars  = cars;
        this.sim   = sim;
        setBackground(new Color(240, 240, 240));
    }

    public void paintComponent(Graphics g){
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Korridor-Hintergrund (weiss)
        g2.setColor(Color.WHITE);
        g2.fillRect((int)Track.X0, (int)Track.Y0,
                    (int)(Track.X1 - Track.X0), (int)(Track.Y1 - Track.Y0));

        // --- Trails alter Episoden (faden mit Alter, Farbe nach Outcome) ---
        int n = sim.trails.size();
        for(int i=0; i<n; i++){
            Trail t = sim.trails.get(i);
            int alpha = 25 + (int)(180 * (i+1.0)/n);   // neuere Trails kraeftiger
            Color c;
            switch(t.outcome){
                case 1:  c = new Color(0, 170, 0, alpha);   break;   // Ziel  = gruen
                case 2:  c = new Color(220, 140, 0, alpha); break;   // Timeout = orange
                default: c = new Color(220, 60, 60, alpha); break;   // Crash = rot
            }
            g2.setColor(c);
            g2.setStroke(new BasicStroke(1.5f));
            for(int j=1; j<t.points.size(); j++){
                double[] p1 = t.points.get(j-1);
                double[] p2 = t.points.get(j);
                g2.drawLine((int)p1[0], (int)p1[1], (int)p2[0], (int)p2[1]);
            }
        }

        // --- Waende ---
        g2.setColor(Color.BLACK);
        g2.setStroke(new BasicStroke(3));
        for(double[] w : track.walls)
            g2.drawLine((int)w[0], (int)w[1], (int)w[2], (int)w[3]);

        // --- Checkpoints (grau, gestrichelt) ---
        g2.setColor(new Color(190, 190, 190));
        g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                                     1f, new float[]{4f, 4f}, 0f));
        for(double[] cp : track.checkpoints)
            g2.drawLine((int)cp[0], (int)cp[1], (int)cp[2], (int)cp[3]);

        // --- Ziel-Linie (gruen, dick) ---
        g2.setColor(new Color(0, 160, 0));
        g2.setStroke(new BasicStroke(4));
        double[] f = track.finishLine;
        g2.drawLine((int)f[0], (int)f[1], (int)f[2], (int)f[3]);

        // --- Start-Marker ---
        g2.setColor(new Color(50, 100, 200));
        g2.fillOval((int)track.startPos[0]-6, (int)track.startPos[1]-6, 12, 12);
        g2.setColor(Color.BLACK);
        g2.setFont(new Font("SansSerif", Font.BOLD, 11));
        g2.drawString("START", (int)track.startPos[0]-15, (int)track.startPos[1]-10);
        g2.drawString("ZIEL",  (int)f[0] + (f[0] < 200 ? 8 : -32), (int)((f[1]+f[3])/2) + 4);

        // --- Autos: Sensoren nur fuer Leader, alle anderen blau ---
        g2.setStroke(new BasicStroke(1));
        for(Car car : cars){
            if(car.isLeader){
                g2.setColor(new Color(255, 200, 200));
                for(int s=0; s<Car.N_SENSORS; s++){
                    double a  = car.angle + Car.SENSOR_ANGLES[s];
                    double ex = car.x + car.sensors[s]*Math.cos(a);
                    double ey = car.y + car.sensors[s]*Math.sin(a);
                    g2.drawLine((int)car.x, (int)car.y, (int)ex, (int)ey);
                }
            }
            g2.setColor(car.isLeader ? Color.RED : new Color(40, 80, 200, 200));
            drawCar(g2, car);
        }

        // --- Statistik unter der Strecke ---
        g2.setColor(Color.BLACK);
        g2.setFont(new Font("Monospaced", Font.PLAIN, 13));
        int sy = (int)Track.Y1 + 30;
        g2.drawString(String.format("Episoden:        %d",       sim.episodes),         70, sy);
        g2.drawString(String.format("Epsilon:         %.3f",     sim.agent.epsilon),    70, sy+18);
        g2.drawString(String.format("Best (CPs):      %d / %d",  sim.bestCheckpoints,
                                                                  track.checkpoints.size()), 70, sy+36);
        g2.drawString(String.format("Ziel erreicht:   %d Mal",   sim.finishCount),     420, sy);
        g2.drawString(String.format("Autos / Leader:  %d  (Schritte %d/%d)",
                                                                  cars.size(), sim.steps[0],
                                                                  Simulation.MAX_STEPS),     420, sy+18);
        g2.drawString(String.format("Letzte Episode:  %s",       sim.lastOutcome),     420, sy+36);

        // Legende
        g2.setColor(new Color(220, 60, 60));  g2.fillRect(770, sy-10, 12, 12);
        g2.setColor(Color.BLACK);             g2.drawString("Crash", 790, sy);
        g2.setColor(new Color(220,140,0));    g2.fillRect(770, sy+8, 12, 12);
        g2.setColor(Color.BLACK);             g2.drawString("Timeout", 790, sy+18);
        g2.setColor(new Color(0,170,0));      g2.fillRect(770, sy+26, 12, 12);
        g2.setColor(Color.BLACK);             g2.drawString("Ziel", 790, sy+36);
    }

    private void drawCar(Graphics2D g2, Car car){
        int x1 = (int)(car.x + 10*Math.cos(car.angle));
        int y1 = (int)(car.y + 10*Math.sin(car.angle));
        int x2 = (int)(car.x +  6*Math.cos(car.angle + 2.5));
        int y2 = (int)(car.y +  6*Math.sin(car.angle + 2.5));
        int x3 = (int)(car.x +  6*Math.cos(car.angle - 2.5));
        int y3 = (int)(car.y +  6*Math.sin(car.angle - 2.5));
        Polygon p = new Polygon();
        p.addPoint(x1, y1); p.addPoint(x2, y2); p.addPoint(x3, y3);
        g2.fillPolygon(p);
    }
}
