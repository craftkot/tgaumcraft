package dev.ehtid.thaumcraft.golem.test;

import com.mojang.logging.LogUtils;
import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.golem.inventory.GolemContainers;
import dev.ehtid.thaumcraft.golem.seal.*;
import dev.ehtid.thaumcraft.port.core.golem.GolemDesign;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
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

/** Explicit opt-in two-process test: actual pickup debit -> disk -> delivery from persisted hands. */
@EventBusSubscriber(modid=ThaumcraftPort.MOD_ID)
public final class GolemLogisticsRestartProbe {
    private static final boolean ENABLED="logistics".equals(System.getProperty("thaumcraft.golemRestartScenario",""));
    private static final String PHASE=System.getProperty("thaumcraft.golemRestartPhase","");
    private static final UUID OWNER=UUID.fromString("198c9de5-38fa-4edc-9178-ebca83cbd281"), WORKER=UUID.fromString("0f3cb366-5ab9-4ffd-8a6e-98a4cb253859");
    private static final GolemSeals.SealKey SOURCE=new GolemSeals.SealKey(new BlockPos(3,120,8),Direction.UP), TARGET=new GolemSeals.SealKey(new BlockPos(12,120,8),Direction.UP);
    private static MinecraftServer active; private static int ticks,settled; private static boolean prepared,checked;
    private GolemLogisticsRestartProbe() {}
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if(!ENABLED || !PHASE.equals("write")&&!PHASE.equals("read")) return;
        active=event.getServer(); ticks=settled=0; prepared=checked=false;
        active.overworld().setChunkForced(0,0,true); active.overworld().getChunk(0,0); // Fixture only.
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { if(event.getServer()==active) active=null; }
    private static ItemStack cargo(int amount) { var s=new ItemStack(Items.IRON_INGOT,amount); s.set(DataComponents.CUSTOM_NAME,Component.literal("logistics cold cargo")); return s; }
    private static IItemHandlerModifiable handler(GolemSeals.SealKey key) { return (IItemHandlerModifiable)GolemContainers.handler(active.overworld(),key); }
    private static int stock(GolemSeals.SealKey key) { var inv=handler(key); int n=0; for(int i=0;i<inv.getSlots();i++) n+=inv.getStackInSlot(i).getCount(); return n; }
    private static ThaumcraftGolem worker() { return active.overworld().getEntity(WORKER) instanceof ThaumcraftGolem g?g:null; }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if(active!=event.getServer() || checked || ++ticks<60) return;
        var level=active.overworld();
        boolean ready=level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(0,0)) && level.isPositionEntityTicking(SOURCE.pos());
        if(!ready && ticks<600) return;
        try {
            require(ready,"Fixture chunk really ticking"); var data=GolemSeals.get(level);
            if(PHASE.equals("write") && !prepared) {
                require(worker()==null && !data.contains(SOURCE.pos(),SOURCE.face()),"Fresh isolated write world");
                for(int x=1;x<=14;x++) for(int z=2;z<=14;z++) level.setBlockAndUpdate(new BlockPos(x,119,z),Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(SOURCE.pos(),Blocks.BARREL.defaultBlockState()); level.setBlockAndUpdate(TARGET.pos(),Blocks.BARREL.defaultBlockState());
                data.place(SOURCE.pos(),SOURCE.face(),OWNER,GolemSeals.Type.PROVIDE); data.place(TARGET.pos(),TARGET.face(),OWNER,GolemSeals.Type.STOCK);
                var seal=data.get(TARGET); data.configureLogistics(seal,OWNER,seal.logistics().withSlot(8,new SealFilter(cargo(1),false,12)));
                handler(SOURCE).setStackInSlot(0,cargo(8));
                var g=GolemModule.GOLEM.get().create(level); g.setUUID(WORKER); g.moveTo(Vec3.atBottomCenterOf(SOURCE.pos().east()));
                g.initialize(GolemDesign.defaultWood(),OWNER,g.blockPosition(),0); g.setNoAi(true); require(level.addFreshEntity(g),"Actual golem spawn");
                prepared=true; return;
            }
            if(PHASE.equals("write")) {
                if(ticks<85) return;
                var g=worker(); var pickup=data.claimBlock(g,p->true).orElseThrow();
                require(data.completeBlock(level,g,pickup) && stock(SOURCE)==0 && stock(TARGET)==0 && ItemStack.matches(g.getMainHandItem(),cargo(8)),"Real provider pickup paid8 before save");
                require(data.save(new CompoundTag(),level.registryAccess()).getAllKeys().equals(java.util.Set.of("seals")),"Requests and leases are not persisted as cargo");
            } else {
                var g=worker(); require(g!=null && OWNER.equals(g.ownerId()),"Same persisted worker UUID/owner");
                if(!prepared) {
                    var seal=data.get(TARGET);
                    require(seal!=null && seal.type()==GolemSeals.Type.STOCK && seal.logistics().slot(8).limit()==12
                            && ItemStack.matches(seal.logistics().slot(8).item(),cargo(1)),"Ninth filter/type/desired12/components survived disk restart");
                    require(stock(SOURCE)==0 && stock(TARGET)==0 && ItemStack.matches(g.getMainHandItem(),cargo(8)),"Paid cargo8 survives without another source copy");
                    g.setNoAi(false); prepared=true; return;
                }
                require(stock(SOURCE)+stock(TARGET)+g.getMainHandItem().getCount()+g.transferRecovery().getCount()==8,"Per-tick conservation across resumed delivery");
                if(stock(TARGET)!=8 || !g.getMainHandItem().isEmpty()) { require(ticks<600,"Autonomous resume completes within600 ticks"); return; }
                if(++settled<40) return;
                require(stock(SOURCE)==0 && stock(TARGET)==8,"Unfunded deficit4 never materializes");
                require(level.getEntitiesOfClass(ThaumcraftGolem.class,new AABB(SOURCE.pos()).inflate(20)).size()==1
                        && level.getEntitiesOfClass(ItemEntity.class,new AABB(SOURCE.pos()).inflate(20)).isEmpty(),"One worker and no ground duplicates");
            }
            checked=true; LogUtils.getLogger().info("GOLEM_LOGISTICS_RESTART_{}_ASSERTIONS_OK",PHASE.toUpperCase(java.util.Locale.ROOT));
        } catch(RuntimeException|AssertionError ex) { checked=true; LogUtils.getLogger().error("GOLEM_LOGISTICS_RESTART_ASSERTIONS_FAILED",ex); }
        if(checked) active.halt(false);
    }
    private static void require(boolean value,String message) { if(!value) throw new AssertionError(message); }
}
