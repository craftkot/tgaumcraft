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
    private final DataSlot[] combatToggles={DataSlot.standalone(),DataSlot.standalone(),DataSlot.standalone()};
    private final DataSlot blacklist = DataSlot.standalone();
    private final DataSlot fill = DataSlot.standalone();
    private final DataSlot limitLow = DataSlot.standalone();
    private final DataSlot limitHigh = DataSlot.standalone();

    private final DataSlot priority = DataSlot.standalone(), color = DataSlot.standalone();
    private final DataSlot locked = DataSlot.standalone(), redstone = DataSlot.standalone();
    private final DataSlot areaX = DataSlot.standalone(), areaY = DataSlot.standalone(), areaZ = DataSlot.standalone();
    private final DataSlot leaveOne = DataSlot.standalone();
    private final DataSlot matchNbt = DataSlot.standalone();
    private final DataSlot metadata=DataSlot.standalone(),ore=DataSlot.standalone(),mod=DataSlot.standalone();
    private final DataSlot[] useToggles=java.util.stream.IntStream.range(0,5).mapToObj(i->DataSlot.standalone()).toArray(DataSlot[]::new);
    private final DataSlot replant=DataSlot.standalone(),provision=DataSlot.standalone();

    public GolemSealMenu(int id, Inventory inventory) { this(id, inventory, null, null); }

    public GolemSealMenu(int id, Inventory inventory, ServerLevel level, GolemSeals.Seal seal) {
        super(GolemModule.SEAL_MENU.get(), id);
        this.level = level;
        this.expected = seal;
        this.viewer = inventory.player;
        addSlot(new Slot(ghost, 0, 17, 24) {
            @Override public boolean isActive() { return !harvest()&&!lumber()&&!combat(); }
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
        addDataSlot(leaveOne);
        addDataSlot(matchNbt);
        addDataSlot(replant); addDataSlot(provision); addDataSlot(metadata); addDataSlot(ore); addDataSlot(mod); for(var toggle:useToggles) addDataSlot(toggle); for(var toggle:combatToggles) addDataSlot(toggle);
        refresh();
    }

    private GolemSeals.Seal current() { return level == null || expected == null ? null : GolemSeals.get(level).get(expected.key()); }

    @Override public boolean stillValid(Player player) {
        if (level == null) return true; // Client mirror; all authority checks occur on the server.
        GolemSeals.Seal current = current();
        return player == viewer && player.level() == level && player.isAlive() && !player.isSpectator()
                && current != null && current.instanceId().equals(expected.instanceId())
                && current.owner().equals(player.getUUID()) && level.hasChunkAt(current.key().pos())
                && (!level.isEmptyBlock(current.key().pos())||current.type()==GolemSeals.Type.USE) && player.mayInteract(level, current.key().pos())
                && player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(current.key().pos())) <= 64;
    }

    public boolean blacklist() { return blacklist.get() != 0; }
    public boolean hasLimit() { return fill.get() == 1 && !blacklist(); }
    public int limit() { return (limitLow.get() & 0xffff) | (limitHigh.get() & 0x7fff) << 16; }

    public int priority() { return priority.get(); }
    public int color() { return color.get(); }
    public boolean locked() { return locked.get() != 0; }
    public boolean redstone() { return redstone.get() != 0; }
    public boolean hasArea() { return fill.get() == 0 || harvest() || breaker() || lumber() || combat(); }
    public boolean combat() { return fill.get()>=7&&fill.get()<=9; }
    public boolean advancedGuard() { return fill.get()==8; }
    public boolean combatToggle(int index) { return combatToggles[index].get()!=0; }
    public boolean use() { return fill.get()==6; }
    public boolean useToggle(int sourceIndex) { return useToggles[sourceIndex-4].get()!=0; }
    public boolean ore() { return ore.get()!=0; }
    public boolean mod() { return mod.get()!=0; }
    public boolean lumber() { return fill.get()==5; }
    public boolean breaker() { return fill.get()==4; }
    public boolean metadata() { return metadata.get()!=0; }
    public boolean harvest() { return fill.get()==3; }
    public boolean replant() { return replant.get()!=0; }
    public boolean provision() { return provision.get()!=0; }
    public int areaX() { return areaX.get(); }
    public int areaY() { return areaY.get(); }
    public int areaZ() { return areaZ.get(); }
    public boolean hasLeaveOne() { return fill.get() == 2; }
    public boolean leaveOne() { return leaveOne.get() != 0; }
    public boolean matchNbt() { return matchNbt.get() != 0; }

    private void refresh() {
        var seal = current();
        if (seal == null) return;
        ghost.setItem(0, seal.filter().item());
        priority.set(seal.priority()); color.set(seal.color());
        locked.set(seal.locked() ? 1 : 0); redstone.set(seal.redstone() ? 1 : 0);
        areaX.set(seal.area().getX()); areaY.set(seal.area().getY()); areaZ.set(seal.area().getZ());
        leaveOne.set(seal.leaveOne() ? 1 : 0);
        matchNbt.set(seal.filter().matchNbt() ? 1 : 0); metadata.set(seal.filter().options().metadata()?1:0); ore.set(seal.filter().options().ore()?1:0); mod.set(seal.filter().options().mod()?1:0);
        var settings=GolemSeals.get(level).useSettings(seal); for(int i=0;i<5;i++) useToggles[i].set(settings.toggle(i+4)?1:0);
        var combat=GolemSeals.get(level).combatSettings(seal); combatToggles[0].set(combat.mobs()?1:0); combatToggles[1].set(combat.animals()?1:0); combatToggles[2].set(combat.players()?1:0);
        blacklist.set(seal.filter().blacklist() ? 1 : 0);
        fill.set(switch (seal.type()) { case PICKUP -> 0; case FILL -> 1; case HARVEST->3; case BREAKER->4; case LUMBER->5; case USE->6; case GUARD->7; case GUARD_ADVANCED->8; case BUTCHER->9; default -> 2; });
        replant.set(seal.harvest().replant()?1:0); provision.set(seal.harvest().provision()?1:0);
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
            if(harvest()||lumber()||combat()) return;
            if (level != null && click == ClickType.PICKUP && (button == 0 || button == 1)) {
                var policy = current().filter();
                setFilter(player, policy.withItem(button == 1 ? ItemStack.EMPTY : getCarried()));
            }
            return; // Ghost never participates in THROW, SWAP, CLONE, drag, double-click or quick-move.
        }
        super.clicked(slot, button, click, player);
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if(harvest()||lumber()||combat()) return ItemStack.EMPTY;
        if (level != null && stillValid(player) && index > 0 && index < slots.size()) {
            var policy = current().filter();
            setFilter(player, policy.withItem(slots.get(index).getItem()));
        }
        return ItemStack.EMPTY; // No real stack moved or consumed by a ghost copy.
    }

    @Override public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.container != ghost && super.canTakeItemForPickAll(stack, slot);
    }

    @Override public boolean clickMenuButton(Player player, int id) {
        if (level == null || !stillValid(player)) return false;
        var seal = current();
        if(advancedGuard()&&(id>=30&&id<=32||id>=60&&id<=62)) {
            int index=id<60?id-30:id-60; var data=GolemSeals.get(level);
            boolean result=data.configureCombat(expected,player.getUUID(),data.combatSettings(seal).withToggle(index,id<60));
            if(result) broadcastChanges(); return result;
        }
        if(use()) {
            if(id==30||id==60||id==32||id==62||id==33||id==63) {
                var options=seal.filter().options();
                options=switch(id) { case 30,60->options.withMetadata(id==30); case 32,62->options.withOre(id==32); default->options.withMod(id==33); };
                boolean result=GolemSeals.get(level).configure(expected,player.getUUID(),seal.filter().withOptions(options)); if(result) broadcastChanges(); return result;
            }
            int index=id>=34&&id<=38?id-30:id>=64&&id<=68?id-60:-1;
            if(index>=4) { boolean result=GolemSeals.get(level).configureUse(expected,player.getUUID(),GolemSeals.get(level).useSettings(seal).withToggle(index,id<60)); if(result) broadcastChanges(); return result; }
        }
        if(harvest() && (id==30 || id==60 || id==31 || id==61)) {
            boolean result=GolemSeals.get(level).configureHarvest(expected,player.getUUID(),
                    new dev.ehtid.thaumcraft.golem.seal.HarvestSettings(id==30 || id!=60&&seal.harvest().replant(),
                            id==31 || id!=61&&seal.harvest().provision()));
            if(result) broadcastChanges(); return result;
        }
        if(breaker() && (id==30 || id==60)) {
            boolean result=GolemSeals.get(level).configure(expected,player.getUUID(),seal.filter().withOptions(seal.filter().options().withMetadata(id==30)));
            if(result) broadcastChanges(); return result;
        }
        if (id == 31 || id == 61) {
            if(breaker()||lumber()||combat()) return false;
            boolean result = GolemSeals.get(level).configure(expected, player.getUUID(), seal.filter().withMatchNbt(id == 31));
            if (result) broadcastChanges();
            return result;
        }
        if (id == 35 || id == 65) {
            boolean result = GolemSeals.get(level).configureLeaveOne(expected, player.getUUID(), id == 35);
            if (result) broadcastChanges();
            return result;
        }
        if (id >= 90 && id <= 95) {
            if (!seal.type().areaControl()) return false;
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
        if(harvest()||lumber()||combat()) return false;
        if (id == 0) setFilter(player, policy.withBlacklist(!policy.blacklist()));
        else if (id == 5) setFilter(player, policy.withItem(ItemStack.EMPTY));
        else if (hasLimit() && id >= 1 && id <= 4) {
            int delta = switch (id) { case 1 -> -10; case 2 -> -1; case 3 -> 1; default -> 10; };
            int next = (int) Math.max(0L, Math.min(Integer.MAX_VALUE, (long) policy.limit() + delta));
            setFilter(player, policy.withLimit(next));
        } else return false;
        return true;
    }
}
