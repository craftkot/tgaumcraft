package dev.ehtid.thaumcraft.client;

import dev.ehtid.thaumcraft.menu.ResearchTableMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * First playable research-table screen: the original BETA26 256x256
 * gui_research_table.png, original table slots and the original side-by-side
 * inventory grid. Card-choice widgets are deliberately NOT fabricated before
 * the full old drawCards / savedCards state machine is restored.
 */
public final class ResearchTableScreen extends AbstractContainerScreen<ResearchTableMenu> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(
                    "thaumcraft", "textures/gui/gui_research_table.png");

    public ResearchTableScreen(ResearchTableMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 256;
        imageHeight = 256;
        titleLabelX = 80;
        titleLabelY = 8;
        inventoryLabelX = 78;
        inventoryLabelY = 178;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos,
                0, 0, imageWidth, imageHeight, 256, 256);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        if (menu.hasSession()) {
            Component inspiration = Component.translatable(
                    "research_table.inspiration",
                    menu.inspiration(), menu.inspirationStart());
            graphics.drawString(font, inspiration, 80, 43, 0xEEE0BA, false);
        } else {
            graphics.drawString(font, Component.translatable(
                    "research_table.awaiting_theory"), 80, 43, 0xEEE0BA, false);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
