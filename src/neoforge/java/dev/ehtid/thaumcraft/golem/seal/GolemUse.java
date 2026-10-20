package dev.ehtid.thaumcraft.golem.seal;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import dev.ehtid.thaumcraft.golem.inventory.GolemContainers;
import dev.ehtid.thaumcraft.port.core.golem.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import java.util.*;

/** Source SealUse: one held actual stack -> protected fake-player block click -> actual remainders. */
final class GolemUse {
    private record Work(GolemSeals.Seal seal) {}
    private final GolemSeals data;
    private final Map<GolemSeals.SealKey,GolemUseRules.Settings> settings=new HashMap<>();
    private final Map<Integer,Work> work=new HashMap<>();
    private final Map<GolemSeals.SealKey,Integer> watched=new HashMap<>();
    private final Set<UUID> busy=new HashSet<>();
    GolemUse(GolemSeals data) { this.data=data; }
    GolemUseRules.Settings settings(GolemSeals.Seal seal) { return settings.getOrDefault(seal.key(),GolemUseRules.Settings.defaults()); }
    void configure(GolemSeals.Seal seal,GolemUseRules.Settings value) { settings.put(seal.key(),value); }
    void removed(GolemSeals.SealKey key) { settings.remove(key); watched.remove(key); }
    void forget(int id) { var w=work.remove(id); if(w!=null) watched.remove(w.seal.key(),id); }
    private static boolean loaded(ServerLevel level,BlockPos pos) { return !level.isOutsideBuildHeight(pos)&&level.hasChunkAt(pos); }
    void tick(ServerLevel level) {
        if(level.getGameTime()%GolemUseRules.SCAN_TICKS!=0) return;
        for(var s:data.all()) if(s.type()==GolemSeals.Type.USE&&loaded(level,s.key().pos())&&!GolemSeals.stopped(level,s)&&GolemUseRules.target(settings(s),level.isEmptyBlock(s.key().pos()))) {
            var old=watched.get(s.key()); if(old!=null&&data.tasks().get(old).filter(t->!t.isSuspended()&&!t.isCompleted()).isPresent()) continue;
            var task=GolemTask.block(data.nextId(),GolemProvisioning.location(s.key()),new SealArea.BlockPos(s.key().pos().getX(),s.key().pos().getY(),s.key().pos().getZ())); task.setPriority(s.priority());
            if(data.enqueue(task,s)) { watched.put(s.key(),task.getId()); work.put(task.getId(),new Work(s)); }
        }
    }
    private EquipmentSlot carrying(ThaumcraftGolem g,GolemSeals.Seal s) {
        for(var slot:new EquipmentSlot[]{EquipmentSlot.MAINHAND,EquipmentSlot.OFFHAND}) {
            if(slot==EquipmentSlot.OFFHAND&&g.design().stats().carrySlots()<2) continue;
            if(s.filter().accepts(g.getItemBySlot(slot))) return slot;
        }
        return null;
    }
    /** Bind source wildcard metadata to an actual available payload; generic provider transactions remain STRICT. */
    private ItemStack provision(ServerLevel level,ThaumcraftGolem g,GolemSeals.Seal recipient) {
        var template=recipient.filter().item(); if(recipient.filter().options().metadata()) return template;
        var options=new SealFilterRules.Options(false,true,false,false); // Source request wildcard preserves original NBT, independently of Use pnbt.
        for(var source:data.all()) if(source.type()==GolemSeals.Type.PROVIDE&&loaded(level,source.key().pos())&&!GolemSeals.stopped(level,source)
                &&(!source.locked()||source.owner().equals(g.ownerId()))&&TaskSelection.colorMatches(source.color(),g.golemColor())
                &&g.home().distSqr(source.key().pos())<(double)g.design().stats().homeRadius()*g.design().stats().homeRadius()
                &&ProvisionRules.inProviderRange(source.key().pos().distSqr(recipient.key().pos()))) {
            var inv=GolemContainers.handler(level,source.key()); if(inv==null) continue;
            for(int i=0;i<inv.getSlots();i++) {
                var actual=inv.getStackInSlot(i); if(actual.isEmpty()||!GolemStackMatcher.matches(template,actual,options)||!source.logistics().accepts(actual)) continue;
                int stock=0; for(int j=0;j<inv.getSlots();j++) { var candidate=inv.getStackInSlot(j); if(ItemStack.isSameItemSameComponents(actual,candidate)) stock+=candidate.getCount(); }
                if(stock>(source.leaveOne()?1:0)) return actual.copyWithCount(1);
            }
        }
        return ItemStack.EMPTY; // No chunk loads or ghost-converted request payload; next scan sees newly available variants.
    }
    boolean eligible(ServerLevel level,ThaumcraftGolem g,GolemTask task,GolemSeals.Seal seal) {
        var w=work.get(task.getId()); var config=settings(seal);
        if(w==null||w.seal!=seal||!GolemUseRules.eligible(g.design().traits())||g.hasTransferRecovery()||!loaded(level,seal.key().pos())||!GolemUseRules.target(config,level.isEmptyBlock(seal.key().pos()))) return false;
        boolean has=carrying(g,seal)!=null;
        if(GolemUseRules.request(config,has,seal.filter().blacklist(),seal.filter().item().isEmpty())) {
            var request=provision(level,g,seal); if(!request.isEmpty()) GolemProvisioning.request(level,new ProvisionRequest.SealTarget(GolemProvisioning.location(seal.key()),seal.instanceId()),request,0);
        }
        return GolemUseRules.carrying(config,has);
    }
    boolean complete(ServerLevel level,ThaumcraftGolem g,GolemSeals.BlockAssignment assignment) {
        if(data.blockTarget(level,g,assignment)==null) return false; var task=data.tasks().task(assignment.lease()).orElse(null); var w=task==null?null:work.get(task.getId());
        if(w==null||g.distanceToSqr(Vec3.atCenterOf(w.seal.key().pos()))>4) return false;
        var config=settings(w.seal); var carried=carrying(g,w.seal);
        int hand=GolemUseRules.completionHand(w.seal.filter().item().isEmpty(),carried==EquipmentSlot.MAINHAND,carried==EquipmentSlot.OFFHAND);
        var slot=hand==1?EquipmentSlot.OFFHAND:EquipmentSlot.MAINHAND;
        if(!config.emptyHand()&&(hand<0||g.getItemBySlot(slot).isEmpty())) { data.finish(assignment.lease()); return true; } // Source suspends without a click or XP.
        if(!busy.add(g.getUUID())) return false;
        var actor=FakePlayerFactory.get(level,new GameProfile(g.ownerId()==null?g.getUUID():g.ownerId(),"TCU"+g.getUUID().toString().replace("-","").substring(0,13)));
        var pos=w.seal.key().pos(); var face=w.seal.key().face();
        actor.setGameMode(GameType.SURVIVAL); actor.moveTo(g.getX(),g.getY(),g.getZ(),g.getYRot(),g.getXRot()); actor.setShiftKeyDown(config.sneak());
        if(!config.emptyHand()) { var actual=g.getItemBySlot(slot); g.setItemSlot(slot,ItemStack.EMPTY); actor.setItemInHand(InteractionHand.MAIN_HAND,actual); }
        else actor.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY); // Keep real cargo safe; source's empty-hand pre-debit could discard it.
        try {
            try {
                if(config.left()) actor.gameMode.handleBlockBreakAction(pos,ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,face,level.getMaxBuildHeight(),0);
                else actor.gameMode.useItemOn(actor,level,actor.getMainHandItem(),InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(face.getNormal()).scale(.5)),face,pos,false));
            } catch(RuntimeException ex) { LogUtils.getLogger().warn("Golem Use callback failed; actual inventory will be recovered",ex); }
            // Source awards one SMART XP for the attempted click, even when the action returned PASS/FAIL.
            data.finish(assignment.lease()); g.addRankXp(1); g.swing(InteractionHand.MAIN_HAND); return true;
        } finally {
            try { if(config.left()) actor.gameMode.handleBlockBreakAction(pos,ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK,face,level.getMaxBuildHeight(),0); } catch(RuntimeException ex) { LogUtils.getLogger().warn("Golem Use dig cleanup failed; recovering actual inventory",ex); }
            try {
                var cursor=actor.containerMenu.getCarried(); actor.containerMenu.setCarried(ItemStack.EMPTY); if(!cursor.isEmpty()) g.retainTransfer(cursor);
                actor.closeContainer();
            } finally {
                try { for(int i=0;i<actor.getInventory().getContainerSize();i++) { var actual=actor.getInventory().removeItemNoUpdate(i); if(!actual.isEmpty()) g.retainTransfer(actual); } }
                finally { actor.setShiftKeyDown(false); busy.remove(g.getUUID()); }
            }
        }
    }
    void saveRow(CompoundTag row,GolemSeals.Seal s) { var c=settings(s); row.putBoolean("pleft",c.left()); row.putBoolean("pempty",c.empty()); row.putBoolean("pemptyhand",c.emptyHand()); row.putBoolean("psneak",c.sneak()); row.putBoolean("ppro",c.provision()); }
    void loadRow(CompoundTag row,GolemSeals.Seal s) { configure(s,new GolemUseRules.Settings(row.getBoolean("pleft"),row.getBoolean("pempty"),row.getBoolean("pemptyhand"),row.getBoolean("psneak"),row.getBoolean("ppro"))); }
}
