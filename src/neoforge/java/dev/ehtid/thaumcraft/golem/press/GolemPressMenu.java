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
    private final SimpleContainer ingredients=new SimpleContainer(7);
    private final DataSlot remaining = DataSlot.standalone(), maximum = DataSlot.standalone(), active = DataSlot.standalone();
    private final DataSlot[] parts=java.util.stream.IntStream.range(0,5).mapToObj(i->DataSlot.standalone()).toArray(DataSlot[]::new);
    private final DataSlot known=DataSlot.standalone(),cost=DataSlot.standalone();
    public GolemPressMenu(int id, Inventory inventory) { this(id, inventory, null); }
    public GolemPressMenu(int id, Inventory inventory, GolemPressBlockEntity press) {
        super(GolemModule.PRESS_MENU.get(), id);
        this.press = press; this.viewer = inventory.player;
        addSlot(new Slot(press == null ? new SimpleContainer(1) : press, 0, 142, 121) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
        });
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 190 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, 248));
        for(int i=0;i<7;i++) addSlot(new Slot(ingredients,i,8+i*18,121) {
            @Override public boolean mayPickup(Player p) { return false; }
            @Override public boolean mayPlace(ItemStack s) { return false; }
        });
        addDataSlot(remaining); addDataSlot(maximum); addDataSlot(active);
        for(var data:parts) addDataSlot(data); addDataSlot(known); addDataSlot(cost);
        refresh();
    }
    public int remaining() { return remaining.get(); }
    public int maximum() { return maximum.get(); }
    public boolean active() { return active.get() != 0; }
    public int part(int index) { return parts[index].get(); }
    public boolean known() { return known.get()!=0; }
    public int cost() { return cost.get(); }
    private void refresh() {
        if (press != null) { remaining.set(press.job().remaining()); maximum.set(press.job().maximum()); active.set(press.job().active() ? 1 : 0); }
        if(press!=null) {
            var d=press.selection(); int[] ids={d.material().id(),d.head().id(),d.arms().id(),d.legs().id(),d.addon().id()};
            for(int i=0;i<5;i++) parts[i].set(ids[i]);
            known.set(press.selectionKnown(viewer)?1:0); cost.set(dev.ehtid.thaumcraft.port.core.golem.GolemPress.cost(d));
            var recipe=dev.ehtid.thaumcraft.port.core.golem.GolemAssembly.components(d);
            for(int i=0;i<7;i++) ingredients.setItem(i,i<recipe.size()?GolemComponents.stack(recipe.get(i)):ItemStack.EMPTY);
        }
    }
    @Override public void broadcastChanges() { refresh(); super.broadcastChanges(); }
    @Override public boolean stillValid(Player player) { return press == null || player == viewer && press.stillValid(player); }
    @Override public boolean clickMenuButton(Player player, int action) {
        if (press == null || !stillValid(player)) return false;
        if(action>=10 && action<=14 || action>=20 && action<=24) {
            boolean result=press.select(player,action%10,action<20?1:-1); refresh(); return result;
        }
        if(action!=0) return false;
        boolean started = press.start(player); refresh(); return started;
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index != 0 || !stillValid(player) || !getSlot(0).hasItem()) return ItemStack.EMPTY;
        ItemStack actual = getSlot(0).getItem(), original = actual.copy();
        if (!moveItemStackTo(actual, 1, 37, true)) return ItemStack.EMPTY;
        if (actual.isEmpty()) getSlot(0).setByPlayer(ItemStack.EMPTY); else getSlot(0).setChanged();
        getSlot(0).onTake(player, actual);
        return original;
    }
    @Override public void clicked(int slot,int button,net.minecraft.world.inventory.ClickType type,Player player) {
        if(slot>=37 || press!=null && !stillValid(player)) return;
        super.clicked(slot,button,type,player);
    }
    @Override public boolean canTakeItemForPickAll(ItemStack stack,Slot slot) {
        return slot.container!=ingredients && super.canTakeItemForPickAll(stack,slot);
    }
}
