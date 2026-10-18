package dev.ehtid.thaumcraft.port.core;

/**
 * Bounds-safe page/window projection for the read-only Thaumonomicon index.
 * Keeps page navigation and click targets valid if categories, knowledge or
 * window dimensions change while the screen remains open.
 */
public final class ResearchBrowserPagination {
    private ResearchBrowserPagination() {}

    public record Page(int index, int pageCount, int fromIndex, int toIndex) {
        public boolean hasPrevious() { return index > 0; }
        public boolean hasNext() { return index + 1 < pageCount; }
        public int visibleCount() { return toIndex - fromIndex; }

        /** -1 for a click outside the currently visible list. */
        public int entryIndexAtRow(int row) {
            return row < 0 || row >= visibleCount() ? -1 : fromIndex + row;
        }
    }

    public static Page of(int entryCount, int requestedPage, int pageSize) {
        if (entryCount < 0) throw new IllegalArgumentException("negative research count");
        if (pageSize <= 0) throw new IllegalArgumentException("page size must be positive");
        int pages = entryCount == 0 ? 1 : 1 + (entryCount - 1) / pageSize;
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        int from = page * pageSize; // <= entryCount, so cannot overflow int
        int to = from + Math.min(pageSize, entryCount - from);
        return new Page(page, pages, from, to);
    }
}
