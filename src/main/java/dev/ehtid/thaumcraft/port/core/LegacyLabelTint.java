package dev.ehtid.thaumcraft.port.core;

/**
 * Color for the ORIGINAL BETA26 filled ItemLabel's overlay.
 * Generated item models use tint index 0 for the unchanged paper base and
 * tint index 1 for the one-aspect overlay. Unknown/malformed labels stay white.
 */
public final class LegacyLabelTint {
    private static final AspectRegistry ASPECTS = new AspectRegistry();
    private LegacyLabelTint() {}

    public static int colorFor(String aspectTag, int tintIndex) {
        if (tintIndex != 1 || aspectTag == null) return 0xFFFFFFFF;
        AspectData aspect = ASPECTS.get(aspectTag);
        return aspect == null ? 0xFFFFFFFF : (0xFF000000 | aspect.color());
    }
}
