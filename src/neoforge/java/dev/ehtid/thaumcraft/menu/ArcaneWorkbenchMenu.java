package dev.ehtid.thaumcraft.menu;

import dev.ehtid.thaumcraft.blockentity.ArcaneWorkbenchBlockEntity;
import dev.ehtid.thaumcraft.network.ThaumcraftNetworking;
import dev.ehtid.thaumcraft.player.ThaumcraftPlayerAttachments;
import dev.ehtid.thaumcraft.port.core.ArcaneWorkbenchRuntime;
import dev.ehtid.thaumcraft.registry.ThaumcraftItems;
import net.minecraft.server.level.ServerPlayer;
import dev.ehtid.thaumcraft.registry.ThaumcraftMenus;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Server-authoritative 15-slot Arcane Workbench inventory bridge. */
public final class ArcaneWorkbenchMenu extends AbstractContainerMenu {
    private final Container bench;
    private final SimpleContainer result = new SimpleContainer(1);
    private final Player owner;

    public ArcaneWorkbenchMenu(int id, Inventory inventory) {
        this(id, inventory, new SimpleContainer(ArcaneWorkbenchRuntime.SLOT_COUNT));
    }

    public ArcaneWorkbenchMenu(int id, Inventory inventory, Container bench) {
        super(ThaumcraftMenus.ARCANE_WORKBENCH.get(), id);
        checkContainerSize(bench, ArcaneWorkbenchRuntime.SLOT_COUNT);
        this.bench = bench;
        this.owner = inventory.player;
        bench.startOpen(inventory.player);
        addSlot(new Slot(result, 0, 160, 64) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
            @Override public boolean mayPickup(Player player) {
                if (player instanceof ServerPlayer sp) return canCraftThaumometer(sp, 0.0F);
                return result.getItem(0).is(ThaumcraftItems.THAUMOMETER.get());
            }
            @Override public void onTake(Player player, ItemStack stack) {
                if (player instanceof ServerPlayer sp) craftThaumometer(sp, 0.0F);
                super.onTake(player, stack);
            }
        });
        for (int i = 0; i < 9; i++) {
            addSlot(new Slot(bench, i, 40 + (i % 3) * 24, 40 + (i / 3) * 24));
        }
        final int[] crystalX = {64, 17, 112, 17, 112, 64};
        final int[] crystalY = {13, 35, 35, 93, 93, 115};
        for (int i = 9; i < 15; i++) {
            final int slot = i;
            addSlot(new Slot(bench, slot, crystalX[slot - 9], crystalY[slot - 9]) {
                @Override public boolean mayPlace(ItemStack stack) {
                    return bench.canPlaceItem(slot, stack);
                }
            });
        }
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(inventory, col + row * 9 + 9, 16 + col * 18, 151 + row * 18));
        for (int col = 0; col < 9; col++)
            addSlot(new Slot(inventory, col, 16 + col * 18, 209));
    }

    private boolean canCraftThaumometer(ServerPlayer player, float totalDiscount) {
        if (!(bench instanceof ArcaneWorkbenchBlockEntity workbench) || !stillValid(player)) return false;
        return workbench.canCraftThaumometer(
                player.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge(), totalDiscount);
    }

    /** Server-authoritative FIRSTSTEPS craft endpoint; output is granted only after the atomic commit. */
    public boolean craftThaumometer(ServerPlayer player, float totalDiscount) {
        if (!(bench instanceof ArcaneWorkbenchBlockEntity workbench) || !stillValid(player)) return false;
        var knowledge = player.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge();
        if (!workbench.craftThaumometer(knowledge, totalDiscount)) return false;
        ThaumcraftNetworking.sync(player);
        refreshResult();
        broadcastChanges();
        return true;
    }

    private void refreshResult() {
        if (owner instanceof ServerPlayer sp && canCraftThaumometer(sp, 0.0F))
            result.setItem(0, new ItemStack(ThaumcraftItems.THAUMOMETER.get()));
        else result.setItem(0, ItemStack.EMPTY);
    }

    @Override public void broadcastChanges() {
        refreshResult();
        super.broadcastChanges();
    }

    @Override public boolean stillValid(Player player) {
        return bench.stillValid(player);
    }

    private boolean hasInventorySpace(ItemStack stack) {
        for (int i = 16; i < 52; i++) {
            Slot slot = slots.get(i);
            ItemStack existing = slot.getItem();
            if (existing.isEmpty() && slot.mayPlace(stack) && slot.isActive()) return true;
            if (!existing.isEmpty() && slot.isActive() && slot.mayPlace(stack)
                    && ItemStack.isSameItemSameComponents(existing, stack)
                    && existing.getCount() < Math.min(slot.getMaxStackSize(existing), existing.getMaxStackSize()))
                return true;
        }
        return false;
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot source = slots.get(index);
        if (!source.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = source.getItem();
        ItemStack before = stack.copy();
        if (index == 0) {
            if (!(player instanceof ServerPlayer sp) || !canCraftThaumometer(sp, 0.0F)) return ItemStack.EMPTY;
            if (!hasInventorySpace(stack)) return ItemStack.EMPTY;
            // The output stack is virtual: commit resources once before inserting
            // the crafted item, then refresh the preview from server state.
            if (!craftThaumometer(sp, 0.0F)) return ItemStack.EMPTY;
            ItemStack crafted = new ItemStack(ThaumcraftItems.THAUMOMETER.get());
            if (!moveItemStackTo(crafted, 16, 52, true)) {
                // Defensive fallback: a concurrently filled inventory must not delete the item.
                player.drop(crafted, false);
            }
            broadcastChanges();
            return before;
        } else if (index < 16) {
            if (!moveItemStackTo(stack, 16, 52, true)) return ItemStack.EMPTY;
        } else {
            boolean moved = false;
            for (int i = 10; i < 16; i++) {
                if (slots.get(i).mayPlace(stack)
                        && moveItemStackTo(stack, i, i + 1, false)) {
                    moved = true;
                    break;
                }
            }
            if (!moved && !moveItemStackTo(stack, 1, 10, false)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) source.set(ItemStack.EMPTY);
        else source.setChanged();
        source.onTake(player, stack);
        broadcastChanges();
        return before;
    }

    @Override public void removed(Player player) {
        super.removed(player);
        bench.stopOpen(player);
    }
}
