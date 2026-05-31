package f1_rl;

import java.util.ArrayList;
import java.util.List;

/**
 * Race circuit – Spa-inspired, designed to fill the track panel.
 *
 * IMPORTANT checkpoint invariant:
 *   checkpoint[0] is placed just AHEAD of the last grid slot, so every car
 *   starts behind checkpoint[0] and can always progress forward.
 */
public class Track {

    // ── Control points ────────────────────────────────────────────────────────
    // Wide, flowing 2:1 aspect ratio track. Very smooth curves.
    private static final float[][] CTRL = {
        // Bottom S/F straight (going right)
        { 250, 620}, { 550, 640}, { 850, 620}, {1050, 550},
        // Sweeping right hander (Turn 1 & 2)
        {1140, 450}, {1160, 300}, {1080, 160},
        // Long wavy top straight
        { 900, 100}, { 700, 130}, { 500,  90}, { 300, 120},
        // Very wide sweeping left hander
        { 150, 150}, {  70, 250}, {  60, 380},
        // Smooth S-Curve returning to S/F
        { 100, 480}, { 160, 520}, { 160, 580}
    };

    private final float[][] centreLine;
    private final float[][] checkpoints;
    private       boolean[][] gridBaked;

    /** Pre-computed int arrays for fast polyline rendering. */
    public final int[] centreX, centreY;

    /** Finish-line centre + perpendicular normal (for chequered rendering). */
    public final float finishX, finishY, finishNX, finishNY;

    // ── Constructor ───────────────────────────────────────────────────────────

    public Track() {
        List<float[]> pts = buildSpline();
        centreLine = pts.toArray(new float[0][]);
        int n = centreLine.length;

        centreX = new int[n];
        centreY = new int[n];
        for (int i = 0; i < n; i++) {
            centreX[i] = (int) centreLine[i][0];
            centreY[i] = (int) centreLine[i][1];
        }

        bakeGrid();

        // Checkpoints start AHEAD of the finish line so all grid-slot cars
        // begin behind checkpoint[0].  Finish line is at spline index FINISH_IDX.
        // Checkpoint[0] is at FINISH_IDX + a few steps forward.
        checkpoints = buildCheckpoints(FINISH_IDX + 6);

        // Finish-line geometry (at FINISH_IDX in the spline)
        int fi  = Math.min(FINISH_IDX, n - 2);
        float tx = centreLine[fi + 1][0] - centreLine[fi][0];
        float ty = centreLine[fi + 1][1] - centreLine[fi][1];
        float tl = (float) Math.sqrt(tx * tx + ty * ty) + 1e-9f;
        finishX  = centreLine[fi][0];
        finishY  = centreLine[fi][1];
        finishNX = -ty / tl;
        finishNY =  tx / tl;
    }

    /** Spline index where the finish line is placed (and grid starts behind it). */
    static final int FINISH_IDX = 30;

    // ── Checkpoint builder ────────────────────────────────────────────────────

    /**
     * Builds checkpoints starting from {@code startIdx} and going forward
     * around the full circuit.  This guarantees checkpoint[0] is ahead of all
     * grid-slot cars (which start at indices ≤ FINISH_IDX).
     */
    private float[][] buildCheckpoints(int startIdx) {
        List<float[]> cps = new ArrayList<>();
        int n   = centreLine.length;
        float acc = 0;
        for (int step = 1; step <= n; step++) {
            int cur  = (startIdx + step)     % n;
            int prev = (startIdx + step - 1) % n;
            float dx = centreLine[cur][0] - centreLine[prev][0];
            float dy = centreLine[cur][1] - centreLine[prev][1];
            acc += (float) Math.sqrt(dx * dx + dy * dy);
            if (acc >= Config.CHECKPOINT_SPACING) {
                cps.add(centreLine[cur].clone());
                acc = 0;
            }
        }
        return cps.toArray(new float[0][]);
    }

    // ── Grid baking ───────────────────────────────────────────────────────────

    private void bakeGrid() {
        // Size grid from actual track extents (not window size)
        int maxX = 0, maxY = 0;
        for (float[] p : centreLine) {
            maxX = Math.max(maxX, (int) p[0] + Config.TRACK_WIDTH + 20);
            maxY = Math.max(maxY, (int) p[1] + Config.TRACK_WIDTH + 20);
        }
        int gw = maxX / Config.GRID_SCALE + 4;
        int gh = maxY / Config.GRID_SCALE + 4;
        gridBaked = new boolean[gw][gh];

        float hwSq = (Config.TRACK_WIDTH / 2f) * (Config.TRACK_WIDTH / 2f);
        for (int gx = 0; gx < gw; gx++) {
            for (int gy = 0; gy < gh; gy++) {
                if (minDistToSplineSq(gx * Config.GRID_SCALE, gy * Config.GRID_SCALE) <= hwSq)
                    gridBaked[gx][gy] = true;
            }
        }
    }

    // ── Catmull-Rom spline ────────────────────────────────────────────────────

    private List<float[]> buildSpline() {
        List<float[]> out = new ArrayList<>();
        int n     = CTRL.length;
        int steps = Config.BEZIER_STEPS / n;
        for (int i = 0; i < n; i++) {
            float[] p0 = CTRL[(i - 1 + n) % n];
            float[] p1 = CTRL[i];
            float[] p2 = CTRL[(i + 1) % n];
            float[] p3 = CTRL[(i + 2) % n];
            for (int s = 0; s < steps; s++) {
                float t = (float) s / steps;
                out.add(catmullRom(p0, p1, p2, p3, t));
            }
        }
        return out;
    }

    private float[] catmullRom(float[] p0, float[] p1, float[] p2, float[] p3, float t) {
        float t2 = t * t, t3 = t2 * t;
        float x = 0.5f * ((2*p1[0]) + (-p0[0]+p2[0])*t + (2*p0[0]-5*p1[0]+4*p2[0]-p3[0])*t2 + (-p0[0]+3*p1[0]-3*p2[0]+p3[0])*t3);
        float y = 0.5f * ((2*p1[1]) + (-p0[1]+p2[1])*t + (2*p0[1]-5*p1[1]+4*p2[1]-p3[1])*t2 + (-p0[1]+3*p1[1]-3*p2[1]+p3[1])*t3);
        return new float[]{x, y};
    }

    // ── Distance helpers ──────────────────────────────────────────────────────

    private float minDistToSplineSq(float px, float py) {
        float min = Float.MAX_VALUE;
        int n = centreLine.length;
        for (int i = 0; i < n; i++) {
            float d = segDistSq(px, py,
                    centreLine[i][0], centreLine[i][1],
                    centreLine[(i + 1) % n][0], centreLine[(i + 1) % n][1]);
            if (d < min) min = d;
        }
        return min;
    }

    private float segDistSq(float px, float py, float ax, float ay, float bx, float by) {
        float abx = bx - ax, aby = by - ay;
        float t   = Math.max(0, Math.min(1,
                ((px - ax) * abx + (py - ay) * aby) / (abx * abx + aby * aby + 1e-9f)));
        float cx  = ax + t * abx - px, cy = ay + t * aby - py;
        return cx * cx + cy * cy;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public boolean isOnTrack(float px, float py) {
        int gx = (int) (px / Config.GRID_SCALE);
        int gy = (int) (py / Config.GRID_SCALE);
        if (gx < 0 || gx >= gridBaked.length || gy < 0 || gy >= gridBaked[0].length) return false;
        return gridBaked[gx][gy];
    }

    public float castRay(float ox, float oy, float dx, float dy) {
        float step = 3f, dist = 0f, x = ox, y = oy;
        while (dist < Config.RAY_MAX_LEN) {
            x += dx * step; y += dy * step; dist += step;
            if (!isOnTrack(x, y)) break;
        }
        return Math.min(dist, Config.RAY_MAX_LEN) / Config.RAY_MAX_LEN;
    }

    public float[]   getCheckpoint(int i)    { return checkpoints[i % checkpoints.length]; }
    public int       getNumCheckpoints()     { return checkpoints.length; }
    public float[][] getCentreLine()         { return centreLine; }

    /** Bounding box [minX, minY, maxX, maxY] of the centreline. */
    public float[] getBounds() {
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (float[] p : centreLine) {
            if (p[0] < minX) minX = p[0]; if (p[0] > maxX) maxX = p[0];
            if (p[1] < minY) minY = p[1]; if (p[1] > maxY) maxY = p[1];
        }
        return new float[]{minX, minY, maxX, maxY};
    }

    /**
     * Start position for grid slot {@code slot}.
     * All slots land at or BEFORE FINISH_IDX so every car starts behind CP[0].
     */
    public float[] getGridSlot(int slot) {
        int n   = centreLine.length;
        // 8 spline steps ≈ 35px gap between each car → no immediate pile-up
        int idx = Math.max(0, FINISH_IDX - slot * 8);
        float[] p = centreLine[idx];
        float[] q = centreLine[Math.min(idx + 4, n - 1)];
        float angle = (float) Math.atan2(q[1] - p[1], q[0] - p[0]);
        return new float[]{p[0], p[1], angle};
    }
}
