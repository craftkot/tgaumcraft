package dev.ehtid.thaumcraft.golem.ai;

import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/** BETA26 AIGotoHome thresholds/delays. NeoForge ground navigation is the initial adapter. */
public final class GolemHomeGoal extends Goal {
    private final ThaumcraftGolem golem;
    private int idleCounter = 10;
    private Vec3 target;

    public GolemHomeGoal(ThaumcraftGolem golem) {
        this.golem = golem;
        setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        if (golem.isFollowingOwner() || golem.hasWorkTask()) return false;
        if (idleCounter-- > 0) return false;
        idleCounter = 50;
        double distance = golem.distanceToSqr(Vec3.atCenterOf(golem.home()));
        if (distance < 5) return false;
        target = distance > 1024
                ? DefaultRandomPos.getPosTowards(golem, 16, 7, Vec3.atLowerCornerOf(golem.home()), Math.PI / 2)
                : Vec3.atLowerCornerOf(golem.home());
        return target != null;
    }

    @Override
    public void start() {
        golem.getNavigation().moveTo(target.x, target.y, target.z, golem.design().stats().moveSpeedMultiplier());
    }

    @Override
    public boolean canContinueToUse() {
        return !golem.isFollowingOwner() && !golem.hasWorkTask() && !golem.getNavigation().isDone()
                && golem.distanceToSqr(Vec3.atCenterOf(golem.home())) > 3;
    }

    @Override
    public void stop() {
        idleCounter = 50;
        golem.getNavigation().stop();
    }
}
