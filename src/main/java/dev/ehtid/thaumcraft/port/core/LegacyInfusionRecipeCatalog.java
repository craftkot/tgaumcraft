package dev.ehtid.thaumcraft.port.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Source recipe data, independent of Minecraft registries and content modules. */
public final class LegacyInfusionRecipeCatalog {
    private LegacyInfusionRecipeCatalog() {}
    public record StackAlternative(String itemId, Integer damage, String tagSnbt) {
        public StackAlternative {
            validateId(itemId);
            if (damage != null && damage < 0) throw new IllegalArgumentException("Negative ingredient damage");
        }
    }
    public record Ingredient(List<String> itemIds, List<String> tagIds, String crystalAspect,
                             List<StackAlternative> stackAlternatives, String requiredTagSnbt, boolean any) {
        public Ingredient(List<String> itemIds, List<String> tagIds, String crystalAspect) {
            this(itemIds,tagIds,crystalAspect,List.of(),null,false);
        }
        public Ingredient {
            itemIds = List.copyOf(itemIds); tagIds = List.copyOf(tagIds);
            stackAlternatives = List.copyOf(stackAlternatives);
            if (itemIds.isEmpty() && tagIds.isEmpty() && stackAlternatives.isEmpty() && !any)
                throw new IllegalArgumentException("Empty ingredient");
            for (String id : itemIds) validateId(id);
            for (String id : tagIds) validateId(id);
            if (crystalAspect != null && new AspectRegistry().get(crystalAspect) == null)
                throw new IllegalArgumentException("Unknown crystal aspect");
        }
        public static Ingredient item(String id) { return new Ingredient(List.of(id),List.of(),null); }
        public static Ingredient tag(String id) { return new Ingredient(List.of(),List.of(id),null); }
        public static Ingredient crystal(String aspect) { return new Ingredient(List.of("thaumcraft:crystal_essence"),List.of(),aspect); }
        public static Ingredient anyStack() { return new Ingredient(List.of(),List.of(),null,List.of(),null,true); }
        private boolean legacyDefinition() { return stackAlternatives.isEmpty() && requiredTagSnbt == null && !any; }
    }
    public enum OutputMode { STACK, SET_TAG, VANILLA_ENCHANTMENT, RUNIC, INFUSION_ENCHANTMENT }
    /** Registry-independent description; SNBT values preserve their original numeric tag types. */
    public record Output(OutputMode mode, String componentsJson, String tagSnbt,
                         String tagKey, String tagValueSnbt, String enchantmentId) {
        public Output {
            Objects.requireNonNull(mode);
            if (mode == OutputMode.SET_TAG && (tagKey == null || tagKey.isBlank() || tagValueSnbt == null))
                throw new IllegalArgumentException("Missing original NBT output");
            if ((mode == OutputMode.VANILLA_ENCHANTMENT || mode == OutputMode.INFUSION_ENCHANTMENT)
                    && (enchantmentId == null || enchantmentId.isBlank()))
                throw new IllegalArgumentException("Missing original enchantment output");
        }
        public static Output stack() { return new Output(OutputMode.STACK,null,null,null,null,null); }
        public boolean legacyDefinition() { return equals(stack()); }
    }
    public record Recipe(String legacyId, String research, Ingredient center, List<Ingredient> components,
                         String outputItemId, int outputCount, Map<String,Integer> aspects, int instability, Output output) {
        public Recipe(String legacyId, String research, Ingredient center, List<Ingredient> components,
                      String outputItemId, int outputCount, Map<String,Integer> aspects, int instability) {
            this(legacyId,research,center,components,outputItemId,outputCount,aspects,instability,Output.stack());
        }
        public Recipe {
            if (legacyId == null || legacyId.isBlank() || research == null || research.isBlank() || center == null)
                throw new IllegalArgumentException("Missing recipe identity or research gate");
            validateId(outputItemId);
            components = List.copyOf(components);
            aspects = Collections.unmodifiableMap(new LinkedHashMap<>(aspects));
            Objects.requireNonNull(output);
            if (components.isEmpty() || components.size() > 30 || outputCount < 1 || outputCount > 99 || instability < 0 || aspects.isEmpty())
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
            boolean legacy = output.legacyDefinition() && center.legacyDefinition()
                    && components.stream().allMatch(Ingredient::legacyDefinition);
            var definition = new StringBuilder(legacy ? "infusion-recipe-v1:" : "infusion-recipe-v2:");
            appendField(definition,legacyId); appendField(definition,research);
            appendIngredient(definition,center);
            definition.append(components.size()).append(':');
            components.forEach(part -> appendIngredient(definition,part));
            appendField(definition,outputItemId);
            definition.append(outputCount).append(':').append(instability).append(':').append(aspects.size()).append(':');
            new TreeMap<>(aspects).forEach((aspect,amount) -> {
                appendField(definition,aspect); definition.append(amount).append(':');
            });
            if (!legacy) {
                appendIngredientExtension(definition,center);
                components.forEach(part -> appendIngredientExtension(definition,part));
                appendField(definition,output.mode().name());
                appendField(definition,output.componentsJson()); appendField(definition,output.tagSnbt());
                appendField(definition,output.tagKey()); appendField(definition,output.tagValueSnbt());
                appendField(definition,output.enchantmentId());
            }
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
    private static void appendIngredientExtension(StringBuilder target, Ingredient ingredient) {
        target.append(ingredient.any()).append(':'); appendField(target,ingredient.requiredTagSnbt());
        target.append(ingredient.stackAlternatives().size()).append(':');
        ingredient.stackAlternatives().stream().sorted(Comparator.comparing(StackAlternative::itemId)
                .thenComparing(part -> Objects.toString(part.damage(),""))
                .thenComparing(part -> Objects.toString(part.tagSnbt(),""))).forEach(part -> {
            appendField(target,part.itemId()); appendField(target,Objects.toString(part.damage(),null));
            appendField(target,part.tagSnbt());
        });
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
