package dev.ehtid.thaumcraft.golem.test;

import com.mojang.logging.LogUtils;
import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.golem.api.GolemHarvestRegistry;
import dev.ehtid.thaumcraft.golem.seal.*;
import dev.ehtid.thaumcraft.port.core.golem.GolemDesign;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.*;

/** Persist a real registered-crop replant obligation and exact paid seeds, then resume native placement. */
@EventBusSubscriber(modid=ThaumcraftPort.MOD_ID)
public final class GolemSeedRestartProbe {
    private static final String SCENARIO=System.getProperty("thaumcraft.golemRestartScenario","");
    private static final boolean CROSS="seeded_cross".equals(SCENARIO),EXTERNAL="seeded_external".equals(SCENARIO),CALLBACK="seeded_callback".equals(SCENARIO),DIRECTIONAL="seeded_directional".equals(SCENARIO),ENABLED=CROSS||EXTERNAL||CALLBACK||DIRECTIONAL||"seeded".equals(SCENARIO);
    private static final String PHASE=System.getProperty("thaumcraft.golemRestartPhase","");
    private static final UUID OWNER=UUID.fromString("7eae222e-670d-4c43-952a-43a040b2e895"),WORKER=UUID.fromString("6af44574-5cb2-4f7c-b706-a788bd884394");
    private static final BlockPos CROP=new BlockPos(6,120,6);
    private static final GolemSeals.SealKey KEY=new GolemSeals.SealKey(CROP.below(),Direction.UP);
    private static MinecraftServer active;private static GolemHarvestRegistry.Registration crops;private static GolemHarvestRegistry.SeedRegistration seeds;private static int ticks,stage,settled;private static boolean checked;
    private GolemSeedRestartProbe() {}
    private static ItemStack seed(int n){var s=new ItemStack(seedItem(),n);s.set(DataComponents.CUSTOM_NAME,Component.literal(DIRECTIONAL?"M54 cold directional seed":"M45 cold paid seed"));return s;}
    @SubscribeEvent public static void started(ServerStartedEvent e){
        if(!ENABLED||!Set.of("write","read").contains(PHASE))return;
        active=e.getServer();ticks=stage=settled=0;checked=false;
        crops=GolemHarvestRegistry.registerStandard(ripe());seeds=DIRECTIONAL?GolemHarvestRegistry.registerDirectionalSeed(ripe().getBlock(),seed(1),plantedBlock(),HorizontalDirectionalBlock.FACING):CALLBACK?GolemHarvestRegistry.registerSeed(ripe().getBlock(),seed(1),plantedBlock()):GolemHarvestRegistry.registerSeed(ripe().getBlock(),seed(1));
        active.overworld().setChunkForced(0,0,true);active.overworld().getChunk(0,0);active.overworld().getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0,active);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e){if(active==e.getServer()){active=null;crops.close();seeds.close();}}
    @SubscribeEvent public static void tick(ServerTickEvent.Post e){
        if(active!=e.getServer()||checked||++ticks<60)return;var level=active.overworld();
        try{
            require(ticks<1000,"Bounded registered seed restart");if(!level.isPositionEntityTicking(CROP)||!level.areEntitiesLoaded(ChunkPos.asLong(0,0)))return;
            var data=GolemSeals.get(level);
            if(PHASE.equals("write")&&stage==0){
                require(level.getEntity(WORKER)==null&&!data.contains(KEY.pos(),KEY.face()),"Fresh fixture");
                for(int x=0;x<15;x++)for(int z=0;z<15;z++){level.setBlockAndUpdate(new BlockPos(x,119,z),Blocks.STONE.defaultBlockState());for(int y=120;y<=124;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());}
                level.setBlockAndUpdate(KEY.pos(),(CROSS||EXTERNAL||CALLBACK)?Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE,7):Blocks.DIRT.defaultBlockState());
                if(DIRECTIONAL)level.setBlockAndUpdate(CROP.east(),Blocks.JUNGLE_LOG.defaultBlockState());
                level.setBlockAndUpdate(CROP,ripe());
                require(data.place(KEY.pos(),KEY.face(),OWNER,GolemSeals.Type.HARVEST),"Actual Harvest seal");data.configureArea(data.get(KEY),OWNER,new BlockPos(1,1,1));data.configureHarvest(data.get(KEY),OWNER,new HarvestSettings(true,false));
                var g=GolemModule.GOLEM.get().create(level);g.setUUID(WORKER);g.moveTo(Vec3.atBottomCenterOf(DIRECTIONAL?CROP.west():CROP.east()));g.initialize(GolemDesign.fromIds(0,1,1,0,0,0),OWNER,CROP,0);g.setNoAi(true);g.setItemSlot(EquipmentSlot.MAINHAND,seed(2));require(level.addFreshEntity(g),"Actual farmer with two named seeds");stage=1;return;
            }
            var g=level.getEntity(WORKER) instanceof ThaumcraftGolem worker?worker:null;require(g!=null&&OWNER.equals(g.ownerId()),"Same original worker UUID/owner");
            if(PHASE.equals("write")){
                if(ticks<85)return;var task=data.claimBlock(g,p->true).orElseThrow();require(data.completeBlock(level,g,task)&&level.isEmptyBlock(CROP)&&ItemStack.matches(g.getMainHandItem(),seed(2))&&g.rankXp()==1,"Actual break with both paid seeds retained");
                var intent=pending(level,data);require(intent.size()==1&&ItemStack.matches(ItemStack.parseOptional(level.registryAccess(),intent.getCompound(0).getCompound("seed")),seed(1)),"Actual exact seed obligation recorded in SavedData");
                require(intent.getCompound(0).getString("plant").equals(BuiltInRegistries.BLOCK.getKey(plantedBlock()).toString()),"Persist expected native plant distinct from harvest origin");
                if(DIRECTIONAL)require(intent.getCompound(0).getByte("support")==Direction.EAST.ordinal(),"Original east support recorded before shutdown");
                int n=ground(level,false);require((DIRECTIONAL?n==0:n>0)&&ground(level,true)==0,"Actual configured crop drops; no synthetic named seed");g.getPersistentData().putInt("M45ActualDropped",n);checked=true;LogUtils.getLogger().info("GOLEM_SEED_RESTART_WRITE_ASSERTIONS_OK crossBlock={} externalMod={} callbackSeed={} directionalSeed={} ordinaryFruit={}",CROSS,EXTERNAL,CALLBACK,DIRECTIONAL,n);
            }else{
                int n=g.getPersistentData().getInt("M45ActualDropped");require((DIRECTIONAL?n==0:n>0)&&ground(level,false)==n&&ground(level,true)==0,"Actual crop drops survived independently, no named seed copy");
                if(stage==0){require(g.isNoAi()&&g.rankXp()==1&&ItemStack.matches(g.getMainHandItem(),seed(2))&&level.isEmptyBlock(CROP)&&pending(level,data).size()==1,"Native exact seed2/XP1/intent/crop state restored from disk");if(DIRECTIONAL)require(pending(level,data).getCompound(0).getByte("support")==Direction.EAST.ordinal()&&level.getBlockState(CROP.east()).is(Blocks.JUNGLE_LOG),"Original east support and actual log restored from disk");g.setNoAi(false);stage=1;return;}
                boolean planted=level.getBlockState(CROP).is(plantedBlock());require(g.getMainHandItem().getCount()+g.transferRecoveryItems().stream().mapToInt(ItemStack::getCount).sum()+(planted?1:0)==2,"Each-tick seed conservation, one paid native plant");
                if(!planted||!pending(level,data).isEmpty())return;var state=level.getBlockState(CROP);int age=DIRECTIONAL?state.getValue(GolemPlantAdapterItems.DirectionalPlant.AGE):(CROSS||EXTERNAL||CALLBACK)?((CropBlock)plantedBlock()).getAge(state):state.getValue(SweetBerryBushBlock.AGE);
                require(ItemStack.matches(g.getMainHandItem(),seed(1))&&age==0,"Successful native planting pays exactly one matching seed");
                if(DIRECTIONAL)require(state.getValue(HorizontalDirectionalBlock.FACING)==Direction.EAST&&g.rankXp()==2&&!g.hasTransferRecovery()&&g.getOffhandItem().isEmpty(),"Restored task really plants toward its saved east support with one exact seed and XP2");
                if(++settled<100)return;
                require(level.getEntitiesOfClass(ThaumcraftGolem.class,new AABB(CROP).inflate(16)).size()==1,"One restored worker, no replay for100ticks");checked=true;LogUtils.getLogger().info("GOLEM_SEED_RESTART_READ_ASSERTIONS_OK crossBlock={} externalMod={} callbackSeed={} directionalSeed={} ordinaryFruit={}",CROSS,EXTERNAL,CALLBACK,DIRECTIONAL,n);
            }
        }catch(RuntimeException|AssertionError ex){checked=true;LogUtils.getLogger().error("GOLEM_SEED_RESTART_ASSERTIONS_FAILED",ex);}
        if(checked)active.halt(false);
    }
    private static ListTag pending(ServerLevel l,GolemSeals d){for(var tag:d.save(new CompoundTag(),l.registryAccess()).getList("seals",Tag.TAG_COMPOUND)){var row=(CompoundTag)tag;if(row.getLong("pos")==KEY.pos().asLong())return row.getList("ReplantTasks",Tag.TAG_COMPOUND);}throw new AssertionError("Original seal absent");}
    private static int ground(ServerLevel l,boolean named){return l.getEntitiesOfClass(ItemEntity.class,new AABB(CROP).inflate(16),i->i.getItem().is(named?seedItem():fruitItem())&&i.getItem().has(DataComponents.CUSTOM_NAME)==named).stream().mapToInt(i->i.getItem().getCount()).sum();}
    private static net.minecraft.world.level.block.state.BlockState ripe(){
        if(DIRECTIONAL)return plantedBlock().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING,Direction.EAST).setValue(GolemPlantAdapterItems.DirectionalPlant.AGE,1);
        if(EXTERNAL||CALLBACK){var crop=(CropBlock)plantedBlock();return crop.getStateForAge(crop.getMaxAge());}
        return CROSS?Blocks.CARROTS.defaultBlockState().setValue(CropBlock.AGE,7):Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(SweetBerryBushBlock.AGE,3);}
    private static Item foreignItem(String path){var item=BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("farmersdelight",path));require(item!=Items.AIR,"Real external binary item required: "+path);return item;}
    private static Item seedItem(){
        if(DIRECTIONAL){var item=BuiltInRegistries.ITEM.get(GolemPlantAdapterItems.id("directional"));require(item!=Items.AIR&&!(item instanceof BlockItem),"Real opt-in directional non-BlockItem callback required");return item;}
        if(CALLBACK){var item=BuiltInRegistries.ITEM.get(GolemPlantAdapterItems.id("no_debit"));require(item!=Items.AIR&&!(item instanceof BlockItem),"Real opt-in non-BlockItem callback required");return item;}
        return EXTERNAL?foreignItem("cabbage_seeds"):CROSS?Items.WHEAT_SEEDS:Items.SWEET_BERRIES;}
    private static Item fruitItem(){return DIRECTIONAL?Items.AIR:CALLBACK?Items.WHEAT:EXTERNAL?foreignItem("cabbage"):CROSS?Items.CARROT:Items.SWEET_BERRIES;}
    private static Block plantedBlock(){
        if(DIRECTIONAL){var block=BuiltInRegistries.BLOCK.get(GolemPlantAdapterItems.id("directional"));require(block instanceof GolemPlantAdapterItems.DirectionalPlant,"Real opt-in directional crop required");return block;}
        if(!EXTERNAL)return CROSS||CALLBACK?Blocks.WHEAT:Blocks.SWEET_BERRY_BUSH;
        var block=BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("farmersdelight","cabbages"));require(block instanceof CropBlock,"Real external binary CropBlock required");return block;
    }
    private static void require(boolean v,String m){if(!v)throw new AssertionError(m);}
}
