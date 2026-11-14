package dev.ehtid.thaumcraft.golem.test;

import com.mojang.logging.LogUtils;
import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.golem.menu.GolemSealMenu;
import dev.ehtid.thaumcraft.golem.seal.GolemSeals;
import dev.ehtid.thaumcraft.golem.seal.SealFilter;
import dev.ehtid.thaumcraft.port.core.golem.GolemDesign;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.UUID;

/** Fresh opt-in world, two real network players, real vanilla interaction and menu packets. */
@EventBusSubscriber(modid=ThaumcraftPort.MOD_ID)
public final class GolemOwnerClientProbe {
    private static final boolean ENABLED=Boolean.getBoolean("thaumcraft.golemOwnerClientProbe");
    private static final AABB AREA=new AABB(0,118,0,15,127,15);
    private static final ThaumcraftGolem[] workers=new ThaumcraftGolem[2];
    private static final UUID[] owners=new UUID[2],instances=new UUID[2];
    private static final int[] foreignDye=new int[2],ownDye=new int[2],foreignSeal=new int[2];
    private static MinecraftServer active;
    private static int phase,ticks,phaseTick,lastAttempt,settled,finished;
    private static boolean failed;
    private GolemOwnerClientProbe() {}

    @SubscribeEvent public static void started(ServerStartedEvent event){
        if(!ENABLED||!event.getServer().isDedicatedServer())return;
        active=event.getServer();phase=ticks=phaseTick=lastAttempt=settled=finished=0;failed=false;
        for(int i=0;i<2;i++){workers[i]=null;owners[i]=instances[i]=null;foreignDye[i]=ownDye[i]=foreignSeal[i]=0;}
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){if(active==event.getServer())active=null;}

    @SubscribeEvent public static void entityPacket(PlayerInteractEvent.EntityInteract event){
        if(active==null||event.getLevel().isClientSide()||event.getHand()!=InteractionHand.MAIN_HAND)return;
        int role=role(event.getEntity().getUUID());if(role<0)return;
        if(phase==1&&event.getTarget().getUUID().equals(GolemOwnerClientFixture.worker(1-role))){foreignDye[role]++;lastAttempt=ticks;}
        if(phase==2&&event.getTarget().getUUID().equals(GolemOwnerClientFixture.worker(role))){ownDye[role]++;lastAttempt=ticks;}
    }
    @SubscribeEvent public static void blockPacket(PlayerInteractEvent.RightClickBlock event){
        if(active==null||phase!=3||event.getLevel().isClientSide()||event.getHand()!=InteractionHand.MAIN_HAND)return;
        int role=role(event.getEntity().getUUID());if(role<0)return;
        var key=GolemOwnerClientFixture.key(1-role);
        if(event.getPos().equals(key.pos())&&event.getFace()==key.face()){foreignSeal[role]++;lastAttempt=ticks;}
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        if(active!=event.getServer())return;
        ticks++;
        if(failed){if(ticks-finished>=100)active.halt(false);return;}
        try{
            require(phase!=0||ticks<6000,"Bounded wait for two actual named TCP players");
            var level=active.overworld();var data=GolemSeals.get(level);
            if(phase==0){
                if(player(0)==null||player(1)==null)return;
                initialize(level,data);return;
            }
            require(ticks-phaseTick<2000||phase==7,"Each real network phase has a bounded deadline; phase="+phase);
            for(int i=0;i<2;i++){
                var player=player(i);var worker=workers[i];var seal=data.get(GolemOwnerClientFixture.key(i));
                require(player!=null&&player.getUUID().equals(owners[i]),"Same two original live network players");
                require(level.getEntity(GolemOwnerClientFixture.worker(i))==worker&&worker.isAlive()&&owners[i].equals(worker.ownerId()),"Original worker UUID and owner preserved");
                require(seal!=null&&owners[i].equals(seal.owner())&&instances[i].equals(seal.instanceId())&&seal.locked(),"Original owner-locked seal and instance preserved");
            }
            require(level.getEntitiesOfClass(ThaumcraftGolem.class,AREA).size()==2,"Exactly two original workers");
            if(phase==1){
                for(int i=0;i<2;i++)require(workers[i].golemColor()==0&&player(i).getMainHandItem().is(Items.RED_DYE)&&player(i).getMainHandItem().getCount()==6,"Foreign real interaction cannot dye another owner's worker or consume dye");
                if(foreignDye[0]==0||foreignDye[1]==0||ticks-lastAttempt<10)return;
                LogUtils.getLogger().info("GOLEM_OWNER_SERVER_FOREIGN_DYE_ASSERTIONS_OK attempts={},{} colors=0,0 dyes=6,6",foreignDye[0],foreignDye[1]);
                advance(level,2,Blocks.GOLD_BLOCK);return;
            }
            if(phase==2){
                if(ownDye[0]==0||ownDye[1]==0||ticks-lastAttempt<10)return;
                for(int i=0;i<2;i++)require(workers[i].golemColor()==GolemOwnerClientFixture.dyeColor()&&player(i).getMainHandItem().is(Items.RED_DYE)&&player(i).getMainHandItem().getCount()==5,"Own real interaction applies source color and consumes precisely one dye");
                LogUtils.getLogger().info("GOLEM_OWNER_SERVER_OWN_DYE_ASSERTIONS_OK attempts={},{} color={} dyes=5,5",ownDye[0],ownDye[1],GolemOwnerClientFixture.dyeColor());
                for(int i=0;i<2;i++)player(i).setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(GolemModule.GOLEM_BELL.get()));
                advance(level,3,Blocks.IRON_BLOCK);return;
            }
            if(phase==3){
                for(int i=0;i<2;i++)require(player(i).containerMenu==player(i).inventoryMenu&&data.get(GolemOwnerClientFixture.key(i)).priority()==0,"Foreign real bell click cannot open a menu or mutate either seal");
                if(foreignSeal[0]==0||foreignSeal[1]==0||ticks-lastAttempt<10)return;
                LogUtils.getLogger().info("GOLEM_OWNER_SERVER_FOREIGN_SEAL_ASSERTIONS_OK attempts={},{} menus=none priorities=0,0",foreignSeal[0],foreignSeal[1]);
                advance(level,4,Blocks.DIAMOND_BLOCK);return;
            }
            if(phase==4){
                if(data.get(GolemOwnerClientFixture.key(0)).priority()!=1||data.get(GolemOwnerClientFixture.key(1)).priority()!=1)return;
                for(int i=0;i<2;i++)require(player(i).containerMenu instanceof GolemSealMenu&&player(i).containerMenu.stillValid(player(i)),"Both actual players have independent valid owner menus simultaneously");
                require(player(0).containerMenu!=player(1).containerMenu,"Per-player menu objects are distinct even if numeric container IDs coincide");
                LogUtils.getLogger().info("GOLEM_OWNER_SERVER_OWNER_MENUS_ASSERTIONS_OK priorities=1,1 menuIds={},{}",player(0).containerMenu.containerId,player(1).containerMenu.containerId);
                advance(level,5,Blocks.EMERALD_BLOCK);return;
            }
            if(phase==5){
                if(player(0).containerMenu!=player(0).inventoryMenu||player(1).containerMenu!=player(1).inventoryMenu)return;
                for(int i=0;i<2;i++){
                    var p=Vec3.atBottomCenterOf(GolemOwnerClientFixture.key(1-i).pos().above().north());
                    var item=new ItemEntity(level,p.x,p.y+.1,p.z,GolemOwnerClientFixture.cargo(i));
                    item.setNoPickUpDelay();item.setDeltaMovement(Vec3.ZERO);require(level.addFreshEntity(item),"Actual funded ground stack");
                    workers[i].setNoAi(false);
                }
                advance(level,6,Blocks.LAPIS_BLOCK);
                LogUtils.getLogger().info("GOLEM_OWNER_SERVER_AI_ENABLED iron=13 gold=17 wrongOwnerStartsCloser=true");return;
            }
            for(int i=0;i<2;i++){
                var wanted=GolemOwnerClientFixture.cargo(i);
                int ground=ground(level,wanted.getItem());
                int held=count(workers[0],wanted.getItem())+count(workers[1],wanted.getItem());
                require(ground+held==wanted.getCount(),"Each-tick actual ground plus worker conservation for role="+i);
                require(count(workers[1-i],wanted.getItem())==0,"A differently owned worker cannot collect the locked seal's task");
                for(var player:active.getPlayerList().getPlayers())require(player.getInventory().countItem(wanted.getItem())==0,"Actual cargo was not picked up by fixture players");
            }
            if(phase==6){
                if(!ItemStack.matches(workers[0].getMainHandItem(),GolemOwnerClientFixture.cargo(0))||!ItemStack.matches(workers[1].getMainHandItem(),GolemOwnerClientFixture.cargo(1)))return;
                require(workers[0].getOffhandItem().isEmpty()&&workers[1].getOffhandItem().isEmpty()&&!workers[0].hasTransferRecovery()&&!workers[1].hasTransferRecovery(),"No hidden duplicated recovery cargo");
                if(++settled<100)return;
                advance(level,7,Blocks.NETHERITE_BLOCK);finished=ticks;
                LogUtils.getLogger().info("GOLEM_OWNER_SERVER_ASSERTIONS_OK players=2 workers=2 lockedSeals=2 priorities=1,1 iron=13 gold=17 noReplayTicks={}",settled);return;
            }
            require(ItemStack.matches(workers[0].getMainHandItem(),GolemOwnerClientFixture.cargo(0))&&ItemStack.matches(workers[1].getMainHandItem(),GolemOwnerClientFixture.cargo(1)),"Final actual owner cargo stays stable");
            if(ticks-finished>=200)active.halt(false);
        }catch(RuntimeException|AssertionError ex){failed=true;finished=ticks;LogUtils.getLogger().error("GOLEM_OWNER_SERVER_ASSERTIONS_FAILED phase="+phase,ex);}
    }

    private static void initialize(ServerLevel level,GolemSeals data){
        require(active.getPlayerList().getPlayerCount()==2,"Exactly two real TCP players");
        for(int i=0;i<2;i++)require(level.getEntity(GolemOwnerClientFixture.worker(i))==null&&!data.contains(GolemOwnerClientFixture.key(i).pos(),GolemOwnerClientFixture.key(i).face()),"Fresh isolated owner fixture; previous worlds must not be overwritten");
        level.setChunkForced(0,0,true);level.getChunk(0,0);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,active);level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,active);
        level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false,active);level.setDayTime(6000);level.setWeatherParameters(6000,0,false,false);
        for(int x=0;x<15;x++)for(int z=0;z<15;z++){level.setBlockAndUpdate(new BlockPos(x,119,z),Blocks.STONE.defaultBlockState());for(int y=120;y<=126;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());}
        for(int i=0;i<2;i++){
            var player=player(i);owners[i]=player.getUUID();var key=GolemOwnerClientFixture.key(i);
            require(data.place(key.pos(),key.face(),owners[i],GolemSeals.Type.PICKUP),"One actual seal per owner");
            require(data.configureControls(data.get(key),owners[i],0,0,true,false),"Locked owner identity and source color-zero wildcard");
            require(data.configureArea(data.get(key),owners[i],new BlockPos(4,1,4)),"Overlapping real pickup work areas");
            require(data.configure(data.get(key),owners[i],new SealFilter(GolemOwnerClientFixture.cargo(i,1),false,0)),"Each actual source seal has an unambiguous named-item filter");
            instances[i]=data.get(key).instanceId();
            var worker=GolemModule.GOLEM.get().create(level);require(worker!=null,"Registered real worker factory");workers[i]=worker;
            worker.setUUID(GolemOwnerClientFixture.worker(i));worker.initialize(GolemDesign.defaultWood(),owners[i],key.pos().above(),0);worker.moveTo(Vec3.atBottomCenterOf(key.pos().above()));worker.setNoAi(true);
            require(level.addFreshEntity(worker),"One actual worker per owner");
            player.setGameMode(GameType.SURVIVAL);player.teleportTo(level,i==0?7.15:7.85,120,8.5,180,30);player.getInventory().clearContent();player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.RED_DYE,6));
        }
        advance(level,1,Blocks.RED_WOOL);
        LogUtils.getLogger().info("GOLEM_OWNER_SERVER_INITIAL_READY players=2 ownerA={} ownerB={} workerA={} workerB={}",owners[0],owners[1],workers[0].getUUID(),workers[1].getUUID());
    }
    private static ServerPlayer player(int role){return active.getPlayerList().getPlayerByName(GolemOwnerClientFixture.playerName(role));}
    private static int role(UUID id){for(int i=0;i<2;i++)if(id.equals(owners[i]))return i;return -1;}
    private static void advance(ServerLevel level,int next,Block marker){phase=next;phaseTick=ticks;level.setBlockAndUpdate(GolemOwnerClientFixture.MARKER,marker.defaultBlockState());}
    private static int count(ThaumcraftGolem worker,Item item){int n=0;for(var stack:new ItemStack[]{worker.getMainHandItem(),worker.getOffhandItem()})if(stack.is(item))n+=stack.getCount();for(var stack:worker.transferRecoveryItems())if(stack.is(item))n+=stack.getCount();return n;}
    private static int ground(ServerLevel level,Item item){return level.getEntitiesOfClass(ItemEntity.class,AREA,e->e.getItem().is(item)).stream().mapToInt(e->e.getItem().getCount()).sum();}
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
