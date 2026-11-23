package dev.ehtid.thaumcraft.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.entity.WispEntity;
import dev.ehtid.thaumcraft.port.core.visual.LegacyWispRenderAtlas;
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
 * BETA26 RenderWisp billboard layers. The two particle-sheet frames use the
 * 64x64 particles atlas; the aspect-coloured third layer uses the original
 * 32x32 auranodes atlas. All three retain SRC_ALPHA/ONE additive blending.
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
        private static final ResourceLocation NODE_ATLAS =
                ResourceLocation.fromNamespaceAndPath(
                        ThaumcraftPort.MOD_ID, "textures/misc/auranodes.png");
        private static final RenderType PARTICLE_LAYER =
                WispRenderTypes.additive("thaumcraft:wisp_particle_additive", PARTICLE_ATLAS);
        private static final RenderType NODE_LAYER =
                WispRenderTypes.additive("thaumcraft:wisp_node_additive", NODE_ATLAS);

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
            Matrix4f matrix = poseStack.last().pose();
            VertexConsumer particles = buffers.getBuffer(PARTICLE_LAYER);
            renderQuad(particles, matrix, 0.4F,
                    LegacyWispRenderAtlas.animatedFrame(LegacyWispRenderAtlas.OUTER_BASE_FRAME, entity.tickCount),
                    LegacyWispRenderAtlas.PARTICLE_GRID, 0xFFFFFF, 255);
            renderQuad(particles, matrix, 0.75F,
                    LegacyWispRenderAtlas.animatedFrame(LegacyWispRenderAtlas.INNER_BASE_FRAME, entity.tickCount),
                    LegacyWispRenderAtlas.PARTICLE_GRID, 0xFFFFFF, 64);

            // BETA26 binds UtilsFX.nodeTexture before drawing this third layer.
            VertexConsumer nodes = buffers.getBuffer(NODE_LAYER);
            renderQuad(nodes, matrix, 0.75F,
                    LegacyWispRenderAtlas.animatedFrame(LegacyWispRenderAtlas.ASPECT_BASE_FRAME, entity.tickCount),
                    LegacyWispRenderAtlas.NODE_GRID, entity.getAspectColor(), 128);
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
                        // RenderWisp/this billboard is centered at entity position, not its eye.
                        Vec3 wispRenderOrigin = new Vec3(entity.getX(), entity.getY(), entity.getZ());
                        Vec3 toWispRenderOrigin = wispRenderOrigin.subtract(playerEye);
                        Vec3 toWispEye = wispEye.subtract(playerEye);
                        double aimDot = toWispRenderOrigin.lengthSqr() < 1.0E-12D
                                ? 1.0D
                                : player.getViewVector(partialTick).dot(toWispRenderOrigin.normalize());
                        double eyeAimDot = toWispEye.lengthSqr() < 1.0E-12D
                                ? 1.0D
                                : player.getViewVector(partialTick).dot(toWispEye.normalize());
                        System.out.println("TC_SMOKE_WISP_CAMERA=YES id=" + entity.getId()
                                + " aimTarget=render_origin"
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
                                + " aimDot=" + aimDot
                                + " eyeAimDot=" + eyeAimDot);
                    }
                    System.out.println("TC_SMOKE_WISP_RENDERED=YES id=" + entity.getId()
                            + " aspect=" + entity.getAspectType()
                            + " tick=" + entity.tickCount);
                    WispRenderSmokeProbe.recordRendered(entity.getId(), entity.tickCount);
                }
            }
        }

        private static void renderQuad(
                VertexConsumer consumer,
                Matrix4f matrix,
                float scale,
                int frame,
                int gridSize,
                int color,
                int alpha
        ) {
            LegacyWispRenderAtlas.Uv uv = LegacyWispRenderAtlas.frameUv(frame, gridSize);
            int red = (color >> 16) & 0xFF;
            int green = (color >> 8) & 0xFF;
            int blue = color & 0xFF;

            vertex(consumer, matrix, -scale, -scale, uv.u1(), uv.v1(), red, green, blue, alpha);
            vertex(consumer, matrix, -scale, scale, uv.u1(), uv.v0(), red, green, blue, alpha);
            vertex(consumer, matrix, scale, scale, uv.u0(), uv.v0(), red, green, blue, alpha);
            vertex(consumer, matrix, scale, -scale, uv.u0(), uv.v1(), red, green, blue, alpha);
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

        private static RenderType additive(String name, ResourceLocation texture) {
            return RenderType.create(
                    name,
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
