package dev.ehtid.thaumcraft.port.core;

import java.util.List;
import java.util.Map;

/** Source-derived output count, durability and special infusion transformations. */
public final class InfusionOutputParityTest {
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        var recipe = new LegacyInfusionRecipeCatalog.Recipe("thaumcraft:SourceCount", "INFUSION",
                LegacyInfusionRecipeCatalog.Ingredient.item("minecraft:stone"),
                List.of(LegacyInfusionRecipeCatalog.Ingredient.item("minecraft:diamond")),
                "minecraft:arrow", 4, Map.of("ordo", 10), 0);
        require(recipe.outputCount() == 4, "BETA26 copied ItemStack preserves result count");
        var cheapest = new LegacyInfusionRecipeCatalog.Recipe("thaumcraft:ZeroFloor",recipe.research(),recipe.center(),
                recipe.components(),recipe.outputItemId(),1,Map.of("ordo",1),0);
        var zeroCost = LegacyInfusionStartPlanner.calculateEssentia(cheapest.essentia(),0.5F);
        require(zeroCost.size()==1 && zeroCost.visSize()==0,"BETA26 retains floored zero aspect keys for later missing-ingredient penalties");
        var freeLedger = LegacyInfusionRitualLedger.begin(cheapest.legacyId(),zeroCost);
        require(freeLedger.readyForIngredients() && freeLedger.paid() == 0, "source cost floors can all become zero");
        require(LegacyInfusionRitualLedger.restoreVerified(cheapest,Map.of("ordo",0),Map.of("ordo",0),0,0.5F).readyForIngredients(),
                "source-validated zero-cost checkpoint restores");
        try {
            LegacyInfusionRitualLedger.restore(cheapest.legacyId(),Map.of(),Map.of(),0);
            throw new AssertionError("unverified empty price accepted");
        } catch (IllegalArgumentException expected) { }
        var penalty = freeLedger.withPenalty("ordo");
        require(penalty.requested().equals(Map.of("ordo",0)) && penalty.surchargeCosts().equals(Map.of("ordo",1))
                && penalty.remaining().equals(Map.of("ordo",1)) && penalty.paid()==0,
                "Missing ingredient adds source aspect unit while preserving original frozen price");
        var extraPaid = penalty.afterWorldDebit("ordo");
        require(extraPaid.readyForIngredients() && extraPaid.paid()==1 && extraPaid.surchargeCosts().get("ordo")==1,
                "Only an actual debit pays the added source unit");
        require(LegacyInfusionRitualLedger.restoreVerified(cheapest,extraPaid.requested(),extraPaid.remaining(),1,0.5F,
                extraPaid.surchargeCosts()).readyForIngredients(),"Source surcharge survives verified checkpoint");
        reject(() -> LegacyInfusionRitualLedger.restoreVerified(cheapest,Map.of("ordo",1),Map.of("ordo",0),1,0.5F,Map.of()),
                "Surcharge cannot forge original source cost");
        reject(() -> LegacyInfusionRitualLedger.restoreVerified(cheapest,Map.of("ordo",0),Map.of("ordo",0),1,0.5F,Map.of()),
                "Paid surcharge requires recorded source penalty");
        reject(() -> penalty.withPenalty("ignis"),"Penalty cannot introduce unrelated aspect");
        reject(() -> LegacyInfusionRitualLedger.restoreVerified(cheapest,Map.of("ordo",0),Map.of("ordo",0),100001,0.5F,
                Map.of("ordo",100001)),"Persisted surcharge is bounded");
        require(LegacyInfusionOutputLogic.transferredDamage(31,100,true,0,250,true) == 77,
                "source float damage fraction truncates");
        require(LegacyInfusionOutputLogic.transferredDamage(31,100,true,5,250,true) == 5,
                "pre-damaged outputs keep their damage");
        require(LegacyInfusionOutputLogic.transferredDamage(31,100,false,0,250,true) == 0,
                "non-damageable center cannot transfer metadata as durability");
        require(LegacyInfusionOutputLogic.runicAspects(0).equals(Map.of("praemunio",40,"vitreus",20,"potentia",20)),
                "first runic upgrade source price");
        require(LegacyInfusionOutputLogic.runicAspects(3).equals(Map.of("praemunio",180,"vitreus",90,"potentia",90)),
                "fourth runic upgrade exponential price");
        require(LegacyInfusionOutputLogic.runicAmberCount(3) == 4
                && LegacyInfusionOutputLogic.runicInstability(3) == 6, "runic side count and instability");
        require(LegacyInfusionOutputLogic.enchantmentAspects(Map.of("instrumentum",25),1,2)
                .equals(Map.of("instrumentum",66)), "source float enchant scaling truncates before altar multiplier");
        require(LegacyInfusionOutputLogic.enchantmentWarp(4,2,1) == 5
                && LegacyInfusionOutputLogic.enchantmentWarp(4,2,2) == 4, "warp source probability boundary");
        require(LegacyInfusionOutputLogic.enchantment("ESSENCE").legacyOrdinal() == 6
                && LegacyInfusionOutputLogic.enchantment("ESSENCE").maxLevel() == 5, "original enchant identity and limit");
        var tagged = new LegacyInfusionRecipeCatalog.Recipe(recipe.legacyId(),recipe.research(),recipe.center(),
                recipe.components(),recipe.outputItemId(),recipe.outputCount(),recipe.aspects(),recipe.instability(),
                new LegacyInfusionRecipeCatalog.Output(LegacyInfusionRecipeCatalog.OutputMode.SET_TAG,
                        null,null,"goggles","1b",null));
        require(!tagged.checkpointFingerprint().equals(recipe.checkpointFingerprint()),
                "output transformation is bound to paid checkpoint");
        var otherTag = new LegacyInfusionRecipeCatalog.Recipe(recipe.legacyId(),recipe.research(),recipe.center(),
                recipe.components(),recipe.outputItemId(),recipe.outputCount(),recipe.aspects(),recipe.instability(),
                new LegacyInfusionRecipeCatalog.Output(LegacyInfusionRecipeCatalog.OutputMode.SET_TAG,
                        null,null,"goggles","0b",null));
        require(!otherTag.checkpointFingerprint().equals(tagged.checkpointFingerprint()),
                "tag payload changes invalidate paid checkpoint");
        System.out.println("BETA26 infusion output semantics: OK");
    }
    private static void reject(Runnable action,String description) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError(description);
    }
}
