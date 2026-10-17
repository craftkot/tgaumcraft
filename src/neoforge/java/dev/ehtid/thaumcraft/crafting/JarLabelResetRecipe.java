package dev.ehtid.thaumcraft.crafting;

import dev.ehtid.thaumcraft.item.JarLabelItem;
import dev.ehtid.thaumcraft.registry.ThaumcraftItems;
import dev.ehtid.thaumcraft.registry.ThaumcraftRecipes;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/** Original ConfigRecipes#JarLabelNull: a typed label alone becomes blank. */
public final class JarLabelResetRecipe extends CustomRecipe {
    public JarLabelResetRecipe(CraftingBookCategory category) { super(category); }
    @Override public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 1;
    }

    @Override public boolean matches(CraftingInput input, Level level) {
        int count = 0;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) continue;
            if (JarLabelItem.selectedAspect(stack) == null || ++count != 1) return false;
        }
        return count == 1;
    }

    @Override public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        return matches(input, null) ? new ItemStack(ThaumcraftItems.JAR_LABEL.get()) : ItemStack.EMPTY;
    }
    @Override public ItemStack getResultItem(HolderLookup.Provider registries) {
        return new ItemStack(ThaumcraftItems.JAR_LABEL.get());
    }
    @Override public RecipeSerializer<?> getSerializer() {
        return ThaumcraftRecipes.JAR_LABEL_RESET.get();
    }
}
