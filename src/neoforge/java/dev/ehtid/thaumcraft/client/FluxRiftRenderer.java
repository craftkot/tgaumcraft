package dev.ehtid.thaumcraft.client;

import dev.ehtid.thaumcraft.entity.FluxRiftEntity;
import dev.ehtid.thaumcraft.port.core.LegacyFluxRiftGeometry;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

import java.util.List;

/**
 * First visible Rift mesh: BETA26's seeded polycone path and radii, rendered
 * with vanilla additive lightning geometry. The original end-shader/star
 * compositing is not yet ported and is not claimed as visual parity.
 */
public final class FluxRiftRenderer extends EntityRenderer<FluxRiftEntity> {
    private static final int SIDES = 6;

    public FluxRiftRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.0F;
    }

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
            VertexConsumer vertices = buffers.getBuffer(RenderType.lightning());
            float stability = Mth.clamp(1.0F - entity.getRiftStability() / 50.0F, 0.0F, 1.5F);

            for (int layer = 0; layer <= 3; layer++) {
                float layerScale = layer < 3 ? 1.25F + 0.5F * layer : 1.0F;
                int alpha = layer < 3 ? 96 : 220;
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
        return ResourceLocation.withDefaultNamespace("textures/entity/end_portal.png");
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
