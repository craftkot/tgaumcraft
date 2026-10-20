package dev.ehtid.thaumcraft.golem.test;

import com.mojang.logging.LogUtils;
import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.golem.seal.*;
import dev.ehtid.thaumcraft.port.core.golem.*;
import net.minecraft.core.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.UUID;

/** Opt-in cold Breaker proof: entity/design/seal persist, ephemeral hardness work starts afresh. */
@EventBusSubscriber(modid=ThaumcraftPort.MOD_ID)
public final class GolemBreakerRestartProbe {
    private static final String SCENARIO=System.getProperty("thaumcraft.golemRestartScenario","");
    private static final boolean LUMBER=SCENARIO.equals("lumber"), ENABLED=LUMBER||SCENARIO.equals("breaker");
    private static final String PHASE=System.getProperty("thaumcraft.golemRestartPhase","");
    private static final UUID OWNER=UUID.fromString("517e9a0d-725f-426f-9d13-dfdab465718c"), WORKER=UUID.fromString("4d40468f-1661-4fbf-83d5-a617f8f70026");
    private static final GolemSeals.SealKey KEY=new GolemSeals.SealKey(new BlockPos(8,119,8),Direction.UP);
    private static final BlockPos TARGET=KEY.pos().above();
    private static final GolemDesign DESIGN=GolemDesign.fromIds(0,LUMBER?1:0,3,0,0,0); // Source non-SMART earns no rank XP.
    private static MinecraftServer active; private static int ticks,phaseTick,callbacks; private static boolean prepared,checked; private static GolemSeals.BlockAssignment reservation;
    private GolemBreakerRestartProbe() {}
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if(!ENABLED || !PHASE.equals("write")&&!PHASE.equals("read")) return;
        active=event.getServer(); ticks=phaseTick=callbacks=0; prepared=checked=false; reservation=null;
        active.overworld().setChunkForced(0,0,true); active.overworld().getChunk(0,0);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { if(active==event.getServer()) active=null; }
    private static ThaumcraftGolem worker() { return active.overworld().getEntity(WORKER) instanceof ThaumcraftGolem g?g:null; }
    private static int drops() { return active.overworld().getEntitiesOfClass(ItemEntity.class,new AABB(TARGET).inflate(16)).stream().mapToInt(e->{ require(e.getItem().is(LUMBER?Items.OAK_LOG:Items.OBSIDIAN),"Only actual obsidian loot, no synthetic tool"); return e.getItem().getCount(); }).sum(); }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if(active!=event.getServer() || checked || ++ticks<60) return;
        var level=active.overworld(); boolean ready=level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(0,0)) && level.isPositionEntityTicking(TARGET);
        if(!ready && ticks<600) return;
        try {
            require(ready,"Actual ticking fixture chunk"); var data=GolemSeals.get(level);
            if(PHASE.equals("write") && !prepared) {
                require(worker()==null && !data.contains(KEY.pos(),KEY.face()),"Fresh isolated Breaker world");
                for(int x=1;x<=14;x++) for(int z=2;z<=14;z++) level.setBlockAndUpdate(new BlockPos(x,119,z),Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(TARGET,(LUMBER?Blocks.OAK_LOG:Blocks.OBSIDIAN).defaultBlockState());
                if(LUMBER) { level.setBlockAndUpdate(TARGET.above(),Blocks.OAK_LOG.defaultBlockState()); level.setBlockAndUpdate(TARGET.above(2),Blocks.OAK_LOG.defaultBlockState()); }
                data.place(KEY.pos(),KEY.face(),OWNER,LUMBER?GolemSeals.Type.LUMBER:GolemSeals.Type.BREAKER); data.configureArea(data.get(KEY),OWNER,new BlockPos(1,1,1));
                data.configure(data.get(KEY),OWNER,new SealFilter(new net.minecraft.world.item.ItemStack(Items.OBSIDIAN),false,0,SealFilterRules.Options.strict().withMetadata(false)));
                var g=GolemModule.GOLEM.get().create(level); g.setUUID(WORKER); g.moveTo(Vec3.atBottomCenterOf(TARGET.north())); g.initialize(DESIGN,OWNER,KEY.pos(),0); g.setNoAi(true); level.addFreshEntity(g);
                prepared=true; phaseTick=ticks; return;
            }
            var g=worker(); var seal=data.get(KEY); require(g!=null&&g.ownerId().equals(OWNER)&&g.design().equals(DESIGN),"Exact entity UUID/owner/breaker arms persisted");
            require(seal!=null&&seal.type()==(LUMBER?GolemSeals.Type.LUMBER:GolemSeals.Type.BREAKER)&&seal.area().equals(new BlockPos(1,1,1))&&!seal.filter().options().metadata()&&!seal.filter().blacklist(),"Typed seal/area/filter/pmeta persisted");
            if(PHASE.equals("write")) {
                if(ticks-phaseTick<10) return;
                if(reservation==null) reservation=data.claimBlock(g,p->true).orElseThrow();
                if((ticks-phaseTick-10)%11!=0) return;
                require(!data.completeBlock(level,g,reservation)&&data.blockWorkOngoing(level,g,reservation)&&level.getBlockState(TARGET).is(LUMBER?Blocks.OAK_LOG:Blocks.OBSIDIAN)&&drops()==(LUMBER?1:0),"Actual partial work before disk save");
                if(LUMBER) require(level.isEmptyBlock(TARGET.above(2))&&g.rankXp()==1,"One upper actual log paid before shutdown");
                if(++callbacks<(LUMBER?1:12)) return; // Half the original500 work remains; none of it is persisted as a task.
            } else {
                if(!prepared) { require(level.getBlockState(TARGET).is(LUMBER?Blocks.OAK_LOG:Blocks.OBSIDIAN)&&drops()==(LUMBER?1:0)&&g.getMainHandItem().isEmpty()&&g.rankXp()==(LUMBER?1:0),"No unpaid reward at disk boundary"); g.setNoAi(false); prepared=true; phaseTick=ticks; return; }
                int elapsed=ticks-phaseTick;
                if(!LUMBER&&elapsed<253) require(level.getBlockState(TARGET).is(Blocks.OBSIDIAN)&&drops()==0,"Old task progress was not restored/replayed into new reservation");
                if(LUMBER) { int remaining=0; for(int y=0;y<3;y++) if(level.getBlockState(TARGET.above(y)).is(Blocks.OAK_LOG)) remaining++; require(remaining+drops()==3,"Per read tick three real logs conserved across restart"); }
                if(!level.isEmptyBlock(TARGET)) { require(elapsed<500,"Real native AI completes fresh hardness work after restart"); return; }
                require(drops()==(LUMBER?3:1)&&g.getMainHandItem().isEmpty()&&g.rankXp()==(LUMBER?3:0),"One actual obsidian/no built-in tool/non-SMART no XP");
                if(elapsed<(LUMBER?100:540)) return;
            }
            require(level.getEntitiesOfClass(ThaumcraftGolem.class,new AABB(TARGET).inflate(16)).size()==1,"No duplicate entity");
            checked=true; LogUtils.getLogger().info("GOLEM_{}_RESTART_{}_ASSERTIONS_OK",SCENARIO.toUpperCase(java.util.Locale.ROOT),PHASE.toUpperCase(java.util.Locale.ROOT));
        } catch(RuntimeException|AssertionError ex) { checked=true; LogUtils.getLogger().error("GOLEM_"+SCENARIO.toUpperCase(java.util.Locale.ROOT)+"_RESTART_ASSERTIONS_FAILED",ex); }
        if(checked) active.halt(false);
    }
    private static void require(boolean value,String message) { if(!value) throw new AssertionError(message); }
}
