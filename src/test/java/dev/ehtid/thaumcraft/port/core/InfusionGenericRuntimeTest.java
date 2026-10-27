package dev.ehtid.thaumcraft.port.core;

import java.util.*;

/** Actual N-input sequencing and stale-world receipt recovery, without Minecraft mocks. */
public final class InfusionGenericRuntimeTest {
    private static void require(boolean value) { if (!value) throw new AssertionError(); }
    private static void reject(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("Invalid transition accepted");
    }
    /** A paid checkpoint belongs to the full recipe definition that authorized it. */
    private static void rejectsChangedRecipeDefinitionOnCheckpointRestore(LegacyInfusionRecipeCatalog.Recipe original) {
        String saved = original.checkpointFingerprint();
        require(saved != null && !saved.isBlank());
        original.verifyCheckpointFingerprint(saved);
        reject(() -> original.verifyCheckpointFingerprint(null));
        reject(() -> original.verifyCheckpointFingerprint(""));
        reject(() -> original.verifyCheckpointFingerprint(" "));

        var reorderedCosts = new LinkedHashMap<String,Integer>();
        var costEntries = new ArrayList<>(original.aspects().entrySet());
        Collections.reverse(costEntries);
        costEntries.forEach(entry -> reorderedCosts.put(entry.getKey(),entry.getValue()));
        var sameDefinition = new LegacyInfusionRecipeCatalog.Recipe(original.legacyId(),original.research(),
                original.center(),original.components(),original.outputItemId(),1,reorderedCosts,original.instability());
        require(saved.equals(sameDefinition.checkpointFingerprint()));
        sameDefinition.verifyCheckpointFingerprint(saved);

        var changedSides = new ArrayList<>(original.components());
        changedSides.set(0,LegacyInfusionRecipeCatalog.Ingredient.item("minecraft:carrot"));
        var reorderedSides = new ArrayList<>(original.components());
        Collections.reverse(reorderedSides);
        var changedCosts = new LinkedHashMap<>(original.aspects());
        changedCosts.compute("herba",(aspect,amount) -> amount + 1);
        var changedDefinitions = List.of(
                new LegacyInfusionRecipeCatalog.Recipe("thaumcraft:ChangedSealHarvest",original.research(),original.center(),original.components(),original.outputItemId(),1,original.aspects(),original.instability()),
                new LegacyInfusionRecipeCatalog.Recipe(original.legacyId(),original.research(),original.center(),original.components(),"minecraft:diamond",1,original.aspects(),original.instability()),
                new LegacyInfusionRecipeCatalog.Recipe(original.legacyId(),"CHANGED_RESEARCH",original.center(),original.components(),original.outputItemId(),1,original.aspects(),original.instability()),
                new LegacyInfusionRecipeCatalog.Recipe(original.legacyId(),original.research(),LegacyInfusionRecipeCatalog.Ingredient.item("minecraft:stone"),original.components(),original.outputItemId(),1,original.aspects(),original.instability()),
                new LegacyInfusionRecipeCatalog.Recipe(original.legacyId(),original.research(),original.center(),changedSides,original.outputItemId(),1,original.aspects(),original.instability()),
                new LegacyInfusionRecipeCatalog.Recipe(original.legacyId(),original.research(),original.center(),reorderedSides,original.outputItemId(),1,original.aspects(),original.instability()),
                new LegacyInfusionRecipeCatalog.Recipe(original.legacyId(),original.research(),original.center(),original.components(),original.outputItemId(),1,changedCosts,original.instability()),
                new LegacyInfusionRecipeCatalog.Recipe(original.legacyId(),original.research(),original.center(),original.components(),original.outputItemId(),1,original.aspects(),original.instability() + 1));
        for (var changed : changedDefinitions)
            reject(() -> changed.verifyCheckpointFingerprint(saved));
    }
    private static void rejectsChangedIngredientDefinitionsOnCheckpointRestore() {
        var ingredient = new LegacyInfusionRecipeCatalog.Ingredient(
                List.of("minecraft:wheat_seeds","minecraft:sugar_cane"),
                List.of("minecraft:small_flowers","minecraft:tall_flowers"),null);
        var original = new LegacyInfusionRecipeCatalog.Recipe("thaumcraft:FingerprintIngredientRegression","INFUSION",
                LegacyInfusionRecipeCatalog.Ingredient.crystal("terra"),List.of(ingredient),
                "minecraft:diamond",1,Map.of("herba",10),0);
        String saved = original.checkpointFingerprint();
        var reorderedIngredient = new LegacyInfusionRecipeCatalog.Ingredient(
                List.of("minecraft:sugar_cane","minecraft:wheat_seeds"),
                List.of("minecraft:tall_flowers","minecraft:small_flowers"),null);
        var sameDefinition = new LegacyInfusionRecipeCatalog.Recipe(original.legacyId(),original.research(),
                original.center(),List.of(reorderedIngredient),original.outputItemId(),1,original.aspects(),0);
        sameDefinition.verifyCheckpointFingerprint(saved);
        var changedItem = new LegacyInfusionRecipeCatalog.Ingredient(
                List.of("minecraft:wheat_seeds","minecraft:carrot"),ingredient.tagIds(),null);
        var changedTag = new LegacyInfusionRecipeCatalog.Ingredient(
                ingredient.itemIds(),List.of("minecraft:small_flowers","minecraft:logs"),null);
        for (var changed : List.of(changedItem,changedTag)) {
            var changedRecipe = new LegacyInfusionRecipeCatalog.Recipe(original.legacyId(),original.research(),
                    original.center(),List.of(changed),original.outputItemId(),1,original.aspects(),0);
            reject(() -> changedRecipe.verifyCheckpointFingerprint(saved));
        }
        var changedCrystal = new LegacyInfusionRecipeCatalog.Recipe(original.legacyId(),original.research(),
                LegacyInfusionRecipeCatalog.Ingredient.crystal("ignis"),original.components(),
                original.outputItemId(),1,original.aspects(),0);
        reject(() -> changedCrystal.verifyCheckpointFingerprint(saved));
    }
    public static void main(String[] args) {
        for (int count : new int[]{2,3,6,12,30}) {
            var progress = LegacyInfusionIngredientProgress.initial(count);
            for (int index = 0; index < count; index++) {
                require(progress.nextIngredientBit() == 1 << index);
                var tooEarly = progress;
                reject(() -> tooEarly.afterActualPedestalRemoval(1 << Integer.bitCount(tooEarly.consumedMask())));
                for (int tick = 0; tick < 5; tick++) progress = progress.afterValidatedCountdown();
                require(progress.nextAction() == LegacyInfusionIngredientProgress.Action.CONSUME);
                progress = progress.afterActualPedestalRemoval(1 << index);
            }
            require(progress.readyToFinish());
            var done = progress;
            reject(() -> done.afterActualPedestalRemoval(1));
        }
        reject(() -> new LegacyInfusionIngredientProgress(6, 2, 0));
        reject(() -> new LegacyInfusionIngredientProgress(6, 63, 1));
        var stale = new LegacyInfusionIngredientProgress(6, 1, 3);
        require(stale.recoverSavedReceipts(new boolean[]{true,true,true,false,false,false},
                new boolean[]{false,false,false,true,true,true}).consumedMask() == 7);
        reject(() -> stale.recoverSavedReceipts(new boolean[]{true,false,true,false,false,false},
                new boolean[]{false,true,false,true,true,true}));
        reject(() -> stale.recoverSavedReceipts(new boolean[]{false,false,false,false,false,false},
                new boolean[]{true,true,true,true,true,true}));
        reject(() -> stale.recoverSavedReceipts(new boolean[]{true,true,false,false,false,false},
                new boolean[]{false,true,true,true,true,true}));
        for (var crystal : LegacyInfusionCrystalFarmerCatalog.all()) {
            var recipe = LegacyInfusionRecipeCatalog.find(crystal.legacyId());
            require(recipe != null && recipe.components().size() == 2);
            require(recipe.outputItemId().equals(crystal.outputBlockId()));
            var cost = LegacyInfusionStartPlanner.calculateEssentia(recipe.essentia(), 1.35F);
            var ledger = LegacyInfusionRitualLedger.begin(recipe.legacyId(),cost);
            LegacyInfusionRitualLedger.restoreVerified(recipe.legacyId(), ledger.requested(), ledger.remaining(), 0, 1.35F);
            reject(() -> LegacyInfusionRitualLedger.restoreVerified(recipe.legacyId(), ledger.requested(), ledger.remaining(), 0, 1F));
        }
        // Independently transcribed BETA26 SealHarvest: six distinct physical inputs, 30 essentia.
        var sides = List.of("wheat_seeds","pumpkin_seeds","melon_seeds","beetroot_seeds","sugar_cane","cactus")
                .stream().map(id -> LegacyInfusionRecipeCatalog.Ingredient.item("minecraft:" + id)).toList();
        var harvest = new LegacyInfusionRecipeCatalog.Recipe("thaumcraft:SealHarvest","SEALHARVEST",
                LegacyInfusionRecipeCatalog.Ingredient.item("thaumcraft:seal_blank"),sides,
                "thaumcraft:seal_harvest",1,Map.of("herba",10,"sensus",10,"humanus",10),0);
        LegacyInfusionRecipeCatalog.register(harvest);
        reject(() -> LegacyInfusionRecipeCatalog.register(harvest));
        rejectsChangedRecipeDefinitionOnCheckpointRestore(harvest);
        rejectsChangedIngredientDefinitionsOnCheckpointRestore();
        var predicates = sides.stream().<java.util.function.Predicate<String>>map(part -> part.itemIds()::contains).toList();
        var matcher = new LegacyInfusionStartPlanner.Recipe<String>(harvest.legacyId(),harvest.research(),
                harvest.center().itemIds()::contains,predicates,harvest.essentia(),harvest.instability());
        var knowledge = new PlayerKnowledgeData();
        var supplied = new ArrayList<>(sides.stream().map(part -> part.itemIds().getFirst()).toList());
        Collections.reverse(supplied);
        require(LegacyInfusionStartPlanner.prepare(true,true,"thaumcraft:seal_blank",supplied,knowledge,matcher,1F) == null);
        knowledge.addResearch("SEALHARVEST");
        var prepared = LegacyInfusionStartPlanner.prepare(true,true,"thaumcraft:seal_blank",supplied,knowledge,matcher,1F);
        require(prepared != null && prepared.cost().visSize() == 30);
        require(Arrays.equals(prepared.ingredientIndices(),new int[]{5,4,3,2,1,0}));
        var ledger = LegacyInfusionRitualLedger.begin(harvest.legacyId(),prepared.cost());
        var unpaid = ledger;
        reject(() -> LegacyInfusionFinishCheckpoint.initial().reserve(unpaid.readyForIngredients(),true,true));
        for (String aspect : List.of("herba","sensus","humanus"))
            for (int unit = 0; unit < 10; unit++) ledger = ledger.afterWorldDebit(aspect);
        require(ledger.readyForIngredients() && ledger.paid() == 30);
        var saved = LegacyInfusionRitualLedger.restoreVerified(harvest.legacyId(),ledger.requested(),ledger.remaining(),30,1F);
        require(saved.readyForIngredients());
        var reserved = LegacyInfusionFinishCheckpoint.initial().reserve(true,true,true);
        require(LegacyInfusionFinishCheckpoint.restore(reserved.phase().name()).phase() == LegacyInfusionFinishCheckpoint.Phase.STALLED);
        var receiptId = UUID.randomUUID();
        var receipt = new LegacyInfusionPedestalReceipt(receiptId,1 << 5);
        require(receipt.provesSavedRemoval(receiptId,1 << 5,true));
        require(!receipt.provesSavedRemoval(UUID.randomUUID(),1 << 5,true));
        require(!receipt.provesSavedRemoval(receiptId,1 << 5,false));
        reject(() -> new LegacyInfusionPedestalReceipt(receiptId,3));
        System.out.println("BETA26 generic infusion runtime: OK");
    }
}
