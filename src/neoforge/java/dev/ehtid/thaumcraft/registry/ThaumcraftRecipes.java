package dev.ehtid.thaumcraft.registry;

import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.crafting.MagicDustRecipe;
import dev.ehtid.thaumcraft.crafting.BrassBraceRecipe;
import dev.ehtid.thaumcraft.crafting.JarLabelFromPhialRecipe;
import dev.ehtid.thaumcraft.crafting.JarLabelResetRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Serializer for the BETA26 FIRSTSTEPS Salis Mundus special crafting recipe. */
public final class ThaumcraftRecipes {
    private ThaumcraftRecipes() {}

    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, ThaumcraftPort.MOD_ID);

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<MagicDustRecipe>> MAGIC_DUST =
            SERIALIZERS.register("magic_dust",
                    () -> new SimpleCraftingRecipeSerializer<>(MagicDustRecipe::new));

    /** Exact brass metadata=8 recipe, NOT every TC nugget variant. */
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<BrassBraceRecipe>> BRASS_BRACE =
            SERIALIZERS.register("brass_brace",
                    () -> new SimpleCraftingRecipeSerializer<>(BrassBraceRecipe::new));

    /** Original BETA26 label + full (10) phial → one typed label. */
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<JarLabelFromPhialRecipe>>
            JAR_LABEL_FROM_PHIAL = SERIALIZERS.register("label_from_phial",
                    () -> new SimpleCraftingRecipeSerializer<>(JarLabelFromPhialRecipe::new));

    /** Original JarLabelNull: one typed label → one blank label. */
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<JarLabelResetRecipe>>
            JAR_LABEL_RESET = SERIALIZERS.register("label_reset",
                    () -> new SimpleCraftingRecipeSerializer<>(JarLabelResetRecipe::new));

    public static void register(IEventBus modBus) {
        SERIALIZERS.register(modBus);
    }
}
