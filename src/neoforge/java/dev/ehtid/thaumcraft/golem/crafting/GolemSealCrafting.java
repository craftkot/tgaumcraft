package dev.ehtid.thaumcraft.golem.crafting;

import dev.ehtid.thaumcraft.blockentity.ArcaneWorkbenchBlockEntity;
import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.port.core.*;
import dev.ehtid.thaumcraft.port.core.golem.GolemSealRecipes;
import dev.ehtid.thaumcraft.registry.ThaumcraftItems;
import dev.ehtid.thaumcraft.player.ThaumcraftPlayerAttachments;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.ArrayList;

/** Lane-local adapters; existing workbench/menu and crucible keep their transactions. */
public final class GolemSealCrafting {
    private GolemSealCrafting() {}
    private static final TagKey<Item> RED_DYE = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "dyes/red"));
    private static final TagKey<Item> NITOR = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("thaumcraft", "nitor"));

    public static boolean matchesBlank(Container bench) {
        var grid = new ArrayList<GolemSealRecipes.Ingredient>(9);
        for (int i = 0; i < 9; i++) {
            ItemStack stack = bench.getItem(i);
            grid.add(stack.isEmpty() ? GolemSealRecipes.Ingredient.EMPTY
                    : stack.is(Items.CLAY_BALL) ? GolemSealRecipes.Ingredient.CLAY
                    : stack.is(ThaumcraftItems.TALLOW.get()) ? GolemSealRecipes.Ingredient.TALLOW
                    : stack.is(Items.RED_DYE) || stack.is(RED_DYE) ? GolemSealRecipes.Ingredient.RED_DYE
                    : stack.is(NITOR) ? GolemSealRecipes.Ingredient.NITOR : GolemSealRecipes.Ingredient.OTHER);
        }
        return GolemSealRecipes.matchesBlank(grid);
    }

    public static boolean canCraftBlank(Container bench, ServerPlayer player, float discount) {
        if (!(bench instanceof ArcaneWorkbenchBlockEntity wb) || player.level().isClientSide
                || !Float.isFinite(discount) || discount < 0 || discount > 1) return false;
        return wb.stillValid(player) && matchesBlank(bench) && player.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge()
                .isResearchKnown(GolemSealRecipes.BLANK_RESEARCH)
                && !bench.getItem(9).isEmpty() && wb.canPlaceItem(9, bench.getItem(9))
                && wb.getVis() >= (int) (GolemSealRecipes.VIS * (1F - discount));
    }

    public static boolean craftBlank(Container bench, ServerPlayer player, float discount) {
        if (!canCraftBlank(bench, player, discount)) return false;
        var wb = (ArcaneWorkbenchBlockEntity) bench;
        int cost = (int) (GolemSealRecipes.VIS * (1F - discount));
        if (!wb.consumeVis(cost)) return false;
        for (int i = 0; i < 9; i++) if (!bench.getItem(i).isEmpty()) bench.getItem(i).shrink(1);
        bench.getItem(9).shrink(1);
        wb.setChanged();
        return true;
    }

    public static GolemSealRecipes.Conversion conversion(ItemStack stack, PlayerKnowledgeData knowledge,
                                                          AspectListData stored, AspectRegistry registry) {
        var catalyst=stack.is(GolemModule.BLANK_SEAL.get())?GolemSealRecipes.Catalyst.BLANK
                :stack.is(GolemModule.PICKUP_SEAL.get())?GolemSealRecipes.Catalyst.PICKUP
                :stack.is(GolemModule.EMPTY_SEAL.get())?GolemSealRecipes.Catalyst.EMPTY
                :stack.is(GolemModule.FILL_SEAL.get())?GolemSealRecipes.Catalyst.FILL
                :stack.is(GolemModule.EMPTY_ADVANCED_SEAL.get())?GolemSealRecipes.Catalyst.EMPTY_ADVANCED:null;
        return catalyst==null?null:GolemSealRecipes.conversion(catalyst,knowledge,stored,registry);
    }

    public static ItemStack product(GolemSealRecipes.Conversion recipe) {
        return new ItemStack(switch (recipe) {
            case PICKUP -> GolemModule.PICKUP_SEAL.get(); case FILL -> GolemModule.FILL_SEAL.get();
            case EMPTY -> GolemModule.EMPTY_SEAL.get();
            case EMPTY_ADVANCED -> GolemModule.EMPTY_ADVANCED_SEAL.get(); case PROVIDE -> GolemModule.PROVIDE_SEAL.get();
            case STOCK -> GolemModule.STOCK_SEAL.get();
            case PICKUP_ADVANCED -> GolemModule.PICKUP_ADVANCED_SEAL.get(); case FILL_ADVANCED -> GolemModule.FILL_ADVANCED_SEAL.get();
        });
    }
}
