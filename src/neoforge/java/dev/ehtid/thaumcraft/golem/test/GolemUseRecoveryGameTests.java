package dev.ehtid.thaumcraft.golem.test;

import com.mojang.logging.LogUtils;
import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.golem.seal.GolemSeals;
import dev.ehtid.thaumcraft.port.core.golem.GolemDesign;
import dev.ehtid.thaumcraft.port.core.golem.GolemUseRules;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.*;
import java.util.UUID;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;

/** Real Use -> ItemStack.useOn -> capture/event failures, with no copied inventory refunds. */
@GameTestHolder("thaumcraft_golem_plants") @PrefixGameTestTemplate(false)
public final class GolemUseRecoveryGameTests {
    private record Fixture(GolemSeals data,GolemSeals.SealKey key,ThaumcraftGolem worker,ItemStack input){
        BlockPos plant(){return key.pos().above();}
        void close(){data.remove(key.pos(),key.face(),worker.ownerId());worker.discard();}
    }
    private GolemUseRecoveryGameTests(){}
    private static Fixture fixture(GameTestHelper h,String item){
        for(int x=0;x<12;x++)for(int z=0;z<12;z++)h.setBlock(new BlockPos(x,0,z),Blocks.STONE);
        h.getLevel().getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0,h.getLevel().getServer());
        var support=h.absolutePos(new BlockPos(5,1,5));
        h.getLevel().setBlockAndUpdate(support,Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE,7));
        var real=BuiltInRegistries.ITEM.get(GolemPlantAdapterItems.id(item));
        if(real==Items.AIR||real instanceof BlockItem)throw new AssertionError("Explicit native non-BlockItem test run required");
        var input=new ItemStack(real,4);input.set(DataComponents.CUSTOM_NAME,Component.literal("M51 actual Use cargo"));
        var owner=UUID.randomUUID();var data=GolemSeals.get(h.getLevel());var key=new GolemSeals.SealKey(support,Direction.UP);
        h.assertTrue(data.place(support,Direction.UP,owner,GolemSeals.Type.USE),"Actual Use seal on native farmland");
        data.configureControls(data.get(key),owner,0,0,true,false);
        var g=GolemModule.GOLEM.get().create(h.getLevel());g.moveTo(Vec3.atBottomCenterOf(support.north()));
        g.initialize(GolemDesign.fromIds(0,1,1,0,0,0),owner,support,0);g.setNoAi(true);g.setItemSlot(EquipmentSlot.MAINHAND,input.copy());
        h.assertTrue(h.getLevel().addFreshEntity(g),"Actual source SMART+DEFT worker with named four-unit payload");
        return new Fixture(data,key,g,input);
    }
    private static GolemSeals.BlockAssignment claim(Fixture f){return f.data.claimBlock(f.worker,p->true).orElseThrow();}
    private static boolean complete(GameTestHelper h,Fixture f,GolemSeals.BlockAssignment task){return f.data.completeBlock(h.getLevel(),f.worker,task);}
    private static void recovered(GameTestHelper h,Fixture f){
        h.assertTrue(ItemStack.matches(f.worker.getMainHandItem(),f.input)&&!f.worker.hasTransferRecovery(),"Original exact cargo4 returned once without copied/relabelled recovery payload");
        h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class,h.getBounds()).isEmpty(),"No copied or leaked actual input on ground");
        h.assertTrue(h.getLevel().isEmptyBlock(f.plant())&&h.getLevel().getBlockState(f.key.pos()).is(Blocks.FARMLAND),"Actual failed Use plant rolled back and original clicked support retained");
        h.assertTrue(!h.getLevel().captureBlockSnapshots&&!h.getLevel().restoringBlockSnapshots&&h.getLevel().capturedBlockSnapshots.isEmpty(),"Native snapshot capture flags/list restored");
    }
    private static void attemptedOnce(GameTestHelper h,Fixture f,GolemSeals.BlockAssignment task){
        h.assertTrue(f.worker.rankXp()==1&&!complete(h,f,task)&&f.worker.rankXp()==1,"Source attempted XP1/task finish; stale lease cannot replay action or XP");
    }
    private static void itemFault(GameTestHelper h,String type){
        var f=fixture(h,type);h.runAfterDelay(10,()->{try{
            var task=claim(f);h.assertTrue(complete(h,f,task),"Source completes the actual failed click attempt");recovered(h,f);attemptedOnce(h,f,task);
            if(type.equals("multi_fault"))h.assertTrue(h.getLevel().isEmptyBlock(f.plant().east()),"Second real captured block restored; target fallback alone is insufficient");
            LogUtils.getLogger().info("GOLEM_USE_RECOVERY_ITEM_ASSERTIONS_OK type={} actualCargo=4 plant=0 xp=1 staleReplay=false",type);
        }finally{f.close();}h.succeed();});
    }
    @GameTest(template="golem_plant_adapter",batch="use_snapshot_item",timeoutTicks=65)
    public static void actualItemUseOnFailureRestoresPlantAndExactCargo(GameTestHelper h){itemFault(h,"fault");}
    @GameTest(template="golem_plant_adapter",batch="use_snapshot_multi",timeoutTicks=65)
    public static void actualMultiBlockItemFailureRestoresEveryCapturedBlock(GameTestHelper h){itemFault(h,"multi_fault");}
    @GameTest(template="golem_plant_adapter",batch="use_snapshot_cursor",timeoutTicks=65)
    public static void callbackMovesOriginalCargoToCursorThenFailsWithoutDuplicatingIt(GameTestHelper h){
        var f=fixture(h,"cursor_fault");var actor=new AtomicReference<Player>();
        f.data.configureUse(f.data.get(f.key),f.worker.ownerId(),GolemUseRules.Settings.defaults().withToggle(7,true));
        h.runAfterDelay(10,()->{Consumer<PlayerInteractEvent.RightClickBlock> observe=e->{if(e.getLevel()==h.getLevel()&&e.getPos().equals(f.key.pos()))actor.set(e.getEntity());};
            NeoForge.EVENT_BUS.addListener(observe);try{
                var task=claim(f);h.assertTrue(complete(h,f,task),"Actual item moved original hand stack to real cursor then threw");recovered(h,f);attemptedOnce(h,f,task);
                h.assertTrue(actor.get()!=null&&actor.get().getUUID().equals(f.worker.ownerId())&&!actor.get().isShiftKeyDown()&&actor.get().getMainHandItem().isEmpty()&&actor.get().containerMenu.getCarried().isEmpty(),"Owner fake player cursor/hand emptied and sneak reset after real ownership recovery");
                LogUtils.getLogger().info("GOLEM_USE_RECOVERY_CURSOR_ASSERTIONS_OK actualCargo=4 cursor=0 fakeHand=0 xp=1 staleReplay=false");
            }finally{NeoForge.EVENT_BUS.unregister(observe);f.close();}h.succeed();
        });
    }
    @GameTest(template="golem_plant_adapter",batch="use_snapshot_event",timeoutTicks=65)
    public static void actualPlacementEventFailureRestoresAdjacentTargetAfterEngineMovedSnapshots(GameTestHelper h){
        var f=fixture(h,"wheat");h.runAfterDelay(10,()->{var calls=new AtomicInteger();Consumer<BlockEvent.EntityPlaceEvent> fault=e->{if(e.getLevel()==h.getLevel()&&e.getPos().equals(f.plant())){calls.incrementAndGet();h.assertTrue(e.getEntity().getUUID().equals(f.worker.ownerId()),"Real owner placement event");throw new IllegalStateException("Intentional native Use placement event fault");}};
            NeoForge.EVENT_BUS.addListener(fault);try{
                var task=claim(f);h.assertTrue(complete(h,f,task)&&calls.get()==1,"Actual native event ran after ItemStack placement");recovered(h,f);attemptedOnce(h,f,task);
                LogUtils.getLogger().info("GOLEM_USE_RECOVERY_EVENT_ASSERTIONS_OK events=1 actualCargo=4 plant=0 xp=1");
            }finally{NeoForge.EVENT_BUS.unregister(fault);f.close();}h.succeed();
        });
    }
    @GameTest(template="golem_plant_adapter",batch="use_snapshot_cancel",timeoutTicks=85)
    public static void actualProtectionCancellationRecoversStackAndNewScanCanPerformThePermittedAction(GameTestHelper h){
        var f=fixture(h,"wheat");h.runAfterDelay(10,()->{var task=claim(f);var calls=new AtomicInteger();Consumer<BlockEvent.EntityPlaceEvent> deny=e->{if(e.getLevel()==h.getLevel()&&e.getPos().equals(f.plant())){calls.incrementAndGet();e.setCanceled(true);}};
            NeoForge.EVENT_BUS.addListener(deny);try{h.assertTrue(complete(h,f,task)&&calls.get()==1,"Actual protection cancellation");recovered(h,f);attemptedOnce(h,f,task);}
            finally{NeoForge.EVENT_BUS.unregister(deny);}
            h.runAfterDelay(6,()->{try{
                h.assertTrue(complete(h,f,claim(f))&&h.getLevel().getBlockState(f.plant()).is(Blocks.WHEAT)&&ItemStack.matches(f.worker.getMainHandItem(),f.input.copyWithCount(3))&&f.worker.rankXp()==2&&!f.worker.hasTransferRecovery(),"New actual Use attempt plants1+remaining3 after protection permits; failed lease did not replay");
                LogUtils.getLogger().info("GOLEM_USE_RECOVERY_CANCEL_ASSERTIONS_OK initial=4 planted=1 remaining=3 attemptsXP=2");
            }finally{f.close();}h.succeed();});
        });
    }
    @GameTest(template="golem_plant_adapter",batch="use_snapshot_pass",timeoutTicks=65)
    public static void actualMutationThenPassCannotBypassPlacementProtectionAndLeaveAFreePlant(GameTestHelper h){
        var f=fixture(h,"pass");h.runAfterDelay(10,()->{try{var task=claim(f);h.assertTrue(complete(h,f,task),"Actual Use callback returned PASS after mutating target");recovered(h,f);attemptedOnce(h,f,task);
            LogUtils.getLogger().info("GOLEM_USE_RECOVERY_PASS_ASSERTIONS_OK actualCargo=4 plant=0 xp=1");
        }finally{f.close();}h.succeed();});
    }
    @GameTest(template="golem_plant_adapter",batch="use_snapshot_outer",timeoutTicks=65)
    public static void enclosingNativeCaptureKeepsItsActualSnapshotAndLeaseCanRetryAfterItEnds(GameTestHelper h){
        var f=fixture(h,"wheat");h.runAfterDelay(10,()->{var task=claim(f);var level=h.getLevel();var outer=h.absolutePos(new BlockPos(2,1,2));level.captureBlockSnapshots=true;level.setBlockAndUpdate(outer,Blocks.GOLD_BLOCK.defaultBlockState());
            var captured=level.capturedBlockSnapshots.getFirst();try{
                h.assertTrue(!complete(h,f,task)&&f.worker.rankXp()==0&&ItemStack.matches(f.worker.getMainHandItem(),f.input)&&level.captureBlockSnapshots&&level.capturedBlockSnapshots.size()==1&&level.capturedBlockSnapshots.getFirst()==captured&&level.isEmptyBlock(f.plant()),"Nested Use pauses before click/payment and leaves enclosing snapshot ownership intact");
            }finally{level.captureBlockSnapshots=false;level.capturedBlockSnapshots.clear();}
            try{
                h.assertTrue(complete(h,f,task)&&f.worker.rankXp()==1&&level.getBlockState(f.plant()).is(Blocks.WHEAT)&&ItemStack.matches(f.worker.getMainHandItem(),f.input.copyWithCount(3))&&level.getBlockState(outer).is(Blocks.GOLD_BLOCK),"Same unconsumed lease runs once after enclosing capture ends, without reverting the other operation");
                LogUtils.getLogger().info("GOLEM_USE_RECOVERY_OUTER_ASSERTIONS_OK pausedXP=0 retriedXP=1 plant=1 remaining=3 enclosingBlock=retained");
            }finally{f.close();}h.succeed();
        });
    }
    @GameTest(template="golem_plant_adapter",batch="use_snapshot_restoring",timeoutTicks=65)
    public static void enclosingSnapshotRestorationDoesNotStartAUseClickOrMoveItsCargo(GameTestHelper h){
        var f=fixture(h,"wheat");h.runAfterDelay(10,()->{var task=claim(f);var level=h.getLevel();level.restoringBlockSnapshots=true;try{
            h.assertTrue(!complete(h,f,task)&&level.restoringBlockSnapshots&&f.worker.rankXp()==0&&ItemStack.matches(f.worker.getMainHandItem(),f.input)&&level.isEmptyBlock(f.plant()),"Restoring phase retains its flag and no click/XP/payment occurs");
        }finally{level.restoringBlockSnapshots=false;}
            try{h.assertTrue(complete(h,f,task)&&f.worker.rankXp()==1&&level.getBlockState(f.plant()).is(Blocks.WHEAT)&&ItemStack.matches(f.worker.getMainHandItem(),f.input.copyWithCount(3)),"Same lease can perform normal native action after restoration completes");}
            finally{f.close();}h.succeed();
        });
    }
    @GameTest(template="golem_plant_adapter",batch="use_snapshot_abort",timeoutTicks=65)
    public static void abortDigEventFailureRestoresItsCapturedMutationWithoutRepeatingTheStartedClick(GameTestHelper h){
        var f=fixture(h,"wheat");
        f.data.configureUse(f.data.get(f.key),f.worker.ownerId(),GolemUseRules.Settings.defaults().withToggle(4,true));
        h.runAfterDelay(10,()->{var starts=new AtomicInteger();var aborts=new AtomicInteger();Consumer<PlayerInteractEvent.LeftClickBlock> fault=e->{
            if(e.getLevel()!=h.getLevel()||!e.getPos().equals(f.key.pos()))return;
            h.assertTrue(e.getEntity().getUUID().equals(f.worker.ownerId()),"Actual owner dig event");
            if(e.getAction()==PlayerInteractEvent.LeftClickBlock.Action.START){starts.incrementAndGet();return;}
            if(e.getAction()==PlayerInteractEvent.LeftClickBlock.Action.ABORT){
                aborts.incrementAndGet();h.getLevel().captureBlockSnapshots=true;
                h.getLevel().setBlockAndUpdate(f.plant(),Blocks.GOLD_BLOCK.defaultBlockState());
                throw new IllegalStateException("Intentional actual ABORT cleanup callback capture fault");
            }
        };NeoForge.EVENT_BUS.addListener(fault);try{
            var task=claim(f);h.assertTrue(complete(h,f,task)&&starts.get()==1&&aborts.get()==1,"One normal START followed by the real failing ABORT event");
            recovered(h,f);attemptedOnce(h,f,task);
            h.assertTrue(starts.get()==1&&aborts.get()==1,"Stale lease cannot repeat START or cleanup callback");
            LogUtils.getLogger().info("GOLEM_USE_RECOVERY_ABORT_ASSERTIONS_OK start=1 abort=1 actualCargo=4 cleanupBlock=0 xp=1 staleReplay=false");
        }finally{NeoForge.EVENT_BUS.unregister(fault);f.close();}h.succeed();});
    }
}
