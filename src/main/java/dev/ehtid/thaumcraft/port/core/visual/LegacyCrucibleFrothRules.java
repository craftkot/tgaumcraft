package dev.ehtid.thaumcraft.port.core.visual;

/**
 * BETA26 TileCrucible.drawEffects and FXDispatcher.crucibleFroth/FrothDown.
 * The hot-water branch emits one surface froth every client tick and, above
 * 500 stored Vis, eight edge particles in two four-side rounds.
 */
public final class LegacyCrucibleFrothRules {
    public static final int SURFACE_START_FRAME = 64;
    public static final int OVERFLOW_START_FRAME = 73;
    public static final int FINAL_FRAME_FIRST = 65;
    public static final int FINAL_FRAME_LAST = 66;
    public static final int OVERFLOW_PARTICLE_COUNT = 8;
    public static final double RANDOM_MOVEMENT_SCALE = 0.001D;
    public static final double DEFAULT_SLOW_DOWN = LegacyCrucibleBubbleRules.DEFAULT_SLOW_DOWN;
    public static final float SURFACE_GRAVITY = 0.1F;
    public static final float OVERFLOW_GRAVITY = 0.05F;
    public static final float SURFACE_ALPHA = 1.0F;
    public static final float OVERFLOW_ALPHA = 0.8F;
    public static final float SURFACE_RED = 0.5F;
    public static final float SURFACE_GREEN = 0.5F;
    public static final float SURFACE_BLUE = 0.7F;
    public static final float OVERFLOW_RED = 0.25F;
    public static final float OVERFLOW_GREEN = 0.0F;
    public static final float OVERFLOW_BLUE = 0.75F;

    private LegacyCrucibleFrothRules() {}

    /** BETA26 draws one surface froth every client tick while the tank has water and heat > 150. */
    public static boolean shouldSpawnSurface(int heat, int waterMb) {
        return waterMb > 0 && heat > 150;
    }

    /** BETA26 draws eight edge froths per tick only inside the hot-water branch when visSize > 500. */
    public static int overflowParticleCount(int heat, int waterMb, int visSize) {
        return shouldSpawnSurface(heat, waterMb) && visSize > 500 ? OVERFLOW_PARTICLE_COUNT : 0;
    }

    /** Surface: 4 + nextInt(3); overflow: 12 + nextInt(12). */
    public static int lifetime(boolean overflow, int randomRoll) {
        int bound = overflow ? 12 : 3;
        if (randomRoll < 0 || randomRoll >= bound)
            throw new IllegalArgumentException("randomRoll must be in [0, " + bound + ")");
        return (overflow ? 12 : 4) + randomRoll;
    }

    /** Surface: nextFloat * .2 + .2; overflow: nextFloat * .2 + .4. */
    public static float scale(boolean overflow, float random) {
        if (!Float.isFinite(random) || random < 0.0F || random >= 1.0F)
            throw new IllegalArgumentException("random must be finite and in [0, 1)");
        return random * 0.2F + (overflow ? 0.4F : 0.2F);
    }

    /** FXGeneric.setParticle(64/73); setFinalFrames(65, 66). */
    public static int frame(boolean overflow, int age, int lifetime) {
        if (age < 0 || lifetime <= 0)
            throw new IllegalArgumentException("age must be non-negative and lifetime positive");
        if (age > lifetime - 2) {
            int index = Math.max(0, lifetime - age);
            return index == 0 ? FINAL_FRAME_FIRST : FINAL_FRAME_LAST;
        }
        return overflow ? OVERFLOW_START_FRAME : SURFACE_START_FRAME;
    }
}
