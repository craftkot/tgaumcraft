package dev.ehtid.thaumcraft.util;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Opt-in diagnostics for real Minecraft smoke runs; inactive in ordinary player runs. */
public final class ThaumcraftSmokeAudit {
    private static final boolean ENABLED = "1".equals(System.getenv("THAUMCRAFT_SMOKE"));
    private static final ConcurrentHashMap<String, AtomicInteger> COUNTS = new ConcurrentHashMap<>();

    private ThaumcraftSmokeAudit() {}

    public static void mark(String marker) {
        if (!ENABLED) return;
        COUNTS.computeIfAbsent(marker, ignored -> new AtomicInteger()).incrementAndGet();
        System.out.println("TC_SMOKE_" + marker + "=YES");
    }

    /** Count authoritative markers inside the current opt-in JVM smoke process. */
    public static int count(String marker) {
        AtomicInteger count = COUNTS.get(marker);
        return count == null ? 0 : count.get();
    }
}
