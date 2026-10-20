package dev.ehtid.thaumcraft.client;

import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.golem.menu.GolemSealMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Minimal new artwork; original BETA26 bell GUI and visual parity remain pending. */
@EventBusSubscriber(modid = ThaumcraftPort.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class GolemSealScreen extends AbstractContainerScreen<GolemSealMenu> {
    private Button mode, lock, redstone, leaveOne, matchNbt;
    private Button replant,provision,usePanel,metadata,ore,mod;
    private int panel;
    private final Button[] useActions=new Button[5];
    private final java.util.List<Button> common=new java.util.ArrayList<>();
    private final Button[] limits = new Button[4];
    private final Button[] areas = new Button[6];

    public GolemSealScreen(GolemSealMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 272;
        inventoryLabelY = 179;
    }

    @SubscribeEvent public static void register(RegisterMenuScreensEvent event) {
        event.register(GolemModule.SEAL_MENU.get(), GolemSealScreen::new);
    }

    private void send(int id) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    @Override protected void init() {
        super.init();
        mode = addRenderableWidget(Button.builder(Component.translatable("menu.thaumcraft.golem_seal." + (menu.blacklist() ? "blacklist" : "whitelist")), b -> send(0))
                .bounds(leftPos + 8, topPos + 46, 78, 18).build());
        matchNbt = addRenderableWidget(Button.builder(Component.empty(), b -> send(menu.breaker()?(menu.metadata()?60:30):(menu.matchNbt()?61:31)))
                .bounds(leftPos + 90, topPos + 46, 78, 18).build());
        replant=addRenderableWidget(Button.builder(Component.empty(),b->send(menu.replant()?60:30)).bounds(leftPos+8,topPos+46,78,18).build());
        provision=addRenderableWidget(Button.builder(Component.empty(),b->send(menu.provision()?61:31)).bounds(leftPos+90,topPos+46,78,18).build());
        String[] labels = {"-10", "-1", "+1", "+10"};
        for (int i = 0; i < 4; i++) {
            final int id = i + 1;
            limits[i] = addRenderableWidget(Button.builder(Component.literal(labels[i]), b -> send(id))
                    .bounds(leftPos + 8 + i * 41, topPos + 74, 37, 16).build());
        }
        int[] actions = {92, 93, 90, 91, 94, 95}; // X-/X+/Y-/Y+/Z-/Z+
        for (int i = 0; i < areas.length; i++) {
            final int action = actions[i];
            areas[i] = addRenderableWidget(Button.builder(Component.literal(i % 2 == 0 ? "−" : "+"), b -> send(action))
                    .bounds(leftPos + 8 + i / 2 * 54 + i % 2 * 27, topPos + 74, 22, 16).build());
        }
        leaveOne = addRenderableWidget(Button.builder(Component.empty(), b -> send(menu.leaveOne() ? 65 : 35))
                .bounds(leftPos + 8, topPos + 74, 160, 16).build());
        common.add(addRenderableWidget(Button.builder(Component.literal("−"), b -> send(80)).bounds(leftPos+8, topPos+98, 24, 18).build()));
        common.add(addRenderableWidget(Button.builder(Component.literal("+"), b -> send(81)).bounds(leftPos+144, topPos+98, 24, 18).build()));
        common.add(addRenderableWidget(Button.builder(Component.literal("−"), b -> send(82)).bounds(leftPos+8, topPos+118, 24, 18).build()));
        common.add(addRenderableWidget(Button.builder(Component.literal("+"), b -> send(83)).bounds(leftPos+144, topPos+118, 24, 18).build()));
        lock = addRenderableWidget(Button.builder(Component.empty(), b -> send(menu.locked() ? 26 : 25)).bounds(leftPos+8, topPos+138, 160, 18).build());
        redstone = addRenderableWidget(Button.builder(Component.empty(), b -> send(menu.redstone() ? 28 : 27)).bounds(leftPos+8, topPos+158, 160, 18).build());
        common.add(lock); common.add(redstone);
        usePanel=addRenderableWidget(Button.builder(Component.literal("…"),b->{panel=(panel+1)%3;updateControls();}).bounds(leftPos+144,topPos+3,24,18).build());
        metadata=addRenderableWidget(Button.builder(Component.empty(),b->send(menu.metadata()?60:30)).bounds(leftPos+8,topPos+46,160,18).build());
        ore=addRenderableWidget(Button.builder(Component.empty(),b->send(menu.ore()?62:32)).bounds(leftPos+8,topPos+66,160,18).build());
        mod=addRenderableWidget(Button.builder(Component.empty(),b->send(menu.mod()?63:33)).bounds(leftPos+8,topPos+86,160,18).build());
        for(int i=0;i<5;i++) { final int index=i+4; useActions[i]=addRenderableWidget(Button.builder(Component.empty(),b->send((menu.useToggle(index)?60:30)+index)).bounds(leftPos+8,topPos+46+i*22,160,18).build()); }
        updateControls();
    }

    private void updateControls() {
        if(!menu.use()) panel=0;
        usePanel.visible=usePanel.active=menu.use();
        boolean ordinary=panel==0;
        for(var b:common) b.visible=b.active=ordinary;
        metadata.visible=metadata.active=ore.visible=ore.active=mod.visible=mod.active=menu.use()&&panel==1;
        metadata.setMessage(Component.translatable("menu.thaumcraft.golem_seal."+(menu.metadata()?"meta_match":"meta_ignore")));
        ore.setMessage(Component.translatable("menu.thaumcraft.golem_seal."+(menu.ore()?"ore_match":"ore_ignore")));
        mod.setMessage(Component.translatable("menu.thaumcraft.golem_seal."+(menu.mod()?"mod_match":"mod_ignore")));
        String[] on={"left_click","empty_target","empty_hand","sneak","provision"},off={"right_click","solid_target","carried_hand","no_sneak","no_provision"};
        for(int i=0;i<5;i++) { useActions[i].visible=useActions[i].active=menu.use()&&panel==2; useActions[i].setMessage(Component.translatable("menu.thaumcraft.golem_seal."+(menu.useToggle(i+4)?on[i]:off[i]))); }
        mode.visible=mode.active=matchNbt.visible=matchNbt.active=ordinary&&!menu.harvest()&&!menu.lumber();
        replant.visible=replant.active=provision.visible=provision.active=menu.harvest();
        replant.setMessage(Component.translatable("menu.thaumcraft.golem_seal."+(menu.replant()?"replant":"no_replant")));
        provision.setMessage(Component.translatable("menu.thaumcraft.golem_seal."+(menu.provision()?"provision":"no_provision")));
        matchNbt.setMessage(Component.translatable("menu.thaumcraft.golem_seal." + (menu.breaker()?(menu.metadata()?"meta_match":"meta_ignore"):(menu.matchNbt()?"nbt_match":"nbt_ignore"))));
        leaveOne.visible = leaveOne.active = menu.hasLeaveOne();
        leaveOne.setMessage(Component.translatable("menu.thaumcraft.golem_seal." + (menu.leaveOne() ? "leave_one" : "take_all")));
        lock.setMessage(Component.translatable("menu.thaumcraft.golem_seal." + (menu.locked() ? "locked" : "public")));
        redstone.setMessage(Component.translatable("menu.thaumcraft.golem_seal." + (menu.redstone() ? "redstone_stop" : "redstone_ignore")));
        int[] sizes = {menu.areaX(), menu.areaY(), menu.areaZ()};
        for (int i = 0; i < areas.length; i++) {
            areas[i].visible = menu.hasArea();
            areas[i].active = menu.hasArea() && (i % 2 == 0 ? sizes[i / 2] > 1 : sizes[i / 2] < 8);
        }
        for (Button button : limits) button.visible = button.active = menu.hasLimit();
    }

    @Override protected void containerTick() {
        super.containerTick();
        updateControls();
        mode.setMessage(Component.translatable("menu.thaumcraft.golem_seal." + (menu.blacklist() ? "blacklist" : "whitelist")));
        for (Button button : limits) button.active = menu.hasLimit();
    }

    @Override protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xffc6c6c6);
        for (var slot : menu.slots) {
            if(!slot.isActive()) continue;
            g.fill(leftPos + slot.x - 1, topPos + slot.y - 1, leftPos + slot.x + 17, topPos + slot.y + 17, 0xff373737);
            g.fill(leftPos + slot.x, topPos + slot.y, leftPos + slot.x + 16, topPos + slot.y + 16, 0xff8b8b8b);
        }
    }

    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        super.renderLabels(g, mouseX, mouseY);
        if(!menu.harvest()&&!menu.lumber()) {
            g.drawString(font, Component.translatable("menu.thaumcraft.golem_seal.filter"), 40, 25, 0x404040, false);
            g.drawString(font, Component.translatable("menu.thaumcraft.golem_seal.clear"), 40, 35, 0x404040, false);
        }
        if(panel!=0) return;
        Component priority = Component.translatable("menu.thaumcraft.golem_seal.priority", menu.priority());
        Component color = Component.translatable("menu.thaumcraft.golem_seal.color", menu.color());
        g.drawString(font, priority, (imageWidth - font.width(priority)) / 2, 103, 0x404040, false);
        g.drawString(font, color, (imageWidth - font.width(color)) / 2, 123, 0x404040, false);
        if (menu.hasLimit()) {
            Component value = menu.limit() == 0 ? Component.translatable("menu.thaumcraft.golem_seal.unlimited")
                    : Component.literal(Integer.toString(menu.limit()));
            g.drawString(font, Component.translatable("menu.thaumcraft.golem_seal.limit", value), 8, 65, 0x404040, false);
        }
        if (menu.hasArea()) {
            int[] sizes = {menu.areaX(), menu.areaY(), menu.areaZ()};
            String[] axes = {"X", "Y", "Z"};
            for (int i = 0; i < axes.length; i++) {
                String value = axes[i] + ": " + sizes[i];
                g.drawString(font, value, 8 + i * 54 + (49 - font.width(value)) / 2, 65, 0x404040, false);
            }
        }
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
