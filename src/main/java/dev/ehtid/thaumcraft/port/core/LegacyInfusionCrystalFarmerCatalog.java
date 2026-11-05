package dev.ehtid.thaumcraft.port.core;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * All seven real BETA26 ConfigRecipes.initializeInfusionRecipes() crystal
 * farming entries (source bytecode offsets 403..1100).
 *
 * Input: ThaumcraftApiHelper.makeCrystal(aspect) in center,
 * exactly WHEAT_SEEDS + ItemsTC.salisMundus on the two surrounding pedestals.
 * Output: BlocksTC.crystalAir/Fire/Water/Earth/Order/Entropy/Taint.
 *
 * IMPORTANT: This is a faithful recipe CATALOG, not a magic way to create
 * missing BlockCrystal world blocks or to skip EssentiaHandler drainage.
 */
public final class LegacyInfusionCrystalFarmerCatalog {
    private LegacyInfusionCrystalFarmerCatalog() {}

    public static final String RESEARCH = "CRYSTALFARMER";
    public static final String SEED_ITEM = "minecraft:wheat_seeds";
    public static final String SALIS_ITEM = "thaumcraft:salis_mundus";
    public static final int CRYSTAL_VIS = 10;
    public static final int SPECIFIC_VIS = 10;
    public static final int TRAP_VIS = 5;

    public record Recipe(String legacyId, String outputBlockId, String aspectTag,
                         int instability) {
        public Recipe {
            Objects.requireNonNull(legacyId);
            Objects.requireNonNull(outputBlockId);
            Objects.requireNonNull(aspectTag);
            if (instability < 0) throw new IllegalArgumentException("instability");
        }
    }

    private static final List<Recipe> ORIGINAL = List.of(
            new Recipe("thaumcraft:CrystalClusterAir", "thaumcraft:crystal_aer", "aer", 0),
            new Recipe("thaumcraft:CrystalClusterFire", "thaumcraft:crystal_ignis", "ignis", 0),
            new Recipe("thaumcraft:CrystalClusterWater", "thaumcraft:crystal_aqua", "aqua", 0),
            new Recipe("thaumcraft:CrystalClusterEarth", "thaumcraft:crystal_terra", "terra", 0),
            new Recipe("thaumcraft:CrystalClusterOrder", "thaumcraft:crystal_ordo", "ordo", 0),
            new Recipe("thaumcraft:CrystalClusterEntropy", "thaumcraft:crystal_perditio", "perditio", 0),
            new Recipe("thaumcraft:CrystalClusterFlux", "thaumcraft:crystal_vitium", "vitium", 4)
    );

    public static List<Recipe> all() { return ORIGINAL; }

    /**
     * Source IngredientNBTTC compares the actual makeCrystal(aspect) payload,
     * NOT just the first aspect key. A forged crystal with an extra aspect
     * row or a different amount must not match a paid original recipe.
     * Raw NBT list entries (without duplicate-merging) are required.
     */
    public static boolean exactOriginalCrystalEntries(
            String expectedAspect, List<LegacyContainedAspectTags.Entry> raw) {
        if (expectedAspect == null || forAspect(expectedAspect) == null
                || raw == null || raw.size() != 1 || raw.get(0) == null)
            return false;
        var only = raw.get(0);
        return expectedAspect.equals(only.key()) && only.amount() == 1;
    }

    public static Recipe forAspect(String aspectTag) {
        for (Recipe spec : ORIGINAL)
            if (spec.aspectTag().equals(aspectTag)) return spec;
        return null;
    }

    /** A BETA26 AspectList instance per call: 10 matching + 10 Vitreus + 5 Vinculum. */
    public static AspectListData essentia(Recipe recipe, AspectRegistry aspects) {
        Objects.requireNonNull(recipe);
        Objects.requireNonNull(aspects);
        if (!ORIGINAL.contains(recipe)) throw new IllegalArgumentException("Not original crystal infusion");
        AspectData specific = aspects.get(recipe.aspectTag());
        if (specific == null) throw new IllegalStateException("Unregistered legacy aspect");
        return new AspectListData().add(specific, SPECIFIC_VIS)
                .add(aspects.CRYSTAL, CRYSTAL_VIS).add(aspects.TRAP, TRAP_VIS);
    }

    /**
     * Bridges source metadata to the existing unordered research-gated
     * InfusionRecipe matcher. Adapters MUST inspect the actual legacy
     * CrystalEssence NBT aspect; generic crystal item equality is incorrect.
     * The returned recipe does not contain a craftable output ItemStack.
     */
    public static <T> LegacyInfusionStartPlanner.Recipe<T> matcher(
            Recipe spec, AspectRegistry aspects,
            Predicate<T> exactAspectCrystal,
            Predicate<T> wheatSeeds,
            Predicate<T> salisMundus) {
        Objects.requireNonNull(exactAspectCrystal);
        Objects.requireNonNull(wheatSeeds);
        Objects.requireNonNull(salisMundus);
        return new LegacyInfusionStartPlanner.Recipe<>(
                spec.legacyId(), RESEARCH, exactAspectCrystal,
                List.of(wheatSeeds, salisMundus), essentia(spec, aspects),
                spec.instability());
    }
}
