package dev.ehtid.thaumcraft.blockentity;

import dev.ehtid.thaumcraft.item.CrystalEssenceItem;
import dev.ehtid.thaumcraft.port.core.ArcaneWorkbenchRuntime;
import dev.ehtid.thaumcraft.registry.ThaumcraftBlockEntities;
import dev.ehtid.thaumcraft.registry.ThaumcraftItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Server-owned, persisted BETA26 workbench inventory:
 * crafting 0..8; six ordered primal Crystal Essence slots 9..14.
 * The menu/vis transaction bridge is deliberately a separate follow-up.
 */
public final class ArcaneWorkbenchBlockEntity extends BlockEntity implements Container {
    private final NonNullList<ItemStack> items =
            NonNullList.withSize(ArcaneWorkbenchRuntime.SLOT_COUNT, ItemStack.EMPTY);

    public ArcaneWorkbenchBlockEntity(BlockPos pos, BlockState state) {
        super(ThaumcraftBlockEntities.ARCANE_WORKBENCH.get(), pos, state);
    }

    @Override
    public int getContainerSize() {
        return ArcaneWorkbenchRuntime.SLOT_COUNT;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        ItemStack removed = ContainerHelper.removeItem(items, slot, count);
        if (!removed.isEmpty()) setChanged();
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (!stack.isEmpty() && !canPlaceItem(slot, stack)) return;
        items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) stack.setCount(getMaxStackSize());
        setChanged();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= getContainerSize()) return false;
        if (slot < ArcaneWorkbenchRuntime.CRAFT_SLOTS) return true;
        if (stack.isEmpty()) return true;
        if (!stack.is(ThaumcraftItems.CRYSTAL_ESSENCE.get())) return false;
        String aspect = CrystalEssenceItem.legacyAspect(stack);
        return ArcaneWorkbenchRuntime.PRIMAL_ORDER[slot - ArcaneWorkbenchRuntime.CRAFT_SLOTS].equals(aspect);
    }

    @Override
    public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX() + 0.5,
                worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= 64.0;
    }

    @Override
    public void clearContent() {
        for (int slot = 0; slot < items.size(); slot++) items.set(slot, ItemStack.EMPTY);
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ContainerHelper.loadAllItems(tag, items, registries);
    }
}
