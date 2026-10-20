package dev.ehtid.thaumcraft.golem.seal;

import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import dev.ehtid.thaumcraft.port.core.golem.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.animal.*;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import java.util.*;

/** Source living-target task producers. Assignment ends immediately; independent native melee performs the fight. */
final class GolemCombat {
    private record Work(GolemSeals.Seal seal,UUID target) {}
    private final GolemSeals data;
    private final Map<Integer,Work> work=new HashMap<>();
    private final Map<GolemSeals.SealKey,GolemCombatRules.Settings> settings=new HashMap<>();
    private final Map<GolemSeals.SealKey,Map<UUID,Integer>> watched=new HashMap<>();
    GolemCombat(GolemSeals data) { this.data=data; }
    GolemCombatRules.Settings settings(GolemSeals.Seal seal) { return settings.getOrDefault(seal.key(),GolemCombatRules.Settings.defaults()); }
    void configure(GolemSeals.Seal seal,GolemCombatRules.Settings value) { settings.put(seal.key(),value); }
    void removed(GolemSeals.SealKey key) { settings.remove(key); watched.remove(key); }
    void forget(int id) { var w=work.remove(id); if(w!=null) { var map=watched.get(w.seal.key()); if(map!=null) map.remove(w.target,id); } }
    static GolemCombatRules.Target classification(LivingEntity target) {
        boolean monster=target instanceof Enemy;
        boolean legacyAnimal=monster||target instanceof Animal||target instanceof WaterAnimal||target instanceof AmbientCreature||target instanceof AbstractVillager||target instanceof AbstractGolem;
        return new GolemCombatRules.Target(monster,legacyAnimal,target instanceof Player,
                target instanceof Animal animal&&animal.isBaby(),target instanceof TamableAnimal tame&&tame.isTame(),target instanceof AbstractGolem);
    }
    private boolean valid(ServerLevel level,GolemSeals.Seal seal,LivingEntity target) {
        return target.isAlive()&&!target.isRemoved()&&level.hasChunkAt(target.blockPosition())
                &&(seal.type()==GolemSeals.Type.BUTCHER?GolemCombatRules.butcher(classification(target))
                    :GolemCombatRules.guard(settings(seal),classification(target),level.getServer().isPvpAllowed()));
    }
    private LivingEntity target(ServerLevel level,Work w) { return level.getEntity(w.target) instanceof LivingEntity living?living:null; }
    void tick(ServerLevel level) {
        for(var entry:new ArrayList<>(work.entrySet())) {
            var target=target(level,entry.getValue());
            if(target==null||!target.isAlive()||!level.hasChunkAt(target.blockPosition())) data.cancel(entry.getKey());
        }
        for(var seal:data.all()) {
            if(!seal.type().combat()||!level.hasChunkAt(seal.key().pos())||GolemSeals.stopped(level,seal)) continue;
            boolean butcher=seal.type()==GolemSeals.Type.BUTCHER;
            if(level.getGameTime()%(butcher?GolemCombatRules.BUTCHER_INTERVAL:GolemCombatRules.GUARD_INTERVAL)!=0) continue;
            var map=watched.computeIfAbsent(seal.key(),k->new HashMap<>());
            if(butcher&&!map.isEmpty()) continue; // Source transient wait until started/suspended/expired.
            var candidates=level.getEntitiesOfClass(LivingEntity.class,GolemSeals.bounds(seal),e->valid(level,seal,e));
            for(var target:candidates) {
                if(map.containsKey(target.getUUID())) continue;
                if(butcher&&!GolemCombatRules.excess((int)candidates.stream().filter(target.getClass()::isInstance).count())) continue;
                var task=GolemTask.entity(data.nextId(),GolemProvisioning.location(seal.key()),target.getUUID()); task.setPriority(seal.priority()); task.setLifespan(GolemCombatRules.TASK_LIFESPAN);
                if(data.enqueue(task,seal)) { work.put(task.getId(),new Work(seal,target.getUUID())); map.put(target.getUUID(),task.getId()); }
                if(butcher) break;
            }
        }
    }
    boolean eligible(ServerLevel level,ThaumcraftGolem golem,GolemTask task,GolemSeals.Seal seal) {
        var w=work.get(task.getId()); var target=w==null?null:target(level,w);
        return w!=null&&w.seal==seal&&target!=null&&!golem.isFollowingOwner()&&golem.isAlive()&&!golem.isRemoved()
                &&GolemCombatRules.eligible(golem.design().traits(),seal.type()!=GolemSeals.Type.GUARD)
                &&!task.isSuspended()&&!task.isCompleted()&&!GolemSeals.stopped(level,seal)
                &&(!seal.locked()||seal.owner().equals(golem.ownerId()))&&TaskSelection.colorMatches(golem.golemColor(),seal.color())
                &&valid(level,seal,target)&&(seal.type()==GolemSeals.Type.BUTCHER||!golem.isAlliedTo(target))
                &&golem.home().distSqr(target.blockPosition())<(double)golem.design().stats().homeRadius()*golem.design().stats().homeRadius();
    }
    boolean start(ServerLevel level,ThaumcraftGolem golem,GolemSeals.Assignment assignment) {
        if(golem.level()!=level||!assignment.lease().owner().equals(golem.getUUID())) return false;
        var task=data.tasks().task(assignment.lease()).orElse(null); var seal=data.get(assignment.seal());
        if(task==null||seal==null||!seal.type().combat()||!task.getEntityId().equals(assignment.targetId())||!eligible(level,golem,task,seal)) return false;
        var target=target(level,work.get(task.getId())); golem.setTarget(target); golem.addRankXp(1); data.finish(assignment.lease()); return true;
    }
    void loadRow(CompoundTag row,GolemSeals.Seal seal) { if(seal.type()==GolemSeals.Type.GUARD_ADVANCED) configure(seal,new GolemCombatRules.Settings(!row.contains("pmob")||row.getBoolean("pmob"),row.getBoolean("panimal"),row.getBoolean("pplayer"))); }
    void saveRow(CompoundTag row,GolemSeals.Seal seal) { if(seal.type()==GolemSeals.Type.GUARD_ADVANCED) { var s=settings(seal); row.putBoolean("pmob",s.mobs()); row.putBoolean("panimal",s.animals()); row.putBoolean("pplayer",s.players()); } }
}
