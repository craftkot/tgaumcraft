package dev.ehtid.thaumcraft.client;

import dev.ehtid.thaumcraft.menu.ResearchTableMenu;
import dev.ehtid.thaumcraft.registry.ThaumcraftItems;
import net.minecraft.world.item.Items;
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

    // The original sprites/buttons are 49x11 with HALF-SIZE glyphs, which
    // made Russian translations unreadable. Expand ONLY the click targets and
    // text backgrounds; original BETA26 atlas and card logic remain intact.
    private static final int CREATE_X = 116, CREATE_Y = 22;
    private static final int COMPLETE_X = 116, COMPLETE_Y = 96;
    private static final int SCRAP_X = 116, SCRAP_Y = 155;
    private static final int BUTTON_WIDTH = 132, BUTTON_HEIGHT = 18;

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
        } else if (canStartTheory()) {
            renderOriginalButton(graphics, CREATE_X, CREATE_Y);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // Vanilla AbstractContainerScreen paints both labels DARK GREY onto
        // the original DARK WOOD, which effectively makes them invisible.
        // Use the current language's actual vanilla glyphs at full scale.
        ThaumcraftReadableText.line(graphics, font, title, 80, 8,
                ThaumcraftReadableText.TEXT);
        ThaumcraftReadableText.line(graphics, font, playerInventoryTitle, 78, 177,
                ThaumcraftReadableText.TEXT);

        ThaumcraftReadableText.panel(graphics, 75, 44, 175, 43);
        if (menu.hasSession()) {
            Component inspiration = Component.translatable(
                    "research_table.inspiration",
                    menu.inspiration(), menu.inspirationStart());
            ThaumcraftReadableText.wrapped(graphics, font, inspiration,
                    81, 50, 162, 3, ThaumcraftReadableText.TEXT);
            if (menu.inspiration() <= 0) {
                renderButtonText(graphics, COMPLETE_X, COMPLETE_Y,
                        Component.translatable("button.complete.theory"));
            } else {
                renderButtonText(graphics, SCRAP_X, SCRAP_Y,
                        Component.translatable("button.scrap.theory"));
            }
        } else {
            ThaumcraftReadableText.wrapped(graphics, font,
                    Component.translatable("research_table.awaiting_theory"),
                    81, 50, 162, 3, ThaumcraftReadableText.MUTED_TEXT);
            if (canStartTheory()) {
                renderButtonText(graphics, CREATE_X, CREATE_Y,
                        Component.translatable("button.create.theory"));
            }
        }
    }

    private boolean canStartTheory() {
        var tools = menu.getSlot(0).getItem();
        var paper = menu.getSlot(1).getItem();
        return !menu.hasSession()
                && tools.is(ThaumcraftItems.SCRIBING_TOOLS.get())
                && tools.isDamageableItem()
                && tools.getDamageValue() < tools.getMaxDamage()
                && paper.is(Items.PAPER) && !paper.isEmpty();
    }

    private void renderOriginalButton(GuiGraphics graphics, int x, int y) {
        // Keep the same BETA26 green palette and original sprite as trim,
        // but provide 116x18 of legible space rather than a tiny 49x11 hitbox.
        int xx = leftPos + x;
        int yy = topPos + y;
        graphics.fill(xx, yy, xx + BUTTON_WIDTH, yy + BUTTON_HEIGHT, 0xD887795E);
        graphics.fill(xx + 1, yy + 1,
                xx + BUTTON_WIDTH - 1, yy + BUTTON_HEIGHT - 1, 0xF02A4535);
        graphics.blit(BUTTON_ATLAS, xx + 3, yy + 3,
                37, 66, 9, 11, 256, 256);
    }

    private void renderButtonText(GuiGraphics graphics, int x, int y, Component text) {
        ThaumcraftReadableText.centeredLabel(
                graphics, font, text, x + 11, y + 5,
                BUTTON_WIDTH - 16, ThaumcraftReadableText.GREEN_TEXT);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
        if (mouseButton == 0 && !menu.hasSession() && canStartTheory()
                && minecraft != null && minecraft.gameMode != null
                && mouseX >= leftPos + CREATE_X
                && mouseX < leftPos + CREATE_X + BUTTON_WIDTH
                && mouseY >= topPos + CREATE_Y
                && mouseY < topPos + CREATE_Y + BUTTON_HEIGHT) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 1);
            return true;
        }
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
        // Full unshortened translation on hover, even if a long modded
        // language pack cannot fit the enlarged button's single line.
        Component button = null;
        int x, y;
        if (!menu.hasSession() && canStartTheory()) {
            x = CREATE_X;
            y = CREATE_Y;
            button = Component.translatable("button.create.theory");
        } else if (menu.hasSession()) {
            boolean complete = menu.inspiration() <= 0;
            x = complete ? COMPLETE_X : SCRAP_X;
            y = complete ? COMPLETE_Y : SCRAP_Y;
            button = Component.translatable(
                    complete ? "button.complete.theory" : "button.scrap.theory");
        } else {
            return;
        }
        if (mouseX >= leftPos + x && mouseX < leftPos + x + BUTTON_WIDTH
                && mouseY >= topPos + y && mouseY < topPos + y + BUTTON_HEIGHT) {
            graphics.renderTooltip(font, button, mouseX, mouseY);
        }
    }
}
