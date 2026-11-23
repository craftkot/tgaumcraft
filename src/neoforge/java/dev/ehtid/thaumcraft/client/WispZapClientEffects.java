package dev.ehtid.thaumcraft.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.entity.WispEntity;
import dev.ehtid.thaumcraft.port.core.visual.LegacyWispZapGeometry;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-only BETA26 PacketFXWispZap renderer.
 *
 * The original FXBolt lives for three ticks, uses a five-sided seeded polycone,
 * draws two nested widths, binds essentia.png and uses SRC_ALPHA/ONE with depth
 * writes disabled. The event renderer keeps the bolt independent of the Wisp's
 * own billboard and entity-frustum visibility.
 */
@EventBusSubscriber(modid = ThaumcraftPort.MOD_ID, value = Dist.CLIENT)
public final class WispZapClientEffects {
    private static final ResourceLocation ESSENTIA_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ThaumcraftPort.MOD_ID, "textures/misc/essentia.png");
    private static final boolean SMOKE_PROBE = "1".equals(System.getenv("THAUMCRAFT_SMOKE"));
    private static final Map<Integer, Zap> ACTIVE = new ConcurrentHashMap<>();
    private static final Set<Long> SMOKE_RENDERED = ConcurrentHashMap.newKeySet();
    private static final Map<Long, Integer> SMOKE_FRAME_AGE = new ConcurrentHashMap<>();
    private static final RenderType BETA26_ADDITIVE_BOLT = BoltRenderTypes.additive();

    private WispZapClientEffects() {}

    public static void accept(Level level, Entity source, Entity target) {
        if (!(level instanceof net.minecraft.client.multiplayer.ClientLevel clientLevel)
                || !level.isClientSide || !(source instanceof WispEntity wisp)) {
            return;
        }

        RandomSource random = RandomSource.create();
        float phaseOffset = (float) (random.nextInt(50) * Math.PI);
        int seed = random.nextInt(1000);
        Zap zap = new Zap(
                clientLevel.dimension(),
                source.getId(),
                target.getId(),
                source.position(),
                target.position(),
                wisp.getAspectColor(),
                phaseOffset,
                seed,
                clientLevel.getGameTime()
        );
        ACTIVE.put(source.getId(), zap);

        if (SMOKE_PROBE) {
            System.out.println("TC_SMOKE_WISP_ZAP_PAYLOAD_RECEIVED=YES source=" + source.getId()
                    + " target=" + target.getId() + " color=#"
                    + String.format("%06X", zap.rgb())
                    + " createdAt=" + zap.createdAt()
                    + " clientGameTime=" + clientLevel.getGameTime());
        }
    }

    @SubscribeEvent
    public static void expire(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        net.minecraft.client.multiplayer.ClientLevel level = minecraft.level;
        if (level == null) {
            ACTIVE.clear();
            SMOKE_RENDERED.clear();
            SMOKE_FRAME_AGE.clear();
            return;
        }

        long gameTime = level.getGameTime();
        ACTIVE.entrySet().removeIf(entry -> {
            Zap zap = entry.getValue();
            boolean wrongDimension = !zap.dimension().equals(level.dimension());
            boolean expired = gameTime - zap.createdAt() >= 3L;
            if (wrongDimension || expired) {
                forgetSmokeState(zap, gameTime, expired);
                return true;
            }
            return false;
        });
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || ACTIVE.isEmpty()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        net.minecraft.client.multiplayer.ClientLevel level = minecraft.level;
        if (level == null) {
            ACTIVE.clear();
            SMOKE_RENDERED.clear();
            SMOKE_FRAME_AGE.clear();
            return;
        }

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Camera camera = event.getCamera();
        Vec3 cameraPosition = camera.getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        List<ZapRender> rendered = new ArrayList<>();

        for (Map.Entry<Integer, Zap> entry : ACTIVE.entrySet()) {
            Zap zap = entry.getValue();
            if (!zap.dimension().equals(level.dimension())) {
                ACTIVE.remove(entry.getKey(), zap);
                continue;
            }

            double age = level.getGameTime() - zap.createdAt() + partialTick;
            if (age < 0.0D) {
                continue;
            }
            if (age >= 3.0D) {
                ACTIVE.remove(entry.getKey(), zap);
                forgetSmokeState(zap, level.getGameTime(), true);
                continue;
            }

            AABB bounds = bounds(zap);
            Frustum frustum = event.getFrustum();
            if (!frustum.isVisible(bounds)) {
                continue;
            }

            int particleAge = (int) age;
            float agePartial = (float) (age - particleAge);
            List<LegacyWispZapGeometry.Point> points = LegacyWispZapGeometry.build(
                    zap.end().x - zap.start().x,
                    zap.end().y - zap.start().y,
                    zap.end().z - zap.start().z,
                    0.6F,
                    zap.phaseOffset(),
                    zap.seed(),
                    particleAge,
                    agePartial
            );
            if (points.size() <= 2) {
                continue;
            }

            poseStack.pushPose();
            poseStack.translate(
                    zap.start().x - cameraPosition.x,
                    zap.start().y - cameraPosition.y,
                    zap.start().z - cameraPosition.z
            );
            Matrix4f matrix = poseStack.last().pose();
            VertexConsumer vertices = buffers.getBuffer(BETA26_ADDITIVE_BOLT);
            float alpha = Mth.clamp(1.0F - (float) age / 3.0F, 0.1F, 1.0F);
            drawTube(vertices, matrix, points, 1.0F, alpha, zap.rgb());
            drawTube(vertices, matrix, points, 1.0F / 3.0F, alpha, zap.rgb());
            poseStack.popPose();
            rendered.add(new ZapRender(zap, points.size(), age));
        }

        if (!rendered.isEmpty()) {
            buffers.endBatch(BETA26_ADDITIVE_BOLT);
            if (SMOKE_PROBE) {
                long gameTime = level.getGameTime();
                for (ZapRender result : rendered) {
                    Zap zap = result.zap();
                    long key = smokeKey(zap);
                    int ageTick = (int) Math.floor(result.age());
                    Entity source = level.getEntity(zap.sourceId());
                    int wispTick = source == null ? -1 : source.tickCount;
                    if (SMOKE_RENDERED.add(key)) {
                        System.out.println("TC_SMOKE_WISP_ZAP_RENDERED=YES source="
                                + zap.sourceId() + " target=" + zap.targetId()
                                + " points=" + result.pointCount() + " age=" + result.age()
                                + " texture=" + ESSENTIA_TEXTURE + " blend=SRC_ALPHA_ONE depthWrite=OFF"
                                + " createdAt=" + zap.createdAt() + " gameTime=" + gameTime
                                + " ageTick=" + ageTick);
                    }
                    Integer previousAgeTick = SMOKE_FRAME_AGE.put(key, ageTick);
                    if (previousAgeTick == null || previousAgeTick.intValue() != ageTick) {
                        System.out.println("TC_SMOKE_WISP_ZAP_FRAME=YES source=" + zap.sourceId()
                                + " target=" + zap.targetId() + " createdAt=" + zap.createdAt()
                                + " gameTime=" + gameTime + " wispTick=" + wispTick
                                + " ageTick=" + ageTick + " age=" + result.age()
                                + " points=" + result.pointCount()
                                + " texture=" + ESSENTIA_TEXTURE + " blend=SRC_ALPHA_ONE depthWrite=OFF");
                    }
                }
            }
        }
    }

    private static long smokeKey(Zap zap) {
        return (((long) zap.sourceId()) << 32) ^ (zap.createdAt() & 0xFFFFFFFFL);
    }

    private static void forgetSmokeState(Zap zap, long gameTime, boolean expired) {
        long key = smokeKey(zap);
        SMOKE_FRAME_AGE.remove(key);
        SMOKE_RENDERED.remove(key);
        if (SMOKE_PROBE && expired) {
            System.out.println("TC_SMOKE_WISP_ZAP_EXPIRED=YES source=" + zap.sourceId()
                    + " target=" + zap.targetId() + " createdAt=" + zap.createdAt()
                    + " gameTime=" + gameTime + " ageTicks=" + (gameTime - zap.createdAt()));
        }
    }

    private static AABB bounds(Zap zap) {
        return new AABB(
                Math.min(zap.start().x, zap.end().x),
                Math.min(zap.start().y, zap.end().y),
                Math.min(zap.start().z, zap.end().z),
                Math.max(zap.start().x, zap.end().x),
                Math.max(zap.start().y, zap.end().y),
                Math.max(zap.start().z, zap.end().z)
        ).inflate(0.75D);
    }

    private static void drawTube(
            VertexConsumer vertices,
            Matrix4f matrix,
            List<LegacyWispZapGeometry.Point> points,
            float radiusScale,
            float alpha,
            int rgb
    ) {
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;
        int alphaByte = Mth.clamp(Math.round(alpha * 255.0F), 0, 255);

        for (int index = 0; index < points.size() - 1; index++) {
            LegacyWispZapGeometry.Point before = points.get(Math.max(0, index - 1));
            LegacyWispZapGeometry.Point start = points.get(index);
            LegacyWispZapGeometry.Point end = points.get(index + 1);
            LegacyWispZapGeometry.Point after = points.get(Math.min(points.size() - 1, index + 2));

            Vector tangent = new Vector(
                    after.x() - before.x(),
                    after.y() - before.y(),
                    after.z() - before.z()
            ).normalize();
            Vector reference = Math.abs(tangent.y()) > 0.9D
                    ? new Vector(1.0D, 0.0D, 0.0D)
                    : new Vector(0.0D, 1.0D, 0.0D);
            Vector side = tangent.cross(reference).normalize();
            Vector up = tangent.cross(side).normalize();

            double startRadius = start.width() / 10.0D * radiusScale;
            double endRadius = end.width() / 10.0D * radiusScale;
            float u0 = index / (float) (points.size() - 1);
            float u1 = (index + 1) / (float) (points.size() - 1);

            for (int sideIndex = 0; sideIndex < 5; sideIndex++) {
                double angle0 = Math.PI * 2.0D * sideIndex / 5.0D;
                double angle1 = Math.PI * 2.0D * (sideIndex + 1) / 5.0D;
                Vector normal0 = side.scale(Math.cos(angle0)).add(up.scale(Math.sin(angle0))).normalize();
                Vector normal1 = side.scale(Math.cos(angle1)).add(up.scale(Math.sin(angle1))).normalize();
                Vector start0 = ring(start, side, up, startRadius, angle0);
                Vector start1 = ring(start, side, up, startRadius, angle1);
                Vector end1 = ring(end, side, up, endRadius, angle1);
                Vector end0 = ring(end, side, up, endRadius, angle0);
                float v0 = sideIndex / 5.0F;
                float v1 = (sideIndex + 1) / 5.0F;

                vertex(vertices, matrix, start0, u0, v0, red, green, blue, alphaByte, normal0);
                vertex(vertices, matrix, start1, u0, v1, red, green, blue, alphaByte, normal1);
                vertex(vertices, matrix, end1, u1, v1, red, green, blue, alphaByte, normal1);
                vertex(vertices, matrix, end0, u1, v0, red, green, blue, alphaByte, normal0);
            }
        }
    }

    private static Vector ring(
            LegacyWispZapGeometry.Point center,
            Vector side,
            Vector up,
            double radius,
            double angle
    ) {
        return new Vector(center.x(), center.y(), center.z())
                .add(side.scale(Math.cos(angle) * radius))
                .add(up.scale(Math.sin(angle) * radius));
    }

    private static void vertex(
            VertexConsumer vertices,
            Matrix4f matrix,
            Vector point,
            float u,
            float v,
            int red,
            int green,
            int blue,
            int alpha,
            Vector normal
    ) {
        vertices.addVertex(matrix, (float) point.x(), (float) point.y(), (float) point.z())
                .setColor(red, green, blue, alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal((float) normal.x(), (float) normal.y(), (float) normal.z());
    }

    private record Zap(
            ResourceKey<Level> dimension,
            int sourceId,
            int targetId,
            Vec3 start,
            Vec3 end,
            int rgb,
            float phaseOffset,
            int seed,
            long createdAt
    ) {}

    private record ZapRender(Zap zap, int pointCount, double age) {}

    private record Vector(double x, double y, double z) {
        Vector add(Vector other) {
            return new Vector(x + other.x, y + other.y, z + other.z);
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
            return length < 1.0E-8D
                    ? new Vector(0.0D, 0.0D, 0.0D)
                    : new Vector(x / length, y / length, z / length);
        }
    }

    private static final class BoltRenderTypes extends RenderStateShard {
        private static final ShaderStateShard ENTITY_TRANSLUCENT =
                new ShaderStateShard(GameRenderer::getRendertypeEntityTranslucentShader);
        private static final TextureStateShard ESSENTIA =
                new TextureStateShard(ESSENTIA_TEXTURE, false, false);
        private static final RenderType ADDITIVE = RenderType.create(
                "thaumcraft:wisp_zap_bolt",
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS,
                256,
                false,
                false,
                RenderType.CompositeState.builder()
                        .setShaderState(ENTITY_TRANSLUCENT)
                        .setTextureState(ESSENTIA)
                        .setTransparencyState(LIGHTNING_TRANSPARENCY)
                        .setDepthTestState(LEQUAL_DEPTH_TEST)
                        .setCullState(NO_CULL)
                        .setWriteMaskState(COLOR_WRITE)
                        .setLightmapState(LIGHTMAP)
                        .setOverlayState(OVERLAY)
                        .setOutputState(MAIN_TARGET)
                        .createCompositeState(false)
        );

        private BoltRenderTypes() {
            super("thaumcraft_wisp_bolt_render_types", () -> {}, () -> {});
        }

        private static RenderType additive() {
            return ADDITIVE;
        }
    }
}
