package dev.ehtid.thaumcraft.blockentity;

import dev.ehtid.thaumcraft.port.core.AspectData;
import dev.ehtid.thaumcraft.port.core.AspectListData;
import dev.ehtid.thaumcraft.port.core.AspectRegistry;
import dev.ehtid.thaumcraft.port.core.LegacyCrucibleDissolution;
import dev.ehtid.thaumcraft.port.core.LegacyCrucibleCatalystLogic;
import dev.ehtid.thaumcraft.item.ThaumcraftIngotItem;
import dev.ehtid.thaumcraft.item.ThaumcraftNuggetItem;
import dev.ehtid.thaumcraft.item.CrystalEssenceItem;
import dev.ehtid.thaumcraft.port.core.LegacyVisCrystalCrucibleLogic;
import dev.ehtid.thaumcraft.player.ThaumcraftPlayerAttachments;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;
import dev.ehtid.thaumcraft.port.core.LegacyCrucibleHeat;
import dev.ehtid.thaumcraft.registry.ThaumcraftBlockEntities;
import dev.ehtid.thaumcraft.scanning.ThaumometerScanDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Original BETA26 Crucible water/heat and non-recipe item-to-essentia path.
 *
 * Recipe catalysts and pollution/aura effects require subsequent dedicated
 * ports. Unknown old aspect NBT is preserved, never silently discarded.
 */
public final class CrucibleBlockEntity extends BlockEntity {
    private static final AspectRegistry ASPECTS = new AspectRegistry();
    private static final java.util.List<LegacyCrucibleCatalystLogic.Recipe> METALLURGY =
            LegacyCrucibleCatalystLogic.originalIronRecipes(ASPECTS);
    private static final TagKey<Item> IRON_INGOT_C = TagKey.create(
            Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "ingots/iron"));
    private static final TagKey<Item> IRON_INGOT_FORGE = TagKey.create(
            Registries.ITEM, ResourceLocation.fromNamespaceAndPath("forge", "ingots/iron"));
    private int waterMb;
    private int heat;
    private AspectListData aspects = new AspectListData();
    // Unknown aspect tags may have come from add-ons in a legacy world.
    // Do not delete their original NBT when a known object is dissolved.
    private ListTag originalUnknownAspects = new ListTag();
    private CompoundTag savedOriginalAspects = new CompoundTag();

    public CrucibleBlockEntity(BlockPos pos, BlockState state) {
        super(ThaumcraftBlockEntities.CRUCIBLE.get(), pos, state);
    }

    public int waterMb() { return waterMb; }
    public int heat() { return heat; }
    public boolean boiling() { return LegacyCrucibleHeat.boiling(heat, waterMb); }
    public AspectListData getAspects() { return aspects.copy(); }

    /** BETA26 FluidTank receives up to 1000 mB from a water bucket. */
    public boolean fillBucket() {
        if (level == null || level.isClientSide || waterMb != 0) return false;
        waterMb = LegacyCrucibleHeat.CAPACITY_MB;
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        return true;
    }

    /** Original null-player dropped catalysts do not match research recipes. */
    public int dissolve(ItemStack stack, int maxItems) {
        return attemptSmelt(stack, maxItems, null);
    }

    /**
     * BETA26 TileCrucible.attemptSmelt: player research and catalyst
     * recipes take priority over ordinary item-to-aspect dissolution.
     * One input is processed per iteration; leftover stack remains owned
     * by the caller until its actual consumed-count is returned.
     */
    public int attemptSmelt(ItemStack stack, int maxItems, ServerPlayer player) {
        if (!(level instanceof ServerLevel server) || stack.isEmpty()
                || maxItems <= 0 || !boiling()) return 0;
        int remaining = Math.min(stack.getCount(), maxItems);
        AspectListData tags = ThaumometerScanDispatcher.aspectsForCrucibleItem(stack);
        boolean iron = stack.is(Items.IRON_INGOT)
                || stack.is(IRON_INGOT_C) || stack.is(IRON_INGOT_FORGE);
        // BETA26 OreDictionary nuggetQuartz is original metadata 9,
        // NOT a vanilla quartz item or any other nugget subtype.
        boolean quartzSliver = ThaumcraftNuggetItem.legacyMetadata(stack)
                == LegacyVisCrystalCrucibleLogic.QUARTZ_NUGGET_META;
        var knowledge = player == null ? null
                : player.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge();
        int consumed = 0;
        while (consumed < remaining) {
            var recipe = iron && waterMb > 0
                    ? LegacyCrucibleCatalystLogic.bestIronRecipe(knowledge, aspects, METALLURGY)
                    : null;
            var crystal = quartzSliver && waterMb > 0
                    ? LegacyVisCrystalCrucibleLogic.firstEligibleAspect(knowledge, aspects, ASPECTS)
                    : null;
            ItemStack product = ItemStack.EMPTY;
            if (recipe != null) {
                aspects = LegacyCrucibleCatalystLogic.afterCraft(aspects, recipe);
                waterMb = LegacyCrucibleCatalystLogic.waterAfterCraft(waterMb);
                product = ThaumcraftIngotItem.create(recipe.outputMeta());
            } else if (crystal != null) {
                // 37 original thaumcraft:vis_crystal_<aspect> recipes.
                // The output NBT must contain the matched aspect; it
                // must never be randomized by CrystalEssenceItem's tick.
                aspects = LegacyVisCrystalCrucibleLogic.afterCraft(aspects, crystal);
                waterMb = LegacyCrucibleCatalystLogic.waterAfterCraft(waterMb);
                product = CrystalEssenceItem.ofAspect(crystal.tag());
            } else if (LegacyCrucibleDissolution.canDissolve(heat, waterMb, tags)) {
                aspects = LegacyCrucibleDissolution.dissolve(aspects, tags, 1);
            } else break;
            if (!product.isEmpty()) {
                ItemEntity ejected = new ItemEntity(server,
                        worldPosition.getX() + 0.5D,
                        worldPosition.getY() + 1.13D,
                        worldPosition.getZ() + 0.5D, product);
                ejected.setDeltaMovement(new Vec3(0.12D, 0.22D, 0.12D));
                server.addFreshEntity(ejected);
            }
            consumed++;
        }
        if (consumed != 0) {
            setChanged();
            server.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        return consumed;
    }

    public static void serverTick(ServerLevel level, BlockPos pos, BlockState state,
                                  CrucibleBlockEntity tile) {
        BlockState below = level.getBlockState(pos.below());
        // The BETA26 heat source set: fire, lava, nitor, magma. Original
        // nitor blocks are not available yet, so do not fake them.
        boolean heater = below.is(Blocks.FIRE) || below.is(Blocks.SOUL_FIRE)
                || below.is(Blocks.LAVA) || below.is(Blocks.MAGMA_BLOCK);
        int next = LegacyCrucibleHeat.next(tile.heat, tile.waterMb, heater);
        if (next != tile.heat) {
            boolean crossedBoiling = LegacyCrucibleHeat.boiling(next, tile.waterMb)
                    != LegacyCrucibleHeat.boiling(tile.heat, tile.waterMb);
            tile.heat = next;
            tile.setChanged();
            if (crossedBoiling) level.sendBlockUpdated(pos, state, state, 3);
        }
        // The original also spills one random aspect periodically (and
        // when exceeding 500) into Aura pollution. Defer this until the
        // real aura/flux bridge can account for the pollution. Never
        // silently destroy players' collected essentia.
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putShort("Heat", (short) heat);
        tag.putInt("Water", waterMb);
        // Mirror original AspectList.writeToNBT: a compound list under
        // Aspects, each with "key" and "amount", not a synthetic map.
        ListTag list = new ListTag();
        for (AspectData aspect : aspects.getAspects()) {
            CompoundTag one = new CompoundTag();
            one.putString("key", aspect.tag());
            one.putInt("amount", aspects.getAmount(aspect));
            list.add(one);
        }
        for (int i = 0; i < originalUnknownAspects.size(); i++)
            list.add(originalUnknownAspects.getCompound(i).copy());
        tag.put("Aspects", list);
        if (!savedOriginalAspects.isEmpty())
            tag.put("OriginalAspects", savedOriginalAspects.copy());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        heat = Math.max(0, Math.min(LegacyCrucibleHeat.MAX_HEAT, tag.getShort("Heat")));
        waterMb = Math.max(0, Math.min(LegacyCrucibleHeat.CAPACITY_MB, tag.getInt("Water")));
        savedOriginalAspects = tag.contains("OriginalAspects", Tag.TAG_COMPOUND)
                ? tag.getCompound("OriginalAspects").copy() : new CompoundTag();
        aspects = new AspectListData();
        originalUnknownAspects = new ListTag();
        ListTag original = tag.contains("Aspects", Tag.TAG_LIST)
                ? tag.getList("Aspects", Tag.TAG_COMPOUND)
                : savedOriginalAspects.getList("Aspects", Tag.TAG_COMPOUND);
        for (int i = 0; i < original.size(); i++) {
            CompoundTag entry = original.getCompound(i);
            if (!entry.contains("key", Tag.TAG_STRING)
                    || !entry.contains("amount", Tag.TAG_INT)) {
                originalUnknownAspects.add(entry.copy());
                continue;
            }
            AspectData aspect = ASPECTS.get(entry.getString("key"));
            int amount = entry.getInt("amount");
            if (aspect == null || amount < 0) {
                originalUnknownAspects.add(entry.copy());
                continue;
            }
            aspects.add(aspect, amount);
        }
    }
}
