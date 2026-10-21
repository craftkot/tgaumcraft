package dev.ehtid.thaumcraft.blockentity;

import dev.ehtid.thaumcraft.registry.ThaumcraftBlockEntities;
import dev.ehtid.thaumcraft.port.core.LegacyCrystalFarmerPedestalReceipt;
import dev.ehtid.thaumcraft.port.core.LegacyCrystalFarmerOutputReceipt;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import java.util.UUID;
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
    /** Receipt is saved atomically WITH the emptied pedestal slot. */
    private LegacyCrystalFarmerPedestalReceipt infusionRemovalReceipt;
    /** Saved in the SAME center pedestal NBT as the one published output. */
    private LegacyCrystalFarmerOutputReceipt infusionOutputReceipt;

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

    /**
     * Atomic server-only final step for a VERIFIED original infusion cycle.
     * The caller must have already obtained the recipe/catalog match and
     * debited its legitimate costs. A stale plan never removes a different
     * item and cannot duplicate a result when invoked a second time.
     *
     * The complete ItemStack (item ID + data components + count) must still
     * be identical to the snapshot taken before FX countdown.
     * This does not create an infusion recipe or expose a GUI/button.
     */
    public boolean replaceInfusionIngredientIfUnchanged(ItemStack expected,
                                                          ItemStack containerItem) {
        if (level == null || level.isClientSide || expected == null
                || expected.isEmpty() || expected.getCount() != 1)
            return false;
        if (containerItem == null || containerItem.getCount() > 1)
            return false;
        ItemStack now = items.get(0);
        if (now.isEmpty() || now.getCount() != 1
                || !ItemStack.isSameItemSameComponents(now, expected))
            return false;
        setItem(0, containerItem.copy()); // one atomic write + client sync
        return true;
    }

    /**
     * Crystal Farmer side item debit plus its identity in ONE pedestal
     * BlockEntity NBT snapshot. A matrix that saved countdown=1 immediately
     * before a crash can recover ONLY from this matching receipt and EMPTY
     * slot. External inventory changes invalidate the receipt.
     */
    public boolean consumeInfusionIngredientWithReceipt(ItemStack expected,
                                                          UUID ritualId, int ingredientBit) {
        if (level == null || level.isClientSide || ritualId == null
                || expected == null || expected.isEmpty() || expected.getCount() != 1
                || (ingredientBit != 1 && ingredientBit != 2))
            return false;
        ItemStack now = items.get(0);
        if (now.isEmpty() || now.getCount() != 1
                || !ItemStack.isSameItemSameComponents(now, expected))
            return false;
        items.set(0, ItemStack.EMPTY);
        infusionOutputReceipt = null;
        infusionRemovalReceipt = new LegacyCrystalFarmerPedestalReceipt(
                ritualId, ingredientBit);
        changedAndSync();
        return true;
    }

    /**
     * Atomically publish exactly one original result plus the active ritual's
     * output provenance in this pedestal's persistent inventory state.
     * External inventory writes always erase the proof.
     */
    public boolean publishInfusionResultWithReceipt(ItemStack expectedInput,
                                                     ItemStack exactOutput, UUID ritualId) {
        if (level == null || level.isClientSide || ritualId == null
                || expectedInput == null || expectedInput.getCount() != 1
                || exactOutput == null || exactOutput.getCount() != 1
                || expectedInput.isEmpty() || exactOutput.isEmpty())
            return false;
        ItemStack current = items.get(0);
        if (current.getCount() != 1
                || !ItemStack.isSameItemSameComponents(current, expectedInput))
            return false;
        String outputId = BuiltInRegistries.ITEM.getKey(exactOutput.getItem()).toString();
        if (outputId.isBlank()) return false;
        var proof = new LegacyCrystalFarmerOutputReceipt(ritualId, outputId);
        items.set(0, exactOutput.copy());
        clearInfusionRemovalReceipt();
        infusionOutputReceipt = proof;
        changedAndSync();
        return true;
    }

    /** An existing matching ItemStack WITHOUT a receipt cannot authorize ACK. */
    public boolean hasInfusionOutputReceipt(UUID ritualId, ItemStack expectedOutput) {
        if (infusionOutputReceipt == null || expectedOutput == null
                || expectedOutput.getCount() != 1 || expectedOutput.isEmpty())
            return false;
        boolean exactPhysicalOutput = ItemStack.isSameItemSameComponents(
                items.get(0), expectedOutput) && items.get(0).getCount() == 1;
        return infusionOutputReceipt.provesSavedPublication(
                ritualId,
                BuiltInRegistries.ITEM.getKey(expectedOutput.getItem()).toString(),
                exactPhysicalOutput);
    }

    public boolean hasInfusionRemovalReceipt(UUID ritualId, int ingredientBit) {
        return infusionRemovalReceipt != null
                && infusionRemovalReceipt.provesSavedRemoval(
                        ritualId, ingredientBit, items.get(0).isEmpty());
    }

    private void clearInfusionRemovalReceipt() {
        infusionRemovalReceipt = null;
    }

    private void clearInfusionOutputReceipt() {
        infusionOutputReceipt = null;
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
        if (!result.isEmpty()) {
            clearInfusionRemovalReceipt();
            clearInfusionOutputReceipt();
            changedAndSync();
        }
        return result;
    }
    @Override public ItemStack removeItemNoUpdate(int slot) {
        if (slot != 0) return ItemStack.EMPTY;
        ItemStack result = ContainerHelper.takeItem(items, slot);
        if (!result.isEmpty()) {
            clearInfusionRemovalReceipt();
            clearInfusionOutputReceipt();
            changedAndSync();
        }
        return result;
    }
    @Override public void setItem(int slot, ItemStack stack) {
        if (slot != 0) return;
        ItemStack one = stack.copy();
        if (!one.isEmpty()) one.setCount(1);
        items.set(0, one);
        clearInfusionRemovalReceipt();
        clearInfusionOutputReceipt();
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
            clearInfusionRemovalReceipt();
            clearInfusionOutputReceipt();
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
        if (infusionRemovalReceipt != null && items.get(0).isEmpty()) {
            tag.putUUID("PortInfusionRemovalRitual", infusionRemovalReceipt.ritualId());
            tag.putInt("PortInfusionRemovalBit", infusionRemovalReceipt.ingredientBit());
        }
        if (infusionOutputReceipt != null && !items.get(0).isEmpty()
                && BuiltInRegistries.ITEM.getKey(items.get(0).getItem()).toString()
                        .equals(infusionOutputReceipt.itemId())) {
            tag.putUUID("PortInfusionOutputRitual", infusionOutputReceipt.ritualId());
            tag.putString("PortInfusionOutputItem", infusionOutputReceipt.itemId());
        }
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        clearInfusionRemovalReceipt();
        clearInfusionOutputReceipt();
        items.set(0, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);
        // Malformed old-world ItemStacks cannot create multi-stack pedestals.
        if (!items.get(0).isEmpty() && items.get(0).getCount() > 1)
            items.get(0).setCount(1);
        if (!items.get(0).isEmpty()
                && tag.contains("PortInfusionOutputRitual", Tag.TAG_INT_ARRAY)
                && tag.contains("PortInfusionOutputItem", Tag.TAG_STRING)) {
            try {
                var saved = new LegacyCrystalFarmerOutputReceipt(
                        tag.getUUID("PortInfusionOutputRitual"),
                        tag.getString("PortInfusionOutputItem"));
                if (saved.provesSavedPublication(saved.ritualId(),
                        BuiltInRegistries.ITEM.getKey(items.get(0).getItem()).toString(), true))
                    infusionOutputReceipt = saved;
            } catch (IllegalArgumentException malformed) {
                clearInfusionOutputReceipt();
            }
        }
        if (items.get(0).isEmpty()
                && tag.contains("PortInfusionRemovalRitual", Tag.TAG_INT_ARRAY)
                && tag.contains("PortInfusionRemovalBit", Tag.TAG_INT)) {
            int bit = tag.getInt("PortInfusionRemovalBit");
            if (bit == 1 || bit == 2) {
                try {
                    infusionRemovalReceipt = new LegacyCrystalFarmerPedestalReceipt(
                            tag.getUUID("PortInfusionRemovalRitual"), bit);
                } catch (IllegalArgumentException malformed) {
                    clearInfusionRemovalReceipt();
                }
            }
        }
    }
}
