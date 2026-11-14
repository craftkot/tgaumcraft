package dev.ehtid.thaumcraft.golem.test;

import com.mojang.authlib.GameProfile;
import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.golem.inventory.GolemContainers;
import dev.ehtid.thaumcraft.golem.menu.GolemLogisticsMenu;
import dev.ehtid.thaumcraft.golem.seal.*;
import dev.ehtid.thaumcraft.port.core.golem.GolemDesign;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import java.util.UUID;

@GameTestHolder("thaumcraft") @PrefixGameTestTemplate(false)
public final class GolemCycleGameTests {
    private record Fixture(GolemSeals data,GolemSeals.SealKey source,GolemSeals.SealKey sink,ThaumcraftGolem g,IItemHandlerModifiable inv) {
        void close() { data.remove(source.pos(),source.face(),g.ownerId()); g.discard(); }
    }
    private GolemCycleGameTests() {}
    private static Fixture fixture(GameTestHelper h,boolean blacklist) {
        for(int x=0;x<12;x++) for(int z=0;z<12;z++) h.setBlock(new BlockPos(x,0,z),Blocks.STONE);
        var source=new GolemSeals.SealKey(h.absolutePos(new BlockPos(5,1,5)),Direction.UP);
        var sink=new GolemSeals.SealKey(h.absolutePos(new BlockPos(9,1,5)),Direction.UP);
        h.getLevel().setBlockAndUpdate(source.pos(),Blocks.BARREL.defaultBlockState()); h.getLevel().setBlockAndUpdate(sink.pos(),Blocks.BARREL.defaultBlockState());
        var data=GolemSeals.get(h.getLevel()); var owner=UUID.randomUUID(); data.place(source.pos(),source.face(),owner,GolemSeals.Type.EMPTY_ADVANCED);
        var s=data.get(source); var policy=s.logistics().withSlot(0,new SealFilter(new ItemStack(Items.IRON_INGOT),true,0))
                .withSlot(8,new SealFilter(new ItemStack(Items.GOLD_INGOT),true,0)).withMode(blacklist,true,false).withCycle(true);
        data.configureLogistics(s,owner,policy);
        var inv=(IItemHandlerModifiable)GolemContainers.handler(h.getLevel(),source);
        var g=GolemModule.GOLEM.get().create(h.getLevel()); g.moveTo(Vec3.atBottomCenterOf(source.pos().north()));
        g.initialize(GolemDesign.fromIds(0,1,1,0,0,0),owner,source.pos(),0); g.setNoAi(true); h.getLevel().addFreshEntity(g);
        return new Fixture(data,source,sink,g,inv);
    }
    private static GolemSeals.BlockAssignment claim(Fixture f) { return f.data.claimBlock(f.g,p->true).orElseThrow(); }
    private static int stock(IItemHandlerModifiable inv) { int count=0; for(int i=0;i<inv.getSlots();i++) count+=inv.getStackInSlot(i).getCount(); return count; }
    @GameTest(template="golem_test",batch="cycle_order",timeoutTicks=100)
    public static void realCompletionAdvancesAcrossSparseGhostsWithoutChangingInventoryOrder(GameTestHelper h) {
        var f=fixture(h,false); f.inv.setStackInSlot(0,new ItemStack(Items.GOLD_INGOT,4)); f.inv.setStackInSlot(1,new ItemStack(Items.IRON_INGOT,3));
        h.runAfterDelay(25,()->{
            var first=claim(f); h.assertTrue(f.data.completeBlock(h.getLevel(),f.g,first) && f.g.getMainHandItem().is(Items.IRON_INGOT)
                    && f.g.getMainHandItem().getCount()==3 && stock(f.inv)==4,"First populated ghost chooses iron despite gold in earlier actual slot");
            h.assertTrue(!f.data.completeBlock(h.getLevel(),f.g,first),"Stale completion cannot advance source cursor twice");
            h.assertTrue(GolemContainers.deposit(h.getLevel(),f.g,f.sink,SealFilter.defaults())==3,"Move actual first payload to real sink, freeing work hand");
            h.runAfterDelay(25,()->{
                var second=claim(f); h.assertTrue(f.data.completeBlock(h.getLevel(),f.g,second) && f.g.getMainHandItem().is(Items.GOLD_INGOT)
                        && f.g.getMainHandItem().getCount()==4 && stock(f.inv)==0,"Successful completion advances to ninth populated ghost, skipping empty ghosts");
                var sink=(IItemHandlerModifiable)GolemContainers.handler(h.getLevel(),f.sink);
                h.assertTrue(stock(sink)+f.g.getMainHandItem().getCount()==7,"All7 actual items conserved across two paid extractions"); f.close(); h.succeed();
            });
        });
    }
    @GameTest(template="golem_test",batch="cycle_missing",timeoutTicks=100)
    public static void unavailableSelectedGhostWaitsForSupplyInsteadOfSkippingToGold(GameTestHelper h) {
        var f=fixture(h,false); f.inv.setStackInSlot(0,new ItemStack(Items.GOLD_INGOT,4));
        h.runAfterDelay(30,()->{
            h.assertTrue(f.data.claimBlock(f.g,p->true).isEmpty() && stock(f.inv)==4,"Source cycle does not skip unavailable selected iron");
            f.inv.setStackInSlot(1,new ItemStack(Items.IRON_INGOT,1));
            h.runAfterDelay(25,()->{
                h.assertTrue(f.data.completeBlock(h.getLevel(),f.g,claim(f)) && f.g.getMainHandItem().is(Items.IRON_INGOT) && stock(f.inv)==4,"Actual supplied iron permits pending cycle step; gold remains untouched");
                f.close(); h.succeed();
            });
        });
    }
    @GameTest(template="golem_test",batch="cycle_blacklist",timeoutTicks=65)
    public static void blacklistUsesEveryGhostEvenWhenCycleIsEnabled(GameTestHelper h) {
        var f=fixture(h,true); f.inv.setStackInSlot(0,new ItemStack(Items.IRON_INGOT,3)); f.inv.setStackInSlot(1,new ItemStack(Items.GOLD_INGOT,4)); f.inv.setStackInSlot(2,new ItemStack(Items.COAL,2));
        h.runAfterDelay(25,()->{
            h.assertTrue(f.data.completeBlock(h.getLevel(),f.g,claim(f)) && f.g.getMainHandItem().is(Items.COAL) && f.g.getMainHandItem().getCount()==2
                    && stock(f.inv)==7,"pcycle only narrows whitelist; both blacklisted ghosts stay protected"); f.close(); h.succeed();
        });
    }
    @GameTest(template="golem_test",batch="cycle_menu",timeoutTicks=50)
    public static void ownerCycleActionPreservesOtherPoliciesAndRoundTripsWithoutCursor(GameTestHelper h) {
        var f=fixture(h,false); var owner=FakePlayerFactory.get(h.getLevel(),new GameProfile(f.g.ownerId(),"CycleOwner")); owner.moveTo(f.g.position());
        var menu=new GolemLogisticsMenu(93,owner.getInventory(),h.getLevel(),f.data.get(f.source));
        h.assertTrue(menu.advancedEmpty() && menu.cycle() && !menu.single() && menu.clickMenuButton(owner,64) && !menu.cycle(),"Source index4 controls pcycle for advanced Empty, not provider psing");
        menu.clickMenuButton(owner,34); menu.clickMenuButton(owner,61); menu.setCarried(new ItemStack(Items.DIAMOND,13)); menu.clicked(7,0,ClickType.PICKUP,owner);
        h.assertTrue(menu.cycle() && !menu.matchNbt() && menu.getCarried().getCount()==13,"NBT and ghost edits preserve cycle and actual cursor"); menu.setCarried(ItemStack.EMPTY);
        var other=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"CycleStranger")); other.moveTo(owner.position());
        h.assertTrue(!menu.clickMenuButton(other,64),"Other viewer cannot change cycle");
        var tag=f.data.save(new CompoundTag(),h.getLevel().registryAccess()); var copy=GolemSeals.load(tag,h.getLevel().registryAccess());
        h.assertTrue(copy.get(f.source).logistics().cycle() && !copy.get(f.source).logistics().matchNbt() && copy.get(f.source).logistics().slot(7).item().is(Items.DIAMOND)
                && copy.pendingTasks()==0 && tag.getAllKeys().equals(java.util.Set.of("seals")),"Policy survives NBT; transient cursor/tasks are not serialized");
        var rows=tag.getList("seals",Tag.TAG_COMPOUND); for(int i=0;i<rows.size();i++) rows.getCompound(i).remove("pcycle");
        h.assertTrue(!GolemSeals.load(tag,h.getLevel().registryAccess()).get(f.source).logistics().cycle(),"Old save defaults pcycle=false");
        f.data.remove(f.source.pos(),f.source.face(),f.g.ownerId()); f.data.place(f.source.pos(),f.source.face(),f.g.ownerId(),GolemSeals.Type.PROVIDE);
        h.assertTrue(!menu.clickMenuButton(owner,64),"Stale advanced menu cannot change replacement provider");
        var provider=new GolemLogisticsMenu(94,owner.getInventory(),h.getLevel(),f.data.get(f.source));
        h.assertTrue(provider.clickMenuButton(owner,34) && provider.single() && !provider.cycle(),"Same original toggle index retains independent provider psing behavior");
        f.close(); h.succeed();
    }
}
