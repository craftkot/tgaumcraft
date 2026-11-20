package dev.ehtid.thaumcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.ehtid.thaumcraft.blockentity.PedestalBlockEntity;
import dev.ehtid.thaumcraft.port.core.visual.LegacyPedestalRotationAngle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * BETA26 TilePedestalRenderer: one displayed stack with count=1,
 * centered at x/z=.5 y=.75, scale 1.25 and rotation from the render-view
 * entity's tick count (the 1.21 equivalent of ticksExisted).
 */
public final class PedestalItemRenderer implements BlockEntityRenderer<PedestalBlockEntity> {
    private static int lastRotationDebugBucket = Integer.MIN_VALUE;

    public PedestalItemRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(PedestalBlockEntity pedestal, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        ItemStack displayed = pedestal.displayedItem();
        if (displayed.isEmpty()) return;
        pose.pushPose();
        pose.translate(0.5, 0.75, 0.5);
        pose.scale(1.25F, 1.25F, 1.25F);
        var camera = Minecraft.getInstance().getCameraEntity();
        float age = camera == null ? 0F
                : LegacyPedestalRotationAngle.degrees(camera.tickCount, partialTick);
        pose.mulPose(Axis.YP.rotationDegrees(age));
        Minecraft.getInstance().getItemRenderer().renderStatic(
                displayed, ItemDisplayContext.GROUND, light, overlay, pose,
                buffers, pedestal.getLevel(), 0);

        // Opt-in evidence from the real render path. Disabled in normal play;
        // the marker is emitted only after renderStatic returned successfully.
        if (camera != null && Boolean.getBoolean("thaumcraft.pedestalRotationDebug")) {
            int bucket = camera.tickCount / 10;
            if (bucket != lastRotationDebugBucket) {
                lastRotationDebugBucket = bucket;
                System.out.println("TC_PEDESTAL_RENDER_ROTATION cameraTick=" + camera.tickCount
                        + " partialTick=" + partialTick
                        + " angle=" + age
                        + " pos=" + pedestal.getBlockPos().getX() + ","
                        + pedestal.getBlockPos().getY() + ","
                        + pedestal.getBlockPos().getZ());
            }
        }
        pose.popPose();
    }
}
