package dev.ehtid.thaumcraft.crafting;

import dev.ehtid.thaumcraft.item.ThaumcraftNuggetItem;
import dev.ehtid.thaumcraft.registry.ThaumcraftItems;
import dev.ehtid.thaumcraft.registry.ThaumcraftRecipes;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/** ConfigRecipes#BrassBrace: NSN / S S / NSN, output two brass braces.
 * N is exactly legacy brass nugget metadata 8, not any 1.21 TC nugget.
 */
public final class BrassBraceRecipe extends CustomRecipe {
    public BrassBraceRecipe(CraftingBookCategory category) { super(category); }

    @Override public boolean canCraftInDimensions(int width, int height) {
        return width >= 3 && height >= 3;
    }

    @Override public boolean matches(CraftingInput input, Level level) {
        if (input.width() != 3 || input.height() != 3) return false;
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                ItemStack ingredient = input.getItem(x + y * 3);
                if (x == 1 && y == 1) {
                    if (!ingredient.isEmpty()) return false;
                } else if (x == 0 || x == 2) {
                    if (ThaumcraftNuggetItem.legacyMetadata(ingredient) != 8) return false;
                } else if (!ingredient.is(Items.STICK)) return false;
            }
        }
        return true;
    }

    @Override public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        return new ItemStack(ThaumcraftItems.JAR_BRACE.get(), 2);
    }
    @Override public ItemStack getResultItem(HolderLookup.Provider registries) {
        return new ItemStack(ThaumcraftItems.JAR_BRACE.get(), 2);
    }
    @Override public RecipeSerializer<?> getSerializer() {
        return ThaumcraftRecipes.BRASS_BRACE.get();
    }
}
