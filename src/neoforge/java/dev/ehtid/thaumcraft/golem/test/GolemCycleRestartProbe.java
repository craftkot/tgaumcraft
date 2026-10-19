package dev.ehtid.thaumcraft.golem.test;

import com.mojang.logging.LogUtils;
import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.golem.inventory.GolemContainers;
import dev.ehtid.thaumcraft.golem.seal.*;
import dev.ehtid.thaumcraft.port.core.golem.GolemDesign;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import java.util.UUID;

/** Opt-in two-process source pcycle check: policy persists, completed cursor resets; real cargo never replays. */
@EventBusSubscriber(modid=ThaumcraftPort.MOD_ID)
public final class GolemCycleRestartProbe {
    private static final String SCENARIO=System.getProperty("thaumcraft.golemRestartScenario","");
    private static final boolean MATCHING=SCENARIO.equals("matching"), ENABLED=MATCHING||SCENARIO.equals("cycle");
    private static final String PHASE=System.getProperty("thaumcraft.golemRestartPhase","");
    private static final UUID OWNER=UUID.fromString("251582ae-0aa8-451d-bb8a-292f2dcc37e1"), WORKER=UUID.fromString("49c19dd7-9ede-44cb-80c3-fb7a5dd02213");
    private static final GolemSeals.SealKey SOURCE=new GolemSeals.SealKey(new BlockPos(3,120,8),Direction.UP), SINK=new GolemSeals.SealKey(new BlockPos(12,120,8),Direction.UP);
    private static MinecraftServer active; private static int ticks,phaseTick; private static boolean prepared,checked;
    private GolemCycleRestartProbe() {}
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if(!ENABLED || !PHASE.equals("write")&&!PHASE.equals("read")) return;
        active=event.getServer(); ticks=phaseTick=0; prepared=checked=false;
        active.overworld().setChunkForced(0,0,true); active.overworld().getChunk(0,0); // Fixture only.
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { if(active==event.getServer()) active=null; }
    private static IItemHandlerModifiable inventory(GolemSeals.SealKey key) { return (IItemHandlerModifiable)GolemContainers.handler(active.overworld(),key); }
    private static int stock(GolemSeals.SealKey key) { var inv=inventory(key); int n=0; for(int i=0;i<inv.getSlots();i++) n+=inv.getStackInSlot(i).getCount(); return n; }
    private static ItemStack paid() {
        var stack=new ItemStack(MATCHING?Items.CARROT:Items.IRON_INGOT,MATCHING?4:3);
        if(MATCHING) stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("Actual cold carrot"));
        return stack;
    }
    private static ItemStack nextBatch() {
        var stack=new ItemStack(MATCHING?Items.COAL:Items.IRON_INGOT,2);
        if(MATCHING) stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("Actual cold coal"));
        return stack;
    }
    private static ThaumcraftGolem worker() { return active.overworld().getEntity(WORKER) instanceof ThaumcraftGolem g?g:null; }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if(active!=event.getServer() || checked || ++ticks<60) return;
        var level=active.overworld(); boolean ready=level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(0,0)) && level.isPositionEntityTicking(SOURCE.pos());
        if(!ready && ticks<600) return;
        try {
            require(ready,"Actual ticking fixture chunk"); var data=GolemSeals.get(level);
            if(PHASE.equals("write") && !prepared) {
                require(worker()==null && !data.contains(SOURCE.pos(),SOURCE.face()),"Fresh isolated cycle world");
                for(int x=1;x<=14;x++) for(int z=2;z<=14;z++) level.setBlockAndUpdate(new BlockPos(x,119,z),Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(SOURCE.pos(),Blocks.BARREL.defaultBlockState()); level.setBlockAndUpdate(SINK.pos(),Blocks.BARREL.defaultBlockState());
                data.place(SOURCE.pos(),SOURCE.face(),OWNER,GolemSeals.Type.EMPTY_ADVANCED); var s=data.get(SOURCE);
                data.configureLogistics(s,OWNER,s.logistics().withSlot(0,new SealFilter(new ItemStack(Items.IRON_INGOT),true,0))
                        .withSlot(8,new SealFilter(new ItemStack(Items.GOLD_INGOT),true,0)).withMode(false,true,false).withCycle(true).withOptions(MATCHING?
                                new dev.ehtid.thaumcraft.port.core.golem.SealFilterRules.Options(false,false,true,true):dev.ehtid.thaumcraft.port.core.golem.SealFilterRules.Options.strict()));
                inventory(SOURCE).setStackInSlot(0,MATCHING?paid():new ItemStack(Items.GOLD_INGOT,4)); inventory(SOURCE).setStackInSlot(1,new ItemStack(Items.IRON_INGOT,3));
                var g=GolemModule.GOLEM.get().create(level); g.setUUID(WORKER); g.moveTo(Vec3.atBottomCenterOf(SOURCE.pos().north()));
                g.initialize(GolemDesign.fromIds(0,1,1,0,0,0),OWNER,SOURCE.pos(),0); g.setNoAi(true); require(level.addFreshEntity(g),"Actual SMART worker spawned");
                prepared=true; phaseTick=ticks; return;
            }
            var g=worker(); require(g!=null && OWNER.equals(g.ownerId()),"Same actual entity UUID/owner");
            if(PHASE.equals("write")) {
                if(ticks-phaseTick<25) return;
                var first=data.claimBlock(g,p->true).orElseThrow(); require(data.completeBlock(level,g,first) && ItemStack.matches(g.getMainHandItem(),paid()) && stock(SOURCE)==(MATCHING?3:4),"First source cursor0 debits3 actual iron despite earlier gold slot");
                inventory(SOURCE).setStackInSlot(MATCHING?0:1,nextBatch()); // A declared new fixture batch, never a gameplay supply.
                require(stock(SOURCE)==(MATCHING?5:6) && stock(SINK)==0 && !data.completeBlock(level,g,first),"Completed lease cannot repeat extraction or cursor advance");
                require(data.save(new CompoundTag(),level.registryAccess()).getAllKeys().equals(java.util.Set.of("seals")),"Live cursor/tasks not saved as root state");
            } else {
                if(!prepared) {
                    var s=data.get(SOURCE); require(s!=null && s.type()==GolemSeals.Type.EMPTY_ADVANCED && s.logistics().cycle() && !s.logistics().blacklist()
                            && s.logistics().slot(0).item().is(Items.IRON_INGOT) && s.logistics().slot(8).item().is(Items.GOLD_INGOT),"Original pcycle and sparse ordered ghosts survived disk");
                    require(s.logistics().options().equals(MATCHING?new dev.ehtid.thaumcraft.port.core.golem.SealFilterRules.Options(false,false,true,true)
                            :dev.ehtid.thaumcraft.port.core.golem.SealFilterRules.Options.strict()),"Exact metadata/NBT/ore/mod flags survive disk");
                    require(stock(SOURCE)==(MATCHING?5:6) && stock(SINK)==0 && ItemStack.matches(g.getMainHandItem(),paid()),"Actual paid cargo/source preserved without replay");
                    g.moveTo(Vec3.atBottomCenterOf(SINK.pos().north())); require(GolemContainers.deposit(level,g,SINK,SealFilter.defaults())==paid().getCount(),"Move actual persisted cargo to actual sink");
                    g.moveTo(Vec3.atBottomCenterOf(SOURCE.pos().north())); prepared=true; phaseTick=ticks; return;
                }
                if(ticks-phaseTick<25) return;
                var first=data.claimBlock(g,p->true).orElseThrow();
                require(data.completeBlock(level,g,first) && ItemStack.matches(g.getMainHandItem(),nextBatch())
                        && inventory(SOURCE).getStackInSlot(MATCHING?1:0).getCount()==(MATCHING?3:4),"Source transient cursor restarted at0: choose new iron2, not gold4");
                require(stock(SOURCE)==(MATCHING?3:4) && stock(SINK)==paid().getCount() && g.getMainHandItem().getCount()==2 && !data.completeBlock(level,g,first),"All9 real items conserved and stale completion cannot replay");
                require(level.getEntitiesOfClass(ThaumcraftGolem.class,new AABB(SOURCE.pos()).inflate(20)).size()==1
                        && level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new AABB(SOURCE.pos()).inflate(20)).isEmpty(),"One worker and no world copies");
            }
            checked=true; LogUtils.getLogger().info("GOLEM_{}_RESTART_{}_ASSERTIONS_OK",SCENARIO.toUpperCase(java.util.Locale.ROOT),PHASE.toUpperCase(java.util.Locale.ROOT));
        } catch(RuntimeException|AssertionError ex) { checked=true; LogUtils.getLogger().error("GOLEM_"+SCENARIO.toUpperCase(java.util.Locale.ROOT)+"_RESTART_ASSERTIONS_FAILED",ex); }
        if(checked) active.halt(false);
    }
    private static void require(boolean value,String message) { if(!value) throw new AssertionError(message); }
}
