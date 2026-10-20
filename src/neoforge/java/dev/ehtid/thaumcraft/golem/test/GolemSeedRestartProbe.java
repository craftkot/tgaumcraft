package dev.ehtid.thaumcraft.golem.test;

import com.mojang.logging.LogUtils;
import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.golem.api.GolemHarvestRegistry;
import dev.ehtid.thaumcraft.golem.seal.*;
import dev.ehtid.thaumcraft.port.core.golem.GolemDesign;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
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
import java.util.*;

/** Persist a real registered-crop replant obligation and exact paid seeds, then resume native placement. */
@EventBusSubscriber(modid=ThaumcraftPort.MOD_ID)
public final class GolemSeedRestartProbe {
    private static final boolean ENABLED="seeded".equals(System.getProperty("thaumcraft.golemRestartScenario",""));
    private static final String PHASE=System.getProperty("thaumcraft.golemRestartPhase","");
    private static final UUID OWNER=UUID.fromString("7eae222e-670d-4c43-952a-43a040b2e895"),WORKER=UUID.fromString("6af44574-5cb2-4f7c-b706-a788bd884394");
    private static final BlockPos CROP=new BlockPos(6,120,6);
    private static final GolemSeals.SealKey KEY=new GolemSeals.SealKey(CROP.below(),Direction.UP);
    private static MinecraftServer active;private static GolemHarvestRegistry.Registration crops;private static GolemHarvestRegistry.SeedRegistration seeds;private static int ticks,stage,settled;private static boolean checked;
    private GolemSeedRestartProbe() {}
    private static ItemStack seed(int n){var s=new ItemStack(Items.SWEET_BERRIES,n);s.set(DataComponents.CUSTOM_NAME,Component.literal("M45 cold paid seed"));return s;}
    @SubscribeEvent public static void started(ServerStartedEvent e){
        if(!ENABLED||!Set.of("write","read").contains(PHASE))return;
        active=e.getServer();ticks=stage=settled=0;checked=false;
        crops=GolemHarvestRegistry.registerStandard(Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(SweetBerryBushBlock.AGE,3));seeds=GolemHarvestRegistry.registerSeed(Blocks.SWEET_BERRY_BUSH,seed(1));
        active.overworld().setChunkForced(0,0,true);active.overworld().getChunk(0,0);active.overworld().getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0,active);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e){if(active==e.getServer()){active=null;crops.close();seeds.close();}}
    @SubscribeEvent public static void tick(ServerTickEvent.Post e){
        if(active!=e.getServer()||checked||++ticks<60)return;var level=active.overworld();
        try{
            require(ticks<1000,"Bounded registered seed restart");if(!level.isPositionEntityTicking(CROP)||!level.areEntitiesLoaded(ChunkPos.asLong(0,0)))return;
            var data=GolemSeals.get(level);
            if(PHASE.equals("write")&&stage==0){
                require(level.getEntity(WORKER)==null&&!data.contains(KEY.pos(),KEY.face()),"Fresh fixture");
                for(int x=0;x<15;x++)for(int z=0;z<15;z++){level.setBlockAndUpdate(new BlockPos(x,119,z),Blocks.STONE.defaultBlockState());for(int y=120;y<=124;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());}
                level.setBlockAndUpdate(KEY.pos(),Blocks.DIRT.defaultBlockState());level.setBlockAndUpdate(CROP,Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(SweetBerryBushBlock.AGE,3));
                require(data.place(KEY.pos(),KEY.face(),OWNER,GolemSeals.Type.HARVEST),"Actual Harvest seal");data.configureArea(data.get(KEY),OWNER,new BlockPos(1,1,1));data.configureHarvest(data.get(KEY),OWNER,new HarvestSettings(true,false));
                var g=GolemModule.GOLEM.get().create(level);g.setUUID(WORKER);g.moveTo(Vec3.atBottomCenterOf(CROP.east()));g.initialize(GolemDesign.fromIds(0,1,1,0,0,0),OWNER,CROP,0);g.setNoAi(true);g.setItemSlot(EquipmentSlot.MAINHAND,seed(2));require(level.addFreshEntity(g),"Actual farmer with two named seeds");stage=1;return;
            }
            var g=level.getEntity(WORKER) instanceof ThaumcraftGolem worker?worker:null;require(g!=null&&OWNER.equals(g.ownerId()),"Same original worker UUID/owner");
            if(PHASE.equals("write")){
                if(ticks<85)return;var task=data.claimBlock(g,p->true).orElseThrow();require(data.completeBlock(level,g,task)&&level.isEmptyBlock(CROP)&&ItemStack.matches(g.getMainHandItem(),seed(2))&&g.rankXp()==1,"Actual break with both paid seeds retained");
                var intent=pending(level,data);require(intent.size()==1&&ItemStack.matches(ItemStack.parseOptional(level.registryAccess(),intent.getCompound(0).getCompound("seed")),seed(1)),"Actual exact seed obligation recorded in SavedData");
                int n=ground(level,false);require(n>0&&ground(level,true)==0,"Real fruit dropped; no synthetic named seed");g.getPersistentData().putInt("M45ActualDropped",n);checked=true;LogUtils.getLogger().info("GOLEM_SEED_RESTART_WRITE_ASSERTIONS_OK ordinaryFruit={}",n);
            }else{
                int n=g.getPersistentData().getInt("M45ActualDropped");require(n>0&&ground(level,false)==n&&ground(level,true)==0,"Actual crop drops survived independently, no named seed copy");
                if(stage==0){require(g.isNoAi()&&g.rankXp()==1&&ItemStack.matches(g.getMainHandItem(),seed(2))&&level.isEmptyBlock(CROP)&&pending(level,data).size()==1,"Native exact seed2/XP1/intent/crop state restored from disk");g.setNoAi(false);stage=1;return;}
                boolean planted=level.getBlockState(CROP).is(Blocks.SWEET_BERRY_BUSH);require(g.getMainHandItem().getCount()+g.transferRecoveryItems().stream().mapToInt(ItemStack::getCount).sum()+(planted?1:0)==2,"Each-tick seed conservation, one paid native plant");
                if(!planted||!pending(level,data).isEmpty())return;require(ItemStack.matches(g.getMainHandItem(),seed(1))&&level.getBlockState(CROP).getValue(SweetBerryBushBlock.AGE)==0,"Native BlockItem consumes exactly one matching seed");if(++settled<100)return;
                require(level.getEntitiesOfClass(ThaumcraftGolem.class,new AABB(CROP).inflate(16)).size()==1,"One restored worker, no replay for100ticks");checked=true;LogUtils.getLogger().info("GOLEM_SEED_RESTART_READ_ASSERTIONS_OK ordinaryFruit={}",n);
            }
        }catch(RuntimeException|AssertionError ex){checked=true;LogUtils.getLogger().error("GOLEM_SEED_RESTART_ASSERTIONS_FAILED",ex);}
        if(checked)active.halt(false);
    }
    private static ListTag pending(ServerLevel l,GolemSeals d){for(var tag:d.save(new CompoundTag(),l.registryAccess()).getList("seals",Tag.TAG_COMPOUND)){var row=(CompoundTag)tag;if(row.getLong("pos")==KEY.pos().asLong())return row.getList("ReplantTasks",Tag.TAG_COMPOUND);}throw new AssertionError("Original seal absent");}
    private static int ground(ServerLevel l,boolean named){return l.getEntitiesOfClass(ItemEntity.class,new AABB(CROP).inflate(16),i->i.getItem().is(Items.SWEET_BERRIES)&&i.getItem().has(DataComponents.CUSTOM_NAME)==named).stream().mapToInt(i->i.getItem().getCount()).sum();}
    private static void require(boolean v,String m){if(!v)throw new AssertionError(m);}
}
