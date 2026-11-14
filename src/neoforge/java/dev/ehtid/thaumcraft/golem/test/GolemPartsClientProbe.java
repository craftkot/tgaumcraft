package dev.ehtid.thaumcraft.golem.test;

import com.mojang.logging.LogUtils;
import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.golem.inventory.GolemContainers;
import dev.ehtid.thaumcraft.golem.seal.GolemSeals;
import dev.ehtid.thaumcraft.item.PlateItem;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Opt-in dedicated fixture. Normal recipe/menu/entity packets and real seal AI do all work. */
@EventBusSubscriber(modid=ThaumcraftPort.MOD_ID)
public final class GolemPartsClientProbe {
    private static final boolean ENABLED=Boolean.getBoolean("thaumcraft.golemPartsClientProbe");
    private static final AABB AREA=new AABB(0,118,0,20,128,18);
    private static final GolemSeals.SealKey SOURCE=new GolemSeals.SealKey(GolemPartsClientFixture.SOURCE,Direction.UP);
    private static final GolemSeals.SealKey SINK=new GolemSeals.SealKey(GolemPartsClientFixture.SINK,Direction.UP);
    private static MinecraftServer active;
    private static ThaumcraftGolem worker;
    private static ItemEntity paid;
    private static int ticks,phase,at,recipe,finished;
    private static boolean failed,sawCargo;
    private GolemPartsClientProbe() {}
    @SubscribeEvent public static void started(ServerStartedEvent e){
        if(!ENABLED||!e.getServer().isDedicatedServer())return;
        active=e.getServer();ticks=phase=at=recipe=finished=0;failed=sawCargo=false;worker=null;paid=null;
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e){if(active==e.getServer())active=null;}
    @SubscribeEvent public static void tick(ServerTickEvent.Post e){
        if(active!=e.getServer())return;
        ticks++;
        if(failed){if(ticks-finished>=40)active.halt(false);return;}
        try{
            require(ticks<6000,"Hard bounded real TCP fixture");
            var level=active.overworld();
            if(phase==0){
                if(active.getPlayerList().getPlayerCount()==0)return;
                require(active.getPlayerList().getPlayerCount()==1,"Exactly one actual TCP player");
                var player=player();var data=GolemSeals.get(level);
                require(level.getEntity(GolemPartsClientFixture.worker())==null&&!data.contains(SOURCE.pos(),SOURCE.face())&&!data.contains(SINK.pos(),SINK.face()),"Fresh fixture world, never overwrite an old proof");
                level.setChunkForced(0,0,true);level.setChunkForced(1,0,true);level.setChunkForced(0,1,true);level.setChunkForced(1,1,true);
                level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,active);
                level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,active);
                level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false,active);
                level.setDayTime(6000);level.setWeatherParameters(6000,0,false,false);
                for(int x=0;x<20;x++)for(int z=0;z<18;z++){
                    level.setBlockAndUpdate(new BlockPos(x,119,z),Blocks.STONE.defaultBlockState());
                    for(int y=120;y<=127;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());
                }
                String[] names={"Wood / basic","Iron / fine / wheels","Clay / claws / climber","Brass / breakers / flyer","Thaumium / darts","Void / basic / wheels"};
                for(int i=0;i<6;i++){
                    var golem=GolemModule.GOLEM.get().create(level);require(golem!=null,"Registered gallery entity");
                    golem.setUUID(GolemPartsClientFixture.gallery(i));golem.moveTo(2+3*i,120,4.5,0,0);
                    golem.initialize(GolemPartsClientFixture.design(i),player.getUUID(),new BlockPos(2+3*i,120,4),0);
                    golem.setNoAi(true);golem.setCustomName(Component.literal(names[i]));golem.setCustomNameVisible(true);
                    golem.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(i%2==0?Items.IRON_INGOT:Items.GOLD_INGOT,i+1));
                    require(level.addFreshEntity(golem),"One original material/part gallery entity");
                }
                level.setBlockAndUpdate(GolemPartsClientFixture.TABLE,Blocks.CRAFTING_TABLE.defaultBlockState());
                level.setBlockAndUpdate(SINK.pos(),Blocks.BARREL.defaultBlockState());
                for(var key:new GolemSeals.SealKey[]{SOURCE,SINK}){
                    require(data.place(key.pos(),key.face(),player.getUUID(),key==SOURCE?GolemSeals.Type.PICKUP:GolemSeals.Type.FILL),"Actual owner logistics seal");
                    require(data.configureControls(data.get(key),player.getUUID(),0,0,true,false),"Actual locked fixture task");
                }
                worker=GolemModule.GOLEM.get().create(level);require(worker!=null,"Actual wheel worker");
                worker.setUUID(GolemPartsClientFixture.worker());worker.moveTo(6.5,120,9.5);
                worker.initialize(GolemPartsClientFixture.workerDesign(),player.getUUID(),new BlockPos(6,120,9),0);worker.setNoAi(true);
                worker.setCustomName(Component.literal("Paid iron cargo: 11"));worker.setCustomNameVisible(true);
                require(level.addFreshEntity(worker),"One actual wheeled hauler");
                paid=new ItemEntity(level,3.5,120,9.5,new ItemStack(Items.IRON_INGOT,11));paid.setNoPickUpDelay();paid.setDeltaMovement(Vec3.ZERO);
                require(level.addFreshEntity(paid),"Exactly eleven funded fixture ingots");
                player.setGameMode(GameType.SURVIVAL);player.getInventory().clearContent();player.teleportTo(level,9.5,121,16.5,180,12);
                marker(Blocks.RED_WOOL);phase=1;at=ticks;
                LogUtils.getLogger().info("GOLEM_PARTS_SERVER_GALLERY_READY materials=6 entities=7 fundedCargo=11");return;
            }
            var player=player();require(player!=null,"Same live TCP player throughout proof");
            require(level.getEntity(worker.getUUID())==worker&&worker.isAlive()&&worker.ownerId().equals(player.getUUID()),"Same original live worker/owner");
            require(level.getEntitiesOfClass(ThaumcraftGolem.class,AREA).size()==7,"Exactly six original gallery golems plus one worker");
            int stock=stock();int held=count(worker);int ground=paid.isRemoved()?0:paid.getItem().getCount();
            require(stock+held+ground==11,"Every actual pickup/deposit tick conserves eleven funded ingots");
            require(worker.getOffhandItem().isEmpty()&&!worker.hasTransferRecovery(),"No hidden recovery or offhand cargo");
            if(phase==1&&ticks-at>=180){openRecipe(player);phase=2;at=ticks;return;}
            if(phase==2){
                require(ticks-at<800,"Bounded actual client recipe menu clicks");
                if(player.containerMenu instanceof CraftingMenu menu){
                    int result=plates(player,GolemPartsClientFixture.PLATES[recipe]);
                    require(result==0||result==3,"No fabricated plate reward");
                    if(result==3){
                        require(menu.getCarried().isEmpty(),"Actual paid output put into ordinary inventory");
                        for(int slot=0;slot<=9;slot++)require(menu.getSlot(slot).getItem().isEmpty(),"Three actual source ingots consumed once");
                    }
                    return;
                }
                require(plates(player,GolemPartsClientFixture.PLATES[recipe])==3,"Actual C2S output take paid three ingots for three plates");
                LogUtils.getLogger().info("GOLEM_PARTS_SERVER_PLATE_CLICK_ASSERTIONS_OK recipe={} inputPaid=3 output=3",GolemPartsClientFixture.RECIPES[recipe]);
                if(++recipe<3){openRecipe(player);at=ticks;return;}
                worker.setNoAi(false);marker(Blocks.LAPIS_BLOCK);phase=3;at=ticks;
                LogUtils.getLogger().info("GOLEM_PARTS_SERVER_AI_ENABLED originalWorker={} cargo=11",worker.getUUID());return;
            }
            if(phase==3){
                if(held==11&&worker.position().distanceToSqr(new Vec3(6.5,120,9.5))>1)sawCargo=true;
                require(ticks-at<1200,"Bounded autonomous real wheel pickup/deposit");
                if(stock!=11||held!=0||!paid.isRemoved())return;
                require(sawCargo&&!worker.isNoAi()&&worker.position().distanceToSqr(new Vec3(6.5,120,9.5))>4,"Actual enabled wheel navigation moved funded cargo");
                marker(Blocks.EMERALD_BLOCK);phase=4;finished=ticks;
                player.teleportTo(level,12.5,120,12.5,160,8);
                LogUtils.getLogger().info("GOLEM_PARTS_SERVER_ASSERTIONS_OK materials=6 recipes=3 sourceIngotsPaid=9 plates=9 depositedIron=11 sameWorker=true");return;
            }
            if(phase==4){require(stock==11,"Final deposited cargo remains stable");if(ticks-finished>=300)active.halt(false);}
        }catch(RuntimeException|AssertionError ex){failed=true;finished=ticks;LogUtils.getLogger().error("GOLEM_PARTS_SERVER_ASSERTIONS_FAILED phase="+phase,ex);}
    }
    private static void openRecipe(ServerPlayer player){
        player.openMenu(new SimpleMenuProvider((id,inventory,p)->new CraftingMenu(id,inventory,ContainerLevelAccess.create(active.overworld(),GolemPartsClientFixture.TABLE)),Component.literal("Paid plate network proof")));
        require(player.containerMenu instanceof CraftingMenu,"Actual vanilla crafting menu opens");
        var menu=(CraftingMenu)player.containerMenu;
        for(int slot=1;slot<=3;slot++)menu.getSlot(slot).set(GolemPartsClientFixture.ingot(GolemPartsClientFixture.METALS[recipe]));
        menu.slotsChanged(menu.getSlot(1).container);menu.broadcastChanges();
        require(PlateItem.legacyMetadata(menu.getSlot(0).getItem())==GolemPartsClientFixture.PLATES[recipe]&&menu.getSlot(0).getItem().getCount()==3,"Real registered plate recipe preview");
        LogUtils.getLogger().info("GOLEM_PARTS_SERVER_PLATE_MENU_READY recipe={} container={}",GolemPartsClientFixture.RECIPES[recipe],menu.containerId);
    }
    private static int plates(ServerPlayer p,int meta){int n=0;for(var s:p.getInventory().items)if(PlateItem.legacyMetadata(s)==meta)n+=s.getCount();return n;}
    private static int count(ThaumcraftGolem g){return g.getMainHandItem().isEmpty()?0:g.getMainHandItem().getCount();}
    private static int stock(){var h=GolemContainers.handler(active.overworld(),SINK);require(h!=null,"Actual barrel capability");int n=0;for(int i=0;i<h.getSlots();i++){var s=h.getStackInSlot(i);require(s.isEmpty()||s.is(Items.IRON_INGOT),"Only funded iron in barrel");n+=s.getCount();}return n;}
    private static ServerPlayer player(){return active.getPlayerList().getPlayers().isEmpty()?null:active.getPlayerList().getPlayers().getFirst();}
    private static void marker(net.minecraft.world.level.block.Block block){active.overworld().setBlockAndUpdate(GolemPartsClientFixture.MARKER,block.defaultBlockState());}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
