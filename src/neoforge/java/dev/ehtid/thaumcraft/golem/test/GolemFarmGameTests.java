package dev.ehtid.thaumcraft.golem.test;

import com.mojang.authlib.GameProfile;
import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.golem.inventory.GolemContainers;
import dev.ehtid.thaumcraft.golem.menu.GolemSealMenu;
import dev.ehtid.thaumcraft.golem.seal.*;
import dev.ehtid.thaumcraft.port.core.golem.GolemDesign;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import java.util.*;

@GameTestHolder("thaumcraft") @PrefixGameTestTemplate(false)
public final class GolemFarmGameTests {
    private static final GolemDesign FARMER=GolemDesign.fromIds(0,1,1,0,0,0);
    private record Farm(GolemSeals data,GolemSeals.SealKey key,BlockPos crop,ThaumcraftGolem golem) {
        void close() { data.remove(key.pos(),key.face(),golem.ownerId()); golem.discard(); }
    }
    private GolemFarmGameTests() {}
    private static Farm farm(GameTestHelper h,BlockState state,Item seed) {
        for(int x=0;x<12;x++) for(int z=0;z<12;z++) h.setBlock(new BlockPos(x,0,z),Blocks.STONE);
        boolean stacked=state.is(Blocks.SUGAR_CANE)||state.is(Blocks.CACTUS);
        var support=h.absolutePos(new BlockPos(5,stacked?1:0,5)); var crop=support.above();
        if(stacked) {
            h.getLevel().setBlockAndUpdate(support.below(),state.is(Blocks.CACTUS)?Blocks.SAND.defaultBlockState():Blocks.DIRT.defaultBlockState());
            if(state.is(Blocks.SUGAR_CANE)) h.getLevel().setBlockAndUpdate(support.below().north(),Blocks.WATER.defaultBlockState());
        }
        h.getLevel().setBlockAndUpdate(support,stacked?state:state.is(Blocks.NETHER_WART)?Blocks.SOUL_SAND.defaultBlockState():Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE,7));
        h.getLevel().setBlockAndUpdate(crop,state);
        var data=GolemSeals.get(h.getLevel()); var key=new GolemSeals.SealKey(support,Direction.UP); var owner=UUID.randomUUID();
        h.assertTrue(data.place(support,Direction.UP,owner,GolemSeals.Type.HARVEST),"Actual Harvest seal placed");
        data.configureArea(data.get(key),owner,new BlockPos(1,1,1));
        var g=GolemModule.GOLEM.get().create(h.getLevel()); g.moveTo(Vec3.atBottomCenterOf(crop.east())); g.initialize(FARMER,owner,crop,0); g.setNoAi(true);
        if(seed!=null) g.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(seed,2));
        h.assertTrue(h.getLevel().addFreshEntity(g),"Actual SMART+DEFT farmer spawned"); return new Farm(data,key,crop,g);
    }
    private static GolemSeals.BlockAssignment claim(Farm f) { return f.data.claimBlock(f.golem,p->true).orElseThrow(); }
    private static boolean complete(GameTestHelper h,Farm f,GolemSeals.BlockAssignment task) { return f.data.completeBlock(h.getLevel(),f.golem,task); }
    private static ListTag pending(GameTestHelper h,Farm f) {
        var rows=f.data.save(new CompoundTag(),h.getLevel().registryAccess()).getList("seals",Tag.TAG_COMPOUND);
        for(int i=0;i<rows.size();i++) if(rows.getCompound(i).getLong("pos")==f.key.pos().asLong()) return rows.getCompound(i).getList("ReplantTasks",Tag.TAG_COMPOUND);
        throw new AssertionError("Saved seal missing");
    }
    private static void cycle(GameTestHelper h,BlockState ripe,BlockState young,Item seed) {
        var f=farm(h,ripe,seed);
        h.runAfterDelay(8,()->{
            var harvest=claim(f); h.assertTrue(complete(h,f,harvest) && h.getLevel().isEmptyBlock(f.crop),"Real mature crop harvested");
            h.assertTrue(!complete(h,f,harvest) && pending(h,f).size()==1,"Exclusive harvest records one durable replant intent");
            h.assertTrue(!h.getLevel().getEntitiesOfClass(ItemEntity.class,h.getBounds()).isEmpty(),"Vanilla harvest creates real loot");
            h.runAfterDelay(8,()->{
                var plant=claim(f); h.assertTrue(complete(h,f,plant),"Actual seed placement succeeds");
                h.assertTrue(h.getLevel().getBlockState(f.crop).equals(young) && f.golem.getMainHandItem().getCount()==1,"Plant starts at age0 and charges exactly one real seed");
                h.assertTrue(!complete(h,f,plant) && pending(h,f).isEmpty(),"Completed placement cannot charge another seed");
                h.assertTrue(f.data.claimBlock(f.golem,p->true).isEmpty(),"Immature crop is not harvested again");
                f.close(); h.succeed();
            });
        });
    }
    @GameTest(template="golem_test",batch="farm_wheat",timeoutTicks=60)
    public static void wheatUsesRealLootAndExactlyOneSeed(GameTestHelper h) { cycle(h,Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE,7),Blocks.WHEAT.defaultBlockState(),Items.WHEAT_SEEDS); }
    @GameTest(template="golem_test",batch="farm_carrot",timeoutTicks=60)
    public static void carrotsUseTheirEdibleCropAsSeed(GameTestHelper h) { cycle(h,Blocks.CARROTS.defaultBlockState().setValue(CropBlock.AGE,7),Blocks.CARROTS.defaultBlockState(),Items.CARROT); }
    @GameTest(template="golem_test",batch="farm_potato",timeoutTicks=60)
    public static void potatoesUseTheirEdibleCropAsSeed(GameTestHelper h) { cycle(h,Blocks.POTATOES.defaultBlockState().setValue(CropBlock.AGE,7),Blocks.POTATOES.defaultBlockState(),Items.POTATO); }
    @GameTest(template="golem_test",batch="farm_beetroot",timeoutTicks=60)
    public static void beetrootUsesAgeThreeAndBeetrootSeeds(GameTestHelper h) { cycle(h,Blocks.BEETROOTS.defaultBlockState().setValue(BeetrootBlock.AGE,3),Blocks.BEETROOTS.defaultBlockState(),Items.BEETROOT_SEEDS); }
    @GameTest(template="golem_test",batch="farm_wart",timeoutTicks=60)
    public static void netherWartUsesAgeThreeAndSoulSand(GameTestHelper h) { cycle(h,Blocks.NETHER_WART.defaultBlockState().setValue(NetherWartBlock.AGE,3),Blocks.NETHER_WART.defaultBlockState(),Items.NETHER_WART); }

    private static void fruit(GameTestHelper h,Block fruit,Item seed,Block stem) {
        var f=farm(h,fruit.defaultBlockState(),seed);
        h.getLevel().setBlockAndUpdate(f.crop.north().below(),Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE,7));
        h.getLevel().setBlockAndUpdate(f.crop.north(),stem.defaultBlockState().setValue(StemBlock.AGE,7));
        h.runAfterDelay(8,()->{
            h.assertTrue(complete(h,f,claim(f)) && h.getLevel().isEmptyBlock(f.crop),"Actual fruit harvested");
            h.assertTrue(f.golem.getMainHandItem().getCount()==2 && pending(h,f).isEmpty(),"Fruit has no source seed mapping: no fabricated replant or debit");
            h.assertTrue(h.getLevel().getBlockState(f.crop.north()).is(stem),"Fruit harvest preserves actual stem");
            f.data.configureArea(f.data.get(f.key),f.golem.ownerId(),new BlockPos(2,1,2));
            h.runAfterDelay(50,()->{
                h.assertTrue(f.data.claimBlock(f.golem,p->true).isEmpty() && h.getLevel().getBlockState(f.crop.north()).is(stem),"Mature stems are excluded from crop harvesting");
                f.close(); h.succeed();
            });
        });
    }
    @GameTest(template="golem_test",batch="farm_melon",timeoutTicks=90)
    public static void melonFruitPreservesStemAndDoesNotInventReplant(GameTestHelper h) { fruit(h,Blocks.MELON,Items.MELON_SEEDS,Blocks.MELON_STEM); }
    @GameTest(template="golem_test",batch="farm_pumpkin",timeoutTicks=90)
    public static void pumpkinFruitPreservesStemAndDoesNotInventReplant(GameTestHelper h) { fruit(h,Blocks.PUMPKIN,Items.PUMPKIN_SEEDS,Blocks.PUMPKIN_STEM); }
    private static void stacked(GameTestHelper h,Block block,Item item) {
        var f=farm(h,block.defaultBlockState(),item);
        h.runAfterDelay(8,()->{
            h.assertTrue(complete(h,f,claim(f)) && h.getLevel().isEmptyBlock(f.crop) && h.getLevel().getBlockState(f.key.pos()).is(block),"Actual upper segment harvested; rooted base remains");
            h.assertTrue(pending(h,f).isEmpty() && f.golem.getMainHandItem().getCount()==2,"Source ItemBlock/ItemReed are not registered IPlantable seeds; no replant debit");
            f.data.remove(f.key.pos(),f.key.face(),f.golem.ownerId());
            var lower=new GolemSeals.SealKey(f.key.pos().below(),Direction.UP);
            f.data.place(lower.pos(),lower.face(),f.golem.ownerId(),GolemSeals.Type.HARVEST); f.data.configureArea(f.data.get(lower),f.golem.ownerId(),new BlockPos(1,1,1));
            h.runAfterDelay(10,()->{
                h.assertTrue(f.data.claimBlock(f.golem,p->true).isEmpty() && h.getLevel().getBlockState(f.key.pos()).is(block),"Root segment never becomes a harvest task");
                f.data.remove(lower.pos(),lower.face(),f.golem.ownerId()); f.golem.discard(); h.succeed();
            });
        });
    }
    @GameTest(template="golem_test",batch="farm_cane",timeoutTicks=60)
    public static void sugarCaneHarvestLeavesRootAndDoesNotRequestSeed(GameTestHelper h) { stacked(h,Blocks.SUGAR_CANE,Items.SUGAR_CANE); }
    @GameTest(template="golem_test",batch="farm_cactus",timeoutTicks=60)
    public static void cactusHarvestLeavesRootAndDoesNotRequestSeed(GameTestHelper h) { stacked(h,Blocks.CACTUS,Items.CACTUS); }

    @GameTest(template="golem_test",batch="farm_break_cancel",timeoutTicks=60)
    public static void canceledHarvestCannotCreateLootOrReplantAndCanRetry(GameTestHelper h) {
        var f=farm(h,Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE,7),Items.WHEAT_SEEDS);
        h.runAfterDelay(8,()->{
            var task=claim(f); java.util.function.Consumer<BlockEvent.BreakEvent> reject=e->{ if(e.getLevel()==h.getLevel()&&e.getPos().equals(f.crop)) e.setCanceled(true); };
            NeoForge.EVENT_BUS.addListener(reject);
            try { h.assertTrue(!complete(h,f,task) && h.getLevel().getBlockState(f.crop).is(Blocks.WHEAT) && pending(h,f).isEmpty(),"Protected crop stays intact without replant credit"); }
            finally { NeoForge.EVENT_BUS.unregister(reject); }
            h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class,h.getBounds()).isEmpty() && f.golem.getMainHandItem().getCount()==2,"No loot or seed debit on canceled break");
            h.assertTrue(complete(h,f,task),"Same lease retries after protection permits break"); f.close(); h.succeed();
        });
    }

    @GameTest(template="golem_test",batch="farm_place_cancel",timeoutTicks=80)
    public static void canceledReplantRefundsActualSeedThenRetriesWithoutDuplication(GameTestHelper h) {
        var f=farm(h,Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE,7),Items.WHEAT_SEEDS);
        h.runAfterDelay(8,()->{
            complete(h,f,claim(f));
            h.runAfterDelay(8,()->{
                h.getLevel().setBlockAndUpdate(f.crop.below(),Blocks.DIRT.defaultBlockState());
                var task=claim(f); java.util.function.Consumer<BlockEvent.EntityPlaceEvent> reject=e->{ if(e.getLevel()==h.getLevel()&&e.getPos().equals(f.crop)) e.setCanceled(true); };
                NeoForge.EVENT_BUS.addListener(reject);
                try { h.assertTrue(!complete(h,f,task) && h.getLevel().isEmptyBlock(f.crop) && f.golem.getMainHandItem().getCount()==2 && pending(h,f).size()==1,"Canceled real placement refunds seed and retains intent");
                    h.assertTrue(h.getLevel().getBlockState(f.crop.below()).is(Blocks.FARMLAND),"Built-in hoe restores farmland through real useOn before seed retry"); }
                finally { NeoForge.EVENT_BUS.unregister(reject); }
                h.assertTrue(complete(h,f,task) && f.golem.getMainHandItem().getCount()==1 && pending(h,f).isEmpty(),"Retry consumes exactly one seed");
                h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class,h.getBounds()).stream().noneMatch(e->e.getItem().is(Items.IRON_HOE)),"Built-in tool never becomes world loot"); f.close(); h.succeed();
            });
        });
    }

    @GameTest(template="golem_test",batch="farm_ai",timeoutTicks=220)
    public static void realAiWalksToHarvestAndReplantsFromHands(GameTestHelper h) {
        var f=farm(h,Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE,7),Items.WHEAT_SEEDS);
        f.golem.moveTo(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(2,1,5)))); f.golem.setNoAi(false);
        h.succeedWhen(()->{
            h.assertTrue(h.getLevel().getBlockState(f.crop).equals(Blocks.WHEAT.defaultBlockState()) && f.golem.getMainHandItem().getCount()==1,"Native navigation/task AI completed harvest and paid replant"); f.close();
        });
    }

    @GameTest(template="golem_test",batch="farm_provide",timeoutTicks=260)
    public static void harvestProvisionUsesRealProviderPickupWithoutExtraDelivery(GameTestHelper h) {
        var f=farm(h,Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE,7),null);
        var source=new GolemSeals.SealKey(h.absolutePos(new BlockPos(2,1,5)),Direction.UP); h.getLevel().setBlockAndUpdate(source.pos(),Blocks.BARREL.defaultBlockState());
        f.data.place(source.pos(),source.face(),f.golem.ownerId(),GolemSeals.Type.PROVIDE);
        var inv=(IItemHandlerModifiable)GolemContainers.handler(h.getLevel(),source); inv.setStackInSlot(0,new ItemStack(Items.WHEAT_SEEDS,1));
        f.data.configureHarvest(f.data.get(f.key),f.golem.ownerId(),new HarvestSettings(true,true));
        f.golem.setNoAi(false);
        h.succeedWhen(()->{
            h.assertTrue(h.getLevel().getBlockState(f.crop).equals(Blocks.WHEAT.defaultBlockState()) && inv.getStackInSlot(0).isEmpty() && f.golem.getMainHandItem().isEmpty(),"Farm requested real seed, worker paid provider once and planted it");
            f.data.remove(source.pos(),source.face(),f.golem.ownerId()); f.close();
        });
    }

    @GameTest(template="golem_test",batch="farm_menu",timeoutTicks=60)
    public static void ownerMenuPersistsFlagsAreaAndPendingIntentWithoutLeases(GameTestHelper h) {
        var f=farm(h,Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE,7),null);
        var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(f.golem.ownerId(),"FarmOwner")); p.moveTo(Vec3.atCenterOf(f.key.pos()));
        var menu=new GolemSealMenu(81,p.getInventory(),h.getLevel(),f.data.get(f.key));
        h.assertTrue(menu.harvest() && menu.replant() && !menu.provision() && menu.hasArea() && !menu.getSlot(0).isActive(),"Source defaults and area controls with no fake filter");
        menu.setCarried(new ItemStack(Items.DIAMOND,3)); menu.clicked(0,0,ClickType.PICKUP,p);
        h.assertTrue(menu.getCarried().getCount()==3 && !menu.getSlot(0).hasItem(),"Hidden ghost cannot mutate actual cursor");
        h.assertTrue(menu.clickMenuButton(p,31) && menu.clickMenuButton(p,60) && !menu.replant() && menu.provision(),"Source toggle IDs remain independent");
        menu.clickMenuButton(p,30); menu.setCarried(ItemStack.EMPTY);
        h.runAfterDelay(8,()->{
            complete(h,f,claim(f)); var tag=f.data.save(new CompoundTag(),h.getLevel().registryAccess());
            var copy=GolemSeals.load(tag,h.getLevel().registryAccess()); var seal=copy.get(f.key);
            h.assertTrue(seal.type()==GolemSeals.Type.HARVEST && seal.harvest().replant() && seal.harvest().provision() && seal.area().equals(new BlockPos(1,1,1)) && copy.pendingTasks()==0 && pending(h,f).size()==1,"Type/settings/intent persist; live leases do not");
            h.assertTrue(copy.save(new CompoundTag(),h.getLevel().registryAccess()).getList("seals",Tag.TAG_COMPOUND).stream().anyMatch(t->t instanceof CompoundTag row && row.getList("ReplantTasks",Tag.TAG_COMPOUND).size()==1),"Pending placement survives full decode/encode");
            f.close(); h.succeed();
        });
    }

    @GameTest(template="golem_test",batch="farm_gates",timeoutTicks=80)
    public static void wrongTraitsAndChangedColorCannotCompleteAReservedHarvest(GameTestHelper h) {
        var f=farm(h,Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE,7),Items.WHEAT_SEEDS);
        var other=GolemModule.GOLEM.get().create(h.getLevel()); other.moveTo(f.golem.position());
        other.initialize(GolemDesign.defaultWood(),f.golem.ownerId(),f.crop,0); other.setNoAi(true); h.getLevel().addFreshEntity(other);
        h.runAfterDelay(8,()->{
            h.assertTrue(f.data.claimBlock(other,p->true).isEmpty(),"Ordinary worker lacks SMART and DEFT");
            var task=claim(f); f.golem.setGolemColor((byte)1);
            f.data.configureControls(f.data.get(f.key),f.golem.ownerId(),0,2,true,false);
            h.assertTrue(!complete(h,f,task) && h.getLevel().getBlockState(f.crop).is(Blocks.WHEAT),"Reconfigured seal invalidates reserved callback");
            h.runAfterDelay(8,()->{
                h.assertTrue(f.data.claimBlock(f.golem,p->true).isEmpty(),"Mismatched color cannot claim new work");
                f.golem.setGolemColor((byte)2); var next=claim(f); f.golem.setGolemColor((byte)1);
                h.assertTrue(!complete(h,f,next) && pending(h,f).isEmpty(),"Color changed after claim is rechecked at completion");
                f.golem.setGolemColor((byte)2); h.assertTrue(complete(h,f,next),"Compatible worker retries same protected lease");
                other.discard(); f.close(); h.succeed();
            });
        });
    }

    @GameTest(template="golem_test",batch="farm_recovery",timeoutTicks=50)
    public static void severalRealRefundVariantsPersistAndReclaimExactlyOnce(GameTestHelper h) {
        var f=farm(h,Blocks.WHEAT.defaultBlockState(),Items.WHEAT_SEEDS); var g=f.golem;
        g.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.COBBLESTONE,64));
        var named=new ItemStack(Items.WHEAT_SEEDS,3); named.set(DataComponents.CUSTOM_NAME,Component.literal("Actual returned seed"));
        g.retainTransfer(named); g.retainTransfer(new ItemStack(Items.CARROT,4)); named.setCount(32);
        var state=new CompoundTag(); g.saveWithoutId(state); var copy=GolemModule.GOLEM.get().create(h.getLevel()); copy.readAdditionalSaveData(state);
        h.assertTrue(copy.transferRecoveryItems().size()==2 && copy.transferRecoveryItems().getFirst().getCount()==3 && copy.transferRecoveryItems().get(1).getCount()==4,"Actual refund variants/components survive NBT with defensive copies");
        var actor=FakePlayerFactory.get(h.getLevel(),new GameProfile(g.ownerId(),"RefundOwner")); actor.moveTo(g.position());
        copy.moveTo(g.position()); actor.setShiftKeyDown(true);
        h.assertTrue(copy.interact(actor,net.minecraft.world.InteractionHand.MAIN_HAND).consumesAction(),"Reclaim delivers every real queued variant");
        var drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,h.getBounds());
        h.assertTrue(drops.stream().mapToInt(e->e.getItem().is(Items.WHEAT_SEEDS)?e.getItem().getCount():0).sum()==3
                && drops.stream().mapToInt(e->e.getItem().is(Items.CARROT)?e.getItem().getCount():0).sum()==4,"Exactly3 real seeds and4 real carrots reclaimed");
        h.assertTrue(!copy.interact(actor,net.minecraft.world.InteractionHand.MAIN_HAND).consumesAction(),"Removed entity cannot replay cargo refund"); drops.forEach(ItemEntity::discard); f.close(); h.succeed();
    }
}
