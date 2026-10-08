package dev.ehtid.thaumcraft.client;

import dev.ehtid.thaumcraft.menu.ArcaneWorkbenchMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/** FIRSTSTEPS arcane workbench screen; legacy slot geometry is owned by the menu. */
public final class ArcaneWorkbenchScreen extends AbstractContainerScreen<ArcaneWorkbenchMenu> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");

    public ArcaneWorkbenchScreen(ArcaneWorkbenchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 190;
        imageHeight = 234;
        inventoryLabelX = 16;
        inventoryLabelY = 140;
        titleLabelX = 16;
        titleLabelY = 5;
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        int available = menu.availableVis();
        int cost = menu.displayedVisCost();

        String availableText = available + " " + Component.translatable("workbench.available").getString();
        int availableColor = available < cost ? 15625838 : 7237358;
        drawHalfScaleCentered(graphics, availableText, 168, 46, availableColor);

        if (cost > 0) {
            String costText = cost + " " + Component.translatable("workbench.cost").getString();
            drawHalfScaleCentered(graphics, costText, 168, 38, 12648447);
        }
    }

    private void drawHalfScaleCentered(GuiGraphics graphics, String text, int x, int y, int color) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(0.5F, 0.5F, 1.0F);
        graphics.drawString(font, text, -font.width(text) / 2, 0, color, false);
        graphics.pose().popPose();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // Temporary vanilla texture until the original BETA26 art is migrated.
        // Do not sample outside the 256x256 texture or stretch beyond its source bounds.
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
