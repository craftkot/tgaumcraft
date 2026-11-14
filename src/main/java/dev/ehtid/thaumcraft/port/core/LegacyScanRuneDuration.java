package dev.ehtid.thaumcraft.port.core;

/**
 * BETA26 ItemThaumometer.drawFX passes (int)(target.height * 15) to
 * FXBlockRunes for entities, or 15 for blocks. FXBlockRunes then lives 3*m
 * ticks. The original rune is always purple (green channel = 0).
 *
 * In NeoForge's SimpleParticleType API the unused Y-speed argument carries
 * the duration units to the client provider; it is NOT a color or velocity.
 */
public final class LegacyScanRuneDuration {
    public static final int BLOCK_UNITS = 15;

    private LegacyScanRuneDuration() {}

    public static int entityUnits(float height) {
        if (!Float.isFinite(height) || height <= 0.0F) return 1;
        // Original integer truncation, with a minimum to avoid zero-life particles.
        return Math.max(1, (int) (height * 15.0F));
    }

    public static int lifetimeTicks(double encodedUnits) {
        if (!Double.isFinite(encodedUnits)) return 3 * BLOCK_UNITS;
        // The provider receives this value as a double; reject impossible
        // oversized units before multiplication to avoid int overflow.
        int units = Math.max(1, Math.min(100000, (int) encodedUnits));
        return 3 * units;
    }
}
