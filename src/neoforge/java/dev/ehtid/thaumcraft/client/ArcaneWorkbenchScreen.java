package dev.ehtid.thaumcraft.client;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import dev.ehtid.thaumcraft.menu.ArcaneWorkbenchMenu;
import dev.ehtid.thaumcraft.port.core.visual.WorkbenchVisPanelLayout;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/** FIRSTSTEPS arcane workbench screen; legacy slot geometry is owned by the menu. */
public final class ArcaneWorkbenchScreen extends AbstractContainerScreen<ArcaneWorkbenchMenu> {
    // ShardType metadata order in BETA26: AIR, FIRE, WATER, EARTH, ORDER, ENTROPY.
    private static final int[] ASPECT_COLORS = {
            16777086, 16734721, 3986684, 5685248, 14013676, 4210752
    };

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/gui/arcaneworkbench.png");

    private boolean smokeAuditEmitted;

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
        // The old full-width panel at y=112 covered the three lower BETA26
        // crystal sockets. Move the readout into the unused wooden strip at
        // the right edge; the six animated requirement icons remain visible.
        ThaumcraftReadableText.panel(graphics,
                WorkbenchVisPanelLayout.PANEL_X, WorkbenchVisPanelLayout.PANEL_Y,
                WorkbenchVisPanelLayout.PANEL_WIDTH, WorkbenchVisPanelLayout.PANEL_HEIGHT);
        Component availableText = Component.literal(available + " ")
                .append(Component.translatable("workbench.available"));
        int availableColor = available < cost
                ? ThaumcraftReadableText.BAD_TEXT : ThaumcraftReadableText.GREEN_TEXT;
        ThaumcraftReadableText.wrapped(graphics, font, availableText,
                WorkbenchVisPanelLayout.TEXT_X, WorkbenchVisPanelLayout.AVAILABLE_Y,
                WorkbenchVisPanelLayout.TEXT_WIDTH, WorkbenchVisPanelLayout.MAX_LINES,
                availableColor);
        Component costText = cost > 0
                ? Component.literal(cost + " ").append(Component.translatable("workbench.cost"))
                : Component.empty();
        if (cost > 0) {
            ThaumcraftReadableText.wrapped(graphics, font, costText,
                    WorkbenchVisPanelLayout.TEXT_X, WorkbenchVisPanelLayout.COST_Y,
                    WorkbenchVisPanelLayout.TEXT_WIDTH, WorkbenchVisPanelLayout.MAX_LINES,
                    ThaumcraftReadableText.MUTED_TEXT);
        }
        emitSmokeAudit(availableText, costText, available, cost);
    }

    private void emitSmokeAudit(Component availableText, Component costText, int available, int cost) {
        if (smokeAuditEmitted || !"1".equals(System.getenv("THAUMCRAFT_SMOKE"))) return;
        smokeAuditEmitted = true;
        System.out.println("TC_SMOKE_WORKBENCH_GUI_RENDERED=YES"
                + " title_b64=" + smokeBase64(title.getString())
                + " available_text_b64=" + smokeBase64(availableText.getString())
                + " cost_text_b64=" + smokeBase64(costText.getString())
                + " available_vis=" + available
                + " cost_vis=" + cost
                + " crystal_mask=" + menu.requiredCrystalMask()
                + " title_width=" + font.width(title)
                + " title_x=" + titleLabelX
                + " title_y=" + titleLabelY
                + " panel_x=" + WorkbenchVisPanelLayout.PANEL_X
                + " panel_y=" + WorkbenchVisPanelLayout.PANEL_Y
                + " panel_width=" + WorkbenchVisPanelLayout.PANEL_WIDTH
                + " panel_height=" + WorkbenchVisPanelLayout.PANEL_HEIGHT
                + " text_width=" + WorkbenchVisPanelLayout.TEXT_WIDTH
                + " max_lines=" + WorkbenchVisPanelLayout.MAX_LINES
                + " gui_width=" + imageWidth
                + " gui_height=" + imageHeight);
    }

    private static String smokeBase64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
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
        for (int i = 0; i < 6; i++) {
            // BETA26 only highlights aspects actually required by the
            // currently matched recipe, not all six primal crystals.
            if ((crystalMask & (1 << i)) == 0) continue;
            int color = ASPECT_COLORS[i];
            float red = ((color >> 16) & 0xFF) / 255.0F;
            float green = ((color >> 8) & 0xFF) / 255.0F;
            float blue = (color & 0xFF) / 255.0F;

            graphics.pose().pushPose();
            graphics.pose().translate(leftPos + WorkbenchVisPanelLayout.aspectX(i) + 7.5F,
                    topPos + WorkbenchVisPanelLayout.aspectY(i) + 8.0F, 0.0F);
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
