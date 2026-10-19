package dev.ehtid.thaumcraft.golem.menu;

import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.golem.seal.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/** Nine ghost slots and selected-slot stock controls; every edit is owner/instance/range guarded. */
public final class GolemLogisticsMenu extends AbstractContainerMenu {
    private final ServerLevel level;
    private final GolemSeals.Seal expected;
    private final Player viewer;
    private final SimpleContainer ghost=new SimpleContainer(9);
    private final DataSlot metadata=DataSlot.standalone(),ore=DataSlot.standalone(),mod=DataSlot.standalone();
    private final DataSlot cycle=DataSlot.standalone();
    private final DataSlot type=DataSlot.standalone(), selected=DataSlot.standalone();
    private final DataSlot low=DataSlot.standalone(), high=DataSlot.standalone();
    private final DataSlot blacklist=DataSlot.standalone(), nbt=DataSlot.standalone(), single=DataSlot.standalone(), leave=DataSlot.standalone();
    private final DataSlot priority=DataSlot.standalone(),color=DataSlot.standalone(),locked=DataSlot.standalone(),redstone=DataSlot.standalone();
    public GolemLogisticsMenu(int id,Inventory inv) { this(id,inv,null,null); }
    public GolemLogisticsMenu(int id,Inventory inv,ServerLevel level,GolemSeals.Seal seal) {
        super(GolemModule.LOGISTICS_MENU.get(),id); this.level=level; expected=seal; viewer=inv.player;
        for(int i=0;i<9;i++) addSlot(new Slot(ghost,i,8+i*18,23) {
            @Override public boolean mayPickup(Player p) { return false; }
            @Override public boolean mayPlace(ItemStack s) { return false; }
        });
        for(int r=0;r<3;r++) for(int c=0;c<9;c++) addSlot(new Slot(inv,c+r*9+9,8+c*18,190+r*18));
        for(int c=0;c<9;c++) addSlot(new Slot(inv,c,8+c*18,248));
        for(var data:new DataSlot[]{type,selected,low,high,blacklist,nbt,single,leave,priority,color,locked,redstone,cycle,metadata,ore,mod}) addDataSlot(data);
        refresh();
    }
    private GolemSeals.Seal current() { return level==null || expected==null?null:GolemSeals.get(level).get(expected.key()); }
    @Override public boolean stillValid(Player p) {
        if(level==null) return true;
        var s=current();
        return p==viewer && p.level()==level && p.isAlive() && !p.isSpectator() && s!=null
                && s.instanceId().equals(expected.instanceId()) && s.type().logistics() && s.owner().equals(p.getUUID())
                && level.hasChunkAt(s.key().pos()) && !level.isEmptyBlock(s.key().pos()) && p.mayInteract(level,s.key().pos())
                && p.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(s.key().pos()))<=64;
    }
    public int selected() { return selected.get(); }
    public boolean stock() { return type.get()==1; }
    public boolean provider() { return type.get()==2; }
    public boolean advancedEmpty() { return type.get()==3; }
    public boolean cycle() { return cycle.get()!=0; }
    public boolean blacklist() { return blacklist.get()!=0; }
    public boolean metadata() { return metadata.get()!=0; }
    public boolean ore() { return ore.get()!=0; }
    public boolean mod() { return mod.get()!=0; }
    public boolean matchNbt() { return nbt.get()!=0; }
    public boolean single() { return single.get()!=0; }
    public boolean leaveOne() { return leave.get()!=0; }
    public int limit() { return (low.get()&65535)|((high.get()&32767)<<16); }
    public int priority() { return priority.get(); }
    public int color() { return color.get(); }
    public boolean locked() { return locked.get()!=0; }
    public boolean redstone() { return redstone.get()!=0; }
    private void refresh() {
        var s=current(); if(s==null) return;
        type.set(s.type()==GolemSeals.Type.STOCK?1:s.type()==GolemSeals.Type.PROVIDE?2:3);
        for(int i=0;i<9;i++) ghost.setItem(i,s.logistics().slot(i).item());
        var filter=s.logistics().slot(selected());
        low.set(filter.limit()&65535); high.set(filter.limit()>>>16);
        blacklist.set(s.logistics().blacklist()?1:0); nbt.set(s.logistics().matchNbt()?1:0);
        metadata.set(s.logistics().options().metadata()?1:0); ore.set(s.logistics().options().ore()?1:0); mod.set(s.logistics().options().mod()?1:0);
        cycle.set(s.logistics().cycle()?1:0); single.set(s.logistics().single()?1:0); leave.set(s.leaveOne()?1:0);
        priority.set(s.priority()); color.set(s.color()); locked.set(s.locked()?1:0); redstone.set(s.redstone()?1:0);
    }
    @Override public void broadcastChanges() { refresh(); super.broadcastChanges(); }
    private boolean edit(Player p,LogisticsPolicy next) {
        boolean changed=stillValid(p) && GolemSeals.get(level).configureLogistics(expected,p.getUUID(),next);
        if(changed) broadcastChanges(); return changed;
    }
    @Override public void clicked(int slot,int button,ClickType click,Player p) {
        if(level!=null && !stillValid(p)) return;
        if(slot>=0 && slot<9) {
            if(level!=null && click==ClickType.PICKUP && (button==0 || button==1)) {
                selected.set(slot); var policy=current().logistics();
                edit(p,policy.withSlot(slot,policy.slot(slot).withItem(button==1?ItemStack.EMPTY:getCarried())));
            }
            return;
        }
        super.clicked(slot,button,click,p);
    }
    @Override public ItemStack quickMoveStack(Player p,int index) {
        if(level!=null && stillValid(p) && index>=9 && index<slots.size()) {
            var policy=current().logistics(); edit(p,policy.withSlot(selected(),policy.slot(selected()).withItem(slots.get(index).getItem())));
        }
        return ItemStack.EMPTY;
    }
    @Override public boolean canTakeItemForPickAll(ItemStack s,Slot slot) { return slot.container!=ghost && super.canTakeItemForPickAll(s,slot); }
    @Override public boolean clickMenuButton(Player p,int id) {
        if(level==null || !stillValid(p)) return false;
        var s=current(); var policy=s.logistics(); var data=GolemSeals.get(level);
        if(id>=100 && id<109) { selected.set(id-100); broadcastChanges(); return true; }
        if(id==0 && !stock()) return edit(p,policy.withMode(!policy.blacklist(),policy.matchNbt(),policy.single()));
        if(id==30 || id==60) return edit(p,policy.withOptions(policy.options().withMetadata(id==30)));
        if(id==32 || id==62) return edit(p,policy.withOptions(policy.options().withOre(id==32)));
        if(id==33 || id==63) return edit(p,policy.withOptions(policy.options().withMod(id==33)));
        if(id==31 || id==61) return edit(p,policy.withMode(policy.blacklist(),id==31,policy.single()));
        if((id==34 || id==64) && advancedEmpty()) return edit(p,policy.withCycle(id==34));
        if((id==34 || id==64) && provider()) return edit(p,policy.withMode(policy.blacklist(),policy.matchNbt(),id==34));
        boolean result;
        if((id==35 || id==65) && !stock()) result=data.configureLeaveOne(expected,p.getUUID(),id==35);
        else if(stock() && id>=1 && id<=4) {
            int delta=switch(id) { case 1->-10; case 2->-1; case 3->1; default->10; };
            int count=(int)Math.max(0L,Math.min(Integer.MAX_VALUE,(long)limit()+delta));
            return edit(p,policy.withSlot(selected(),policy.slot(selected()).withLimit(count)));
        } else if(id==25 || id==26 || id==27 || id==28 || id>=80 && id<=83) {
            result=data.configureControls(expected,p.getUUID(),s.priority()+(id==80?-1:id==81?1:0),s.color()+(id==82?-1:id==83?1:0),
                    id==25 || id!=26&&s.locked(),id==27 || id!=28&&s.redstone());
        } else return false;
        if(result) broadcastChanges(); return result;
    }
}
