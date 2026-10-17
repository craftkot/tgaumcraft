package dev.ehtid.thaumcraft.crafting;

import dev.ehtid.thaumcraft.item.JarLabelItem;
import dev.ehtid.thaumcraft.item.PhialItem;
import dev.ehtid.thaumcraft.registry.ThaumcraftItems;
import dev.ehtid.thaumcraft.registry.ThaumcraftRecipes;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/** ConfigRecipes#label_<aspect>: blank label + exactly ten-unit filled
 * phial in any crafting slots. Does NOT accept an empty/partial phial.
 */
public final class JarLabelFromPhialRecipe extends CustomRecipe {
    public JarLabelFromPhialRecipe(CraftingBookCategory category) { super(category); }

    @Override public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    private static String validAspect(CraftingInput input) {
        int blanks = 0, phials = 0;
        String aspect = null;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) continue;
            if (JarLabelItem.isBlank(stack)) {
                blanks++;
            } else if (stack.is(ThaumcraftItems.PHIAL.get())
                    && PhialItem.containedAmount(stack) == PhialItem.CAPACITY) {
                phials++;
                aspect = PhialItem.containedAspect(stack);
            } else return null;
        }
        return blanks == 1 && phials == 1 ? aspect : null;
    }

    @Override public boolean matches(CraftingInput input, Level level) {
        return canCraftInDimensions(input.width(), input.height())
                && validAspect(input) != null;
    }

    @Override public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        String aspect = validAspect(input);
        return aspect == null ? ItemStack.EMPTY : JarLabelItem.filled(aspect);
    }

    @Override public ItemStack getResultItem(HolderLookup.Provider registries) {
        return new ItemStack(ThaumcraftItems.JAR_LABEL.get());
    }

    @Override public RecipeSerializer<?> getSerializer() {
        return ThaumcraftRecipes.JAR_LABEL_FROM_PHIAL.get();
    }
}
