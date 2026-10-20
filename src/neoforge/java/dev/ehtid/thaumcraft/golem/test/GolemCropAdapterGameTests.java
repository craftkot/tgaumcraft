package dev.ehtid.thaumcraft.golem.test;

import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.golem.api.GolemHarvestRegistry;
import dev.ehtid.thaumcraft.golem.seal.*;
import dev.ehtid.thaumcraft.port.core.golem.GolemDesign;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder("thaumcraft") @PrefixGameTestTemplate(false)
public final class GolemCropAdapterGameTests {
    private static final net.minecraft.world.level.block.state.BlockState RIPE=Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(SweetBerryBushBlock.AGE,3);
    private record Fixture(GolemSeals data,GolemSeals.SealKey key,BlockPos crop,ThaumcraftGolem worker,GolemHarvestRegistry.Registration cropScope,GolemHarvestRegistry.SeedRegistration seedScope){
        void close(){cropScope.close();if(seedScope!=null)seedScope.close();data.remove(key.pos(),key.face(),worker.ownerId());worker.discard();}
    }
    private GolemCropAdapterGameTests() {}
    private static ItemStack seed(int n){var s=new ItemStack(Items.SWEET_BERRIES,n);s.set(DataComponents.CUSTOM_NAME,Component.literal("M45 exact paid seed"));return s;}
    private static Fixture fixture(GameTestHelper h,boolean stacked,boolean seeds){
        for(int x=0;x<12;x++)for(int z=0;z<12;z++)h.setBlock(new BlockPos(x,0,z),Blocks.STONE);
        var support=h.absolutePos(new BlockPos(5,0,5));h.getLevel().setBlockAndUpdate(support,Blocks.DIRT.defaultBlockState());
        if(stacked){support=support.above();h.getLevel().setBlockAndUpdate(support,Blocks.BAMBOO.defaultBlockState());}
        var crop=support.above();h.getLevel().setBlockAndUpdate(crop,stacked?Blocks.BAMBOO.defaultBlockState():RIPE);
        var data=GolemSeals.get(h.getLevel());var key=new GolemSeals.SealKey(support,Direction.UP);var owner=UUID.randomUUID();
        h.assertTrue(data.place(support,Direction.UP,owner,GolemSeals.Type.HARVEST),"Actual adapter Harvest seal");data.configureArea(data.get(key),owner,new BlockPos(1,1,1));data.configureHarvest(data.get(key),owner,new HarvestSettings(true,false));
        var g=GolemModule.GOLEM.get().create(h.getLevel());g.moveTo(Vec3.atBottomCenterOf(crop.east()));g.initialize(GolemDesign.fromIds(0,1,1,0,0,0),owner,crop,0);g.setNoAi(true);g.setItemSlot(EquipmentSlot.MAINHAND,seeds?seed(2):new ItemStack(Items.IRON_INGOT,13));h.assertTrue(h.getLevel().addFreshEntity(g),"Real farmer");
        var crops=stacked?GolemHarvestRegistry.registerStacked(Blocks.BAMBOO):GolemHarvestRegistry.registerStandard(RIPE);var reg=seeds?GolemHarvestRegistry.registerSeed(Blocks.SWEET_BERRY_BUSH,seed(1)):null;
        return new Fixture(data,key,crop,g,crops,reg);
    }
    private static GolemSeals.BlockAssignment claim(Fixture f){return f.data.claimBlock(f.worker,p->true).orElseThrow();}
    private static boolean complete(GameTestHelper h,Fixture f,GolemSeals.BlockAssignment a){return f.data.completeBlock(h.getLevel(),f.worker,a);}
    private static ListTag pending(GameTestHelper h,GolemSeals data,GolemSeals.SealKey key){var rows=data.save(new CompoundTag(),h.getLevel().registryAccess()).getList("seals",Tag.TAG_COMPOUND);for(int i=0;i<rows.size();i++)if(rows.getCompound(i).getLong("pos")==key.pos().asLong())return rows.getCompound(i).getList("ReplantTasks",Tag.TAG_COMPOUND);throw new AssertionError("Seal row missing");}
    @GameTest(template="golem_test",batch="crop_seed_registry",timeoutTicks=30)
    public static void seedScopesAreDefensiveRestorePreviousAdapterAndRejectUnimplementedSeedKinds(GameTestHelper h){
        var input=seed(4);try(var first=GolemHarvestRegistry.registerSeed(Blocks.SWEET_BERRY_BUSH,input)){
            input.setCount(0);var copy=GolemHarvestRegistry.seed(Blocks.SWEET_BERRY_BUSH);copy.set(DataComponents.CUSTOM_NAME,Component.literal("not authoritative"));
            h.assertTrue(ItemStack.matches(GolemHarvestRegistry.seed(Blocks.SWEET_BERRY_BUSH),seed(1)),"Input/output mutations cannot change exact one-unit registry template");
            var other=new ItemStack(Items.SWEET_BERRIES);try(var override=GolemHarvestRegistry.registerSeed(Blocks.SWEET_BERRY_BUSH,other)){h.assertTrue(ItemStack.matches(GolemHarvestRegistry.seed(Blocks.SWEET_BERRY_BUSH),other),"Most recent explicit seed mapping wins");}
            h.assertTrue(ItemStack.matches(GolemHarvestRegistry.seed(Blocks.SWEET_BERRY_BUSH),seed(1)),"Closing adapter restores prior mapping");
        }
        h.assertTrue(GolemHarvestRegistry.seed(Blocks.SWEET_BERRY_BUSH).isEmpty(),"No default post-1.12 seed registration");
        try{GolemHarvestRegistry.registerSeed(Blocks.SWEET_BERRY_BUSH,new ItemStack(Items.IRON_INGOT));throw new AssertionError("Unknown planting callback accepted");}catch(IllegalArgumentException expected){}
        h.succeed();
    }
    @GameTest(template="golem_test",batch="crop_standard",timeoutTicks=60)
    public static void standardAdapterReallyBreaksUnknownCropAndMakesNoUnfundedReplant(GameTestHelper h){
        var f=fixture(h,false,false);h.runAfterDelay(8,()->{try{h.assertTrue(complete(h,f,claim(f))&&h.getLevel().isEmptyBlock(f.crop)&&f.worker.rankXp()==1&&f.worker.getMainHandItem().getCount()==13&&pending(h,f.data,f.key).isEmpty(),"Actual standard harvest breaks enrolled specimen; no seed mapping means no fabricated replant");}finally{f.close();}h.succeed();});
    }
    @GameTest(template="golem_test",batch="crop_stacked",timeoutTicks=60)
    public static void stackedAdapterRequiresSameBlockBelowAndKeepsItsActualRoot(GameTestHelper h){
        var f=fixture(h,true,false);h.assertTrue(!GolemHarvestRegistry.isMature(Blocks.BAMBOO.defaultBlockState(),false)&&GolemHarvestRegistry.isMature(Blocks.BAMBOO.defaultBlockState(),true),"Source stacked classification checks identical block below");
        h.runAfterDelay(8,()->{try{h.assertTrue(complete(h,f,claim(f))&&h.getLevel().isEmptyBlock(f.crop)&&h.getLevel().getBlockState(f.crop.below()).is(Blocks.BAMBOO)&&pending(h,f.data,f.key).isEmpty(),"Real upper segment harvested and native rooted lower segment retained");}finally{f.close();}h.succeed();});
    }
    @GameTest(template="golem_test",batch="crop_seed_payment",timeoutTicks=80)
    public static void registeredSeedPersistsExactComponentsAndPaysOneRealItemForReplant(GameTestHelper h){
        var f=fixture(h,false,true);h.runAfterDelay(8,()->{
            h.assertTrue(complete(h,f,claim(f))&&h.getLevel().isEmptyBlock(f.crop)&&ItemStack.matches(f.worker.getMainHandItem(),seed(2)),"Harvest did not spend held seeds");
            var list=pending(h,f.data,f.key);h.assertTrue(list.size()==1&&list.getCompound(0).getString("crop").equals("minecraft:sweet_berry_bush")&&ItemStack.matches(ItemStack.parseOptional(h.getLevel().registryAccess(),list.getCompound(0).getCompound("seed")),seed(1)),"Modern crop ID and exact component-bearing seed persisted");
            var copy=GolemSeals.load(f.data.save(new CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess());h.assertTrue(pending(h,copy,f.key).size()==1&&copy.pendingTasks()==0,"Native intent survives decode without serialized lease");
            h.runAfterDelay(8,()->{try{h.assertTrue(complete(h,f,claim(f))&&h.getLevel().getBlockState(f.crop).is(Blocks.SWEET_BERRY_BUSH)&&h.getLevel().getBlockState(f.crop).getValue(SweetBerryBushBlock.AGE)==0&&ItemStack.matches(f.worker.getMainHandItem(),seed(1))&&pending(h,f.data,f.key).isEmpty(),"Actual native BlockItem placement paid exactly one named seed and clears intent");}finally{f.close();}h.succeed();});
        });
    }
    @GameTest(template="golem_test",batch="crop_seed_stale",timeoutTicks=80)
    public static void changedSeedConfigurationAndForgedNbtCannotCreateAnArbitraryPlant(GameTestHelper h){
        var f=fixture(h,false,true);h.runAfterDelay(8,()->{h.assertTrue(complete(h,f,claim(f)),"Actual standard harvest");var tag=f.data.save(new CompoundTag(),h.getLevel().registryAccess());
            for(var row:tag.getList("seals",Tag.TAG_COMPOUND))if(((CompoundTag)row).getLong("pos")==f.key.pos().asLong())((CompoundTag)row).getList("ReplantTasks",Tag.TAG_COMPOUND).getCompound(0).put("seed",new ItemStack(Items.DIAMOND).save(h.getLevel().registryAccess()));
            h.assertTrue(pending(h,GolemSeals.load(tag,h.getLevel().registryAccess()),f.key).isEmpty(),"Forged persisted seed is rejected instead of ghost conversion");f.seedScope.close();
            h.runAfterDelay(8,()->{try{h.assertTrue(f.data.claimBlock(f.worker,p->true).isEmpty()&&ItemStack.matches(f.worker.getMainHandItem(),seed(2))&&h.getLevel().isEmptyBlock(f.crop),"Removing authoritative seed mapping pauses placement without debit");}finally{f.close();}h.succeed();});
        });
    }
    @GameTest(template="golem_test",batch="crop_click_priority",timeoutTicks=60)
    public static void clickableRegistrationTakesSourcePrecedenceOverStandardAndSeedMappings(GameTestHelper h){
        var f=fixture(h,false,true);var click=GolemHarvestRegistry.registerClickable(RIPE);h.runAfterDelay(8,()->{try{h.assertTrue(complete(h,f,claim(f))&&h.getLevel().getBlockState(f.crop).getValue(SweetBerryBushBlock.AGE)==1&&ItemStack.matches(f.worker.getMainHandItem(),seed(2))&&pending(h,f.data,f.key).isEmpty(),"Source clickable branch wins, no destruction or seed/replant side effect");}finally{click.close();f.close();}h.succeed();});
    }
    private static ItemStack crossSeed(int n){var s=new ItemStack(Items.WHEAT_SEEDS,n);s.set(DataComponents.CUSTOM_NAME,Component.literal("M47 crop rotation seed"));return s;}
    private static void carrots(GameTestHelper h,Fixture f){h.getLevel().setBlockAndUpdate(f.key.pos(),Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE,7));h.getLevel().setBlockAndUpdate(f.crop,Blocks.CARROTS.defaultBlockState().setValue(CropBlock.AGE,7));f.worker.setItemSlot(EquipmentSlot.MAINHAND,crossSeed(2));}
    @GameTest(template="golem_test",batch="crop_cross_registration",timeoutTicks=30)
    public static void sourceSeedMappingMayPointAtADifferentNativePlant(GameTestHelper h){
        try(var mapping=GolemHarvestRegistry.registerSeed(Blocks.PUMPKIN,new ItemStack(Items.PUMPKIN_SEEDS))){var actual=GolemHarvestRegistry.seed(Blocks.PUMPKIN);h.assertTrue(actual.is(Items.PUMPKIN_SEEDS)&&((BlockItem)actual.getItem()).getBlock()==Blocks.PUMPKIN_STEM,"Source native seed mapping pumpkin->stem is not falsely rejected as a different block");}
        h.succeed();
    }
    @GameTest(template="golem_test",batch="crop_cross_payment",timeoutTicks=80)
    public static void actualCrossCropSeedPlantsItsNativeBlockAndPaysExactlyOne(GameTestHelper h){
        var f=fixture(h,false,false);carrots(h,f);var mapping=GolemHarvestRegistry.registerSeed(Blocks.CARROTS,crossSeed(1));
        h.runAfterDelay(8,()->{h.assertTrue(complete(h,f,claim(f))&&h.getLevel().isEmptyBlock(f.crop)&&ItemStack.matches(f.worker.getMainHandItem(),crossSeed(2)),"Real carrot harvest retains both actual wheat seeds");
            var intent=pending(h,f.data,f.key).getCompound(0);h.assertTrue(intent.getString("crop").equals("CARROTS")&&intent.getString("plant").equals("minecraft:wheat"),"Paid intent separates harvested crop from expected native planted block");
            var copy=GolemSeals.load(f.data.save(new CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess());h.assertTrue(pending(h,copy,f.key).size()==1,"Cross-block mapping survives decode with exact actual seed");
            h.runAfterDelay(8,()->{try{h.assertTrue(complete(h,f,claim(f))&&h.getLevel().getBlockState(f.crop).is(Blocks.WHEAT)&&h.getLevel().getBlockState(f.crop).getValue(CropBlock.AGE)==0&&ItemStack.matches(f.worker.getMainHandItem(),crossSeed(1))&&pending(h,f.data,f.key).isEmpty()&&f.worker.rankXp()==2,"Real Native BlockItem planted wheat rather than carrots, seed2->seed1+plant1, no ghost crop or stale intent");}finally{mapping.close();f.close();}h.succeed();});
        });
    }
    @GameTest(template="golem_test",batch="crop_cross_forgery",timeoutTicks=60)
    public static void forgedOrMissingCrossPlantTargetCannotTurnSeedIntoAnotherBlock(GameTestHelper h){
        var f=fixture(h,false,false);carrots(h,f);var mapping=GolemHarvestRegistry.registerSeed(Blocks.CARROTS,crossSeed(1));
        h.runAfterDelay(8,()->{try{h.assertTrue(complete(h,f,claim(f)),"Real crop harvest");var tag=f.data.save(new CompoundTag(),h.getLevel().registryAccess());CompoundTag intent=null;
            for(var t:tag.getList("seals",Tag.TAG_COMPOUND))if(((CompoundTag)t).getLong("pos")==f.key.pos().asLong())intent=((CompoundTag)t).getList("ReplantTasks",Tag.TAG_COMPOUND).getCompound(0);
            h.assertTrue(intent!=null,"Real pending record");intent.putString("plant","minecraft:diamond_block");h.assertTrue(pending(h,GolemSeals.load(tag,h.getLevel().registryAccess()),f.key).isEmpty(),"Forged target is not accepted for real wheat seeds");
            intent.remove("plant");h.assertTrue(pending(h,GolemSeals.load(tag,h.getLevel().registryAccess()),f.key).isEmpty(),"Legacy missing target means original crop only; it cannot silently upgrade into a cross-block command");
            h.assertTrue(ItemStack.matches(f.worker.getMainHandItem(),crossSeed(2))&&h.getLevel().isEmptyBlock(f.crop),"Decode never mutates the actual seed owner or world");
        }finally{mapping.close();f.close();}h.succeed();});
    }

}
