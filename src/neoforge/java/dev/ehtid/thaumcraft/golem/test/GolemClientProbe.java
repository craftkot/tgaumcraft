package dev.ehtid.thaumcraft.golem.test;

import com.mojang.logging.LogUtils;
import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.golem.inventory.GolemContainers;
import dev.ehtid.thaumcraft.golem.menu.*;
import dev.ehtid.thaumcraft.golem.seal.GolemSeals;
import dev.ehtid.thaumcraft.port.core.golem.GolemDesign;
import net.minecraft.core.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

/** Opt-in isolated integrated-client fixture. Assertions require real C2S widget changes and actual AI transfers. */
@EventBusSubscriber(modid=ThaumcraftPort.MOD_ID)
public final class GolemClientProbe {
    private static final boolean ENABLED=Boolean.getBoolean("thaumcraft.golemClientProbe");
    private static final GolemSeals.SealKey USE=new GolemSeals.SealKey(new BlockPos(4,120,9),Direction.UP), BREAKER=new GolemSeals.SealKey(new BlockPos(8,120,9),Direction.UP), PICKUP=new GolemSeals.SealKey(new BlockPos(3,119,4),Direction.UP), FILL=new GolemSeals.SealKey(new BlockPos(10,120,4),Direction.UP);
    private static MinecraftServer active; private static int phase,ticks,phaseTick; private static boolean useChecked,silkChecked,done,failed;
    private static ThaumcraftGolem worker; private static ItemEntity paid;
    private GolemClientProbe() {}
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if(!ENABLED||event.getServer().isDedicatedServer()) return;
        active=event.getServer(); phase=ticks=phaseTick=0; useChecked=silkChecked=done=failed=false; worker=null; paid=null;
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { if(active==event.getServer()) active=null; }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if(active!=event.getServer()||done||failed||active.getPlayerList().getPlayers().isEmpty()) return;
        ticks++; ServerPlayer player=active.getPlayerList().getPlayers().getFirst(); var level=active.overworld(); var data=GolemSeals.get(level);
        try {
            if(phase==0) {
                require(!data.contains(USE.pos(),USE.face())&&!data.contains(BREAKER.pos(),BREAKER.face()),"Create a fresh isolated smoke world; existing fixture remains untouched");
                level.setChunkForced(0,0,true); level.getChunk(0,0);
                level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,active); level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false,active); level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,active); level.setDayTime(6000); level.setWeatherParameters(6000,0,false,false);
                for(int x=0;x<15;x++) for(int z=0;z<15;z++) { level.setBlockAndUpdate(new BlockPos(x,119,z),Blocks.STONE.defaultBlockState()); for(int y=120;y<126;y++) level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState()); }
                player.setGameMode(GameType.CREATIVE); player.teleportTo(level,6.5,120,11.5,180,15);
                level.setBlockAndUpdate(USE.pos(),Blocks.STONE.defaultBlockState()); level.setBlockAndUpdate(BREAKER.pos(),Blocks.STONE.defaultBlockState());
                require(data.place(USE.pos(),USE.face(),player.getUUID(),GolemSeals.Type.USE)&&data.place(BREAKER.pos(),BREAKER.face(),player.getUUID(),GolemSeals.Type.BREAKER_ADVANCED),"Fresh isolated client fixture; do not rerun in an existing fixture world");
                require(!data.useSettings(data.get(USE)).emptyHand()&&!data.get(BREAKER).logistics().silk(),"Initial flags false, no seeded success");
                phase=1; phaseTick=ticks; return;
            }
            if(phase==1&&ticks-phaseTick>=40) {
                require(GolemSealMenus.open(player,level,data.get(USE)),"Actual owner Use menu opens through normal menu provider"); phase=2; phaseTick=ticks; LogUtils.getLogger().info("GOLEM_CLIENT_USE_MENU_READY"); return;
            }
            if(phase==2) {
                if(!useChecked&&data.useSettings(data.get(USE)).emptyHand()) { require(player.containerMenu instanceof GolemSealMenu menu&&menu.use(),"Actual Use container still open when real C2S toggle arrives"); useChecked=true; LogUtils.getLogger().info("GOLEM_CLIENT_USE_TOGGLE_ASSERTIONS_OK"); }
                if(useChecked&&player.containerMenu==player.inventoryMenu) { phase=3; phaseTick=ticks; } return;
            }
            if(phase==3&&ticks-phaseTick>=20) {
                require(GolemSealMenus.open(player,level,data.get(BREAKER)),"Actual advanced breaker menu opens"); phase=4; phaseTick=ticks; LogUtils.getLogger().info("GOLEM_CLIENT_SILK_MENU_READY"); return;
            }
            if(phase==4) {
                if(!silkChecked&&data.get(BREAKER).logistics().silk()) { require(player.containerMenu instanceof GolemLogisticsMenu menu&&menu.advancedBreaker(),"Actual advanced container receives C2S psilk action"); silkChecked=true; LogUtils.getLogger().info("GOLEM_CLIENT_SILK_TOGGLE_ASSERTIONS_OK"); }
                if(silkChecked&&player.containerMenu==player.inventoryMenu) {
                    level.setBlockAndUpdate(FILL.pos(),Blocks.BARREL.defaultBlockState()); require(data.place(PICKUP.pos(),PICKUP.face(),player.getUUID(),GolemSeals.Type.PICKUP)&&data.place(FILL.pos(),FILL.face(),player.getUUID(),GolemSeals.Type.FILL),"Fresh actual logistics fixture");
                    worker=GolemModule.GOLEM.get().create(level); worker.moveTo(6.5,120,7.5); worker.initialize(GolemDesign.fromIds(0,0,0,1,0,0),player.getUUID(),new BlockPos(6,120,6),0); require(level.addFreshEntity(worker),"One actual source wheeled worker spawned");
                    paid=new ItemEntity(level,3.5,120,4.5,new ItemStack(Items.IRON_INGOT,11)); paid.setDeltaMovement(Vec3.ZERO); paid.setNoPickUpDelay(); require(level.addFreshEntity(paid),"Actual creative fixture input11, not a transfer reward"); phase=5; phaseTick=ticks;
                } return;
            }
            if(phase==5) {
                var inventory=(IItemHandlerModifiable)GolemContainers.handler(level,FILL); int stock=0; for(int i=0;i<inventory.getSlots();i++) stock+=inventory.getStackInSlot(i).getCount();
                require(stock+worker.getMainHandItem().getCount()+(paid.isRemoved()?0:paid.getItem().getCount())==11,"Integrated server preserves11actualitems each tick");
                if(stock==11&&worker.getMainHandItem().isEmpty()&&paid.isRemoved()) { require(useChecked&&silkChecked&&!worker.isNoAi(),"Both actual client widget changes and actual enabled AI checked"); done=true; LogUtils.getLogger().info("GOLEM_CLIENT_AI_TRANSFER_ASSERTIONS_OK"); }
                else require(ticks-phaseTick<600,"Actual integrated client AI transfer completes within watchdog");
            }
        } catch(RuntimeException|AssertionError ex) { failed=true; LogUtils.getLogger().error("GOLEM_CLIENT_ASSERTIONS_FAILED",ex); }
    }
    private static void require(boolean value,String message) { if(!value) throw new AssertionError(message); }
}
