package dev.ehtid.thaumcraft.golem.test;

import com.mojang.authlib.GameProfile;
import dev.ehtid.thaumcraft.blockentity.CrucibleBlockEntity;
import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.golem.inventory.GolemContainers;
import dev.ehtid.thaumcraft.golem.menu.GolemSealMenu;
import dev.ehtid.thaumcraft.golem.seal.GolemSeals;
import dev.ehtid.thaumcraft.golem.seal.SealFilter;
import dev.ehtid.thaumcraft.item.CrystalEssenceItem;
import dev.ehtid.thaumcraft.player.ThaumcraftPlayerAttachments;
import dev.ehtid.thaumcraft.port.core.golem.GolemDesign;
import dev.ehtid.thaumcraft.registry.ThaumcraftBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;
import java.util.UUID;
import java.util.function.Consumer;

@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class GolemEmptyGameTests {
    private GolemEmptyGameTests() {}

    @GameTest(template="golem_test", batch="empty_recipe", timeoutTicks=40)
    public static void paidVacuosRecipePlacementMenuAndBellRefundUseExactEmptyType(GameTestHelper h) {
        floor(h); var level=h.getLevel(); var owner=player(h,"EmptyCrafter");
        var pos=h.absolutePos(new BlockPos(3,1,3)); h.setBlock(new BlockPos(3,1,3),ThaumcraftBlocks.CRUCIBLE.get());
        var crucible=(CrucibleBlockEntity)level.getBlockEntity(pos); var tag=new CompoundTag(); tag.putInt("Water",1000); tag.putShort("Heat",(short)200);
        crucible.loadWithComponents(tag,level.registryAccess()); var crystals=CrystalEssenceItem.ofAspect("vacuos"); crystals.setCount(10);
        int dissolved=crucible.dissolve(crystals,10); crystals.shrink(dissolved);
        h.assertTrue(dissolved==10 && crystals.isEmpty(),"Actual Vacuos crystals dissolved");
        var blank=new ItemStack(GolemModule.BLANK_SEAL.get());
        h.assertTrue(crucible.attemptSmelt(blank,1,owner)==0 && crucible.waterMb()==1000,"SEALEMPTY gate enforced");
        owner.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge().addResearch("SEALEMPTY");
        Consumer<EntityJoinLevelEvent> reject=e->{ if(e.getLevel()==level && e.getEntity() instanceof ItemEntity item && item.getItem().is(GolemModule.EMPTY_SEAL.get())) e.setCanceled(true); };
        NeoForge.EVENT_BUS.addListener(reject);
        try { h.assertTrue(crucible.attemptSmelt(blank,1,owner)==0 && crucible.waterMb()==1000,"Rejected Empty output rolls resources back"); }
        finally { NeoForge.EVENT_BUS.unregister(reject); }
        int paid=crucible.attemptSmelt(blank,1,owner); blank.shrink(paid);
        h.assertTrue(paid==1 && blank.isEmpty() && crucible.waterMb()==950,"One blank,10Vacuos,50mB spent");
        var drops=level.getEntitiesOfClass(ItemEntity.class,h.getBounds());
        h.assertTrue(drops.size()==1 && drops.getFirst().getItem().is(GolemModule.EMPTY_SEAL.get()),"One real Empty output"); drops.getFirst().discard();
        owner.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(GolemModule.EMPTY_SEAL.get(),2));
        var stone=h.absolutePos(new BlockPos(6,0,6)); owner.moveTo(Vec3.atBottomCenterOf(stone.above()));
        h.assertTrue(!GolemModule.EMPTY_SEAL.get().useOn(context(owner,stone,Direction.UP)).consumesAction()
                && owner.getMainHandItem().getCount()==2,"Empty cannot be placed without sided ItemHandler");
        h.setBlock(new BlockPos(6,1,6),Blocks.BARREL); var support=stone.above(); owner.moveTo(Vec3.atBottomCenterOf(support.above()));
        h.assertTrue(GolemModule.EMPTY_SEAL.get().useOn(context(owner,support,Direction.UP)).consumesAction()
                && owner.getMainHandItem().getCount()==1,"Actual item places typed Empty and spends one");
        var data=GolemSeals.get(level); var key=new GolemSeals.SealKey(support,Direction.UP);
        var menu=new GolemSealMenu(21,owner.getInventory(),level,data.get(key));
        h.assertTrue(!menu.hasArea() && !menu.hasLimit() && !menu.clickMenuButton(owner,91),"Empty uses filter/controls, not area or Fill cap");
        owner.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(GolemModule.GOLEM_BELL.get())); owner.setShiftKeyDown(true);
        h.assertTrue(GolemModule.GOLEM_BELL.get().useOn(context(owner,support,Direction.UP)).consumesAction() && !data.contains(support,Direction.UP),"Bell removes exact Empty seal");
        drops=level.getEntitiesOfClass(ItemEntity.class,h.getBounds());
        h.assertTrue(drops.size()==1 && drops.getFirst().getItem().is(GolemModule.EMPTY_SEAL.get()) && drops.getFirst().getItem().getCount()==1,"Exactly one typed Empty refund");
        owner.setShiftKeyDown(false); owner.getInventory().clearContent(); h.succeed();
    }

    @GameTest(template="golem_test", batch="empty_sides", timeoutTicks=40)
    public static void furnaceExtractionRespectsSideAndStrictComponents(GameTestHelper h) {
        floor(h); var level=h.getLevel(); var owner=player(h,"EmptySides"); var worker=worker(h,owner,new BlockPos(5,1,6),false);
        h.setBlock(new BlockPos(6,1,6),Blocks.FURNACE); var pos=h.absolutePos(new BlockPos(6,1,6));
        var furnace=(FurnaceBlockEntity)level.getBlockEntity(pos); furnace.setItem(0,new ItemStack(Items.RAW_IRON,3)); furnace.setItem(1,new ItemStack(Items.COAL,4)); furnace.setItem(2,cargo(7));
        var top=new GolemSeals.SealKey(pos,Direction.UP); var bottom=new GolemSeals.SealKey(pos,Direction.DOWN);
        h.assertTrue(GolemContainers.emptyCandidate(level,top,SealFilter.defaults()).is(Items.RAW_IRON),"Vanilla top exposes raw input, not output");
        var named=GolemContainers.emptyCandidate(level,bottom,SealFilter.defaults());
        h.assertTrue(ItemStack.matches(named,cargo(7)),"Bottom exposes actual named output");
        h.assertTrue(GolemContainers.extractInto(worker,GolemContainers.handler(level,top),named,SealFilter.defaults())==0
                && worker.getMainHandItem().isEmpty(),"Top cannot extract the cached output from another side");
        var wrong=new SealFilter(new ItemStack(Items.EMERALD),false,0);
        h.assertTrue(GolemContainers.emptyCandidate(level,bottom,wrong).isEmpty(),"Different components do not satisfy whitelist");
        var policy=new SealFilter(cargo(1),false,1);
        h.assertTrue(GolemContainers.extractInto(worker,GolemContainers.handler(level,bottom),named,policy)==7
                && furnace.getItem(2).isEmpty() && furnace.getItem(0).getCount()==3 && furnace.getItem(1).getCount()==4
                && ItemStack.matches(cargo(7),worker.getMainHandItem()),"Extract exact output; Empty ignores Fill stock limit and leaves input/fuel intact");
        h.assertTrue(GolemContainers.emptyCandidate(level,bottom,SealFilter.defaults()).isEmpty(),"Vanilla bottom refuses extraction of remaining coal fuel");
        h.succeed();
    }

    @GameTest(template="golem_test", batch="empty_workers", timeoutTicks=90)
    public static void twoCachedTasksRecountStockAndNeverDuplicateNamedCargo(GameTestHelper h) {
        floor(h); var level=h.getLevel(); var owner=player(h,"EmptyPair");
        var key=seal(h,owner,new BlockPos(6,1,6),GolemSeals.Type.EMPTY); handler(h,key).setStackInSlot(0,cargo(10));
        var data=GolemSeals.get(level); var a=worker(h,owner,new BlockPos(5,1,6),false); var b=worker(h,owner,new BlockPos(6,1,5),false);
        h.runAfterDelay(45,()->{
            var first=data.claimEmpty(a,ignored->true).orElseThrow(); var second=data.claimEmpty(b,ignored->true).orElseThrow();
            h.assertTrue(!first.lease().equals(second.lease()),"Different cached tasks reserved by different workers");
            h.assertTrue(data.empty(level,a,first) && !data.empty(level,a,first),"First completion takes ten once");
            h.assertTrue(!data.empty(level,b,second) && b.getMainHandItem().isEmpty() && stock(h,key)==0
                    && ItemStack.matches(cargo(10),a.getMainHandItem()),"Second cached snapshot cannot fabricate consumed stock");
            data.releaseBlock(level,b,second,false); data.remove(key.pos(),key.face(),owner.getUUID()); h.succeed();
        });
    }

    @GameTest(template="golem_test", batch="empty_actual", timeoutTicks=40)
    public static void actualExtractionPayloadAndRecoverySurviveNbtAndResumeAfterFill(GameTestHelper h) {
        floor(h); var owner=player(h,"EmptyActual"); var worker=worker(h,owner,new BlockPos(5,1,6),false);
        var partial=new ItemStackHandler(1) {
            @Override public ItemStack extractItem(int slot,int amount,boolean simulate) { return super.extractItem(slot,simulate?amount:Math.min(amount,2),simulate); }
        };
        partial.setStackInSlot(0,cargo(10));
        h.assertTrue(GolemContainers.extractInto(worker,partial,cargo(10),SealFilter.defaults())==2
                && partial.getStackInSlot(0).getCount()==8 && ItemStack.matches(cargo(2),worker.getMainHandItem()),"Actual two overrides simulated ten without aliases");
        worker.setItemSlot(EquipmentSlot.MAINHAND,cargo(60));
        var changed=new ItemStackHandler(1) {
            @Override public ItemStack extractItem(int slot,int amount,boolean simulate) {
                if(!simulate) setStackInSlot(slot,new ItemStack(Items.DIAMOND,3));
                return super.extractItem(slot,amount,simulate);
            }
        };
        changed.setStackInSlot(0,cargo(4));
        h.assertTrue(GolemContainers.extractInto(worker,changed,cargo(4),SealFilter.defaults())==3 && changed.getStackInSlot(0).isEmpty()
                && ItemStack.matches(cargo(60),worker.getMainHandItem()) && worker.transferRecovery().is(Items.DIAMOND)
                && worker.transferRecovery().getCount()==3,"Unexpected actual type retained in owned recovery, no synthetic emeralds");
        CompoundTag saved=new CompoundTag(); worker.addAdditionalSaveData(saved);
        var restored=GolemModule.GOLEM.get().create(h.getLevel()); restored.readAdditionalSaveData(saved);
        h.assertTrue(ItemStack.matches(worker.transferRecovery(),restored.transferRecovery()) && ItemStack.matches(cargo(60),restored.getMainHandItem()),"Both normal and recovery cargo persisted once");
        var more=new ItemStackHandler(1); more.setStackInSlot(0,cargo(5));
        h.assertTrue(GolemContainers.extractInto(worker,more,cargo(5),SealFilter.defaults())==0 && more.getStackInSlot(0).getCount()==5,"Recovery blocks another extraction");
        var destination=new ItemStackHandler(1);
        h.assertTrue(GolemContainers.insertCarried(worker,destination)==60,"Existing Fill drains normal cargo");
        h.runAfterDelay(2,()->{
            h.assertTrue(!worker.hasTransferRecovery() && worker.getMainHandItem().is(Items.DIAMOND) && worker.getMainHandItem().getCount()==3,"Recovery moves into free hands on actual server tick");
            worker.addAdditionalSaveData(saved);
            h.assertTrue(!saved.contains("transferRecovery") && h.getLevel().getEntitiesOfClass(ItemEntity.class,h.getBounds()).isEmpty(),"No stale recovery NBT or ground copy"); h.succeed();
        });
    }

    @GameTest(template="golem_test", batch="empty_reclaim", timeoutTicks=40)
    public static void rejectedReclaimRetainsRecoveryAndRetryDropsEachOwnedStackOnce(GameTestHelper h) {
        floor(h); var level=h.getLevel(); var owner=player(h,"EmptyRecovery"); var worker=worker(h,owner,new BlockPos(5,1,6),false);
        worker.holdItem(cargo(7)); worker.holdExtracted(new ItemStack(Items.DIAMOND,3)); owner.moveTo(worker.position()); owner.setShiftKeyDown(true);
        Consumer<EntityJoinLevelEvent> reject=e->{ if(e.getLevel()==level && e.getEntity() instanceof ItemEntity item && item.getItem().is(Items.DIAMOND)) e.setCanceled(true); };
        NeoForge.EVENT_BUS.addListener(reject);
        try { h.assertTrue(!worker.interact(owner,InteractionHand.MAIN_HAND).consumesAction() && worker.isAlive()
                    && worker.getMainHandItem().isEmpty() && worker.transferRecovery().getCount()==3,"Rejected recovery drop preserves it; already accepted cargo no longer in hands"); }
        finally { NeoForge.EVENT_BUS.unregister(reject); }
        h.assertTrue(worker.interact(owner,InteractionHand.MAIN_HAND).consumesAction() && worker.isRemoved(),"Retry completes reclaim");
        var drops=level.getEntitiesOfClass(ItemEntity.class,h.getBounds());
        h.assertTrue(drops.size()==3 && drops.stream().filter(e->ItemStack.matches(cargo(7),e.getItem())).count()==1
                && drops.stream().filter(e->e.getItem().is(Items.DIAMOND) && e.getItem().getCount()==3).count()==1
                && drops.stream().filter(e->e.getItem().is(GolemModule.GOLEM_PLACER.get())).count()==1,"Cargo/recovery/placer each emitted exactly once");
        owner.setShiftKeyDown(false); h.succeed();
    }

    @GameTest(template="golem_test", batch="empty_authority", timeoutTicks=110)
    public static void colorRedstoneModeAndRemovedContainerInvalidateEmptyWithoutTakingItems(GameTestHelper h) {
        floor(h); var level=h.getLevel(); var owner=player(h,"EmptyControl"); var data=GolemSeals.get(level);
        var key=seal(h,owner,new BlockPos(6,1,6),GolemSeals.Type.EMPTY); handler(h,key).setStackInSlot(0,cargo(7));
        data.configureControls(data.get(key),owner.getUUID(),0,15,true,true); var worker=worker(h,owner,new BlockPos(5,1,6),false); worker.setGolemColor((byte)12);
        h.runAfterDelay(25,()->{
            h.assertTrue(data.claimEmpty(worker,ignored->true).isEmpty(),"Wrong color refused"); worker.setGolemColor((byte)15);
            var old=data.claimEmpty(worker,ignored->true).orElseThrow(); worker.setGolemColor((byte)12);
            h.assertTrue(!data.empty(level,worker,old) && stock(h,key)==7,"Recolor blocks completion"); data.releaseBlock(level,worker,old,false); worker.setGolemColor((byte)15);
            var signal=data.claimEmpty(worker,ignored->true).orElseThrow(); level.setBlockAndUpdate(key.pos().east(),Blocks.REDSTONE_BLOCK.defaultBlockState());
            h.assertTrue(!data.empty(level,worker,signal) && stock(h,key)==7,"Late support signal blocks extraction"); GolemSeals.tick(level);
            level.setBlockAndUpdate(key.pos().east(),Blocks.AIR.defaultBlockState()); h.assertTrue(data.emptyTarget(level,worker,signal)==null,"Signal off never revives old lease");
        });
        h.runAfterDelay(50,()->{
            var lease=data.claimEmpty(worker,ignored->true).orElseThrow(); worker.setFollowingOwner(true);
            h.assertTrue(!data.empty(level,worker,lease) && data.claimEmpty(worker,ignored->true).isEmpty() && stock(h,key)==7,"Follow mode pauses extraction and preserves source");
            worker.setFollowingOwner(false); data.releaseBlock(level,worker,lease,false);
            var fresh=data.claimEmpty(worker,ignored->true).orElseThrow();
            var redirected=new GolemSeals.BlockAssignment(fresh.lease(),new GolemSeals.SealKey(key.pos(),Direction.DOWN));
            h.assertTrue(!data.empty(level,worker,redirected),"Lease cannot redirect to another face");
            level.setBlockAndUpdate(key.pos(),Blocks.STONE.defaultBlockState());
            h.assertTrue(!data.empty(level,worker,fresh) && worker.getMainHandItem().isEmpty(),"Removed handler cannot fabricate cached cargo");
            data.remove(key.pos(),key.face(),owner.getUUID()); h.succeed();
        });
    }

    @GameTest(template="golem_test", batch="empty_death", timeoutTicks=60)
    public static void deathDropsNormalAndRecoveryCargoOnce(GameTestHelper h) {
        floor(h); var level=h.getLevel(); var owner=player(h,"EmptyDeath"); var worker=worker(h,owner,new BlockPos(5,1,6),false);
        worker.holdItem(cargo(7)); worker.holdExtracted(new ItemStack(Items.DIAMOND,3));
        h.assertTrue(worker.hurt(level.damageSources().generic(),1000),"Actual lethal damage");
        h.runAfterDelay(25,()->{
            var drops=level.getEntitiesOfClass(ItemEntity.class,h.getBounds());
            h.assertTrue(worker.isRemoved() && drops.size()==2 && drops.stream().filter(e->ItemStack.matches(cargo(7),e.getItem())).count()==1
                    && drops.stream().filter(e->e.getItem().is(Items.DIAMOND) && e.getItem().getCount()==3).count()==1,"One normal stack and one retained actual stack, no duplicates"); h.succeed();
        });
    }

    @GameTest(template="golem_test", batch="empty_priority", timeoutTicks=70)
    public static void emptyAndFillCompeteInOnePrioritySortedBlockQueue(GameTestHelper h) {
        floor(h); var level=h.getLevel(); var owner=player(h,"EmptyPriority"); var data=GolemSeals.get(level);
        var empty=seal(h,owner,new BlockPos(6,1,6),GolemSeals.Type.EMPTY); handler(h,empty).setStackInSlot(0,cargo(9));
        var fill=seal(h,owner,new BlockPos(4,1,6),GolemSeals.Type.FILL);
        data.configureControls(data.get(empty),owner.getUUID(),5,0,true,false); data.configureControls(data.get(fill),owner.getUUID(),-5,0,true,false);
        var worker=worker(h,owner,new BlockPos(5,1,6),false); worker.holdItem(cargo(7));
        h.runAfterDelay(25,()->{
            var high=data.claimBlock(worker,ignored->true).orElseThrow();
            h.assertTrue(high.seal().equals(empty) && data.completeBlock(level,worker,high) && worker.getMainHandItem().getCount()==16,"Higher Empty priority beats available Fill");
            data.configureControls(data.get(empty),owner.getUUID(),-5,0,true,false); data.configureControls(data.get(fill),owner.getUUID(),5,0,true,false);
            handler(h,empty).setStackInSlot(0,cargo(9)); // Keep both kinds of work available for the second priority choice.
        });
        h.runAfterDelay(50,()->{
            var next=data.claimBlock(worker,ignored->true).orElseThrow();
            h.assertTrue(next.seal().equals(fill) && data.completeBlock(level,worker,next) && stock(h,fill)==16 && stock(h,empty)==9,"Same queue routes actual cargo to higher Fill while Empty remains available");
            data.remove(empty.pos(),empty.face(),owner.getUUID()); data.remove(fill.pos(),fill.face(),owner.getUUID()); h.succeed();
        });
    }

    @GameTest(template="golem_test", batch="empty_cycle", timeoutTicks=400)
    public static void twoWorkersNavigateSourceToDestinationAndConserveEveryItemEachTick(GameTestHelper h) {
        floor(h); var level=h.getLevel(); var owner=player(h,"EmptyRoute"); var data=GolemSeals.get(level);
        var source=seal(h,owner,new BlockPos(4,1,6),GolemSeals.Type.EMPTY); handler(h,source).setStackInSlot(0,cargo(64)); handler(h,source).setStackInSlot(1,cargo(6));
        var target=seal(h,owner,new BlockPos(10,1,6),GolemSeals.Type.FILL);
        var a=worker(h,owner,new BlockPos(2,1,5),true); var b=worker(h,owner,new BlockPos(2,1,7),true);
        data.configure(data.get(source),owner.getUUID(),new SealFilter(cargo(1),false,0));
        data.configure(data.get(target),owner.getUUID(),new SealFilter(cargo(1),false,0));
        h.onEachTick(()->h.assertTrue(stock(h,source)+stock(h,target)+held(a)+held(b)==70,"Container→hands→container conserves seventy named items every tick"));
        h.startSequence().thenIdle(300).thenExecute(()->{
            h.assertTrue(stock(h,source)==0 && stock(h,target)==70 && held(a)==0 && held(b)==0,"Two autonomous workers deliver source exactly once");
            for(int i=0;i<handler(h,target).getSlots();i++) { var stack=handler(h,target).getStackInSlot(i); if(!stack.isEmpty()) h.assertTrue(ItemStack.isSameItemSameComponents(stack,cargo(1)),"Components survive whole route"); }
            h.assertTrue(level.getEntitiesOfClass(ItemEntity.class,h.getBounds()).isEmpty(),"No duplicate ground cargo");
            data.remove(source.pos(),source.face(),owner.getUUID()); data.remove(target.pos(),target.face(),owner.getUUID());
        }).thenSucceed();
    }

    private static ItemStack cargo(int n) { var stack=new ItemStack(Items.EMERALD,n); stack.set(DataComponents.CUSTOM_NAME,Component.literal("M10 Empty cargo")); return stack; }
    private static FakePlayer player(GameTestHelper h,String name) { var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),name)); p.setGameMode(GameType.SURVIVAL); return p; }
    private static ThaumcraftGolem worker(GameTestHelper h,FakePlayer owner,BlockPos relative,boolean ai) {
        var e=GolemModule.GOLEM.get().create(h.getLevel()); var pos=h.absolutePos(relative); e.initialize(GolemDesign.defaultWood(),owner.getUUID(),pos,0);
        e.moveTo(Vec3.atBottomCenterOf(pos)); e.setOnGround(true); e.setNoAi(!ai); h.assertTrue(h.getLevel().addFreshEntity(e),"Actual worker"); return e;
    }
    private static GolemSeals.SealKey seal(GameTestHelper h,FakePlayer owner,BlockPos relative,GolemSeals.Type type) {
        h.setBlock(relative,Blocks.BARREL); var key=new GolemSeals.SealKey(h.absolutePos(relative),Direction.UP);
        var data=GolemSeals.get(h.getLevel());
        h.assertTrue(data.place(key.pos(),key.face(),owner.getUUID(),type),"Typed seal on real barrel");
        h.assertTrue(data.configureControls(data.get(key),owner.getUUID(),0,0,true,false),"Isolate this test's tasks to its owner"); return key;
    }
    private static net.neoforged.neoforge.items.IItemHandlerModifiable handler(GameTestHelper h,GolemSeals.SealKey key) { return (net.neoforged.neoforge.items.IItemHandlerModifiable)GolemContainers.handler(h.getLevel(),key); }
    private static int stock(GameTestHelper h,GolemSeals.SealKey key) { var inv=handler(h,key); int total=0; for(int i=0;i<inv.getSlots();i++) total+=inv.getStackInSlot(i).getCount(); return total; }
    private static int held(ThaumcraftGolem e) { return e.getMainHandItem().getCount()+e.getOffhandItem().getCount()+e.transferRecovery().getCount(); }
    private static UseOnContext context(FakePlayer player,BlockPos pos,Direction face) {
        return new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos).add(face.getStepX()*.5,face.getStepY()*.5,face.getStepZ()*.5),face,pos,false));
    }
    private static void floor(GameTestHelper h) { for(int x=0;x<12;x++) for(int z=0;z<12;z++) h.setBlock(new BlockPos(x,0,z),Blocks.STONE); }
}
