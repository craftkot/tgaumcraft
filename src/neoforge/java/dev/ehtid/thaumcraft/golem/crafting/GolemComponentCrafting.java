package dev.ehtid.thaumcraft.golem.crafting;

import dev.ehtid.thaumcraft.blockentity.ArcaneWorkbenchBlockEntity;
import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.item.PlateItem;
import dev.ehtid.thaumcraft.player.ThaumcraftPlayerAttachments;
import dev.ehtid.thaumcraft.port.core.golem.GolemComponentRecipes;
import dev.ehtid.thaumcraft.port.core.golem.GolemComponentRecipes.Recipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import static dev.ehtid.thaumcraft.port.core.golem.GolemComponentRecipes.Ingredient.*;
import static dev.ehtid.thaumcraft.port.core.golem.GolemComponentRecipes.bit;

/** Lane-local adapter; uses the existing persisted workbench, aura and six primal crystal slots. */
public final class GolemComponentCrafting {
    private GolemComponentCrafting() {}
    private static TagKey<Item> tag(String id) { return TagKey.create(Registries.ITEM, ResourceLocation.parse(id)); }
    private static final TagKey<Item> BRASS = tag("c:plates/brass"), IRON = tag("c:plates/iron");
    private static final TagKey<Item> OLD_BRASS = tag("forge:plates/brass"), OLD_IRON = tag("forge:plates/iron");
    private static final TagKey<Item> RODS = tag("thaumcraft:golem_bell_sticks");
    private static final TagKey<Item> PANES = tag("c:glass_panes"), VANILLA_PANES = tag("minecraft:glass_panes");

    public static Recipe recipe(Container bench) {
        if (bench.getContainerSize() < 9) return null;
        int[] masks = new int[9];
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = bench.getItem(slot);
            if (stack.isEmpty()) { masks[slot] = bit(EMPTY); continue; }
            int metadata = PlateItem.legacyMetadata(stack);
            if (metadata == 0 || stack.is(BRASS) || stack.is(OLD_BRASS)) masks[slot] |= bit(BRASS_PLATE);
            if (metadata == 1 || stack.is(IRON) || stack.is(OLD_IRON)) masks[slot] |= bit(IRON_PLATE);
            if (stack.is(Items.STICK) || stack.is(RODS)) masks[slot] |= bit(WOODEN_ROD);
            if (stack.is(Items.GLASS_PANE) || stack.is(PANES) || stack.is(VANILLA_PANES)) masks[slot] |= bit(GLASS_PANE);
            if (stack.is(GolemModule.SIMPLE_MECHANISM.get())) masks[slot] |= bit(SIMPLE_MECHANISM);
            if (stack.is(Items.COMPARATOR)) masks[slot] |= bit(COMPARATOR);
            if (stack.is(Items.GLASS_BOTTLE)) masks[slot] |= bit(GLASS_BOTTLE);
            if (stack.is(Items.FERMENTED_SPIDER_EYE)) masks[slot] |= bit(FERMENTED_SPIDER_EYE);
            if (stack.is(Items.BLAZE_POWDER)) masks[slot] |= bit(BLAZE_POWDER);
        }
        for (Recipe recipe : Recipe.values()) if (recipe.matches(masks)) return recipe;
        return null;
    }

    public static ItemStack product(Recipe recipe) {
        return recipe == null ? ItemStack.EMPTY : new ItemStack(switch (recipe) {
            case SIMPLE_MECHANISM -> GolemModule.SIMPLE_MECHANISM.get();
            case CLOCKWORK_MIND -> GolemModule.CLOCKWORK_MIND.get();
            case VISION_MODULE -> GolemModule.VISION_MODULE.get();
            case AGGRESSION_MODULE -> GolemModule.AGGRESSION_MODULE.get();
        });
    }

    public static boolean isProduct(ItemStack stack) {
        return stack.is(GolemModule.SIMPLE_MECHANISM.get()) || stack.is(GolemModule.CLOCKWORK_MIND.get())
                || stack.is(GolemModule.VISION_MODULE.get()) || stack.is(GolemModule.AGGRESSION_MODULE.get());
    }

    public static boolean canCraft(Container bench, ServerPlayer player, float discount) {
        if (!(bench instanceof ArcaneWorkbenchBlockEntity wb) || player.level() != wb.getLevel()
                || !player.isAlive() || player.isSpectator() || !wb.stillValid(player)
                || !Float.isFinite(discount) || discount < 0 || discount > 1) return false;
        Recipe recipe = recipe(bench);
        if (recipe == null || !player.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge().isResearchKnown(recipe.research)) return false;
        for (int primal = 0; primal < 6; primal++) {
            if ((recipe.crystalMask & 1 << primal) == 0) continue;
            int slot = 9 + primal;
            if (bench.getItem(slot).isEmpty() || !wb.canPlaceItem(slot, bench.getItem(slot))) return false;
        }
        return wb.getVis() >= recipe.discountedVis(discount);
    }

    public static ItemStack preview(Container bench, ServerPlayer player, float discount) {
        return canCraft(bench, player, discount) ? product(recipe(bench)) : ItemStack.EMPTY;
    }

    public static ItemStack craft(Container bench, ServerPlayer player, ItemStack expected, float discount) {
        if (!canCraft(bench, player, discount)) return ItemStack.EMPTY;
        Recipe recipe = recipe(bench);
        ItemStack product = product(recipe);
        if (!ItemStack.isSameItemSameComponents(product, expected)) return ItemStack.EMPTY;
        var wb = (ArcaneWorkbenchBlockEntity) bench;
        if (!wb.consumeVis(recipe.discountedVis(discount))) return ItemStack.EMPTY;
        for (int slot = 0; slot < 9; slot++) if (recipe.pattern().get(slot) != EMPTY) bench.getItem(slot).shrink(1);
        for (int primal = 0; primal < 6; primal++) if ((recipe.crystalMask & 1 << primal) != 0) bench.getItem(9 + primal).shrink(1);
        wb.setChanged();
        return product;
    }
}
