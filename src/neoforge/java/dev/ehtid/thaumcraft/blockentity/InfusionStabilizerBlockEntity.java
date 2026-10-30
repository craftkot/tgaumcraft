package dev.ehtid.thaumcraft.blockentity;

import dev.ehtid.thaumcraft.infusion.InfusionStabilityBlocks;
import dev.ehtid.thaumcraft.port.core.LegacyInfusionMitigation;
import dev.ehtid.thaumcraft.world.ThaumcraftAuraSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * BETA26 TileStabilizer's infusion energy store. Recharge costs real aura
 * pollution; mitigation debits this saved device, never a virtual allowance.
 * Flux-rift stabilization belongs to that separate entity's future runtime.
 */
public final class InfusionStabilizerBlockEntity extends BlockEntity {
    private int energy;
    private int ticks;

    public InfusionStabilizerBlockEntity(BlockPos pos, BlockState state) {
        super(InfusionStabilityBlocks.STABILIZER_ENTITY.get(), pos, state);
    }

    public int energy() { return energy; }

    public boolean mitigate(int cost) {
        if (level == null || level.isClientSide || cost <= 0 || energy < cost) return false;
        energy = LegacyInfusionMitigation.afterMitigation(energy, cost);
        changedAndSync();
        return true;
    }

    public static void serverTick(ServerLevel level, BlockPos pos, BlockState state,
                                  InfusionStabilizerBlockEntity tile) {
        var recharge = LegacyInfusionMitigation.recharge(tile.energy, ++tile.ticks);
        if (recharge.energy() == tile.energy) return;
        tile.energy = recharge.energy();
        ThaumcraftAuraSavedData.get(level).polluteFlux(pos, recharge.pollution());
        tile.changedAndSync();
    }

    private void changedAndSync() {
        setChanged();
        if (level == null || level.isClientSide) return;
        BlockState state = getBlockState();
        level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        level.updateNeighborsAt(worldPosition, state.getBlock());
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("energy", energy);
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy = Math.max(0, Math.min(LegacyInfusionMitigation.CAPACITY, tag.getInt("energy")));
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
