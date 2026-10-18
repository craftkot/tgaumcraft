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
    private Button mode;
    private final Button[] limits = new Button[4];

    public GolemSealScreen(GolemSealMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 186;
        inventoryLabelY = 93;
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
                .bounds(leftPos + 8, topPos + 46, 160, 18).build());
        String[] labels = {"-10", "-1", "+1", "+10"};
        for (int i = 0; i < 4; i++) {
            final int id = i + 1;
            limits[i] = addRenderableWidget(Button.builder(Component.literal(labels[i]), b -> send(id))
                    .bounds(leftPos + 8 + i * 41, topPos + 74, 37, 16).build());
        }
    }

    @Override protected void containerTick() {
        super.containerTick();
        mode.setMessage(Component.translatable("menu.thaumcraft.golem_seal." + (menu.blacklist() ? "blacklist" : "whitelist")));
        for (Button button : limits) button.active = menu.hasLimit();
    }

    @Override protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xffc6c6c6);
        for (var slot : menu.slots) {
            g.fill(leftPos + slot.x - 1, topPos + slot.y - 1, leftPos + slot.x + 17, topPos + slot.y + 17, 0xff373737);
            g.fill(leftPos + slot.x, topPos + slot.y, leftPos + slot.x + 16, topPos + slot.y + 16, 0xff8b8b8b);
        }
    }

    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        super.renderLabels(g, mouseX, mouseY);
        g.drawString(font, Component.translatable("menu.thaumcraft.golem_seal.filter"), 40, 25, 0x404040, false);
        g.drawString(font, Component.translatable("menu.thaumcraft.golem_seal.clear"), 40, 35, 0x404040, false);
        if (menu.hasLimit()) {
            Component value = menu.limit() == 0 ? Component.translatable("menu.thaumcraft.golem_seal.unlimited")
                    : Component.literal(Integer.toString(menu.limit()));
            g.drawString(font, Component.translatable("menu.thaumcraft.golem_seal.limit", value), 8, 65, 0x404040, false);
        }
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
