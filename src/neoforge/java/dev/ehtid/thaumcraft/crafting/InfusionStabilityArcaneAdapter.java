package dev.ehtid.thaumcraft.crafting;

import dev.ehtid.thaumcraft.blockentity.ArcaneWorkbenchBlockEntity;
import dev.ehtid.thaumcraft.port.core.LegacyInfusionStabilityRecipes;
import dev.ehtid.thaumcraft.port.core.LegacyInfusionStabilityRecipes.Kind;
import dev.ehtid.thaumcraft.port.core.PlayerKnowledgeData;
import dev.ehtid.thaumcraft.registry.ThaumcraftItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Paid source arcane acquisition. Missing original device ingredients never receive substitutes. */
public final class InfusionStabilityArcaneAdapter {
    private InfusionStabilityArcaneAdapter() {}

    public static Kind selected(ArcaneWorkbenchBlockEntity bench) {
        for (Kind kind : Kind.values())
            if (LegacyInfusionStabilityRecipes.matches(kind,
                    (slot, token) -> ingredient(kind, token, bench.getItem(slot)), slot -> bench.getItem(slot).isEmpty()))
                return kind;
        return null;
    }

    public static int displayedVisCost(ArcaneWorkbenchBlockEntity bench) {
        Kind kind = selected(bench);
        return kind == null ? 0 : kind.vis;
    }

    public static int crystalMask(ArcaneWorkbenchBlockEntity bench) {
        Kind kind = selected(bench);
        return kind == null ? 0 : kind.crystalMask;
    }

    public static ItemStack output(Kind kind) {
        if (kind == null) return ItemStack.EMPTY;
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("thaumcraft", kind.outputPath);
        return BuiltInRegistries.ITEM.containsKey(id)
                ? new ItemStack(BuiltInRegistries.ITEM.get(id), kind.outputCount) : ItemStack.EMPTY;
    }

    public static boolean canCraft(ArcaneWorkbenchBlockEntity bench, PlayerKnowledgeData knowledge, float discount) {
        Kind kind = selected(bench);
        if (kind == null || knowledge == null || !knowledge.isResearchKnown(kind.research)
                || output(kind).isEmpty() || bench.getVis() < LegacyInfusionStabilityRecipes.discountedVis(kind, discount))
            return false;
        for (int primal = 0; primal < 6; primal++) if ((kind.crystalMask & 1 << primal) != 0) {
            ItemStack crystal = bench.getItem(9 + primal);
            if (crystal.isEmpty() || !bench.canPlaceItem(9 + primal, crystal)) return false;
        }
        return true;
    }

    public static ItemStack preview(ArcaneWorkbenchBlockEntity bench, PlayerKnowledgeData knowledge, float discount) {
        return canCraft(bench, knowledge, discount) ? output(selected(bench)) : ItemStack.EMPTY;
    }

    /** Validate every resource and exact preview before the first physical debit. */
    public static ItemStack craft(ArcaneWorkbenchBlockEntity bench, PlayerKnowledgeData knowledge,
                                  ItemStack expected, float discount) {
        if (bench.getLevel() == null || bench.getLevel().isClientSide || !canCraft(bench, knowledge, discount))
            return ItemStack.EMPTY;
        Kind kind = selected(bench);
        ItemStack output = output(kind);
        if (expected == null || !ItemStack.isSameItemSameComponents(expected, output)
                || expected.getCount() != output.getCount()) return ItemStack.EMPTY;
        if (!bench.consumeVis(LegacyInfusionStabilityRecipes.discountedVis(kind, discount))) return ItemStack.EMPTY;
        for (int slot = 0; slot < 9; slot++) if (!bench.getItem(slot).isEmpty()) bench.removeItem(slot, 1);
        for (int primal = 0; primal < 6; primal++) if ((kind.crystalMask & 1 << primal) != 0)
            bench.removeItem(9 + primal, 1);
        bench.setChanged();
        return output;
    }

    private static boolean ingredient(Kind kind, char token, ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (kind == Kind.INLAY) return switch (token) {
            case 'R' -> stack.is(Items.REDSTONE) || tag(stack, "c:dusts/redstone");
            case 'G' -> stack.is(Items.GOLD_INGOT) || tag(stack, "c:ingots/gold");
            default -> false;
        };
        if (kind == Kind.MIRRORED_GLASS) return switch (token) {
            case 'R' -> stack.is(ThaumcraftItems.QUICKSILVER.get());
            case 'G' -> stack.is(Items.GLASS_PANE) || tag(stack, "minecraft:glass_panes") || tag(stack, "c:glass_panes");
            default -> false;
        };
        if (kind == Kind.STABILIZER) return switch (token) {
            case 'S' -> registered(stack, "thaumcraft:slab_arcane_stone");
            case 'R' -> stack.is(Items.REDSTONE_BLOCK) || tag(stack, "c:storage_blocks/redstone");
            case 'B' -> stack.is(ThaumcraftItems.STONE_ARCANE.get());
            case 'V' -> stack.is(ThaumcraftItems.VIS_RESONATOR.get());
            case 'I' -> registered(stack, "thaumcraft:inlay");
            case 'M' -> registered(stack, "thaumcraft:mechanism_complex");
            default -> false;
        };
        return switch (token) {
            case 'S' -> stack.is(ThaumcraftItems.STONE_ARCANE.get());
            case 'N' -> ThaumcraftItems.NITOR.values().stream().anyMatch(item -> stack.is(item.get()))
                    || tag(stack, "thaumcraft:nitor") || tag(stack, "c:nitor");
            case 'A' -> stack.is(ThaumcraftItems.ALUMENTUM.get());
            case 'G' -> stack.is(Items.DIAMOND_BLOCK);
            default -> false;
        };
    }

    private static boolean registered(ItemStack stack, String id) {
        ResourceLocation location = ResourceLocation.parse(id);
        return BuiltInRegistries.ITEM.containsKey(location) && stack.is(BuiltInRegistries.ITEM.get(location));
    }

    private static boolean tag(ItemStack stack, String id) {
        return stack.is(TagKey.<Item>create(Registries.ITEM, ResourceLocation.parse(id)));
    }
}
