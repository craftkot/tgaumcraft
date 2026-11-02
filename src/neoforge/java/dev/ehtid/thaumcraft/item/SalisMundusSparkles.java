package dev.ehtid.thaumcraft.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Actor-only visual fallback for BETA26 ItemMagicDust#doSparkles.
 *
 * The original emits 50 warm-colored sparkles from the hand toward the clicked
 * block, then a separate block sparkle. Until the original FXDispatcher sprite
 * and particle lifetime are ported, use tinted vanilla dust particles instead.
 * This is NOT exact visual parity with the original renderer.
 */
public final class SalisMundusSparkles {
    private static final int TRAIL_COUNT = 50;
    private static final int BLOCK_COUNT = 12;

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

        // IDustTrigger.sparkle defaults to the clicked block for simple/ore triggers.
        // This is a bounded vanilla approximation of FXDispatcher.drawBlockSparkles.
        for (int i = 0; i < BLOCK_COUNT; i++) {
            level.sendParticles(player, warmDust(level), false,
                    pos.getX() + level.random.nextDouble(),
                    pos.getY() + level.random.nextDouble(),
                    pos.getZ() + level.random.nextDouble(),
                    0, 0.0D, 0.025D, 0.0D, 1.0D);
        }
    }

    private static DustParticleOptions warmDust(ServerLevel level) {
        // BETA26: R=255, G=[189..255], B=[64..255], inclusive.
        return new DustParticleOptions(new Vector3f(1.0F,
                (189 + level.random.nextInt(67)) / 255.0F,
                (64 + level.random.nextInt(192)) / 255.0F), 0.5F);
    }
}
