package dev.ehtid.thaumcraft.port.core.visual;

/** BETA26 Wisp billboard atlas contract. */
public final class LegacyWispRenderAtlas {
    public static final int ANIMATION_FRAME_COUNT = 16;
    public static final int PARTICLE_GRID = 64;
    public static final int NODE_GRID = 32;
    public static final int OUTER_BASE_FRAME = 512;
    public static final int INNER_BASE_FRAME = 320;
    public static final int ASPECT_BASE_FRAME = 800;

    private LegacyWispRenderAtlas() {}

    public record Uv(float u0, float v0, float u1, float v1) {}

    public static int animatedFrame(int baseFrame, int tickCount) {
        if (baseFrame < 0) throw new IllegalArgumentException("negative BETA26 Wisp base frame");
        return baseFrame + Math.floorMod(tickCount, ANIMATION_FRAME_COUNT);
    }

    public static Uv frameUv(int frame, int gridSize) {
        if (gridSize <= 0 || frame < 0 || (long) frame >= (long) gridSize * gridSize) {
            throw new IllegalArgumentException("BETA26 Wisp frame must fit a positive square atlas grid");
        }
        float cell = 1.0F / gridSize;
        int column = frame % gridSize;
        int row = frame / gridSize;
        return new Uv(column * cell, row * cell, (column + 1) * cell, (row + 1) * cell);
    }
}
