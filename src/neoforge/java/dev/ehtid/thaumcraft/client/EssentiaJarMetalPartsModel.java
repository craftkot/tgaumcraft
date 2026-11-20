package dev.ehtid.thaumcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.port.core.LegacyEssentiaJarMetalPartsLogic;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * Exact BETA26 ModelJar.Lid and ModelJar.LidExtension cuboids.
 * The original 64x32 jarbrine texture and mirrored UV layout are retained.
 */
public final class EssentiaJarMetalPartsModel {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            ThaumcraftPort.MOD_ID, "textures/models/jarbrine.png");
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(ThaumcraftPort.MOD_ID, "jar_metal_parts"), "main");

    private final ModelPart lid;
    private final ModelPart extension;

    public EssentiaJarMetalPartsModel(ModelPart root) {
        lid = root.getChild("lid");
        extension = root.getChild("extension");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("lid",
                CubeListBuilder.create()
                        .texOffs(LegacyEssentiaJarMetalPartsLogic.LID_TEXTURE_U,
                                LegacyEssentiaJarMetalPartsLogic.LID_TEXTURE_V)
                        .mirror()
                        .addBox(LegacyEssentiaJarMetalPartsLogic.LID_X,
                                LegacyEssentiaJarMetalPartsLogic.LID_Y,
                                LegacyEssentiaJarMetalPartsLogic.LID_Z,
                                LegacyEssentiaJarMetalPartsLogic.LID_WIDTH,
                                LegacyEssentiaJarMetalPartsLogic.LID_HEIGHT,
                                LegacyEssentiaJarMetalPartsLogic.LID_DEPTH),
                PartPose.offset(0.0F, LegacyEssentiaJarMetalPartsLogic.LID_PIVOT_Y, 0.0F));
        root.addOrReplaceChild("extension",
                CubeListBuilder.create()
                        .texOffs(LegacyEssentiaJarMetalPartsLogic.EXTENSION_TEXTURE_U,
                                LegacyEssentiaJarMetalPartsLogic.EXTENSION_TEXTURE_V)
                        .mirror()
                        .addBox(LegacyEssentiaJarMetalPartsLogic.EXTENSION_X,
                                LegacyEssentiaJarMetalPartsLogic.EXTENSION_Y,
                                LegacyEssentiaJarMetalPartsLogic.EXTENSION_Z,
                                LegacyEssentiaJarMetalPartsLogic.EXTENSION_WIDTH,
                                LegacyEssentiaJarMetalPartsLogic.EXTENSION_HEIGHT,
                                LegacyEssentiaJarMetalPartsLogic.EXTENSION_DEPTH),
                PartPose.offset(0.0F, LegacyEssentiaJarMetalPartsLogic.EXTENSION_PIVOT_Y, 0.0F));
        return LayerDefinition.create(mesh,
                LegacyEssentiaJarMetalPartsLogic.TEXTURE_WIDTH,
                LegacyEssentiaJarMetalPartsLogic.TEXTURE_HEIGHT);
    }

    public void renderLidBrace(PoseStack poseStack, MultiBufferSource buffers, int packedOverlay) {
        render(lid, poseStack, buffers, packedOverlay,
                LegacyEssentiaJarMetalPartsLogic.BRACE_SCALE,
                LegacyEssentiaJarMetalPartsLogic.BRACE_SCALE,
                LegacyEssentiaJarMetalPartsLogic.BRACE_SCALE);
    }

    public void renderLidExtension(PoseStack poseStack, MultiBufferSource buffers, int packedOverlay) {
        float scale = LegacyEssentiaJarMetalPartsLogic.EXTENSION_SCALE_XZ;
        render(extension, poseStack, buffers, packedOverlay, scale, 1.0F, scale);
    }

    private static void render(ModelPart part, PoseStack poseStack, MultiBufferSource buffers,
                               int packedOverlay, float scaleX, float scaleY, float scaleZ) {
        poseStack.pushPose();
        poseStack.translate(0.5D, 0.01D, 0.5D);
        poseStack.mulPose(Axis.XP.rotationDegrees(180.0F));
        poseStack.scale(scaleX, scaleY, scaleZ);
        VertexConsumer vertices = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        // BETA26 disables GL_LIGHTING around both model parts.
        part.render(poseStack, vertices, LightTexture.FULL_BRIGHT, packedOverlay);
        poseStack.popPose();
    }
}
