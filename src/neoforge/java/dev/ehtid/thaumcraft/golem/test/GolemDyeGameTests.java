package dev.ehtid.thaumcraft.golem.test;

import com.mojang.authlib.GameProfile;
import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import dev.ehtid.thaumcraft.golem.GolemDyes;
import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.golem.ai.GolemPickupGoal;
import dev.ehtid.thaumcraft.golem.inventory.GolemContainers;
import dev.ehtid.thaumcraft.golem.seal.GolemSeals;
import dev.ehtid.thaumcraft.port.core.golem.GolemDesign;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.UUID;

@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class GolemDyeGameTests {
    private GolemDyeGameTests() {}

    @GameTest(template="golem_test", batch="dye_colors", timeoutTicks=40)
    public static void sixteenTaggedDyesConsumeOneInBothHandsAndCreativeAndPersist(GameTestHelper h) {
        floor(h); var owner=player(h,"DyePalette"); var worker=worker(h,owner,new BlockPos(5,1,6));
        worker.holdItem(cargo()); worker.setFollowingOwner(true); worker.setHealth(7);
        for (DyeColor dye : DyeColor.values()) {
            ItemStack input=new ItemStack(DyeItem.byColor(dye),3);
            InteractionHand hand=dye.getId()%2==0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
            owner.setItemInHand(hand,input);
            h.assertTrue(input.is(dye.getTag()),"Loaded standard color tag: "+dye);
            h.assertTrue(worker.interact(owner,hand).consumesAction(),"Owner dyes without research: "+dye);
            h.assertTrue(worker.golemColor()==dye.getId()+1 && input.getCount()==2,"Source color and exactly one paid dye: "+dye);
        }
        owner.setGameMode(GameType.CREATIVE);
        ItemStack repeat=new ItemStack(Items.BLACK_DYE,3); owner.setItemInHand(InteractionHand.MAIN_HAND,repeat);
        h.assertTrue(worker.interact(owner,InteractionHand.MAIN_HAND).consumesAction() && repeat.getCount()==2
                && worker.golemColor()==16,"Same black dye still consumed in creative");
        h.assertTrue(worker.isFollowingOwner() && worker.home().equals(h.absolutePos(new BlockPos(5,1,6)))
                && worker.getHealth()==7 && ItemStack.matches(cargo(),worker.getMainHandItem()),"Dye changes only color, preserving cargo/components, mode, home and health");
        CompoundTag saved=new CompoundTag(); worker.addAdditionalSaveData(saved);
        var restored=GolemModule.GOLEM.get().create(h.getLevel()); restored.readAdditionalSaveData(saved);
        h.assertTrue(restored.golemColor()==16 && restored.isFollowingOwner() && ItemStack.matches(cargo(),restored.getMainHandItem()),"NBT restores paid color and cargo without another dye");
        owner.getInventory().clearContent(); h.succeed();
    }

    @GameTest(template="golem_test", batch="dye_authority", timeoutTicks=40)
    public static void authorityRejectsInvalidPaintAndSneakingStillReclaimsWithoutSpendingDye(GameTestHelper h) {
        floor(h); var owner=player(h,"DyeOwner"); var worker=worker(h,owner,new BlockPos(5,1,6));
        var stranger=player(h,"DyeStranger"); stranger.moveTo(owner.position());
        stranger.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.RED_DYE,2));
        h.assertTrue(!worker.interact(stranger,InteractionHand.MAIN_HAND).consumesAction()
                && stranger.getMainHandItem().getCount()==2 && worker.golemColor()==0,"Only owner may spend dye or change color");
        owner.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.RED_DYE,2)); owner.moveTo(worker.position().add(6,0,0));
        h.assertTrue(!worker.interact(owner,InteractionHand.MAIN_HAND).consumesAction() && owner.getMainHandItem().getCount()==2,"Remote interaction rejected");
        owner.moveTo(worker.position()); owner.setGameMode(GameType.SPECTATOR);
        h.assertTrue(!worker.interact(owner,InteractionHand.MAIN_HAND).consumesAction() && worker.golemColor()==0,"Spectator cannot paint");
        owner.setGameMode(GameType.SURVIVAL); owner.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.REDSTONE,2));
        h.assertTrue(GolemDyes.color(owner.getMainHandItem())==0 && !worker.interact(owner,InteractionHand.MAIN_HAND).consumesAction()
                && owner.getMainHandItem().getCount()==2 && worker.golemColor()==0,"Red item without a dye tag is not red dye");
        CompoundTag saved=new CompoundTag(); worker.addAdditionalSaveData(saved); saved.putLong("props",-1L);
        var invalid=GolemModule.GOLEM.get().create(h.getLevel()); invalid.readAdditionalSaveData(saved); invalid.moveTo(worker.position());
        owner.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.RED_DYE,2));
        h.assertTrue(!invalid.interact(owner,InteractionHand.MAIN_HAND).consumesAction() && owner.getMainHandItem().getCount()==2,"Quarantined design cannot consume paint");
        worker.holdItem(cargo()); owner.setShiftKeyDown(true);
        h.assertTrue(worker.interact(owner,InteractionHand.MAIN_HAND).consumesAction() && worker.isRemoved()
                && owner.getMainHandItem().getCount()==2,"Sneak retains original reclaim action instead of painting");
        var drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,h.getBounds());
        h.assertTrue(drops.size()==2 && drops.stream().filter(e->e.getItem().is(GolemModule.GOLEM_PLACER.get())).count()==1
                && drops.stream().filter(e->ItemStack.matches(cargo(),e.getItem())).count()==1,"Reclaim emits one placer and exact cargo once");
        h.assertTrue(!worker.interact(owner,InteractionHand.MAIN_HAND).consumesAction() && owner.getMainHandItem().getCount()==2,"Removed worker cannot consume another dye");
        owner.setShiftKeyDown(false); owner.getInventory().clearContent(); stranger.getInventory().clearContent(); h.succeed();
    }

    @GameTest(template="golem_test", batch="dye_pickup", timeoutTicks=70)
    public static void repaintInvalidatesPickupAndGoalReleasesItToAnotherColoredWorker(GameTestHelper h) {
        floor(h); var level=h.getLevel(); var owner=player(h,"DyePickup");
        var worker=worker(h,owner,new BlockPos(2,1,6)); paint(h,owner,worker,Items.RED_DYE);
        var data=GolemSeals.get(level); var key=pickup(h,owner,15);
        ItemEntity item=drop(h,new BlockPos(8,1,6));
        h.runAfterDelay(10,()->{
            var goal=new GolemPickupGoal(worker);
            h.assertTrue(goal.canUse(),"Red worker claims red pickup"); goal.start();
            h.assertTrue(worker.hasWorkTask() && goal.canContinueToUse(),"Real pickup goal reserved before arrival");
            paint(h,owner,worker,Items.BLUE_DYE);
            h.assertTrue(!goal.canContinueToUse() && item.isAlive() && ItemStack.matches(cargo(),item.getItem())
                    && worker.getMainHandItem().isEmpty(),"New color rejects old goal without taking source cargo");
            goal.stop(); h.assertTrue(!worker.hasWorkTask(),"Normal AI stop releases incompatible assignment");
            var next=worker(h,owner,new BlockPos(8,1,6)); paint(h,owner,next,Items.RED_DYE);
            var fresh=data.claim(next,ignored->true).orElseThrow();
            h.assertTrue(data.pickup(level,next,fresh) && !item.isAlive() && ItemStack.matches(cargo(),next.getMainHandItem()),"Another red worker collects exact released cargo once");
            paint(h,owner,worker,Items.RED_DYE);
            h.assertTrue(data.claim(worker,ignored->true).isEmpty() && worker.getMainHandItem().isEmpty(),"Repainting back cannot replay completed pickup");
            data.remove(key.pos(),key.face(),owner.getUUID()); h.succeed();
        });
    }

    @GameTest(template="golem_test", batch="dye_fill", timeoutTicks=70)
    public static void repaintRechecksFillAtCompletionAndOldLeaseCannotRevive(GameTestHelper h) {
        floor(h); var level=h.getLevel(); var owner=player(h,"DyeFill");
        var worker=worker(h,owner,new BlockPos(6,1,5)); paint(h,owner,worker,Items.RED_DYE); worker.holdItem(cargo());
        var data=GolemSeals.get(level); var key=fill(h,owner,new BlockPos(6,1,6),15,0);
        h.runAfterDelay(25,()->{
            var old=data.claimFill(worker,ignored->true).orElseThrow();
            paint(h,owner,worker,Items.RED_DYE);
            h.assertTrue(data.fillTarget(level,worker,old)!=null,"Repeating the same color does not interrupt compatible work");
            paint(h,owner,worker,Items.BLUE_DYE);
            h.assertTrue(data.fillTarget(level,worker,old)==null && !data.fill(level,worker,old)
                    && ItemStack.matches(cargo(),worker.getMainHandItem()) && stored(h,key)==0,"Late repaint blocks deposit without changing cargo or destination");
            data.releaseFill(level,worker,old,false); paint(h,owner,worker,Items.RED_DYE);
            h.assertTrue(data.fillTarget(level,worker,old)==null && !data.fill(level,worker,old),"Released lease stays invalid when color becomes eligible again");
            var fresh=data.claimFill(worker,ignored->true).orElseThrow();
            h.assertTrue(data.fill(level,worker,fresh) && worker.getMainHandItem().isEmpty() && stored(h,key)==7,"Fresh matching assignment deposits exactly seven");
            data.remove(key.pos(),key.face(),owner.getUUID()); h.succeed();
        });
    }

    @GameTest(template="golem_test", batch="dye_cycle", timeoutTicks=300)
    public static void actualNavigationRoutesPaintedWorkerToMatchingSeals(GameTestHelper h) {
        floor(h); var owner=player(h,"DyeRoute"); var worker=worker(h,owner,new BlockPos(2,1,6));
        var data=GolemSeals.get(h.getLevel()); var pickup=pickup(h,owner,15);
        var red=fill(h,owner,new BlockPos(9,1,6),15,-5);
        var blue=fill(h,owner,new BlockPos(5,1,9),12,5);
        var item=drop(h,new BlockPos(4,1,6)); paint(h,owner,worker,Items.BLUE_DYE); worker.setNoAi(false);
        h.runAfterDelay(25,()->{
            h.assertTrue(item.isAlive() && ItemStack.matches(cargo(),item.getItem()) && worker.getMainHandItem().isEmpty(),"Wrong color leaves actual pickup untouched");
            paint(h,owner,worker,Items.RED_DYE);
        });
        h.onEachTick(()->{
            int source=item.isAlive()?item.getItem().getCount():0;
            h.assertTrue(source+worker.getMainHandItem().getCount()+worker.getOffhandItem().getCount()+stored(h,red)+stored(h,blue)==7,"Every tick conserves all seven items during recolor and navigation");
            h.assertTrue(stored(h,blue)==0,"Higher-priority wrong-color destination receives nothing");
        });
        h.startSequence().thenIdle(200).thenExecute(()->{
            h.assertTrue(stored(h,red)==7 && worker.getMainHandItem().isEmpty() && !item.isAlive(),"Real Pickup/path/Fill cycle reaches matching lower-priority barrel");
            var handler=GolemContainers.handler(h.getLevel(),red);
            h.assertTrue(ItemStack.matches(cargo(),handler.getStackInSlot(0)),"Named cargo components reach the selected destination");
            data.remove(pickup.pos(),pickup.face(),owner.getUUID()); data.remove(red.pos(),red.face(),owner.getUUID()); data.remove(blue.pos(),blue.face(),owner.getUUID());
        }).thenSucceed();
    }

    private static void paint(GameTestHelper h,FakePlayer owner,ThaumcraftGolem worker,net.minecraft.world.item.Item dye) {
        owner.moveTo(worker.position()); owner.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(dye,2));
        h.assertTrue(worker.interact(owner,InteractionHand.MAIN_HAND).consumesAction() && owner.getMainHandItem().getCount()==1,"Actual paid owner interaction");
    }
    private static FakePlayer player(GameTestHelper h,String name) {
        var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),name)); p.setGameMode(GameType.SURVIVAL); p.setShiftKeyDown(false); return p;
    }
    private static ThaumcraftGolem worker(GameTestHelper h,FakePlayer owner,BlockPos relative) {
        var e=GolemModule.GOLEM.get().create(h.getLevel()); h.assertTrue(e!=null,"Registered factory");
        var pos=h.absolutePos(relative); e.initialize(GolemDesign.defaultWood(),owner.getUUID(),pos,0);
        e.moveTo(Vec3.atBottomCenterOf(pos)); e.setOnGround(true); e.setNoAi(true); owner.moveTo(e.position());
        h.assertTrue(h.getLevel().addFreshEntity(e),"Real worker added"); return e;
    }
    private static GolemSeals.SealKey pickup(GameTestHelper h,FakePlayer owner,int color) {
        var key=new GolemSeals.SealKey(h.absolutePos(new BlockPos(6,0,6)),Direction.UP); var data=GolemSeals.get(h.getLevel());
        h.assertTrue(data.place(key.pos(),key.face(),owner.getUUID()) && data.configureControls(data.get(key),owner.getUUID(),0,color,true,false),"Actual colored pickup seal"); return key;
    }
    private static GolemSeals.SealKey fill(GameTestHelper h,FakePlayer owner,BlockPos pos,int color,int priority) {
        h.setBlock(pos,Blocks.BARREL); var key=new GolemSeals.SealKey(h.absolutePos(pos),Direction.UP); var data=GolemSeals.get(h.getLevel());
        h.assertTrue(data.place(key.pos(),key.face(),owner.getUUID(),GolemSeals.Type.FILL)
                && data.configureControls(data.get(key),owner.getUUID(),priority,color,true,false),"Actual colored fill seal"); return key;
    }
    private static ItemStack cargo() { var s=new ItemStack(Items.EMERALD,7); s.set(DataComponents.CUSTOM_NAME,Component.literal("M10 dyed route")); return s; }
    private static ItemEntity drop(GameTestHelper h,BlockPos pos) {
        var p=Vec3.atBottomCenterOf(h.absolutePos(pos)); var e=new ItemEntity(h.getLevel(),p.x,p.y,p.z,cargo());
        e.setNoPickUpDelay(); e.setDeltaMovement(Vec3.ZERO); e.setOnGround(true); h.assertTrue(h.getLevel().addFreshEntity(e),"Real cargo source"); return e;
    }
    private static int stored(GameTestHelper h,GolemSeals.SealKey key) {
        var handler=GolemContainers.handler(h.getLevel(),key); int n=0;
        for(int i=0;i<handler.getSlots();i++) n+=handler.getStackInSlot(i).getCount(); return n;
    }
    private static void floor(GameTestHelper h) { for(int x=0;x<12;x++) for(int z=0;z<12;z++) h.setBlock(new BlockPos(x,0,z),Blocks.STONE); }
}
