package dev.ehtid.thaumcraft.port.core;

/**
 * Loader-independent BETA26 TileJarRenderer aspect-label transforms.
 * Values are copied from TileJarRenderer.renderTileEntityAt and UtilsFX.drawTag.
 */
public final class LegacyEssentiaJarLabelRenderLogic {
    public static final float LABEL_CENTER_Y = -0.4F;
    public static final float LABEL_CENTER_Z = 0.315F;
    public static final float ICON_CENTER_Z = 0.316F;
    public static final float LABEL_HALF_SIZE = 0.25F;
    public static final float ICON_SCALE = 0.021F;
    public static final float ICON_HALF_SIZE = 8.0F;
    public static final int ICON_TINT_RGB = 0x1A1A1A;
    public static final int ICON_ALPHA = 204;

    private LegacyEssentiaJarLabelRenderLogic() {}

    /** BETA26 checks only whether aspectFilter is non-null before drawing the label. */
    public static boolean shouldRender(String aspectFilter) {
        return aspectFilter != null;
    }

    /** BETA26 facing values: 2 north, 3 south, 4 west, 5 east. */
    public static int facingRotationDegrees(int facing) {
        return switch (facing) {
            case 3 -> 180;
            case 5 -> 90;
            case 4 -> 270;
            default -> 0;
        };
    }

    /**
     * Exact BETA26 crooked-label rotation:
     * (aspectFilter.getTag().hashCode() + x + facing) % 4 - 2.
     * Keep Java's signed remainder; floorMod changes negative-tag behavior.
     */
    public static int crookedRotationDegrees(String aspectTag, int blockX, int facing) {
        if (aspectTag == null) throw new IllegalArgumentException("aspectTag");
        return (aspectTag.hashCode() + blockX + facing) % 4 - 2;
    }
}
