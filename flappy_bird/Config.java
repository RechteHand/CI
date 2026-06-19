package flappy_bird;

public class Config {
    // Window settings
    public static final int WINDOW_WIDTH = 800;
    public static final int WINDOW_HEIGHT = 600;

    // Physics
    public static final double GRAVITY = 0.6;
    public static final double JUMP_STRENGTH = -8.0;
    public static final double MAX_VELOCITY = 12.0;
    public static final double PIPE_SPEED = 4.0;

    // Entity settings
    public static final int BIRD_SIZE = 20;
    public static final int BIRD_X = 100; // Fixed x position
    public static final int PIPE_WIDTH = 60;
    public static final int PIPE_GAP = 160;
    public static final int PIPE_SPAWN_X = WINDOW_WIDTH;
    public static final int PIPE_SPAWN_INTERVAL = 100; // frames between pipes

    // Neural Network
    public static final int INPUT_SIZE = 3; // dx to pipe, dy to center of gap, current velocity
    public static final int HIDDEN_SIZE = 6;
    public static final int OUTPUT_SIZE = 1; // > 0.5 means JUMP

    // Genetics
    public static final int POPULATION_SIZE = 5; // Reduced to show learning curve
    public static final double MUTATION_RATE = 0.15;
    public static final double MUTATION_STRENGTH = 0.5; // Stdev of gaussian noise

    // Game state
    public static int FPS = 60;
    public static boolean FAST_FORWARD = false;
}
