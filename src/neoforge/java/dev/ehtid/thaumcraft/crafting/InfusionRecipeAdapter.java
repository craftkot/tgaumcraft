package dev.ehtid.thaumcraft.crafting;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import dev.ehtid.thaumcraft.blockentity.InfusionMatrixBlockEntity;
import dev.ehtid.thaumcraft.blockentity.PedestalBlockEntity;
import dev.ehtid.thaumcraft.item.CrystalEssenceItem;
import dev.ehtid.thaumcraft.port.core.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;

/** World adapter for original recipes; unresolved content is never replaced by AIR. */
public final class InfusionRecipeAdapter {
    private InfusionRecipeAdapter() {}
    private static boolean modulesLoaded;
    public record Preview(LegacyInfusionRecipeCatalog.Recipe recipe,
                          LegacyInfusionStartPlanner.Prepared<ItemStack> matching) {}

    public static synchronized void loadModules() {
        if (modulesLoaded) return;
        var recipes = new ArrayList<LegacyInfusionRecipeCatalog.Recipe>();
        for (String path : List.of("/data/thaumcraft/infusion/runtime_recipes.json",
                "/data/thaumcraft/golem/infusion_recipes.json")) {
            try (var stream = InfusionRecipeAdapter.class.getResourceAsStream(path)) {
                if (stream == null) continue;
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
                            row.get("instability").getAsInt(), outputDefinition(row.getAsJsonObject("output"))));
                }
            } catch (IOException | RuntimeException error) {
                throw new IllegalStateException("Invalid source infusion module " + path,error);
            }
        }
        var ids = new HashSet<String>();
        for (var recipe : recipes)
            if (!ids.add(recipe.legacyId()) || LegacyInfusionRecipeCatalog.find(recipe.legacyId()) != null)
                throw new IllegalArgumentException("Duplicate infusion ID " + recipe.legacyId());
        recipes.forEach(LegacyInfusionRecipeCatalog::register);
        modulesLoaded = true;
    }
    private static String string(JsonObject row, String key) {
        return row.has(key) && !row.get(key).isJsonNull() ? row.get(key).getAsString() : null;
    }
    private static LegacyInfusionRecipeCatalog.Ingredient ingredient(JsonObject row) {
        var items = new ArrayList<String>(); var tags = new ArrayList<String>();
        if (row.has("itemIds")) row.getAsJsonArray("itemIds").forEach(e -> items.add(e.getAsString()));
        if (row.has("tagIds")) row.getAsJsonArray("tagIds").forEach(e -> tags.add(e.getAsString()));
        var alternatives = new ArrayList<LegacyInfusionRecipeCatalog.StackAlternative>();
        if (row.has("stackAlternatives")) for (var element : row.getAsJsonArray("stackAlternatives")) {
            var alternative = element.getAsJsonObject();
            alternatives.add(new LegacyInfusionRecipeCatalog.StackAlternative(alternative.get("itemId").getAsString(),
                    alternative.has("damage") ? alternative.get("damage").getAsInt() : null,
                    canonicalSnbt(string(alternative,"tagSnbt"))));
        }
        return new LegacyInfusionRecipeCatalog.Ingredient(items,tags,string(row,"crystalAspect"),alternatives,
                canonicalSnbt(string(row,"requiredTagSnbt")),row.has("any") && row.get("any").getAsBoolean());
    }
    private static LegacyInfusionRecipeCatalog.Output outputDefinition(JsonObject row) {
        if (row == null) return LegacyInfusionRecipeCatalog.Output.stack();
        String components = string(row,"componentsJson");
        if (row.has("components")) components = row.get("components").toString();
        if (components != null) components = canonicalJson(JsonParser.parseString(components)).toString();
        String value = string(row,"tagValueSnbt");
        if (value != null) value = stableTag(parseValue(value));
        return new LegacyInfusionRecipeCatalog.Output(
                LegacyInfusionRecipeCatalog.OutputMode.valueOf(row.has("mode") ? row.get("mode").getAsString() : "STACK"),
                components,canonicalSnbt(string(row,"tagSnbt")),string(row,"tagKey"),value,string(row,"enchantmentId"));
    }
    private static JsonElement canonicalJson(JsonElement element) {
        if (element.isJsonObject()) {
            var result = new JsonObject();
            var entries = new TreeMap<String,JsonElement>();
            element.getAsJsonObject().entrySet().forEach(entry -> entries.put(entry.getKey(),entry.getValue()));
            entries.forEach((key,value) -> result.add(key,canonicalJson(value)));
            return result;
        }
        if (element.isJsonArray()) {
            var result = new JsonArray(); element.getAsJsonArray().forEach(value -> result.add(canonicalJson(value))); return result;
        }
        return element;
    }
    private static String stableTag(Tag tag) {
        if (tag instanceof CompoundTag compound) {
            var entries = new ArrayList<String>();
            for (String key : new TreeSet<>(compound.getAllKeys()))
                entries.add(StringTag.quoteAndEscape(key) + ":" + stableTag(compound.get(key)));
            return "{" + String.join(",",entries) + "}";
        }
        if (tag instanceof ListTag list) {
            var entries = new ArrayList<String>(); list.forEach(value -> entries.add(stableTag(value)));
            return "[" + String.join(",",entries) + "]";
        }
        return tag.toString();
    }
    private static CompoundTag parseCompound(String snbt) {
        try { return TagParser.parseTag(snbt); }
        catch (com.mojang.brigadier.exceptions.CommandSyntaxException invalid) {
            throw new IllegalArgumentException("Invalid original infusion NBT",invalid);
        }
    }
    private static Tag parseValue(String snbt) { return parseCompound("{value:" + snbt + "}").get("value"); }
    private static String canonicalSnbt(String snbt) { return snbt == null ? null : stableTag(parseCompound(snbt)); }
    private static CompoundTag customData(ItemStack stack) {
        var data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? new CompoundTag() : data.copyTag();
    }
    /** IngredientNBTTC compares each required top-level tag value exactly, allowing extra top-level keys. */
    private static boolean matchesTag(String snbt, ItemStack stack) {
        if (snbt == null) return true;
        var required = parseCompound(snbt); var supplied = customData(stack);
        for (String key : required.getAllKeys()) if (!Objects.equals(required.get(key),supplied.get(key))) return false;
        return true;
    }
    public static LegacyInfusionRecipeCatalog.Recipe recipe(String id) {
        loadModules(); return LegacyInfusionRecipeCatalog.find(id);
    }
    public static boolean matches(LegacyInfusionRecipeCatalog.Ingredient ingredient, ItemStack stack) {
        if (ingredient == null || stack == null || stack.isEmpty() || stack.getCount() != 1) return false;
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        boolean identity = ingredient.any() || ingredient.itemIds().contains(itemId)
                || ingredient.tagIds().stream().anyMatch(id -> stack.is(TagKey.create(Registries.ITEM,ResourceLocation.parse(id))))
                || ingredient.stackAlternatives().stream().anyMatch(part -> part.itemId().equals(itemId)
                    && (part.damage() == null || (part.damage() == 32767 && part.tagSnbt() == null)
                        || stack.getDamageValue() == part.damage())
                    && matchesTag(part.tagSnbt(),stack));
        return identity && matchesTag(ingredient.requiredTagSnbt(),stack) && (ingredient.crystalAspect() == null
                || CrystalEssenceItem.isOriginalInfusionCrystal(stack,ingredient.crystalAspect()));
    }
    /** BETA26 replaces each complete side stack with its real container item, including its tags. */
    public static ItemStack remainder(ItemStack input) {
        return input == null || input.isEmpty() ? ItemStack.EMPTY : input.copy().getCraftingRemainingItem().copy();
    }
    private static int infusionLevel(CompoundTag tag, int ordinal) {
        var enchants = tag.getList("infench",Tag.TAG_COMPOUND);
        for (int i = 0; i < enchants.size(); i++) {
            var entry = enchants.getCompound(i);
            if (entry.getShort("id") == ordinal) return entry.getShort("lvl");
        }
        return 0;
    }
    private static List<Integer> infusionEnchantments(CompoundTag tag) {
        var result = new ArrayList<Integer>(); var enchants = tag.getList("infench",Tag.TAG_COMPOUND);
        for (int i = 0; i < enchants.size(); i++) {
            int ordinal = enchants.getCompound(i).getShort("id");
            if (LegacyInfusionOutputLogic.validEnchantmentOrdinal(ordinal)) result.add(ordinal);
        }
        return result;
    }
    private static boolean eligible(LegacyInfusionRecipeCatalog.Recipe recipe, ItemStack center) {
        if (!matches(recipe.center(),center)) return false;
        if (recipe.output().mode() == LegacyInfusionRecipeCatalog.OutputMode.RUNIC)
            return center.getItem() instanceof ArmorItem;
        if (recipe.output().mode() != LegacyInfusionRecipeCatalog.OutputMode.INFUSION_ENCHANTMENT) return true;
        var enchant = LegacyInfusionOutputLogic.enchantment(recipe.output().enchantmentId());
        if (infusionLevel(customData(center),enchant.legacyOrdinal()) >= enchant.maxLevel()) return false;
        var classes = enchant.toolClasses();
        if (classes.contains("all")) return true;
        // The original weapon gate is the presence of a MAINHAND attack damage modifier.
        if (classes.contains("weapon")) {
            boolean[] weapon = {false};
            center.forEachModifier(EquipmentSlot.MAINHAND,(attribute,modifier) -> {
                if (attribute.equals(Attributes.ATTACK_DAMAGE)) weapon[0] = true;
            });
            if (weapon[0]) return true;
        }
        if ((center.getItem() instanceof AxeItem && classes.contains("axe"))
                || (center.getItem() instanceof PickaxeItem && classes.contains("pickaxe"))
                || (center.getItem() instanceof ShovelItem && classes.contains("shovel"))) return true;
        if (center.getItem() instanceof ArmorItem armor) {
            String slot = switch (armor.getEquipmentSlot()) {
                case HEAD -> "helm"; case CHEST -> "chest"; case LEGS -> "legs"; case FEET -> "boots"; default -> "none";
            };
            return classes.contains("armor") || classes.contains(slot);
        }
        return false;
    }
    /** Resolve input-dependent source price, component count and instability before matching or save validation. */
    public static LegacyInfusionRecipeCatalog.Recipe effectiveRecipe(LegacyInfusionRecipeCatalog.Recipe recipe, ItemStack center) {
        if (recipe == null || center == null || center.isEmpty() || !eligible(recipe,center)) return null;
        var costs = recipe.aspects(); var sides = recipe.components(); int instability = recipe.instability();
        if (recipe.output().mode() == LegacyInfusionRecipeCatalog.OutputMode.RUNIC) {
            int oldRunic = customData(center).getByte("TC.RUNIC");
            costs = LegacyInfusionOutputLogic.runicAspects(oldRunic);
            instability = LegacyInfusionOutputLogic.runicInstability(oldRunic);
            // The source sequence is salis, amber, then one amber for every old runic charge.
            if (sides.size() < 2) return null;
            var dynamicSides = new ArrayList<LegacyInfusionRecipeCatalog.Ingredient>(); dynamicSides.add(sides.getFirst());
            for (int i = 0; i < LegacyInfusionOutputLogic.runicAmberCount(oldRunic); i++) dynamicSides.add(sides.get(1));
            sides = dynamicSides;
        } else if (recipe.output().mode() == LegacyInfusionRecipeCatalog.OutputMode.INFUSION_ENCHANTMENT) {
            var enchant = LegacyInfusionOutputLogic.enchantment(recipe.output().enchantmentId()); var tag = customData(center);
            var present = infusionEnchantments(tag);
            int others = present.size() - (present.contains(enchant.legacyOrdinal()) ? 1 : 0);
            costs = LegacyInfusionOutputLogic.enchantmentAspects(recipe.aspects(),infusionLevel(tag,enchant.legacyOrdinal()),others);
        }
        try { return new LegacyInfusionRecipeCatalog.Recipe(recipe.legacyId(),recipe.research(),recipe.center(),sides,
                recipe.outputItemId(),recipe.outputCount(),costs,instability,recipe.output()); }
        catch (IllegalArgumentException unsupported) { return null; }
    }
    /** Legacy fixed output API, retained for the original crystal/module callers. */
    public static ItemStack output(LegacyInfusionRecipeCatalog.Recipe recipe) {
        if (recipe == null || recipe.output().mode() != LegacyInfusionRecipeCatalog.OutputMode.STACK
                || recipe.output().componentsJson() != null) return ItemStack.EMPTY;
        return output(recipe,ItemStack.EMPTY,null,bound -> bound - 1);
    }
    public static ItemStack output(LegacyInfusionRecipeCatalog.Recipe recipe, ItemStack center, HolderLookup.Provider lookup) {
        return output(recipe,center,lookup,bound -> bound - 1);
    }
    /** Evaluate exactly once at ritual start; callers persist the resulting full stack. */
    public static ItemStack output(LegacyInfusionRecipeCatalog.Recipe recipe, ItemStack center,
                                   HolderLookup.Provider lookup, IntUnaryOperator boundedRandom) {
        if (recipe == null) return ItemStack.EMPTY;
        var definition = recipe.output(); ItemStack result;
        if (definition.mode() == LegacyInfusionRecipeCatalog.OutputMode.STACK) {
            var id = ResourceLocation.parse(recipe.outputItemId());
            if (!BuiltInRegistries.ITEM.containsKey(id)) return ItemStack.EMPTY;
            result = new ItemStack(BuiltInRegistries.ITEM.get(id),recipe.outputCount());
            if (definition.componentsJson() != null) {
                if (lookup == null) return ItemStack.EMPTY;
                result.applyComponentsAndValidate(DataComponentPatch.CODEC.parse(lookup.createSerializationContext(JsonOps.INSTANCE),
                        JsonParser.parseString(definition.componentsJson())).getOrThrow());
            }
            if (definition.tagSnbt() != null) result.set(DataComponents.CUSTOM_DATA,CustomData.of(parseCompound(definition.tagSnbt())));
            if (center != null && !center.isEmpty() && center.isDamaged()
                    && result.isDamageableItem() && !result.isDamaged()) result.setDamageValue(LegacyInfusionOutputLogic.transferredDamage(
                    center.getDamageValue(),center.getMaxDamage(),center.isDamageableItem(),
                    result.getDamageValue(),result.getMaxDamage(),result.isDamageableItem()));
            return result;
        }
        if (center == null || center.isEmpty() || !eligible(recipe,center)) return ItemStack.EMPTY;
        result = center.copy(); var tag = customData(result);
        switch (definition.mode()) {
            case SET_TAG -> tag.put(definition.tagKey(),parseValue(definition.tagValueSnbt()).copy());
            case RUNIC -> tag.putByte("TC.RUNIC",LegacyInfusionOutputLogic.nextRunic(tag.getByte("TC.RUNIC")));
            case INFUSION_ENCHANTMENT -> {
                var enchant = LegacyInfusionOutputLogic.enchantment(definition.enchantmentId());
                int oldLevel = infusionLevel(tag,enchant.legacyOrdinal()); var existing = infusionEnchantments(tag);
                int randomTen = boundedRandom.applyAsInt(10);
                byte oldWarp = tag.getByte("TC.WARP");
                byte newWarp = LegacyInfusionOutputLogic.enchantmentWarp(oldWarp,existing.size(),randomTen);
                if (randomTen < existing.size()) tag.putByte("TC.WARP",newWarp);
                var list = tag.getList("infench",Tag.TAG_COMPOUND); boolean found = false;
                for (int i = 0; i < list.size(); i++) if (list.getCompound(i).getShort("id") == enchant.legacyOrdinal()) {
                    list.getCompound(i).putShort("lvl",(short)(oldLevel + 1)); found = true; break;
                }
                if (!found) { var entry = new CompoundTag(); entry.putShort("id",(short)enchant.legacyOrdinal());
                    entry.putShort("lvl",(short)(oldLevel + 1)); list.add(entry); }
                tag.put("infench",list);
            }
            case VANILLA_ENCHANTMENT -> {
                if (lookup == null) return ItemStack.EMPTY;
                var enchantment = lookup.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(ResourceKey.create(
                        Registries.ENCHANTMENT,ResourceLocation.parse(definition.enchantmentId())));
                int next = EnchantmentHelper.getItemEnchantmentLevel(enchantment,result) + 1;
                EnchantmentHelper.updateEnchantments(result,mutable -> mutable.set(enchantment,next));
                return result;
            }
            default -> throw new IllegalStateException("Unexpected original output mode");
        }
        result.set(DataComponents.CUSTOM_DATA,CustomData.of(tag)); return result;
    }
    private static boolean sameStack(ItemStack a, ItemStack b) {
        return !a.isEmpty() && !b.isEmpty() && a.getCount() == b.getCount() && ItemStack.isSameItemSameComponents(a,b);
    }
    /** A persisted random output is accepted only if every component is a source-legal outcome. */
    public static boolean isValidOutput(LegacyInfusionRecipeCatalog.Recipe recipe, ItemStack center,
                                        ItemStack saved, HolderLookup.Provider lookup) {
        if (recipe == null || saved == null || saved.isEmpty()) return false;
        if (sameStack(output(recipe,center,lookup,bound -> bound - 1),saved)) return true;
        return recipe.output().mode() == LegacyInfusionRecipeCatalog.OutputMode.INFUSION_ENCHANTMENT
                && sameStack(output(recipe,center,lookup,bound -> 0),saved);
    }
    public static Preview prepare(InfusionMatrixBlockEntity matrix, PlayerKnowledgeData knowledge) {
        loadModules();
        if (matrix.getLevel() == null || !(matrix.getLevel().getBlockEntity(matrix.getBlockPos().below(2))
                instanceof PedestalBlockEntity pedestal)) return null;
        ItemStack center = pedestal.displayedItem();
        for (var source : LegacyInfusionRecipeCatalog.all()) {
            var recipe = effectiveRecipe(source,center);
            if (recipe == null || output(recipe,center,matrix.getLevel().registryAccess()).isEmpty()) continue;
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
