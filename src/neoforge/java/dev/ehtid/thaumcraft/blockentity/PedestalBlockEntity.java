package dev.ehtid.thaumcraft.blockentity;

import dev.ehtid.thaumcraft.registry.ThaumcraftBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * BETA26 TilePedestal: exactly one synced inventory slot with max stack
 * size 1. Inventory saves to normal Items NBT and is sent on block updates.
 * The infusion matrix may later use putFromInfusion during an actual ritual.
 */
public final class PedestalBlockEntity extends BlockEntity implements Container {
    private final NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);

    public PedestalBlockEntity(BlockPos pos, BlockState state) {
        super(ThaumcraftBlockEntities.PEDESTAL.get(), pos, state);
    }

    public ItemStack displayedItem() { return items.get(0); }
    public boolean isOccupied() { return !items.get(0).isEmpty(); }

    /** Exact BETA26 item-insertion: split 1 from the player's real hand. */
    public boolean insertFromPlayer(ItemStack held) {
        if (level == null || level.isClientSide || isOccupied() || held.isEmpty()) return false;
        setItem(0, held.split(1));
        return true;
    }

    /** Keep the original item components/metadata, no synthetic copy or reward. */
    public ItemStack extractToPlayer() {
        if (level == null || level.isClientSide || !isOccupied()) return ItemStack.EMPTY;
        return removeItemNoUpdate(0);
    }

    /** Infusion writes are server authoritative; caller owns cost validation. */
    public void putFromInfusion(ItemStack ingredient) {
        if (level == null || level.isClientSide)
            throw new IllegalStateException("Pedestal infusion writes must be server-side");
        setItem(0, ingredient);
    }

    private void changedAndSync() {
        setChanged();
        if (level != null && !level.isClientSide)
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override public int getContainerSize() { return 1; }
    @Override public boolean isEmpty() { return !isOccupied(); }
    @Override public ItemStack getItem(int slot) {
        return slot == 0 ? items.get(0) : ItemStack.EMPTY;
    }
    @Override public ItemStack removeItem(int slot, int amount) {
        if (slot != 0 || amount <= 0) return ItemStack.EMPTY;
        ItemStack result = ContainerHelper.removeItem(items, slot, amount);
        if (!result.isEmpty()) changedAndSync();
        return result;
    }
    @Override public ItemStack removeItemNoUpdate(int slot) {
        if (slot != 0) return ItemStack.EMPTY;
        ItemStack result = ContainerHelper.takeItem(items, slot);
        if (!result.isEmpty()) changedAndSync();
        return result;
    }
    @Override public void setItem(int slot, ItemStack stack) {
        if (slot != 0) return;
        ItemStack one = stack.copy();
        if (!one.isEmpty()) one.setCount(1);
        items.set(0, one);
        changedAndSync();
    }
    @Override public int getMaxStackSize() { return 1; }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == 0 && (stack.isEmpty() || !isOccupied());
    }
    @Override public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX() + 0.5,
                        worldPosition.getY() + 0.5,
                        worldPosition.getZ() + 0.5) <= 64.0;
    }
    @Override public void clearContent() {
        if (isOccupied()) {
            items.set(0, ItemStack.EMPTY);
            changedAndSync();
        }
    }
    @Override public void setChanged() {
        super.setChanged();
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
        ContainerHelper.saveAllItems(tag, items, registries);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.set(0, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);
        // Malformed old-world ItemStacks cannot create multi-stack pedestals.
        if (!items.get(0).isEmpty() && items.get(0).getCount() > 1)
            items.get(0).setCount(1);
    }
}
