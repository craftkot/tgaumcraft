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
    private static final ResourceLocation BUTTON_ATLAS =
            ResourceLocation.fromNamespaceAndPath(
                    "thaumcraft", "textures/gui/gui_base.png");

    // Exact BETA26 GuiResearchTable buttonCreate/Complete/Scrap positions.
    private static final int COMPLETE_X = 191, COMPLETE_Y = 96;
    private static final int SCRAP_X = 128, SCRAP_Y = 168;
    private static final int BUTTON_WIDTH = 49, BUTTON_HEIGHT = 11;

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
        // GUI button IDs 7 and 9 are implemented server-authoritatively;
        // original buttons for start/draw/select stay hidden until CardChoice
        // generation and aid handling are completely restored.
        if (menu.hasSession()) {
            if (menu.inspiration() <= 0) {
                renderOriginalButton(graphics, COMPLETE_X, COMPLETE_Y);
            } else {
                renderOriginalButton(graphics, SCRAP_X, SCRAP_Y);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        if (menu.hasSession()) {
            Component inspiration = Component.translatable(
                    "research_table.inspiration",
                    menu.inspiration(), menu.inspirationStart());
            graphics.drawString(font, inspiration, 80, 43, 0xEEE0BA, false);
            if (menu.inspiration() <= 0) {
                renderButtonText(graphics, COMPLETE_X, COMPLETE_Y,
                        Component.translatable("button.complete.theory"));
            } else {
                renderButtonText(graphics, SCRAP_X, SCRAP_Y,
                        Component.translatable("button.scrap.theory"));
            }
        } else {
            graphics.drawString(font, Component.translatable(
                    "research_table.awaiting_theory"), 80, 43, 0xEEE0BA, false);
        }
    }

    private void renderOriginalButton(GuiGraphics graphics, int x, int y) {
        // Exact green BETA26 GuiImageButton texture region (37,66) in gui_base.
        graphics.blit(BUTTON_ATLAS, leftPos + x, topPos + y,
                37, 66, BUTTON_WIDTH, BUTTON_HEIGHT, 256, 256);
    }

    private void renderButtonText(GuiGraphics graphics, int x, int y, Component text) {
        graphics.pose().pushPose();
        graphics.pose().translate(x + BUTTON_WIDTH / 2.0, y + 2, 0.0);
        graphics.pose().scale(0.5F, 0.5F, 1.0F);
        graphics.drawString(font, text.getString(), -font.width(text) / 2, 0, 0x89AFAA, false);
        graphics.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
        if (mouseButton == 0 && menu.hasSession() && minecraft != null
                && minecraft.gameMode != null) {
            int x = menu.inspiration() <= 0 ? COMPLETE_X : SCRAP_X;
            int y = menu.inspiration() <= 0 ? COMPLETE_Y : SCRAP_Y;
            if (mouseX >= leftPos + x && mouseX < leftPos + x + BUTTON_WIDTH
                    && mouseY >= topPos + y && mouseY < topPos + y + BUTTON_HEIGHT) {
                // Original ContainerResearchTable uses the same button IDs.
                minecraft.gameMode.handleInventoryButtonClick(
                        menu.containerId, menu.inspiration() <= 0 ? 7 : 9);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
