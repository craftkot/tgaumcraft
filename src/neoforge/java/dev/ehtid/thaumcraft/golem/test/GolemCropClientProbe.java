package dev.ehtid.thaumcraft.golem.test;

import com.mojang.logging.LogUtils;
import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.golem.api.GolemHarvestRegistry;
import dev.ehtid.thaumcraft.golem.seal.*;
import dev.ehtid.thaumcraft.port.core.golem.GolemDesign;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Dedicated graphical fixture. Every completion is actual autonomous Harvest and real paid native useOn. */
@EventBusSubscriber(modid=ThaumcraftPort.MOD_ID)
public final class GolemCropClientProbe {
    private static final boolean ENABLED=Boolean.getBoolean("thaumcraft.golemCropClientProbe");
    private static final int START_TICKS=Integer.getInteger("thaumcraft.golemCropStartTicks",600);
    private static final int HOLD_TICKS=Integer.getInteger("thaumcraft.golemCropHoldTicks",600);
    private static final BlockPos CROP=GolemCropClientFixture.CROP;
    private static final GolemSeals.SealKey KEY=new GolemSeals.SealKey(CROP.below(),Direction.UP);
    private static MinecraftServer active;
    private static ThaumcraftGolem worker;
    private static GolemHarvestRegistry.SeedRegistration seeds;
    private static int ticks,phase,phaseTick,settled,finished;
    private static boolean failed;
    private GolemCropClientProbe() {}
    @SubscribeEvent public static void started(ServerStartedEvent e){
        if(!ENABLED||!e.getServer().isDedicatedServer())return;
        active=e.getServer();worker=null;ticks=phase=phaseTick=settled=finished=0;failed=false;
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e){
        if(active!=e.getServer())return;
        if(seeds!=null){seeds.close();seeds=null;}active=null;worker=null;
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post e){
        if(active!=e.getServer())return;
        ticks++;
        if(failed){if(ticks-finished>=100)active.halt(false);return;}
        try{
            require(phase!=0||ticks<6000,"Bounded wait for actual TCP client login");
            var level=active.overworld();var data=GolemSeals.get(level);
            if(phase==0){
                if(active.getPlayerList().getPlayers().isEmpty())return;
                var player=active.getPlayerList().getPlayers().getFirst();
                require(level.getEntity(GolemCropClientFixture.WORKER)==null&&!data.contains(KEY.pos(),KEY.face()),"Fresh isolated crop fixture; existing worlds must not be overwritten");
                level.setChunkForced(0,0,true);level.getChunk(0,0);
                level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,active);
                level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,active);
                level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false,active);
                level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0,active);
                level.setDayTime(6000);level.setWeatherParameters(6000,0,false,false);
                for(int x=0;x<15;x++)for(int z=0;z<15;z++){
                    level.setBlockAndUpdate(new BlockPos(x,119,z),Blocks.STONE.defaultBlockState());
                    for(int y=120;y<=126;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());
                }
                level.setBlockAndUpdate(KEY.pos(),Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE,7));
                level.setBlockAndUpdate(CROP,Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE,7));
                seeds=GolemHarvestRegistry.registerSeed(Blocks.WHEAT,GolemCropClientFixture.seed(1),Blocks.WHEAT);
                require(data.place(KEY.pos(),KEY.face(),player.getUUID(),GolemSeals.Type.HARVEST),"One actual owner Harvest seal");
                data.configureControls(data.get(KEY),player.getUUID(),0,0,true,false);
                data.configureArea(data.get(KEY),player.getUUID(),new BlockPos(1,1,1));
                data.configureHarvest(data.get(KEY),player.getUUID(),new HarvestSettings(true,false));
                worker=GolemModule.GOLEM.get().create(level);worker.setUUID(GolemCropClientFixture.WORKER);
                worker.moveTo(Vec3.atBottomCenterOf(CROP.east()));worker.initialize(GolemDesign.fromIds(0,1,1,0,0,0),player.getUUID(),CROP,0);
                worker.setNoAi(true);worker.setItemSlot(EquipmentSlot.MAINHAND,GolemCropClientFixture.seed(2));
                require(level.addFreshEntity(worker),"One actual worker with two paid callback seeds");
                player.setGameMode(GameType.CREATIVE);player.teleportTo(level,6.5,120,11.5,180,13);
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(GolemModule.GOLEM_BELL.get()));
                phase=1;phaseTick=ticks;
                LogUtils.getLogger().info("GOLEM_CROP_SERVER_INITIAL_READY uuid={} seed=2 age=7 startTicks={}",worker.getUUID(),START_TICKS);
                return;
            }
            require(worker!=null&&worker.isAlive()&&level.getEntity(GolemCropClientFixture.WORKER)==worker,"Same original live worker");
            boolean planted=worker.rankXp()==2&&level.getBlockState(CROP).is(Blocks.WHEAT);
            int actualSeeds=worker.getMainHandItem().getCount()+worker.transferRecoveryItems().stream().mapToInt(ItemStack::getCount).sum()+ground(level,GolemCropClientFixture.seed(1).getItem());
            require(actualSeeds+(planted?1:0)==2,"Each-tick actual callback seed plus paid plant conservation");
            require(ground(level,GolemCropClientFixture.seed(1).getItem())==0,"No duplicate callback seed on ground");
            require(level.getEntitiesOfClass(ThaumcraftGolem.class,new AABB(CROP).inflate(16)).size()==1,"Exactly one actual worker");
            if(phase==1){
                require(worker.isNoAi()&&worker.rankXp()==0&&ItemStack.matches(worker.getMainHandItem(),GolemCropClientFixture.seed(2))
                    &&level.getBlockState(CROP).is(Blocks.WHEAT)&&level.getBlockState(CROP).getValue(CropBlock.AGE)==7,"Stable genuine initial screenshot state");
                if(ticks-phaseTick<START_TICKS)return;
                worker.setNoAi(false);phase=2;phaseTick=ticks;
                LogUtils.getLogger().info("GOLEM_CROP_SERVER_AI_ENABLED uuid={}",worker.getUUID());return;
            }
            if(worker.rankXp()>=1)require(ground(level,Items.WHEAT)==1,"Original actual harvest wheat remains exactly one");
            if(phase==2){
                require(ticks-phaseTick<1200,"Bounded actual AI harvest and replant");
                if(!planted||!pending(level,data).isEmpty())return;
                require(worker.rankXp()==2&&level.getBlockState(CROP).getValue(CropBlock.AGE)==0&&!worker.isNoAi()
                    &&ItemStack.matches(worker.getMainHandItem(),GolemCropClientFixture.seed(1))&&!worker.hasTransferRecovery(),"Actual no-debit callback was paid once and leaves one original named seed");
                if(++settled<100)return;
                phase=3;finished=ticks;
                LogUtils.getLogger().info("GOLEM_CROP_SERVER_ASSERTIONS_OK uuid={} seed=1 plant=1 wheat=1 xp=2 pending=0 noReplayTicks={}",worker.getUUID(),settled);
                return;
            }
            require(planted&&worker.rankXp()==2&&level.getBlockState(CROP).getValue(CropBlock.AGE)==0
                &&ItemStack.matches(worker.getMainHandItem(),GolemCropClientFixture.seed(1))&&pending(level,data).isEmpty(),"Actual completed state remains stable for final screenshots");
            if(ticks-finished>=HOLD_TICKS)active.halt(false);
        }catch(RuntimeException|AssertionError ex){
            failed=true;finished=ticks;LogUtils.getLogger().error("GOLEM_CROP_SERVER_ASSERTIONS_FAILED",ex);
        }
    }
    private static int ground(ServerLevel level,Item item){
        return level.getEntitiesOfClass(ItemEntity.class,new AABB(CROP).inflate(16),e->e.getItem().is(item)).stream().mapToInt(e->e.getItem().getCount()).sum();
    }
    private static ListTag pending(ServerLevel level,GolemSeals data){
        for(var tag:data.save(new CompoundTag(),level.registryAccess()).getList("seals",Tag.TAG_COMPOUND)){
            var row=(CompoundTag)tag;if(row.getLong("pos")==KEY.pos().asLong())return row.getList("ReplantTasks",Tag.TAG_COMPOUND);
        }throw new AssertionError("Original Harvest seal absent");
    }
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
