package dev.ehtid.thaumcraft.golem.seal;

import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import dev.ehtid.thaumcraft.port.core.golem.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Source SealBreaker work/callbacks on the shared task board; modern protected real block loot. */
final class GolemBreaker {
    private record Work(GolemSeals.Seal seal,BlockPos pos,BlockState original) {}
    private record Crack(ServerLevel level,int worker,BlockPos pos) {}
    private final GolemSeals data;
    private final Map<Integer,Work> work=new HashMap<>();
    private final Map<GolemSeals.SealKey,Map<BlockPos,Integer>> watched=new HashMap<>();
    private final Map<GolemSeals.SealKey,Integer> scan=new HashMap<>();
    private final Map<Integer,Crack> cracks=new HashMap<>();
    private final Map<Integer,Long> nextCallback=new HashMap<>();
    private final Set<UUID> busy=new HashSet<>();
    GolemBreaker(GolemSeals data) { this.data=data; }
    void removed(GolemSeals.SealKey key) { watched.remove(key); scan.remove(key); }
    void forget(int id) {
        var w=work.remove(id); if(w!=null) { var positions=watched.get(w.seal.key()); if(positions!=null) positions.remove(w.pos,id); }
        nextCallback.remove(id); clearCrack(id);
    }
    void released(int id) { clearCrack(id); nextCallback.remove(id); }
    private void clearCrack(int id) { var crack=cracks.remove(id); if(crack!=null) crack.level.destroyBlockProgress(crack.worker,crack.pos,-1); }
    private static boolean loaded(ServerLevel level,BlockPos pos) { return !level.isOutsideBuildHeight(pos) && level.hasChunkAt(pos); }
    private static boolean valid(ServerLevel level,BlockPos pos,GolemSeals.Seal seal) {
        if(!loaded(level,pos)) return false;
        var state=level.getBlockState(pos);
        if(!GolemBreakerRules.breakable(state.isAir(),state.getDestroySpeed(level,pos))) return false;
        var ghost=seal.filter().item();
        if(ghost.isEmpty()) return true;
        var actual=state.getBlock().getCloneItemStack(level,pos,state);
        var a=GolemStackMatcher.identity(actual); var b=GolemStackMatcher.identity(ghost);
        return GolemBreakerRules.accepts(false,seal.filter().blacklist(),a.id().equals(b.id()),a.metadata(),b.metadata(),seal.filter().options().metadata());
    }
    void tick(ServerLevel level) {
        for(var s:data.all()) if(s.type()==GolemSeals.Type.BREAKER && loaded(level,s.key().pos()) && !GolemSeals.stopped(level,s)) {
            var area=new SealArea(GolemProvisioning.location(s.key()),new SealArea.BlockPos(s.area().getX(),s.area().getY(),s.area().getZ()));
            int index=scan.getOrDefault(s.key(),0); var target=area.positionAt(index); scan.put(s.key(),(index+1)%area.count());
            var pos=new BlockPos(target.x(),target.y(),target.z()); if(!valid(level,pos,s)) continue;
            var positions=watched.computeIfAbsent(s.key(),k->new HashMap<>());
            Integer old=positions.get(pos); if(old!=null && data.tasks().get(old).filter(t->!t.isSuspended()&&!t.isCompleted()).isPresent()) continue;
            var task=GolemTask.block(data.nextId(),GolemProvisioning.location(s.key()),target); task.setPriority(s.priority()); task.setData(GolemBreakerRules.work(level.getBlockState(pos).getDestroySpeed(level,pos)));
            if(data.enqueue(task,s)) { positions.put(pos,task.getId()); work.put(task.getId(),new Work(s,pos,level.getBlockState(pos))); }
        }
    }
    GolemSeals.SealKey destination(GolemTask task) { var w=work.get(task.getId()); return w==null?null:new GolemSeals.SealKey(w.pos,Direction.UP); }
    boolean eligible(ServerLevel level,ThaumcraftGolem g,GolemTask task,GolemSeals.Seal seal) {
        var w=work.get(task.getId());
        if(w==null || w.seal!=seal || !GolemBreakerRules.eligible(g.design().traits()) || g.hasTransferRecovery() || !valid(level,w.pos,seal)
                || g.home().distSqr(w.pos)>=(double)g.design().stats().homeRadius()*g.design().stats().homeRadius()) return false;
        // A replacement block never inherits accumulated work from its predecessor.
        if(!level.getBlockState(w.pos).equals(w.original)) { data.cancel(task.getId()); return false; }
        return true;
    }
    boolean ongoing(ServerLevel level,ThaumcraftGolem g,GolemSeals.BlockAssignment assignment) { return work.containsKey(assignment.lease().taskId()) && data.blockTarget(level,g,assignment)!=null; }
    boolean complete(ServerLevel level,ThaumcraftGolem g,GolemSeals.BlockAssignment assignment) {
        if(data.blockTarget(level,g,assignment)==null) return false;
        var task=data.tasks().task(assignment.lease()).orElse(null); var w=task==null?null:work.get(task.getId());
        if(w==null || g.distanceToSqr(Vec3.atCenterOf(w.pos))>4 || !busy.add(g.getUUID())) return false;
        try {
            if(nextCallback.getOrDefault(task.getId(),0L)>level.getGameTime()) return false;
            nextCallback.put(task.getId(),level.getGameTime()+GolemBreakerRules.CALLBACK_TICKS); g.swing(InteractionHand.MAIN_HAND);
            if(!GolemBreakerRules.completes(task.getData(),false)) {
                task.setData(GolemBreakerRules.remaining(task.getData(),false)); task.setLifespan((short)Math.max(10,task.getLifespan()));
                cracks.put(task.getId(),new Crack(level,g.getId(),w.pos)); level.destroyBlockProgress(g.getId(),w.pos,GolemBreakerRules.crackStage(task.getData(),w.original.getDestroySpeed(level,w.pos)));
                var sound=w.original.getSoundType(level,w.pos,g); level.playSound(null,w.pos,sound.getHitSound(),SoundSource.BLOCKS,(sound.getVolume()+.7F)/8,sound.getPitch()*.5F); return false;
            }
            boolean success=GolemBlockHarvest.harvest(level,g,w.pos,true);
            if(!success) { data.cancel(task.getId()); return false; }
            data.finish(assignment.lease()); g.addRankXp(1); return true;
        } finally { busy.remove(g.getUUID()); }
    }
}
