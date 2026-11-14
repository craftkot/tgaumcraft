package dev.ehtid.thaumcraft.golem.test;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.blockentity.EssentiaJarBlockEntity;
import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.golem.press.*;
import dev.ehtid.thaumcraft.item.GolemPlacerItem;
import dev.ehtid.thaumcraft.player.ThaumcraftPlayerAttachments;
import dev.ehtid.thaumcraft.port.core.golem.*;
import dev.ehtid.thaumcraft.port.core.golem.GolemPressBlueprint.Part;
import dev.ehtid.thaumcraft.registry.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.*;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.*;
import static dev.ehtid.thaumcraft.golem.test.GolemFormationGameTests.pos;

/** Actual formed presses, paid buffer/blocked output, native chunk unload and disk-created block entities. */
@EventBusSubscriber(modid=ThaumcraftPort.MOD_ID)
public final class GolemPressChunkUnloadProbe {
    private static final boolean ENABLED="press_chunk".equals(System.getProperty("thaumcraft.golemRestartScenario",""));
    private static final int C=544,BASE=C*16;
    private static final BlockPos[] ORIGINS={new BlockPos(BASE+4,120,BASE+6),new BlockPos(BASE+10,120,BASE+6)};
    private static final BlockPos[] CONTROLLERS=Arrays.stream(ORIGINS).map(o->pos(o,1,Part.CONTROLLER)).toArray(BlockPos[]::new);
    private static final GolemDesign DESIGN=GolemDesign.defaultWood();
    private static MinecraftServer active; private static FakePlayer owner;
    private static final GolemPressBlockEntity[] old=new GolemPressBlockEntity[2];
    private static final GolemPress.State[] saved=new GolemPress.State[2];
    private static int ticks,phase,phaseTick,completedTick;private static boolean checked,unloaded;
    private GolemPressChunkUnloadProbe() {}
    @SubscribeEvent public static void started(ServerStartedEvent e) {
        if(!ENABLED||!e.getServer().isDedicatedServer()) return;
        active=e.getServer();ticks=phase=phaseTick=completedTick=0;checked=unloaded=false;
        active.overworld().setChunkForced(C,C,true);active.overworld().getChunk(C,C);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e) {if(active==e.getServer()){active=null;owner=null;}}
    @SubscribeEvent public static void unloaded(ChunkEvent.Unload e) {
        if(active!=null&&phase==2&&e.getLevel()==active.overworld()&&e.getChunk().getPos().equals(new ChunkPos(C,C))) {
            // Immutable values from existing references; never request a chunk during an unload event.
            for(int i=0;i<2;i++) saved[i]=old[i].job();unloaded=true;
        }
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post e) {
        if(active!=e.getServer()||checked||++ticks<60) return;
        var level=active.overworld();
        try {
            require(ticks<2200,"Bounded real Press chunk lifecycle");
            if(phase==0) {
                if(!level.isPositionEntityTicking(CONTROLLERS[0])) return;
                require(GolemPressStructures.get(level).size()==0&&!level.getBlockState(CONTROLLERS[0]).is(GolemModule.PRESS.get()),"Fresh isolated world");
                level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,active);
                owner=FakePlayerFactory.get(level,new GameProfile(UUID.fromString("9e5de247-9f91-4316-aef4-b933c64fe1be"),"PressChunk"));owner.setGameMode(GameType.SURVIVAL);owner.getInventory().clearContent();
                var knowledge=owner.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge();knowledge.addResearch("MINDCLOCKWORK");knowledge.addResearch("MATSTUDWOOD");
                for(int i=0;i<2;i++) {
                    GolemFormationGameTests.placeSource(level,ORIGINS[i],1);owner.moveTo(Vec3.atBottomCenterOf(CONTROLLERS[i].east()));owner.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ThaumcraftItems.SALIS_MUNDUS.get()));
                    require(GolemFormationGameTests.use(owner,CONTROLLERS[i],Direction.UP).consumesAction()&&owner.getMainHandItem().isEmpty(),"Actual one dust debit per structure");
                }
                phase=1;phaseTick=ticks;return;
            }
            if(phase==1) {
                if(ticks-phaseTick<56)return;
                require(GolemPressStructures.get(level).size()==2,"Two actual fifty-tick formations");
                for(int i=0;i<2;i++) {
                    var p=CONTROLLERS[i];links(level,i);level.setBlockAndUpdate(p.below(),ThaumcraftBlocks.JAR_NORMAL.get().defaultBlockState());level.setBlockAndUpdate(p.north(),Blocks.BARREL.defaultBlockState());
                    ingredients((Container)level.getBlockEntity(p.north()));credit(jar(level,i),i==0?4:10);owner.moveTo(Vec3.atBottomCenterOf(p.east()));old[i]=press(level,i);
                    require(old[i].start(owner)&&((Container)level.getBlockEntity(p.north())).isEmpty(),"One actual component batch paid");ingredients((Container)level.getBlockEntity(p.north()));
                    old[i].setItem(0,blocker());
                    for(int t=0;t<(i==0?15:40);t++)GolemPressBlockEntity.tick(level,p,old[i].getBlockState(),old[i]);
                    if(i==0)require(jar(level,i).takeTransport("machina",1,Direction.UP)==1&&old[i].addTransport("machina",1,Direction.DOWN)==1,"Fourth physical unit buffered before unload");
                }
                require(old[0].job().remaining()==5&&old[0].job().buffered()&&jar(level,0).snapshot().amount()==0,"Real partial job with paid buffer");
                require(old[1].job().ready()&&jar(level,1).snapshot().amount()==2,"Real fully paid blocked job");
                require(level.setChunkForced(C,C,false),"Release fixture forced ticket");phase=2;phaseTick=ticks;LogUtils.getLogger().info("GOLEM_PRESS_CHUNK_TICKETS_RELEASED");return;
            }
            if(phase==2) {
                if(!unloaded||level.getChunkSource().getChunkNow(C,C)!=null||level.areEntitiesLoaded(ChunkPos.asLong(C,C))) {require(ticks-phaseTick<900,"Actual native Press chunk unload: event="+unloaded+", blockLoaded="+(level.getChunkSource().getChunkNow(C,C)!=null));return;}
                require(saved[0]!=null&&saved[0].remaining()-(saved[0].buffered()?1:0)==4&&saved[1].ready(),"Saved remaining minus actual paid buffer equals four missing units; unloading may precede the next five-tick debit");
                phase=3;phaseTick=ticks;LogUtils.getLogger().info("GOLEM_PRESS_CHUNK_NATIVE_UNLOAD_OBSERVED");return;
            }
            if(phase==3) {
                require(level.getChunkSource().getChunkNow(C,C)==null&&!level.areEntitiesLoaded(ChunkPos.asLong(C,C)),"Structure scanner does not load absent chunks for40ticks");
                for(int i=0;i<2;i++)require(old[i].job().equals(saved[i]),"Unloaded block entities cannot continue processing");
                if(ticks-phaseTick<40)return;level.setChunkForced(C,C,true);level.getChunk(C,C);phase=4;phaseTick=ticks;return;
            }
            if(!level.isPositionEntityTicking(CONTROLLERS[0])) {require(ticks-phaseTick<300,"Reload completes");return;}
            if(phase==4) {
                for(int i=0;i<2;i++) {
                    var p=press(level,i);require(p!=old[i]&&p.job().equals(saved[i])&&p.selection().equals(DESIGN)&&ItemStack.matches(p.getItem(0),blocker()),"Different native block entity restored same paid job/selection/output components");
                    require(jar(level,i).snapshot().amount()==(i==0?0:2),"Actual jar disk debit state restored");links(level,i);spare(level,i);
                }
                credit(jar(level,0),6); // Exactly four missing units plus two spare; only fixture supplies stock.
                require(press(level,0).removeItem(0,2).getCount()==2&&press(level,1).removeItem(0,2).getCount()==2,"Unblock two actual outputs");
                phase=5;phaseTick=ticks;return;
            }
            if(phase==5) {
                for(int i=0;i<2;i++)spare(level,i);
                if(press(level,0).job().active()||press(level,1).job().active()) {require(ticks-phaseTick<100,"Native ticking finishes both paid products");return;}
                for(int i=0;i<2;i++)require(ItemStack.matches(press(level,i).getItem(0),GolemPlacerItem.stack(DESIGN,0))&&jar(level,i).snapshot().amount()==2,"One product and exactly two unspent Machina per restored machine");
                require(drops(level).isEmpty(),"No duplicated ground outputs");if(completedTick==0)completedTick=ticks;if(ticks-completedTick<100)return;
                require(level.destroyBlock(pos(ORIGINS[0],1,Part.ANVIL),true),"Actual placeholder break after reload");
                require(level.getBlockState(CONTROLLERS[0]).is(Blocks.PISTON)&&GolemPressStructures.get(level).size()==1,"Persisted structure links restore exactly one machine");
                var drops=drops(level);require(drops.stream().filter(d->d.getItem().is(GolemModule.GOLEM_PLACER.get())).mapToInt(d->d.getItem().getCount()).sum()==1&&drops.stream().filter(d->d.getItem().is(Items.ANVIL)).mapToInt(d->d.getItem().getCount()).sum()==1&&drops.size()==2,"One paid product plus broken anvil, no duplicate refunds");
                require(press(level,1).getItem(0).getCount()==1,"Other formed machine retains its single product");
                checked=true;LogUtils.getLogger().info("GOLEM_PRESS_CHUNK_UNLOAD_ASSERTIONS_OK");
            }
        }catch(RuntimeException|AssertionError ex){checked=true;LogUtils.getLogger().error("GOLEM_PRESS_CHUNK_UNLOAD_ASSERTIONS_FAILED",ex);}
        if(checked)active.halt(false);
    }
    private static void links(ServerLevel l,int i){for(Part part:Part.values())require(CONTROLLERS[i].equals(GolemPressStructures.get(l).controllerFor(pos(ORIGINS[i],1,part))),"All five actual formed links retained");}
    private static List<ItemEntity> drops(ServerLevel l){return l.getEntitiesOfClass(ItemEntity.class,new AABB(ORIGINS[0]).inflate(12));}
    private static GolemPressBlockEntity press(ServerLevel l,int i){return (GolemPressBlockEntity)l.getBlockEntity(CONTROLLERS[i]);}
    private static EssentiaJarBlockEntity jar(ServerLevel l,int i){return (EssentiaJarBlockEntity)l.getBlockEntity(CONTROLLERS[i].below());}
    private static void ingredients(Container c){int s=0;for(var component:GolemAssembly.components(DESIGN))c.setItem(s++,GolemComponents.stack(component));}
    private static void spare(ServerLevel l,int i){var c=(Container)l.getBlockEntity(CONTROLLERS[i].north());int s=0;for(var component:GolemAssembly.components(DESIGN))require(ItemStack.matches(c.getItem(s++),GolemComponents.stack(component)),"Second physical component batch never paid on resume");}
    private static void credit(EssentiaJarBlockEntity jar,int n){for(int i=0;i<n;i++)require(jar.addFromTransport("machina",1,Direction.UP)==1,"Finite physical fixture supply");}
    private static ItemStack blocker(){var s=new ItemStack(Items.GOLD_INGOT,2);s.set(DataComponents.CUSTOM_NAME,Component.literal("Press live chunk blocker"));return s;}
    private static void require(boolean v,String m){if(!v)throw new AssertionError(m);}
}
