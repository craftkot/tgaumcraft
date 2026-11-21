package dev.ehtid.thaumcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.blockentity.EssentiaJarBlockEntity;
import dev.ehtid.thaumcraft.port.core.AspectData;
import dev.ehtid.thaumcraft.port.core.AspectRegistry;
import dev.ehtid.thaumcraft.port.core.LegacyEssentiaJarLabelRenderLogic;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** BETA26 TileJarRenderer aspectFilter label and monochrome aspect glyph. */
public final class EssentiaJarLabelRenderer {
    private static final ResourceLocation LABEL_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            ThaumcraftPort.MOD_ID, "textures/models/label.png");
    private static final AspectRegistry ASPECTS = new AspectRegistry();
    private static final boolean SMOKE_PROBE = Boolean.getBoolean("thaumcraft.jarLiquidSmoke");
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

    private EssentiaJarLabelRenderer() {}

    public static void render(EssentiaJarBlockEntity jar, PoseStack poseStack,
                              MultiBufferSource buffers, int packedOverlay) {
        String aspectFilter = jar.snapshot().filter();
        if (!LegacyEssentiaJarLabelRenderLogic.shouldRender(aspectFilter)) return;

        BlockPos pos = jar.getBlockPos();
        int facing = jar.facing();
        int facingRotation = LegacyEssentiaJarLabelRenderLogic.facingRotationDegrees(facing);
        boolean crookedLabels = ThaumcraftClientConfig.CROOKED_JAR_LABELS.get();
        int crookedRotation = LegacyEssentiaJarLabelRenderLogic.crookedRotationDegrees(
                crookedLabels, aspectFilter, pos.getX(), facing);
        AspectData aspect = ASPECTS.get(aspectFilter);

        // TileJarRenderer's outer transform: block center, +0.01 Y, then X=180.
        poseStack.pushPose();
        poseStack.translate(0.5D, 0.01D, 0.5D);
        poseStack.mulPose(Axis.XP.rotationDegrees(180.0F));
        if (facingRotation != 0) {
            poseStack.mulPose(Axis.YP.rotationDegrees(facingRotation));
        }

        // BETA26 applies the same crooked rotation to both the paper quad and the aspect glyph.
        poseStack.pushPose();
        poseStack.translate(0.0D,
                LegacyEssentiaJarLabelRenderLogic.LABEL_CENTER_Y,
                LegacyEssentiaJarLabelRenderLogic.LABEL_CENTER_Z);
        poseStack.mulPose(Axis.ZP.rotationDegrees(crookedRotation));
        drawLabelBackground(buffers, poseStack, packedOverlay);
        poseStack.popPose();

        // UtilsFX.drawTag(-8,-8,aspect): 16x16 quad, 0.021 scale, Y=180,
        // dark monochrome tint and 0.8 alpha. Unknown custom tags keep the paper label.
        if (aspect != null) {
            ResourceLocation aspectTexture = ResourceLocation.fromNamespaceAndPath(
                    ThaumcraftPort.MOD_ID, "textures/aspects/" + aspect.tag() + ".png");
            poseStack.pushPose();
            poseStack.translate(0.0D,
                    LegacyEssentiaJarLabelRenderLogic.LABEL_CENTER_Y,
                    LegacyEssentiaJarLabelRenderLogic.ICON_CENTER_Z);
            poseStack.mulPose(Axis.ZP.rotationDegrees(crookedRotation));
            float scale = LegacyEssentiaJarLabelRenderLogic.ICON_SCALE;
            poseStack.scale(scale, scale, scale);
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
            drawAspectIcon(buffers, poseStack, packedOverlay, aspectTexture);
            poseStack.popPose();
        }
        poseStack.popPose();

        if (SMOKE_PROBE && REPORTED.add("label:" + pos.asLong() + ":" + aspectFilter + ":" + facing)) {
            System.out.printf(Locale.ROOT,
                    "TC_SMOKE_JAR_LABEL_RENDERED=YES pos=%d,%d,%d aspect=%s facing=%d crooked=%d texture=%s%n",
                    pos.getX(), pos.getY(), pos.getZ(), aspectFilter, facing, crookedRotation, LABEL_TEXTURE);
            if (aspect != null) {
                System.out.printf(Locale.ROOT,
                        "TC_SMOKE_JAR_LABEL_ICON_RENDERED=YES pos=%d,%d,%d aspect=%s crooked=%d texture=%s%n",
                        pos.getX(), pos.getY(), pos.getZ(), aspectFilter, crookedRotation,
                        ResourceLocation.fromNamespaceAndPath(
                                ThaumcraftPort.MOD_ID, "textures/aspects/" + aspect.tag() + ".png"));
            }
        }
    }

    private static void drawLabelBackground(MultiBufferSource buffers, PoseStack poseStack, int overlay) {
        VertexConsumer out = buffers.getBuffer(RenderType.entityTranslucent(LABEL_TEXTURE));
        float half = LegacyEssentiaJarLabelRenderLogic.LABEL_HALF_SIZE;
        vertex(out, poseStack, -half, half, 0.0F, 1.0F, 1.0F, overlay, 255, 255, 255, 255);
        vertex(out, poseStack, half, half, 0.0F, 1.0F, 0.0F, overlay, 255, 255, 255, 255);
        vertex(out, poseStack, half, -half, 0.0F, 0.0F, 0.0F, overlay, 255, 255, 255, 255);
        vertex(out, poseStack, -half, -half, 0.0F, 0.0F, 1.0F, overlay, 255, 255, 255, 255);
    }

    private static void drawAspectIcon(MultiBufferSource buffers, PoseStack poseStack,
                                       int overlay, ResourceLocation texture) {
        VertexConsumer out = buffers.getBuffer(RenderType.entityTranslucent(texture));
        float half = LegacyEssentiaJarLabelRenderLogic.ICON_HALF_SIZE;
        int tint = LegacyEssentiaJarLabelRenderLogic.ICON_TINT_RGB;
        int red = (tint >>> 16) & 0xFF;
        int green = (tint >>> 8) & 0xFF;
        int blue = tint & 0xFF;
        int alpha = LegacyEssentiaJarLabelRenderLogic.ICON_ALPHA;
        vertex(out, poseStack, -half, half, 0.0F, 0.0F, 1.0F, overlay, red, green, blue, alpha);
        vertex(out, poseStack, half, half, 0.0F, 1.0F, 1.0F, overlay, red, green, blue, alpha);
        vertex(out, poseStack, half, -half, 0.0F, 1.0F, 0.0F, overlay, red, green, blue, alpha);
        vertex(out, poseStack, -half, -half, 0.0F, 0.0F, 0.0F, overlay, red, green, blue, alpha);
    }

    private static void vertex(VertexConsumer out, PoseStack poseStack,
                               float x, float y, float z, float u, float v,
                               int overlay, int red, int green, int blue, int alpha) {
        out.addVertex(poseStack.last(), x, y, z)
                .setColor(red, green, blue, alpha)
                .setUv(u, v)
                .setOverlay(overlay)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(poseStack.last(), 0.0F, 0.0F, 1.0F);
    }
}
