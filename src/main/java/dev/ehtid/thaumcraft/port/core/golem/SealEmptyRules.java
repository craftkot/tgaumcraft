package dev.ehtid.thaumcraft.port.core.golem;

/** BETA26 SealEmpty default pmeta/pnbt=true, other toggles=false. */
public final class SealEmptyRules {
    private SealEmptyRules() {}
    public static final int INTERVAL_TICKS = 20;
    public static final short TASK_LIFESPAN_SECONDS = 5;

    public static int requested(int cachedCount, int carryCapacity) {
        return Math.max(0, Math.min(cachedCount, carryCapacity));
    }
}
