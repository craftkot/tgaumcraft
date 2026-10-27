package dev.ehtid.thaumcraft.golem.test;

import com.mojang.authlib.GameProfile;
import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.golem.inventory.GolemContainers;
import dev.ehtid.thaumcraft.golem.seal.GolemSeals;
import dev.ehtid.thaumcraft.golem.seal.SealFilter;
import dev.ehtid.thaumcraft.port.core.golem.GolemDesign;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Real synchronous callbacks must transfer owned payloads without replaying cargo snapshots. */
@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class GolemTransferOwnershipGameTests {
    private GolemTransferOwnershipGameTests() {}

    @GameTest(template="golem_test", batch="transfer_ownership", timeoutTicks=40)
    public static void fallbackSpawnCannotRecursivelyDepositTheSameCargo(GameTestHelper h) {
        var f=fixture(h);f.golem.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.PAPER,9));
        var entered=new AtomicBoolean();var nested=new AtomicInteger(-1);
        Consumer<EntityJoinLevelEvent> reenter=e->{
            if(paperJoin(h,e)&&entered.compareAndSet(false,true))
                nested.set(GolemContainers.deposit(h.getLevel(),f.golem,f.target));
        };
        int moved;
        NeoForge.EVENT_BUS.addListener(reenter);
        try { moved=GolemContainers.deposit(h.getLevel(),f.golem,f.target); }
        finally { NeoForge.EVENT_BUS.unregister(reenter);cleanup(f); }
        h.assertTrue(entered.get()&&nested.get()==0&&moved==9,"Actual spawn callback cannot reenter the same worker's deposit; nested="+nested.get());
        h.assertTrue(cargo(f.golem,Items.PAPER)==0&&ground(h,Items.PAPER)==9&&drops(h).size()==1,
                "Exactly one accepted paper9 entity owns the original cargo");
        h.succeed();
    }

    @GameTest(template="golem_test", batch="transfer_ownership", timeoutTicks=40)
    public static void cancelledFallbackReturnsTheActualChangedPayload(GameTestHelper h) {
        var f=fixture(h);f.golem.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.PAPER,9));
        var rejected=new AtomicReference<ItemEntity>();
        Consumer<EntityJoinLevelEvent> cancel=e->{
            if(paperJoin(h,e)) {
                var item=(ItemEntity)e.getEntity();rejected.set(item);
                item.getItem().set(DataComponents.CUSTOM_NAME,Component.literal("Changed rejected fallback"));e.setCanceled(true);
            }
        };
        int moved;
        NeoForge.EVENT_BUS.addListener(cancel);
        try { moved=GolemContainers.deposit(h.getLevel(),f.golem,f.target); }
        finally { NeoForge.EVENT_BUS.unregister(cancel);cleanup(f); }
        h.assertTrue(moved==0&&rejected.get()!=null&&f.golem.isAlive(),"Actual fallback publication was cancelled");
        h.assertTrue(ItemStack.matches(f.golem.getMainHandItem(),named(Items.PAPER,9,"Changed rejected fallback"))
                &&rejected.get().getItem().isEmpty()&&drops(h).isEmpty(),
                "Changed actual paper9 returns once and the rejected entity retains no payload");
        h.succeed();
    }

    @GameTest(template="golem_test", batch="transfer_ownership", timeoutTicks=40)
    public static void insertionCallbackCannotExtractIntoTheSameWorker(GameTestHelper h) {
        var f=fixture(h);f.golem.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.DIAMOND,10));
        var source=new ItemStackHandler(1);source.setStackInSlot(0,new ItemStack(Items.GOLD_INGOT,3));
        var entered=new AtomicBoolean();var nested=new AtomicInteger(-1);
        var destination=new ItemStackHandler(1) {
            @Override protected void onContentsChanged(int slot) {
                if(entered.compareAndSet(false,true))
                    nested.set(GolemContainers.extractInto(f.golem,source,new ItemStack(Items.GOLD_INGOT,3),SealFilter.defaults()));
            }
        };
        int moved;
        try { moved=GolemContainers.insertCarried(f.golem,destination); }
        finally { cleanup(f); }
        h.assertTrue(entered.get()&&nested.get()==0&&moved==10,"Actual insertion callback cannot start nested extraction; nested="+nested.get());
        h.assertTrue(source.getStackInSlot(0).is(Items.GOLD_INGOT)&&source.getStackInSlot(0).getCount()==3
                &&destination.getStackInSlot(0).is(Items.DIAMOND)&&destination.getStackInSlot(0).getCount()==10
                &&allCargo(f.golem)==0&&drops(h).isEmpty(),"Source gold3 and accepted diamond10 survive without overwritten callback cargo");
        h.succeed();
    }

    @GameTest(template="golem_test", batch="transfer_ownership", timeoutTicks=40)
    public static void partialInsertionRetainsReturnedRemainderComponents(GameTestHelper h) {
        var f=fixture(h);f.golem.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.DIAMOND,10));
        var destination=new ItemStackHandler(1) {
            @Override public int getSlotLimit(int slot) { return 2; }
            @Override public ItemStack insertItem(int slot,ItemStack input,boolean simulate) {
                var remainder=super.insertItem(slot,input,simulate);
                if(!simulate&&!remainder.isEmpty())remainder.set(DataComponents.CUSTOM_NAME,Component.literal("Actual returned remainder"));
                return remainder;
            }
        };
        int moved;
        try { moved=GolemContainers.insertCarried(f.golem,destination); }
        finally { cleanup(f); }
        h.assertTrue(moved==2&&destination.getStackInSlot(0).is(Items.DIAMOND)&&destination.getStackInSlot(0).getCount()==2,
                "Real slot capacity of two accepts only two of the ten diamonds");
        h.assertTrue(ItemStack.matches(f.golem.getMainHandItem(),named(Items.DIAMOND,8,"Actual returned remainder"))
                &&allCargo(f.golem)==8&&drops(h).isEmpty(),"Actual returned eight retain their changed components without snapshot reconstruction");
        h.succeed();
    }

    @GameTest(template="golem_test", batch="transfer_ownership", timeoutTicks=40)
    public static void fallbackSpawnCannotReclaimTheTransferringWorker(GameTestHelper h) {
        var f=fixture(h);f.golem.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.PAPER,9));f.owner.setShiftKeyDown(true);
        var entered=new AtomicBoolean();var nested=new AtomicReference<InteractionResult>();
        Consumer<EntityJoinLevelEvent> reclaim=e->{
            if(paperJoin(h,e)&&entered.compareAndSet(false,true))nested.set(f.golem.interact(f.owner,InteractionHand.MAIN_HAND));
        };
        int moved;
        NeoForge.EVENT_BUS.addListener(reclaim);
        try { moved=GolemContainers.deposit(h.getLevel(),f.golem,f.target); }
        finally { NeoForge.EVENT_BUS.unregister(reclaim);cleanup(f); }
        h.assertTrue(entered.get()&&nested.get()!=null&&!nested.get().consumesAction()&&moved==9&&f.golem.isAlive(),
                "Owner reclaim refuses while actual cargo publication is in progress");
        h.assertTrue(ground(h,Items.PAPER)==9&&ground(h,GolemModule.GOLEM_PLACER.get())==0&&drops(h).size()==1
                &&allCargo(f.golem)==0,"Only paper9 is published; live worker is not minted into a placer");
        h.succeed();
    }

    @GameTest(template="golem_test", batch="transfer_ownership", timeoutTicks=40)
    public static void reclaimSpawnCannotRecursivelyReclaimTheSameWorker(GameTestHelper h) {
        var f=fixture(h);f.golem.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.PAPER,9));f.owner.setShiftKeyDown(true);
        var entered=new AtomicBoolean();var nested=new AtomicReference<InteractionResult>();
        Consumer<EntityJoinLevelEvent> reclaim=e->{
            if(paperJoin(h,e)&&entered.compareAndSet(false,true))nested.set(f.golem.interact(f.owner,InteractionHand.MAIN_HAND));
        };
        InteractionResult result;
        NeoForge.EVENT_BUS.addListener(reclaim);
        try { result=f.golem.interact(f.owner,InteractionHand.MAIN_HAND); }
        finally { NeoForge.EVENT_BUS.unregister(reclaim);cleanup(f); }
        h.assertTrue(result.consumesAction()&&entered.get()&&nested.get()!=null&&!nested.get().consumesAction()&&f.golem.isRemoved(),
                "One owner reclaim completes and its synchronous recursive reclaim refuses");
        h.assertTrue(ground(h,Items.PAPER)==9&&ground(h,GolemModule.GOLEM_PLACER.get())==1&&drops(h).size()==2,
                "Original paper9 and one placer are each published once");
        h.succeed();
    }

    @GameTest(template="golem_test", batch="transfer_ownership", timeoutTicks=40)
    public static void cancelledReclaimRetainsTheActualChangedCargo(GameTestHelper h) {
        var f=fixture(h);f.golem.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.PAPER,9));f.owner.setShiftKeyDown(true);
        var rejected=new AtomicReference<ItemEntity>();
        Consumer<EntityJoinLevelEvent> cancel=e->{
            if(paperJoin(h,e)) {
                var item=(ItemEntity)e.getEntity();rejected.set(item);
                item.getItem().set(DataComponents.CUSTOM_NAME,Component.literal("Changed rejected reclaim"));e.setCanceled(true);
            }
        };
        InteractionResult result;
        NeoForge.EVENT_BUS.addListener(cancel);
        try { result=f.golem.interact(f.owner,InteractionHand.MAIN_HAND); }
        finally { NeoForge.EVENT_BUS.unregister(cancel);cleanup(f); }
        h.assertTrue(!result.consumesAction()&&rejected.get()!=null&&f.golem.isAlive(),"Actual canceled cargo spawn aborts reclaim");
        h.assertTrue(ItemStack.matches(f.golem.getMainHandItem(),named(Items.PAPER,9,"Changed rejected reclaim"))
                &&rejected.get().getItem().isEmpty()&&drops(h).isEmpty(),
                "Live worker owns changed paper9 once; rejected payload is detached and no placer exists");
        h.succeed();
    }

    @GameTest(template="golem_test", batch="transfer_ownership", timeoutTicks=40)
    public static void extractionRetainsActualPayloadBesideCallbackFundedRecovery(GameTestHelper h) {
        var f=fixture(h);var funding=new ItemStackHandler(2);
        funding.setStackInSlot(0,new ItemStack(Items.GOLD_INGOT,64));funding.setStackInSlot(1,new ItemStack(Items.EMERALD,3));
        var armed=new AtomicBoolean();var entered=new AtomicBoolean();
        var source=new ItemStackHandler(1) {
            @Override protected void onContentsChanged(int slot) {
                if(armed.get()&&entered.compareAndSet(false,true)) {
                    f.golem.retainTransfer(funding.extractItem(0,64,false));
                    f.golem.retainTransfer(funding.extractItem(1,3,false));
                }
            }
        };
        source.setStackInSlot(0,new ItemStack(Items.DIAMOND,10));armed.set(true);
        int moved=-1;RuntimeException failure=null;
        try { moved=GolemContainers.extractInto(f.golem,source,new ItemStack(Items.DIAMOND,10),SealFilter.defaults()); }
        catch(RuntimeException ex) { failure=ex; }
        finally { cleanup(f); }
        h.assertTrue(failure==null,"Actual extraction must retain its returned payload when callback already filled recovery: "+failure);
        h.assertTrue(entered.get()&&moved==10&&source.getStackInSlot(0).isEmpty()
                &&funding.getStackInSlot(0).isEmpty()&&funding.getStackInSlot(1).isEmpty(),"Callback moves independent real gold64/emerald3 and extraction moves actual diamond10");
        h.assertTrue(cargo(f.golem,Items.GOLD_INGOT)==64&&cargo(f.golem,Items.EMERALD)==3&&cargo(f.golem,Items.DIAMOND)==10
                &&allCargo(f.golem)==77&&drops(h).isEmpty(),"Hands and persistent recovery retain all seventy-seven actual funded items");
        h.succeed();
    }

    @GameTest(template="golem_test", batch="transfer_ownership", timeoutTicks=40)
    public static void reclaimRetainsCargoFundedDuringCargoPublication(GameTestHelper h) {
        var f=fixture(h);f.golem.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.PAPER,9));f.owner.setShiftKeyDown(true);
        var funding=new ItemStackHandler(1);funding.setStackInSlot(0,new ItemStack(Items.GOLD_INGOT,3));
        var entered=new AtomicBoolean();
        Consumer<EntityJoinLevelEvent> fund=e->{
            if(paperJoin(h,e)&&entered.compareAndSet(false,true))f.golem.retainTransfer(funding.extractItem(0,3,false));
        };
        InteractionResult result;
        NeoForge.EVENT_BUS.addListener(fund);
        try { result=f.golem.interact(f.owner,InteractionHand.MAIN_HAND); }
        finally { NeoForge.EVENT_BUS.unregister(fund);cleanup(f); }
        h.assertTrue(entered.get()&&!result.consumesAction()&&f.golem.isAlive(),
                "Reclaim stops before creating a placer when its cargo callback funds new cargo");
        h.assertTrue(funding.getStackInSlot(0).isEmpty()&&cargo(f.golem,Items.GOLD_INGOT)==3&&allCargo(f.golem)==3
                &&ground(h,Items.PAPER)==9&&ground(h,GolemModule.GOLEM_PLACER.get())==0&&drops(h).size()==1,
                "Actual source gold3 stays with the live worker beside accepted paper9 and no placer");
        retryFundedReclaim(h,f);
        h.succeed();
    }

    @GameTest(template="golem_test", batch="transfer_ownership", timeoutTicks=40)
    public static void reclaimRetainsCargoFundedDuringPlacerPublication(GameTestHelper h) {
        var f=fixture(h);f.golem.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.PAPER,9));f.owner.setShiftKeyDown(true);
        var funding=new ItemStackHandler(1);funding.setStackInSlot(0,new ItemStack(Items.GOLD_INGOT,3));
        var entered=new AtomicBoolean();var actualPlacer=new AtomicReference<ItemEntity>();
        Consumer<EntityJoinLevelEvent> fund=e->{
            if(e.getLevel()==h.getLevel()&&e.getEntity() instanceof ItemEntity item
                    &&item.getItem().is(GolemModule.GOLEM_PLACER.get())&&h.getBounds().contains(item.position())
                    &&entered.compareAndSet(false,true)) {
                actualPlacer.set(item);f.golem.retainTransfer(funding.extractItem(0,3,false));
            }
        };
        InteractionResult result;
        NeoForge.EVENT_BUS.addListener(fund);
        try { result=f.golem.interact(f.owner,InteractionHand.MAIN_HAND); }
        finally { NeoForge.EVENT_BUS.unregister(fund);cleanup(f); }
        h.assertTrue(entered.get()&&!result.consumesAction()&&f.golem.isAlive(),
                "Reclaim stops after its placer callback funds new cargo instead of discarding that cargo");
        h.assertTrue(funding.getStackInSlot(0).isEmpty()&&cargo(f.golem,Items.GOLD_INGOT)==3&&allCargo(f.golem)==3
                &&ground(h,Items.PAPER)==9&&ground(h,GolemModule.GOLEM_PLACER.get())==0&&drops(h).size()==1,
                "Actual source gold3 stays with the live worker beside accepted paper9 and no published placer");
        h.assertTrue(actualPlacer.get()!=null&&actualPlacer.get().isRemoved()&&actualPlacer.get().getItem().isEmpty(),
                "The exact newly created placer entity is emptied and discarded when reclaim aborts");
        retryFundedReclaim(h,f);
        h.succeed();
    }

    private static void retryFundedReclaim(GameTestHelper h,Fixture f){
        f.owner.setShiftKeyDown(true);
        try{h.assertTrue(f.golem.interact(f.owner,InteractionHand.MAIN_HAND).consumesAction()&&f.golem.isRemoved(),
                "Once the funding callback is gone, normal reclaim retries successfully");}
        finally{cleanup(f);}
        h.assertTrue(ground(h,Items.PAPER)==9&&ground(h,Items.GOLD_INGOT)==3
                &&ground(h,GolemModule.GOLEM_PLACER.get())==1&&drops(h).size()==3,
                "Retry emits funded gold3 and one placer, preserving the already paid paper9 exactly once");
    }

    @GameTest(template="golem_test", batch="transfer_ownership", timeoutTicks=40)
    public static void insertionQueryCannotOverwriteCallbackFundedReplacementCargo(GameTestHelper h) {
        var f=fixture(h);f.golem.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.PAPER,9));
        var funding=new ItemStackHandler(1);funding.setStackInSlot(0,new ItemStack(Items.PAPER,3));
        var armed=new AtomicBoolean();var donated=new AtomicBoolean();
        var destination=new ItemStackHandler(1) {
            @Override public ItemStack getStackInSlot(int slot) {
                var actual=super.getStackInSlot(slot);
                if(armed.get()&&donated.compareAndSet(false,true))f.golem.retainTransfer(funding.extractItem(0,3,false));
                return actual;
            }
        };
        var filter=new SealFilter(new ItemStack(Items.PAPER),false,64);armed.set(true);
        try {
            int first=GolemContainers.insertCarried(f.golem,destination,filter);
            h.assertTrue(donated.get()&&first==0&&funding.getStackInSlot(0).isEmpty()&&destination.getStackInSlot(0).isEmpty()
                    &&cargo(f.golem,Items.PAPER)==12&&allCargo(f.golem)==12,
                    "Actual stock query replaces paper9 with funded paper12; stale hand reference must abort before debit");
            int second=GolemContainers.insertCarried(f.golem,destination,filter);
            h.assertTrue(second==12&&destination.getStackInSlot(0).is(Items.PAPER)&&destination.getStackInSlot(0).getCount()==12
                    &&allCargo(f.golem)==0&&funding.getStackInSlot(0).isEmpty()&&drops(h).isEmpty(),
                    "Fresh retry transfers all twelve actual papers once after the one funded query callback");
        } finally { cleanup(f); }
        h.succeed();
    }

    private record Fixture(ThaumcraftGolem golem,FakePlayer owner,GolemSeals.SealKey target) {}

    private static Fixture fixture(GameTestHelper h) {
        for(int x=0;x<12;x++)for(int z=0;z<12;z++)h.setBlock(new BlockPos(x,0,z),Blocks.STONE);
        var owner=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"TransferOwner"));
        owner.setGameMode(GameType.SURVIVAL);owner.getInventory().clearContent();owner.getInventory().selected=0;owner.setShiftKeyDown(false);
        var pos=h.absolutePos(new BlockPos(5,1,6));var golem=GolemModule.GOLEM.get().create(h.getLevel());
        h.assertTrue(golem!=null,"Actual worker factory");golem.initialize(GolemDesign.defaultWood(),owner.getUUID(),pos,0);
        golem.moveTo(Vec3.atBottomCenterOf(pos));golem.setOnGround(true);golem.setNoAi(true);
        h.assertTrue(h.getLevel().addFreshEntity(golem),"Actual NoAI worker spawned");owner.moveTo(golem.position());
        var target=new GolemSeals.SealKey(h.absolutePos(new BlockPos(8,1,6)),Direction.EAST);
        h.setBlock(new BlockPos(8,1,6),Blocks.STONE);
        h.assertTrue(GolemContainers.loaded(h.getLevel(),target)&&GolemContainers.handler(h.getLevel(),target)==null,
                "Loaded native stone is a real no-handler fallback target");
        return new Fixture(golem,owner,target);
    }

    private static boolean paperJoin(GameTestHelper h,EntityJoinLevelEvent event) {
        return event.getLevel()==h.getLevel()&&event.getEntity() instanceof ItemEntity item
                &&item.getItem().is(Items.PAPER)&&h.getBounds().contains(item.position());
    }

    private static ItemStack named(Item item,int count,String name) {
        var stack=new ItemStack(item,count);stack.set(DataComponents.CUSTOM_NAME,Component.literal(name));return stack;
    }

    private static List<ItemEntity> drops(GameTestHelper h) { return h.getLevel().getEntitiesOfClass(ItemEntity.class,h.getBounds()); }
    private static int ground(GameTestHelper h,Item item) { return drops(h).stream().filter(e->e.getItem().is(item)).mapToInt(e->e.getItem().getCount()).sum(); }
    private static int cargo(ThaumcraftGolem golem,Item item) {
        int result=0;
        for(var slot:new EquipmentSlot[]{EquipmentSlot.MAINHAND,EquipmentSlot.OFFHAND}) {
            var stack=golem.getItemBySlot(slot);if(stack.is(item))result+=stack.getCount();
        }
        for(var stack:golem.transferRecoveryItems())if(stack.is(item))result+=stack.getCount();
        return result;
    }
    private static int allCargo(ThaumcraftGolem golem) {
        return golem.getMainHandItem().getCount()+golem.getOffhandItem().getCount()
                +golem.transferRecoveryItems().stream().mapToInt(ItemStack::getCount).sum();
    }
    private static void cleanup(Fixture f) { f.owner.setShiftKeyDown(false);f.owner.getInventory().clearContent(); }
}
