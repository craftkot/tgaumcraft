package dev.ehtid.thaumcraft.blockentity;

import dev.ehtid.thaumcraft.item.CrystalEssenceItem;
import dev.ehtid.thaumcraft.port.core.ArcaneWorkbenchRuntime;
import dev.ehtid.thaumcraft.port.core.ThaumometerRecipeLogic;
import dev.ehtid.thaumcraft.port.core.PlayerKnowledgeData;
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
 * Server-authoritative vis transactions are exposed through the menu bridge.
 */
public final class ArcaneWorkbenchBlockEntity extends BlockEntity implements Container {
    private static final String TAG_VIS = "vis";

    private final NonNullList<ItemStack> items =
            NonNullList.withSize(ArcaneWorkbenchRuntime.SLOT_COUNT, ItemStack.EMPTY);
    private float vis;

    public ArcaneWorkbenchBlockEntity(BlockPos pos, BlockState state) {
        super(ThaumcraftBlockEntities.ARCANE_WORKBENCH.get(), pos, state);
    }

    /** Server-owned vis snapshot used by FIRSTSTEPS arcane crafting transactions. */
    public float getVis() {
        return vis;
    }

    public void setVis(float vis) {
        if (!Float.isFinite(vis)) throw new IllegalArgumentException("vis must be finite");
        float normalized = Math.max(0.0F, vis);
        if (Float.compare(this.vis, normalized) != 0) {
            this.vis = normalized;
            setChanged();
        }
    }

    /** Atomically checks and consumes vis; failed crafts leave the stored value untouched. */
    public boolean consumeVis(float amount) {
        if (!Float.isFinite(amount) || amount < 0.0F) throw new IllegalArgumentException("amount");
        if (vis < amount) return false;
        setVis(vis - amount);
        return true;
    }

    /**
     * Commits the legacy Thaumometer inputs and vis as one server-side transaction.
     * Callers must validate research separately; this method never partially consumes.
     */
    public boolean canCraftThaumometer(PlayerKnowledgeData knowledge, float totalDiscount) {
        if (!knowledge.isResearchKnown(ThaumometerRecipeLogic.RESEARCH)) return false;
        int cost = ThaumometerRecipeLogic.discountedVis(totalDiscount);
        for (int slot = 0; slot < ArcaneWorkbenchRuntime.CRAFT_SLOTS; slot++) {
            ItemStack stack = items.get(slot);
            if (slot == 4) {
                if (!stack.is(net.minecraft.world.item.Items.GLASS_PANE)) return false;
            } else if (slot == 1 || slot == 3 || slot == 5 || slot == 7) {
                if (!stack.is(net.minecraft.world.item.Items.GOLD_INGOT)) return false;
            } else if (!stack.isEmpty()) {
                return false;
            }
        }
        for (int slot = ArcaneWorkbenchRuntime.CRAFT_SLOTS; slot < ArcaneWorkbenchRuntime.SLOT_COUNT; slot++) {
            if (items.get(slot).isEmpty() || !canPlaceItem(slot, items.get(slot))) return false;
        }
        return vis >= cost;
    }

    public boolean craftThaumometer(PlayerKnowledgeData knowledge, float totalDiscount) {
        if (!canCraftThaumometer(knowledge, totalDiscount)) return false;
        int cost = ThaumometerRecipeLogic.discountedVis(totalDiscount);
        for (int slot : new int[]{1, 3, 4, 5, 7, 9, 10, 11, 12, 13, 14}) items.get(slot).shrink(1);
        vis -= cost;
        setChanged();
        knowledge.addResearch(ThaumometerRecipeLogic.LEGACY_CRAFT_MARKER);
        return true;
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
        return slot >= 0 && slot < items.size() ? items.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        if (slot < 0 || slot >= items.size() || count <= 0) return ItemStack.EMPTY;
        ItemStack removed = ContainerHelper.removeItem(items, slot, count);
        if (!removed.isEmpty()) setChanged();
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (slot < 0 || slot >= items.size()) return ItemStack.EMPTY;
        ItemStack removed = ContainerHelper.takeItem(items, slot);
        if (!removed.isEmpty()) setChanged();
        return removed;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= getContainerSize()) return;
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
        tag.putFloat(TAG_VIS, vis);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ContainerHelper.loadAllItems(tag, items, registries);
        float storedVis = tag.getFloat(TAG_VIS);
        vis = Float.isFinite(storedVis) ? Math.max(0.0F, storedVis) : 0.0F;
    }
}
