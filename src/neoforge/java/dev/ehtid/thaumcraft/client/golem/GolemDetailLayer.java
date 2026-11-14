package dev.ehtid.thaumcraft.client.golem;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;

/** Neutral vanilla surface with own colored mechanical details; no imported golem textures. */
public final class GolemDetailLayer extends RenderLayer<ThaumcraftGolem, GolemModel> {
    private static final ResourceLocation INLAY =
            ResourceLocation.withDefaultNamespace("textures/block/white_concrete.png");

    public GolemDetailLayer(RenderLayerParent<ThaumcraftGolem, GolemModel> parent) { super(parent); }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, ThaumcraftGolem golem,
                       float limbSwing, float limbAmount, float partialTick, float age, float yaw, float pitch) {
        if (golem.isInvisible()) return;
        var buffer = buffers.getBuffer(RenderType.entityCutoutNoCull(INLAY));
        int overlay = LivingEntityRenderer.getOverlayCoords(golem, 0);
        GolemModel model = getParentModel();
        model.renderSurface(GolemModel.Surface.DARK, pose, buffer, light, overlay, 0xff34383d);
        model.renderSurface(GolemModel.Surface.METAL, pose, buffer, light, overlay, 0xffd5b772);
        model.renderSurface(GolemModel.Surface.EYES, pose, buffer, light, overlay, 0xff8be5dc);
        model.renderSurface(GolemModel.Surface.BRAIN, pose, buffer, light, overlay, 0xffd998a2);
        int color = Byte.toUnsignedInt(golem.golemColor());
        if (color >= 1 && color <= 16) {
            model.renderSurface(GolemModel.Surface.PAINT, pose, buffer, light, overlay,
                    DyeColor.byId(color - 1).getTextureDiffuseColor());
        }
    }
}
