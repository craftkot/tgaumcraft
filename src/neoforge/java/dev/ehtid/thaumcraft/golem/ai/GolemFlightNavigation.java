package dev.ehtid.thaumcraft.golem.ai;

import dev.ehtid.thaumcraft.port.core.golem.GolemFlightRules;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.*;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.phys.*;

/** Source air navigator: 3D body-center tracking and six-node/36-square collision-ray smoothing. */
public final class GolemFlightNavigation extends PathNavigation {
    public GolemFlightNavigation(Mob mob,Level level) { super(mob,level); }
    @Override protected PathFinder createPathFinder(int maxVisitedNodes) { nodeEvaluator=new GolemFlightNodeEvaluator(); return new PathFinder(nodeEvaluator,maxVisitedNodes); }
    @Override protected boolean canUpdatePath() { return true; }
    @Override protected Vec3 getTempMobPos() { return mob.position().add(0,mob.getBbHeight()*.5,0); }
    @Override protected double getGroundY(Vec3 pos) { return pos.y; }
    @Override protected void followThePath() {
        var pos=getTempMobPos(); double widthSquared=mob.getBbWidth()*mob.getBbWidth();
        if(pos.distanceToSqr(path.getEntityPosAtNode(mob,path.getNextNodeIndex()))<widthSquared) path.advance();
        if(!path.isDone()) for(int index=Math.min(path.getNextNodeIndex()+GolemFlightRules.LOOK_AHEAD,path.getNodeCount()-1);index>path.getNextNodeIndex();index--) {
            var candidate=path.getEntityPosAtNode(mob,index);
            if(pos.distanceToSqr(candidate)<=GolemFlightRules.SMOOTH_DISTANCE_SQUARED&&canMoveDirectly(pos,candidate)) { path.setNextNodeIndex(index); break; }
        }
        doStuckDetection(pos);
    }
    @Override protected boolean canMoveDirectly(Vec3 from,Vec3 to) {
        return level.clip(new ClipContext(from,to.add(0,mob.getBbHeight()*.5,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,mob)).getType()==HitResult.Type.MISS;
    }
}
