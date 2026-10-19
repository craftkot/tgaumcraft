package dev.ehtid.thaumcraft.client;

import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.golem.menu.GolemLogisticsMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Functional new layout; original BETA26 artwork remains a separate parity task. */
@EventBusSubscriber(modid=ThaumcraftPort.MOD_ID,bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class GolemLogisticsScreen extends AbstractContainerScreen<GolemLogisticsMenu> {
    private Button mode,nbt,single,leave,locked,redstone,metadata,ore,mod,matching;
    private int panel;
    private final Button[] areas=new Button[6];
    private final java.util.List<Button> ordinary=new java.util.ArrayList<>();
    private final Button[] limits=new Button[4];
    public GolemLogisticsScreen(GolemLogisticsMenu menu,Inventory inv,Component title) {
        super(menu,inv,title); imageWidth=176; imageHeight=272; inventoryLabelY=179;
    }
    @SubscribeEvent public static void register(RegisterMenuScreensEvent event) { event.register(GolemModule.LOGISTICS_MENU.get(),GolemLogisticsScreen::new); }
    private void send(int id) { if(minecraft!=null && minecraft.gameMode!=null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId,id); }
    private Button button(Component text,int x,int y,int width,Runnable action) {
        return addRenderableWidget(Button.builder(text,b->action.run()).bounds(leftPos+x,topPos+y,width,16).build());
    }
    @Override protected void init() {
        super.init(); ordinary.clear();
        for(int i=0;i<9;i++) { final int id=100+i; button(Component.literal(Integer.toString(i+1)),8+i*18,43,16,()->send(id)); }
        matching=button(Component.literal("…"),144,3,24,()->{ panel=(panel+1)%(menu.hasArea()?3:2); update(); });
        matching.setTooltip(net.minecraft.client.gui.components.Tooltip.create(label("matching")));
        int[] areaActions={92,93,90,91,94,95};
        for(int i=0;i<6;i++) { final int action=areaActions[i]; areas[i]=button(Component.literal(i%2==0?"−":"+"),i%2==0?8:144,83+(i/2)*20,24,()->send(action)); }
        metadata=button(Component.empty(),8,83,78,()->send(menu.metadata()?60:30));
        ore=button(Component.empty(),90,83,78,()->send(menu.ore()?62:32));
        mod=button(Component.empty(),8,103,160,()->send(menu.mod()?63:33));
        mode=button(Component.empty(),8,63,78,()->send(0));
        nbt=button(Component.empty(),90,63,78,()->send(menu.matchNbt()?61:31));
        String[] labels={"-10","-1","+1","+10"};
        for(int i=0;i<4;i++) { final int id=i+1; limits[i]=button(Component.literal(labels[i]),8+i*41,95,37,()->send(id)); }
        leave=button(Component.empty(),8,95,78,()->send(menu.leaveOne()?65:35));
        single=button(Component.empty(),90,95,78,()->send((menu.provider()?menu.single():menu.advancedFill()?menu.existingOnly():menu.cycle())?64:34));
        ordinary.add(button(Component.literal("−"),8,117,24,()->send(80))); ordinary.add(button(Component.literal("+"),144,117,24,()->send(81)));
        ordinary.add(button(Component.literal("−"),8,137,24,()->send(82))); ordinary.add(button(Component.literal("+"),144,137,24,()->send(83)));
        locked=button(Component.empty(),8,157,78,()->send(menu.locked()?26:25));
        redstone=button(Component.empty(),90,157,78,()->send(menu.redstone()?28:27));
        ordinary.add(locked); ordinary.add(redstone);
        update();
    }
    private Component label(String key) { return Component.translatable("menu.thaumcraft.golem_seal."+key); }
    private void update() {
        if(panel==2&&!menu.hasArea()) panel=0;
        boolean matchingPanel=panel==1;
        for(int i=0;i<areas.length;i++) areas[i].visible=areas[i].active=panel==2&&menu.hasArea();
        for(var b:ordinary) b.visible=b.active=panel==0;
        metadata.visible=metadata.active=ore.visible=ore.active=mod.visible=mod.active=matchingPanel;
        metadata.setMessage(label(menu.metadata()?"meta_match":"meta_ignore"));
        ore.setMessage(label(menu.ore()?"ore_match":"ore_ignore"));
        mod.setMessage(label(menu.mod()?"mod_match":"mod_ignore"));
        mode.active=!menu.stock(); mode.setMessage(label(menu.blacklist()?"blacklist":"whitelist"));
        nbt.setMessage(label(menu.matchNbt()?"nbt_match":"nbt_ignore"));
        for(var button:limits) button.visible=button.active=panel==0&&menu.hasLimits();
        leave.visible=leave.active=panel==0&&menu.canLeaveOne(); leave.setMessage(label(menu.leaveOne()?"leave_one":"take_all"));
        single.visible=single.active=panel==0&&(menu.provider()||menu.advancedEmpty()||menu.advancedFill()); single.setMessage(label(menu.provider()?(menu.single()?"single":"batch"):menu.advancedFill()?(menu.existingOnly()?"existing_only":"any_stock"):(menu.cycle()?"cycle":"all_filters")));
        locked.setMessage(label(menu.locked()?"locked":"public"));
        redstone.setMessage(label(menu.redstone()?"redstone_stop":"redstone_ignore"));
    }
    @Override protected void containerTick() { super.containerTick(); update(); }
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my) {
        g.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xffc6c6c6);
        for(var slot:menu.slots) {
            g.fill(leftPos+slot.x-1,topPos+slot.y-1,leftPos+slot.x+17,topPos+slot.y+17,0xff373737);
            g.fill(leftPos+slot.x,topPos+slot.y,leftPos+slot.x+16,topPos+slot.y+16,0xff8b8b8b);
        }
        g.fill(leftPos+8+menu.selected()*18,topPos+39,leftPos+24+menu.selected()*18,topPos+41,0xffffd45a);
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my) {
        super.renderLabels(g,mx,my);
        if(panel==0&&menu.hasLimits()) g.drawString(font,Component.translatable("menu.thaumcraft.golem_seal.limit",menu.advancedFill()&&menu.limit()==0?label("unlimited"):Component.literal(Integer.toString(menu.limit()))),8,84,0x404040,false);
        if(panel==2) {
            int[] sizes={menu.areaX(),menu.areaY(),menu.areaZ()};
            for(int i=0;i<3;i++) { var text=Component.literal("XYZ".charAt(i)+": "+sizes[i]); g.drawString(font,text,(imageWidth-font.width(text))/2,87+i*20,0x404040,false); }
        }
        if(panel!=0) return;
        var priority=Component.translatable("menu.thaumcraft.golem_seal.priority",menu.priority());
        var color=Component.translatable("menu.thaumcraft.golem_seal.color",menu.color());
        g.drawString(font,priority,(imageWidth-font.width(priority))/2,121,0x404040,false);
        g.drawString(font,color,(imageWidth-font.width(color))/2,141,0x404040,false);
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial) { super.render(g,mx,my,partial); renderTooltip(g,mx,my); }
}
