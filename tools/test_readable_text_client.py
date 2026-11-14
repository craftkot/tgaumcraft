#!/usr/bin/env python3
"""Compile and exercise the real ThaumcraftReadableText with tiny Minecraft API stubs."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parent.parent
sources = {
    'net/minecraft/network/chat/Style.java': '''package net.minecraft.network.chat;
public final class Style {
    public static final Style EMPTY = new Style(false);
    public final boolean bold;
    public Style(boolean bold) { this.bold = bold; }
}''',
    'net/minecraft/util/FormattedCharSequence.java': '''package net.minecraft.util;
import net.minecraft.network.chat.Style;
@FunctionalInterface public interface FormattedCharSequence {
    boolean accept(Sink sink);
    @FunctionalInterface interface Sink { boolean accept(int index, Style style, int codePoint); }
}''',
    'net/minecraft/network/chat/Component.java': '''package net.minecraft.network.chat;
import net.minecraft.util.FormattedCharSequence;
public final class Component {
    private final String text;
    private final Style style;
    private final FormattedCharSequence visual;
    public Component(String text, Style style) { this(text, style, null); }
    public Component(String text, Style style, FormattedCharSequence visual) {
        this.text = text; this.style = style; this.visual = visual;
    }
    public String getString() { return text; }
    public FormattedCharSequence getVisualOrderText() {
        if (visual != null) return visual;
        return sink -> { int index = 0;
            for (int cp : text.codePoints().toArray()) {
                if (!sink.accept(index, style, cp)) return false;
                index += Character.charCount(cp);
            }
            return true;
        };
    }
}''',
    'net/minecraft/client/gui/Font.java': '''package net.minecraft.client.gui;
import java.util.List;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
public final class Font {
    private int advance(int cp, Style style) {
        return cp == '.' ? 2 : (style.bold ? 10 : 6);
    }
    public int width(String s) { return s.codePoints().map(cp -> advance(cp, Style.EMPTY)).sum(); }
    public int width(FormattedCharSequence s) {
        int[] width = {0};
        s.accept((index, style, cp) -> { width[0] += advance(cp, style); return true; });
        return width[0];
    }
    public List<FormattedCharSequence> split(Component message, int maxWidth) {
        // Explicit newline gives two visual lines for the wrapped/ellipsis test.
        return java.util.Arrays.stream(message.getString()
                .split(Character.toString((char)10), -1))
                .map(s -> new Component(s, Style.EMPTY).getVisualOrderText()).toList();
    }
}''',
    'net/minecraft/client/gui/GuiGraphics.java': '''package net.minecraft.client.gui;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.network.chat.Component;
public final class GuiGraphics {
    public String text = "";
    public List<Boolean> bold = new ArrayList<>();
    public int draws, x, drawnWidth;
    public int fills;
    public List<int[]> rectangles = new ArrayList<>();
    public boolean shadow;
    public void fill(int x1, int y1, int x2, int y2, int color) {
        fills++;
        rectangles.add(new int[] {x1, y1, x2, y2});
    }
    public void drawString(Font font, FormattedCharSequence seq, int x, int y, int color, boolean shadow) {
        StringBuilder out = new StringBuilder();
        bold.clear();
        seq.accept((index, style, cp) -> { out.appendCodePoint(cp); bold.add(style.bold); return true; });
        this.text = out.toString(); this.x = x; this.drawnWidth = font.width(seq);
        this.shadow = shadow; draws++;
    }
    public void drawString(Font font, Component msg, int x, int y, int color, boolean shadow) {
        drawString(font, msg.getVisualOrderText(), x, y, color, shadow);
    }
}''',
    'dev/ehtid/thaumcraft/client/ThaumcraftReadableTextClientStubTest.java': '''package dev.ehtid.thaumcraft.client;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
public final class ThaumcraftReadableTextClientStubTest {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++; if (!condition) throw new AssertionError(message);
    }
    private static void inside(GuiGraphics g, int x, int width) {
        check(g.x >= x, "left overflow: " + g.text);
        check(g.x + g.drawnWidth + (g.shadow ? 1 : 0) <= x + width,
                "right shadow overflow: " + g.text);
    }
    public static void main(String[] args) {
        Font font = new Font();
        Style bold = new Style(true);
        GuiGraphics g = new GuiGraphics();
        // GUI panel borders must not create inverted or zero-area fill calls.
        ThaumcraftReadableText.panel(g, 4, 5, 0, 20);
        check(g.fills == 0, "zero-width panel submitted a fill");
        ThaumcraftReadableText.panel(g, 4, 5, 20, -1);
        check(g.fills == 0, "negative-height panel submitted a fill");
        ThaumcraftReadableText.panel(g, 4, 5, 1, 1);
        check(g.fills == 1, "one-pixel panel must paint border only");
        ThaumcraftReadableText.panel(g, 4, 5, 2, 2);
        check(g.fills == 2, "two-pixel panel must paint border only");
        ThaumcraftReadableText.panel(g, 4, 5, 3, 3);
        check(g.fills == 4, "three-pixel panel needs border and valid interior");
        for (int[] rect : g.rectangles) {
            check(rect[2] > rect[0] && rect[3] > rect[1],
                    "panel submitted an inverted/empty fill rectangle");
        }
        int[] interior = g.rectangles.get(3);
        check(interior[0] == 5 && interior[1] == 6
                        && interior[2] == 6 && interior[3] == 7,
                "three-pixel panel interior coordinates changed");
        g = new GuiGraphics();
        Component label = new Component("Жирный текст", bold);
        ThaumcraftReadableText.centeredLabel(g, font, label, 40, 0, 35, 0xFFFFFF);
        check(g.text.equals("Жи..."), "styled truncation lost: " + g.text);
        check(g.bold.get(0) && g.bold.get(1), "bold prefix style lost");
        check(!g.bold.get(2) && !g.bold.get(3), "marker should have neutral style");
        inside(g, 40, 35);
        g = new GuiGraphics();
        ThaumcraftReadableText.centeredLabel(g, font, label, 10, 0, 200, 0xFFFFFF);
        check(g.text.equals("Жирный текст"), "untruncated label changed");
        check(g.bold.stream().allMatch(Boolean::booleanValue), "full label lost bold style");
        inside(g, 10, 200);
        g = new GuiGraphics();
        FormattedCharSequence visual = sink -> sink.accept(0, Style.EMPTY, 'c')
                && sink.accept(1, Style.EMPTY, 'b') && sink.accept(2, Style.EMPTY, 'a');
        ThaumcraftReadableText.centeredLabel(g, font,
                new Component("abc", Style.EMPTY, visual), 0, 0, 40, 0);
        check(g.text.equals("cba"), "visual bidi order ignored");
        g = new GuiGraphics();
        ThaumcraftReadableText.centeredLabel(g, font,
                new Component("🔮Magic", bold), 0, 0, 17, 0);
        check(g.text.equals("🔮..."), "supplementary code point split: " + g.text);
        inside(g, 0, 17);
        // At 16px the 16px glyphs would fit, but their 1px shadow would not.
        g = new GuiGraphics();
        ThaumcraftReadableText.centeredLabel(g, font,
                new Component("🔮Magic", bold), 0, 0, 16, 0);
        check(g.text.equals("..."), "shadow pixel was not reserved: " + g.text);
        inside(g, 0, 16);
        g = new GuiGraphics();
        ThaumcraftReadableText.centeredLabel(g, font, label, 0, 0, 0, 0);
        check(g.draws == 0, "zero width should not draw");
        g = new GuiGraphics();
        ThaumcraftReadableText.centeredLabel(g, font, label, 0, 0, 1, 0);
        check(g.draws == 0, "sub-dot width should not draw");
        g = new GuiGraphics();
        ThaumcraftReadableText.centeredLabel(g, font, label, 0, 0, 2, 0);
        check(g.draws == 0, "two pixels cannot contain a dot plus shadow");
        g = new GuiGraphics();
        ThaumcraftReadableText.centeredLabel(g, font, label, 0, 0, 3, 0);
        check(g.text.equals("."), "one-dot fallback missing");
        inside(g, 0, 3);
        // A code-point-safe prefix can still split a single visible grapheme.
        g = new GuiGraphics();
        ThaumcraftReadableText.centeredLabel(g, font,
                new Component("е\\u0301XYZ", bold), 0, 0, 16, 0);
        check(g.text.equals("..."), "orphaned accent: " + g.text);
        inside(g, 0, 16);
        g = new GuiGraphics();
        ThaumcraftReadableText.centeredLabel(g, font,
                new Component("е\\u0301XYZ", bold), 0, 0, 27, 0);
        check(g.text.equals("е\\u0301..."), "accent cluster not retained: " + g.text);
        inside(g, 0, 27);
        g = new GuiGraphics();
        ThaumcraftReadableText.centeredLabel(g, font,
                new Component("👩‍🔬AB", bold), 0, 0, 26, 0);
        check(g.text.equals("..."), "split joined emoji: " + g.text);
        inside(g, 0, 26);
        g = new GuiGraphics();
        ThaumcraftReadableText.centeredLabel(g, font,
                new Component("👩‍🔬AB", bold), 0, 0, 37, 0);
        check(g.text.equals("👩‍🔬..."), "joined emoji cluster not retained: " + g.text);
        inside(g, 0, 37);
        // Last visible wrapped line must fit together with the 1px shadow.
        // Before the fix "A..." was 12px plus shadow in a 12px panel.
        g = new GuiGraphics();
        ThaumcraftReadableText.wrapped(g, font,
                new Component("AB\\nCD", Style.EMPTY), 0, 0, 12, 1, 0);
        check(g.text.equals("..."), "wrapped shadow budget ignored: " + g.text);
        inside(g, 0, 12);
        g = new GuiGraphics();
        ThaumcraftReadableText.wrapped(g, font,
                new Component("AB\\nCD", Style.EMPTY), 0, 0, 1, 1, 0);
        check(g.draws == 0, "1px panel cannot contain a shadowed glyph");
        // A single indivisible glyph can exceed Font.split's width budget.
        // Previously wrapped() only ellipsized when lines.size() > maxLines;
        // a one-line oversized glyph was drawn past the panel edge.
        g = new GuiGraphics();
        ThaumcraftReadableText.wrapped(g, font,
                new Component("🔮", Style.EMPTY), 3, 0, 5, 1, 0);
        check(g.text.equals(".."), "oversized single glyph not clamped: " + g.text);
        inside(g, 3, 5);
        g = new GuiGraphics();
        ThaumcraftReadableText.wrapped(g, font,
                new Component("🔮", Style.EMPTY), 3, 0, 7, 1, 0);
        check(g.text.equals("🔮"), "fitting single glyph was altered: " + g.text);
        inside(g, 3, 7);
        System.out.println("Client styled GUI layout stubs: OK (" + checks + " assertions)");
    }
}'''
}

with tempfile.TemporaryDirectory(prefix='tc-client-layout-') as tmp:
    tmp = Path(tmp)
    for name, body in sources.items():
        path = tmp / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(body, encoding='utf-8')
    java_files = [str(path) for path in tmp.rglob('*.java')]
    java_files.extend([
        str(root / 'src/main/java/dev/ehtid/thaumcraft/port/core/ReadableTextLayout.java'),
        str(root / 'src/neoforge/java/dev/ehtid/thaumcraft/client/ThaumcraftReadableText.java'),
    ])
    out = tmp / 'classes'
    subprocess.run(['javac', '--release', '21', '-d', str(out), *java_files], check=True)
    subprocess.run(['java', '-cp', str(out),
        'dev.ehtid.thaumcraft.client.ThaumcraftReadableTextClientStubTest'], check=True)
