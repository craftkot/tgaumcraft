package dev.ehtid.thaumcraft.port.core;

/**
 * Geometry and visibility contract copied from Thaumcraft 6.1.BETA26
 * ModelJar and TileJarRenderer. Model coordinates are 1/16 block units;
 * the renderer flips X by 180 degrees exactly like the original GL path.
 */
public final class LegacyEssentiaJarMetalPartsLogic {
    private LegacyEssentiaJarMetalPartsLogic() {}

    public static final int TEXTURE_WIDTH = 64;
    public static final int TEXTURE_HEIGHT = 32;
    public static final boolean MIRRORED_UV = true;

    public static final int LID_TEXTURE_U = 32;
    public static final int LID_TEXTURE_V = 24;
    public static final float LID_X = -3.0F;
    public static final float LID_Y = 0.0F;
    public static final float LID_Z = -3.0F;
    public static final float LID_WIDTH = 6.0F;
    public static final float LID_HEIGHT = 2.0F;
    public static final float LID_DEPTH = 6.0F;
    public static final float LID_PIVOT_Y = -14.0F;
    public static final float BRACE_SCALE = 1.001F;

    public static final int EXTENSION_TEXTURE_U = 0;
    public static final int EXTENSION_TEXTURE_V = 23;
    public static final float EXTENSION_X = -2.0F;
    public static final float EXTENSION_Y = -16.0F;
    public static final float EXTENSION_Z = -2.0F;
    public static final float EXTENSION_WIDTH = 4.0F;
    public static final float EXTENSION_HEIGHT = 2.0F;
    public static final float EXTENSION_DEPTH = 4.0F;
    public static final float EXTENSION_PIVOT_Y = 0.0F;
    public static final float EXTENSION_SCALE_XZ = 0.9F;

    /** BETA26 TileJarRenderer renders the lid only while TileJarFillable.blocked. */
    public static boolean shouldRenderBrace(boolean blocked) {
        return blocked;
    }

    /**
     * BETA26 getConnectableTile(pos, UP) checks only the adjacent transport's
     * DOWN face. It does not require that the neighbor can currently output.
     */
    public static boolean shouldRenderLidExtension(boolean connectableAbove) {
        return connectableAbove;
    }

    /** World-space Y bounds after the original 180-degree X rotation. */
    public static float braceMinY() {
        return -(LID_PIVOT_Y + LID_Y + LID_HEIGHT) / 16.0F;
    }

    public static float braceMaxY() {
        return -(LID_PIVOT_Y + LID_Y) / 16.0F;
    }

    public static float extensionMinY() {
        return -(EXTENSION_PIVOT_Y + EXTENSION_Y + EXTENSION_HEIGHT) / 16.0F;
    }

    public static float extensionMaxY() {
        return -(EXTENSION_PIVOT_Y + EXTENSION_Y) / 16.0F;
    }
}
