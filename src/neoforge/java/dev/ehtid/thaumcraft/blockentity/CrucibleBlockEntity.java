package dev.ehtid.thaumcraft.blockentity;

import dev.ehtid.thaumcraft.port.core.AspectData;
import dev.ehtid.thaumcraft.port.core.AspectListData;
import dev.ehtid.thaumcraft.port.core.AspectRegistry;
import dev.ehtid.thaumcraft.port.core.LegacyCrucibleDissolution;
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

    /**
     * BETA26 TileCrucible.attemptSmelt's no-recipe fallback: merge the
     * BETA26 object tags into the stored AspectList, consuming exactly
     * maxItems items. There is no recipe-reward shortcut in this slice.
     *
     * Return the number actually consumed so both direct interaction and
     * ItemEntity collision update their own inventory/stack atomically.
     */
    public int dissolve(ItemStack stack, int maxItems) {
        if (level == null || level.isClientSide || stack.isEmpty() || maxItems <= 0)
            return 0;
        AspectListData objectTags = ThaumometerScanDispatcher.aspectsForCrucibleItem(stack);
        if (!LegacyCrucibleDissolution.canDissolve(heat, waterMb, objectTags))
            return 0;
        int count = Math.min(stack.getCount(), maxItems);
        if (count <= 0) return 0;
        aspects = LegacyCrucibleDissolution.dissolve(aspects, objectTags, count);
        // The original only drains 50 mB when a CATALYST produces an item;
        // simple aspect dissolution does not consume water.
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        return count;
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
