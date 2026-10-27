package dev.ehtid.thaumcraft.golem.test;

import com.mojang.logging.LogUtils;
import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.golem.inventory.GolemContainers;
import dev.ehtid.thaumcraft.golem.seal.*;
import dev.ehtid.thaumcraft.port.core.golem.GolemDesign;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import java.util.*;

/** Opt-in disposable-world crash experiment. WRITE deliberately bypasses every JVM shutdown hook. */
@EventBusSubscriber(modid=ThaumcraftPort.MOD_ID)
public final class GolemCrashProbe {
    private static final String KIND=System.getProperty("thaumcraft.golemCrashCase",""),PHASE=System.getProperty("thaumcraft.golemCrashPhase","");
    private static final boolean ENABLED=Set.of("unsaved","saved","split").contains(KIND)&&Set.of("write","read").contains(PHASE);
    private static final UUID OWNER=UUID.fromString("a42d762d-75f2-49e8-b9cb-690655df61a1"),WORKER=UUID.fromString("61256fc3-deec-4391-a44e-4b4ab4fe8937"),CONTROL=UUID.fromString("41329f0b-4825-4c9d-8804-7f01526bc64f");
    private static final GolemSeals.SealKey SOURCE=new GolemSeals.SealKey(new BlockPos(3,120,8),Direction.UP),TARGET=new GolemSeals.SealKey(new BlockPos(12,120,8),Direction.UP);
    private static final BlockPos HOPPER=new BlockPos(7,120,3);
    private static MinecraftServer active;private static int ticks,stage,finishedAt;private static boolean checked;
    private GolemCrashProbe() {}
    @SubscribeEvent public static void started(ServerStartedEvent e) {
        if(!ENABLED||!e.getServer().isDedicatedServer())return;
        active=e.getServer();ticks=stage=finishedAt=0;checked=false;
        active.overworld().setChunkForced(0,0,true);active.overworld().getChunk(0,0);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e){if(active==e.getServer())active=null;}
    @SubscribeEvent public static void tick(ServerTickEvent.Post e) {
        if(active!=e.getServer()||checked||++ticks<60)return;
        var level=active.overworld();
        try {
            require(ticks<1400,"Bounded crash experiment");
            if(!level.isPositionEntityTicking(SOURCE.pos())||!level.areEntitiesLoaded(ChunkPos.asLong(0,0)))return;
            if(PHASE.equals("write")&&stage==0) {setup(level);stage=1;return;}
            var g=level.getEntity(WORKER) instanceof ThaumcraftGolem worker?worker:null;
            require(g!=null&&OWNER.equals(g.ownerId()),"Original worker UUID/owner is present");
            if(PHASE.equals("read")&&stage==0) {
                int iron=stock(level,TARGET.pos())+held(g)+stock(level,SOURCE.pos());int gold=stock(level,HOPPER)+ground(level,Items.GOLD_INGOT);
                if(KIND.equals("split")) {
                    require(iron==26&&stock(level,TARGET.pos())==13&&held(g)==13&&gold==34&&stock(level,HOPPER)==17&&level.getEntity(CONTROL) instanceof ItemEntity,"Measured partial native save restores both original entity payloads beside already saved destinations: iron="+iron+", gold="+gold);
                    checked=true;LogUtils.getLogger().warn("GOLEM_CRASH_SPLIT_SAVE_HAZARD_OBSERVED golemIron={} vanillaHopperGold={}; NOT_A_RECOVERY_PASS",iron,gold);
                } else {
                    require(iron==13&&gold==17,"Last complete checkpoint conserves both native paths");
                    require(KIND.equals("unsaved")?held(g)==13&&stock(level,TARGET.pos())==0&&stock(level,HOPPER)==0&&level.getEntity(CONTROL) instanceof ItemEntity:held(g)==0&&stock(level,TARGET.pos())==13&&stock(level,HOPPER)==17&&level.getEntity(CONTROL)==null,"Actual restart matches the last durable checkpoint, not an invented newer state");
                    if(KIND.equals("unsaved")) {
                        require(g.isNoAi()&&GolemSeals.stopped(level,GolemSeals.get(level).get(TARGET)),"Paused checkpoint really restored");
                        g.setNoAi(false);level.setBlockAndUpdate(TARGET.pos().east(),Blocks.AIR.defaultBlockState());
                    }
                    stage=1;return;
                }
            } else if(!checked) {
                require(stock(level,SOURCE.pos())+stock(level,TARGET.pos())+held(g)+ground(level,Items.IRON_INGOT)==13,"Each-tick iron conservation during actual AI delivery");
                if(stock(level,TARGET.pos())!=13||held(g)!=0){require(ticks<800,"Actual native Fill finishes");return;}
                if(level.getEntity(CONTROL) instanceof ItemEntity item)require(HopperBlockEntity.addItem((Container)level.getBlockEntity(HOPPER),item)&&item.isRemoved(),"Actual vanilla hopper pickup consumes the original item entity");
                require(stock(level,HOPPER)==17&&ground(level,Items.GOLD_INGOT)==0,"Vanilla control gold17 is conserved");
                require(level.getEntitiesOfClass(ThaumcraftGolem.class,new AABB(SOURCE.pos()).inflate(20)).size()==1,"Exactly one actual worker");
                if(PHASE.equals("write")) {
                    if(KIND.equals("saved"))level.save(null,true,false);
                    if(KIND.equals("split"))level.getChunkSource().save(true); // Actual block-save prefix of ServerLevel.save; entity store deliberately has not run yet.
                    LogUtils.getLogger().warn("GOLEM_CRASH_ARMED case={} targetIron=13 workerIron=0 hopperGold=17; Runtime.halt(86), NO_GRACEFUL_SAVE",KIND);
                    System.out.flush();System.err.flush();Runtime.getRuntime().halt(86);
                } else {
                    if(finishedAt==0)finishedAt=ticks;if(ticks-finishedAt<100)return;
                    checked=true;LogUtils.getLogger().info("GOLEM_CRASH_{}_READ_ASSERTIONS_OK",KIND.toUpperCase(Locale.ROOT));
                }
            }
        }catch(RuntimeException|AssertionError ex){checked=true;LogUtils.getLogger().error("GOLEM_CRASH_ASSERTIONS_FAILED case={} phase={}",KIND,PHASE,ex);}
        if(checked)active.halt(false);
    }
    private static void setup(ServerLevel level) {
        require(level.getEntity(WORKER)==null&&!GolemSeals.get(level).contains(TARGET.pos(),TARGET.face()),"Fresh disposable crash world, prior fixture never overwritten");
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,active);
        for(int x=0;x<15;x++)for(int z=0;z<15;z++){level.setBlockAndUpdate(new BlockPos(x,119,z),Blocks.STONE.defaultBlockState());for(int y=120;y<=126;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());}
        level.setBlockAndUpdate(SOURCE.pos(),Blocks.BARREL.defaultBlockState());level.setBlockAndUpdate(TARGET.pos(),Blocks.BARREL.defaultBlockState());level.setBlockAndUpdate(HOPPER,Blocks.HOPPER.defaultBlockState());level.setBlockAndUpdate(TARGET.pos().east(),Blocks.REDSTONE_BLOCK.defaultBlockState());
        var data=GolemSeals.get(level);require(data.place(TARGET.pos(),TARGET.face(),OWNER,GolemSeals.Type.FILL)&&data.configureControls(data.get(TARGET),OWNER,0,0,true,true),"Actual powered Fill pauses fixture");
        var g=GolemModule.GOLEM.get().create(level);g.setUUID(WORKER);g.moveTo(Vec3.atBottomCenterOf(SOURCE.pos().east()));g.initialize(GolemDesign.fromIds(0,1,0,1,0,0),OWNER,g.blockPosition(),0);g.setNoAi(true);require(level.addFreshEntity(g),"Real worker");
        var source=(IItemHandlerModifiable)GolemContainers.handler(level,SOURCE);source.setStackInSlot(0,cargo(Items.IRON_INGOT,13));
        require(GolemContainers.extractInto(g,source,cargo(Items.IRON_INGOT,13),SealFilter.defaults())==13&&stock(level,SOURCE.pos())==0&&held(g)==13,"Actual finite component-bearing extraction13 into hands");
        var item=new ItemEntity(level,7.5,124,3.5,cargo(Items.GOLD_INGOT,17));item.setUUID(CONTROL);item.setNoGravity(true);item.setDeltaMovement(Vec3.ZERO);item.setNeverPickUp();require(level.addFreshEntity(item),"Real vanilla item control17");
        level.save(null,true,false);
        require(stock(level,TARGET.pos())==0&&stock(level,HOPPER)==0&&held(g)==13&&!item.isRemoved(),"Durable initial checkpoint is before either delivery");
        LogUtils.getLogger().info("GOLEM_CRASH_CHECKPOINT_ASSERTIONS_OK case={} workerIron=13 vanillaEntityGold=17",KIND);
        g.setNoAi(false);level.setBlockAndUpdate(TARGET.pos().east(),Blocks.AIR.defaultBlockState());
    }
    private static ItemStack cargo(Item item,int n){var s=new ItemStack(item,n);s.set(DataComponents.CUSTOM_NAME,Component.literal("M43 crash exact input"));return s;}
    private static int held(ThaumcraftGolem g){return g.getMainHandItem().getCount()+g.getOffhandItem().getCount()+g.transferRecoveryItems().stream().mapToInt(ItemStack::getCount).sum();}
    private static int stock(ServerLevel l,BlockPos p){var c=(Container)l.getBlockEntity(p);int n=0;for(int i=0;i<c.getContainerSize();i++){var s=c.getItem(i);if(!s.isEmpty()){require((s.is(Items.IRON_INGOT)||s.is(Items.GOLD_INGOT))&&s.getHoverName().getString().equals("M43 crash exact input"),"Original item components retained");n+=s.getCount();}}return n;}
    private static int ground(ServerLevel l,Item item){return l.getEntitiesOfClass(ItemEntity.class,new AABB(SOURCE.pos()).inflate(20),e->e.getItem().is(item)).stream().mapToInt(e->e.getItem().getCount()).sum();}
    private static void require(boolean v,String message){if(!v)throw new AssertionError(message);}
}
