package flappy_bird;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

public class SimulationEngine {
    private List<Bird> birds;
    private List<Pipe> pipes;
    private int tickCount;
    private Random random;
    
    public SimulationEngine() {
        birds = new CopyOnWriteArrayList<>();
        pipes = new CopyOnWriteArrayList<>();
        random = new Random();
    }
    
    public void reset(List<NeuralNetwork> brains) {
        birds.clear();
        pipes.clear();
        tickCount = 0;
        
        for (NeuralNetwork brain : brains) {
            birds.add(new Bird(brain));
        }
    }
    
    public void update() {
        tickCount++;
        
        // Spawn pipes
        if (tickCount % Config.PIPE_SPAWN_INTERVAL == 0 || pipes.isEmpty()) {
            double minGapY = 50;
            double maxGapY = Config.WINDOW_HEIGHT - Config.PIPE_GAP - 50;
            double gapY = minGapY + random.nextDouble() * (maxGapY - minGapY);
            pipes.add(new Pipe(gapY));
        }
        
        // Update pipes
        for (int i = pipes.size() - 1; i >= 0; i--) {
            Pipe p = pipes.get(i);
            p.update();
            if (p.isOffScreen()) {
                pipes.remove(i);
            }
        }
        
        // Find the next upcoming pipe
        Pipe nextPipe = getNextPipe();
        
        // Update birds and process Neural Network
        for (Bird bird : birds) {
            if (!bird.isAlive()) continue;
            
            // Feed sensors to brain
            double[] sensors = getSensors(bird, nextPipe);
            double[] outputs = bird.getBrain().feedForward(sensors);
            
            // Output > 0.5 means jump
            if (outputs[0] > 0.5) {
                bird.jump();
            }
            
            bird.update();
            checkCollision(bird, nextPipe);
        }
    }
    
    private Pipe getNextPipe() {
        for (Pipe p : pipes) {
            if (p.getX() + Config.PIPE_WIDTH > Config.BIRD_X) {
                return p;
            }
        }
        return null;
    }
    
    private double[] getSensors(Bird bird, Pipe nextPipe) {
        double[] sensors = new double[Config.INPUT_SIZE];
        if (nextPipe != null) {
            // 1. Distance to pipe
            sensors[0] = (nextPipe.getX() - Config.BIRD_X) / Config.WINDOW_WIDTH;
            // 2. Height difference to center of gap
            double gapCenterY = nextPipe.getGapY() + (Config.PIPE_GAP / 2.0);
            sensors[1] = (bird.getY() - gapCenterY) / Config.WINDOW_HEIGHT;
        } else {
            sensors[0] = 1.0;
            sensors[1] = 0.0;
        }
        // 3. Current velocity
        sensors[2] = bird.getVelocity() / Config.MAX_VELOCITY;
        return sensors;
    }
    
    private void checkCollision(Bird bird, Pipe nextPipe) {
        if (!bird.isAlive() || nextPipe == null) return;
        
        // Horizontal bounds check
        boolean inPipeX = Config.BIRD_X + Config.BIRD_SIZE > nextPipe.getX() && 
                          Config.BIRD_X < nextPipe.getX() + Config.PIPE_WIDTH;
                          
        if (inPipeX) {
            // Vertical bounds check (hitting top pipe or bottom pipe)
            boolean hittingTopPipe = bird.getY() < nextPipe.getGapY();
            boolean hittingBottomPipe = bird.getY() + Config.BIRD_SIZE > nextPipe.getGapY() + Config.PIPE_GAP;
            
            if (hittingTopPipe || hittingBottomPipe) {
                bird.kill();
            }
        }
    }
    
    public List<Bird> getBirds() { return birds; }
    public List<Pipe> getPipes() { return pipes; }
    public boolean isAllDead() {
        for (Bird b : birds) {
            if (b.isAlive()) return false;
        }
        return true;
    }
    public int getAliveCount() {
        int count = 0;
        for (Bird b : birds) if (b.isAlive()) count++;
        return count;
    }
}
