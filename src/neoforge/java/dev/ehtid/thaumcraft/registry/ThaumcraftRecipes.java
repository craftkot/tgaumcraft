package dev.ehtid.thaumcraft.registry;

import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.crafting.MagicDustRecipe;
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

    public static void register(IEventBus modBus) {
        SERIALIZERS.register(modBus);
    }
}
