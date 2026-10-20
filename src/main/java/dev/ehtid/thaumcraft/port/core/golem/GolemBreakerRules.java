package dev.ehtid.thaumcraft.port.core.golem;

import java.util.Set;

/** BETA26 SealBreaker: hardness work, required trait and OreDictionary.itemMatches semantics. */
public final class GolemBreakerRules {
    private GolemBreakerRules() {}
    public static final int NORMAL_STEP=21, SILK_STEP=7, CALLBACK_TICKS=11;
    public static boolean eligible(Set<GolemTrait> traits) { return traits.contains(GolemTrait.BREAKER); }
    public static boolean breakable(boolean air,float hardness) { return !air && Float.isFinite(hardness) && hardness>=0; }
    /** Source loops every nonempty ghost: whitelist ALL, blacklist NONE; empty list accepts either mode. */
    public static boolean acceptsFilters(java.util.List<Boolean> matches,boolean blacklist) {
        return blacklist?matches.stream().noneMatch(Boolean::booleanValue):matches.stream().allMatch(Boolean::booleanValue);
    }
    public static int work(float hardness) {
        if(!breakable(false,hardness)) throw new IllegalArgumentException("Unbreakable hardness");
        return (int)(hardness*10.0F);
    }
    public static boolean completes(int remaining,boolean silk) { return remaining<=(silk?SILK_STEP:NORMAL_STEP); }
    public static int remaining(int work,boolean silk) { return Math.max(0,work-(silk?SILK_STEP:NORMAL_STEP)); }
    public static int crackStage(int remaining,float hardness) {
        return hardness<=0?-1:Math.max(0,Math.min(9,(int)(9.0F*(1.0F-remaining/(hardness*10.0F)))));
    }
    /** Source compares block drop (target) to ghost (input), ignoring all NBT and ore groups. Empty ghost accepts both modes. */
    public static boolean accepts(boolean ghostEmpty,boolean blacklist,boolean sameItem,int blockMetadata,int ghostMetadata,boolean metadata) {
        if(ghostEmpty) return true;
        boolean match=sameItem && (!metadata || blockMetadata==ghostMetadata);
        return blacklist?!match:match;
    }
}
