package dev.ehtid.thaumcraft.golem.seal;

import com.mojang.authlib.GameProfile;
import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import dev.ehtid.thaumcraft.golem.api.GolemHarvestRegistry;
import dev.ehtid.thaumcraft.golem.inventory.GolemInteractionInventory;
import dev.ehtid.thaumcraft.port.core.golem.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.util.*;
import java.util.*;

/** Source Harvest scan -> shared task board -> real protected harvest and paid seed placement. */
final class GolemFarm {
    private record Plant(GolemFarmRules.Crop crop,Direction support,boolean farmland) {}
    private record Work(GolemSeals.Seal seal,BlockPos pos,boolean replant) {}
    private final GolemSeals data;
    private final Map<GolemSeals.SealKey,Map<BlockPos,Plant>> replants=new HashMap<>();
    private final Map<Integer,Work> work=new HashMap<>();
    private final Map<GolemSeals.SealKey,Map<BlockPos,Integer>> watched=new HashMap<>();
    private final Map<GolemSeals.SealKey,Integer> scan=new HashMap<>();
    private final Set<UUID> busy=new HashSet<>();
    GolemFarm(GolemSeals data) { this.data=data; }
    void removed(GolemSeals.SealKey key) { replants.remove(key); scan.remove(key); watched.remove(key); }
    void forget(int id) {
        var w=work.remove(id);
        if(w!=null) { var positions=watched.get(w.seal.key()); if(positions!=null) positions.remove(w.pos,id); }
    }
    static GolemFarmRules.Crop kind(BlockState state) {
        var id=BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if(!id.getNamespace().equals("minecraft")) return null;
        for(var crop:GolemFarmRules.Crop.values()) if(crop.block.equals(id.getPath())) return crop;
        return null;
    }
    private static boolean mature(ServerLevel level,BlockPos pos) {
        var state=level.getBlockState(pos);
        if(GolemHarvestRegistry.isClickable(state))return true;
        var crop=kind(state); if(crop==null) return false;
        int age=crop.readiness!=GolemFarmRules.Readiness.AGE?-1:state.getBlock() instanceof CropBlock block?block.getAge(state):state.is(Blocks.COCOA)?state.getValue(CocoaBlock.AGE):state.getValue(NetherWartBlock.AGE);
        return GolemFarmRules.ready(crop,age,level.getBlockState(pos.below()).is(state.getBlock()));
    }
    private static boolean loaded(ServerLevel level,BlockPos pos) {
        return !level.isOutsideBuildHeight(pos) && GolemSeals.active(level,pos) && GolemSeals.active(level,pos.below());
    }
    private static ItemStack seed(GolemFarmRules.Crop crop) { return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace(crop.seed))); }
    private static SealArea area(GolemSeals.Seal s) {
        return new SealArea(GolemProvisioning.location(s.key()),new SealArea.BlockPos(s.area().getX(),s.area().getY(),s.area().getZ()));
    }
    void tick(ServerLevel level) {
        for(var s:data.all()) if(s.type()==GolemSeals.Type.HARVEST) {
            var pending=replants.get(s.key()); var area=area(s);
            if(pending!=null && pending.keySet().removeIf(p->!area.bounds().contains(new SealArea.BlockPos(p.getX(),p.getY(),p.getZ())))) data.setDirty();
            if(level.getGameTime()%GolemFarmRules.SCAN_TICKS!=0 || !GolemSeals.active(level,s.key().pos()) || GolemSeals.stopped(level,s)) continue;
            int index=scan.getOrDefault(s.key(),0); var target=area.positionAt(index); scan.put(s.key(),(index+1)%area.count());
            var pos=new BlockPos(target.x(),target.y(),target.z());
            if(!loaded(level,pos)) continue;
            var current=level.getBlockState(pos); boolean harvest=mature(level,pos);
            boolean plant=!harvest && s.harvest().replant() && pending!=null && pending.containsKey(pos) && current.isAir();
            if(!harvest && !plant) continue;
            var positions=watched.computeIfAbsent(s.key(),k->new HashMap<>());
            Integer old=positions.get(pos);
            if(old!=null && data.tasks().get(old).filter(t->!t.isSuspended()&&!t.isCompleted()).isPresent()) continue;
            var task=GolemTask.block(data.nextId(),GolemProvisioning.location(s.key()),target); task.setPriority(s.priority()); task.setData(plant?1:0);
            if(data.enqueue(task,s)) { positions.put(pos,task.getId()); work.put(task.getId(),new Work(s,pos,plant)); }
        }
    }
    GolemSeals.SealKey destination(GolemTask task) {
        var w=work.get(task.getId()); return w==null?null:new GolemSeals.SealKey(w.pos,Direction.UP);
    }
    private static EquipmentSlot carrying(ThaumcraftGolem g,GolemFarmRules.Crop crop) {
        var template=seed(crop);
        for(var slot:new EquipmentSlot[]{EquipmentSlot.MAINHAND,EquipmentSlot.OFFHAND}) {
            if(slot==EquipmentSlot.OFFHAND && g.design().stats().carrySlots()<2) continue;
            if(ItemStack.isSameItemSameComponents(template,g.getItemBySlot(slot))) return slot;
        }
        return null;
    }
    boolean eligible(ServerLevel level,ThaumcraftGolem g,GolemTask task,GolemSeals.Seal s) {
        var w=work.get(task.getId());
        if(w==null || w.seal!=s || !GolemFarmRules.eligible(g.design().traits()) || g.hasTransferRecovery() || !loaded(level,w.pos)
                || g.home().distSqr(w.pos)>=(double)g.design().stats().homeRadius()*g.design().stats().homeRadius()) return false;
        if(!w.replant) return mature(level,w.pos);
        var plant=replants.getOrDefault(s.key(),Map.of()).get(w.pos);
        if(plant==null || !s.harvest().replant() || !level.isEmptyBlock(w.pos)) return false;
        if(!GolemSeals.active(level,w.pos.relative(plant.support))) return false;
        var crop=plant.crop;
        if(carrying(g,crop)!=null) return true;
        if(s.harvest().provision()) GolemProvisioning.request(level,new ProvisionRequest.SealTarget(GolemProvisioning.location(s.key()),s.instanceId()),seed(crop),0);
        return false;
    }
    boolean complete(ServerLevel level,ThaumcraftGolem g,GolemSeals.BlockAssignment assignment) {
        if(data.blockTarget(level,g,assignment)==null) return false;
        var task=data.tasks().task(assignment.lease()).orElse(null); var w=task==null?null:work.get(task.getId());
        if(w==null || !assignment.seal().equals(w.seal.key()) || data.get(w.seal.key())!=w.seal || GolemSeals.stopped(level,w.seal)
                || !eligible(level,g,task,w.seal) || g.distanceToSqr(Vec3.atCenterOf(w.pos))>4 || !busy.add(g.getUUID())) return false;
        var profile=new GameProfile(g.ownerId()==null?g.getUUID():g.ownerId(),"TCG"+g.getUUID().toString().replace("-","").substring(0,13));
        var actor=FakePlayerFactory.get(level,profile); actor.setGameMode(GameType.SURVIVAL); actor.moveTo(g.position());
        try {
            boolean success;
            if(!w.replant) {
                var before=level.getBlockState(w.pos); var crop=kind(before);
                if(GolemHarvestRegistry.isClickable(before)) {
                    actor.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
                    var face=GolemFarmRules.clickFace(new SealArea.BlockPos(w.pos.getX(),w.pos.getY(),w.pos.getZ()),g.getX(),g.getY(),g.getZ(),g.getEyeHeight(),SealArea.Facing.valueOf(g.getDirection().name()));
                    try {
                        actor.gameMode.useItemOn(actor,level,ItemStack.EMPTY,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atLowerCornerOf(w.pos),Direction.values()[face.ordinal()],w.pos,false));
                    } catch(RuntimeException ex) {com.mojang.logging.LogUtils.getLogger().warn("Golem Harvest click failed; recovering actual callback inventory",ex);}
                    // Source ignores onBlockActivated's boolean and grants XP for the attempted click; no replant intent.
                    data.finish(assignment.lease());g.addRankXp(1);g.swing(InteractionHand.MAIN_HAND);return true;
                }
                var support=before.is(Blocks.COCOA)?before.getValue(CocoaBlock.FACING):Direction.DOWN;
                boolean farmland=level.getBlockState(w.pos.below()).is(Blocks.FARMLAND);
                success=actor.gameMode.destroyBlock(w.pos);
                if(success && level.isEmptyBlock(w.pos) && crop.seed!=null && w.seal.harvest().replant()) {
                    replants.computeIfAbsent(w.seal.key(),k->new HashMap<>()).put(w.pos,new Plant(crop,support,farmland)); data.setDirty();
                }
            } else {
                var plant=replants.get(w.seal.key()).get(w.pos); var crop=plant.crop; var slot=carrying(g,crop);
                if(plant.farmland && (level.getBlockState(w.pos.below()).is(Blocks.DIRT) || level.getBlockState(w.pos.below()).is(Blocks.GRASS_BLOCK)
                        || level.getBlockState(w.pos.below()).is(Blocks.COARSE_DIRT))) {
                    actor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_HOE));
                    try { actor.getMainHandItem().useOn(context(actor,w.pos.below())); }
                    finally { actor.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY); } // Source built-in tool, never a player reward.
                }
                // Real ownership moves out of the golem before placement callbacks can reenter.
                var held=g.getItemBySlot(slot); var actual=held.split(GolemFarmRules.SEED_COST);
                actor.setItemInHand(InteractionHand.MAIN_HAND,actual);
                g.setItemSlot(slot,held);
                var placement=context(actor,w.pos.relative(plant.support),plant.support.getOpposite());
                // Modern BlockItem may target a replaceable clicked support itself. Never plant outside the assigned crop cell.
                if(!new BlockPlaceContext(placement).getClickedPos().equals(w.pos)) return false;
                actual.useOn(placement);
                success=kind(level.getBlockState(w.pos))==crop;
                if(success) {
                    replants.get(w.seal.key()).remove(w.pos); data.setDirty();
                }
            }
            if(!success) return false;
            data.finish(assignment.lease()); g.addRankXp(1); g.swing(InteractionHand.MAIN_HAND); return true;
        } finally {
            // Recover actual leftover seeds or legitimate auto-pickup inventory from mod callbacks.
            try {GolemInteractionInventory.recover(actor,g);}
            finally {busy.remove(g.getUUID());}
        }
    }
    private static UseOnContext context(FakePlayer actor,BlockPos support) {
        return context(actor,support,Direction.UP);
    }
    private static UseOnContext context(FakePlayer actor,BlockPos support,Direction outward) {
        var hit=Vec3.atCenterOf(support).add(Vec3.atLowerCornerOf(outward.getNormal()).scale(.5));
        return new UseOnContext(actor,InteractionHand.MAIN_HAND,new BlockHitResult(hit,outward,support,false));
    }
    void saveRow(CompoundTag row,GolemSeals.Seal s) {
        var list=new ListTag();
        for(var e:replants.getOrDefault(s.key(),Map.of()).entrySet()) { var entry=new CompoundTag(); entry.putLong("pos",e.getKey().asLong()); entry.putString("crop",e.getValue().crop.name()); entry.putByte("support",(byte)e.getValue().support.ordinal()); entry.putBoolean("farmland",e.getValue().farmland); list.add(entry); }
        if(!list.isEmpty()) row.put("ReplantTasks",list);
    }
    void loadRow(CompoundTag row,GolemSeals.Seal s) {
        if(s==null || s.type()!=GolemSeals.Type.HARVEST) return;
        var list=row.getList("ReplantTasks",Tag.TAG_COMPOUND); var area=area(s).bounds();
        for(int i=0;i<list.size();i++) try {
            var entry=list.getCompound(i); var pos=BlockPos.of(entry.getLong("pos")); var crop=GolemFarmRules.Crop.valueOf(entry.getString("crop"));
            int face=entry.contains("support",Tag.TAG_BYTE)?entry.getByte("support"):Direction.DOWN.ordinal();
            if(face<0 || face>=Direction.values().length) continue;
            var support=Direction.values()[face];
            if(crop==GolemFarmRules.Crop.COCOA?support.getAxis().isVertical():support!=Direction.DOWN) continue;
            boolean farmland=entry.contains("farmland")?entry.getBoolean("farmland"):crop.farmland;
            if(crop.seed!=null && area.contains(new SealArea.BlockPos(pos.getX(),pos.getY(),pos.getZ()))) replants.computeIfAbsent(s.key(),k->new HashMap<>()).putIfAbsent(pos,new Plant(crop,support,farmland));
        } catch(IllegalArgumentException ignored) { /* Unknown crops cannot become arbitrary block placement. */ }
    }
}
