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
    public GolemPressScreen(GolemPressMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title); imageHeight = 186; inventoryLabelY = 93;
    }
    @SubscribeEvent public static void register(RegisterMenuScreensEvent event) { event.register(GolemModule.PRESS_MENU.get(), GolemPressScreen::new); }
    @Override protected void init() {
        super.init();
        start = addRenderableWidget(Button.builder(Component.translatable("menu.thaumcraft.golem_press.start"), b -> {
            if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0);
        }).bounds(leftPos + 8, topPos + 64, 116, 20).build());
    }
    @Override protected void containerTick() { super.containerTick(); start.active = !menu.active(); }
    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF342C3E);
        for (var slot : menu.slots) graphics.fill(leftPos + slot.x - 1, topPos + slot.y - 1, leftPos + slot.x + 17, topPos + slot.y + 17, 0xFF17131D);
    }
    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 8, 6, 0xFFFFFF, false);
        graphics.drawString(font, Component.translatable("menu.thaumcraft.golem_press.wood"), 8, 23, 0xFFFFFF, false);
        graphics.drawString(font, Component.translatable("menu.thaumcraft.golem_press.progress", menu.maximum() - menu.remaining(), menu.maximum()), 8, 43, 0xCCBBDD, false);
        graphics.drawString(font, playerInventoryTitle, 8, inventoryLabelY, 0xFFFFFF, false);
    }
    @Override public void render(GuiGraphics graphics, int x, int y, float partialTick) { super.render(graphics, x, y, partialTick); renderTooltip(graphics, x, y); }
}
