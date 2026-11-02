package dev.ehtid.thaumcraft.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import dev.ehtid.thaumcraft.port.core.visual.SalisSparkleFaceLayout;
import dev.ehtid.thaumcraft.port.core.visual.SalisSparkleHandOrigin;
import org.joml.Vector3f;

/**
 * Actor-only visual fallback for BETA26 ItemMagicDust#doSparkles.
 *
 * The original emits 50 warm-colored sparkles from the hand toward the clicked
 * block, then a face-aware block sparkle burst. Until the original FXDispatcher sprite
 * and particle lifetime are ported, use tinted vanilla dust particles instead.
 * This is NOT exact visual parity with the original renderer.
 */
public final class SalisMundusSparkles {
    private static final int TRAIL_COUNT = 50;

    private SalisMundusSparkles() {}

    public static void sendToActor(ServerLevel level, ServerPlayer player, BlockPos pos, InteractionHand hand) {
        Vec3 forward = player.getLookAngle();
        // BETA26 ItemMagicDust uses EntityUtils.posToHand: chest height,
        // yaw-based hand side and a 0.3 look-vector offset. The previous
        // eye-position approximation shifted the trail and changed with the
        // preferred main arm; the original distinguishes MAIN/OFF_HAND only.
        double[] origin = SalisSparkleHandOrigin.position(
                player.getX(), player.getBoundingBox().minY, player.getBbHeight(),
                player.getZ(), player.getYRot(),
                forward.x, forward.y, forward.z, hand == InteractionHand.OFF_HAND);
        Vec3 start = new Vec3(origin[0], origin[1], origin[2]);
        Vec3 travel = Vec3.atCenterOf(pos).subtract(start);

        for (int i = 0; i < TRAIL_COUNT; i++) {
            boolean floaty = i < TRAIL_COUNT / 3;
            DustParticleOptions color = warmDust(level);
            // count=0 means one particle with the supplied exact velocity.
            level.sendParticles(player, color, false, start.x, start.y, start.z, 0,
                    travel.x / 6.0D + level.random.nextGaussian() * 0.05D,
                    travel.y / 6.0D + level.random.nextGaussian() * 0.05D + (floaty ? 0.05D : 0.15D),
                    travel.z / 6.0D + level.random.nextGaussian() * 0.05D, 1.0D);
        }

        // BETA26 FXDispatcher.drawBlockSparkles: only EXPOSED block faces
        // emit sparkles. The previous 12 random interior positions were often
        // occluded by the solid bookshelf and did not match the source effect.
        // Original: 2 * floor(20 * mean edge length) per visible face.
        var shape = level.getBlockState(pos).getShape(level, pos);
        // BETA26 FXDispatcher calls bs.grow(0.1, 0.1, 0.1) but discards
        // its return value. 1.12.2 AxisAlignedBB is immutable: the original
        // effect actually clamps to the UNEXPANDED shape (40/full-cube face).
        AABB bounds = shape.isEmpty() ? new AABB(0, 0, 0, 1, 1, 1) : shape.bounds();
        int perFace = SalisSparkleFaceLayout.particlesPerFace(
                bounds.getXsize(), bounds.getYsize(), bounds.getZsize());
        for (Direction face : Direction.values()) {
            BlockPos adjacent = pos.relative(face);
            var neighbor = level.getBlockState(adjacent);
            if (neighbor.isSolidRender(level, adjacent)
                    || neighbor.isFaceSturdy(level, adjacent, face.getOpposite())) {
                continue;
            }

            // BETA26 uses block-local 0.5 +/- 0.51, then clamps to the
            // shape. The old midpoint-of-shape formula moved asymmetric FX.
            for (int i = 0; i < perFace; i++) {
                double x = SalisSparkleFaceLayout.coordinate(bounds.minX, bounds.maxX,
                        face.getStepX(), face.getStepX() == 0 ? level.random.nextGaussian() : 0.0D);
                double y = SalisSparkleFaceLayout.coordinate(bounds.minY, bounds.maxY,
                        face.getStepY(), face.getStepY() == 0 ? level.random.nextGaussian() : 0.0D);
                double z = SalisSparkleFaceLayout.coordinate(bounds.minZ, bounds.maxZ,
                        face.getStepZ(), face.getStepZ() == 0 ? level.random.nextGaussian() : 0.0D);
                level.sendParticles(player, warmDust(level), false,
                        pos.getX() + x,
                        pos.getY() + y,
                        pos.getZ() + z,
                        0, 0.0D, 0.0025D, 0.0D, 1.0D);
            }
        }
        // Diagnostic only: packet emission is not proof of visible client rendering.
        dev.ehtid.thaumcraft.util.ThaumcraftSmokeAudit.mark("SALIS_SPARKLES_PACKETS_SENT");
    }

    private static DustParticleOptions warmDust(ServerLevel level) {
        // BETA26: R=255, G=[189..255], B=[64..255], inclusive.
        return new DustParticleOptions(new Vector3f(1.0F,
                (189 + level.random.nextInt(67)) / 255.0F,
                (64 + level.random.nextInt(192)) / 255.0F), 0.5F);
    }
}
