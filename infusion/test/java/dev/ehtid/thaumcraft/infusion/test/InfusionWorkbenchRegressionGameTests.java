package dev.ehtid.thaumcraft.infusion.test;

import com.mojang.authlib.GameProfile;
import dev.ehtid.thaumcraft.blockentity.ArcaneWorkbenchBlockEntity;
import dev.ehtid.thaumcraft.crafting.InfusionStabilityArcaneAdapter;
import dev.ehtid.thaumcraft.item.CrystalEssenceItem;
import dev.ehtid.thaumcraft.menu.ArcaneWorkbenchMenu;
import dev.ehtid.thaumcraft.player.ThaumcraftPlayerAttachments;
import dev.ehtid.thaumcraft.port.core.LegacyInfusionStabilityRecipes.Kind;
import dev.ehtid.thaumcraft.registry.ThaumcraftBlocks;
import dev.ehtid.thaumcraft.registry.ThaumcraftItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** Regression proofs use actual server menus, physical ingredients, aura, inventory and world drops. */
@GameTestHolder("thaumcraft_infusion_regression")
@PrefixGameTestTemplate(false)
public final class InfusionWorkbenchRegressionGameTests {
    private InfusionWorkbenchRegressionGameTests() {}

    @GameTest(template="infusion_paid_blank",batch="infusion_workbench_regression",timeoutTicks=20)
    public static void partiallyFullInventoryRetainsEveryPaidShiftClickOutput(GameTestHelper h) {
        BlockPos pos=h.absolutePos(new BlockPos(12,5,12));
        var bench=bench(h,pos); var actor=actor(h,pos);
        inlay(bench); bench.setVis(25);
        var inlay=InfusionStabilityArcaneAdapter.output(Kind.INLAY).getItem();
        for(int slot=0;slot<36;slot++) actor.getInventory().setItem(slot,new ItemStack(Items.COBBLESTONE,64));
        actor.getInventory().setItem(0,new ItemStack(inlay,63));
        var menu=new ArcaneWorkbenchMenu(20,actor.getInventory(),bench); menu.broadcastChanges();
        h.assertTrue(menu.getSlot(0).getItem().getCount()==2,"Actual source inlay preview has two items");
        ItemStack earned=menu.quickMoveStack(actor,0);
        h.assertTrue(earned.getCount()==2 && earned.is(inlay) && bench.getVis()==0
                && bench.getItem(2).isEmpty() && bench.getItem(7).isEmpty() && bench.getItem(11).isEmpty(),
                "One actual shift-click pays one complete source recipe");
        int dropped=h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(4)).stream()
                .filter(entity->entity.getItem().is(inlay)).mapToInt(entity->entity.getItem().getCount()).sum();
        h.assertTrue(actor.getInventory().countItem(inlay)+dropped==65,
                "One free inventory space cannot discard the second paid inlay output");
        actor.getInventory().clearContent(); h.succeed();
    }

    @GameTest(template="infusion_paid_blank",batch="infusion_workbench_regression",timeoutTicks=20)
    public static void staleNormalPickupCannotGrantUnpaidOutputAfterSharedBenchRecipeChanges(GameTestHelper h) {
        BlockPos pos=h.absolutePos(new BlockPos(12,5,12));
        var bench=bench(h,pos); var first=actor(h,pos); var second=actor(h,pos);
        inlay(bench); bench.setVis(50);
        var stale=new ArcaneWorkbenchMenu(21,first.getInventory(),bench);
        var other=new ArcaneWorkbenchMenu(22,second.getInventory(),bench);
        stale.broadcastChanges(); other.broadcastChanges();
        ItemStack oldPreview=stale.getSlot(0).getItem().copy();
        h.assertTrue(ItemStack.matches(oldPreview,InfusionStabilityArcaneAdapter.output(Kind.INLAY)),
                "First actual menu holds an authorized inlay preview");
        // Real shared inventory can change before the first viewer's next broadcast.
        for(int slot=0;slot<15;slot++) bench.setItem(slot,ItemStack.EMPTY);
        bench.setItem(0,new ItemStack(Items.GLASS_PANE));
        bench.setItem(8,new ItemStack(ThaumcraftItems.QUICKSILVER.get()));
        bench.setItem(11,CrystalEssenceItem.ofAspect("aqua"));
        bench.setItem(13,CrystalEssenceItem.ofAspect("ordo"));
        other.broadcastChanges();
        h.assertTrue(ItemStack.matches(other.getSlot(0).getItem(),InfusionStabilityArcaneAdapter.output(Kind.MIRRORED_GLASS))
                && ItemStack.matches(stale.getSlot(0).getItem(),oldPreview),
                "Other viewer sees new valid recipe while first viewer still has stale physical menu state");
        stale.clicked(0,0,ClickType.PICKUP,first);
        h.assertTrue(stale.getCarried().isEmpty() && first.getInventory().countItem(oldPreview.getItem())==0,
                "A stale normal result pickup must not grant virtual inlay when only mirrored glass can be paid");
        h.assertTrue(bench.getVis()==50 && bench.getItem(0).is(Items.GLASS_PANE)
                && bench.getItem(8).is(ThaumcraftItems.QUICKSILVER.get())
                && bench.getItem(11).getCount()==1 && bench.getItem(13).getCount()==1,
                "Rejected stale pickup cannot debit the unrelated current recipe");
        first.getInventory().clearContent(); second.getInventory().clearContent(); h.succeed();
    }

    private static ArcaneWorkbenchBlockEntity bench(GameTestHelper h,BlockPos pos) {
        h.getLevel().setBlockAndUpdate(pos,ThaumcraftBlocks.ARCANE_WORKBENCH.get().defaultBlockState());
        return (ArcaneWorkbenchBlockEntity)h.getLevel().getBlockEntity(pos);
    }
    private static void inlay(ArcaneWorkbenchBlockEntity bench) {
        bench.setItem(2,new ItemStack(Items.GOLD_INGOT)); bench.setItem(7,new ItemStack(Items.REDSTONE));
        bench.setItem(11,CrystalEssenceItem.ofAspect("aqua"));
    }
    private static ServerPlayer actor(GameTestHelper h,BlockPos pos) {
        var actor=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"WorkbenchProof"));
        actor.setGameMode(GameType.SURVIVAL); actor.getInventory().clearContent();
        actor.moveTo(Vec3.atBottomCenterOf(pos.north()));
        var knowledge=actor.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge();
        knowledge.addResearch("INFUSIONSTABLE"); knowledge.addResearch("BASEARTIFICE");
        return actor;
    }
}
