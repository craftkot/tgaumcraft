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
import dev.ehtid.thaumcraft.port.core.visual.SalisSparkleScaleRules;
import dev.ehtid.thaumcraft.port.core.visual.SalisSparkleFaceDelayRules;
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
        // Cosmetic effects must not load a clicked chunk that was unloaded
        // between the item interaction and this server-side invocation.
        // Check before querying the shape or enqueueing any trail particles.
        if (!level.hasChunkAt(pos)) {
            return;
        }
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
        // BETA26 measures face-sparkle delay from the clicked hit point.
        // The use-on-item call currently does not pass hit coordinates (PR #3
        // also owns that item file). Reconstruct the ray/box intersection;
        // the block center is a safe fallback, not exact hit-position parity.
        Vec3 eye = player.getEyePosition();
        Vec3 faceStart = new AABB(pos).clip(eye, eye.add(forward.scale(6.0D)))
                .orElse(Vec3.atCenterOf(pos));

        for (int i = 0; i < TRAIL_COUNT; i++) {
            boolean floaty = i < TRAIL_COUNT / 3;
            DustParticleOptions color = warmDust(level, false);
            // BETA26 FXDispatcher.drawSimpleSparkle queues each trail sprite
            // for a random 0..4 tick delay. Preserve the 50 independent
            // velocities/colors without changing the actor-only recipient.
            Vec3 velocity = new Vec3(
                    travel.x / 6.0D + level.random.nextGaussian() * 0.05D,
                    travel.y / 6.0D + level.random.nextGaussian() * 0.05D + (floaty ? 0.05D : 0.15D),
                    travel.z / 6.0D + level.random.nextGaussian() * 0.05D);
            SalisSparkleTrailQueue.emitOrQueue(level, player, color, start, velocity,
                    level.random.nextInt(5));
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
            // A face may border an unloaded chunk. Reading its block state
            // can synchronously load that chunk just to draw cosmetic FX.
            // Treat an unknown neighbor as exposed; never request its state.
            if (level.hasChunkAt(adjacent)) {
                var neighbor = level.getBlockState(adjacent);
                if (neighbor.isSolidRender(level, adjacent)
                        || neighbor.isFaceSturdy(level, adjacent, face.getOpposite())) {
                    continue;
                }
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
                Vec3 spawn = new Vec3(pos.getX() + x, pos.getY() + y, pos.getZ() + z);
                DustParticleOptions dust = warmDust(level, true);
                // Original drawBlockSparkles: (int)(rand.nextInt(5) + distance*16).
                // Queue on server ticks instead of immediately sending all faces.
                int delayTicks = SalisSparkleFaceDelayRules.delayTicks(
                        level.random.nextInt(5), spawn.distanceTo(faceStart));
                SalisSparkleTrailQueue.emitFaceOrQueue(level, player, dust, spawn,
                        new Vec3(0.0D, 0.0025D, 0.0D), delayTicks);
            }
        }
        // Diagnostic only: scheduled/emitted particles are not proof of client rendering.
        dev.ehtid.thaumcraft.util.ThaumcraftSmokeAudit.mark("SALIS_SPARKLES_PACKETS_SENT");
    }

    private static DustParticleOptions warmDust(ServerLevel level, boolean blockFace) {
        // Evaluate the warm color first: BETA26 samples face RGB before its
        // 0.4 + Gaussian*0.1 scale. The hand trail retains its original 0.5.
        Vector3f color = new Vector3f(1.0F,
                (189 + level.random.nextInt(67)) / 255.0F,
                (64 + level.random.nextInt(192)) / 255.0F);
        float scale = blockFace
                ? SalisSparkleScaleRules.blockFace(level.random.nextGaussian())
                : 0.5F;
        // Still a vanilla-dust fallback: FXGeneric's 2-phase size and
        // 320/512 sprite animation/lifetime are not yet reproduced.
        return new DustParticleOptions(color, scale);
    }
}
