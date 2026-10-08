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

    public ArcaneWorkbenchMenu(int id, Inventory inventory) {
        this(id, inventory, new SimpleContainer(ArcaneWorkbenchRuntime.SLOT_COUNT));
    }

    public ArcaneWorkbenchMenu(int id, Inventory inventory, Container bench) {
        super(ThaumcraftMenus.ARCANE_WORKBENCH.get(), id);
        checkContainerSize(bench, ArcaneWorkbenchRuntime.SLOT_COUNT);
        this.bench = bench;
        bench.startOpen(inventory.player);
        for (int i = 0; i < 9; i++) {
            addSlot(new Slot(bench, i, 8 + (i % 3) * 18, 18 + (i / 3) * 18));
        }
        for (int i = 9; i < 15; i++) {
            final int slot = i;
            addSlot(new Slot(bench, slot, 8 + (slot - 9) * 18, 76) {
                @Override public boolean mayPlace(ItemStack stack) {
                    return bench.canPlaceItem(slot, stack);
                }
            });
        }
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 104 + row * 18));
        for (int col = 0; col < 9; col++)
            addSlot(new Slot(inventory, col, 8 + col * 18, 162));
    }

    /** Server-authoritative FIRSTSTEPS craft endpoint; output is granted only after the atomic commit. */
    public boolean craftThaumometer(ServerPlayer player, float totalDiscount) {
        if (!(bench instanceof ArcaneWorkbenchBlockEntity workbench)) return false;
        var knowledge = player.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge();
        if (!workbench.craftThaumometer(knowledge, totalDiscount)) return false;
        ItemStack output = new ItemStack(ThaumcraftItems.THAUMOMETER.get());
        if (!player.getInventory().add(output)) player.drop(output, false);
        ThaumcraftNetworking.sync(player);
        broadcastChanges();
        return true;
    }

    @Override public boolean stillValid(Player player) {
        return bench.stillValid(player);
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot source = slots.get(index);
        if (!source.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = source.getItem();
        ItemStack before = stack.copy();
        if (index < 15) {
            if (!moveItemStackTo(stack, 15, 51, true)) return ItemStack.EMPTY;
        } else {
            boolean moved = false;
            for (int i = 9; i < 15; i++) {
                if (slots.get(i).mayPlace(stack)
                        && moveItemStackTo(stack, i, i + 1, false)) {
                    moved = true;
                    break;
                }
            }
            if (!moved && !moveItemStackTo(stack, 0, 9, false)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) source.set(ItemStack.EMPTY);
        else source.setChanged();
        source.onTake(player, stack);
        return before;
    }

    @Override public void removed(Player player) {
        super.removed(player);
        bench.stopOpen(player);
    }
}
