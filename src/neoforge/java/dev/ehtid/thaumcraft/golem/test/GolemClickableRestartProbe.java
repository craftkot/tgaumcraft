package dev.ehtid.thaumcraft.golem.test;

import com.mojang.logging.LogUtils;
import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.golem.api.GolemHarvestRegistry;
import dev.ehtid.thaumcraft.golem.seal.GolemSeals;
import dev.ehtid.thaumcraft.port.core.golem.GolemDesign;
import net.minecraft.core.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.*;

/** Fresh startup adapter enrollment -> actual AI harvest/pickup -> disk -> same worker resumes Fill. */
@EventBusSubscriber(modid=ThaumcraftPort.MOD_ID)
public final class GolemClickableRestartProbe {
    private static final boolean ENABLED="clickable".equals(System.getProperty("thaumcraft.golemRestartScenario",""));
    private static final String PHASE=System.getProperty("thaumcraft.golemRestartPhase","");
    private static final UUID OWNER=UUID.fromString("2f95109e-559e-4b76-87da-279207b420b9"),WORKER=UUID.fromString("184aa4df-081c-4452-bf29-29659473bfaf");
    private static final BlockPos CROP=new BlockPos(6,120,6);
    private static final GolemSeals.SealKey HARVEST=new GolemSeals.SealKey(CROP.below(),Direction.UP),PICKUP=new GolemSeals.SealKey(CROP.below().west(2),Direction.UP),FILL=new GolemSeals.SealKey(new BlockPos(12,120,6),Direction.UP);
    private static MinecraftServer active;private static GolemHarvestRegistry.Registration registration;private static int ticks,phase,settled;private static boolean checked;
    private GolemClickableRestartProbe() {}
    @SubscribeEvent public static void started(ServerStartedEvent e){
        if(!ENABLED||!Set.of("write","read").contains(PHASE))return;
        active=e.getServer();ticks=phase=settled=0;checked=false;
        registration=GolemHarvestRegistry.registerClickable(Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(SweetBerryBushBlock.AGE,3));
        active.overworld().setChunkForced(0,0,true);active.overworld().getChunk(0,0);active.overworld().getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0,active);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e){if(active==e.getServer()){active=null;registration.close();registration=null;}}
    @SubscribeEvent public static void tick(ServerTickEvent.Post e){
        if(active!=e.getServer()||checked||++ticks<60)return;var level=active.overworld();
        try{
            require(ticks<1200,"Bounded clickable cold scenario");if(!level.isPositionEntityTicking(CROP)||!level.areEntitiesLoaded(ChunkPos.asLong(0,0)))return;
            var data=GolemSeals.get(level);
            if(PHASE.equals("write")&&phase==0){
                require(level.getEntity(WORKER)==null&&!data.contains(HARVEST.pos(),HARVEST.face()),"Fresh isolated fixture");
                for(int x=0;x<15;x++)for(int z=0;z<15;z++){level.setBlockAndUpdate(new BlockPos(x,119,z),Blocks.STONE.defaultBlockState());for(int y=120;y<=124;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());}
                level.setBlockAndUpdate(HARVEST.pos(),Blocks.DIRT.defaultBlockState());level.setBlockAndUpdate(CROP,Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(SweetBerryBushBlock.AGE,3));level.setBlockAndUpdate(FILL.pos(),Blocks.BARREL.defaultBlockState());level.setBlockAndUpdate(FILL.pos().east(),Blocks.REDSTONE_BLOCK.defaultBlockState());
                require(data.place(HARVEST.pos(),HARVEST.face(),OWNER,GolemSeals.Type.HARVEST)&&data.place(PICKUP.pos(),PICKUP.face(),OWNER,GolemSeals.Type.PICKUP)&&data.place(FILL.pos(),FILL.face(),OWNER,GolemSeals.Type.FILL),"Actual three source seals");
                data.configureArea(data.get(HARVEST),OWNER,new BlockPos(1,1,1));data.configureArea(data.get(PICKUP),OWNER,new BlockPos(4,1,2));data.configureControls(data.get(FILL),OWNER,0,0,true,true);
                var g=GolemModule.GOLEM.get().create(level);g.setUUID(WORKER);g.moveTo(Vec3.atBottomCenterOf(CROP.east()));g.initialize(GolemDesign.fromIds(0,1,1,0,0,0),OWNER,CROP,0);require(level.addFreshEntity(g),"Actual SMART/DEFT farmer AI enabled");phase=1;return;
            }
            var g=level.getEntity(WORKER) instanceof ThaumcraftGolem worker?worker:null;require(g!=null&&OWNER.equals(g.ownerId()),"Original UUID/owner restored");
            if(PHASE.equals("write")){
                if(level.getBlockState(CROP).getValue(SweetBerryBushBlock.AGE)!=1||!g.getMainHandItem().is(Items.SWEET_BERRIES)||ground(level)!=0)return;
                int n=g.getMainHandItem().getCount();require(n>=2&&n<=4&&g.rankXp()>=1&&stored(level)==0&&!g.isNoAi(),"Actual click/pickup resulted in finite cargo before save");
                g.getPersistentData().putInt("M44ExpectedFruit",n);g.getPersistentData().putInt("M44SavedXp",g.rankXp());g.setNoAi(true);checked=true;
                LogUtils.getLogger().info("GOLEM_CLICKABLE_RESTART_WRITE_ASSERTIONS_OK actualFruit={}",n);
            }else{
                int n=g.getPersistentData().getInt("M44ExpectedFruit");require(n>=2&&n<=4,"Persisted fixture observation records actual original fruit quantity");
                if(phase==0){
                    require(g.isNoAi()&&g.getMainHandItem().is(Items.SWEET_BERRIES)&&g.getMainHandItem().getCount()==n&&g.rankXp()==g.getPersistentData().getInt("M44SavedXp")&&stored(level)==0&&ground(level)==0&&level.getBlockState(CROP).getValue(SweetBerryBushBlock.AGE)==1,"Same paid cargo/rank/native crop and no extra fruit after disk");
                    require(data.get(HARVEST).type()==GolemSeals.Type.HARVEST&&data.get(PICKUP).type()==GolemSeals.Type.PICKUP&&data.get(FILL).redstone()&&GolemSeals.stopped(level,data.get(FILL)),"Original seal metadata and physical redstone guard persisted");
                    g.setNoAi(false);level.setBlockAndUpdate(FILL.pos().east(),Blocks.AIR.defaultBlockState());phase=1;return;
                }
                require(g.getMainHandItem().getCount()+g.transferRecoveryItems().stream().mapToInt(s->s.getCount()).sum()+ground(level)+stored(level)==n,"Each-tick fruit conservation after restart");
                if(stored(level)!=n||!g.getMainHandItem().isEmpty())return;if(++settled<100)return;
                require(level.getEntitiesOfClass(ThaumcraftGolem.class,new AABB(CROP).inflate(16)).size()==1&&level.getBlockState(CROP).getValue(SweetBerryBushBlock.AGE)==1,"Single restored worker and no replayed harvest");checked=true;LogUtils.getLogger().info("GOLEM_CLICKABLE_RESTART_READ_ASSERTIONS_OK actualFruit={}",n);
            }
        }catch(RuntimeException|AssertionError ex){checked=true;LogUtils.getLogger().error("GOLEM_CLICKABLE_RESTART_ASSERTIONS_FAILED",ex);}
        if(checked)active.halt(false);
    }
    private static int ground(ServerLevel l){return l.getEntitiesOfClass(ItemEntity.class,new AABB(CROP).inflate(16),i->i.getItem().is(Items.SWEET_BERRIES)).stream().mapToInt(i->i.getItem().getCount()).sum();}
    private static int stored(ServerLevel l){var c=(Container)l.getBlockEntity(FILL.pos());int n=0;for(int i=0;i<c.getContainerSize();i++){var s=c.getItem(i);if(!s.isEmpty()){require(s.is(Items.SWEET_BERRIES),"Only actual fruit delivered");n+=s.getCount();}}return n;}
    private static void require(boolean v,String m){if(!v)throw new AssertionError(m);}
}
