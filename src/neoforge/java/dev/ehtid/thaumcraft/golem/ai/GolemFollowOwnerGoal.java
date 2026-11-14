package dev.ehtid.thaumcraft.golem.ai;

import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import dev.ehtid.thaumcraft.port.core.golem.GolemBellRules;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;
import java.util.EnumSet;

/** Source follow thresholds/speed/repath/ring order, with modern collision and loaded-chunk guards. */
public final class GolemFollowOwnerGoal extends Goal {
    private final ThaumcraftGolem golem;
    private Player owner;
    private int repath;
    private float previousWaterMalus;
    private boolean started;

    public GolemFollowOwnerGoal(ThaumcraftGolem golem) {
        this.golem = golem;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override public boolean canUse() {
        if (!golem.isFollowingOwner() || !(golem.level() instanceof ServerLevel level) || golem.ownerId() == null) return false;
        Player candidate = level.getPlayerByUUID(golem.ownerId());
        if (candidate == null || !candidate.isAlive() || candidate.isSpectator()
                || !level.hasChunkAt(candidate.blockPosition())
                || golem.distanceToSqr(candidate) < GolemBellRules.START_DISTANCE_SQUARED) return false;
        owner = candidate;
        return true;
    }

    @Override public boolean canContinueToUse() {
        return golem.isFollowingOwner() && owner != null && owner.isAlive() && !owner.isRemoved()
                && owner.level() == golem.level() && !owner.isSpectator()
                && !golem.getNavigation().isDone() && golem.distanceToSqr(owner) > GolemBellRules.STOP_DISTANCE_SQUARED;
    }

    @Override public void start() {
        repath = 0;
        previousWaterMalus = golem.getPathfindingMalus(PathType.WATER);
        golem.setPathfindingMalus(PathType.WATER, 0);
        started = true;
    }

    @Override public void stop() {
        owner = null;
        golem.getNavigation().stop();
        if (started) golem.setPathfindingMalus(PathType.WATER, previousWaterMalus);
        started = false;
    }

    @Override public void tick() {
        if (owner == null || !(golem.level() instanceof ServerLevel level)) return;
        golem.getLookControl().setLookAt(owner, 10, golem.getMaxHeadXRot());
        if (--repath > 0) return;
        repath = GolemBellRules.REPATH_TICKS;
        if (!level.hasChunkAt(owner.blockPosition())) return;
        if (golem.getNavigation().moveTo(owner, GolemBellRules.SPEED) || golem.isLeashed()
                || golem.distanceToSqr(owner) < GolemBellRules.TELEPORT_DISTANCE_SQUARED) return;
        BlockPos center = BlockPos.containing(owner.getX(), owner.getBoundingBox().minY, owner.getZ());
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            if (Math.abs(x) < 2 && Math.abs(z) < 2) continue;
            BlockPos pos = center.offset(x, 0, z);
            if (!level.hasChunkAt(pos) || !level.getBlockState(pos.below()).isSolidRender(level, pos.below())
                    || !level.getFluidState(pos).isEmpty() || !level.getFluidState(pos.above()).isEmpty()) continue;
            Vec3 destination = Vec3.atBottomCenterOf(pos);
            var box = golem.getBoundingBox().move(destination.subtract(golem.position()));
            if (!level.noCollision(golem, box)) continue;
            golem.moveTo(destination);
            golem.getNavigation().stop();
            return;
        }
    }

    @Override public boolean requiresUpdateEveryTick() { return true; }
}
