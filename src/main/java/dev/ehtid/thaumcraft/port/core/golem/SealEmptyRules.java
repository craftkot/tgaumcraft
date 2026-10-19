package dev.ehtid.thaumcraft.port.core.golem;

/** BETA26 SealEmpty default pmeta/pnbt=true, other toggles=false. */
public final class SealEmptyRules {
    private SealEmptyRules() {}
    public static final int INTERVAL_TICKS = 20;
    public static final short TASK_LIFESPAN_SECONDS = 5;

    public static int requested(int cachedCount, int carryCapacity) {
        return Math.max(0, Math.min(cachedCount, carryCapacity));
    }

    /** Source pleave subtracts one from fresh matching stock, never from a stale task budget. */
    public static int requested(int cachedCount, int carryCapacity, long stock, boolean leaveOne) {
        int reserve = leaveOne ? 1 : 0;
        return stock <= reserve ? 0 : (int) Math.min(requested(cachedCount, carryCapacity), stock - reserve);
    }
}
