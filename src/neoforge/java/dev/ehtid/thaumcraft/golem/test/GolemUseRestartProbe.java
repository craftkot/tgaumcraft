package dev.ehtid.thaumcraft.golem.test;

import com.mojang.logging.LogUtils;
import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.golem.inventory.GolemContainers;
import dev.ehtid.thaumcraft.golem.seal.*;
import dev.ehtid.thaumcraft.port.core.golem.*;
import net.minecraft.core.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import java.util.UUID;

/** Actual provider payment before disk boundary, actual Use click after a separate server restart. */
@EventBusSubscriber(modid=ThaumcraftPort.MOD_ID)
public final class GolemUseRestartProbe {
    private static final boolean ENABLED="use".equals(System.getProperty("thaumcraft.golemRestartScenario",""));
    private static final String PHASE=System.getProperty("thaumcraft.golemRestartPhase","");
    private static final UUID OWNER=UUID.fromString("70f74977-5c01-43cb-8128-0d729d6c594d"), WORKER=UUID.fromString("313b8bed-1675-43d4-9cab-b173cedb48ea");
    private static final GolemSeals.SealKey SOURCE=new GolemSeals.SealKey(new BlockPos(3,120,8),Direction.UP), TARGET=new GolemSeals.SealKey(new BlockPos(12,120,8),Direction.UP);
    private static final GolemUseRules.Settings SETTINGS=new GolemUseRules.Settings(false,true,false,true,true);
    private static MinecraftServer active; private static int ticks,phaseTick; private static boolean prepared,checked,requested;
    private GolemUseRestartProbe() {}
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if(!ENABLED||!PHASE.equals("write")&&!PHASE.equals("read")) return;
        active=event.getServer(); ticks=phaseTick=0; prepared=checked=requested=false; active.overworld().setChunkForced(0,0,true); active.overworld().getChunk(0,0);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { if(active==event.getServer()) active=null; }
    private static IItemHandlerModifiable inventory() { return (IItemHandlerModifiable)GolemContainers.handler(active.overworld(),SOURCE); }
    private static ThaumcraftGolem worker() { return active.overworld().getEntity(WORKER) instanceof ThaumcraftGolem g?g:null; }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if(active!=event.getServer()||checked||++ticks<60) return;
        var level=active.overworld(); boolean ready=level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(0,0))&&level.isPositionEntityTicking(TARGET.pos()); if(!ready&&ticks<600) return;
        try {
            require(ready,"Actual ticking fixture chunk"); var data=GolemSeals.get(level);
            if(PHASE.equals("write")&&!prepared) {
                require(worker()==null&&!data.contains(TARGET.pos(),TARGET.face()),"Fresh isolated Use world");
                for(int x=1;x<=14;x++) for(int z=2;z<=14;z++) level.setBlockAndUpdate(new BlockPos(x,119,z),Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(SOURCE.pos(),Blocks.BARREL.defaultBlockState()); level.setBlockAndUpdate(TARGET.pos(),Blocks.AIR.defaultBlockState());
                data.place(SOURCE.pos(),SOURCE.face(),OWNER,GolemSeals.Type.PROVIDE); var p=data.get(SOURCE); data.configureLogistics(p,OWNER,p.logistics().withSlot(8,new SealFilter(new ItemStack(Items.SPRUCE_PLANKS),false,0)).withMode(false,true,false)); inventory().setStackInSlot(0,new ItemStack(Items.SPRUCE_PLANKS,2));
                data.place(TARGET.pos(),TARGET.face(),OWNER,GolemSeals.Type.USE); data.configure(data.get(TARGET),OWNER,new SealFilter(new ItemStack(Items.OAK_PLANKS),false,0,new SealFilterRules.Options(false,false,true,false))); data.configureUse(data.get(TARGET),OWNER,SETTINGS);
                var g=GolemModule.GOLEM.get().create(level); g.setUUID(WORKER); g.moveTo(Vec3.atBottomCenterOf(SOURCE.pos().north())); g.initialize(GolemDesign.fromIds(0,1,1,0,0,0),OWNER,TARGET.pos(),0); g.setNoAi(true); level.addFreshEntity(g); prepared=true; phaseTick=ticks; return;
            }
            var g=worker(); var seal=data.get(TARGET); require(g!=null&&g.ownerId().equals(OWNER)&&g.design().equals(GolemDesign.fromIds(0,1,1,0,0,0)),"Exact worker/owner/design restored");
            require(seal!=null&&seal.type()==GolemSeals.Type.USE&&data.useSettings(seal).equals(SETTINGS)&&!seal.filter().options().metadata()&&!seal.filter().options().nbt()&&seal.filter().options().ore()&&!seal.filter().options().mod()&&seal.filter().item().is(Items.OAK_PLANKS),"Typed Use action/matching flags and ghost persisted independently of actual spruce");
            if(PHASE.equals("write")) {
                if(ticks-phaseTick<10) return;
                if(!requested) { require(data.claimBlock(g,p->true).isEmpty(),"Empty hands request actual wildcard provision before task can run"); requested=true; phaseTick=ticks; return; }
                if(ticks-phaseTick<25) return;
                var task=data.claimBlock(g,p->true).orElseThrow(); require(task.seal().equals(SOURCE)&&data.completeBlock(level,g,task)&&inventory().getStackInSlot(0).getCount()==1&&g.getMainHandItem().is(Items.SPRUCE_PLANKS)&&g.getMainHandItem().getCount()==1&&g.rankXp()==1&&level.isEmptyBlock(TARGET.pos()),"Real provider2→source1+actualspruce1 before disk; no oak payload invented");
            } else {
                if(!prepared) { require(g.getMainHandItem().is(Items.SPRUCE_PLANKS)&&g.getMainHandItem().getCount()==1&&inventory().getStackInSlot(0).getCount()==1&&g.rankXp()==1&&level.isEmptyBlock(TARGET.pos()),"Actual paid spruce/XP1/source1 survived disk"); g.setNoAi(false); prepared=true; phaseTick=ticks; return; }
                int placed=level.getBlockState(TARGET.pos()).is(Blocks.SPRUCE_PLANKS)?1:0; require(inventory().getStackInSlot(0).getCount()+g.getMainHandItem().getCount()+placed==2,"Every read tick conserves two real spruce items");
                if(placed!=1||!g.getMainHandItem().isEmpty()) { require(ticks-phaseTick<450,"Native Use resumes actual paid cargo and clicks assigned air cell"); return; }
                require(g.rankXp()==2&&inventory().getStackInSlot(0).getCount()==1,"One paid provider click plus one Use click, no replay"); if(ticks-phaseTick<100) return;
            }
            require(level.getEntitiesOfClass(ThaumcraftGolem.class,new AABB(TARGET.pos()).inflate(20)).size()==1&&level.getEntitiesOfClass(ItemEntity.class,new AABB(TARGET.pos()).inflate(20)).isEmpty(),"One worker/no stray cargo copies"); checked=true; LogUtils.getLogger().info("GOLEM_USE_RESTART_{}_ASSERTIONS_OK",PHASE.toUpperCase(java.util.Locale.ROOT));
        } catch(RuntimeException|AssertionError ex) { checked=true; LogUtils.getLogger().error("GOLEM_USE_RESTART_ASSERTIONS_FAILED",ex); }
        if(checked) active.halt(false);
    }
    private static void require(boolean value,String message) { if(!value) throw new AssertionError(message); }
}
