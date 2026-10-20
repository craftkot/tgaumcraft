package dev.ehtid.thaumcraft.golem.test;

import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.golem.api.GolemHarvestRegistry;
import dev.ehtid.thaumcraft.golem.seal.GolemSeals;
import dev.ehtid.thaumcraft.port.core.golem.GolemDesign;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.*;
import java.util.*;
import java.util.function.Consumer;

/** Explicit adapter specimen; post-1.12 berries are never enrolled as source content by default. */
@GameTestHolder("thaumcraft") @PrefixGameTestTemplate(false)
public final class GolemClickableHarvestGameTests {
    private static final BlockState RIPE=Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(SweetBerryBushBlock.AGE,3);
    private record Fixture(GolemSeals data,GolemSeals.SealKey key,BlockPos crop,ThaumcraftGolem worker,GolemHarvestRegistry.Registration registration) {
        void close(){registration.close();data.remove(key.pos(),key.face(),worker.ownerId());worker.discard();}
    }
    private GolemClickableHarvestGameTests() {}
    private static Fixture fixture(GameTestHelper h,boolean ai) {
        for(int x=0;x<12;x++)for(int z=0;z<12;z++)h.setBlock(new BlockPos(x,0,z),Blocks.STONE);
        var base=h.absolutePos(new BlockPos(5,0,5));var crop=base.above();h.getLevel().setBlockAndUpdate(base,Blocks.DIRT.defaultBlockState());h.getLevel().setBlockAndUpdate(crop,RIPE);
        var data=GolemSeals.get(h.getLevel());var key=new GolemSeals.SealKey(base,Direction.UP);var owner=UUID.randomUUID();
        h.assertTrue(data.place(base,Direction.UP,owner,GolemSeals.Type.HARVEST),"Actual Harvest seal");data.configureArea(data.get(key),owner,new BlockPos(1,1,1));
        var worker=GolemModule.GOLEM.get().create(h.getLevel());worker.moveTo(Vec3.atBottomCenterOf(crop.east()));worker.setYRot(0);worker.initialize(GolemDesign.fromIds(0,1,1,0,0,0),owner,crop,0);worker.setNoAi(!ai);
        if(!ai)worker.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_INGOT,13));
        h.assertTrue(h.getLevel().addFreshEntity(worker),"Real SMART/DEFT farmer");var registration=GolemHarvestRegistry.registerClickable(RIPE);
        return new Fixture(data,key,crop,worker,registration);
    }
    private static GolemSeals.BlockAssignment claim(Fixture f){return f.data.claimBlock(f.worker,p->true).orElseThrow();}
    private static boolean complete(GameTestHelper h,Fixture f,GolemSeals.BlockAssignment task){return f.data.completeBlock(h.getLevel(),f.worker,task);}
    private static int drops(GameTestHelper h,Item item){return h.getLevel().getEntitiesOfClass(ItemEntity.class,h.getBounds(),e->e.getItem().is(item)).stream().mapToInt(e->e.getItem().getCount()).sum();}
    private static int recovery(ThaumcraftGolem g,Item item){return g.transferRecoveryItems().stream().filter(s->s.is(item)).mapToInt(ItemStack::getCount).sum();}
    private static boolean noReplant(GameTestHelper h,Fixture f){var rows=f.data.save(new CompoundTag(),h.getLevel().registryAccess()).getList("seals",Tag.TAG_COMPOUND);for(int i=0;i<rows.size();i++)if(rows.getCompound(i).getLong("pos")==f.key.pos().asLong())return rows.getCompound(i).getList("ReplantTasks",Tag.TAG_COMPOUND).isEmpty();throw new AssertionError("Seal record missing");}
    @GameTest(template="golem_test",batch="click_crop_registry",timeoutTicks=30)
    public static void registryIsExplicitExactDefensiveAndScopesDoNotEraseOtherAdapters(GameTestHelper h) {
        h.assertTrue(!GolemHarvestRegistry.isClickable(RIPE),"New vanilla crops are not silently enrolled");
        var input=new BlockState[]{RIPE};var first=GolemHarvestRegistry.registerClickable(input);var second=GolemHarvestRegistry.registerClickable(RIPE);
        try {input[0]=Blocks.AIR.defaultBlockState();first.close();first.close();h.assertTrue(GolemHarvestRegistry.isClickable(RIPE)&&!GolemHarvestRegistry.isClickable(RIPE.setValue(SweetBerryBushBlock.AGE,2)),"Duplicate scope survives another scope's repeated close and exact age is respected");}
        finally{first.close();second.close();}
        h.assertTrue(!GolemHarvestRegistry.isClickable(RIPE),"Last scope removes explicit enrollment");
        try(var wildcard=GolemHarvestRegistry.registerClickable(Blocks.SWEET_BERRY_BUSH)){h.assertTrue(GolemHarvestRegistry.isClickable(RIPE.setValue(SweetBerryBushBlock.AGE,0)),"Explicit source wildcard maps to all modern states");}
        try{GolemHarvestRegistry.registerClickable(Blocks.AIR.defaultBlockState());throw new AssertionError("Air enrollment accepted");}catch(IllegalArgumentException expected){}
        h.succeed();
    }
    @GameTest(template="golem_test",batch="click_crop_actual",timeoutTicks=60)
    public static void realRegisteredClickHarvestsWithoutBreakingReplantingOrSpendingCargo(GameTestHelper h) {
        var f=fixture(h,false);h.runAfterDelay(8,()->{try{
            var task=claim(f);h.assertTrue(complete(h,f,task),"Actual shared-task click completes");int berries=drops(h,Items.SWEET_BERRIES);
            h.assertTrue(h.getLevel().getBlockState(f.crop).getValue(SweetBerryBushBlock.AGE)==1&&berries>=2&&berries<=4&&f.worker.getMainHandItem().getCount()==13&&f.worker.rankXp()==1,"Native block use resets age and creates real fruit, retains all13 cargo, source attempted-clickXP1");
            h.assertTrue(!complete(h,f,task)&&noReplant(h,f)&&!f.worker.hasTransferRecovery(),"No stale callback/replant intent or duplicate reward");
        }finally{f.close();}h.succeed();});
    }
    @GameTest(template="golem_test",batch="click_crop_protection",timeoutTicks=60)
    public static void actualOwnerProtectionSeesSourceHandFaceAndCornerAndPreventsTheClick(GameTestHelper h) {
        var f=fixture(h,false);h.runAfterDelay(8,()->{int[] calls={0};Consumer<PlayerInteractEvent.RightClickBlock> deny=e->{if(e.getLevel()==h.getLevel()&&e.getPos().equals(f.crop)){
            calls[0]++;h.assertTrue(e.getEntity().getUUID().equals(f.worker.ownerId())&&e.getEntity().getMainHandItem().isEmpty()&&e.getFace()==Direction.NORTH&&e.getHitVec().getLocation().equals(Vec3.atLowerCornerOf(f.crop)),"Actual owner, empty source hand, legacy horizontal face and hitXYZ0 passed to protected native use");e.setCanceled(true);
        }};NeoForge.EVENT_BUS.addListener(deny);try{
            h.assertTrue(complete(h,f,claim(f))&&calls[0]==1&&h.getLevel().getBlockState(f.crop)==RIPE&&drops(h,Items.SWEET_BERRIES)==0&&f.worker.rankXp()==1&&f.worker.getMainHandItem().getCount()==13&&noReplant(h,f),"Protection cancels world mutation; source attempted-click XP retained without spending cargo");
        }finally{NeoForge.EVENT_BUS.unregister(deny);f.close();}h.succeed();});
    }
    @GameTest(template="golem_test",batch="click_crop_exception",timeoutTicks=60)
    public static void reentrantFailingModCallbackRecoversActualCursorAndInventoryOnce(GameTestHelper h) {
        var f=fixture(h,false);h.runAfterDelay(8,()->{var task=claim(f);int[] calls={0};Consumer<PlayerInteractEvent.RightClickBlock> fault=e->{if(e.getLevel()==h.getLevel()&&e.getPos().equals(f.crop)){
            calls[0]++;h.assertTrue(!complete(h,f,task),"Reentrant same lease cannot click twice");e.getEntity().containerMenu.setCarried(new ItemStack(Items.DIAMOND,2));e.getEntity().setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.EMERALD,3));throw new IllegalStateException("Intentional clickable crop callback fault");
        }};NeoForge.EVENT_BUS.addListener(fault);try{
            h.assertTrue(complete(h,f,task)&&calls[0]==1&&f.worker.rankXp()==1&&f.worker.getMainHandItem().getCount()==13&&recovery(f.worker,Items.DIAMOND)==2&&recovery(f.worker,Items.EMERALD)==3&&h.getLevel().getBlockState(f.crop)==RIPE&&drops(h,Items.SWEET_BERRIES)==0,"Actual callback cursor2/inventory3 are recovered exactly once around original cargo13");
            h.assertTrue(!complete(h,f,task)&&recovery(f.worker,Items.DIAMOND)==2&&recovery(f.worker,Items.EMERALD)==3,"Stale assignment cannot replay callback-owned items");
        }finally{NeoForge.EVENT_BUS.unregister(fault);f.close();}h.succeed();});
    }
    @GameTest(template="golem_test",batch="click_crop_stale",timeoutTicks=60)
    public static void removedAdapterInvalidatesAlreadyAssignedCropWithoutClickOrExperience(GameTestHelper h) {
        var f=fixture(h,false);h.runAfterDelay(8,()->{try{var task=claim(f);f.registration.close();h.assertTrue(!complete(h,f,task)&&f.worker.rankXp()==0&&h.getLevel().getBlockState(f.crop)==RIPE&&drops(h,Items.SWEET_BERRIES)==0,"Fresh completion rechecks authoritative adapter enrollment");}finally{f.close();}h.succeed();});
    }
    @GameTest(template="golem_test",batch="click_crop_ai",timeoutTicks=330)
    public static void actualAiClicksThenCollectsAndFillsRealFruitThroughTheSharedBoard(GameTestHelper h) {
        var f=fixture(h,true);var pickup=new GolemSeals.SealKey(h.absolutePos(new BlockPos(3,0,5)),Direction.UP);var fill=new GolemSeals.SealKey(h.absolutePos(new BlockPos(9,1,5)),Direction.UP);
        h.getLevel().setBlockAndUpdate(fill.pos(),Blocks.BARREL.defaultBlockState());h.assertTrue(f.data.place(pickup.pos(),pickup.face(),f.worker.ownerId(),GolemSeals.Type.PICKUP)&&f.data.place(fill.pos(),fill.face(),f.worker.ownerId(),GolemSeals.Type.FILL),"Real Pickup and Fill peers");f.data.configureArea(f.data.get(pickup),f.worker.ownerId(),new BlockPos(4,1,2));
        var barrel=(Container)h.getLevel().getBlockEntity(fill.pos());h.runAfterDelay(329,()->{f.data.remove(pickup.pos(),pickup.face(),f.worker.ownerId());f.data.remove(fill.pos(),fill.face(),f.worker.ownerId());f.close();});
        h.startSequence().thenWaitUntil(()->h.assertTrue(barrel.getItem(0).is(Items.SWEET_BERRIES),"Real AI fruit delivery"))
            .thenIdle(100).thenExecute(()->{try{int n=barrel.getItem(0).getCount();h.assertTrue(n>=2&&n<=4&&drops(h,Items.SWEET_BERRIES)==0&&f.worker.getMainHandItem().isEmpty()&&!f.worker.hasTransferRecovery()&&h.getLevel().getBlockState(f.crop).getValue(SweetBerryBushBlock.AGE)==1&&f.worker.rankXp()>=1&&noReplant(h,f),"Actual click→item entity pickup→native barrel Fill, no seed debit/replay for100ticks");}finally{f.data.remove(pickup.pos(),pickup.face(),f.worker.ownerId());f.data.remove(fill.pos(),fill.face(),f.worker.ownerId());f.close();}}).thenSucceed();
    }
}
