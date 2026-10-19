package dev.ehtid.thaumcraft.golem.test;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.blockentity.EssentiaJarBlockEntity;
import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.golem.press.GolemPressBlockEntity;
import dev.ehtid.thaumcraft.golem.press.GolemPressStructures;
import dev.ehtid.thaumcraft.player.ThaumcraftPlayerAttachments;
import dev.ehtid.thaumcraft.port.core.golem.GolemPressBlueprint.Part;
import dev.ehtid.thaumcraft.registry.ThaumcraftBlocks;
import dev.ehtid.thaumcraft.registry.ThaumcraftItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.UUID;
import static dev.ehtid.thaumcraft.golem.test.GolemFormationGameTests.pos;

/** Opt-in actual assembly→disk→paid resume→placeholder break across two dedicated processes. */
@EventBusSubscriber(modid=ThaumcraftPort.MOD_ID)
public final class GolemFormationRestartProbe {
    private static final boolean ENABLED="formation".equals(System.getProperty("thaumcraft.golemRestartScenario",""));
    private static final String PHASE=System.getProperty("thaumcraft.golemRestartPhase","");
    private static final BlockPos ORIGIN=new BlockPos(8,120,8), CONTROLLER=pos(ORIGIN,1,Part.CONTROLLER);
    private static MinecraftServer active; private static int ticks; private static boolean started,checked; private static FakePlayer owner;
    private GolemFormationRestartProbe() {}
    @SubscribeEvent public static void started(ServerStartedEvent event){
        if(!ENABLED||!PHASE.equals("write")&&!PHASE.equals("read"))return;
        active=event.getServer();ticks=0;started=false;checked=false;
        active.overworld().setChunkForced(0,0,true);active.overworld().getChunk(0,0); // Fixture only.
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){if(event.getServer()==active){active=null;owner=null;}}
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        if(event.getServer()!=active||checked||++ticks<60)return;
        var level=active.overworld();boolean ready=level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(0,0))&&level.isPositionEntityTicking(ORIGIN);
        if(!ready&&ticks<600)return;
        try{
            require(ready,"Fixture chunk ticking");
            if(PHASE.equals("write")&&!started){
                require(!level.getBlockState(CONTROLLER).is(GolemModule.PRESS.get()),"Fresh write fixture");
                owner=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"FormationRestart"));owner.setGameMode(GameType.SURVIVAL);owner.getInventory().clearContent();
                owner.moveTo(Vec3.atBottomCenterOf(CONTROLLER.east()));owner.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge().addResearch("MINDCLOCKWORK");
                GolemFormationGameTests.placeSource(level,ORIGIN,1);owner.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ThaumcraftItems.SALIS_MUNDUS.get()));
                require(GolemFormationGameTests.use(owner,CONTROLLER,Direction.UP).consumesAction()&&owner.getMainHandItem().isEmpty(),"Actual single dust payment");
                started=true;return;
            }
            if(PHASE.equals("write")&&ticks<115)return;
            checked=true;
            if(PHASE.equals("write")){
                require(CONTROLLER.equals(GolemPressStructures.get(level).controllerFor(pos(ORIGIN,1,Part.BARS))),"Actual fifty-tick formation completed");
                GolemPressGameTests.ingredients(owner.getInventory(),1);
                level.setBlockAndUpdate(CONTROLLER.below(),ThaumcraftBlocks.JAR_NORMAL.get().defaultBlockState());
                var jar=(EssentiaJarBlockEntity)level.getBlockEntity(CONTROLLER.below());
                for(int i=0;i<8;i++)require(jar.addFromTransport("machina",1,Direction.UP)==1,"Physical fixture Machina stock");
                var press=(GolemPressBlockEntity)level.getBlockEntity(CONTROLLER);
                require(press.start(owner)&&owner.getInventory().isEmpty(),"Real component payment in formed machine");
                var blocker=new ItemStack(Items.GOLD_INGOT,2);blocker.set(DataComponents.CUSTOM_NAME,Component.literal("formation cold blocker"));press.setItem(0,blocker);
                require(press.job().remaining()==8,"Save before processing");
            }else{
                var data=GolemPressStructures.get(level);
                for(Part part:Part.values())require(CONTROLLER.equals(data.controllerFor(pos(ORIGIN,1,part))),"All five links survived actual disk restart");
                var press=(GolemPressBlockEntity)level.getBlockEntity(CONTROLLER);var jar=(EssentiaJarBlockEntity)level.getBlockEntity(CONTROLLER.below());
                require(press.job().ready()&&jar.snapshot().amount()==0,"Restored machine paid exactly8 Machina and retained ready product");
                var blocker=press.removeItem(0,2);require(blocker.getCount()==2&&blocker.getHoverName().getString().equals("formation cold blocker"),"Actual stored output components survived");
                for(int i=0;i<5;i++)GolemPressBlockEntity.tick(level,CONTROLLER,press.getBlockState(),press);
                require(press.getItem(0).is(GolemModule.GOLEM_PLACER.get())&&press.getItem(0).getCount()==1,"One actual paid golem product after resume");
                level.destroyBlock(pos(ORIGIN,1,Part.ANVIL),true);
                require(level.getBlockState(CONTROLLER).is(Blocks.PISTON)&&data.controllerFor(CONTROLLER)==null,"Persisted placeholder link restores controller on break");
                var drops=level.getEntitiesOfClass(ItemEntity.class,new AABB(ORIGIN).inflate(5));
                require(drops.stream().filter(e->e.getItem().is(GolemModule.GOLEM_PLACER.get())).mapToInt(e->e.getItem().getCount()).sum()==1,"Exactly one actual golem output drop");
                require(drops.stream().filter(e->e.getItem().is(Items.ANVIL)).mapToInt(e->e.getItem().getCount()).sum()==1&&drops.size()==2,"Exactly broken anvil plus actual output, no constituent duplicate refunds");
            }
            LogUtils.getLogger().info("GOLEM_FORMATION_RESTART_{}_ASSERTIONS_OK",PHASE.toUpperCase(java.util.Locale.ROOT));
        }catch(RuntimeException|AssertionError error){checked=true;LogUtils.getLogger().error("GOLEM_FORMATION_RESTART_ASSERTIONS_FAILED",error);}
        if(checked)active.halt(false);
    }
    private static void require(boolean condition,String text){if(!condition)throw new AssertionError(text);}
}
