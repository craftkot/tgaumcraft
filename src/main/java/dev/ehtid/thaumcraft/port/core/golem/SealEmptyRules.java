package dev.ehtid.thaumcraft.port.core.golem;

/** BETA26 SealEmpty default pmeta/pnbt=true, other toggles=false. */
public final class SealEmptyRules {
    private SealEmptyRules() {}
    public static final int INTERVAL_TICKS = 20;
    public static final short TASK_LIFESPAN_SECONDS = 5;

    public static int requested(int cachedCount, int carryCapacity) {
        return Math.max(0, Math.min(cachedCount, carryCapacity));
    }

    /** Advanced getInv: abs(completed % populated), omitting empty ghosts; cursor advances on completion. */
    public static int cycleIndex(int completed,int populated) {
        if(populated<1 || populated>9) throw new IllegalArgumentException("One to nine populated ghosts required");
        return Math.abs(completed%populated);
    }

    /** Source pleave subtracts one from fresh matching stock, never from a stale task budget. */
    public static int requested(int cachedCount, int carryCapacity, long stock, boolean leaveOne) {
        int reserve = leaveOne ? 1 : 0;
        return stock <= reserve ? 0 : (int) Math.min(requested(cachedCount, carryCapacity), stock - reserve);
    }
}
