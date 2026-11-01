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
        equal("Сканирование", ReadableTextLayout.ellipsize("Сканирование", 100, ReadableTextLayoutTest::pixels));
        equal("", ReadableTextLayout.ellipsize("", 10, ReadableTextLayoutTest::pixels));

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
}
