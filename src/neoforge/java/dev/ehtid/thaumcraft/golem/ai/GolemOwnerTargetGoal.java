package dev.ehtid.thaumcraft.golem.ai;

import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;

/** BETA26 AIOwnerHurtByTarget/AIOwnerHurtTarget, active only while following. */
public final class GolemOwnerTargetGoal extends TargetGoal {
    private final ThaumcraftGolem golem;
    private final boolean defend;
    private int remembered;
    private LivingEntity candidate;
    public GolemOwnerTargetGoal(ThaumcraftGolem golem, boolean defend) {
        super(golem, false); this.golem = golem; this.defend = defend;
    }
    @Override public boolean canUse() {
        var owner = golem.ownerEntity();
        if (!golem.fighter() || !golem.isFollowingOwner() || owner == null) return false;
        candidate = defend ? owner.getLastHurtByMob() : owner.getLastHurtMob();
        int stamp = defend ? owner.getLastHurtByMobTimestamp() : owner.getLastHurtMobTimestamp();
        return stamp != remembered && candidate != null && !golem.isAlliedTo(candidate)
                && canAttack(candidate, TargetingConditions.DEFAULT);
    }
    @Override public boolean canContinueToUse() {
        return golem.fighter() && golem.isFollowingOwner() && super.canContinueToUse();
    }
    @Override public void start() {
        golem.setTarget(candidate);
        var owner = golem.ownerEntity();
        if (owner != null) remembered = defend ? owner.getLastHurtByMobTimestamp() : owner.getLastHurtMobTimestamp();
        super.start();
    }
}
