package dev.ehtid.thaumcraft.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.util.Mth;
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
    private static final int LEGACY_BLOCK_SPARKLES_PER_EDGE = 20;

    private SalisMundusSparkles() {}

    public static void sendToActor(ServerLevel level, ServerPlayer player, BlockPos pos, InteractionHand hand) {
        Vec3 forward = player.getLookAngle();
        Vec3 right = new Vec3(forward.z, 0.0D, -forward.x);
        right = right.lengthSqr() > 1.0E-6D ? right.normalize() : new Vec3(1.0D, 0.0D, 0.0D);
        boolean rightHand = player.getMainArm() == HumanoidArm.RIGHT;
        if (hand == InteractionHand.OFF_HAND) rightHand = !rightHand;
        double side = rightHand ? 1.0D : -1.0D;
        Vec3 start = player.getEyePosition().add(forward.scale(0.45D))
                .add(right.scale(side * 0.28D)).add(0.0D, -0.38D, 0.0D);
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
        AABB bounds = shape.isEmpty() ? new AABB(0, 0, 0, 1, 1, 1) : shape.bounds();
        int perFace = 2 * Math.max(1, (int) ((bounds.getXsize()
                + bounds.getYsize() + bounds.getZsize()) / 3.0D
                * LEGACY_BLOCK_SPARKLES_PER_EDGE));
        for (Direction face : Direction.values()) {
            BlockPos adjacent = pos.relative(face);
            var neighbor = level.getBlockState(adjacent);
            if (neighbor.isSolidRender(level, adjacent)
                    || neighbor.isFaceSturdy(level, adjacent, face.getOpposite())) {
                continue;
            }

            double cx = (bounds.minX + bounds.maxX) * 0.5D;
            double cy = (bounds.minY + bounds.maxY) * 0.5D;
            double cz = (bounds.minZ + bounds.maxZ) * 0.5D;
            for (int i = 0; i < perFace; i++) {
                double x = cx + face.getStepX() * (bounds.getXsize() * 0.5D + 0.01D);
                double y = cy + face.getStepY() * (bounds.getYsize() * 0.5D + 0.01D);
                double z = cz + face.getStepZ() * (bounds.getZsize() * 0.5D + 0.01D);
                if (face.getStepX() == 0) x += level.random.nextGaussian() * 0.6D;
                if (face.getStepY() == 0) y += level.random.nextGaussian() * 0.6D;
                if (face.getStepZ() == 0) z += level.random.nextGaussian() * 0.6D;
                level.sendParticles(player, warmDust(level), false,
                        pos.getX() + Mth.clamp(x, bounds.minX, bounds.maxX),
                        pos.getY() + Mth.clamp(y, bounds.minY, bounds.maxY),
                        pos.getZ() + Mth.clamp(z, bounds.minZ, bounds.maxZ),
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
