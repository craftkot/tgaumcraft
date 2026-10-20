package dev.ehtid.thaumcraft.golem.seal;

import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import dev.ehtid.thaumcraft.port.core.golem.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** SealLumber: source ordered farthest log, one real harvest per callback; no invented planting/filtering. */
final class GolemLumber {
    private record Work(GolemSeals.Seal seal,BlockPos origin,BlockState original) {}
    private final GolemSeals data;
    private final Map<Integer,Work> work=new HashMap<>();
    private final Map<GolemSeals.SealKey,Map<BlockPos,Integer>> watched=new HashMap<>();
    private final Map<GolemSeals.SealKey,Integer> scan=new HashMap<>();
    private final Map<Integer,Long> nextCallback=new HashMap<>();
    GolemLumber(GolemSeals data) { this.data=data; }
    void removed(GolemSeals.SealKey key) { watched.remove(key); scan.remove(key); }
    void forget(int id) { var w=work.remove(id); if(w!=null) { var positions=watched.get(w.seal.key()); if(positions!=null) positions.remove(w.origin,id); } nextCallback.remove(id); }
    void released(int id) { nextCallback.remove(id); }
    private static boolean loaded(ServerLevel level,BlockPos pos) { return !level.isOutsideBuildHeight(pos)&&GolemSeals.active(level,pos); }
    private static ItemStack item(ServerLevel level,BlockPos pos,BlockState state) { return state.getBlock().getCloneItemStack(level,pos,state); }
    private static GolemLegacyItem identity(ServerLevel level,BlockPos pos,BlockState state) {
        var blockId=BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        for(String species:new String[]{"oak","spruce","birch","jungle","acacia","dark_oak"})
            if(blockId.equals("minecraft:"+species+"_wood")) return GolemLegacyItem.modern("minecraft:"+species+"_log",false,0); // Old bark log damageDropped discards axis/bark bits.
        var block=GolemLegacyItem.modern(blockId,false,0); var dropped=GolemStackMatcher.identity(item(level,pos,state));
        return new GolemLegacyItem(block.id(),dropped.metadata()); // Distinct mod blocks sharing a picked item remain distinct source blocks.
    }
    private static boolean wood(ServerLevel level,BlockPos pos) {
        if(!loaded(level,pos)) return false; var state=level.getBlockState(pos);
        return state.is(BlockTags.LOGS) || GolemStackMatcher.matches(new ItemStack(Items.OAK_LOG),item(level,pos,state),new SealFilterRules.Options(false,false,true,false));
    }
    void tick(ServerLevel level) {
        for(var s:data.all()) if(s.type()==GolemSeals.Type.LUMBER&&loaded(level,s.key().pos())&&!GolemSeals.stopped(level,s)) {
            var area=new SealArea(GolemProvisioning.location(s.key()),new SealArea.BlockPos(s.area().getX(),s.area().getY(),s.area().getZ()));
            int index=scan.getOrDefault(s.key(),0); var target=area.positionAt(index); scan.put(s.key(),(index+1)%area.count()); var pos=new BlockPos(target.x(),target.y(),target.z()); if(!wood(level,pos)) continue;
            var positions=watched.computeIfAbsent(s.key(),k->new HashMap<>()); var old=positions.get(pos); if(old!=null&&data.tasks().get(old).filter(t->!t.isSuspended()&&!t.isCompleted()).isPresent()) continue;
            var task=GolemTask.block(data.nextId(),GolemProvisioning.location(s.key()),target); task.setPriority(s.priority());
            if(data.enqueue(task,s)) { positions.put(pos,task.getId()); work.put(task.getId(),new Work(s,pos,level.getBlockState(pos))); }
        }
    }
    GolemSeals.SealKey destination(GolemTask task) { var w=work.get(task.getId()); return w==null?null:new GolemSeals.SealKey(w.origin,Direction.UP); }
    boolean eligible(ServerLevel level,ThaumcraftGolem g,GolemTask task,GolemSeals.Seal seal) {
        var w=work.get(task.getId());
        if(w==null||w.seal!=seal||!GolemLumberRules.eligible(g.design().traits())||g.hasTransferRecovery()||!wood(level,w.origin)||g.home().distSqr(w.origin)>=(double)g.design().stats().homeRadius()*g.design().stats().homeRadius()) return false;
        if(!level.getBlockState(w.origin).equals(w.original)) { data.cancel(task.getId()); return false; } return true;
    }
    boolean ongoing(ServerLevel level,ThaumcraftGolem g,GolemSeals.BlockAssignment assignment) { return work.containsKey(assignment.lease().taskId())&&data.blockTarget(level,g,assignment)!=null; }
    boolean complete(ServerLevel level,ThaumcraftGolem g,GolemSeals.BlockAssignment assignment) {
        if(data.blockTarget(level,g,assignment)==null) return false; var task=data.tasks().task(assignment.lease()).orElse(null); var w=task==null?null:work.get(task.getId());
        if(w==null||g.distanceToSqr(Vec3.atCenterOf(w.origin))>4||nextCallback.getOrDefault(task.getId(),0L)>level.getGameTime()) return false;
        nextCallback.put(task.getId(),level.getGameTime()+GolemBreakerRules.CALLBACK_TICKS);
        var origin=new GolemLumberRules.Pos(w.origin.getX(),w.origin.getY(),w.origin.getZ()); var identity=identity(level,w.origin,w.original);
        var target=GolemLumberRules.furthest(origin,p->{ var pos=new BlockPos(p.x(),p.y(),p.z());
            if(level.isOutsideBuildHeight(pos)) return GolemLumberRules.Cell.OTHER;
            if(!GolemSeals.active(level,pos)) return GolemLumberRules.Cell.UNKNOWN;
            var state=level.getBlockState(pos); if(state.isAir()||state.getDestroySpeed(level,pos)<0) return GolemLumberRules.Cell.OTHER;
            return identity(level,pos,state).equals(identity)?GolemLumberRules.Cell.MATCH:GolemLumberRules.Cell.OTHER;
        });
        if(target.isEmpty()) return false; // Pause at unknown chunks rather than loading them.
        var p=target.get(); var pos=new BlockPos(p.x(),p.y(),p.z()); g.swing(InteractionHand.MAIN_HAND);
        if(!GolemBlockHarvest.harvest(level,g,pos,false)) { data.cancel(task.getId()); return false; }
        g.addRankXp(1); task.setLifespan((short)Math.max(10,task.getLifespan()));
        for(int x=-3;x<=3;x++) for(int y=-3;y<=3;y++) for(int z=-3;z<=3;z++) { var neighbor=pos.offset(x,y,z); if(loaded(level,neighbor)) level.scheduleTick(neighbor,level.getBlockState(neighbor).getBlock(),50+level.random.nextInt(75)); }
        if(!wood(level,w.origin)) { data.finish(assignment.lease()); return true; }
        return false;
    }
}
