package dev.ehtid.thaumcraft.golem.ai;

import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import dev.ehtid.thaumcraft.golem.seal.GolemSeals;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/** BETA26 AIGotoEntity: shared selection for real pickup and immediate living-target assignment. */
public final class GolemPickupGoal extends Goal {
    private final ThaumcraftGolem golem;
    private GolemSeals.Assignment assignment;
    private int cooldown;
    private int travelTicks;
    private BlockPos previousPosition;
    private boolean completed;
    private boolean combat;
    private boolean failed;

    public GolemPickupGoal(ThaumcraftGolem golem) {
        this.golem = golem;
        setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        if (!(golem.level() instanceof ServerLevel level)) return false;
        if (cooldown > 0) { cooldown--; return false; }
        cooldown = 5;
        assignment = GolemSeals.get(level).claimEntity(golem, this::canReach).orElse(null);
        combat=assignment!=null&&GolemSeals.get(level).combat(assignment);
        return assignment != null;
    }

    private boolean canReach(net.minecraft.world.entity.Entity item) {
        double reach = GolemSeals.reachSquared(item);
        if (golem.distanceToSqr(item) < reach) return true;
        Path path = golem.getNavigation().createPath(item, 0);
        if (path == null) return false;
        Node end = path.getEndNode();
        if (end == null) return false;
        int dx = end.x - item.blockPosition().getX();
        int dz = end.z - item.blockPosition().getZ();
        return (double) dx * dx + (double) dz * dz < reach;
    }

    @Override
    public void start() {
        travelTicks = 0;
        previousPosition = null;
        completed = false;
        failed = false;
        if(combat && golem.level() instanceof ServerLevel level) {
            completed=GolemSeals.get(level).startCombat(level,golem,assignment); failed=!completed;
            golem.setWorking(false); return;
        }
        golem.setWorking(true);
        ItemEntity target = target();
        if (target != null) golem.getNavigation().moveTo(target, speed());
    }

    @Override
    public boolean canContinueToUse() {
        return !completed && !failed && assignment != null && travelTicks <= 1000 && target() != null;
    }

    @Override
    public void tick() {
        ItemEntity item = target();
        if (item == null || !(golem.level() instanceof ServerLevel level)) return;
        golem.getLookControl().setLookAt(item, 10.0F, golem.getMaxHeadXRot());
        if (golem.distanceToSqr(item) <= GolemSeals.reachSquared(item)) {
            completed = GolemSeals.get(level).pickup(level, golem, assignment);
            failed = !completed;
            return;
        }
        travelTicks++;
        if (travelTicks % 5 != 0) return;
        if (golem.blockPosition().equals(previousPosition)) {
            Vec3 detour = DefaultRandomPos.getPosTowards(golem, 6, 4, item.position(), Math.PI / 2.0D);
            if (detour != null) golem.getNavigation().moveTo(detour.x + .5D, detour.y + .5D,
                    detour.z + .5D, speed());
        } else {
            golem.getNavigation().moveTo(item, speed());
        }
        previousPosition = golem.blockPosition();
    }

    private double speed() {
        // The default wood construction has LIGHT: source navigation multiplier 1.2.
        // Use derived source stats for all future registered constructions.
        return golem.design().stats().moveSpeedMultiplier();
    }

    private ItemEntity target() {
        return assignment != null && golem.level() instanceof ServerLevel level
                ? GolemSeals.get(level).target(level, golem, assignment) : null;
    }

    @Override
    public void stop() {
        release();
    }

    /** Entity.remove must call this as well: discard does not guarantee Goal.stop. */
    public void release() {
        if (assignment != null && golem.level() instanceof ServerLevel level)
            GolemSeals.get(level).release(level, golem, assignment, failed || !completed && travelTicks > 1000);
        assignment = null;
        golem.setWorking(false);
        golem.getNavigation().stop();
        cooldown = 5;
    }

    @Override
    public boolean requiresUpdateEveryTick() { return true; }
}
