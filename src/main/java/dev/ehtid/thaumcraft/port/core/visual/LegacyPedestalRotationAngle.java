package dev.ehtid.thaumcraft.port.core.visual;

/**
 * BETA26 TilePedestalRenderer rotates the displayed stack from the render-view
 * entity's tick count, not the world's game time.
 */
public final class LegacyPedestalRotationAngle {
    private LegacyPedestalRotationAngle() {}

    public static float degrees(int renderViewEntityTicks, float partialTick) {
        return (renderViewEntityTicks + partialTick) % 360.0F;
    }
}
