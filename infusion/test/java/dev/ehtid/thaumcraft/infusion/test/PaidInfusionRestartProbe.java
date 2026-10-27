package dev.ehtid.thaumcraft.infusion.test;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.registry.ThaumcraftItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.UUID;

/** Opt-in normal save/stop -> fresh JVM disk reload. Never seeds or repairs the read phase. */
@EventBusSubscriber(modid=ThaumcraftPort.MOD_ID)
public final class PaidInfusionRestartProbe {
    private static final String PHASE=System.getProperty("thaumcraft.infusionRestartPhase","");
    private static final BlockPos MATRIX=new BlockPos(8,128,8);
    private static MinecraftServer server;
    private static PaidInfusionFixture fixture;
    private static int ticks;
    private static boolean done;
    private PaidInfusionRestartProbe() {}
    private static Path manifest() { return server.getWorldPath(LevelResource.ROOT).resolve("infusion-paid-test.properties"); }
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if(!PHASE.equals("write") && !PHASE.equals("read")) return;
        server=event.getServer(); ticks=0; done=false;
        try {
            var level=server.overworld();
            // Source scan and all physical blocks fit within this forced fixture chunk and its neighbours.
            for(int x=-1;x<=1;x++) for(int z=-1;z<=1;z++) { level.setChunkForced(x,z,true); level.getChunk(x,z); }
            fixture=new PaidInfusionFixture(level,MATRIX,"aer");
            if(PHASE.equals("write")) {
                PaidInfusionFixture.require(!Files.exists(manifest()),"Fresh isolated restart world required");
                fixture.constructAndStart();
            } else {
                var saved=new Properties(); try(var in=Files.newInputStream(manifest())) { saved.load(in); }
                fixture.ritual=UUID.fromString(saved.getProperty("ritual"));
                fixture.lastPaid=Integer.parseInt(saved.getProperty("paid"));
                PaidInfusionFixture.require(fixture.matrix()!=null && fixture.matrix().crafting(),"Actual saved incomplete ritual loaded before native ticks");
                var pending=fixture.ledger();
                PaidInfusionFixture.require(fixture.ritual.equals(pending.getUUID("RitualId"))
                        && pending.getInt("Paid")==fixture.lastPaid && fixture.lastPaid>0 && fixture.lastPaid<25,
                        "Exact partially paid ritual UUID/checkpoint survived new JVM");
                for(int i=0;i<3;i++) PaidInfusionFixture.require(fixture.jar(i).snapshot().amount()==Integer.parseInt(saved.getProperty("jar"+i)),"Each actual disk-loaded jar equals pre-stop balance");
                PaidInfusionFixture.require(fixture.pedestal(0).isOccupied() && fixture.pedestal(1).isOccupied()
                        && fixture.pedestal(2).isOccupied(),"All unpaid ingredient stages preserved before continuation");
                fixture.actor=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"InfusionReloadTest"));
                fixture.actor.setGameMode(GameType.SURVIVAL); fixture.actor.moveTo(Vec3.atBottomCenterOf(MATRIX.north()));
                fixture.actor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ThaumcraftItems.CASTER_BASIC.get()));
                fixture.snapshot("COLD_LOADED_BEFORE_TICKS");
                LogUtils.getLogger().info("INFUSION_COLD_LOAD_PARTIAL_ASSERTIONS_OK paid={} ritual={}",fixture.lastPaid,fixture.ritual);
            }
        } catch(Exception|AssertionError ex) { fail(ex); }
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if(server!=event.getServer() || done) return;
        try {
            ticks++; PaidInfusionFixture.require(ticks<1000,"Native paid restart probe timeout");
            fixture.checkLivePayment();
            if(PHASE.equals("write")) {
                if(fixture.lastPaid<8) return;
                PaidInfusionFixture.require(fixture.lastPaid<25,"Save while incomplete");
                var saved=new Properties(); saved.setProperty("ritual",fixture.ritual.toString()); saved.setProperty("paid",Integer.toString(fixture.lastPaid));
                for(int i=0;i<3;i++) saved.setProperty("jar"+i,Integer.toString(fixture.jar(i).snapshot().amount()));
                try(var out=Files.newOutputStream(manifest())) { saved.store(out,"Paid infusion explicit test fixture; new JVM read must match these physical balances"); }
                fixture.snapshot("BEFORE_NORMAL_SAVE_STOP");
                PaidInfusionFixture.require(server.saveEverything(true,true,true),"Normal server saveEverything(flush=true) succeeds");
                done=true;
                LogUtils.getLogger().info("INFUSION_PAID_RESTART_WRITE_ASSERTIONS_OK paid={} ritual={}",fixture.lastPaid,fixture.ritual);
                server.halt(false); // Graceful stop saves world; runner must start a separate JVM with phase=read.
            } else if(!fixture.matrix().crafting()) {
                fixture.verifyFinished();
                if(fixture.completionTick<0) {
                    fixture.completionTick=ticks; fixture.snapshot("COLD_CONTINUATION_FINISHED"); fixture.casterClick();
                    PaidInfusionFixture.require(!fixture.matrix().crafting(),"No replay after cold continuation");
                }
                if(ticks-fixture.completionTick>=120) {
                    fixture.verifyFinished(); fixture.snapshot("COLD_NO_REPLAY_120_TICKS"); done=true;
                    LogUtils.getLogger().info("INFUSION_PAID_RESTART_READ_ASSERTIONS_OK paid=25 output=1 replayTicks=120 ritual={}",fixture.ritual);
                    server.halt(false);
                }
            }
        } catch(Exception|AssertionError ex) { fail(ex); }
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { if(server==event.getServer()) server=null; }
    private static void fail(Throwable ex) {
        done=true; LogUtils.getLogger().error("INFUSION_PAID_RESTART_"+PHASE+"_ASSERTIONS_FAILED",ex);
        if(server!=null) server.halt(false);
    }
}
