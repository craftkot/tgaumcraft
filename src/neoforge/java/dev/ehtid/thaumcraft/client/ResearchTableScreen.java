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
    private static final int KEEP_X = 116, KEEP_Y = 121;
    private static final int BUTTON_WIDTH = 132, BUTTON_HEIGHT = 18;
    private static final int AID_FIRST_X = 76, AID_FIRST_Y = 95;
    private static final int AID_COL_STRIDE = 87, AID_ROW_STRIDE = 11;
    private static final int AID_WIDTH = 85, AID_HEIGHT = 11;
    private static final int AID_COUNT = 14;

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
                if (menu.hasChosenTheoryCard())
                    renderOriginalButton(graphics, KEEP_X, KEEP_Y);
            }
        } else if (canStartTheory()) {
            renderOriginalButton(graphics, CREATE_X, CREATE_Y);
        }
        renderAidCheckboxes(graphics);
    }

    /** An aid is selectable only after its real world object was found by the server. */
    private void renderAidCheckboxes(GuiGraphics graphics) {
        if (menu.hasSession()) return;
        for (int i = 0; i < AID_COUNT; i++) {
            if (!menu.aidAvailable(i) && !menu.aidSelected(i)) continue;
            int x = leftPos + aidX(i), y = topPos + aidY(i);
            int fill = !menu.aidAvailable(i) ? 0xEC69312C
                    : (menu.aidSelected(i) ? 0xEC3C6546 : 0xE3323129);
            graphics.fill(x, y, x + AID_WIDTH, y + AID_HEIGHT, 0xF3AA9A71);
            graphics.fill(x + 1, y + 1,
                    x + AID_WIDTH - 1, y + AID_HEIGHT - 1, fill);
        }
    }

    private static int aidX(int index) {
        return AID_FIRST_X + (index / 7) * AID_COL_STRIDE;
    }

    private static int aidY(int index) {
        return AID_FIRST_Y + (index % 7) * AID_ROW_STRIDE;
    }

    private static boolean withinAid(int index, double mouseX, double mouseY,
                                     int left, int top) {
        return mouseX >= left + aidX(index)
                && mouseX < left + aidX(index) + AID_WIDTH
                && mouseY >= top + aidY(index)
                && mouseY < top + aidY(index) + AID_HEIGHT;
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
            graphics.drawString(font, Component.translatable("research_table.aids"),
                    80, 86, ThaumcraftReadableText.TEXT, false);
            for (int i = 0; i < AID_COUNT; i++) {
                if (!menu.aidAvailable(i) && !menu.aidSelected(i)) continue;
                String label = Component.translatable("research_table.aid." + i).getString();
                String visible = font.plainSubstrByWidth(label, AID_WIDTH - 11);
                graphics.drawString(font, (menu.aidSelected(i) ? "✓" : "+")
                                + visible, aidX(i) + 2, aidY(i) + 1,
                        ThaumcraftReadableText.GREEN_TEXT, false);
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
        if (mouseButton == 0 && !menu.hasSession() && minecraft != null
                && minecraft.gameMode != null) {
            for (int i = 0; i < AID_COUNT; i++) {
                if ((menu.aidAvailable(i) || menu.aidSelected(i))
                        && withinAid(i, mouseX, mouseY, leftPos, topPos)) {
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 10 + i);
                    return true;
                }
            }
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
        if (!menu.hasSession()) {
            for (int i = 0; i < AID_COUNT; i++) {
                if (menu.aidAvailable(i)
                        && withinAid(i, mouseX, mouseY, leftPos, topPos)) {
                    graphics.renderTooltip(font,
                            Component.translatable("research_table.aid." + i),
                            mouseX, mouseY);
                    return;
                }
            }
        }
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
