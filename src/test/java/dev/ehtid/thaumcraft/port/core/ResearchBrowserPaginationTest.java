package dev.ehtid.thaumcraft.port.core;

/** Standalone Java 21 tests; requires neither Gradle nor Minecraft runtime. */
public final class ResearchBrowserPaginationTest {
    private static int checks;
    private static void verify(boolean assertion, String reason) {
        checks++;
        if (!assertion) throw new AssertionError(reason);
    }

    public static void main(String[] args) {
        var empty = ResearchBrowserPagination.of(0, 500, 10);
        verify(empty.index() == 0 && empty.pageCount() == 1, "empty page clamped");
        verify(empty.visibleCount() == 0, "no fake results");
        verify(!empty.hasNext() && !empty.hasPrevious(), "empty navigation disabled");
        verify(empty.entryIndexAtRow(0) == -1, "empty click rejected");
        var first = ResearchBrowserPagination.of(21, -4, 10);
        verify(first.index() == 0 && first.pageCount() == 3, "initial page and total");
        verify(first.fromIndex() == 0 && first.toIndex() == 10, "first window");
        verify(first.entryIndexAtRow(9) == 9, "last first-page row");
        verify(first.entryIndexAtRow(10) == -1, "no invisible row click");
        verify(first.hasNext() && !first.hasPrevious(), "first page arrows");
        var last = ResearchBrowserPagination.of(21, 200, 10);
        verify(last.index() == 2 && last.pageCount() == 3, "last page clamped");
        verify(last.fromIndex() == 20 && last.toIndex() == 21, "last partial page");
        verify(last.entryIndexAtRow(0) == 20 && last.entryIndexAtRow(1) == -1,
                "never select off-page entry");
        verify(!last.hasNext() && last.hasPrevious(), "last page arrows");
        var shrunk = ResearchBrowserPagination.of(3, last.index(), 10);
        verify(shrunk.index() == 0 && shrunk.visibleCount() == 3,
                "relock/category shrink corrects old page");
        var full = ResearchBrowserPagination.of(Integer.MAX_VALUE, Integer.MAX_VALUE,
                Integer.MAX_VALUE);
        verify(full.pageCount() == 1 && full.toIndex() == Integer.MAX_VALUE,
                "avoid integer overflow on large counts");
        var edge = ResearchBrowserPagination.of(Integer.MAX_VALUE, Integer.MAX_VALUE, 10);
        verify(edge.fromIndex() == 2147483640 && edge.toIndex() == Integer.MAX_VALUE,
                "last page offset is within int bounds");
        boolean badSize = false, badCount = false;
        try { ResearchBrowserPagination.of(1, 0, 0); }
        catch (IllegalArgumentException expected) { badSize = true; }
        try { ResearchBrowserPagination.of(-1, 0, 10); }
        catch (IllegalArgumentException expected) { badCount = true; }
        verify(badSize && badCount, "reject invalid input");
        System.out.println("M07 research browser pagination: OK (" + checks + " assertions)");
    }
}
