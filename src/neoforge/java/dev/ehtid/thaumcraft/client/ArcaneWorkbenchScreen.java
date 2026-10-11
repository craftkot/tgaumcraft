package dev.ehtid.thaumcraft.client;

import dev.ehtid.thaumcraft.menu.ArcaneWorkbenchMenu;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/** FIRSTSTEPS arcane workbench screen; legacy slot geometry is owned by the menu. */
public final class ArcaneWorkbenchScreen extends AbstractContainerScreen<ArcaneWorkbenchMenu> {
    private static final int[][] ASPECT_LOCS = {
            {72, 21}, {24, 43}, {24, 102}, {72, 124}, {120, 102}, {120, 43}
    };
    // ShardType metadata order in BETA26: AIR, FIRE, WATER, EARTH, ORDER, ENTROPY.
    private static final int[] ASPECT_COLORS = {
            16777086, 16734721, 3986684, 5685248, 14013676, 4210752
    };

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/gui/arcaneworkbench.png");

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
        // Original workbench used 0.5x letters on a busy green dial: those
        // pixels were too small for non-English translations and GUI scales.
        // Keep all original vis/cost data, but draw with full vanilla glyphs.
        ThaumcraftReadableText.line(graphics, font, title, 16, 5,
                ThaumcraftReadableText.TEXT);
        ThaumcraftReadableText.line(graphics, font, playerInventoryTitle, 16, 139,
                ThaumcraftReadableText.TEXT);

        int available = menu.availableVis();
        int cost = menu.displayedVisCost();
        ThaumcraftReadableText.panel(graphics, 9, 112, 173, 24);
        Component availableText = Component.literal(available + " ")
                .append(Component.translatable("workbench.available"));
        int availableColor = available < cost
                ? ThaumcraftReadableText.BAD_TEXT : ThaumcraftReadableText.GREEN_TEXT;
        ThaumcraftReadableText.centeredLabel(graphics, font, availableText,
                15, 115, 161, availableColor);
        if (cost > 0) {
            Component costText = Component.literal(cost + " ")
                    .append(Component.translatable("workbench.cost"));
            ThaumcraftReadableText.centeredLabel(graphics, font, costText,
                    15, 125, 161, ThaumcraftReadableText.MUTED_TEXT);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // Exact BETA26 190x234 GUI background, preserved at its original 256x256 atlas size.
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
        renderCrystalRequirements(graphics, partialTick);
    }

    private void renderCrystalRequirements(GuiGraphics graphics, float partialTick) {
        if (menu.displayedVisCost() <= 0) return;
        int crystalMask = menu.requiredCrystalMask();
        if (crystalMask == 0) return;

        int ticks = minecraft != null && minecraft.player != null ? minecraft.player.tickCount : 0;
        for (int i = 0; i < ASPECT_LOCS.length; i++) {
            // BETA26 only highlights aspects actually required by the
            // currently matched recipe, not all six primal crystals.
            if ((crystalMask & (1 << i)) == 0) continue;
            int color = ASPECT_COLORS[i];
            float red = ((color >> 16) & 0xFF) / 255.0F;
            float green = ((color >> 8) & 0xFF) / 255.0F;
            float blue = (color & 0xFF) / 255.0F;

            graphics.pose().pushPose();
            graphics.pose().translate(leftPos + ASPECT_LOCS[i][0] + 7.5F,
                    topPos + ASPECT_LOCS[i][1] + 8.0F, 0.0F);
            graphics.pose().mulPose(Axis.ZP.rotationDegrees(i * 60.0F + (ticks % 360) + partialTick));
            graphics.pose().scale(0.5F, 0.5F, 1.0F);
            graphics.setColor(red, green, blue, 0.33F);
            graphics.blit(TEXTURE, -32, -32, 192, 0, 64, 64, 256, 256);
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            graphics.pose().popPose();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
