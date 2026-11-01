package dev.ehtid.thaumcraft.client;

import dev.ehtid.thaumcraft.port.core.ReadableTextLayout;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Readability improvements for Thaumcraft's intentionally dark 1.12 GUIs.
 *
 * Never replace Minecraft's active language font (including Cyrillic glyphs)
 * with the original tiny decorative bitmap. Keep full-size vanilla glyphs
 * and readable contrast; preserve the old GUI textures and game mechanics.
 */
public final class ThaumcraftReadableText {
    public static final int TEXT = 0xFFF3E7C9;
    public static final int MUTED_TEXT = 0xFFD1C5A9;
    public static final int GREEN_TEXT = 0xFFDFF6D9;
    public static final int BAD_TEXT = 0xFFFFB4A8;
    private static final int BACKGROUND = 0xD91A201D;
    private static final int BORDER = 0xB187795E;

    private ThaumcraftReadableText() {}

    public static void panel(GuiGraphics graphics, int x, int y, int width, int height) {
        // Zero-sized or collapsed panels must not submit inverted rectangles:
        // GuiGraphics.fill is not a clipping API and can paint outside the GUI.
        if (width <= 0 || height <= 0) return;
        graphics.fill(x, y, x + width, y + height, BORDER);
        // A 1-2px panel has no interior; preserve its border only.
        if (width > 2 && height > 2)
            graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, BACKGROUND);
    }

    /** Uses the current Minecraft font and its normal shadow, never 0.5 scale. */
    public static void line(GuiGraphics graphics, Font font,
                            Component message, int x, int y, int color) {
        graphics.drawString(font, message, x, y, color, true);
    }

    /**
     * Keep text at vanilla 1x pixel scale and clip only if the translation
     * doesn't fit. A hover tooltip can show the original full translation.
     * Splitting on glyph widths (not Java string length) supports Cyrillic.
     */
    public static void centeredLabel(GuiGraphics graphics, Font font,
                                     Component message, int x, int y,
                                     int availableWidth, int color) {
        // Minecraft's shadow pass draws one pixel to the right of the glyphs.
        // Reserve that pixel as part of the label's real visual footprint.
        if (availableWidth <= 1) return;
        int textWidth = availableWidth - 1;
        // Component.getString() discards bold, color, and bidirectional ordering.
        // Truncate the actual visual glyph sequence instead, measuring it with
        // the same font and styles used by GuiGraphics.drawString.
        FormattedCharSequence display = fitStyledLine(
                font, message.getVisualOrderText(), textWidth);
        int renderedWidth = font.width(display);
        if (renderedWidth <= 0) return;
        graphics.drawString(font, display,
                x + (availableWidth - renderedWidth) / 2, y, color, true);
    }

    /**
     * Draw up to maxLines without shrinking fonts. When the final line overflows,
     * reserve its own pixel budget for an ellipsis, preserving per-glyph styles.
     * The previous implementation drew dots over a full line and could spill left
     * of the panel for widths smaller than the marker itself.
     */
    public static void wrapped(GuiGraphics graphics, Font font,
                               Component message, int x, int y,
                               int maxWidth, int maxLines, int color) {
        // The split width must also include the 1px shadow drawn by GuiGraphics.
        if (maxWidth <= 1 || maxLines <= 0) return;
        int textWidth = maxWidth - 1;
        List<FormattedCharSequence> lines = font.split(message, textWidth);
        int count = Math.min(lines.size(), maxLines);
        for (int i = 0; i < count; i++) {
            FormattedCharSequence visible = lines.get(i);
            if (i == count - 1 && lines.size() > maxLines) {
                visible = lastLineWithEllipsis(font, visible, textWidth);
            } else {
                // Font.split normally respects the width, but an indivisible
                // glyph can be wider than the entire box (emoji, CJK, or a
                // very narrow UI column). Clamp EVERY visible line, not only
                // the last line when additional lines are hidden.
                visible = fitStyledLine(font, visible, textWidth);
            }
            graphics.drawString(font, visible, x, y + i * 10, color, true);
        }
    }

    /** Retain Component styles and visual glyph order when centering narrow labels. */
    private static FormattedCharSequence fitStyledLine(
            Font font, FormattedCharSequence line, int maxWidth) {
        if (maxWidth <= 0) return sink -> true;
        return font.width(line) <= maxWidth
                ? line : lastLineWithEllipsis(font, line, maxWidth);
    }

    private record StyledGlyph(int codePoint, Style style) {}

    private static FormattedCharSequence lastLineWithEllipsis(
            Font font, FormattedCharSequence line, int maxWidth) {
        List<StyledGlyph> glyphs = new ArrayList<>();
        line.accept((index, style, codePoint) -> {
            glyphs.add(new StyledGlyph(codePoint, style));
            return true;
        });
        String suffix = "...";
        while (!suffix.isEmpty() && font.width(suffix) > maxWidth)
            suffix = suffix.substring(1);
        if (suffix.isEmpty()) return sink -> true;

        // Measure only complete visual grapheme clusters, not arbitrary glyph
        // boundaries. Cutting after the base of an accented letter or inside a
        // ZWJ emoji changes what the player sees in localized GUI text.
        List<Integer> prefixes = graphemePrefixCounts(glyphs);
        String marker = suffix;
        int clusterPrefix = ReadableTextLayout.longestPrefixWithSuffix(
                prefixes.size() - 1, maxWidth,
                keep -> font.width(withSuffix(glyphs, prefixes.get(keep), marker)));
        return clusterPrefix < 0 ? sink -> true
                : withSuffix(glyphs, prefixes.get(clusterPrefix), marker);
    }

    private static List<Integer> graphemePrefixCounts(List<StyledGlyph> glyphs) {
        StringBuilder visual = new StringBuilder();
        for (StyledGlyph glyph : glyphs) visual.appendCodePoint(glyph.codePoint());
        BreakIterator clusters = BreakIterator.getCharacterInstance(Locale.ROOT);
        clusters.setText(visual.toString());
        List<Integer> prefixes = new ArrayList<>();
        prefixes.add(0);
        int glyphIndex = 0;
        int utf16End = 0;
        for (int boundary = clusters.next(); boundary != BreakIterator.DONE;
             boundary = clusters.next()) {
            while (glyphIndex < glyphs.size() && utf16End < boundary) {
                utf16End += Character.charCount(glyphs.get(glyphIndex++).codePoint());
            }
            if (utf16End == boundary) prefixes.add(glyphIndex);
        }
        return prefixes;
    }

    /** Build a styled visual prefix without cutting grapheme clusters or losing text colors. */
    private static FormattedCharSequence withSuffix(
            List<StyledGlyph> glyphs, int keep, String suffix) {
        return sink -> {
            int index = 0;
            for (int i = 0; i < keep; i++) {
                StyledGlyph glyph = glyphs.get(i);
                if (!sink.accept(index, glyph.style(), glyph.codePoint())) return false;
                index += Character.charCount(glyph.codePoint());
            }
            for (int i = 0; i < suffix.length(); i++) {
                if (!sink.accept(index++, Style.EMPTY, suffix.charAt(i))) return false;
            }
            return true;
        };
    }
}
