package dev.ehtid.thaumcraft.port.core.golem;

/** Original EntityEvents.livingDrops300..415; BrainyZombie has its own loot path. */
public final class GolemBrainDropRules {
    private GolemBrainDropRules() {}

    public static boolean shouldDrop(boolean zombie, boolean brainy, boolean recentlyHit, int looting, int roll) {
        return zombie && !brainy && recentlyHit && roll >= 0 && roll < 10 && (long) roll - looting < 1;
    }
}
