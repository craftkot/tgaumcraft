package dev.ehtid.thaumcraft.golem.test;

import com.mojang.authlib.GameProfile;
import dev.ehtid.thaumcraft.blockentity.CrucibleBlockEntity;
import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.golem.crafting.GolemSealCrafting;
import dev.ehtid.thaumcraft.golem.inventory.GolemContainers;
import dev.ehtid.thaumcraft.golem.menu.GolemLogisticsMenu;
import dev.ehtid.thaumcraft.golem.seal.*;
import dev.ehtid.thaumcraft.item.CrystalEssenceItem;
import dev.ehtid.thaumcraft.player.ThaumcraftPlayerAttachments;
import dev.ehtid.thaumcraft.port.core.golem.*;
import dev.ehtid.thaumcraft.registry.ThaumcraftBlocks;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.util.*;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import java.util.UUID;

@GameTestHolder("thaumcraft") @PrefixGameTestTemplate(false)
public final class GolemAdvancedGameTests {
    private GolemAdvancedGameTests() {}
    private static void floor(GameTestHelper h) { for(int x=0;x<12;x++) for(int z=0;z<12;z++) h.setBlock(new BlockPos(x,0,z),Blocks.STONE); }
    private static GolemSeals.SealKey key(GameTestHelper h,int x,int y,int z,boolean barrel) { var p=new BlockPos(x,y,z); h.setBlock(p,barrel?Blocks.BARREL:Blocks.STONE); return new GolemSeals.SealKey(h.absolutePos(p),Direction.UP); }
    private static IItemHandlerModifiable inv(GameTestHelper h,GolemSeals.SealKey key) { return (IItemHandlerModifiable)GolemContainers.handler(h.getLevel(),key); }
    private static int count(IItemHandlerModifiable inv) { int n=0; for(int i=0;i<inv.getSlots();i++) n+=inv.getStackInSlot(i).getCount(); return n; }
    private static ThaumcraftGolem worker(GameTestHelper h,UUID owner,GolemSeals.SealKey key,GolemDesign design,boolean ai) {
        var g=GolemModule.GOLEM.get().create(h.getLevel()); g.moveTo(Vec3.atBottomCenterOf(key.pos().north()));
        g.initialize(design,owner,key.pos(),0); g.setNoAi(!ai); h.getLevel().addFreshEntity(g); return g;
    }
    private static GolemDesign smart() { return GolemDesign.fromIds(0,1,1,0,0,0); }
    private static void whitelist(GolemSeals d,GolemSeals.SealKey key,UUID owner,ItemStack ghost,int size) {
        var seal=d.get(key); d.configureLogistics(seal,owner,seal.logistics().withSlot(8,new SealFilter(ghost,false,size)).withMode(false,true,false));
    }
    private static ItemEntity drop(GameTestHelper h,BlockPos relative,ItemStack stack) {
        var item=new ItemEntity(h.getLevel(),h.absoluteVec(Vec3.atBottomCenterOf(relative)).x,h.absoluteVec(Vec3.atBottomCenterOf(relative)).y,h.absoluteVec(Vec3.atBottomCenterOf(relative)).z,stack);
        item.setDeltaMovement(Vec3.ZERO); item.setNoPickUpDelay(); h.getLevel().addFreshEntity(item); return item;
    }
    @GameTest(template="golem_test",batch="advanced_ai",timeoutTicks=350)
    public static void nativeAiPicksNinthGhostAndFillsNinthBudgetWithoutTakingRejectedGold(GameTestHelper h) {
        floor(h); var pickup=key(h,2,0,5,false); var fill=key(h,9,1,5,true); var owner=UUID.randomUUID(); var d=GolemSeals.get(h.getLevel());
        d.place(pickup.pos(),pickup.face(),owner,GolemSeals.Type.PICKUP_ADVANCED); d.place(fill.pos(),fill.face(),owner,GolemSeals.Type.FILL_ADVANCED);
        whitelist(d,pickup,owner,new ItemStack(Items.IRON_INGOT),0); whitelist(d,fill,owner,new ItemStack(Items.IRON_INGOT),7);
        d.configureArea(d.get(pickup),owner,new BlockPos(3,1,3));
        var iron=drop(h,new BlockPos(3,1,5),new ItemStack(Items.IRON_INGOT,11)); var gold=drop(h,new BlockPos(3,1,6),new ItemStack(Items.GOLD_INGOT,4));
        var g=worker(h,owner,fill,smart(),true); g.moveTo(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(5,1,5))));
        h.onEachTick(()->h.assertTrue(count(inv(h,fill))+g.getMainHandItem().getCount()+(iron.isRemoved()?0:iron.getItem().getCount())+(gold.isRemoved()?0:gold.getItem().getCount())==15,"Each tick conserves15 actual items"));
        h.succeedWhen(()->{
            h.assertTrue(count(inv(h,fill))==7 && iron.isRemoved() && g.getMainHandItem().getCount()==4 && !gold.isRemoved() && gold.getItem().getCount()==4,"Native navigation: sink="+count(inv(h,fill))+", hand="+g.getMainHandItem()+", iron="+iron.getItem()+" removed="+iron.isRemoved()+", gold="+gold.getItem()+" removed="+gold.isRemoved());
            d.remove(pickup.pos(),pickup.face(),owner); d.remove(fill.pos(),fill.face(),owner); g.discard();
        });
    }
    @GameTest(template="golem_test",batch="advanced_smart",timeoutTicks=75)
    public static void bothAdvancedSealsRequireSmartDespiteOrdinaryWorkersHavingCapacity(GameTestHelper h) {
        floor(h); var p=key(h,4,0,5,false); var f=key(h,6,1,5,true); var owner=UUID.randomUUID(); var d=GolemSeals.get(h.getLevel());
        d.place(p.pos(),p.face(),owner,GolemSeals.Type.PICKUP_ADVANCED); d.place(f.pos(),f.face(),owner,GolemSeals.Type.FILL_ADVANCED);
        whitelist(d,p,owner,new ItemStack(Items.IRON_INGOT),0); whitelist(d,f,owner,new ItemStack(Items.IRON_INGOT),0);
        var basic=worker(h,owner,f,GolemDesign.defaultWood(),false); var smart=worker(h,owner,f,smart(),false);
        basic.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_INGOT,2)); smart.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_INGOT,2));
        drop(h,new BlockPos(4,1,5),new ItemStack(Items.IRON_INGOT,1));
        h.runAfterDelay(25,()->{
            h.assertTrue(d.claimBlock(basic,x->true).isEmpty() && d.claim(basic,x->true).isEmpty(),"Ordinary worker cannot reserve either advanced seal even with carry capacity");
            var pickup=d.claim(smart,x->true).orElseThrow(); var fill=d.claimFill(smart,x->true).orElseThrow(); smart.moveTo(Vec3.atBottomCenterOf(f.pos().north()));
            h.assertTrue(d.completeBlock(h.getLevel(),smart,fill) && count(inv(h,f))==2,"SMART reserves both source types and completes actual Fill");
            d.release(h.getLevel(),smart,pickup,false); d.remove(p.pos(),p.face(),owner); d.remove(f.pos(),f.face(),owner); basic.discard(); smart.discard(); h.succeed();
        });
    }
    @GameTest(template="golem_test",batch="advanced_limits",timeoutTicks=100)
    public static void firstMatchingGhostWinsAndZeroFillLimitIsUnlimited(GameTestHelper h) {
        floor(h); var f=key(h,5,1,5,true); var owner=UUID.randomUUID(); var d=GolemSeals.get(h.getLevel()); d.place(f.pos(),f.face(),owner,GolemSeals.Type.FILL_ADVANCED);
        var seal=d.get(f); var policy=seal.logistics().withSlot(0,new SealFilter(new ItemStack(Items.IRON_INGOT),false,5)).withSlot(8,new SealFilter(new ItemStack(Items.IRON_INGOT),false,9)).withMode(false,true,false); d.configureLogistics(seal,owner,policy);
        inv(h,f).setStackInSlot(0,new ItemStack(Items.IRON_INGOT,4)); var g=worker(h,owner,f,smart(),false); g.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_INGOT,7));
        h.runAfterDelay(25,()->{
            var first=d.claimFill(g,x->true).orElseThrow(); h.assertTrue(d.completeBlock(h.getLevel(),g,first) && count(inv(h,f))==5 && g.getMainHandItem().getCount()==6,"First matching ghost5 wins over later duplicate9");
            var current=d.get(f); d.configureLogistics(current,owner,current.logistics().withSlot(0,current.logistics().slot(0).withLimit(0)));
            h.runAfterDelay(25,()->{
                h.assertTrue(d.completeBlock(h.getLevel(),g,d.claimFill(g,x->true).orElseThrow()) && count(inv(h,f))==11 && g.getMainHandItem().isEmpty(),"First zero limit means unlimited; it does not fall through to duplicate9 or Stock zero behavior");
                d.remove(f.pos(),f.face(),owner); g.discard(); h.succeed();
            });
        });
    }
    @GameTest(template="golem_test",batch="advanced_exist",timeoutTicks=75)
    public static void existingOnlyRechecksTargetAndRetriesWithoutDebitingHands(GameTestHelper h) {
        floor(h); var f=key(h,5,1,5,true); var owner=UUID.randomUUID(); var d=GolemSeals.get(h.getLevel()); d.place(f.pos(),f.face(),owner,GolemSeals.Type.FILL_ADVANCED); whitelist(d,f,owner,new ItemStack(Items.IRON_INGOT),0);
        var s=d.get(f); d.configureLogistics(s,owner,s.logistics().withExistingOnly(true)); inv(h,f).setStackInSlot(0,new ItemStack(Items.IRON_INGOT,1));
        var g=worker(h,owner,f,smart(),false); g.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_INGOT,5));
        h.runAfterDelay(25,()->{
            var task=d.claimFill(g,x->true).orElseThrow(); var removed=inv(h,f).extractItem(0,1,false);
            h.assertTrue(!d.completeBlock(h.getLevel(),g,task) && g.getMainHandItem().getCount()==5 && removed.getCount()==1,"Disappearing target stock rejects old pexist callback without debit");
            h.assertTrue(inv(h,f).insertItem(0,removed,false).isEmpty() && d.completeBlock(h.getLevel(),g,task) && count(inv(h,f))==6 && g.getMainHandItem().isEmpty(),"Restored actual stock permits same-lease retry conserving6 items");
            h.assertTrue(!d.completeBlock(h.getLevel(),g,task),"Stale completion cannot replay deposit"); d.remove(f.pos(),f.face(),owner); g.discard(); h.succeed();
        });
    }
    @GameTest(template="golem_test",batch="advanced_exist_drop",timeoutTicks=75)
    public static void sourceExistingFlagDoesNotDisableNoHandlerWorldDrop(GameTestHelper h) {
        floor(h); var f=key(h,5,1,5,false); var owner=UUID.randomUUID(); var d=GolemSeals.get(h.getLevel()); d.place(f.pos(),f.face(),owner,GolemSeals.Type.FILL_ADVANCED); whitelist(d,f,owner,new ItemStack(Items.IRON_INGOT),0);
        var s=d.get(f); d.configureLogistics(s,owner,s.logistics().withExistingOnly(true)); var g=worker(h,owner,f,smart(),false); g.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_INGOT,3));
        h.runAfterDelay(25,()->{
            var task=d.claimFill(g,x->true).orElseThrow(); h.assertTrue(d.completeBlock(h.getLevel(),g,task) && g.getMainHandItem().isEmpty(),"Source pexist is ignored without a handler; actual fallback drop remains allowed");
            var drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,h.getBounds()); h.assertTrue(drops.size()==1 && drops.getFirst().getItem().getCount()==3 && !d.completeBlock(h.getLevel(),g,task),"Actual drop3 once without replay");
            d.remove(f.pos(),f.face(),owner); g.discard(); drops.forEach(ItemEntity::discard); h.succeed();
        });
    }
    @GameTest(template="golem_test",batch="advanced_blacklist_hand",timeoutTicks=75)
    public static void blacklistOreSelectionCannotDepositBlockedMainhandInsteadOfAllowedOffhand(GameTestHelper h) {
        floor(h); var f=key(h,5,1,5,true); var owner=UUID.randomUUID(); var d=GolemSeals.get(h.getLevel()); d.place(f.pos(),f.face(),owner,GolemSeals.Type.FILL_ADVANCED);
        var ghost=new ItemStack(Items.OAK_LOG); ghost.set(DataComponents.CUSTOM_NAME,Component.literal("Ghost")); var s=d.get(f);
        d.configureLogistics(s,owner,s.logistics().withSlot(0,new SealFilter(ghost,true,0)).withOptions(SealFilterRules.Options.strict().withOre(true)));
        var g=worker(h,owner,f,GolemDesign.fromIds(0,1,1,0,3,0),false); g.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.ACACIA_LOG,2));
        var allowed=new ItemStack(Items.ACACIA_LOG,3); allowed.set(DataComponents.CUSTOM_NAME,Component.literal("Actual allowed offhand")); g.setItemSlot(EquipmentSlot.OFFHAND,allowed.copy());
        h.runAfterDelay(25,()->{
            h.assertTrue(d.completeBlock(h.getLevel(),g,d.claimFill(g,x->true).orElseThrow()) && g.getMainHandItem().getCount()==2 && g.getOffhandItem().isEmpty()
                    && ItemStack.matches(inv(h,f).getStackInSlot(0),allowed),"Source carry-order/blacklist choice is bound to actual offhand despite asymmetric ore shortcut accepting mainhand against cached offhand");
            d.remove(f.pos(),f.face(),owner); g.discard(); h.succeed();
        });
    }
    @GameTest(template="golem_test",batch="advanced_area_menu",timeoutTicks=45)
    public static void pickupAreaAndFillExistingControlsPersistWithTheirCorrectTypes(GameTestHelper h) {
        floor(h); var p=key(h,4,0,5,false); var f=key(h,7,1,5,true); var owner=UUID.randomUUID(); var d=GolemSeals.get(h.getLevel());
        d.place(p.pos(),p.face(),owner,GolemSeals.Type.PICKUP_ADVANCED); d.place(f.pos(),f.face(),owner,GolemSeals.Type.FILL_ADVANCED); d.configureArea(d.get(p),owner,new BlockPos(1,1,1));
        try {
        var actor=FakePlayerFactory.get(h.getLevel(),new GameProfile(owner,"AdvancedOwner")); actor.moveTo(Vec3.atCenterOf(p.pos()));
        var pickup=new GolemLogisticsMenu(118,actor.getInventory(),h.getLevel(),d.get(p));
        h.assertTrue(pickup.hasArea() && pickup.clickMenuButton(actor,93) && pickup.areaX()==2 && !pickup.clickMenuButton(actor,34),"Advanced pickup source area controls without an invented fifth toggle");
        actor.moveTo(Vec3.atCenterOf(f.pos())); var fill=new GolemLogisticsMenu(119,actor.getInventory(),h.getLevel(),d.get(f));
        h.assertTrue(fill.advancedFill() && !fill.existingOnly() && fill.clickMenuButton(actor,34) && fill.existingOnly() && !fill.clickMenuButton(actor,91),"Advanced Fill index4 is pexist and never an area/cycle edit");
        fill.clickMenuButton(actor,0); fill.setCarried(new ItemStack(Items.IRON_INGOT,13)); fill.clicked(8,0,ClickType.PICKUP,actor); fill.clickMenuButton(actor,3);
        h.assertTrue(fill.limit()==1 && fill.getCarried().getCount()==13 && fill.existingOnly(),"Ninth Fill size/ghost edits preserve pexist and actual cursor"); fill.setCarried(ItemStack.EMPTY);
        var restored=GolemSeals.load(d.save(new CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess());
        h.assertTrue(restored.get(p).type()==GolemSeals.Type.PICKUP_ADVANCED && restored.get(p).area().getX()==2 && restored.get(f).type()==GolemSeals.Type.FILL_ADVANCED
                    && restored.get(f).logistics().existingOnly() && restored.get(f).logistics().slot(8).limit()==1 && restored.pendingTasks()==0,"Correct typed area/settings persist while live leases do not");
        h.succeed();
        } finally { d.remove(p.pos(),p.face(),owner); d.remove(f.pos(),f.face(),owner); }
    }
    @GameTest(template="golem_test",batch="advanced_recipes",timeoutTicks=45)
    public static void bothSourceAdvancedRecipesPayRealBaseSealsAndBothAspects(GameTestHelper h) {
        floor(h); h.setBlock(new BlockPos(5,1,5),ThaumcraftBlocks.CRUCIBLE.get()); var crucible=(CrucibleBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(5,1,5)));
        var actor=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"AdvancedCrafter")); var knowledge=actor.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge();
        for(boolean pickup:new boolean[]{true,false}) {
            var tag=new CompoundTag(); tag.putInt("Water",1000); tag.putShort("Heat",(short)200); crucible.loadWithComponents(tag,h.getLevel().registryAccess());
            for(String aspect:new String[]{"sensus","cognitio"}) { var actual=CrystalEssenceItem.ofAspect(aspect); actual.setCount(10); int debit=crucible.dissolve(actual,10); actual.shrink(debit); h.assertTrue(debit==10 && actual.isEmpty(),"Ten actual crystals paid for "+aspect); }
            knowledge.addResearch(pickup?"SEALCOLLECT":"SEALSTORE"); var catalyst=new ItemStack(pickup?GolemModule.PICKUP_SEAL.get():GolemModule.FILL_SEAL.get());
            if(pickup) h.assertTrue(GolemSealCrafting.conversion(catalyst,knowledge,crucible.getAspects(),new dev.ehtid.thaumcraft.port.core.AspectRegistry())==null,"Base research alone cannot obtain advanced pickup");
            knowledge.addResearch("MINDBIOTHAUMIC"); int debit=crucible.attemptSmelt(catalyst,1,actor); catalyst.shrink(debit);
            h.assertTrue(debit==1 && catalyst.isEmpty() && crucible.waterMb()==950 && crucible.getAspects().visSize()==0,"Actual base seal1/two aspects10/water50 consumed once");
            var drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,h.getBounds()); h.assertTrue(drops.size()==1 && drops.getFirst().getItem().is(pickup?GolemModule.PICKUP_ADVANCED_SEAL.get():GolemModule.FILL_ADVANCED_SEAL.get()),"One real correct advanced output"); drops.forEach(ItemEntity::discard);
        }
        h.succeed();
    }
    @GameTest(template="golem_test",batch="advanced_place",timeoutTicks=45)
    public static void realAdvancedItemPlacesAndRefundsItsOwnTypeOnce(GameTestHelper h) {
        floor(h); var support=key(h,5,1,5,false); var actor=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"AdvancedPlacer")); actor.setGameMode(GameType.SURVIVAL); actor.moveTo(Vec3.atCenterOf(support.pos().north()));
        actor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(GolemModule.PICKUP_ADVANCED_SEAL.get(),2));
        var context=new UseOnContext(actor,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(support.pos()).add(0,.5,0),Direction.UP,support.pos(),false));
        h.assertTrue(GolemModule.PICKUP_ADVANCED_SEAL.get().onItemUseFirst(actor.getMainHandItem(),context).consumesAction() && actor.getMainHandItem().getCount()==1
                && GolemSeals.get(h.getLevel()).get(support).type()==GolemSeals.Type.PICKUP_ADVANCED,"Actual advanced item consumes1 to place its own typed seal");
        actor.setShiftKeyDown(true); h.assertTrue(GolemModule.PICKUP_ADVANCED_SEAL.get().onItemUseFirst(actor.getMainHandItem(),context).consumesAction()
                && !GolemSeals.get(h.getLevel()).contains(support.pos(),support.face()) && actor.getMainHandItem().getCount()==2,"Owner toggle refunds exact advanced type once"); h.succeed();
    }
}
