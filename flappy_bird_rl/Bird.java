package flappy_bird_rl;

public class Bird {
    private double y;
    private double velocity;
    private boolean isAlive;
    private int score; // Surviving ticks
    private NeuralNetwork brain;
    
    public Bird(NeuralNetwork brain) {
        this.y = Config.WINDOW_HEIGHT / 2.0;
        this.velocity = 0;
        this.isAlive = true;
        this.score = 0;
        this.brain = brain;
    }
    
    public void update() {
        if (!isAlive) return;
        
        velocity += Config.GRAVITY;
        if (velocity > Config.MAX_VELOCITY) velocity = Config.MAX_VELOCITY;
        y += velocity;
        score++;
        
        // Die if hitting top or bottom
        if (y < 0 || y + Config.BIRD_SIZE > Config.WINDOW_HEIGHT) {
            isAlive = false;
        }
    }
    
    public void jump() {
        if (isAlive) {
            velocity = Config.JUMP_STRENGTH;
        }
    }
    
    public void kill() {
        this.isAlive = false;
    }
    
    // Getters
    public double getY() { return y; }
    public double getVelocity() { return velocity; }
    public boolean isAlive() { return isAlive; }
    public int getScore() { return score; }
    public NeuralNetwork getBrain() { return brain; }
}
