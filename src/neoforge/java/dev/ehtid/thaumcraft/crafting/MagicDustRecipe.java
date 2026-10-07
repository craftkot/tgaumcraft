package dev.ehtid.thaumcraft.crafting;

import dev.ehtid.thaumcraft.item.CrystalEssenceItem;
import dev.ehtid.thaumcraft.port.core.RecipeMagicDustLogic;
import dev.ehtid.thaumcraft.registry.ThaumcraftItems;
import dev.ehtid.thaumcraft.registry.ThaumcraftRecipes;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * BETA26 RecipeMagicDust: exactly one bowl, flint and redstone plus three
 * crystal essences with distinct first legacy aspect tags, in any order.
 * Original bytecode returns ONE Salis Mundus and retains bowl and flint.
 */
public final class MagicDustRecipe extends CustomRecipe {
    public MagicDustRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        if (!canCraftInDimensions(input.width(), input.height())) return false;
        List<RecipeMagicDustLogic.Ingredient> ingredients = new ArrayList<>(input.size());
        for (int slot = 0; slot < input.size(); slot++) {
            ItemStack stack = input.getItem(slot);
            if (stack.isEmpty()) {
                ingredients.add(new RecipeMagicDustLogic.Ingredient(RecipeMagicDustLogic.Kind.EMPTY, null));
            } else if (stack.is(Items.BOWL)) {
                ingredients.add(new RecipeMagicDustLogic.Ingredient(RecipeMagicDustLogic.Kind.BOWL, null));
            } else if (stack.is(Items.FLINT)) {
                ingredients.add(new RecipeMagicDustLogic.Ingredient(RecipeMagicDustLogic.Kind.FLINT, null));
            } else if (stack.is(Items.REDSTONE)) {
                ingredients.add(new RecipeMagicDustLogic.Ingredient(RecipeMagicDustLogic.Kind.REDSTONE, null));
            } else if (stack.is(ThaumcraftItems.CRYSTAL_ESSENCE.get())) {
                ingredients.add(new RecipeMagicDustLogic.Ingredient(
                        RecipeMagicDustLogic.Kind.CRYSTAL_ESSENCE, CrystalEssenceItem.legacyAspect(stack)));
            } else {
                return false;
            }
        }
        return RecipeMagicDustLogic.matches(ingredients);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return RecipeMagicDustLogic.canFit(width, height);
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        return new ItemStack(ThaumcraftItems.SALIS_MUNDUS.get());
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return new ItemStack(ThaumcraftItems.SALIS_MUNDUS.get());
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(input.size(), ItemStack.EMPTY);
        for (int slot = 0; slot < input.size(); slot++) {
            ItemStack stack = input.getItem(slot);
            if (stack.isEmpty()) continue;
            // BETA26 calls ForgeHooks.getContainerItem first, then overrides
            // bowl and flint with a one-count COPY (preserving stack components).
            ItemStack container = stack.getCraftingRemainingItem();
            if (stack.is(Items.BOWL) || stack.is(Items.FLINT)) {
                container = stack.copyWithCount(1);
            }
            remaining.set(slot, container);
        }
        return remaining;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ThaumcraftRecipes.MAGIC_DUST.get();
    }
}
