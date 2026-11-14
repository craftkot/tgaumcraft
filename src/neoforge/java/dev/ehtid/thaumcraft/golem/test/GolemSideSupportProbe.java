package dev.ehtid.thaumcraft.golem.test;

import com.mojang.logging.LogUtils;
import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.golem.api.GolemHarvestRegistry;
import dev.ehtid.thaumcraft.golem.seal.GolemSeals;
import dev.ehtid.thaumcraft.golem.seal.HarvestSettings;
import dev.ehtid.thaumcraft.port.core.golem.GolemDesign;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.*;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.UUID;

/** Real ticket gradient, paused native replant, real chunk/entity unload and automatic paid resume. */
@EventBusSubscriber(modid=ThaumcraftPort.MOD_ID)
public final class GolemSideSupportProbe {
    private static final boolean ENABLED=Boolean.getBoolean("thaumcraft.golemSideSupportProbe");
    private static final ChunkPos CENTER=new ChunkPos(960,960),SIDE=new ChunkPos(961,960);
    private static final BlockPos CROP=new BlockPos(CENTER.getMinBlockX()+15,120,CENTER.getMinBlockZ()+6),SUPPORT=CROP.east();
    private static final GolemSeals.SealKey KEY=new GolemSeals.SealKey(CROP.below(),Direction.UP);
    private static final UUID OWNER=UUID.fromString("b9e32535-9f36-4712-ab42-2e5a1d3a5c56"),WORKER=UUID.fromString("1891c405-fef9-4b71-986f-7edbc6f0ed56");
    private static final GolemDesign DESIGN=GolemDesign.fromIds(0,1,1,0,0,0);
    private static MinecraftServer active;
    private static ThaumcraftGolem original;
    private static GolemHarvestRegistry.Registration crops;
    private static GolemHarvestRegistry.SeedRegistration seeds;
    private static int ticks,phase,phaseTick,blocked,settled;
    private static boolean done,sourceUnloaded,sideUnloaded,trackingEnded,loadedFromDisk;
    private GolemSideSupportProbe() {}
    private static Block crop(){return BuiltInRegistries.BLOCK.get(GolemPlantAdapterItems.id("directional"));}
    private static ItemStack seed(int count){
        var item=BuiltInRegistries.ITEM.get(GolemPlantAdapterItems.id("directional"));
        require(item!=Items.AIR&&!(item instanceof BlockItem),"Actual opt-in non-BlockItem directional seed required");
        var seed=new ItemStack(item,count);seed.set(DataComponents.CUSTOM_NAME,Component.literal("M56 paid side seed"));return seed;
    }
    @SubscribeEvent public static void started(ServerStartedEvent event){
        if(!ENABLED||!event.getServer().isDedicatedServer())return;
        active=event.getServer();ticks=phase=phaseTick=blocked=settled=0;
        done=sourceUnloaded=sideUnloaded=trackingEnded=loadedFromDisk=false;original=null;
        require(crop() instanceof GolemPlantAdapterItems.DirectionalPlant,"Actual gated directional crop required");
        crops=GolemHarvestRegistry.registerStandard(crop().defaultBlockState().setValue(GolemPlantAdapterItems.DirectionalPlant.AGE,1).setValue(HorizontalDirectionalBlock.FACING,Direction.EAST));
        seeds=GolemHarvestRegistry.registerDirectionalSeed(crop(),seed(1),crop(),HorizontalDirectionalBlock.FACING);
        var level=active.overworld();require(!forced(level,CENTER)&&!forced(level,SIDE),"Fresh isolated fixture tickets; never overwrite an existing world");
        level.setChunkForced(CENTER.x,CENTER.z,true);level.getChunk(CENTER.x,CENTER.z);
        level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0,active);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,active);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){
        if(active!=event.getServer())return;
        if(crops!=null)crops.close();if(seeds!=null)seeds.close();active=null;original=null;
    }
    @SubscribeEvent public static void unloaded(ChunkEvent.Unload event){
        if(active==null||event.getLevel()!=active.overworld())return;
        if(event.getChunk().getPos().equals(CENTER))sourceUnloaded=true;
        if(event.getChunk().getPos().equals(SIDE))sideUnloaded=true;
    }
    @SubscribeEvent public static void left(EntityLeaveLevelEvent event){if(active!=null&&event.getEntity()==original)trackingEnded=true;}
    @SubscribeEvent public static void joined(EntityJoinLevelEvent event){
        if(active!=null&&phase>=3&&event.getEntity() instanceof ThaumcraftGolem&&WORKER.equals(event.getEntity().getUUID())&&event.loadedFromDisk())loadedFromDisk=true;
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        if(active!=event.getServer()||done)return;
        ticks++;var level=active.overworld();
        try{
            require(ticks<2400,"Bounded native side-support lifecycle; phase="+phase);
            var data=GolemSeals.get(level);
            if(phase==0){
                if(!GolemSeals.active(level,CROP)||level.getChunkSource().getChunkNow(SIDE.x,SIDE.z)==null)return;
                inactiveSupport(level);
                require(level.getEntity(WORKER)==null&&!data.contains(KEY.pos(),KEY.face()),"Fresh isolated worker and seal");
                for(int x=12;x<=15;x++)for(int z=3;z<=9;z++)level.setBlockAndUpdate(new BlockPos(CENTER.getMinBlockX()+x,119,CENTER.getMinBlockZ()+z),Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(SUPPORT,Blocks.JUNGLE_LOG.defaultBlockState());
                level.setBlockAndUpdate(CROP,crop().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING,Direction.EAST).setValue(GolemPlantAdapterItems.DirectionalPlant.AGE,1));
                require(data.place(KEY.pos(),KEY.face(),OWNER,GolemSeals.Type.HARVEST),"Actual owner Harvest seal");
                data.configureControls(data.get(KEY),OWNER,0,0,true,false);data.configureArea(data.get(KEY),OWNER,new BlockPos(1,1,1));data.configureHarvest(data.get(KEY),OWNER,new HarvestSettings(true,false));
                original=GolemModule.GOLEM.get().create(level);require(original!=null,"Registered worker");original.setUUID(WORKER);original.initialize(DESIGN,OWNER,CROP,0);original.moveTo(Vec3.atBottomCenterOf(CROP.west()));original.setNoAi(true);original.setItemSlot(EquipmentSlot.MAINHAND,seed(2));
                require(level.addFreshEntity(original),"One actual funded worker");phase=1;phaseTick=ticks;
                LogUtils.getLogger().info("GOLEM_SIDE_INITIAL_READY sourceTicket=31 sideTicket=32 sideLoaded=true sideEntityTicking=false");return;
            }
            if(phase==1){
                inactiveSupport(level);if(ticks-phaseTick<10)return;
                var work=data.claimBlock(original,p->true);if(work.isEmpty())return;
                require(data.completeBlock(level,original,work.get())&&level.isEmptyBlock(CROP)&&original.rankXp()==1&&ItemStack.matches(original.getMainHandItem(),seed(2)),"Actual mature harvest retains both paid seeds");
                require(pending(level,data).size()==1&&pending(level,data).getCompound(0).getByte("support")==Direction.EAST.ordinal(),"Real EAST replant intent recorded");phase=2;phaseTick=ticks;return;
            }
            if(phase==2){
                inactiveSupport(level);
                require(ItemStack.matches(original.getMainHandItem(),seed(2))&&original.rankXp()==1&&!original.hasTransferRecovery()&&original.getOffhandItem().isEmpty()&&level.isEmptyBlock(CROP)&&pending(level,data).size()==1,"Inactive side preserves seed2/XP1/AIR/intent1");
                require(data.claimBlock(original,p->true).isEmpty(),"Inactive side support must refuse the real candidate lease");
                if(ticks-phaseTick<40)return;require(data.pendingTasks()>0,"An actual queued replant exists; refusal is not absence of work");
                if(++blocked<100)return;
                LogUtils.getLogger().info("GOLEM_SIDE_PAUSE_ASSERTIONS_OK rejectedClaims={} seed=2 xp=1 pending=1 sourceTicket=31 sideTicket=32 forcedSide=false",blocked);
                require(level.setChunkForced(CENTER.x,CENTER.z,false),"Fixture releases its only forced ticket");phase=3;phaseTick=ticks;return;
            }
            if(phase==3){
                if(!sourceUnloaded||!sideUnloaded||!trackingEnded||original.getRemovalReason()!=Entity.RemovalReason.UNLOADED_TO_CHUNK
                    ||level.getChunkSource().getChunkNow(CENTER.x,CENTER.z)!=null||level.getChunkSource().getChunkNow(SIDE.x,SIDE.z)!=null||level.areEntitiesLoaded(CENTER.toLong()))return;
                require(level.getEntity(WORKER)==null,"Original UUID absent after actual native entity unload");phase=4;phaseTick=ticks;
                LogUtils.getLogger().info("GOLEM_SIDE_NATIVE_UNLOAD_OBSERVED sourceChunk=false sideChunk=false worker=false removal=UNLOADED_TO_CHUNK");return;
            }
            if(phase==4){
                require(!forced(level,CENTER)&&!forced(level,SIDE)&&level.getChunkSource().getChunkNow(CENTER.x,CENTER.z)==null&&level.getChunkSource().getChunkNow(SIDE.x,SIDE.z)==null&&level.getEntity(WORKER)==null,"Pending directional work never force-loads either absent chunk");
                require(pending(level,data).size()==1,"Unloaded intent retained without touching blocks");if(ticks-phaseTick<40)return;
                level.setChunkForced(CENTER.x,CENTER.z,true);level.setChunkForced(SIDE.x,SIDE.z,true);level.getChunk(CENTER.x,CENTER.z);level.getChunk(SIDE.x,SIDE.z);
                phase=5;phaseTick=ticks;LogUtils.getLogger().info("GOLEM_SIDE_FIXTURE_RELOAD_REQUESTED absentTicks=40");return;
            }
            if(!GolemSeals.active(level,CROP)||!GolemSeals.active(level,SUPPORT)||!(level.getEntity(WORKER) instanceof ThaumcraftGolem))return;
            var worker=(ThaumcraftGolem)level.getEntity(WORKER);
            if(phase==5){
                require(loadedFromDisk&&worker!=original&&OWNER.equals(worker.ownerId())&&worker.design().equals(DESIGN)&&worker.isNoAi()&&worker.rankXp()==1&&ItemStack.matches(worker.getMainHandItem(),seed(2))&&pending(level,data).size()==1&&level.isEmptyBlock(CROP),"Different real entity loaded from disk with same UUID/owner/design/seed2/XP1/EAST intent");
                worker.setNoAi(false);phase=6;phaseTick=ticks;LogUtils.getLogger().info("GOLEM_SIDE_RELOADED_ASSERTIONS_OK sameUuid=true differentObject=true seed=2 xp=1 nativeAiEnabled=true");return;
            }
            boolean planted=level.getBlockState(CROP).is(crop());
            int actual=actualSeeds(worker);
            require(actual+(planted?1:0)==2&&ground(level)==0,"Each-tick actual seed plus plant conservation without ground copies");
            if(!planted||!pending(level,data).isEmpty())return;
            var state=level.getBlockState(CROP);
            require(state.getValue(HorizontalDirectionalBlock.FACING)==Direction.EAST&&state.getValue(GolemPlantAdapterItems.DirectionalPlant.AGE)==0&&ItemStack.matches(worker.getMainHandItem(),seed(1))&&worker.rankXp()==2&&!worker.hasTransferRecovery()&&worker.getOffhandItem().isEmpty(),"Resumed real native callback pays one seed and retains EAST/young state/XP2");
            require(level.getEntitiesOfClass(ThaumcraftGolem.class,new AABB(CROP).inflate(16)).size()==1,"One original worker after native unload/reload");
            if(++settled<100)return;done=true;
            LogUtils.getLogger().info("GOLEM_SIDE_SUPPORT_ASSERTIONS_OK blockedClaims={} absentTicks=40 nativeReload=true seed=1 plant=1 xp=2 pending=0 noReplayTicks={}",blocked,settled);
        }catch(RuntimeException|AssertionError ex){done=true;LogUtils.getLogger().error("GOLEM_SIDE_SUPPORT_ASSERTIONS_FAILED phase="+phase,ex);}
        if(done){level.setChunkForced(CENTER.x,CENTER.z,false);level.setChunkForced(SIDE.x,SIDE.z,false);active.halt(false);}
    }
    private static boolean forced(ServerLevel level,ChunkPos pos){return level.getForcedChunks().contains(pos.toLong());}
    private static void inactiveSupport(ServerLevel level){
        var source=level.getChunkSource();var chunks=source.chunkMap;
        var center=chunks.getVisibleChunkIfPresent(CENTER.toLong());var side=chunks.getVisibleChunkIfPresent(SIDE.toLong());
        require(center!=null&&center.getTicketLevel()==31&&GolemSeals.active(level,CROP),"Actual source ticket31/entity ticking");
        require(side!=null&&side.getTicketLevel()==32&&source.getChunkNow(SIDE.x,SIDE.z)!=null&&!GolemSeals.active(level,SUPPORT)&&!forced(level,SIDE),"Actual side ticket32/FULL/inactive with no forced ticket");
    }
    private static int ground(ServerLevel level){return level.getEntitiesOfClass(ItemEntity.class,new AABB(CROP).inflate(16),e->e.getItem().is(seed(1).getItem())).stream().mapToInt(e->e.getItem().getCount()).sum();}
    private static int actualSeeds(ThaumcraftGolem worker){
        var item=seed(1).getItem();int count=0;
        for(var stack:new ItemStack[]{worker.getMainHandItem(),worker.getOffhandItem()}){
            require(stack.isEmpty()||stack.is(item),"No substituted hand cargo in this paid seed fixture");count+=stack.getCount();
        }
        for(var stack:worker.transferRecoveryItems()){
            require(stack.is(item),"No substituted recovery cargo in this paid seed fixture");count+=stack.getCount();
        }
        return count;
    }
    private static ListTag pending(ServerLevel level,GolemSeals data){
        for(var t:data.save(new CompoundTag(),level.registryAccess()).getList("seals",Tag.TAG_COMPOUND)){var row=(CompoundTag)t;if(row.getLong("pos")==KEY.pos().asLong())return row.getList("ReplantTasks",Tag.TAG_COMPOUND);}
        throw new AssertionError("Original side-support seal absent");
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
