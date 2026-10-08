package dev.ehtid.thaumcraft.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import dev.ehtid.thaumcraft.network.ClientAuraState;
import dev.ehtid.thaumcraft.port.core.ThaumometerHudMath;
import dev.ehtid.thaumcraft.registry.ThaumcraftItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.text.DecimalFormat;

/**
 * BETA26 Thaumometer aura dial: same 525 normalization, overflow rescale,
 * atlas coordinates, colors, shift-only values and left-edge placement.
 */
public final class ThaumometerHudRenderer {
    private static final ResourceLocation HUD =
            ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/gui/hud.png");
    private static final DecimalFormat VALUE_FORMAT = new DecimalFormat("#######.#");

    private ThaumometerHudRenderer() {}

    public static void render(GuiGraphics graphics, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) return;
        if (!minecraft.player.getMainHandItem().is(ThaumcraftItems.THAUMOMETER.get())
                && !minecraft.player.getOffhandItem().is(ThaumcraftItems.THAUMOMETER.get())) return;

        ClientAuraState.Snapshot aura = ClientAuraState.current();
        if (aura == null) return;

        ThaumometerHudMath.State state = ThaumometerHudMath.calculate(aura.base(), aura.vis(), aura.flux());
        float ticks = minecraft.player.tickCount + partialTick;

        graphics.pose().pushPose();
        graphics.pose().translate(2.0F, 0.0F, 0.0F);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        if (state.vis() > 0.0F) {
            drawScaledRotated(graphics, 5.0F, state.visTop(), 88, 56, 8, 64,
                    1.0F, state.vis(), 0.7F, 0.4F, 0.9F, 1.0F);

            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            drawRotatedCrop(graphics, 5.0F, state.visTop(), 96,
                    56 + ((int) ticks & 63), 8, Math.max(1, Math.round(state.vis() * 64.0F)),
                    1.0F, 1.0F, 1.0F, 0.5F);
            RenderSystem.defaultBlendFunc();

            if (minecraft.player.isShiftKeyDown())
                drawHalfScaleText(graphics, VALUE_FORMAT.format(aura.vis()), 16.0F, state.visTop(), 15641343);
        }

        if (state.flux() > 0.0F) {
            drawScaledRotated(graphics, 5.0F, state.fluxTop(), 88, 56, 8, 64,
                    1.0F, state.flux(), 0.25F, 0.1F, 0.3F, 1.0F);

            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            int v = 120 - (((int) (ticks / 3.0F)) & 63);
            drawRotatedCrop(graphics, 5.0F, state.fluxTop(), 104, v, 8,
                    Math.max(1, Math.round(state.flux() * 64.0F)),
                    0.7F, 0.4F, 1.0F, 0.5F);
            RenderSystem.defaultBlendFunc();

            if (minecraft.player.isShiftKeyDown())
                drawHalfScaleText(graphics, VALUE_FORMAT.format(aura.flux()), 16.0F, state.fluxTop() - 4.0F, 11145659);
        }

        drawRotatedCrop(graphics, 1.0F, 1.0F, 72, 48, 16, 80,
                1.0F, 1.0F, 1.0F, 1.0F);
        drawRotatedCrop(graphics, 2.0F, state.baseTop(), 117, 61, 14, 5,
                1.0F, 1.0F, 1.0F, 1.0F);

        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.defaultBlendFunc();
        graphics.pose().popPose();
    }

    private static void drawScaledRotated(GuiGraphics graphics, float x, float y,
                                          int u, int v, int w, int h,
                                          float sx, float sy,
                                          float r, float g, float b, float a) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(sx, sy, 1.0F);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(-90.0F));
        graphics.setColor(r, g, b, a);
        graphics.blit(HUD, 0, 0, u, v, w, h, 256, 256);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        graphics.pose().popPose();
    }

    private static void drawRotatedCrop(GuiGraphics graphics, float x, float y,
                                        int u, int v, int w, int h,
                                        float r, float g, float b, float a) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(-90.0F));
        graphics.setColor(r, g, b, a);
        graphics.blit(HUD, 0, 0, u, v, w, h, 256, 256);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        graphics.pose().popPose();
    }

    private static void drawHalfScaleText(GuiGraphics graphics, String text, float x, float y, int color) {
        Minecraft minecraft = Minecraft.getInstance();
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(0.5F, 0.5F, 1.0F);
        graphics.drawString(minecraft.font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }
}
