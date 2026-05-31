package f1_rl;

/**
 * Central configuration for the F1 Racing Simulation.
 * All tuneable parameters are defined here so nothing is hard-coded elsewhere.
 */
public final class Config {

    private Config() {} // utility class

    // ── Window ───────────────────────────────────────────────────────────────
    // MacBook Air M1 13" — default logical resolution 1280×800
    // Subtracting space for macOS menu bar and dock.
    public static final int WINDOW_WIDTH    = 1260;
    public static final int WINDOW_HEIGHT   = 780;
    public static final int DASHBOARD_WIDTH = 280;

    // ── Race format ───────────────────────────────────────────────────────────
    /** Number of AI drivers competing simultaneously. */
    public static int NUM_DRIVERS  = 10;
    /** Laps each driver must complete to finish the race. */
    public static int NUM_LAPS     = 3;

    // ── Track ─────────────────────────────────────────────────────────────────
    public static final int   TRACK_WIDTH      = 80;   // road width in pixels
    public static final int   BEZIER_STEPS     = 700;  // spline resolution
    public static final int   GRID_SCALE       = 3;    // pixels per grid cell
    public static final float CHECKPOINT_REACH = 55f;  // radius to collect a CP
    public static final float CHECKPOINT_SPACING = 65f; // distance between CPs
    /** Coordinate space the CTRL points were authored in – used for scaling. */
    public static final int   TRACK_DESIGN_W    = 1000;
    public static final int   TRACK_DESIGN_H    = 720;

    // ── Car physics ───────────────────────────────────────────────────────────
    public static final float CAR_MAX_SPEED    = 5.5f;
    public static final float CAR_ACCEL        = 0.13f;
    public static final float CAR_BRAKE        = 0.07f;
    public static final float CAR_FRICTION     = 0.974f;
    public static final float CAR_STEER_SPEED  = 0.046f;
    public static final float CAR_INIT_SPEED   = 2.0f;
    public static final float CAR_LENGTH       = 22f;
    public static final float CAR_WIDTH        = 11f;
    /** Grid stagger: cars start this many pixels apart along the S/F straight. */
    public static final float GRID_STAGGER     = 28f;

    // ── Collision ─────────────────────────────────────────────────────────────
    public static final float CAR_COLLISION_RADIUS  = 14f;
    public static final int   COLLISION_COOLDOWN    = 40;  // ticks of immunity after crash
    public static final float COLLISION_SPEED_PENALTY = 0.60f; // multiply speed by this on crash

    // ── Drafting (Windschatten) ───────────────────────────────────────────────
    public static final float DRAFT_DISTANCE    = 120f;   // max px behind car ahead
    public static final float DRAFT_HALF_ANGLE  = 22f;    // degrees half-cone
    public static final float DRAFT_SPEED_BOOST = 0.14f;  // fraction of max speed added
    public static final float DRAFT_MOMENTUM    = 0.96f;  // speed at which draft fades/tick

    // ── Neural network ────────────────────────────────────────────────────────
    public static final int NN_INPUTS  = 16; // 7 wall + 4 opp + draft + speed + cp_angle + cp_dist + gas_prev
    public static final int NN_HIDDEN  = 12;
    public static final int NN_OUTPUTS = 2;  // gas, steer

    // ── Sensors ───────────────────────────────────────────────────────────────
    public static final int   NUM_WALL_RAYS = 7;
    public static final float RAY_MAX_LEN   = 200f;
    public static final float[] RAY_ANGLES  = {-90f, -45f, -20f, 0f, 20f, 45f, 90f};

    // ── Genetic algorithm ─────────────────────────────────────────────────────
    public static final double MUTATION_RATE     = 0.18;
    public static final double MUTATION_STRENGTH = 0.50;
    public static final int    ELITE_COUNT       = 2;   // top N copied unchanged

    // ── Simulation ──────────────────────────────────────────────────────────────
    /** A car is DNF if it hasn't collected a checkpoint in this many ticks. */
    public static final int STUCK_TICKS         = 500;
    /** Race hard-limit: race ends after this many ticks even if nobody finishes. */
    public static final int RACE_TIMEOUT_TICKS  = 25_000;
    /** If best fitness doesn't improve for this many consecutive races, boost mutation. */
    public static final int STAGNATION_THRESHOLD = 15;

    // ── Runtime UI flags (mutable) ────────────────────────────────────────────
    public static int     FPS          = 60;
    public static boolean FAST_FORWARD = false;
    public static boolean FOLLOW_LEADER = false;
}
