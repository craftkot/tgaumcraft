package dev.ehtid.thaumcraft.golem.test;

import com.mojang.logging.LogUtils;
import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.entity.*;
import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.golem.inventory.GolemContainers;
import dev.ehtid.thaumcraft.golem.menu.*;
import dev.ehtid.thaumcraft.golem.seal.GolemSeals;
import dev.ehtid.thaumcraft.port.core.golem.GolemDesign;
import net.minecraft.core.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

/** Opt-in isolated integrated-client fixture. Assertions require real C2S widget changes and actual AI transfers. */
@EventBusSubscriber(modid=ThaumcraftPort.MOD_ID)
public final class GolemClientProbe {
    private static final boolean ENABLED=Boolean.getBoolean("thaumcraft.golemClientProbe");
    private static final boolean COMBAT="combat".equals(System.getProperty("thaumcraft.golemClientScenario",""));
    private static final GolemSeals.SealKey USE=new GolemSeals.SealKey(new BlockPos(4,120,9),Direction.UP), BREAKER=new GolemSeals.SealKey(new BlockPos(8,120,9),Direction.UP), PICKUP=new GolemSeals.SealKey(new BlockPos(3,119,4),Direction.UP), FILL=new GolemSeals.SealKey(new BlockPos(10,120,4),Direction.UP);
    private static MinecraftServer active; private static int phase,ticks,phaseTick; private static boolean useChecked,silkChecked,done,failed;
    private static ThaumcraftGolem worker; private static ItemEntity paid;
    private static final GolemSeals.SealKey GUARD=new GolemSeals.SealKey(new BlockPos(11,123,5),Direction.UP);
    private static ThaumcraftGolem shooter; private static LivingEntity victim;
    private static boolean guardChecked,rangedKill; private static int xpAtKill;
    private GolemClientProbe() {}
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if(!ENABLED||event.getServer().isDedicatedServer()||java.util.Set.of("seals","crops").contains(System.getProperty("thaumcraft.golemClientScenario",""))) return;
        active=event.getServer(); phase=ticks=phaseTick=0; useChecked=silkChecked=done=failed=guardChecked=rangedKill=false; worker=shooter=null; paid=null; victim=null;
    }
    @SubscribeEvent public static void death(LivingDeathEvent event) {
        if(active==null||phase!=8||event.getEntity()!=victim) return;
        require(event.getSource().getDirectEntity() instanceof GolemDart&&event.getSource().getEntity()==shooter,"Actual client fixture target killed by original golem dart");
        rangedKill=true; xpAtKill=shooter.rankXp(); LogUtils.getLogger().info("GOLEM_CLIENT_NATIVE_DART_KILL_OBSERVED");
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { if(active==event.getServer()) active=null; }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if(active!=event.getServer()||done||failed||active.getPlayerList().getPlayers().isEmpty()) return;
        ticks++; ServerPlayer player=active.getPlayerList().getPlayers().getFirst(); var level=active.overworld(); var data=GolemSeals.get(level);
        try {
            if(phase==0) {
                require(!data.contains(USE.pos(),USE.face())&&!data.contains(BREAKER.pos(),BREAKER.face())&&!data.contains(GUARD.pos(),GUARD.face()),"Create a fresh isolated smoke world; existing fixture remains untouched");
                level.setChunkForced(0,0,true); level.getChunk(0,0);
                level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,active); level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false,active); level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,active); level.setDayTime(6000); level.setWeatherParameters(6000,0,false,false);
                for(int x=0;x<15;x++) for(int z=0;z<15;z++) { level.setBlockAndUpdate(new BlockPos(x,119,z),Blocks.STONE.defaultBlockState()); for(int y=120;y<126;y++) level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState()); }
                player.setGameMode(GameType.CREATIVE); player.teleportTo(level,6.5,120,11.5,180,15);
                if(COMBAT) {
                    level.setBlockAndUpdate(GUARD.pos(),Blocks.STONE.defaultBlockState()); require(data.place(GUARD.pos(),GUARD.face(),player.getUUID(),GolemSeals.Type.GUARD_ADVANCED),"Fresh combat-only advanced guard fixture");
                    var settings=data.combatSettings(data.get(GUARD)); require(settings.mobs()&&!settings.animals()&&!settings.players(),"Source defaults before actual C2S changes");
                    phase=6; phaseTick=ticks; return;
                }
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
                if(stock==11&&worker.getMainHandItem().isEmpty()&&paid.isRemoved()) {
                    require(useChecked&&silkChecked&&!worker.isNoAi(),"Both actual client widget changes and actual enabled AI checked"); LogUtils.getLogger().info("GOLEM_CLIENT_AI_TRANSFER_ASSERTIONS_OK");
                    require(data.remove(PICKUP.pos(),PICKUP.face(),player.getUUID())&&data.remove(FILL.pos(),FILL.face(),player.getUUID()),"Old logistics seals removed before combat fixture");
                    level.setBlockAndUpdate(GUARD.pos(),Blocks.STONE.defaultBlockState());
                    require(data.place(GUARD.pos(),GUARD.face(),player.getUUID(),GolemSeals.Type.GUARD_ADVANCED),"Fresh advanced guard fixture");
                    var settings=data.combatSettings(data.get(GUARD)); require(settings.mobs()&&!settings.animals()&&!settings.players(),"Actual source defaults, no seeded UI success");
                    phase=6; phaseTick=ticks;
                }
                else require(ticks-phaseTick<600,"Actual integrated client AI transfer completes within watchdog");
                return;
            }
            if(phase==6&&ticks-phaseTick>=20) {
                player.teleportTo(level,9.5,120,8.5,180,15);
                require(GolemSealMenus.open(player,level,data.get(GUARD)),"Actual advanced guard owner menu opens"); phase=7; phaseTick=ticks; LogUtils.getLogger().info("GOLEM_CLIENT_GUARD_MENU_READY"); return;
            }
            if(phase==7) {
                var settings=data.combatSettings(data.get(GUARD));
                if(!guardChecked&&!settings.mobs()&&settings.animals()&&!settings.players()) {
                    require(player.containerMenu instanceof GolemSealMenu menu&&menu.advancedGuard(),"Real advanced guard container receives both C2S toggles");
                    guardChecked=true; LogUtils.getLogger().info("GOLEM_CLIENT_GUARD_TOGGLES_ASSERTIONS_OK");
                }
                if(guardChecked&&player.containerMenu==player.inventoryMenu) {
                    if(worker!=null) worker.discard(); // Finished empty logistics fixture is not another IAnimals combat target.
                    level.setBlockAndUpdate(new BlockPos(6,121,11),Blocks.STONE.defaultBlockState()); player.teleportTo(level,6.5,122,11.5,-140,-10);
                    shooter=GolemModule.GOLEM.get().create(level); shooter.moveTo(8.5,121,5.5); shooter.initialize(GolemDesign.fromIds(3,1,4,3,0,0),player.getUUID(),new BlockPos(8,120,6),0); shooter.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.GOLD_INGOT,7)); require(level.addFreshEntity(shooter),"One real flying ranged golem, creative test input only");
                    var husk=EntityType.HUSK.create(level); husk.moveTo(11.5,124,5.5); husk.setNoAi(true); husk.setPersistenceRequired(); husk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(40); husk.setHealth(40); require(level.addFreshEntity(husk),"One actual floating target"); victim=husk;
                    phase=8; phaseTick=ticks; LogUtils.getLogger().info("GOLEM_CLIENT_FLYING_RANGED_READY");
                } return;
            }
            if(phase==8) {
                require(!shooter.isNoAi()&&shooter.isNoGravity()&&shooter.ranged()&&shooter.getMainHandItem().is(Items.GOLD_INGOT)&&shooter.getMainHandItem().getCount()==7,"Real enabled flight/ranged AI preserves cargo7 each tick");
                if(rangedKill) {
                    require(guardChecked&&shooter.rankXp()==xpAtKill,"Native ranged kill adds no fictitious melee XP8");
                    done=true; LogUtils.getLogger().info("GOLEM_CLIENT_FLYING_RANGED_ASSERTIONS_OK");
                } else require(ticks-phaseTick<1200,"Actual client ranged combat completes within watchdog");
            }
        } catch(RuntimeException|AssertionError ex) { failed=true; LogUtils.getLogger().error("GOLEM_CLIENT_ASSERTIONS_FAILED",ex); }
    }
    private static void require(boolean value,String message) { if(!value) throw new AssertionError(message); }
}
