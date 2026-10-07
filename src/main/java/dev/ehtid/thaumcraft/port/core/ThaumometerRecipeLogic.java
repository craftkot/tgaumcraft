package dev.ehtid.thaumcraft.port.core;

/** Exact critical-path BETA26 ShapedArcaneRecipe for thaumcraft:thaumometer. */
public final class ThaumometerRecipeLogic {
    private ThaumometerRecipeLogic() {}
    public static final int BASE_VIS=20;
    public static final String RESEARCH="FIRSTSTEPS@2";
    public static final int LEGACY_CRAFT_HASH=1777627067;
    public static final String LEGACY_CRAFT_MARKER="[#]"+LEGACY_CRAFT_HASH;
    public static int discountedVis(float totalDiscount){ return (int)(BASE_VIS * (1.0f-totalDiscount)); }
    public static boolean matches(PlayerKnowledgeData k, ArcaneWorkbenchRuntime w) {
        if (!k.isResearchKnown(RESEARCH)) return false;
        int[] gold={1,3,5,7};
        for(int i=0;i<9;i++) {
            ArcaneWorkbenchRuntime.Stack s=w.get(i);
            if(i==4) { if(s.empty()||!"minecraft:glass_pane".equals(s.itemId())||s.count()<1) return false; }
            else if(contains(gold,i)) { if(s.empty()||!"minecraft:gold_ingot".equals(s.itemId())||s.count()<1) return false; }
            else if(!s.empty()) return false;
        }
        for(int i=0;i<6;i++) if(!w.crystalSlotAccepts(9+i,w.get(9+i))||w.get(9+i).count()<1) return false;
        return true;
    }
    private static boolean contains(int[] a,int v){ for(int x:a)if(x==v)return true; return false; }
    public record CraftResult(boolean crafted, int visCost, String outputId, boolean syncRequested) {}
    public static CraftResult craft(PlayerKnowledgeData k, ArcaneWorkbenchRuntime w, MinimalAuraRuntime aura, long chunkKey, float discount) {
        if(!matches(k,w)) return new CraftResult(false,0,null,false);
        int cost=discountedVis(discount); if(!aura.canSpend(chunkKey,cost)) return new CraftResult(false,cost,null,false);
        // Work on a copy: validation/spend/consumption commit as one server transaction.
        ArcaneWorkbenchRuntime next=w.copy();
        for(int i:new int[]{1,3,4,5,7}) next.consume(i,1);
        for(int i=9;i<15;i++) next.consume(i,1);
        if(!aura.spend(chunkKey,cost)) return new CraftResult(false,cost,null,false);
        w.replaceFrom(next);
        k.addResearch(LEGACY_CRAFT_MARKER);
        return new CraftResult(true,cost,"thaumcraft:thaumometer",true);
    }
}
