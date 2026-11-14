package dev.ehtid.thaumcraft.golem.ai;

import net.minecraft.core.*;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.*;

/** Source FlightNodeProcessor: six axis neighbors and passable full entity-volume cells. */
final class GolemFlightNodeEvaluator extends NodeEvaluator {
    @Override public Node getStart() { var box=mob.getBoundingBox(); return getNode(Mth.floor(box.minX),Mth.floor(box.minY),Mth.floor(box.minZ)); }
    @Override public Target getTarget(double x,double y,double z) {
        // Modern PathFinder supplies voxel positions; convert XZ back to their centers before source width offset.
        return getTargetNodeAt(x+.5-mob.getBbWidth()/2,y+.5,z+.5-mob.getBbWidth()/2);
    }
    @Override public int getNeighbors(Node[] result,Node current) {
        int count=0;
        for(var direction:Direction.values()) {
            int x=current.x+direction.getStepX(),y=current.y+direction.getStepY(),z=current.z+direction.getStepZ();
            if(!passable(x,y,z)) continue; var next=getNode(x,y,z);
            if(!next.closed) { next.type=PathType.WALKABLE; result[count++]=next; }
        }
        return count;
    }
    private boolean passable(int x,int y,int z) {
        var pos=new BlockPos.MutableBlockPos();
        for(int dx=0;dx<entityWidth;dx++) for(int dy=0;dy<entityHeight;dy++) for(int dz=0;dz<entityDepth;dz++) {
            pos.set(x+dx,y+dy,z+dz);
            if(mob.level().isOutsideBuildHeight(pos)||!mob.level().hasChunkAt(pos)) return false;
            var state=currentContext.getBlockState(pos);
            if(!state.isAir()&&!state.isPathfindable(PathComputationType.AIR)) return false;
        }
        return true;
    }
    // These two unused source API methods return WATER. Neighbor eligibility above is independently WALKABLE.
    @Override public PathType getPathTypeOfMob(PathfindingContext context,int x,int y,int z,Mob mob) { return PathType.WATER; }
    @Override public PathType getPathType(PathfindingContext context,int x,int y,int z) { return PathType.WATER; }
}
