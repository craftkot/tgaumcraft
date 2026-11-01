package dev.ehtid.thaumcraft.port.core;

import java.util.Objects;
import java.util.function.ToIntFunction;

/** Pixel-bounded, code-point-safe labels for the original Thaumcraft GUIs. */
public final class ReadableTextLayout {
    private ReadableTextLayout() {}

    /**
     * Truncate by rendered glyph width without splitting UTF-16 surrogate pairs. The original
     * three-dot marker is shortened when even the marker cannot fit, so narrow
     * GUI buttons never draw outside their allotted width. The caller supplies
     * the active Minecraft font's String width function.
     */
    public static String ellipsize(String text, int maxPixels, ToIntFunction<String> width) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(width, "width");
        if (maxPixels <= 0 || text.isEmpty()) return "";
        if (width.applyAsInt(text) <= maxPixels) return text;

        String marker = "...";
        while (!marker.isEmpty() && width.applyAsInt(marker) > maxPixels)
            marker = marker.substring(1);
        if (marker.isEmpty()) return "";

        int low = 0;
        int high = text.codePointCount(0, text.length());
        while (low < high) {
            int middle = low + (high - low + 1) / 2;
            int end = text.offsetByCodePoints(0, middle);
            if (width.applyAsInt(text.substring(0, end) + marker) <= maxPixels)
                low = middle;
            else
                high = middle - 1;
        }
        return text.substring(0, text.offsetByCodePoints(0, low)) + marker;
    }
}
