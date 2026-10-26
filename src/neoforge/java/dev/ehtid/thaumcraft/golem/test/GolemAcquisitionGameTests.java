package dev.ehtid.thaumcraft.golem.test;

import com.mojang.authlib.GameProfile;
import dev.ehtid.thaumcraft.blockentity.ArcaneWorkbenchBlockEntity;
import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.golem.crafting.GolemComponentCrafting;
import dev.ehtid.thaumcraft.golem.press.GolemPressBlockEntity;
import dev.ehtid.thaumcraft.item.CrystalEssenceItem;
import dev.ehtid.thaumcraft.item.GolemPlacerItem;
import dev.ehtid.thaumcraft.item.PlateItem;
import dev.ehtid.thaumcraft.menu.ArcaneWorkbenchMenu;
import dev.ehtid.thaumcraft.player.ThaumcraftPlayerAttachments;
import dev.ehtid.thaumcraft.port.core.ArcaneWorkbenchRuntime;
import dev.ehtid.thaumcraft.port.core.PlayerKnowledgeData;
import dev.ehtid.thaumcraft.registry.ThaumcraftBlocks;
import dev.ehtid.thaumcraft.registry.ThaumcraftItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.UUID;

@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class GolemAcquisitionGameTests {
    private static final BlockPos BENCH = new BlockPos(5, 1, 5);
    private GolemAcquisitionGameTests() {}
    private record Fixture(ArcaneWorkbenchBlockEntity bench, FakePlayer player, ArcaneWorkbenchMenu menu) {}

    @GameTest(template="golem_test", batch="acquisition_vision", timeoutTicks=40)
    public static void visionModuleUsesOriginalArcaneGateBottlesFermentedEyesAndAqua(GameTestHelper h) {
        var f = fixture(h); vision(f.bench, 2); crystals(f.bench, 3); f.bench.setVis(100);
        f.menu.broadcastChanges();
        h.assertTrue(f.menu.getSlot(0).getItem().isEmpty() && f.menu.displayedVisCost() == 50 && f.menu.requiredCrystalMask() == 4,
                "Original vision arcane costs visible before research");
        h.assertTrue(GolemComponentCrafting.craft(f.bench, f.player, new ItemStack(GolemModule.VISION_MODULE.get()), 0).isEmpty(),
                "Unknown GOLEMVISION cannot spend materials");
        knowledge(f).addResearch("GOLEMVISION");
        f.bench.setItem(3, new ItemStack(Items.SPIDER_EYE, 2));
        h.assertTrue(GolemComponentCrafting.preview(f.bench, f.player, 0).isEmpty(), "Unfermented eye is not the source ingredient");
        f.bench.setItem(3, new ItemStack(Items.FERMENTED_SPIDER_EYE, 2)); f.bench.setItem(11, ItemStack.EMPTY);
        h.assertTrue(GolemComponentCrafting.preview(f.bench, f.player, 0).isEmpty(), "Other primal crystals cannot pay missing Aqua");
        f.bench.setItem(11, CrystalEssenceItem.ofAspect("aqua")); f.bench.getItem(11).setCount(3);
        f.bench.setVis(49); h.assertTrue(GolemComponentCrafting.preview(f.bench, f.player, 0).isEmpty(), "49vis cannot buy50vis module");
        f.bench.setVis(100); f.menu.broadcastChanges();
        h.assertTrue(f.menu.getSlot(0).getItem().is(GolemModule.VISION_MODULE.get()), "Actual registered vision module preview");
        f.menu.clicked(0, 1, ClickType.PICKUP, f.player);
        h.assertTrue(f.menu.getCarried().is(GolemModule.VISION_MODULE.get()) && f.menu.getCarried().getCount() == 1
                && f.bench.getVis() == 50 && f.bench.getItem(11).getCount() == 2 && f.bench.getItem(10).getCount() == 3,
                "One right-click batch pays50vis and Aqua1; Ignis retained");
        for (int slot : new int[]{0, 2, 3, 5, 6, 7, 8}) h.assertTrue(f.bench.getItem(slot).getCount() == 1, "All seven vision input slots paid once");
        h.assertTrue(f.bench.getItem(0).is(Items.GLASS_BOTTLE), "Consumed bottle input does not create a second bottle remainder");
        f.menu.setCarried(ItemStack.EMPTY); f.menu.quickMoveStack(f.player, 0);
        h.assertTrue(count(f, GolemModule.VISION_MODULE.get()) == 1 && f.bench.getVis() == 0 && f.bench.getItem(11).getCount() == 1,
                "Shift take pays second whole module");
        f.menu.broadcastChanges(); h.assertTrue(f.menu.getSlot(0).getItem().isEmpty(), "Spent source materials cannot replay");
        f.player.getInventory().clearContent(); h.succeed();
    }

    @GameTest(template="golem_test", batch="acquisition_aggression", timeoutTicks=40)
    public static void aggressionModuleUsesSealGuardGateGlassPanesBlazePowderAndIgnis(GameTestHelper h) {
        var f = fixture(h); aggression(f.bench, 2); crystals(f.bench, 3); f.bench.setVis(100);
        knowledge(f).addResearch("GOLEMCOMBATADV"); f.menu.broadcastChanges();
        h.assertTrue(f.menu.getSlot(0).getItem().isEmpty() && f.menu.displayedVisCost() == 50 && f.menu.requiredCrystalMask() == 2,
                "GOLEMCOMBATADV cannot replace source SEALGUARD recipe gate");
        knowledge(f).addResearch("SEALGUARD"); f.bench.setItem(4, new ItemStack(Items.BLAZE_ROD, 2));
        h.assertTrue(GolemComponentCrafting.preview(f.bench, f.player, 0).isEmpty(), "Blaze rod cannot replace powder");
        f.bench.setItem(4, new ItemStack(Items.BLAZE_POWDER, 2));
        // Source paneGlass ore registration includes stained panes with wildcard colour.
        f.bench.setItem(5, new ItemStack(Items.BLUE_STAINED_GLASS_PANE, 2)); f.bench.setItem(0, new ItemStack(Items.GLASS_PANE));
        h.assertTrue(GolemComponentCrafting.preview(f.bench, f.player, 0).isEmpty(), "Original empty top corners remain mandatory");
        f.bench.setItem(0, ItemStack.EMPTY); f.bench.setItem(10, ItemStack.EMPTY);
        h.assertTrue(GolemComponentCrafting.preview(f.bench, f.player, 0).isEmpty(), "Aqua cannot replace source Ignis");
        f.bench.setItem(10, CrystalEssenceItem.ofAspect("ignis")); f.bench.getItem(10).setCount(3);
        f.menu.broadcastChanges(); f.menu.clicked(0, 0, ClickType.PICKUP, f.player);
        h.assertTrue(f.menu.getCarried().is(GolemModule.AGGRESSION_MODULE.get()) && f.menu.getCarried().getCount() == 1
                && f.bench.getVis() == 50 && f.bench.getItem(10).getCount() == 2 && f.bench.getItem(11).getCount() == 3,
                "One real aggression module pays50vis and Ignis1; Aqua retained");
        for (int slot : new int[]{1, 3, 4, 5, 6, 7, 8}) h.assertTrue(f.bench.getItem(slot).getCount() == 1, "All seven aggression slots paid once");
        h.succeed();
    }

    @GameTest(template="golem_test", batch="acquisition_stale", timeoutTicks=40)
    public static void moduleProductsCannotBeSwappedThroughStalePreviewOrFullOutputSpace(GameTestHelper h) {
        var f = fixture(h); vision(f.bench, 2); crystals(f.bench, 3); f.bench.setVis(100);
        knowledge(f).addResearch("GOLEMVISION"); knowledge(f).addResearch("SEALGUARD"); f.menu.broadcastChanges();
        h.assertTrue(f.menu.getSlot(0).getItem().is(GolemModule.VISION_MODULE.get()), "Capture real vision preview");
        aggression(f.bench, 2);
        h.assertTrue(!f.menu.getSlot(0).mayPickup(f.player), "Same-cost aggression grid cannot grant stale vision product");
        f.menu.clicked(0, 0, ClickType.PICKUP, f.player);
        h.assertTrue(f.menu.getCarried().isEmpty() && f.bench.getVis() == 100 && f.bench.getItem(7).getCount() == 2,
                "Stale attempt debits neither crystal nor mechanism");
        f.menu.broadcastChanges(); f.menu.setCarried(new ItemStack(GolemModule.AGGRESSION_MODULE.get(), 64));
        f.menu.clicked(0, 0, ClickType.PICKUP, f.player);
        h.assertTrue(f.menu.getCarried().getCount() == 64 && f.bench.getVis() == 100, "Full cursor cannot spend recipe");
        f.menu.setCarried(ItemStack.EMPTY);
        for (int slot = 0; slot < 36; slot++) f.player.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        h.assertTrue(f.menu.quickMoveStack(f.player, 0).isEmpty() && f.bench.getVis() == 100 && f.bench.getItem(10).getCount() == 3,
                "Full inventory shift take cannot debit materials or Ignis");
        f.player.getInventory().clearContent(); h.succeed();
    }

    @GameTest(template="golem_test", batch="acquisition_discount", timeoutTicks=40)
    public static void modulePaymentRetainsExactExpectedProductAndDiscountRules(GameTestHelper h) {
        var f = fixture(h); vision(f.bench, 2); crystals(f.bench, 3); f.bench.setVis(100); knowledge(f).addResearch("GOLEMVISION");
        for (float invalid : new float[]{Float.NaN, Float.POSITIVE_INFINITY, -.01F, 1.01F})
            h.assertTrue(GolemComponentCrafting.craft(f.bench, f.player, new ItemStack(GolemModule.VISION_MODULE.get()), invalid).isEmpty(),
                    "Invalid discount cannot buy module");
        h.assertTrue(GolemComponentCrafting.craft(f.bench, f.player, new ItemStack(GolemModule.AGGRESSION_MODULE.get()), 0).isEmpty(),
                "Client-selected other module cannot change product");
        var result = GolemComponentCrafting.craft(f.bench, f.player, new ItemStack(GolemModule.VISION_MODULE.get()), .25F);
        h.assertTrue(result.is(GolemModule.VISION_MODULE.get()) && result.getCount() == 1 && f.bench.getVis() == 63
                && f.bench.getItem(11).getCount() == 2 && f.bench.getItem(7).getCount() == 1,
                "Source integer truncation charges37vis plus full Aqua1/mechanism1");
        h.succeed();
    }

    @GameTest(template="golem_test", batch="acquisition_press", timeoutTicks=100)
    public static void earnedArcaneVisionModuleIsDebitedByActualScoutPressJob(GameTestHelper h) {
        var f = fixture(h); vision(f.bench, 1); crystals(f.bench, 1); f.bench.setVis(50); knowledge(f).addResearch("GOLEMVISION");
        f.menu.broadcastChanges(); f.menu.clicked(0, 0, ClickType.PICKUP, f.player);
        h.assertTrue(f.menu.getCarried().is(GolemModule.VISION_MODULE.get()) && f.player.getInventory().add(f.menu.getCarried()),
                "Transfer actual paid module into actual player inventory");
        f.menu.setCarried(ItemStack.EMPTY); knowledge(f).addResearch("MATSTUDWOOD"); knowledge(f).addResearch("MINDCLOCKWORK");
        f.player.getInventory().setItem(1, new ItemStack(ThaumcraftItems.PLANK_GREATWOOD.get(), 3));
        f.player.getInventory().setItem(2, new ItemStack(GolemModule.SIMPLE_MECHANISM.get(), 2));
        f.player.getInventory().setItem(3, new ItemStack(GolemModule.CLOCKWORK_MIND.get()));
        BlockPos relative = new BlockPos(8, 2, 8); h.setBlock(relative, GolemModule.PRESS.get());
        var press = (GolemPressBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(relative));
        f.player.moveTo(Vec3.atBottomCenterOf(h.absolutePos(relative).east()));
        h.assertTrue(press.select(f.player, 1, 1) && press.selection().head().id() == 3 && press.start(f.player)
                && f.player.getInventory().isEmpty(), "Scout selection consumes earned module with3wood/2mechanisms/mind");
        int cost = press.job().maximum();
        for (int unit = 0; unit < cost; unit++) {
            h.assertTrue(press.addTransport("machina", 1, Direction.DOWN) == 1, "Fixture pays one real accepted Machina credit");
            for (int tick = 0; tick < 5; tick++) GolemPressBlockEntity.tick(h.getLevel(), press.getBlockPos(), press.getBlockState(), press);
        }
        h.assertTrue(!press.job().active() && press.getItem(0).getCount() == 1
                && ItemStack.isSameItemSameComponents(press.getItem(0), GolemPlacerItem.stack(press.selection(), 0)),
                "Paid module contributes to exactly one actual press product");
        h.succeed();
    }

    private static Fixture fixture(GameTestHelper h) {
        h.setBlock(BENCH, ThaumcraftBlocks.ARCANE_WORKBENCH.get());
        var bench = (ArcaneWorkbenchBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(BENCH));
        var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "GolemAcquisition"));
        player.setGameMode(GameType.SURVIVAL); player.getInventory().clearContent(); player.moveTo(Vec3.atBottomCenterOf(h.absolutePos(BENCH).east()));
        return new Fixture(bench, player, new ArcaneWorkbenchMenu(28, player.getInventory(), bench));
    }
    private static PlayerKnowledgeData knowledge(Fixture f) { return f.player.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge(); }
    private static void clear(Container bench) { for (int slot = 0; slot < 9; slot++) bench.setItem(slot, ItemStack.EMPTY); }
    private static void vision(Container bench, int count) {
        clear(bench);
        for (int slot : new int[]{0, 2}) bench.setItem(slot, new ItemStack(Items.GLASS_BOTTLE, count));
        for (int slot : new int[]{3, 5}) bench.setItem(slot, new ItemStack(Items.FERMENTED_SPIDER_EYE, count));
        base(bench, count);
    }
    private static void aggression(Container bench, int count) {
        clear(bench);
        for (int slot : new int[]{1, 3, 5}) bench.setItem(slot, new ItemStack(Items.GLASS_PANE, count));
        bench.setItem(4, new ItemStack(Items.BLAZE_POWDER, count)); base(bench, count);
    }
    private static void base(Container bench, int count) {
        for (int slot : new int[]{6, 8}) { var plate = PlateItem.create(0); plate.setCount(count); bench.setItem(slot, plate); }
        bench.setItem(7, new ItemStack(GolemModule.SIMPLE_MECHANISM.get(), count));
    }
    private static void crystals(Container bench, int count) {
        for (int primal = 0; primal < 6; primal++) {
            var stack = CrystalEssenceItem.ofAspect(ArcaneWorkbenchRuntime.PRIMAL_ORDER[primal]); stack.setCount(count); bench.setItem(9 + primal, stack);
        }
    }
    private static int count(Fixture f, Item item) { return f.player.getInventory().items.stream().filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum(); }
}
