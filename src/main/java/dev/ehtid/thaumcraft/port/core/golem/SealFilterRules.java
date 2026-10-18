package dev.ehtid.thaumcraft.port.core.golem;

/** BETA26 SealFiltered / InventoryUtils tuple matching and SealFill stock limit. */
public final class SealFilterRules {
    private SealFilterRules() {}

    public static boolean accepts(boolean blacklist, boolean hasFilter, boolean matches) {
        return blacklist ? !hasFilter || !matches : hasFilter && matches;
    }

    /** Zero is unlimited; a positive whitelist size bounds target stock, not each delivery. */
    public static int transferable(int carried, boolean blacklist, int limit, long existing) {
        if (carried <= 0) return 0;
        if (blacklist || limit <= 0) return carried;
        return (int) Math.min(carried, Math.max(0L, (long) limit - Math.max(0L, existing)));
    }
}
