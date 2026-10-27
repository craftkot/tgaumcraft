package dev.ehtid.thaumcraft.crafting;

import com.google.gson.*;
import dev.ehtid.thaumcraft.blockentity.InfusionMatrixBlockEntity;
import dev.ehtid.thaumcraft.item.CrystalEssenceItem;
import dev.ehtid.thaumcraft.port.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Predicate;

/** World adapter for registered original recipes; absent content is never replaced by AIR. */
public final class InfusionRecipeAdapter {
    private InfusionRecipeAdapter() {}
    private static boolean modulesLoaded;
    public record Preview(LegacyInfusionRecipeCatalog.Recipe recipe,
                          LegacyInfusionStartPlanner.Prepared<ItemStack> matching) {}
    public static synchronized void loadModules() {
        if (modulesLoaded) return;
        // Optional content module supplied by the golem lane. Kept outside vanilla recipe discovery.
        String path = "/data/thaumcraft/golem/infusion_recipes.json";
        try (var stream = InfusionRecipeAdapter.class.getResourceAsStream(path)) {
            if (stream != null) {
                var recipes = new ArrayList<LegacyInfusionRecipeCatalog.Recipe>();
                var array = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonArray();
                for (var element : array) {
                    var row = element.getAsJsonObject();
                    var sides = new ArrayList<LegacyInfusionRecipeCatalog.Ingredient>();
                    for (var side : row.getAsJsonArray("components")) sides.add(ingredient(side.getAsJsonObject()));
                    var costs = new LinkedHashMap<String,Integer>();
                    row.getAsJsonObject("aspects").entrySet().forEach(e -> costs.put(e.getKey(),e.getValue().getAsInt()));
                    recipes.add(new LegacyInfusionRecipeCatalog.Recipe(row.get("legacyId").getAsString(),
                            row.get("research").getAsString(),ingredient(row.getAsJsonObject("center")),sides,
                            row.get("outputItemId").getAsString(),row.get("outputCount").getAsInt(),costs,
                            row.get("instability").getAsInt()));
                }
                var ids = new HashSet<String>();
                for (var recipe : recipes)
                    if (!ids.add(recipe.legacyId()) || LegacyInfusionRecipeCatalog.find(recipe.legacyId()) != null)
                        throw new IllegalArgumentException("Duplicate infusion ID " + recipe.legacyId());
                recipes.forEach(LegacyInfusionRecipeCatalog::register);
            }
            modulesLoaded = true;
        } catch (IOException | RuntimeException error) {
            throw new IllegalStateException("Invalid source infusion module " + path,error);
        }
    }
    private static LegacyInfusionRecipeCatalog.Ingredient ingredient(JsonObject row) {
        var items = new ArrayList<String>(); var tags = new ArrayList<String>();
        row.getAsJsonArray("itemIds").forEach(e -> items.add(e.getAsString()));
        row.getAsJsonArray("tagIds").forEach(e -> tags.add(e.getAsString()));
        String aspect = row.has("crystalAspect") && !row.get("crystalAspect").isJsonNull()
                ? row.get("crystalAspect").getAsString() : null;
        return new LegacyInfusionRecipeCatalog.Ingredient(items,tags,aspect);
    }
    public static LegacyInfusionRecipeCatalog.Recipe recipe(String id) {
        loadModules(); return LegacyInfusionRecipeCatalog.find(id);
    }
    public static boolean matches(LegacyInfusionRecipeCatalog.Ingredient ingredient, ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getCount() != 1) return false;
        boolean itemMatch = ingredient.itemIds().contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        boolean tagMatch = ingredient.tagIds().stream().anyMatch(id -> stack.is(TagKey.create(Registries.ITEM,ResourceLocation.parse(id))));
        return (itemMatch || tagMatch) && (ingredient.crystalAspect() == null
                || CrystalEssenceItem.isOriginalInfusionCrystal(stack,ingredient.crystalAspect()));
    }
    public static ItemStack output(LegacyInfusionRecipeCatalog.Recipe recipe) {
        if (recipe == null) return ItemStack.EMPTY;
        var id = ResourceLocation.parse(recipe.outputItemId());
        if (!BuiltInRegistries.ITEM.containsKey(id)) return ItemStack.EMPTY;
        return new ItemStack(BuiltInRegistries.ITEM.get(id),recipe.outputCount());
    }
    public static Preview prepare(InfusionMatrixBlockEntity matrix, PlayerKnowledgeData knowledge) {
        loadModules();
        for (var recipe : LegacyInfusionRecipeCatalog.all()) {
            if (output(recipe).isEmpty()) continue;
            List<Predicate<ItemStack>> sides = recipe.components().stream()
                    .<Predicate<ItemStack>>map(part -> stack -> matches(part,stack)).toList();
            var matcher = new LegacyInfusionStartPlanner.Recipe<ItemStack>(recipe.legacyId(),recipe.research(),
                    stack -> matches(recipe.center(),stack),sides,recipe.essentia(),recipe.instability());
            var prepared = matrix.prepareInfusionStartFromWorld(knowledge,matcher);
            if (prepared != null) return new Preview(recipe,prepared);
        }
        return null;
    }
}
