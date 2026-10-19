package dev.ehtid.thaumcraft.golem.menu;

import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.golem.seal.GolemSeals;
import dev.ehtid.thaumcraft.golem.seal.SealFilter;
import dev.ehtid.thaumcraft.port.core.golem.SealArea;
import dev.ehtid.thaumcraft.port.core.golem.SealAreaControls;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Server-owned ghost filter. Uses vanilla menu packets; no arbitrary client state payload. */
public final class GolemSealMenu extends AbstractContainerMenu {
    private final ServerLevel level;
    private final GolemSeals.Seal expected;
    private final Player viewer;
    private final SimpleContainer ghost = new SimpleContainer(1);
    private final DataSlot blacklist = DataSlot.standalone();
    private final DataSlot fill = DataSlot.standalone();
    private final DataSlot limitLow = DataSlot.standalone();
    private final DataSlot limitHigh = DataSlot.standalone();

    private final DataSlot priority = DataSlot.standalone(), color = DataSlot.standalone();
    private final DataSlot locked = DataSlot.standalone(), redstone = DataSlot.standalone();
    private final DataSlot areaX = DataSlot.standalone(), areaY = DataSlot.standalone(), areaZ = DataSlot.standalone();

    public GolemSealMenu(int id, Inventory inventory) { this(id, inventory, null, null); }

    public GolemSealMenu(int id, Inventory inventory, ServerLevel level, GolemSeals.Seal seal) {
        super(GolemModule.SEAL_MENU.get(), id);
        this.level = level;
        this.expected = seal;
        this.viewer = inventory.player;
        addSlot(new Slot(ghost, 0, 17, 24) {
            @Override public boolean mayPickup(Player player) { return false; }
            @Override public boolean mayPlace(ItemStack stack) { return false; }
        });
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 190 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, 248));
        addDataSlot(blacklist);
        addDataSlot(fill);
        addDataSlot(limitLow);
        addDataSlot(limitHigh);
        addDataSlot(priority); addDataSlot(color); addDataSlot(locked); addDataSlot(redstone);
        addDataSlot(areaX); addDataSlot(areaY); addDataSlot(areaZ);
        refresh();
    }

    private GolemSeals.Seal current() { return level == null || expected == null ? null : GolemSeals.get(level).get(expected.key()); }

    @Override public boolean stillValid(Player player) {
        if (level == null) return true; // Client mirror; all authority checks occur on the server.
        GolemSeals.Seal current = current();
        return player == viewer && player.level() == level && player.isAlive() && !player.isSpectator()
                && current != null && current.instanceId().equals(expected.instanceId())
                && current.owner().equals(player.getUUID()) && level.hasChunkAt(current.key().pos())
                && !level.isEmptyBlock(current.key().pos()) && player.mayInteract(level, current.key().pos())
                && player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(current.key().pos())) <= 64;
    }

    public boolean blacklist() { return blacklist.get() != 0; }
    public boolean hasLimit() { return fill.get() == 1 && !blacklist(); }
    public int limit() { return (limitLow.get() & 0xffff) | (limitHigh.get() & 0x7fff) << 16; }

    public int priority() { return priority.get(); }
    public int color() { return color.get(); }
    public boolean locked() { return locked.get() != 0; }
    public boolean redstone() { return redstone.get() != 0; }
    public boolean hasArea() { return fill.get() == 0; }
    public int areaX() { return areaX.get(); }
    public int areaY() { return areaY.get(); }
    public int areaZ() { return areaZ.get(); }

    private void refresh() {
        var seal = current();
        if (seal == null) return;
        ghost.setItem(0, seal.filter().item());
        priority.set(seal.priority()); color.set(seal.color());
        locked.set(seal.locked() ? 1 : 0); redstone.set(seal.redstone() ? 1 : 0);
        areaX.set(seal.area().getX()); areaY.set(seal.area().getY()); areaZ.set(seal.area().getZ());
        blacklist.set(seal.filter().blacklist() ? 1 : 0);
        fill.set(switch (seal.type()) { case PICKUP -> 0; case FILL -> 1; case EMPTY -> 2; });
        limitLow.set(seal.filter().limit() & 0xffff);
        limitHigh.set(seal.filter().limit() >>> 16);
    }

    @Override public void broadcastChanges() { refresh(); super.broadcastChanges(); }

    private void setFilter(Player player, SealFilter filter) {
        if (level != null && stillValid(player) && GolemSeals.get(level).configure(expected, player.getUUID(), filter))
            broadcastChanges();
    }

    @Override public void clicked(int slot, int button, ClickType click, Player player) {
        if (level != null && !stillValid(player)) return;
        if (slot == 0) {
            if (level != null && click == ClickType.PICKUP && (button == 0 || button == 1)) {
                var policy = current().filter();
                setFilter(player, new SealFilter(button == 1 ? ItemStack.EMPTY : getCarried(),
                        policy.blacklist(), policy.limit()));
            }
            return; // Ghost never participates in THROW, SWAP, CLONE, drag, double-click or quick-move.
        }
        super.clicked(slot, button, click, player);
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (level != null && stillValid(player) && index > 0 && index < slots.size()) {
            var policy = current().filter();
            setFilter(player, new SealFilter(slots.get(index).getItem(), policy.blacklist(), policy.limit()));
        }
        return ItemStack.EMPTY; // No real stack moved or consumed by a ghost copy.
    }

    @Override public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.container != ghost && super.canTakeItemForPickAll(stack, slot);
    }

    @Override public boolean clickMenuButton(Player player, int id) {
        if (level == null || !stillValid(player)) return false;
        var seal = current();
        if (id >= 90 && id <= 95) {
            if (seal.type() != GolemSeals.Type.PICKUP) return false;
            var size = new SealArea.BlockPos(seal.area().getX(), seal.area().getY(), seal.area().getZ());
            var adjusted = SealAreaControls.adjust(size, id);
            if (adjusted.isEmpty()) return false;
            var next = adjusted.get();
            boolean result = GolemSeals.get(level).configureArea(expected, player.getUUID(),
                    new net.minecraft.core.BlockPos(next.x(), next.y(), next.z()));
            if (result) broadcastChanges();
            return result;
        }
        if (id == 25 || id == 26 || id == 27 || id == 28 || id >= 80 && id <= 83) {
            int nextPriority = seal.priority() + (id == 80 ? -1 : id == 81 ? 1 : 0);
            int nextColor = seal.color() + (id == 82 ? -1 : id == 83 ? 1 : 0);
            boolean result = GolemSeals.get(level).configureControls(expected, player.getUUID(), nextPriority, nextColor,
                    id == 25 || id != 26 && seal.locked(), id == 27 || id != 28 && seal.redstone());
            if (result) broadcastChanges();
            return result;
        }
        var policy = seal.filter();
        if (id == 0) setFilter(player, new SealFilter(policy.item(), !policy.blacklist(), policy.limit()));
        else if (id == 5) setFilter(player, new SealFilter(ItemStack.EMPTY, policy.blacklist(), policy.limit()));
        else if (hasLimit() && id >= 1 && id <= 4) {
            int delta = switch (id) { case 1 -> -10; case 2 -> -1; case 3 -> 1; default -> 10; };
            int next = (int) Math.max(0L, Math.min(Integer.MAX_VALUE, (long) policy.limit() + delta));
            setFilter(player, new SealFilter(policy.item(), policy.blacklist(), next));
        } else return false;
        return true;
    }
}
