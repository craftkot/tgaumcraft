package dev.ehtid.thaumcraft.port.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Source recipe data, independent of Minecraft registries and content modules. */
public final class LegacyInfusionRecipeCatalog {
    private LegacyInfusionRecipeCatalog() {}
    public record Ingredient(List<String> itemIds, List<String> tagIds, String crystalAspect) {
        public Ingredient {
            itemIds = List.copyOf(itemIds); tagIds = List.copyOf(tagIds);
            if (itemIds.isEmpty() && tagIds.isEmpty()) throw new IllegalArgumentException("Empty ingredient");
            for (String id : itemIds) validateId(id);
            for (String id : tagIds) validateId(id);
            if (crystalAspect != null && new AspectRegistry().get(crystalAspect) == null)
                throw new IllegalArgumentException("Unknown crystal aspect");
        }
        public static Ingredient item(String id) { return new Ingredient(List.of(id),List.of(),null); }
        public static Ingredient tag(String id) { return new Ingredient(List.of(),List.of(id),null); }
        public static Ingredient crystal(String aspect) { return new Ingredient(List.of("thaumcraft:crystal_essence"),List.of(),aspect); }
    }
    public record Recipe(String legacyId, String research, Ingredient center, List<Ingredient> components,
                         String outputItemId, int outputCount, Map<String,Integer> aspects, int instability) {
        public Recipe {
            if (legacyId == null || legacyId.isBlank() || research == null || research.isBlank() || center == null)
                throw new IllegalArgumentException("Missing recipe identity or research gate");
            validateId(outputItemId);
            components = List.copyOf(components);
            aspects = Collections.unmodifiableMap(new LinkedHashMap<>(aspects));
            if (components.isEmpty() || components.size() > 30 || outputCount != 1 || instability < 0 || aspects.isEmpty())
                throw new IllegalArgumentException("Unsupported infusion recipe");
            var registry = new AspectRegistry();
            for (var e : aspects.entrySet())
                if (registry.get(e.getKey()) == null || e.getValue() == null || e.getValue() <= 0 || e.getValue() > 100000)
                    throw new IllegalArgumentException("Invalid original essentia cost");
        }
        public AspectListData essentia() {
            var registry = new AspectRegistry(); var result = new AspectListData();
            aspects.forEach((tag,amount) -> result.add(registry.get(tag),amount));
            return result;
        }
        /** Bind paid world checkpoints to the full definition that authorized the ritual. */
        public String checkpointFingerprint() {
            var definition = new StringBuilder("infusion-recipe-v1:");
            appendField(definition,legacyId); appendField(definition,research);
            appendIngredient(definition,center);
            definition.append(components.size()).append(':');
            components.forEach(part -> appendIngredient(definition,part));
            appendField(definition,outputItemId);
            definition.append(outputCount).append(':').append(instability).append(':').append(aspects.size()).append(':');
            new TreeMap<>(aspects).forEach((aspect,amount) -> {
                appendField(definition,aspect); definition.append(amount).append(':');
            });
            try {
                return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                        .digest(definition.toString().getBytes(StandardCharsets.UTF_8)));
            } catch (NoSuchAlgorithmException unavailable) {
                throw new AssertionError("Required SHA-256 digest unavailable",unavailable);
            }
        }
        public void verifyCheckpointFingerprint(String saved) {
            if (!checkpointFingerprint().equals(saved))
                throw new IllegalArgumentException("Saved infusion recipe definition has changed");
        }
    }
    private static void appendField(StringBuilder target, String value) {
        if (value == null) target.append("-1:");
        else target.append(value.length()).append(':').append(value);
    }
    private static void appendIngredient(StringBuilder target, Ingredient ingredient) {
        target.append(ingredient.itemIds().size()).append(':');
        ingredient.itemIds().stream().sorted().forEach(id -> appendField(target,id));
        target.append(ingredient.tagIds().size()).append(':');
        ingredient.tagIds().stream().sorted().forEach(id -> appendField(target,id));
        appendField(target,ingredient.crystalAspect());
    }
    private static void validateId(String id) {
        if (id == null || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) throw new IllegalArgumentException("Invalid registry ID");
    }
    private static final Map<String,Recipe> RECIPES = new LinkedHashMap<>();
    static {
        for (var crystal : LegacyInfusionCrystalFarmerCatalog.all()) {
            var costs = new LinkedHashMap<String,Integer>();
            costs.put(crystal.aspectTag(),10); costs.put("vitreus",10); costs.put("vinculum",5);
            register(new Recipe(crystal.legacyId(),"CRYSTALFARMER",Ingredient.crystal(crystal.aspectTag()),
                    List.of(Ingredient.item("minecraft:wheat_seeds"),Ingredient.item("thaumcraft:salis_mundus")),
                    crystal.outputBlockId(),1,costs,crystal.instability()));
        }
    }
    /** Called by source-backed module data loaders; saved NBT never registers a recipe. */
    public static synchronized void register(Recipe recipe) {
        Objects.requireNonNull(recipe);
        if (RECIPES.putIfAbsent(recipe.legacyId(),recipe) != null) throw new IllegalArgumentException("Duplicate infusion recipe " + recipe.legacyId());
    }
    public static synchronized Recipe find(String id) { return RECIPES.get(id); }
    public static synchronized List<Recipe> all() { return List.copyOf(RECIPES.values()); }
}
