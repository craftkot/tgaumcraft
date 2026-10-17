package dev.ehtid.thaumcraft.blockentity;

import dev.ehtid.thaumcraft.port.core.LegacyEssentiaJarLogic;
import dev.ehtid.thaumcraft.port.core.LegacyPhialJarExchange;
import dev.ehtid.thaumcraft.registry.ThaumcraftBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Persistent server-side BETA26 jar. No passive essentia generation. */
public final class EssentiaJarBlockEntity extends BlockEntity {
    private LegacyEssentiaJarLogic.State stored = LegacyEssentiaJarLogic.State.empty();
    private int facing = 2;
    private boolean blocked;

    public EssentiaJarBlockEntity(BlockPos pos, BlockState state) {
        super(ThaumcraftBlockEntities.JAR_NORMAL.get(), pos, state);
    }
    public LegacyEssentiaJarLogic.State snapshot() { return stored; }
    public int facing() { return facing; }
    public boolean blocked() { return blocked; }
    /** True if the jar item must retain non-default BlockEntity state. */
    public boolean hasStoredData() {
        return stored.amount() > 0 || stored.filter() != null || blocked || facing != 2;
    }
    public boolean isConnectable(Direction side) { return side == Direction.UP; }
    public int suction(Direction side) {
        return isConnectable(side) ? LegacyEssentiaJarLogic.suction(stored) : 0;
    }
    public String suctionAspect(Direction side) {
        return isConnectable(side) ? LegacyEssentiaJarLogic.suctionAspect(stored) : null;
    }
    /** Caller must debit the returned amount from its actual source. */
    public int addFromTransport(String aspect, int offered, Direction side) {
        if (level == null || level.isClientSide || !isConnectable(side)
                || !LegacyEssentiaJarLogic.accepts(stored, aspect)) return 0;
        var transfer = LegacyEssentiaJarLogic.add(stored, aspect, offered);
        if (transfer.transferred() > 0) updateStored(transfer.state());
        return transfer.transferred();
    }
    /** Source jar debit: one complete 10-unit phial, no partial withdrawal. */
    public boolean drawIntoPhial(String aspect, int amount) {
        if (level == null || level.isClientSide) return false;
        var transfer = LegacyPhialJarExchange.withdraw(stored, aspect, amount);
        if (transfer.transferred() != amount) return false;
        updateStored(transfer.state());
        return true;
    }
    /** Preflight for the item-side manual transfer; no jar mutation. */
    public boolean canReceivePhial(String aspect, int amount) {
        return LegacyPhialJarExchange.deposit(stored, aspect, amount)
                .transferred() == amount;
    }
    /** Server-only all-or-nothing deposit; transport UP rule is irrelevant to a held phial. */
    public boolean receivePhial(String aspect, int amount) {
        if (level == null || level.isClientSide) return false;
        var transfer = LegacyPhialJarExchange.deposit(stored, aspect, amount);
        if (transfer.transferred() != amount) return false;
        updateStored(transfer.state());
        return true;
    }
    /** No partial debit. Caller must have verified recipe and world state. */
    public boolean takeForInfusion(String aspect, int amount) {
        if (level == null || level.isClientSide || amount <= 0) return false;
        var transfer = LegacyEssentiaJarLogic.take(stored, aspect, amount);
        if (transfer.transferred() != amount) return false;
        updateStored(transfer.state());
        return true;
    }
    public int takeForTransport(String aspect, int amount, Direction side) {
        return isConnectable(side) && takeForInfusion(aspect, amount) ? amount : 0;
    }
    private void updateStored(LegacyEssentiaJarLogic.State next) {
        stored = next;
        setChanged();
        if (level != null && !level.isClientSide)
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (stored.aspect() != null) tag.putString("Aspect", stored.aspect());
        if (stored.filter() != null) tag.putString("AspectFilter", stored.filter());
        tag.putShort("Amount", (short) stored.amount());
        tag.putByte("facing", (byte) facing);
        tag.putBoolean("blocked", blocked);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        String aspect = tag.contains("Aspect", Tag.TAG_STRING) ? tag.getString("Aspect") : null;
        String filter = tag.contains("AspectFilter", Tag.TAG_STRING)
                ? tag.getString("AspectFilter") : null;
        int amount = tag.contains("Amount", Tag.TAG_SHORT)
                ? tag.getShort("Amount") : tag.getInt("Amount");
        amount = Math.max(0, Math.min(LegacyEssentiaJarLogic.CAPACITY, amount));
        if (aspect == null || aspect.isBlank()) amount = 0;
        stored = new LegacyEssentiaJarLogic.State(aspect, filter, amount);
        int oldFacing = tag.getByte("facing");
        facing = oldFacing >= 0 && oldFacing <= 5 ? oldFacing : 2;
        blocked = tag.getBoolean("blocked");
    }
}
