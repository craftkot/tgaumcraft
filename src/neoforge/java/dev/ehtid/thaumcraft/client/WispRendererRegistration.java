package dev.ehtid.thaumcraft.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.entity.WispEntity;
import dev.ehtid.thaumcraft.registry.WispRegistration;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.joml.Matrix4f;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * BETA26 RenderWisp billboard layers. The particle-sheet frames and aspect tint
 * are retained; all layers use the source's SRC_ALPHA/ONE additive blend and
 * depth-write-off state. The original auranodes sheet is still missing, so the
 * aspect-tinted third layer remains a documented resource-parity gap.
 */
@EventBusSubscriber(modid = ThaumcraftPort.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class WispRendererRegistration {
    private static final boolean SMOKE_PROBE = "1".equals(System.getenv("THAUMCRAFT_SMOKE"));
    // Entity render callbacks run on the client render thread. Weak keys keep this
    // smoke-only cadence tracker from retaining removed entities.
    private static final Map<WispEntity, Integer> SMOKE_RENDERED_TICK = new WeakHashMap<>();

    private WispRendererRegistration() {}

    @SubscribeEvent
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(WispRegistration.get(), WispRenderer::new);
    }

    private static final class WispRenderer extends EntityRenderer<WispEntity> {
        private static final ResourceLocation PARTICLE_ATLAS =
                ResourceLocation.fromNamespaceAndPath(
                        ThaumcraftPort.MOD_ID, "textures/particle/particles.png");
        private static final RenderType PARTICLE_LAYER = WispRenderTypes.additive(PARTICLE_ATLAS);

        private WispRenderer(EntityRendererProvider.Context context) {
            super(context);
            shadowRadius = 0.0F;
        }

        @Override
        public void render(
                WispEntity entity,
                float entityYaw,
                float partialTick,
                PoseStack poseStack,
                MultiBufferSource buffers,
                int packedLight
        ) {
            if (entity.getHealth() <= 0.0F) {
                return;
            }

            poseStack.pushPose();
            poseStack.mulPose(entityRenderDispatcher.cameraOrientation());
            VertexConsumer consumer = buffers.getBuffer(PARTICLE_LAYER);
            Matrix4f matrix = poseStack.last().pose();
            int frame = entity.tickCount % 16;
            renderQuad(consumer, matrix, 0.4F, 512 + frame, 0xFFFFFF, 255);
            renderQuad(consumer, matrix, 0.75F, 320 + frame, 0xFFFFFF, 64);
            renderQuad(consumer, matrix, 0.75F, 800 + frame, entity.getAspectColor(), 128);
            poseStack.popPose();

            super.render(entity, entityYaw, partialTick, poseStack, buffers, LightTexture.FULL_BRIGHT);
            if (SMOKE_PROBE) {
                Integer previousTick = SMOKE_RENDERED_TICK.put(entity, entity.tickCount);
                if (previousTick == null || previousTick.intValue() != entity.tickCount) {
                    // Emit camera geometry before the render marker so a smoke-driver
                    // that observes the marker is guaranteed to see this same-tick telemetry.
                    var player = Minecraft.getInstance().player;
                    if (player != null) {
                        Vec3 playerEye = player.getEyePosition(partialTick);
                        Vec3 wispEye = entity.getEyePosition(partialTick);
                        Vec3 toWisp = wispEye.subtract(playerEye);
                        double aimDot = toWisp.lengthSqr() < 1.0E-12D
                                ? 1.0D
                                : player.getViewVector(partialTick).dot(toWisp.normalize());
                        System.out.println("TC_SMOKE_WISP_CAMERA=YES id=" + entity.getId()
                                + " tick=" + entity.tickCount
                                + " playerX=" + player.getX()
                                + " playerY=" + player.getY()
                                + " playerZ=" + player.getZ()
                                + " playerYaw=" + player.getYRot()
                                + " playerPitch=" + player.getXRot()
                                + " playerEyeX=" + playerEye.x
                                + " playerEyeY=" + playerEye.y
                                + " playerEyeZ=" + playerEye.z
                                + " wispX=" + entity.getX()
                                + " wispY=" + entity.getY()
                                + " wispZ=" + entity.getZ()
                                + " wispEyeX=" + wispEye.x
                                + " wispEyeY=" + wispEye.y
                                + " wispEyeZ=" + wispEye.z
                                + " aimDot=" + aimDot);
                    }
                    System.out.println("TC_SMOKE_WISP_RENDERED=YES id=" + entity.getId()
                            + " aspect=" + entity.getAspectType()
                            + " tick=" + entity.tickCount);
                }
            }
        }

        private static void renderQuad(
                VertexConsumer consumer,
                Matrix4f matrix,
                float scale,
                int frame,
                int color,
                int alpha
        ) {
            float u0 = (frame % 64) / 64.0F;
            float u1 = u0 + 1.0F / 64.0F;
            float v0 = (frame / 64) / 64.0F;
            float v1 = v0 + 1.0F / 64.0F;
            int red = (color >> 16) & 0xFF;
            int green = (color >> 8) & 0xFF;
            int blue = color & 0xFF;

            vertex(consumer, matrix, -scale, -scale, u1, v1, red, green, blue, alpha);
            vertex(consumer, matrix, -scale, scale, u1, v0, red, green, blue, alpha);
            vertex(consumer, matrix, scale, scale, u0, v0, red, green, blue, alpha);
            vertex(consumer, matrix, scale, -scale, u0, v1, red, green, blue, alpha);
        }

        private static void vertex(
                VertexConsumer consumer,
                Matrix4f matrix,
                float x,
                float y,
                float u,
                float v,
                int red,
                int green,
                int blue,
                int alpha
        ) {
            consumer.addVertex(matrix, x, y, 0.0F)
                    .setColor(red, green, blue, alpha)
                    .setUv(u, v)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(LightTexture.FULL_BRIGHT)
                    .setNormal(0.0F, 0.0F, 1.0F);
        }

        @Override
        public ResourceLocation getTextureLocation(WispEntity entity) {
            return PARTICLE_ATLAS;
        }
    /**
     * RenderWisp delegates to UtilsFX.renderFacingQuad with GL_SRC_ALPHA/GL_ONE
     * and depth writes disabled. LIGHTNING_TRANSPARENCY is NeoForge's matching
     * additive blend state; COLOR_WRITE preserves the source depth-mask behavior.
     */
    private static final class WispRenderTypes extends RenderStateShard {
        private static final ShaderStateShard ENTITY_TRANSLUCENT =
                new ShaderStateShard(GameRenderer::getRendertypeEntityTranslucentShader);

        private WispRenderTypes() {
            super("thaumcraft_wisp_render_types", () -> {}, () -> {});
        }

        private static RenderType additive(ResourceLocation texture) {
            return RenderType.create(
                    "thaumcraft:wisp_additive",
                    DefaultVertexFormat.NEW_ENTITY,
                    VertexFormat.Mode.QUADS,
                    256,
                    false,
                    false,
                    RenderType.CompositeState.builder()
                            .setShaderState(ENTITY_TRANSLUCENT)
                            .setTextureState(new TextureStateShard(texture, false, false))
                            .setTransparencyState(LIGHTNING_TRANSPARENCY)
                            .setDepthTestState(LEQUAL_DEPTH_TEST)
                            .setCullState(NO_CULL)
                            .setWriteMaskState(COLOR_WRITE)
                            .setLightmapState(LIGHTMAP)
                            .setOverlayState(OVERLAY)
                            .setOutputState(MAIN_TARGET)
                            .createCompositeState(false)
            );
        }
    }

    }
}
