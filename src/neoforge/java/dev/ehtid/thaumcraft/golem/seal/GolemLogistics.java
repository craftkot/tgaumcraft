package dev.ehtid.thaumcraft.golem.seal;

import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import dev.ehtid.thaumcraft.golem.inventory.GolemContainers;
import dev.ehtid.thaumcraft.port.core.golem.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/** Uses the existing world's task board, block goal, actual payload transfer and recovery. */
final class GolemLogistics {
    private record Work(ProvisionRequest<GolemProvisioning.ItemKey> request,
                        ProvisionBoard<GolemProvisioning.ItemKey> requests,
                        GolemSeals.Seal provider, GolemSeals.Seal recipient) {}
    private final GolemSeals data;
    private final Map<Integer,Work> work=new HashMap<>();
    private final Map<Integer,GolemTask> taskRefs=new HashMap<>();
    private final Map<ProvisionRequest<GolemProvisioning.ItemKey>,GolemSeals.Seal> recipients=new IdentityHashMap<>();
    GolemLogistics(GolemSeals data) { this.data=data; }

    void changed(GolemSeals.SealKey key) {
        for(var entry:new ArrayList<>(work.entrySet())) {
            var w=entry.getValue();
            if(w.provider.key().equals(key) || w.recipient!=null && w.recipient.key().equals(key)) {
                w.requests.invalidate(w.request); data.cancel(entry.getKey());
            }
        }
        // Requests without a task must also stop when a recipient is removed or reconfigured.
        for(var entry:new ArrayList<>(recipients.entrySet())) if(entry.getValue().key().equals(key)) {
            var r=entry.getKey();
            // Every linked request is already invalidated above; unlinked ones are rejected by snapshot below.
            recipients.remove(r);
            invalidRecipients.put(r,Boolean.TRUE);
        }
    }
    private final Map<ProvisionRequest<GolemProvisioning.ItemKey>,Boolean> invalidRecipients=new IdentityHashMap<>();

    void forget(int id) {
        var w=work.remove(id);
        var task=taskRefs.remove(id);
        if(w!=null && task!=null) w.requests.unlink(w.request,task);
    }

    void tick(ServerLevel level) {
        var requests=GolemProvisioning.get(level);
        for(var request:new ArrayList<>(invalidRecipients.keySet())) requests.invalidate(request);
        invalidRecipients.clear();
        var active=requests.snapshot();
        recipients.keySet().removeIf(r->r.invalid() || !active.contains(r));
        for(var entry:new ArrayList<>(work.entrySet())) {
            var task=data.tasks().get(entry.getKey()).orElse(null); var w=entry.getValue();
            if(task==null || task.isSuspended() || w.request.invalid() || w.request.linkedTask()!=task
                    || !valid(level,w)) { w.requests.invalidate(w.request); data.cancel(entry.getKey()); }
        }
        if(level.getGameTime()%ProvisionRules.INTERVAL_TICKS!=0) return;
        for(var seal:data.all()) if(seal.type()==GolemSeals.Type.STOCK && usable(level,seal))
            for(var request:GolemProvisioning.stock(level,seal.key(),seal.logistics().slots())) recipients.putIfAbsent(request,seal);
        for(var provider:data.all()) if(provider.type()==GolemSeals.Type.PROVIDE && usable(level,provider)) {
            for(var request:requests.snapshot()) {
                if(request.invalid() || request.linkedTask()!=null || request.timeout()<System.currentTimeMillis()
                        || !provider.logistics().accepts(request.item().stack(1))) continue;
                var recipient=recipients.get(request);
                if(request.target() instanceof ProvisionRequest.SealTarget target) {
                    var location=target.location(); var key=new GolemSeals.SealKey(new BlockPos(location.pos().x(),location.pos().y(),location.pos().z()),Direction.values()[location.face().ordinal()]);
                    recipient=data.get(key);
                    if(recipient==null || recipient.type()!=GolemSeals.Type.HARVEST || !recipient.instanceId().equals(target.instanceId())) continue;
                }
                var w=new Work(request,requests,provider,recipient);
                if(!valid(level,w)) continue;
                var destination=destination(level,w);
                if(destination==null || provider.key().equals(destination)
                        || !ProvisionRules.inProviderRange(provider.key().pos().distSqr(destination.pos()))) continue;
                if(!(request.target() instanceof ProvisionRequest.SealTarget) && resumeCargo(level,w)) break;
                var stack=request.item().stack(request.count());
                if(strictStock(GolemContainers.handler(level,provider.key()),stack)<= (provider.leaveOne()?1:0)) continue;
                var task=ProvisionRules.pickup(data.nextId(),GolemProvisioning.location(provider.key()),request,recipient==null?(byte)5:recipient.priority());
                if(!data.enqueue(task,provider)) break;
                if(!requests.link(request,null,task)) { data.cancel(task.getId()); continue; }
                remember(task,w); break;
            }
        }
    }

    private void remember(GolemTask task,Work w) { work.put(task.getId(),w); taskRefs.put(task.getId(),task); }

    /** Protective restart/cancellation recovery: regenerate delivery from real persisted hands, never another debit. */
    private boolean resumeCargo(ServerLevel level,Work w) {
        for(var golem:level.getEntitiesOfClass(ThaumcraftGolem.class,new net.minecraft.world.phys.AABB(w.provider.key().pos()).inflate(64))) {
            if(!golem.isAlive() || golem.isFollowingOwner() || golem.design().hasTrait(GolemTrait.CLUMSY)
                    || golem.hasTransferRecovery() || carried(golem,w).isEmpty()
                    || w.provider.locked() && !w.provider.owner().equals(golem.ownerId())
                    || !TaskSelection.colorMatches(golem.golemColor(),w.provider.color())) continue;
            if(taskRefs.values().stream().anyMatch(t->golem.getUUID().equals(t.getGolemUUID()) && !t.isSuspended())) continue;
            var pickup=ProvisionRules.pickup(data.nextId(),GolemProvisioning.location(w.provider.key()),w.request,(byte)5);
            var next=ProvisionRules.delivery(data.nextId(),pickup,w.request); next.setGolemUUID(golem.getUUID());
            if(!data.enqueue(next,w.provider)) return false;
            if(!w.requests.link(w.request,null,next)) { data.cancel(next.getId()); return false; }
            remember(next,w);
            if(!eligible(level,golem,next,w.provider)) { w.requests.unlink(w.request,next); data.cancel(next.getId()); continue; }
            return true;
        }
        return false;
    }

    private boolean usable(ServerLevel level,GolemSeals.Seal seal) {
        return data.get(seal.key())==seal && !GolemSeals.stopped(level,seal)
                && GolemContainers.handler(level,seal.key())!=null;
    }
    private boolean valid(ServerLevel level,Work w) {
        if(!usable(level,w.provider)) return false;
        if(w.recipient!=null && (data.get(w.recipient.key())!=w.recipient || !level.hasChunkAt(w.recipient.key().pos())
                || level.isEmptyBlock(w.recipient.key().pos()) || GolemSeals.stopped(level,w.recipient))) return false;
        var target=destination(level,w);
        if(target==null || !level.hasChunkAt(target.pos()) || !level.hasChunkAt(target.pos().relative(target.face()))) return false;
        return !(w.request.target() instanceof ProvisionRequest.BlockTarget) || GolemContainers.handler(level,target)!=null;
    }
    private GolemSeals.SealKey destination(ServerLevel level,Work w) {
        if(w.request.target() instanceof ProvisionRequest.SealTarget seal) {
            var p=seal.location(); return new GolemSeals.SealKey(new BlockPos(p.pos().x(),p.pos().y(),p.pos().z()),Direction.values()[p.face().ordinal()]);
        }
        if(w.request.target() instanceof ProvisionRequest.BlockTarget block) {
            var p=block.location();
            return new GolemSeals.SealKey(new BlockPos(p.pos().x(),p.pos().y(),p.pos().z()),Direction.values()[p.face().ordinal()]);
        }
        if(w.request.target() instanceof ProvisionRequest.EntityTarget entity) {
            var target=level.getEntity(entity.entityId());
            return target==null || !target.isAlive()?null:new GolemSeals.SealKey(target.blockPosition(),Direction.UP);
        }
        return null;
    }
    GolemSeals.SealKey destination(ServerLevel level,GolemTask task) {
        var w=work.get(task.getId());
        return w==null?null:task.getData()==0?w.provider.key():destination(level,w);
    }

    private static ItemStack carried(ThaumcraftGolem golem,Work w) {
        var template=w.request.item().stack(1);
        for(var slot:new EquipmentSlot[]{EquipmentSlot.MAINHAND,EquipmentSlot.OFFHAND}) {
            if(slot==EquipmentSlot.OFFHAND && golem.design().stats().carrySlots()<2) continue;
            var stack=golem.getItemBySlot(slot);
            if(ItemStack.isSameItemSameComponents(template,stack)) return stack;
        }
        return ItemStack.EMPTY;
    }
    boolean eligible(ServerLevel level,ThaumcraftGolem golem,GolemTask task,GolemSeals.Seal provider) {
        var w=work.get(task.getId());
        if(w==null || w.provider!=provider || w.request.invalid() || w.request.linkedTask()!=task || !valid(level,w)) return false;
        var target=destination(level,w);
        if(target==null || golem.home().distSqr(target.pos()) >= (double)golem.design().stats().homeRadius()*golem.design().stats().homeRadius()) return false;
        if(w.recipient!=null && ((w.recipient.locked() && !w.recipient.owner().equals(golem.ownerId()))
                || !TaskSelection.colorMatches(golem.golemColor(),w.recipient.color()))) return false;
        if(w.recipient!=null && w.recipient.type()==GolemSeals.Type.HARVEST && !GolemFarmRules.eligible(golem.design().traits())) return false;
        if(task.getData()!=0) return !carried(golem,w).isEmpty();
        if(!carried(golem,w).isEmpty() || golem.hasTransferRecovery()) return false;
        var stack=w.request.item().stack(w.request.count());
        int amount=ProvisionRules.pickupAmount(w.request.count(),golem.canCarryAmount(stack),
                strictStock(GolemContainers.handler(level,provider.key()),stack),provider.logistics().single(),provider.leaveOne());
        return amount>0 && GolemContainers.canExtract(level,golem,provider.key(),stack.copyWithCount(amount),
                new SealFilter(stack,false,0),provider.leaveOne());
    }
    boolean complete(ServerLevel level,ThaumcraftGolem golem,GolemSeals.BlockAssignment assignment) {
        if(data.blockTarget(level,golem,assignment)==null) return false;
        var task=data.tasks().task(assignment.lease()).orElse(null);
        var w=task==null?null:work.get(task.getId());
        if(w==null || !assignment.seal().equals(w.provider.key()) || !eligible(level,golem,task,w.provider)) return false;
        var target=destination(level,task);
        if(target==null || golem.distanceToSqr(Vec3.atCenterOf(target.pos()))>4) return false;
        if(task.getData()==0) {
            var stack=w.request.item().stack(w.request.count());
            int amount=ProvisionRules.pickupAmount(w.request.count(),golem.canCarryAmount(stack),
                    strictStock(GolemContainers.handler(level,target),stack),w.provider.logistics().single(),w.provider.leaveOne());
            int moved=GolemContainers.extractInto(golem,GolemContainers.handler(level,target),stack.copyWithCount(amount),
                    new SealFilter(stack,false,0),w.provider.leaveOne());
            if(moved>0 && !carried(golem,w).isEmpty()) {
                if(w.request.target() instanceof ProvisionRequest.SealTarget) {
                    w.requests.invalidate(w.request); // Recipient's existing replant task uses the real carried seed.
                } else {
                var next=ProvisionRules.delivery(data.nextId(),task,w.request);
                next.setGolemUUID(golem.getUUID()); // Keep the paid cargo with its worker.
                if(data.enqueue(next,w.provider) && w.requests.link(w.request,task,next)) remember(next,w);
                else { data.cancel(next.getId()); w.requests.invalidate(w.request); }
                }
                golem.addRankXp(1);
            } else w.requests.invalidate(w.request);
            data.finish(assignment.lease());
            golem.swing(InteractionHand.MAIN_HAND); return moved>0;
        }
        var stack=carried(golem,w); int budget=Math.min(w.request.count(),stack.getCount());
        int moved=0;
        if(w.request.target() instanceof ProvisionRequest.EntityTarget) {
            var drop=new ItemEntity(level,target.pos().getX()+.5,target.pos().getY()+.5,target.pos().getZ()+.5,stack.copyWithCount(budget));
            if(level.addFreshEntity(drop)) { stack.shrink(budget); moved=budget; }
        } else {
            var handler=GolemContainers.handler(level,target);
            if(w.recipient!=null) {
                var filter=w.recipient.logistics().recipientFilter(stack);
                budget=filter==null?0:Math.min(budget,filter.transferable(stack,matchingStock(handler,stack,filter)));
            }
            if(budget>0) moved=GolemContainers.insertCarried(golem,handler,new SealFilter(stack,false,0),budget);
        }
        // A changed/full recipient keeps the actual remainder on the worker, never on the ground.
        // An explicit API recipient may reject a drop/insertion temporarily. Keep this task/request
        // alive so the goal's existing failure cooldown can retry without another source extraction.
        if(moved==0 && w.recipient==null) return false;
        w.requests.invalidate(w.request);
        if(w.recipient==null && moved<w.request.count())
            GolemProvisioning.request(level,w.request.target(),w.request.item().stack(w.request.count()-moved),w.request.key().ui());
        data.finish(assignment.lease());
        golem.swing(InteractionHand.MAIN_HAND); return true;
    }
    private static long strictStock(net.neoforged.neoforge.items.IItemHandler handler,ItemStack template) {
        return matchingStock(handler,template,new SealFilter(template,false,0));
    }
    private static long matchingStock(net.neoforged.neoforge.items.IItemHandler handler,ItemStack template,SealFilter filter) {
        long count=0;
        if(handler!=null) for(int i=0;i<handler.getSlots();i++) if(filter.matches(template,handler.getStackInSlot(i))) count+=handler.getStackInSlot(i).getCount();
        return count;
    }
}
