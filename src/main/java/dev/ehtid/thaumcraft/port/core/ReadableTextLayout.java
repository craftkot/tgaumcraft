package dev.ehtid.thaumcraft.port.core;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.IntUnaryOperator;
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

        // A code point is not necessarily a visible character: a Cyrillic accent,
        // emoji modifier, flag or ZWJ sequence must remain attached to its base.
        // Java 21 BreakIterator uses extended grapheme-cluster boundaries.
        BreakIterator clusters = BreakIterator.getCharacterInstance(Locale.ROOT);
        clusters.setText(text);
        List<Integer> ends = new ArrayList<>();
        for (int end = clusters.first(); end != BreakIterator.DONE; end = clusters.next())
            ends.add(end);

        // Do not assume monotonic widths: the active font may use kerning.
        // Test complete grapheme prefixes from longest to shortest.
        for (int i = ends.size() - 1; i >= 0; i--) {
            String candidate = text.substring(0, ends.get(i)) + marker;
            if (width.applyAsInt(candidate) <= maxPixels) return candidate;
        }
        return "";
    }

    /**
     * Find the longest glyph prefix whose styled rendering WITH a suffix fits a pixel budget.
     * The width callback measures the complete prefix and suffix, not the unstyled text.
     * A descending scan deliberately tolerates kerning and non-monotonic glyph advances.
     * Returns -1 when even a suffix with zero retained glyphs cannot fit.
     */
    public static int longestPrefixWithSuffix(int glyphCount, int maxPixels,
                                              IntUnaryOperator renderedWidth) {
        Objects.requireNonNull(renderedWidth, "renderedWidth");
        if (glyphCount < 0) throw new IllegalArgumentException("glyphCount < 0");
        if (maxPixels < 0) return -1;
        for (int count = glyphCount; count >= 0; count--) {
            if (renderedWidth.applyAsInt(count) <= maxPixels) return count;
        }
        return -1;
    }
}
