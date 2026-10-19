package dev.ehtid.thaumcraft.client;

import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.golem.press.GolemPressMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Original artwork/editor pending; server-owned default-wood selection only. */
@EventBusSubscriber(modid = ThaumcraftPort.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class GolemPressScreen extends AbstractContainerScreen<GolemPressMenu> {
    private Button start;
    private final Button[] selectors=new Button[10];
    public GolemPressScreen(GolemPressMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title); imageHeight = 272; inventoryLabelY = 179;
    }
    @SubscribeEvent public static void register(RegisterMenuScreensEvent event) { event.register(GolemModule.PRESS_MENU.get(), GolemPressScreen::new); }
    @Override protected void init() {
        super.init();
        for(int part=0;part<5;part++) for(int direction=0;direction<2;direction++) {
            final int action=(direction==0?20:10)+part;
            selectors[part*2+direction]=addRenderableWidget(Button.builder(Component.literal(direction==0?"−":"+"),b->{
                if(minecraft!=null && minecraft.gameMode!=null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId,action);
            }).bounds(leftPos+(direction==0?8:144),topPos+18+part*20,24,16).build());
        }
        start = addRenderableWidget(Button.builder(Component.translatable("menu.thaumcraft.golem_press.start"), b -> {
            if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0);
        }).bounds(leftPos + 8, topPos + 156, 160, 18).build());
    }
    @Override protected void containerTick() { super.containerTick(); start.active = !menu.active() && menu.known(); for(var b:selectors) b.active=!menu.active(); }
    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF342C3E);
        for (var slot : menu.slots) graphics.fill(leftPos + slot.x - 1, topPos + slot.y - 1, leftPos + slot.x + 17, topPos + slot.y + 17, 0xFF17131D);
    }
    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 8, 6, 0xFFFFFF, false);
        String[] kinds={"material","head","arms","legs","addon"};
        for(int i=0;i<5;i++) {
            var label=Component.translatable("menu.thaumcraft.golem_press."+kinds[i]+"."+menu.part(i));
            graphics.drawString(font,label,(imageWidth-font.width(label))/2,22+i*20,0xFFFFFF,false);
        }
        graphics.drawString(font, Component.translatable("menu.thaumcraft.golem_press.progress", menu.active()?menu.maximum() - menu.remaining():0, menu.active()?menu.maximum():menu.cost()), 8, 143, 0xCCBBDD, false);
        graphics.drawString(font, playerInventoryTitle, 8, inventoryLabelY, 0xFFFFFF, false);
    }
    @Override public void render(GuiGraphics graphics, int x, int y, float partialTick) { super.render(graphics, x, y, partialTick); renderTooltip(graphics, x, y); }
}
