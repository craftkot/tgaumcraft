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

/** Opt-in real two-process Advanced Pickup -> carried cargo -> Advanced Fill check. */
@EventBusSubscriber(modid=ThaumcraftPort.MOD_ID)
public final class GolemAdvancedRestartProbe {
    private static final boolean ENABLED="advanced".equals(System.getProperty("thaumcraft.golemRestartScenario",""));
    private static final String PHASE=System.getProperty("thaumcraft.golemRestartPhase","");
    private static final UUID OWNER=UUID.fromString("cc53db0c-2014-48ca-a563-34a7dd233c90"), WORKER=UUID.fromString("f37be34b-d63a-4a0e-a40e-575e16ebc6e2");
    private static final GolemSeals.SealKey SOURCE=new GolemSeals.SealKey(new BlockPos(3,119,8),Direction.UP), SINK=new GolemSeals.SealKey(new BlockPos(12,120,8),Direction.UP);
    private static MinecraftServer active; private static int ticks,phaseTick; private static boolean prepared,checked;
    private GolemAdvancedRestartProbe() {}
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if(!ENABLED || !PHASE.equals("write")&&!PHASE.equals("read")) return;
        active=event.getServer(); ticks=phaseTick=0; prepared=checked=false;
        active.overworld().setChunkForced(0,0,true); active.overworld().getChunk(0,0);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { if(active==event.getServer()) active=null; }
    private static ItemStack cargo(int count) { var s=new ItemStack(Items.IRON_INGOT,count); s.set(DataComponents.CUSTOM_NAME,Component.literal("Paid advanced cargo")); return s; }
    private static IItemHandlerModifiable inventory() { return (IItemHandlerModifiable)GolemContainers.handler(active.overworld(),SINK); }
    private static int stock() { int n=0; var inv=inventory(); for(int i=0;i<inv.getSlots();i++) { var s=inv.getStackInSlot(i); require(s.isEmpty()||ItemStack.isSameItemSameComponents(s,cargo(1)),"Actual named cargo only"); n+=s.getCount(); } return n; }
    private static ThaumcraftGolem worker() { return active.overworld().getEntity(WORKER) instanceof ThaumcraftGolem g?g:null; }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if(active!=event.getServer() || checked || ++ticks<60) return;
        var level=active.overworld(); boolean ready=level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(0,0)) && level.isPositionEntityTicking(SOURCE.pos());
        if(!ready && ticks<600) return;
        try {
            require(ready,"Actual ticking fixture chunk"); var data=GolemSeals.get(level);
            if(PHASE.equals("write") && !prepared) {
                require(worker()==null && !data.contains(SOURCE.pos(),SOURCE.face()),"Fresh isolated advanced world");
                for(int x=1;x<=14;x++) for(int z=2;z<=14;z++) level.setBlockAndUpdate(new BlockPos(x,119,z),Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(SINK.pos(),Blocks.BARREL.defaultBlockState());
                data.place(SOURCE.pos(),SOURCE.face(),OWNER,GolemSeals.Type.PICKUP_ADVANCED); var source=data.get(SOURCE);
                data.configureArea(source,OWNER,new BlockPos(1,1,1)); source=data.get(SOURCE);
                data.configureLogistics(source,OWNER,source.logistics().withSlot(8,new SealFilter(cargo(1),false,0)).withMode(false,true,false));
                data.place(SINK.pos(),SINK.face(),OWNER,GolemSeals.Type.FILL_ADVANCED); var sink=data.get(SINK);
                data.configureLogistics(sink,OWNER,sink.logistics().withSlot(8,new SealFilter(cargo(1),false,7)).withMode(false,true,false).withExistingOnly(true));
                inventory().setStackInSlot(0,cargo(1));
                var item=new ItemEntity(level,3.5,120,8.5,cargo(5)); item.setDeltaMovement(Vec3.ZERO); item.setNoPickUpDelay(); level.addFreshEntity(item);
                var g=GolemModule.GOLEM.get().create(level); g.setUUID(WORKER); g.moveTo(3.5,120,7.5); g.initialize(GolemDesign.fromIds(0,1,1,0,0,0),OWNER,SOURCE.pos(),0); g.setNoAi(true); level.addFreshEntity(g);
                prepared=true; phaseTick=ticks; return;
            }
            var g=worker(); require(g!=null && g.ownerId().equals(OWNER),"Exact persisted worker UUID/owner");
            var source=data.get(SOURCE); var sink=data.get(SINK);
            require(source!=null && source.type()==GolemSeals.Type.PICKUP_ADVANCED && source.area().equals(new BlockPos(1,1,1)) && !source.logistics().slot(8).item().isEmpty(),"Pickup type/area/ninth ghost persisted");
            require(sink!=null && sink.type()==GolemSeals.Type.FILL_ADVANCED && sink.logistics().existingOnly() && sink.logistics().slot(8).limit()==7,"Fill type/pexist/ninth limit persisted");
            if(PHASE.equals("write")) {
                if(ticks-phaseTick<25) return;
                var pickup=data.claim(g,x->true).orElseThrow(); require(data.pickup(level,g,pickup) && ItemStack.matches(g.getMainHandItem(),cargo(5)) && stock()==1,"Actual five items picked before disk save");
                g.moveTo(6.5,120,8.5); require(data.claimFill(g,p->true).isPresent(),"Pending transient Fill task before shutdown");
            } else {
                if(!prepared) { require(ItemStack.matches(g.getMainHandItem(),cargo(5)) && stock()==1,"Exact named five carried plus one target survived disk"); g.setNoAi(false); prepared=true; phaseTick=ticks; return; }
                require(stock()+g.getMainHandItem().getCount()==6,"Every read tick conserves six actual items");
                if(stock()!=6 || !g.getMainHandItem().isEmpty()) { require(ticks-phaseTick<450,"Native AI resumes persisted advanced Fill"); return; }
                if(ticks-phaseTick<500) return;
            }
            require(level.getEntitiesOfClass(ThaumcraftGolem.class,new AABB(SOURCE.pos()).inflate(20)).size()==1 && level.getEntitiesOfClass(ItemEntity.class,new AABB(SOURCE.pos()).inflate(20)).isEmpty(),"One worker/no replay ground cargo");
            checked=true; LogUtils.getLogger().info("GOLEM_ADVANCED_RESTART_{}_ASSERTIONS_OK",PHASE.toUpperCase(java.util.Locale.ROOT));
        } catch(RuntimeException|AssertionError ex) { checked=true; LogUtils.getLogger().error("GOLEM_ADVANCED_RESTART_ASSERTIONS_FAILED",ex); }
        if(checked) active.halt(false);
    }
    private static void require(boolean value,String message) { if(!value) throw new AssertionError(message); }
}
