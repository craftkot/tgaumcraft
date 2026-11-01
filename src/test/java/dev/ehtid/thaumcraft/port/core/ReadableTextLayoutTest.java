package dev.ehtid.thaumcraft.port.core;

/** Independent Java 21 regression for small RU/EN labels and supplementary glyphs. */
public final class ReadableTextLayoutTest {
    private static int assertions;

    public static void main(String[] args) {
        equal("", ReadableTextLayout.ellipsize("Алхимия", 0, ReadableTextLayoutTest::pixels));
        equal("", ReadableTextLayout.ellipsize("Алхимия", -5, ReadableTextLayoutTest::pixels));
        equal(".", ReadableTextLayout.ellipsize("ABCDE", 2, ReadableTextLayoutTest::pixels));
        equal("..", ReadableTextLayout.ellipsize("ABCDE", 4, ReadableTextLayoutTest::pixels));
        equal("...", ReadableTextLayout.ellipsize("ABCDE", 6, ReadableTextLayoutTest::pixels));
        equal("A...", ReadableTextLayout.ellipsize("ABCDE", 12, ReadableTextLayoutTest::pixels));
        equal("Ал...", ReadableTextLayout.ellipsize("Алхимия", 20, ReadableTextLayoutTest::pixels));
        equal("👾...", ReadableTextLayout.ellipsize("👾XYZ", 16, ReadableTextLayoutTest::pixels));
        // UTF-16/code-point safety is insufficient: a visible grapheme may contain
        // multiple code points (combining accents, ZWJ emoji or flag pairs).
        equal("...", ReadableTextLayout.ellipsize("е\u0301XYZ", 16, ReadableTextLayoutTest::pixels));
        equal("е\u0301...", ReadableTextLayout.ellipsize("е\u0301XYZ", 20, ReadableTextLayoutTest::pixels));
        equal("...", ReadableTextLayout.ellipsize("👩‍🔬AB", 26, ReadableTextLayoutTest::pixels));
        equal("👩‍🔬...", ReadableTextLayout.ellipsize("👩‍🔬AB", 32, ReadableTextLayoutTest::pixels));
        equal("...", ReadableTextLayout.ellipsize("🇷🇺AB", 16, ReadableTextLayoutTest::pixels));
        equal("🇷🇺...", ReadableTextLayout.ellipsize("🇷🇺AB", 26, ReadableTextLayoutTest::pixels));
        // Deliberately non-monotonic test metric: longest fitting prefix wins.
        equal("abcd...", ReadableTextLayout.ellipsize("abcd", 10, candidate ->
                candidate.equals("abcd") ? 14 : candidate.equals("abcd...") ? 9
                        : candidate.equals("...") ? 6 : 13));
        equal("Сканирование", ReadableTextLayout.ellipsize("Сканирование", 100, ReadableTextLayoutTest::pixels));
        equal("", ReadableTextLayout.ellipsize("", 10, ReadableTextLayoutTest::pixels));

        // The final visible wrapped line must fit WITH its ellipsis, not draw dots
        // on top of an already-full line or to the left of a narrow rectangle.
        equal(2, ReadableTextLayout.longestPrefixWithSuffix(4, 10, n -> 6 + n * 2));
        equal(0, ReadableTextLayout.longestPrefixWithSuffix(4, 6, n -> 6 + n * 2));
        equal(-1, ReadableTextLayout.longestPrefixWithSuffix(4, 5, n -> 6 + n * 2));
        equal(-1, ReadableTextLayout.longestPrefixWithSuffix(4, -1, n -> 0));
        equal(0, ReadableTextLayout.longestPrefixWithSuffix(0, 6, n -> 6));
        // A styled font can have non-monotonic advances (kerning): do not binary search.
        int[] styledWidths = {6, 12, 9, 13};
        equal(2, ReadableTextLayout.longestPrefixWithSuffix(3, 10, n -> styledWidths[n]));
        boolean rejected = false;
        try { ReadableTextLayout.longestPrefixWithSuffix(-1, 10, n -> 0); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "negative glyph count accepted");

        for (String text : new String[]{"Алхимия и исследования", "FIRSTSTEPS", "👾Кристаллы🔮", "a", ""}) {
            for (int limit = -1; limit <= 150; limit++) {
                String result = ReadableTextLayout.ellipsize(text, limit, ReadableTextLayoutTest::pixels);
                check(pixels(result) <= Math.max(0, limit), "overflow " + limit + ": " + result);
                check(!containsBrokenSurrogates(result), "split Unicode surrogate: " + result);
                if (limit > 0 && pixels(text) <= limit)
                    equal(text, result);
            }
        }
        System.out.println("Readable text pixel bounds: OK (" + assertions + " assertions)");
    }

    private static int pixels(String text) {
        return text.codePoints().map(cp -> cp == '.' ? 2 : cp == ' ' ? 3
                : cp > 0xFFFF ? 10 : cp >= 0x0400 ? 7 : 6).sum();
    }

    private static boolean containsBrokenSurrogates(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (i + 1 == text.length() || !Character.isLowSurrogate(text.charAt(i + 1))) return true;
                i++;
            } else if (Character.isLowSurrogate(c)) return true;
        }
        return false;
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static void equal(String expected, String actual) {
        check(expected.equals(actual), "expected '" + expected + "', got '" + actual + "'");
    }

    private static void equal(int expected, int actual) {
        check(expected == actual, "expected " + expected + ", got " + actual);
    }
}
