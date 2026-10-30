package dev.ehtid.thaumcraft.infusion.test;

import com.mojang.logging.LogUtils;
import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.item.CrystalEssenceItem;
import dev.ehtid.thaumcraft.network.ThaumcraftNetworking;
import dev.ehtid.thaumcraft.player.ThaumcraftPlayerAttachments;
import dev.ehtid.thaumcraft.registry.ThaumcraftBlocks;
import dev.ehtid.thaumcraft.registry.ThaumcraftItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Opt-in test fixture. Only the real remote player starts this paid ritual. */
@EventBusSubscriber(modid=ThaumcraftPort.MOD_ID)
public final class InfusionClientProbe {
    public static final BlockPos MATRIX = new BlockPos(8,128,8);
    public static final BlockPos MARKER = new BlockPos(0,126,0);
    private static final boolean ENABLED = Boolean.getBoolean("thaumcraft.infusionClientProbe");
    private static MinecraftServer server;
    private static PaidInfusionFixture f;
    private static int ticks, phase, finished;
    private static boolean failed;
    @SubscribeEvent public static void started(ServerStartedEvent e) {
        if (ENABLED && e.getServer().isDedicatedServer()) { server=e.getServer(); ticks=phase=finished=0; failed=false; }
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e) { if(server==e.getServer()) server=null; }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (server != event.getServer()) return;
        ticks++;
        if (failed) { if(ticks-finished>=40) server.halt(false); return; }
        try {
            PaidInfusionFixture.require(ticks<6000,"Bounded actual client scenario");
            if (phase==0) {
                if(server.getPlayerList().getPlayerCount()==0) return;
                PaidInfusionFixture.require(server.getPlayerList().getPlayerCount()==1,"One actual TCP player");
                var level=server.overworld(); var player=server.getPlayerList().getPlayers().getFirst();
                PaidInfusionFixture.require(level.getBlockEntity(MATRIX)==null,"Fresh client proof world");
                for(int x=-1;x<=1;x++) for(int z=-1;z<=1;z++){level.setChunkForced(x,z,true);level.getChunk(x,z);}
                level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);
                level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,server);
                level.setDayTime(6000); level.setWeatherParameters(6000,0,false,false);
                for(int x=0;x<18;x++) for(int z=0;z<18;z++) level.setBlockAndUpdate(new BlockPos(x,125,z),Blocks.STONE.defaultBlockState());
                f=new PaidInfusionFixture(level,MATRIX,"aer"); f.actor=player;
                level.setBlockAndUpdate(MATRIX,ThaumcraftBlocks.INFUSION_MATRIX.get().defaultBlockState());
                for(var p:f.pedestals) level.setBlockAndUpdate(p,ThaumcraftBlocks.PEDESTAL_ARCANE.get().defaultBlockState());
                for(int x:new int[]{-1,1})for(int z:new int[]{-1,1})level.setBlockAndUpdate(MATRIX.offset(x,-2,z),ThaumcraftBlocks.PILLAR_ARCANE.get().defaultBlockState());
                f.pedestal(0).insertFromPlayer(CrystalEssenceItem.ofAspect("aer"));
                f.pedestal(1).insertFromPlayer(new ItemStack(Items.WHEAT_SEEDS));
                f.pedestal(2).insertFromPlayer(new ItemStack(ThaumcraftItems.SALIS_MUNDUS.get()));
                String[] aspects={"aer","vitreus","vinculum"};int[] costs={10,10,5};
                for(int i=0;i<3;i++){level.setBlockAndUpdate(f.jars.get(i),ThaumcraftBlocks.JAR_NORMAL.get().defaultBlockState());
                    PaidInfusionFixture.require(f.jar(i).addFromTransport(aspects[i],costs[i],Direction.UP)==costs[i],"Exact physical test funding");}
                player.setGameMode(GameType.SURVIVAL); player.getInventory().clearContent();
                player.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge().addResearch("CRYSTALFARMER");
                ThaumcraftNetworking.sync(player); // Explicit test prerequisite, not earned Survival progression.
                player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ThaumcraftItems.CASTER_BASIC.get()));
                player.getInventory().setChanged();player.inventoryMenu.broadcastChanges();
                player.teleportTo(level,8.5,126,12.3,180,0);
                level.setBlockAndUpdate(MARKER,Blocks.GOLD_BLOCK.defaultBlockState());phase=1;
                LogUtils.getLogger().info("INFUSION_CLIENT_SERVER_READY researchFixture=true physicalEssentia=25 actualTCP=true");return;
            }
            PaidInfusionFixture.require(server.getPlayerList().getPlayers().contains(f.actor),"Same connected player");
            if(phase==1){
                if(!f.matrix().crafting())return;
                f.ritual=f.ledger().getUUID("RitualId");phase=2;
                LogUtils.getLogger().info("INFUSION_CLIENT_SERVER_NETWORK_START ritual={}",f.ritual);
            }
            f.checkLivePayment();
            if(phase==2 && !f.matrix().crafting()){
                f.verifyFinished();finished=ticks;phase=3;f.snapshot("TCP_PAID_FINISHED");
            }
            if(phase==3){
                f.verifyFinished();
                if(ticks-finished==120){
                    f.level.setBlockAndUpdate(MARKER,Blocks.EMERALD_BLOCK.defaultBlockState());
                    LogUtils.getLogger().info("INFUSION_CLIENT_SERVER_ASSERTIONS_OK paid=25 sideItems=2 output=1 noReplayTicks=120 actualTCP=true");
                }
                if(ticks-finished>=240)server.halt(false);
            }
        }catch(RuntimeException|AssertionError error){failed=true;finished=ticks;LogUtils.getLogger().error("INFUSION_CLIENT_SERVER_ASSERTIONS_FAILED",error);}
    }
}
