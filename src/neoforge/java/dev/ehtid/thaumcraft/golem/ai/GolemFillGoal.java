package dev.ehtid.thaumcraft.golem.ai;

import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import dev.ehtid.thaumcraft.golem.seal.GolemSeals;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/** BETA26 AIGotoBlock for Fill, below entity tasks and above the home goal. */
public final class GolemFillGoal extends Goal {
    private final ThaumcraftGolem golem;
    private GolemSeals.FillAssignment assignment;
    private BlockPos destination;
    private BlockPos previousPosition;
    private int cooldown = 5;
    private int travelTicks;
    private boolean done;
    private boolean failed;

    public GolemFillGoal(ThaumcraftGolem golem) {
        this.golem = golem;
        setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        if (!(golem.level() instanceof ServerLevel level)) return false;
        if (cooldown-- > 0) return false;
        cooldown = 5;
        assignment = GolemSeals.get(level).claimFill(golem, this::canReach).orElse(null);
        if (assignment == null) return false;
        destination = adjacentSpace(assignment.seal().pos());
        return true;
    }

    private boolean canReach(BlockPos target) {
        if (distance(target) < 4) return true;
        Path path = golem.getNavigation().createPath(target, 0);
        if (path == null || path.getEndNode() == null) return false;
        var end = path.getEndNode();
        int dx = end.x - target.getX(), dy = end.y - target.getY(), dz = end.z - target.getZ();
        if (dx == 0 && dz == 0 && dy == 2) dy--;
        return dx * dx + dy * dy + dz * dz < 2.25;
    }

    private BlockPos adjacentSpace(BlockPos target) {
        BlockPos closest = target;
        double best = Double.MAX_VALUE;
        for (Direction face : Direction.values()) {
            BlockPos candidate = target.relative(face);
            if (!golem.level().hasChunkAt(candidate) || golem.level().getBlockState(candidate).blocksMotion()) continue;
            double distance = distance(candidate);
            if (distance < best) { best = distance; closest = candidate; }
        }
        return closest;
    }

    private double distance(BlockPos pos) { return golem.distanceToSqr(Vec3.atCenterOf(pos)); }

    private GolemSeals.SealKey target() {
        return assignment != null && golem.level() instanceof ServerLevel level
                ? GolemSeals.get(level).fillTarget(level, golem, assignment) : null;
    }

    private void move() {
        Vec3 pos = Vec3.atCenterOf(destination);
        golem.getNavigation().moveTo(pos.x, pos.y, pos.z, golem.design().stats().moveSpeedMultiplier());
    }

    @Override
    public void start() {
        travelTicks = 0;
        done = failed = false;
        previousPosition = null;
        golem.setWorking(true);
        move();
    }

    @Override
    public boolean canContinueToUse() { return !done && !failed && travelTicks <= 1000 && target() != null; }

    @Override
    public void tick() {
        var target = target();
        if (target == null || !(golem.level() instanceof ServerLevel level)) return;
        Vec3 center = Vec3.atCenterOf(target.pos());
        golem.getLookControl().setLookAt(center.x, center.y, center.z, 10, golem.getMaxHeadXRot());
        if (distance(target.pos()) <= 4) {
            done = GolemSeals.get(level).fill(level, golem, assignment);
            failed = !done;
            return;
        }
        if (++travelTicks % 5 != 0) return;
        if (golem.blockPosition().equals(previousPosition)) {
            Vec3 detour = DefaultRandomPos.getPosTowards(golem, 6, 4, center, Math.PI / 2);
            if (detour != null) golem.getNavigation().moveTo(detour.x, detour.y, detour.z,
                    golem.design().stats().moveSpeedMultiplier());
        } else move();
        previousPosition = golem.blockPosition();
    }

    @Override
    public void stop() {
        if (assignment != null && golem.level() instanceof ServerLevel level)
            GolemSeals.get(level).releaseFill(level, golem, assignment, failed || !done && travelTicks > 1000);
        assignment = null;
        golem.setWorking(false);
        golem.getNavigation().stop();
        cooldown = 5;
    }

    @Override
    public boolean requiresUpdateEveryTick() { return true; }
}
