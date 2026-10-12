package dev.ehtid.thaumcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.ehtid.thaumcraft.blockentity.PedestalBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * BETA26 TilePedestalRenderer: one displayed stack with count=1,
 * centered at x/z=.5 y=.75, scale 1.25 and rotation 1 deg per client tick.
 */
public final class PedestalItemRenderer implements BlockEntityRenderer<PedestalBlockEntity> {
    public PedestalItemRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(PedestalBlockEntity pedestal, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        ItemStack displayed = pedestal.displayedItem();
        if (displayed.isEmpty()) return;
        pose.pushPose();
        pose.translate(0.5, 0.75, 0.5);
        pose.scale(1.25F, 1.25F, 1.25F);
        float age = pedestal.getLevel() == null ? 0F
                : (pedestal.getLevel().getGameTime() + partialTick) % 360F;
        pose.mulPose(Axis.YP.rotationDegrees(age));
        Minecraft.getInstance().getItemRenderer().renderStatic(
                displayed, ItemDisplayContext.GROUND, light, overlay, pose,
                buffers, pedestal.getLevel(), 0);
        pose.popPose();
    }
}
