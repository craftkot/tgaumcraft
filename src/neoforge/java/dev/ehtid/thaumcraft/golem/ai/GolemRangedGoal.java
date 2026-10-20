package dev.ehtid.thaumcraft.golem.ai;

import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import dev.ehtid.thaumcraft.port.core.golem.GolemRangedRules;
import net.minecraft.world.entity.ai.goal.Goal;
import java.util.EnumSet;

/** Source priority1 AIArrowAttack: range16, visible20, delay20..25, move speed1 and live target checks. */
public final class GolemRangedGoal extends Goal {
    private final ThaumcraftGolem golem;
    private GolemRangedRules.Clock clock=GolemRangedRules.Clock.initial();
    public GolemRangedGoal(ThaumcraftGolem golem) { this.golem=golem; setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK)); }
    @Override public boolean canUse() {
        var target=golem.getTarget();
        return golem.ranged()&&target!=null&&target.isAlive()&&!target.isRemoved()&&!golem.isAlliedTo(target)&&golem.level().hasChunkAt(target.blockPosition());
    }
    @Override public boolean canContinueToUse() { return canUse(); }
    @Override public boolean requiresUpdateEveryTick() { return true; }
    @Override public void stop() { clock=GolemRangedRules.Clock.initial(); }
    @Override public void tick() {
        if(!canUse()) return; var target=golem.getTarget();
        var step=GolemRangedRules.tick(clock,golem.distanceToSqr(target.getX(),target.getBoundingBox().minY,target.getZ()),golem.getSensing().hasLineOfSight(target)); clock=step.clock();
        if(step.stopMoving()) golem.getNavigation().stop(); else golem.getNavigation().moveTo(target,1D);
        golem.getLookControl().setLookAt(target,10,30);
        if(step.shoot()) golem.performRangedAttack(target,step.strength());
    }
}
