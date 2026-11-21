package dev.ehtid.thaumcraft.port.core.visual;

/**
 * BETA26 FXDispatcher.crucibleBubble and TileCrucible.drawEffects rules.
 * The bubble is sampled once per six client ticks while water and at least
 * one aspect are present; its texture frames are cells 64, 65 and 66.
 */
public final class LegacyCrucibleBubbleRules {
    private LegacyCrucibleBubbleRules() {}

    public static boolean shouldSpawn(int roll, int aspectCount) {
        if (roll < 0 || roll >= 6)
            throw new IllegalArgumentException("roll must be in [0, 6)");
        if (aspectCount < 0)
            throw new IllegalArgumentException("aspectCount must be non-negative");
        return roll == 0 && aspectCount > 0;
    }

    /** FXDispatcher.crucibleBubble uses 15 + world.rand.nextInt(10). */
    public static int lifetime(int randomRoll) {
        if (randomRoll < 0 || randomRoll >= 10)
            throw new IllegalArgumentException("randomRoll must be in [0, 10)");
        return 15 + randomRoll;
    }

    /** FXGeneric.setScale(Math.random() * 0.3 + 0.3). */
    public static float scale(float random) {
        if (!Float.isFinite(random) || random < 0.0F || random >= 1.0F)
            throw new IllegalArgumentException("random must be finite and in [0, 1)");
        return random * 0.3F + 0.3F;
    }

    /** FXGeneric.setParticle(64); setFinalFrames(65, 66, 66). */
    public static int frame(int age, int lifetime) {
        if (age < 0 || lifetime <= 0)
            throw new IllegalArgumentException("age must be non-negative and lifetime positive");
        if (age > lifetime - 3) {
            int index = Math.max(0, lifetime - age);
            return index == 0 ? 65 : 66;
        }
        return 64;
    }
}
