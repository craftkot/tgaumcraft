package dev.ehtid.thaumcraft.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

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
        String full = message.getString();
        String display = full;
        if (font.width(display) > availableWidth) {
            String suffix = "...";
            display = font.plainSubstrByWidth(display,
                    Math.max(0, availableWidth - font.width(suffix))) + suffix;
        }
        graphics.drawString(font, display,
                x + (availableWidth - font.width(display)) / 2, y, color, true);
    }

    /**
     * Draw up to maxLines without shrinking fonts. Any overflowing content
     * is truncated with a visible ellipsis (never spills into inventory slots).
     */
    public static void wrapped(GuiGraphics graphics, Font font,
                               Component message, int x, int y,
                               int maxWidth, int maxLines, int color) {
        List<FormattedCharSequence> lines = font.split(message, maxWidth);
        int count = Math.min(lines.size(), maxLines);
        for (int i = 0; i < count; i++) {
            graphics.drawString(font, lines.get(i), x, y + i * 10, color, true);
        }
        if (lines.size() > maxLines && maxLines > 0) {
            graphics.drawString(font, "...", x + maxWidth - font.width("..."),
                    y + (maxLines - 1) * 10, color, true);
        }
    }
}
