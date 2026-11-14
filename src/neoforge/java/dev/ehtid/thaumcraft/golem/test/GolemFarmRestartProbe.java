package dev.ehtid.thaumcraft.golem.test;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.golem.seal.*;
import dev.ehtid.thaumcraft.port.core.golem.GolemDesign;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.UUID;

/** Opt-in real disk restart: harvest intent and several actual refund variants, no serialized leases. */
@EventBusSubscriber(modid=ThaumcraftPort.MOD_ID)
public final class GolemFarmRestartProbe {
    private static final String SCENARIO=System.getProperty("thaumcraft.golemRestartScenario","");
    private static final boolean COCOA=SCENARIO.equals("cocoa"), ENABLED=COCOA||SCENARIO.equals("farm");
    private static final String PHASE=System.getProperty("thaumcraft.golemRestartPhase","");
    private static final UUID OWNER=UUID.fromString("f3bad72b-daa0-411c-80b1-b938e50ef20c"), WORKER=UUID.fromString("37147f1e-429a-4caf-ab97-f15e33274a10"), REFUND=UUID.fromString("258fc93a-9748-44b9-ac14-cd7991512d78");
    private static final BlockPos CROP=new BlockPos(8,120,8);
    private static final GolemSeals.SealKey SEAL=new GolemSeals.SealKey(CROP.below(),Direction.UP);
    private static MinecraftServer active; private static int ticks,settled; private static boolean prepared,checked;
    private GolemFarmRestartProbe() {}
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if(!ENABLED || !PHASE.equals("write")&&!PHASE.equals("read")) return;
        active=event.getServer(); ticks=settled=0; prepared=checked=false;
        active.overworld().setChunkForced(0,0,true); active.overworld().getChunk(0,0); // Fixture only.
        active.overworld().getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0,active);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { if(active==event.getServer()) active=null; }
    private static ThaumcraftGolem worker(UUID id) { return active.overworld().getEntity(id) instanceof ThaumcraftGolem g?g:null; }
    private static Item seed() { return COCOA?Items.COCOA_BEANS:Items.WHEAT_SEEDS; }
    private static net.minecraft.world.level.block.state.BlockState young() { return COCOA?Blocks.COCOA.defaultBlockState().setValue(CocoaBlock.FACING,Direction.EAST):Blocks.WHEAT.defaultBlockState(); }
    private static ItemStack returned() { var s=new ItemStack(Items.WHEAT_SEEDS,3); s.set(DataComponents.CUSTOM_NAME,Component.literal("Actual cold refund")); return s; }
    private static ListTag pending(GolemSeals data) {
        var rows=data.save(new CompoundTag(),active.overworld().registryAccess()).getList("seals",Tag.TAG_COMPOUND);
        for(int i=0;i<rows.size();i++) if(rows.getCompound(i).getLong("pos")==SEAL.pos().asLong()) return rows.getCompound(i).getList("ReplantTasks",Tag.TAG_COMPOUND);
        throw new AssertionError("Harvest seal missing from disk state");
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if(active!=event.getServer() || checked || ++ticks<60) return;
        var level=active.overworld();
        boolean ready=level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(0,0)) && level.isPositionEntityTicking(CROP);
        if(!ready && ticks<600) return;
        try {
            require(ready,"Fixture chunk really ticking"); var data=GolemSeals.get(level);
            if(PHASE.equals("write") && !prepared) {
                require(worker(WORKER)==null && worker(REFUND)==null && !data.contains(SEAL.pos(),SEAL.face()),"Fresh isolated farm world");
                for(int x=1;x<=14;x++) for(int z=2;z<=14;z++) level.setBlockAndUpdate(new BlockPos(x,119,z),Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(SEAL.pos(),Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE,7));
                if(COCOA) level.setBlockAndUpdate(CROP.east(),Blocks.JUNGLE_LOG.defaultBlockState());
                level.setBlockAndUpdate(CROP,COCOA?young().setValue(CocoaBlock.AGE,2):young().setValue(CropBlock.AGE,7));
                data.place(SEAL.pos(),SEAL.face(),OWNER,GolemSeals.Type.HARVEST);
                data.configureArea(data.get(SEAL),OWNER,new BlockPos(1,1,1)); data.configureHarvest(data.get(SEAL),OWNER,new HarvestSettings(true,true));
                var g=GolemModule.GOLEM.get().create(level); g.setUUID(WORKER); g.moveTo(Vec3.atBottomCenterOf(COCOA?CROP.west():CROP.east()));
                g.initialize(GolemDesign.fromIds(0,1,1,0,0,0),OWNER,CROP,0); g.setNoAi(true); g.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(seed(),2));
                require(level.addFreshEntity(g),"Actual farmer spawned");
                var r=GolemModule.GOLEM.get().create(level); r.setUUID(REFUND); r.moveTo(Vec3.atBottomCenterOf(CROP.east(4)));
                r.initialize(GolemDesign.defaultWood(),OWNER,CROP,0); r.setNoAi(true); r.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.COBBLESTONE,64));
                r.retainTransfer(returned()); r.retainTransfer(new ItemStack(Items.CARROT,4)); require(level.addFreshEntity(r),"Actual refund holder spawned");
                prepared=true; return;
            }
            if(PHASE.equals("write")) {
                if(ticks<70) return;
                var g=worker(WORKER); var harvest=data.claimBlock(g,p->true).orElseThrow();
                require(data.completeBlock(level,g,harvest) && level.isEmptyBlock(CROP) && pending(data).size()==1 && g.getMainHandItem().getCount()==2,"Real harvest precedes save; one unpaid durable intent, two real seeds");
                require(data.save(new CompoundTag(),level.registryAccess()).getAllKeys().equals(java.util.Set.of("seals")),"No task/lease root serialized");
            } else {
                var g=worker(WORKER); var r=worker(REFUND); require(g!=null && r!=null && OWNER.equals(g.ownerId()),"Same two persisted entity UUIDs/owner");
                if(!prepared) {
                    var seal=data.get(SEAL);
                    require(seal!=null && seal.type()==GolemSeals.Type.HARVEST && seal.harvest().replant() && seal.harvest().provision() && seal.area().equals(new BlockPos(1,1,1)),"Harvest controls and area survived disk restart");
                    require(level.isEmptyBlock(CROP) && pending(data).size()==1 && g.getMainHandItem().is(seed()) && g.getMainHandItem().getCount()==2,"Unpaid replant restored without charging seed during load");
                    require(pending(data).getCompound(0).getByte("support")== (COCOA?Direction.EAST:Direction.DOWN).ordinal()
                            && pending(data).getCompound(0).getBoolean("farmland"),"Exact attachment and original farmland snapshot survived disk");
                    require(r.transferRecoveryItems().size()==2 && ItemStack.matches(r.transferRecoveryItems().getFirst(),returned()) && r.transferRecoveryItems().get(1).getCount()==4,"Both actual refund variants/components survived disk");
                    g.setNoAi(false); prepared=true; return;
                }
                if(!level.getBlockState(CROP).equals(young()) || g.getMainHandItem().getCount()!=1) { require(ticks<600,"Autonomous replant completes within600 ticks"); return; }
                if(++settled<40) return;
                require(pending(data).isEmpty(),"Saved intent consumed exactly once after autonomous resume");
                var actor=FakePlayerFactory.get(level,new GameProfile(OWNER,"ColdRefundOwner")); actor.setShiftKeyDown(true); actor.moveTo(r.position());
                require(r.interact(actor,InteractionHand.MAIN_HAND).consumesAction() && !r.interact(actor,InteractionHand.MAIN_HAND).consumesAction(),"Actual reclaim once, stale callback rejected");
                var drops=level.getEntitiesOfClass(ItemEntity.class,new AABB(CROP).inflate(16));
                require(drops.stream().filter(e->ItemStack.isSameItemSameComponents(e.getItem(),returned())).mapToInt(e->e.getItem().getCount()).sum()==3
                        && drops.stream().filter(e->e.getItem().is(Items.CARROT)).mapToInt(e->e.getItem().getCount()).sum()==4,"Exactly3 named seeds and4 carrots returned after restart");
                require(level.getEntitiesOfClass(ThaumcraftGolem.class,new AABB(CROP).inflate(16)).size()==1,"One remaining farmer; no entity replay");
            }
            checked=true; LogUtils.getLogger().info("GOLEM_{}_RESTART_{}_ASSERTIONS_OK",SCENARIO.toUpperCase(java.util.Locale.ROOT),PHASE.toUpperCase(java.util.Locale.ROOT));
        } catch(RuntimeException|AssertionError ex) { checked=true; LogUtils.getLogger().error("GOLEM_"+SCENARIO.toUpperCase(java.util.Locale.ROOT)+"_RESTART_ASSERTIONS_FAILED",ex); }
        if(checked) active.halt(false);
    }
    private static void require(boolean value,String message) { if(!value) throw new AssertionError(message); }
}
