package dev.ehtid.thaumcraft.client;

import dev.ehtid.thaumcraft.entity.FluxRiftEntity;
import dev.ehtid.thaumcraft.port.core.LegacyFluxRiftGeometry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.RenderStateShard.ShaderStateShard;
import net.minecraft.client.renderer.RenderStateShard.TextureStateShard;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.joml.Matrix4f;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BETA26 seeded polycone, end-portal starfield shader and four source-matched
 * blend/depth passes. The original goggles-only first-layer depth bypass remains
 * unported; this renderer therefore does not claim complete visual parity.
 */
public final class FluxRiftRenderer extends EntityRenderer<FluxRiftEntity> {
    private static final int SIDES = 6;
    private static final ResourceLocation END_PORTAL_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/end_portal.png");
    private static final Set<Integer> SMOKE_RECORDED = ConcurrentHashMap.newKeySet();
    private static volatile ShaderInstance riftShaderInstance;

    public FluxRiftRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.0F;
    }

    static void setRiftShader(ShaderInstance shader) {
        riftShaderInstance = Objects.requireNonNull(shader, "Flux Rift shader registration returned null");
    }

    private static ShaderInstance getRiftShader() {
        return Objects.requireNonNull(riftShaderInstance, "Flux Rift shader has not been registered");
    }

    private static ShaderFrame updateShaderUniforms() {
        ShaderInstance shader = getRiftShader();
        Minecraft minecraft = Minecraft.getInstance();
        Entity camera = minecraft.getCameraEntity();
        Player player = minecraft.player;
        int time = camera == null ? 0 : camera.tickCount;
        float yaw = player == null ? 0.0F : (float) Math.toRadians(player.getYRot());
        float pitch = player == null ? 0.0F : (float) -Math.toRadians(player.getXRot());
        shader.safeGetUniform("time").set(time);
        shader.safeGetUniform("yaw").set(yaw);
        shader.safeGetUniform("pitch").set(pitch);
        return new ShaderFrame(time, yaw, pitch);
    }

    private record ShaderFrame(int time, float yaw, float pitch) {}

    @Override
    public void render(
            FluxRiftEntity entity,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight
    ) {
        List<LegacyFluxRiftGeometry.Point> points = entity.getGeometry();
        if (points.size() > 2) {
            float stability = Mth.clamp(1.0F - entity.getRiftStability() / 50.0F, 0.0F, 1.5F);
            ShaderFrame shaderFrame = updateShaderUniforms();

            boolean recordRenderPasses = smokeProbeEnabled() && SMOKE_RECORDED.add(entity.getId());
            if (recordRenderPasses) {
                System.out.println("TC_SMOKE_RIFT_SHADER_ACTIVE=YES time=" + shaderFrame.time()
                        + " yaw=" + shaderFrame.yaw() + " pitch=" + shaderFrame.pitch()
                        + " sampler=Sampler0 texture=" + END_PORTAL_TEXTURE);
            }
            for (int layer = 0; layer <= 3; layer++) {
                boolean additive = layer < 3;
                VertexConsumer vertices = buffers.getBuffer(
                        additive ? RiftRenderTypes.additive() : RiftRenderTypes.alphaCore());
                if (recordRenderPasses) {
                    System.out.println("TC_SMOKE_RIFT_RENDER_LAYER=PASS entityId=" + entity.getId()
                            + " layer=" + layer
                            + " blend=" + (additive ? "ADDITIVE" : "ALPHA")
                            + " depthWrite=" + (additive ? "OFF" : "ON"));
                }
                float layerScale = layer < 3 ? 1.25F + 0.5F * layer : 1.0F;
                // The BETA26 fragment shader emits the starfield's opaque alpha.
                int alpha = 255;
                Vector[] centers = new Vector[points.size()];
                float[] radii = new float[points.size()];

                for (int index = 0; index < points.size(); index++) {
                    LegacyFluxRiftGeometry.Point point = points.get(index);
                    double phase = entity.tickCount + partialTick;
                    if (index > points.size() / 2) phase -= index * 10.0D;
                    else if (index < points.size() / 2) phase += index * 10.0D;

                    centers[index] = new Vector(
                            point.x() + Math.sin(phase / 50.0D) * 0.1D * stability,
                            point.y() + Math.sin(phase / 60.0D) * 0.1D * stability,
                            point.z() + Math.sin(phase / 70.0D) * 0.1D * stability
                    );
                    double widthWave = 1.0D - Math.sin(phase / 8.0D) * 0.1D * stability;
                    radii[index] = (float) (point.width() * widthWave * layerScale);
                }

                Matrix4f matrix = poseStack.last().pose();
                for (int index = 0; index < centers.length - 1; index++) {
                    Vector tangent = centers[Math.min(index + 1, centers.length - 1)]
                            .subtract(centers[Math.max(index - 1, 0)]).normalize();
                    Vector reference = Math.abs(tangent.y()) > 0.9D
                            ? new Vector(1.0D, 0.0D, 0.0D)
                            : new Vector(0.0D, 1.0D, 0.0D);
                    Vector side = tangent.cross(reference).normalize();
                    Vector up = tangent.cross(side).normalize();

                    for (int sideIndex = 0; sideIndex < SIDES; sideIndex++) {
                        double a0 = Math.PI * 2.0D * sideIndex / SIDES;
                        double a1 = Math.PI * 2.0D * (sideIndex + 1) / SIDES;
                        Vector first0 = ring(centers[index], side, up, radii[index], a0);
                        Vector first1 = ring(centers[index], side, up, radii[index], a1);
                        Vector second1 = ring(centers[index + 1], side, up, radii[index + 1], a1);
                        Vector second0 = ring(centers[index + 1], side, up, radii[index + 1], a0);
                        vertex(vertices, matrix, first0, alpha);
                        vertex(vertices, matrix, first1, alpha);
                        vertex(vertices, matrix, second1, alpha);
                        vertex(vertices, matrix, second0, alpha);
                    }
                }
            }
        }
        super.render(entity, entityYaw, partialTick, poseStack, buffers, packedLight);
    }

    @Override
    public boolean shouldRender(FluxRiftEntity entity, Frustum frustum, double x, double y, double z) {
        return super.shouldRender(entity, frustum, x, y, z) || frustum.isVisible(geometryBounds(entity));
    }

    private static AABB geometryBounds(FluxRiftEntity entity) {
        List<LegacyFluxRiftGeometry.Point> points = entity.getGeometry();
        if (points.isEmpty()) return entity.getBoundingBox().inflate(2.0D);
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (LegacyFluxRiftGeometry.Point point : points) {
            minX = Math.min(minX, point.x());
            minY = Math.min(minY, point.y());
            minZ = Math.min(minZ, point.z());
            maxX = Math.max(maxX, point.x());
            maxY = Math.max(maxY, point.y());
            maxZ = Math.max(maxZ, point.z());
        }
        return new AABB(
                entity.getX() + minX, entity.getY() + minY, entity.getZ() + minZ,
                entity.getX() + maxX, entity.getY() + maxY, entity.getZ() + maxZ
        ).inflate(0.5D);
    }

    @Override
    public ResourceLocation getTextureLocation(FluxRiftEntity entity) {
        return END_PORTAL_TEXTURE;
    }

    private static boolean smokeProbeEnabled() {
        return "1".equals(System.getenv("THAUMCRAFT_SMOKE"));
    }

    /**
     * BETA26 RenderFluxRift.doRender uses SRC_ALPHA/ONE with depth writes off
     * for q=0..2, then SRC_ALPHA/ONE_MINUS_SRC_ALPHA with depth writes on for q=3.
     */
    private static final class RiftRenderTypes extends RenderStateShard {
        private static final ShaderStateShard RIFT_SHADER =
                new ShaderStateShard(FluxRiftRenderer::getRiftShader);
        private static final TextureStateShard END_PORTAL =
                new TextureStateShard(END_PORTAL_TEXTURE, false, false);
        private static final RenderType ADDITIVE = create(
                "thaumcraft:flux_rift_additive", true, false, false);
        private static final RenderType ALPHA_CORE = create(
                "thaumcraft:flux_rift_alpha_core", false, true, true);

        private RiftRenderTypes() {
            super("thaumcraft_flux_rift_render_types", () -> {}, () -> {});
        }

        private static RenderType additive() {
            return ADDITIVE;
        }

        private static RenderType alphaCore() {
            return ALPHA_CORE;
        }

        private static RenderType create(
                String name,
                boolean additive,
                boolean depthWrite,
                boolean sortOnUpload
        ) {
            return RenderType.create(
                    name,
                    DefaultVertexFormat.POSITION_COLOR,
                    VertexFormat.Mode.QUADS,
                    256,
                    false,
                    sortOnUpload,
                    RenderType.CompositeState.builder()
                            .setShaderState(RIFT_SHADER)
                            .setTextureState(END_PORTAL)
                            .setTransparencyState(additive
                                    ? LIGHTNING_TRANSPARENCY
                                    : TRANSLUCENT_TRANSPARENCY)
                            .setDepthTestState(LEQUAL_DEPTH_TEST)
                            .setCullState(NO_CULL)
                            .setWriteMaskState(depthWrite ? COLOR_DEPTH_WRITE : COLOR_WRITE)
                            .setLightmapState(NO_LIGHTMAP)
                            .setOverlayState(NO_OVERLAY)
                            .setOutputState(MAIN_TARGET)
                            .createCompositeState(false)
            );
        }
    }

    private static Vector ring(Vector center, Vector side, Vector up, float radius, double angle) {
        return center.add(side.scale(Math.cos(angle) * radius)).add(up.scale(Math.sin(angle) * radius));
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, Vector point, int alpha) {
        consumer.addVertex(matrix, (float) point.x(), (float) point.y(), (float) point.z())
                .setColor(255, 255, 255, alpha);
    }

    private record Vector(double x, double y, double z) {
        Vector add(Vector other) {
            return new Vector(x + other.x, y + other.y, z + other.z);
        }

        Vector subtract(Vector other) {
            return new Vector(x - other.x, y - other.y, z - other.z);
        }

        Vector scale(double amount) {
            return new Vector(x * amount, y * amount, z * amount);
        }

        Vector cross(Vector other) {
            return new Vector(
                    y * other.z - z * other.y,
                    z * other.x - x * other.z,
                    x * other.y - y * other.x
            );
        }

        Vector normalize() {
            double length = Math.sqrt(x * x + y * y + z * z);
            return length < 1.0E-8D ? new Vector(0.0D, 0.0D, 0.0D)
                    : new Vector(x / length, y / length, z / length);
        }
    }
}
