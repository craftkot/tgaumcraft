package dev.ehtid.thaumcraft.golem.press;

import dev.ehtid.thaumcraft.golem.GolemModule;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Vanilla menu transport; no client-supplied cost, ingredients or packed props. */
public final class GolemPressMenu extends AbstractContainerMenu {
    private final GolemPressBlockEntity press;
    private final Player viewer;
    private final DataSlot remaining = DataSlot.standalone(), maximum = DataSlot.standalone(), active = DataSlot.standalone();
    public GolemPressMenu(int id, Inventory inventory) { this(id, inventory, null); }
    public GolemPressMenu(int id, Inventory inventory, GolemPressBlockEntity press) {
        super(GolemModule.PRESS_MENU.get(), id);
        this.press = press; this.viewer = inventory.player;
        addSlot(new Slot(press == null ? new SimpleContainer(1) : press, 0, 142, 49) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
        });
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 104 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, 162));
        addDataSlot(remaining); addDataSlot(maximum); addDataSlot(active);
        refresh();
    }
    public int remaining() { return remaining.get(); }
    public int maximum() { return maximum.get(); }
    public boolean active() { return active.get() != 0; }
    private void refresh() {
        if (press != null) { remaining.set(press.job().remaining()); maximum.set(press.job().maximum()); active.set(press.job().active() ? 1 : 0); }
    }
    @Override public void broadcastChanges() { refresh(); super.broadcastChanges(); }
    @Override public boolean stillValid(Player player) { return press == null || player == viewer && press.stillValid(player); }
    @Override public boolean clickMenuButton(Player player, int action) {
        if (press == null || action != 0 || !stillValid(player)) return false;
        boolean started = press.start(player); refresh(); return started;
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index != 0 || !stillValid(player) || !getSlot(0).hasItem()) return ItemStack.EMPTY;
        ItemStack actual = getSlot(0).getItem(), original = actual.copy();
        if (!moveItemStackTo(actual, 1, slots.size(), true)) return ItemStack.EMPTY;
        if (actual.isEmpty()) getSlot(0).setByPlayer(ItemStack.EMPTY); else getSlot(0).setChanged();
        getSlot(0).onTake(player, actual);
        return original;
    }
}
