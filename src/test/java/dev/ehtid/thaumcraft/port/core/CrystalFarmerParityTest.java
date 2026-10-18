package dev.ehtid.thaumcraft.port.core;

import java.util.List;

/** BETA26 ConfigRecipes.initializeInfusionRecipes, offsets 403..1100. */
public final class CrystalFarmerParityTest {
    private static int assertions;

    public static void main(String[] args) {
        AspectRegistry aspects = new AspectRegistry();
        var catalog = LegacyInfusionCrystalFarmerCatalog.all();
        eq(7, catalog.size());
        eq("CRYSTALFARMER", LegacyInfusionCrystalFarmerCatalog.RESEARCH);
        eq("minecraft:wheat_seeds", LegacyInfusionCrystalFarmerCatalog.SEED_ITEM);
        eq("thaumcraft:salis_mundus", LegacyInfusionCrystalFarmerCatalog.SALIS_ITEM);
        String[] variants = {"Air","Fire","Water","Earth","Order","Entropy","Flux"};
        String[] tags = {"aer","ignis","aqua","terra","ordo","perditio","vitium"};
        for (int i=0; i<catalog.size();i++) {
            var recipe = catalog.get(i);
            eq("thaumcraft:CrystalCluster"+variants[i], recipe.legacyId());
            eq("thaumcraft:crystal_"+tags[i], recipe.outputBlockId());
            eq(tags[i], recipe.aspectTag());
            eq(i==6 ? 4 : 0, recipe.instability());
            eq(recipe, LegacyInfusionCrystalFarmerCatalog.forAspect(tags[i]));
            var cost = LegacyInfusionCrystalFarmerCatalog.essentia(recipe, aspects);
            eq(10, cost.getAmount(aspects.get(tags[i])));
            eq(10, cost.getAmount(aspects.CRYSTAL));
            eq(5, cost.getAmount(aspects.TRAP));
            eq(25, cost.visSize());
            cost.reduce(aspects.CRYSTAL, 10);
            eq(10,LegacyInfusionCrystalFarmerCatalog.essentia(recipe,aspects)
                    .getAmount(aspects.CRYSTAL));

            var matcher = LegacyInfusionCrystalFarmerCatalog.matcher(
                    recipe, aspects, (String x)->recipe.aspectTag().equals(x),
                    "wheat_seeds"::equals, "salis_mundus"::equals);
            var user = new PlayerKnowledgeData();
            var supplied=List.of("salis_mundus","wheat_seeds");
            eq(null, LegacyInfusionStartPlanner.prepare(
                    true,true,tags[i],supplied,user,matcher,1.0f));
            user.addResearch("CRYSTALFARMER");
            var ready=LegacyInfusionStartPlanner.prepare(
                    true,true,tags[i],supplied,user,matcher,1.0f);
            check(ready!=null);
            eq(recipe.legacyId(),ready.recipeId());
            // Physical occupied-pedestal order is Salis then Seeds; the
            // matcher records input index -> required ingredient index.
            // These indices are used to persist exact source pedestals.
            eq(1,ready.ingredientIndices()[0]);
            eq(0,ready.ingredientIndices()[1]);
            eq(25,ready.cost().visSize());
            eq(recipe.instability(),ready.instability());
            eq(null,LegacyInfusionStartPlanner.prepare(
                    true,true,"wrong",supplied,user,matcher,1.0f));
            eq(null,LegacyInfusionStartPlanner.prepare(
                    true,true,tags[i],List.of("wheat_seeds","wheat_seeds"),
                    user,matcher,1.0f));
            eq(null,LegacyInfusionStartPlanner.prepare(
                    true,true,tags[i],List.of("wheat_seeds"),
                    user,matcher,1.0f));
            var half=LegacyInfusionStartPlanner.prepare(
                    true,true,tags[i],supplied,user,matcher,0.1f);
            check(half != null);
            eq(5,half.cost().getAmount(aspects.get(tags[i])));
            eq(5,half.cost().getAmount(aspects.CRYSTAL));
            eq(2,half.cost().getAmount(aspects.TRAP));
        }
        eq(null,LegacyInfusionCrystalFarmerCatalog.forAspect("unknown"));
        System.out.println("Crystal farmer BETA26 parity: OK ("+assertions+" assertions)");
    }
    private static void check(boolean value) {
        assertions++;
        if (!value) throw new AssertionError("expected true");
    }
    private static void eq(Object expected, Object actual) {
        assertions++;
        if (!java.util.Objects.equals(expected, actual))
            throw new AssertionError("expected "+expected+" but got "+actual);
    }
}
