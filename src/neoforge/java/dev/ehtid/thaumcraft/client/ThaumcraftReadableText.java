package dev.ehtid.thaumcraft.client;

import dev.ehtid.thaumcraft.port.core.ReadableTextLayout;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

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
        graphics.fill(x, y, x + width, y + height, BORDER);
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
        if (availableWidth <= 0) return;
        // Component.getString() discards bold, color, and bidirectional ordering.
        // Truncate the actual visual glyph sequence instead, measuring it with
        // the same font and styles used by GuiGraphics.drawString.
        FormattedCharSequence display = fitStyledLine(
                font, message.getVisualOrderText(), availableWidth);
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
        if (maxWidth <= 0 || maxLines <= 0) return;
        List<FormattedCharSequence> lines = font.split(message, maxWidth);
        int count = Math.min(lines.size(), maxLines);
        for (int i = 0; i < count; i++) {
            FormattedCharSequence visible = lines.get(i);
            if (i == count - 1 && lines.size() > maxLines)
                visible = lastLineWithEllipsis(font, visible, maxWidth);
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

        String marker = suffix;
        int prefix = ReadableTextLayout.longestPrefixWithSuffix(glyphs.size(), maxWidth,
                keep -> font.width(withSuffix(glyphs, keep, marker)));
        return prefix < 0 ? sink -> true : withSuffix(glyphs, prefix, marker);
    }

    /** Build a styled visual prefix without cutting surrogate pairs or losing text colors. */
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
