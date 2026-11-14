package dev.ehtid.thaumcraft.golem.test;

import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.golem.api.GolemHarvestRegistry;
import dev.ehtid.thaumcraft.golem.seal.*;
import dev.ehtid.thaumcraft.port.core.golem.GolemDesign;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.*;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@GameTestHolder("thaumcraft_golem_plants") @PrefixGameTestTemplate(false)
public final class GolemPlantAdapterGameTests {
    private record Fixture(GolemSeals data,GolemSeals.SealKey key,BlockPos pos,ThaumcraftGolem g,ItemStack seed,boolean cocoa,Direction facing,
                           GolemHarvestRegistry.SeedRegistration adapter){
        void close(){adapter.close();data.remove(key.pos(),key.face(),g.ownerId());g.discard();}
    }
    private GolemPlantAdapterGameTests() {}
    private static ItemStack seed(String type,int count){
        var item=BuiltInRegistries.ITEM.get(GolemPlantAdapterItems.id(type));
        if(item==Items.AIR||item instanceof BlockItem)throw new AssertionError("Actual opt-in non-BlockItem fixture required");
        var stack=new ItemStack(item,count);stack.set(DataComponents.CUSTOM_NAME,Component.literal("M50 actual callback seed"));return stack;
    }
    private static Fixture fixture(GameTestHelper h,String type,boolean cocoa){
        return fixture(h,type,cocoa,Direction.NORTH);
    }
    private static Fixture fixture(GameTestHelper h,String type,boolean cocoa,Direction facing){
        h.getLevel().getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0,h.getLevel().getServer());
        for(int x=0;x<12;x++)for(int z=0;z<12;z++)h.setBlock(new BlockPos(x,0,z),Blocks.STONE);
        var support=h.absolutePos(new BlockPos(5,0,5));var pos=support.above();
        h.getLevel().setBlockAndUpdate(support,cocoa?Blocks.STONE.defaultBlockState():Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE,7));
        var ripe=cocoa?Blocks.COCOA.defaultBlockState().setValue(CocoaBlock.FACING,facing).setValue(CocoaBlock.AGE,2):Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE,7);
        if(cocoa)h.getLevel().setBlockAndUpdate(pos.relative(facing),Blocks.JUNGLE_LOG.defaultBlockState());
        h.getLevel().setBlockAndUpdate(pos,ripe);
        var template=seed(type,1);var adapter=GolemHarvestRegistry.registerSeed(ripe.getBlock(),template,ripe.getBlock());
        var owner=UUID.randomUUID();var data=GolemSeals.get(h.getLevel());var key=new GolemSeals.SealKey(support,Direction.UP);
        h.assertTrue(data.place(support,Direction.UP,owner,GolemSeals.Type.HARVEST),"Actual Harvest seal");
        data.configureControls(data.get(key),owner,0,0,true,false);data.configureArea(data.get(key),owner,new BlockPos(1,1,1));data.configureHarvest(data.get(key),owner,new HarvestSettings(true,false));
        var g=GolemModule.GOLEM.get().create(h.getLevel());g.moveTo(Vec3.atBottomCenterOf(cocoa?pos.relative(facing.getOpposite()):pos.east()));
        g.initialize(GolemDesign.fromIds(0,1,1,0,0,0),owner,pos,0);g.setNoAi(true);g.setItemSlot(EquipmentSlot.MAINHAND,template.copyWithCount(2));h.assertTrue(h.getLevel().addFreshEntity(g),"Actual worker/paid seeds");
        return new Fixture(data,key,pos,g,template,cocoa,facing,adapter);
    }
    private static GolemSeals.BlockAssignment claim(Fixture f){return f.data.claimBlock(f.g,p->true).orElseThrow();}
    private static boolean complete(GameTestHelper h,Fixture f,GolemSeals.BlockAssignment t){return f.data.completeBlock(h.getLevel(),f.g,t);}
    private static ListTag pending(GameTestHelper h,Fixture f,GolemSeals d){
        for(var t:d.save(new CompoundTag(),h.getLevel().registryAccess()).getList("seals",Tag.TAG_COMPOUND)){
            var row=(CompoundTag)t;if(row.getLong("pos")==f.key.pos().asLong())return row.getList("ReplantTasks",Tag.TAG_COMPOUND);
        }throw new AssertionError("Original seal absent");
    }
    private static void harvested(GameTestHelper h,Fixture f){h.assertTrue(complete(h,f,claim(f))&&h.getLevel().isEmptyBlock(f.pos)&&ItemStack.matches(f.g.getMainHandItem(),f.seed.copyWithCount(2))&&f.g.rankXp()==1,"Actual harvest, two paid callback seeds retained");}
    private static void planted(GameTestHelper h,Fixture f){
        var state=h.getLevel().getBlockState(f.pos);
        h.assertTrue(state.is(f.cocoa?Blocks.COCOA:Blocks.WHEAT)&&(f.cocoa?state.getValue(CocoaBlock.AGE):state.getValue(CropBlock.AGE))==0
            &&ItemStack.matches(f.g.getMainHandItem(),f.seed)&&!f.g.hasTransferRecovery()&&pending(h,f,f.data).isEmpty()&&f.g.rankXp()==2,"Actual native callback plant1/remaining exact seed1/no extra recovery item/XP2");
        if(f.cocoa)h.assertTrue(state.getValue(CocoaBlock.FACING)==f.facing,"Source original log-facing attachment retained");
    }
    private static void cycle(GameTestHelper h,String type,boolean cocoa){
        cycle(h,type,cocoa,Direction.NORTH);
    }
    private static void cycle(GameTestHelper h,String type,boolean cocoa,Direction facing){
        var f=fixture(h,type,cocoa,facing);h.runAfterDelay(8,()->{harvested(h,f);
            var decoded=GolemSeals.load(f.data.save(new CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess());
            h.assertTrue(pending(h,f,decoded).size()==1,"Native non-BlockItem seed components/expected plant round-trip");
            h.runAfterDelay(11,()->{try{h.assertTrue(complete(h,f,claim(f)),"Real native useOn");planted(h,f);}finally{f.close();}h.succeed();});
        });
    }
    @GameTest(template="golem_plant_adapter",batch="adapter_wheat",timeoutTicks=80)
    public static void actualNonBlockItemNativeWheatCallbackPaysOneAndPersists(GameTestHelper h){cycle(h,"wheat",false);}
    @GameTest(template="golem_plant_adapter",batch="adapter_cocoa",timeoutTicks=80)
    public static void actualNonBlockItemNativeCocoaCallbackRetainsSourceFacing(GameTestHelper h){cycle(h,"cocoa",true);}
    @GameTest(template="golem_plant_adapter",batch="adapter_cocoa_south",timeoutTicks=80)
    public static void actualNonBlockItemCocoaUsesSouthLog(GameTestHelper h){cycle(h,"cocoa",true,Direction.SOUTH);}
    @GameTest(template="golem_plant_adapter",batch="adapter_cocoa_west",timeoutTicks=80)
    public static void actualNonBlockItemCocoaUsesWestLog(GameTestHelper h){cycle(h,"cocoa",true,Direction.WEST);}
    @GameTest(template="golem_plant_adapter",batch="adapter_cocoa_east",timeoutTicks=80)
    public static void actualNonBlockItemCocoaUsesEastLog(GameTestHelper h){cycle(h,"cocoa",true,Direction.EAST);}
    @GameTest(template="golem_plant_adapter",batch="adapter_no_debit",timeoutTicks=80)
    public static void sourceChargesReservedSeedEvenWhenCallbackLeavesItsStackUnchanged(GameTestHelper h){cycle(h,"no_debit",false);}
    @GameTest(template="golem_plant_adapter",batch="adapter_cancel",timeoutTicks=100)
    public static void actualPlaceProtectionRefundsActualSeedAndSameIntentCanRetry(GameTestHelper h){
        var f=fixture(h,"wheat",false);h.runAfterDelay(8,()->{harvested(h,f);h.runAfterDelay(11,()->{
            var task=claim(f);var calls=new AtomicInteger();Consumer<BlockEvent.EntityPlaceEvent> deny=e->{if(e.getLevel()==h.getLevel()&&e.getPos().equals(f.pos)){h.assertTrue(e.getEntity().getUUID().equals(f.g.ownerId()),"Actual owner identity");calls.incrementAndGet();e.setCanceled(true);}};
            NeoForge.EVENT_BUS.addListener(deny);try{
                h.assertTrue(!complete(h,f,task)&&calls.get()==1&&h.getLevel().isEmptyBlock(f.pos)&&ItemStack.matches(f.g.getMainHandItem(),f.seed.copyWithCount(2))&&pending(h,f,f.data).size()==1&&f.g.rankXp()==1,"Actual protected placement rolls back and returns the actual reserved seed");
            }finally{NeoForge.EVENT_BUS.unregister(deny);}
            try{h.assertTrue(complete(h,f,task),"Real retry after protection permits");planted(h,f);}finally{f.close();}h.succeed();
        });});
    }
    @GameTest(template="golem_plant_adapter",batch="adapter_fault",timeoutTicks=80)
    public static void throwingNativeCallbackRestoresCapturedBlockAndGlobalCaptureState(GameTestHelper h){
        var f=fixture(h,"fault",false);h.runAfterDelay(8,()->{harvested(h,f);h.runAfterDelay(11,()->{try{
            h.assertTrue(!complete(h,f,claim(f))&&h.getLevel().isEmptyBlock(f.pos)&&!h.getLevel().captureBlockSnapshots&&!h.getLevel().restoringBlockSnapshots&&h.getLevel().capturedBlockSnapshots.isEmpty(),"Real callback placed then threw: actual snapshot restored, global capture repaired");
            h.assertTrue(ItemStack.matches(f.g.getMainHandItem(),f.seed.copyWithCount(2))&&f.g.rankXp()==1&&pending(h,f,f.data).size()==1&&!f.g.hasTransferRecovery(),"Actual unconsumed seed recovered once, no ghost plant/XP");
        }finally{f.close();}h.succeed();});});
    }
    @GameTest(template="golem_plant_adapter",batch="adapter_event_fault",timeoutTicks=80)
    public static void throwingPlaceEventRestoresTargetAfterEngineClearedItsGlobalSnapshots(GameTestHelper h){
        var f=fixture(h,"wheat",false);h.runAfterDelay(8,()->{harvested(h,f);h.runAfterDelay(11,()->{
            var calls=new AtomicInteger();Consumer<BlockEvent.EntityPlaceEvent> fault=e->{if(e.getLevel()==h.getLevel()&&e.getPos().equals(f.pos)){calls.incrementAndGet();throw new IllegalStateException("Intentional real protection callback failure");}};
            NeoForge.EVENT_BUS.addListener(fault);try{
                h.assertTrue(!complete(h,f,claim(f))&&calls.get()==1&&h.getLevel().isEmptyBlock(f.pos)&&!h.getLevel().captureBlockSnapshots&&!h.getLevel().restoringBlockSnapshots&&h.getLevel().capturedBlockSnapshots.isEmpty(),"Real placement event failed after capture-list transfer: original target restored, no free unprotected plant");
                h.assertTrue(ItemStack.matches(f.g.getMainHandItem(),f.seed.copyWithCount(2))&&!f.g.hasTransferRecovery()&&f.g.rankXp()==1&&pending(h,f,f.data).size()==1,"Actual engine-restored seed recovered once, no duplicate recovery/XP");
            }finally{NeoForge.EVENT_BUS.unregister(fault);f.close();}h.succeed();
        });});
    }
    @GameTest(template="golem_plant_adapter",batch="adapter_outer_capture",timeoutTicks=80)
    public static void enclosingCaptureIsNotClearedOrUsedByANestedPlant(GameTestHelper h){
        var f=fixture(h,"wheat",false);h.runAfterDelay(8,()->{harvested(h,f);h.runAfterDelay(11,()->{var level=h.getLevel();level.captureBlockSnapshots=true;try{
            h.assertTrue(!complete(h,f,claim(f))&&level.captureBlockSnapshots&&level.capturedBlockSnapshots.isEmpty()&&level.isEmptyBlock(f.pos)&&ItemStack.matches(f.g.getMainHandItem(),f.seed.copyWithCount(2)),"Nested callback pauses and preserves enclosing capture/actual seeds");
        }finally{level.captureBlockSnapshots=false;f.close();}h.succeed();});});
    }
    @GameTest(template="golem_plant_adapter",batch="adapter_pass",timeoutTicks=80)
    public static void passResultCannotLeaveAnUnpaidPlantWithoutAProtectionEvent(GameTestHelper h){
        var f=fixture(h,"pass",false);h.runAfterDelay(8,()->{harvested(h,f);h.runAfterDelay(11,()->{try{
            h.assertTrue(!complete(h,f,claim(f))&&h.getLevel().isEmptyBlock(f.pos)&&ItemStack.matches(f.g.getMainHandItem(),f.seed.copyWithCount(2))&&!f.g.hasTransferRecovery()&&f.g.rankXp()==1&&pending(h,f,f.data).size()==1,"Actual mutation followed by PASS is restored, actual reserved seed returned, no unpaid plant or placement XP");
        }finally{f.close();}h.succeed();});});
    }
    @GameTest(template="golem_plant_adapter",batch="adapter_registry",timeoutTicks=40)
    public static void explicitExpectedPlantIsRequiredDefensiveAndScoped(GameTestHelper h){
        var input=seed("wheat",3);
        try{GolemHarvestRegistry.registerSeed(Blocks.WHEAT,input);throw new AssertionError("Guessed non-BlockItem planting");}catch(IllegalArgumentException expected){}
        try{GolemHarvestRegistry.registerSeed(Blocks.WHEAT,input,Blocks.AIR);throw new AssertionError("Air planting accepted");}catch(IllegalArgumentException expected){}
        try(var scope=GolemHarvestRegistry.registerSeed(Blocks.WHEAT,input,Blocks.WHEAT)){
            input.setCount(0);h.assertTrue(GolemHarvestRegistry.plantedBlock(Blocks.WHEAT,seed("wheat",1))==Blocks.WHEAT,"Exact explicit native result independent of input mutation");
            var wrong=seed("wheat",1);wrong.remove(DataComponents.CUSTOM_NAME);h.assertTrue(GolemHarvestRegistry.plantedBlock(Blocks.WHEAT,wrong)==null,"Different components cannot impersonate the paid seed");
        }
        h.assertTrue(GolemHarvestRegistry.seed(Blocks.WHEAT).isEmpty()&&GolemHarvestRegistry.plantedBlock(Blocks.WHEAT,seed("wheat",1))==null,"Scoped adapter removed without a phantom BlockItem fallback");h.succeed();
    }
    @GameTest(template="golem_plant_adapter",batch="adapter_stale",timeoutTicks=100)
    public static void removedMappingAndForgedExpectedPlantCannotSpendACallbackSeed(GameTestHelper h){
        var f=fixture(h,"wheat",false);h.runAfterDelay(8,()->{harvested(h,f);var tag=f.data.save(new CompoundTag(),h.getLevel().registryAccess());
            for(var t:tag.getList("seals",Tag.TAG_COMPOUND))if(((CompoundTag)t).getLong("pos")==f.key.pos().asLong())((CompoundTag)t).getList("ReplantTasks",Tag.TAG_COMPOUND).getCompound(0).putString("plant","minecraft:diamond_block");
            h.assertTrue(pending(h,f,GolemSeals.load(tag,h.getLevel().registryAccess())).isEmpty(),"Forged expected block rejected");f.adapter.close();
            h.runAfterDelay(11,()->{try{h.assertTrue(f.data.claimBlock(f.g,p->true).isEmpty()&&h.getLevel().isEmptyBlock(f.pos)&&ItemStack.matches(f.g.getMainHandItem(),f.seed.copyWithCount(2)),"Removed authoritative mapping pauses without spending real seeds");}finally{f.close();}h.succeed();});
        });
    }
}
