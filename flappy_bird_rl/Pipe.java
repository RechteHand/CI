package flappy_bird_rl;

public class Pipe {
    private double x;
    private final double gapY; // Top Y coordinate of the gap
    private boolean passed; // Has the bird passed this pipe?
    
    public Pipe(double gapY) {
        this.x = Config.PIPE_SPAWN_X;
        this.gapY = gapY;
        this.passed = false;
    }
    
    public void update() {
        x -= Config.PIPE_SPEED;
    }
    
    public boolean isOffScreen() {
        return x + Config.PIPE_WIDTH < 0;
    }
    
    public double getX() { return x; }
    public double getGapY() { return gapY; }
    public boolean isPassed() { return passed; }
    public void setPassed(boolean p) { this.passed = p; }
}
