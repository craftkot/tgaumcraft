package dev.ehtid.thaumcraft.blockentity;

import dev.ehtid.thaumcraft.port.core.LegacyCrucibleHeat;
import dev.ehtid.thaumcraft.registry.ThaumcraftBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Authentic starting TileCrucible lifecycle, NOT yet an alchemy processor.
 *
 * The original tile has a single 1000 mB water tank, heat 0..200 and
 * persists AspectList. This slice provides the real heated tank and save
 * data without pretending unknown original crucible recipes are active.
 */
public final class CrucibleBlockEntity extends BlockEntity {
    private int waterMb;
    private int heat;
    // Placeholder for a future exact AspectList persistence bridge: do not
    // invent synthetic essentia rewards or throw away unknown old NBT.
    private CompoundTag savedOriginalAspects = new CompoundTag();

    public CrucibleBlockEntity(BlockPos pos, BlockState state) {
        super(ThaumcraftBlockEntities.CRUCIBLE.get(), pos, state);
    }

    public int waterMb() { return waterMb; }
    public int heat() { return heat; }
    public boolean boiling() { return LegacyCrucibleHeat.boiling(heat, waterMb); }

    /** Accept exactly one water bucket if the tank is empty. */
    public boolean fillBucket() {
        if (level == null || level.isClientSide || waterMb != 0) return false;
        waterMb = LegacyCrucibleHeat.CAPACITY_MB;
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        return true;
    }

    /** Preserves the old water/heat when a chunk unloads and reloads. */
    public static void serverTick(ServerLevel level, BlockPos pos, BlockState state,
                                  CrucibleBlockEntity tile) {
        BlockState below = level.getBlockState(pos.below());
        // Original heat sources: vanilla material FIRE or LAVA, nitor and
        // magma block. Nitor is not yet ported; never treat unrelated glow
        // blocks as a substitute heater.
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
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putShort("Heat", (short) heat);
        tag.putInt("Water", waterMb);
        tag.put("OriginalAspects", savedOriginalAspects.copy());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        heat = Math.max(0, Math.min(LegacyCrucibleHeat.MAX_HEAT, tag.getShort("Heat")));
        waterMb = Math.max(0, Math.min(LegacyCrucibleHeat.CAPACITY_MB, tag.getInt("Water")));
        savedOriginalAspects = tag.contains("OriginalAspects", net.minecraft.nbt.Tag.TAG_COMPOUND)
                ? tag.getCompound("OriginalAspects").copy() : new CompoundTag();
    }
}
