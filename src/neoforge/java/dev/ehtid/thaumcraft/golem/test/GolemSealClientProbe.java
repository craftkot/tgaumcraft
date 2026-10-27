package dev.ehtid.thaumcraft.golem.test;

import com.mojang.logging.LogUtils;
import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.golem.menu.*;
import dev.ehtid.thaumcraft.golem.seal.GolemSeals;
import net.minecraft.core.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Opt-in fresh creative fixture. Progress requires real hand swap/sneak/menu clicks/bell removal packets. */
@EventBusSubscriber(modid=ThaumcraftPort.MOD_ID)
public final class GolemSealClientProbe {
    private static final boolean ENABLED=Boolean.getBoolean("thaumcraft.golemClientProbe")&&"seals".equals(System.getProperty("thaumcraft.golemClientScenario",""));
    private static final boolean REMOTE=Boolean.getBoolean("thaumcraft.golemSealRemoteProbe");
    private static final GolemSeals.SealKey PICK=new GolemSeals.SealKey(new BlockPos(7,119,5),Direction.UP),FILL=new GolemSeals.SealKey(new BlockPos(11,120,5),Direction.SOUTH),BREAKER=new GolemSeals.SealKey(new BlockPos(4,121,5),Direction.WEST);
    private static MinecraftServer active; private static int phase,ticks,finishedAt; private static boolean checked;
    private GolemSealClientProbe() {}
    @SubscribeEvent public static void started(ServerStartedEvent e) {
        if(!ENABLED||e.getServer().isDedicatedServer()!=REMOTE) return;
        active=e.getServer();phase=ticks=finishedAt=0;checked=false;
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e) { if(active==e.getServer()) active=null; }
    @SubscribeEvent public static void tick(ServerTickEvent.Post e) {
        if(active!=e.getServer()) return;
        ticks++;
        if(checked) {if(REMOTE&&ticks-finishedAt>=200)active.halt(false);return;}
        if(active.getPlayerList().getPlayers().isEmpty()) return; var level=active.overworld(); var player=active.getPlayerList().getPlayers().getFirst();var data=GolemSeals.get(level);
        try {
            if(phase==0) {
                require(!data.contains(PICK.pos(),PICK.face())&&!data.contains(FILL.pos(),FILL.face())&&!data.contains(BREAKER.pos(),BREAKER.face()),"Create a fresh isolated world; prior fixture is never overwritten");
                level.setChunkForced(0,0,true); level.getChunk(0,0);
                level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,active);level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,active);level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false,active);level.setDayTime(6000);level.setWeatherParameters(6000,0,false,false);
                for(int x=0;x<15;x++) for(int z=0;z<15;z++) { level.setBlockAndUpdate(new BlockPos(x,119,z),Blocks.STONE.defaultBlockState());for(int y=120;y<=126;y++) level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState()); }
                level.setBlockAndUpdate(FILL.pos(),Blocks.BARREL.defaultBlockState());level.setBlockAndUpdate(BREAKER.pos(),Blocks.STONE.defaultBlockState());level.setBlockAndUpdate(new BlockPos(7,121,10),Blocks.STONE.defaultBlockState());
                require(data.place(PICK.pos(),PICK.face(),player.getUUID(),GolemSeals.Type.PICKUP)&&data.place(FILL.pos(),FILL.face(),player.getUUID(),GolemSeals.Type.FILL)&&data.place(BREAKER.pos(),BREAKER.face(),player.getUUID(),GolemSeals.Type.BREAKER),"Three actual server seals");
                require(data.configureArea(data.get(PICK),player.getUUID(),new BlockPos(3,1,2))&&data.configureControls(data.get(PICK),player.getUUID(),0,4,true,false),"Actual initial display area/color");
                player.setGameMode(GameType.CREATIVE);player.teleportTo(level,7.5,122,10.5,180,36);player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(GolemModule.GOLEM_BELL.get()));
                phase=1;LogUtils.getLogger().info("GOLEM_CLIENT_SEAL_FIXTURE_READY");return;
            }
            if(phase==1&&player.getOffhandItem().is(GolemModule.GOLEM_BELL.get())&&!player.getMainHandItem().is(GolemModule.GOLEM_BELL.get())) {
                wall(level,true);phase=2;LogUtils.getLogger().info("GOLEM_CLIENT_SEAL_OFFHAND_WALL_READY");return;
            }
            if(phase==2&&player.isShiftKeyDown()) {phase=3;LogUtils.getLogger().info("GOLEM_CLIENT_SEAL_SERVER_CROUCH_OBSERVED");return;}
            if(phase==3&&!player.isShiftKeyDown()&&player.getMainHandItem().is(GolemModule.GOLEM_BELL.get())) {
                wall(level,false);player.teleportTo(level,7.5,120,7.5,180,39);
                require(GolemSealMenus.open(player,level,data.get(PICK)),"Actual owner menu within8block radius");phase=4;LogUtils.getLogger().info("GOLEM_CLIENT_SEAL_EDIT_MENU_READY");return;
            }
            if(phase==4&&data.get(PICK).area().equals(new BlockPos(4,1,2))&&data.get(PICK).color()==5) {
                require(player.containerMenu instanceof GolemSealMenu,"Real C2S area/color changes arrive from actual menu");phase=5;LogUtils.getLogger().info("GOLEM_CLIENT_SEAL_SERVER_EDIT_ASSERTIONS_OK");return;
            }
            if(phase==5&&player.containerMenu==player.inventoryMenu) {
                player.teleportTo(level,7.5,120,7.5,180,39);phase=6;LogUtils.getLogger().info("GOLEM_CLIENT_SEAL_BELL_REMOVE_READY");return;
            }
            if(phase==6&&!data.contains(PICK.pos(),PICK.face())) {
                int drops=level.getEntitiesOfClass(ItemEntity.class,new AABB(PICK.pos()).inflate(8),i->i.getItem().is(GolemModule.PICKUP_SEAL.get())).stream().mapToInt(i->i.getItem().getCount()).sum();
                require(drops==1&&data.contains(FILL.pos(),FILL.face())&&data.contains(BREAKER.pos(),BREAKER.face()),"Real bell removal returns exactly one seal and retains other faces");checked=true;finishedAt=ticks;LogUtils.getLogger().info("GOLEM_CLIENT_SEAL_SERVER_REMOVE_ASSERTIONS_OK");
            }
        } catch(RuntimeException|AssertionError ex) {checked=true;finishedAt=ticks;LogUtils.getLogger().error("GOLEM_CLIENT_SEAL_ASSERTIONS_FAILED",ex);}
    }
    private static void wall(net.minecraft.server.level.ServerLevel level,boolean present) {
        for(int x=3;x<=12;x++) for(int y=120;y<=126;y++) level.setBlockAndUpdate(new BlockPos(x,y,8),(present?Blocks.STONE:Blocks.AIR).defaultBlockState());
    }
    private static void require(boolean value,String message) {if(!value) throw new AssertionError(message);}
}
