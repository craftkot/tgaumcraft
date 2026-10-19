package dev.ehtid.thaumcraft.golem.test;

import com.mojang.authlib.GameProfile;
import dev.ehtid.thaumcraft.blockentity.ArcaneWorkbenchBlockEntity;
import dev.ehtid.thaumcraft.blockentity.EssentiaJarBlockEntity;
import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.golem.crafting.GolemComponentCrafting;
import dev.ehtid.thaumcraft.golem.press.GolemPressBlockEntity;
import dev.ehtid.thaumcraft.item.CrystalEssenceItem;
import dev.ehtid.thaumcraft.item.PlateItem;
import dev.ehtid.thaumcraft.menu.ArcaneWorkbenchMenu;
import dev.ehtid.thaumcraft.player.ThaumcraftPlayerAttachments;
import dev.ehtid.thaumcraft.port.core.ArcaneWorkbenchRuntime;
import dev.ehtid.thaumcraft.registry.ThaumcraftBlocks;
import dev.ehtid.thaumcraft.registry.ThaumcraftItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
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
public final class GolemComponentGameTests {
    private static final BlockPos BENCH = new BlockPos(5, 1, 5);
    private GolemComponentGameTests() {}
    private record Fixture(ArcaneWorkbenchBlockEntity bench, FakePlayer player, ArcaneWorkbenchMenu menu) {}

    @GameTest(template="golem_test", batch="component_mechanism", timeoutTicks=40)
    public static void actualMechanismMenuPaysBothCrystalsAndSourcePlateMatrix(GameTestHelper h) {
        var f = fixture(h); mechanism(f.bench, 2); crystals(f.bench, 3); f.bench.setVis(100);
        f.menu.broadcastChanges();
        h.assertTrue(f.menu.getSlot(0).getItem().isEmpty() && f.menu.displayedVisCost() == 10 && f.menu.requiredCrystalMask() == 6,
                "Source cost visible before research; two-crystal mask");
        h.assertTrue(GolemComponentCrafting.craft(f.bench, f.player, new ItemStack(GolemModule.SIMPLE_MECHANISM.get()), 0).isEmpty(), "Unknown research pays nothing");
        knowledge(f).addResearch("BASEARTIFICE");
        f.bench.setItem(11, ItemStack.EMPTY); f.menu.broadcastChanges();
        h.assertTrue(f.menu.getSlot(0).getItem().isEmpty() && f.bench.getVis() == 100 && f.bench.getItem(1).getCount() == 2,
                "Missing second crystal does not debit first or materials");
        f.bench.setItem(11, CrystalEssenceItem.ofAspect("aqua")); f.bench.getItem(11).setCount(3);
        f.bench.setItem(3, new ItemStack(Items.IRON_INGOT, 2)); f.menu.broadcastChanges();
        h.assertTrue(f.menu.getSlot(0).getItem().isEmpty(), "Iron ingot is not an iron plate");
        f.bench.setItem(3, plate(1, 2)); f.menu.broadcastChanges();
        h.assertTrue(f.menu.getSlot(0).getItem().is(GolemModule.SIMPLE_MECHANISM.get()), "Paid mechanism preview");
        f.menu.clicked(0, 1, ClickType.PICKUP, f.player);
        h.assertTrue(f.menu.getCarried().is(GolemModule.SIMPLE_MECHANISM.get()) && f.menu.getCarried().getCount() == 1
                && f.bench.getVis() == 90 && f.bench.getItem(10).getCount() == 2 && f.bench.getItem(11).getCount() == 2,
                "Right click grants exactly one,10vis/Ignis1/Aqua1 debited");
        h.assertTrue(f.bench.getItem(1).getCount() == 1 && f.bench.getItem(3).getCount() == 1 && f.bench.getItem(4).getCount() == 1
                && f.bench.getItem(5).getCount() == 1 && f.bench.getItem(7).getCount() == 1 && f.bench.getItem(9).getCount() == 3,
                "Five actual input slots debit once, unrelated Aer retained");
        f.menu.setCarried(ItemStack.EMPTY); f.menu.quickMoveStack(f.player, 0);
        h.assertTrue(count(f.player.getInventory(), GolemModule.SIMPLE_MECHANISM.get()) == 1 && f.bench.getVis() == 80
                && f.bench.getItem(1).isEmpty() && f.bench.getItem(10).getCount() == 1, "Shift craft also pays exactly one full recipe");
        f.menu.broadcastChanges(); h.assertTrue(f.menu.getSlot(0).getItem().isEmpty(), "No replay after materials spent");
        f.player.getInventory().clearContent(); h.succeed();
    }

    @GameTest(template="golem_test", batch="component_mind", timeoutTicks=40)
    public static void clockworkMindRequiresStageTwoComparatorAndOnlyIgnisOrdo(GameTestHelper h) {
        var f = fixture(h); mind(f.bench, 2); crystals(f.bench, 3); f.bench.setVis(100);
        knowledge(f).addResearch("MINDCLOCKWORK"); knowledge(f).setResearchStage("MINDCLOCKWORK", 1); f.menu.broadcastChanges();
        h.assertTrue(f.menu.getSlot(0).getItem().isEmpty() && f.menu.displayedVisCost() == 25 && f.menu.requiredCrystalMask() == 18,
                "MINDCLOCKWORK@1 cannot craft @2 recipe; cost remains visible");
        knowledge(f).setResearchStage("MINDCLOCKWORK", 2); f.bench.setVis(24); f.menu.broadcastChanges();
        h.assertTrue(f.menu.getSlot(0).getItem().isEmpty(), "24vis insufficient for mind");
        f.bench.setVis(100); f.bench.setItem(7, new ItemStack(Items.CLOCK, 2)); f.menu.broadcastChanges();
        h.assertTrue(f.menu.getSlot(0).getItem().isEmpty(), "Clock cannot replace original comparator");
        f.bench.setItem(7, new ItemStack(Items.COMPARATOR, 2));
        f.bench.getItem(13).set(DataComponents.CUSTOM_DATA, CrystalEssenceItem.ofAspect("aqua").get(DataComponents.CUSTOM_DATA));
        f.menu.broadcastChanges(); h.assertTrue(f.menu.getSlot(0).getItem().isEmpty(), "Mutated Ordo components rechecked at payment");
        f.bench.setItem(13, CrystalEssenceItem.ofAspect("ordo")); f.bench.getItem(13).setCount(3);
        f.menu.broadcastChanges(); f.menu.clicked(0, 0, ClickType.PICKUP, f.player);
        h.assertTrue(f.menu.getCarried().is(GolemModule.CLOCKWORK_MIND.get()) && f.menu.getCarried().getCount() == 1
                && f.bench.getVis() == 75 && f.bench.getItem(10).getCount() == 2 && f.bench.getItem(13).getCount() == 2,
                "One basic mind pays25vis/Ignis1/Ordo1");
        h.assertTrue(f.bench.getItem(1).getCount() == 1 && f.bench.getItem(3).getCount() == 1 && f.bench.getItem(4).getCount() == 1
                && f.bench.getItem(5).getCount() == 1 && f.bench.getItem(6).getCount() == 1 && f.bench.getItem(7).getCount() == 1
                && f.bench.getItem(8).getCount() == 1 && f.bench.getItem(11).getCount() == 3, "Seven inputs paid; Aqua untouched");
        h.succeed();
    }

    @GameTest(template="golem_test", batch="component_stale", timeoutTicks=40)
    public static void staleVirtualOutputCannotGrantMechanismAgainstDifferentPayableRecipe(GameTestHelper h) {
        var f = fixture(h); knowledge(f).addResearch("BASEARTIFICE"); knowledge(f).addResearch("MINDCLOCKWORK");
        knowledge(f).setResearchStage("MINDCLOCKWORK", 2); mechanism(f.bench, 1); crystals(f.bench, 3); f.bench.setVis(100);
        f.menu.broadcastChanges(); h.assertTrue(f.menu.getSlot(0).getItem().is(GolemModule.SIMPLE_MECHANISM.get()), "Capture virtual mechanism preview");
        mind(f.bench, 1); // Change the actual backing grid without refreshing the virtual slot.
        h.assertTrue(!f.menu.getSlot(0).mayPickup(f.player), "Exact product checked, rather than any craftable recipe");
        f.menu.clicked(0, 0, ClickType.PICKUP, f.player);
        h.assertTrue(f.menu.getCarried().isEmpty() && f.bench.getVis() == 100 && f.bench.getItem(4).getCount() == 1,
                "Stale cursor take cannot grant a free old preview or debit current mind");
        f.menu.broadcastChanges();
        var other = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "OtherCrafter")); other.moveTo(f.player.position());
        h.assertTrue(!f.menu.getSlot(0).mayPickup(other), "Output bound to menu viewer");
        for (int i = 0; i < 36; i++) f.player.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        h.assertTrue(f.menu.quickMoveStack(f.player, 0).isEmpty() && f.bench.getVis() == 100, "Full inventory costs nothing on shift take");
        f.player.getInventory().clearContent(); h.succeed();
    }

    @GameTest(template="golem_test", batch="component_constraints", timeoutTicks=40)
    public static void discountsAndServerContextCannotBypassComponentCosts(GameTestHelper h) {
        var f = fixture(h); knowledge(f).addResearch("BASEARTIFICE"); mechanism(f.bench, 2); crystals(f.bench, 3); f.bench.setVis(100);
        ItemStack expected = new ItemStack(GolemModule.SIMPLE_MECHANISM.get());
        for (float discount : new float[]{Float.NaN, Float.POSITIVE_INFINITY, -.01F, 1.01F})
            h.assertTrue(GolemComponentCrafting.craft(f.bench, f.player, expected, discount).isEmpty(), "Invalid discount rejected");
        h.assertTrue(GolemComponentCrafting.craft(f.bench, f.player, new ItemStack(GolemModule.CLOCKWORK_MIND.get()), 0).isEmpty(),
                "Client cannot select a more expensive product");
        f.player.setGameMode(GameType.SPECTATOR);
        h.assertTrue(!GolemComponentCrafting.canCraft(f.bench, f.player, 0), "Spectator blocked"); f.player.setGameMode(GameType.SURVIVAL);
        f.player.moveTo(f.player.position().add(20, 0, 0));
        h.assertTrue(!GolemComponentCrafting.canCraft(f.bench, f.player, 0), "Actual workbench range checked");
        f.player.moveTo(Vec3.atBottomCenterOf(h.absolutePos(BENCH).east()));
        f.bench.setItem(0, new ItemStack(Items.DIRT));
        h.assertTrue(!GolemComponentCrafting.canCraft(f.bench, f.player, 0), "Nonempty source corner invalidates3x3 pattern"); f.bench.setItem(0, ItemStack.EMPTY);
        ItemStack paid = GolemComponentCrafting.craft(f.bench, f.player, expected, .25F);
        h.assertTrue(paid.is(GolemModule.SIMPLE_MECHANISM.get()) && paid.getCount() == 1 && f.bench.getVis() == 93
                && f.bench.getItem(10).getCount() == 2 && f.bench.getItem(11).getCount() == 2, "Source integer truncation7vis; crystals not discounted");
        h.succeed();
    }

    @GameTest(template="golem_test", batch="component_press_chain", timeoutTicks=160)
    public static void threePaidMechanismsAndMindActuallyFeedOneMachinaPressCycle(GameTestHelper h) {
        var f = fixture(h); knowledge(f).addResearch("MATSTUDWOOD"); var inv = f.player.getInventory(); knowledge(f).addResearch("BASEARTIFICE");
        knowledge(f).addResearch("MINDCLOCKWORK"); knowledge(f).setResearchStage("MINDCLOCKWORK", 2);
        inv.setItem(0, plate(0, 8)); inv.setItem(1, plate(1, 6)); inv.setItem(2, new ItemStack(Items.STICK, 3));
        inv.setItem(3, new ItemStack(Items.GLASS_PANE, 3)); inv.setItem(4, new ItemStack(Items.COMPARATOR));
        inv.setItem(5, new ItemStack(ThaumcraftItems.PLANK_GREATWOOD.get(), 3)); crystals(f.bench, 4); f.bench.setVis(100);
        for (int craft = 0; craft < 3; craft++) {
            clearGrid(f.bench);
            for (int slot : new int[]{1, 7}) f.bench.setItem(slot, inv.getItem(0).split(1));
            for (int slot : new int[]{3, 5}) f.bench.setItem(slot, inv.getItem(1).split(1));
            f.bench.setItem(4, inv.getItem(2).split(1)); f.menu.broadcastChanges();
            f.menu.clicked(0, 0, ClickType.PICKUP, f.player);
            h.assertTrue(f.menu.getCarried().is(GolemModule.SIMPLE_MECHANISM.get()) && inv.add(f.menu.getCarried()), "Real paid mechanism moved to actual player inventory");
            f.menu.setCarried(ItemStack.EMPTY);
        }
        h.assertTrue(count(inv, GolemModule.SIMPLE_MECHANISM.get()) == 3 && f.bench.getVis() == 70, "Three actual mechanisms,30vis");
        clearGrid(f.bench);
        for (int slot : new int[]{1, 3, 5}) f.bench.setItem(slot, inv.getItem(3).split(1));
        for (int slot : new int[]{6, 8}) f.bench.setItem(slot, inv.getItem(0).split(1));
        f.bench.setItem(7, inv.getItem(4).split(1));
        for (int slot = 0; slot < 36; slot++) if (inv.getItem(slot).is(GolemModule.SIMPLE_MECHANISM.get())) { f.bench.setItem(4, inv.getItem(slot).split(1)); break; }
        f.menu.broadcastChanges(); f.menu.clicked(0, 0, ClickType.PICKUP, f.player);
        h.assertTrue(f.menu.getCarried().is(GolemModule.CLOCKWORK_MIND.get()) && inv.add(f.menu.getCarried()), "One of actual mechanisms paid into actual mind");
        f.menu.setCarried(ItemStack.EMPTY);
        h.assertTrue(f.bench.getVis() == 45 && count(inv, GolemModule.SIMPLE_MECHANISM.get()) == 2
                && count(inv, GolemModule.CLOCKWORK_MIND.get()) == 1 && f.bench.getItem(10).isEmpty()
                && f.bench.getItem(11).getCount() == 1 && f.bench.getItem(13).getCount() == 3, "Exact55vis/Ignis4/Aqua3/Ordo1 across full ingredient chain");
        BlockPos pos = new BlockPos(8, 2, 8);
        GolemFormationGameTests.placeSource(h.getLevel(), h.absolutePos(pos.west()), 0);
        h.setBlock(pos.below(), ThaumcraftBlocks.JAR_NORMAL.get());
        var jar = (EssentiaJarBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(pos.below()));
        for (int unit = 0; unit < 8; unit++) h.assertTrue(jar.addFromTransport("machina", 1, Direction.UP) == 1, "Fixture actual source stock");
        f.player.moveTo(Vec3.atBottomCenterOf(h.absolutePos(pos).east()));
        h.assertTrue(inv.getItem(8).isEmpty(), "Fixture Salis slot does not replace earned components");
        inv.selected = 8; inv.setItem(8, new ItemStack(ThaumcraftItems.SALIS_MUNDUS.get()));
        h.assertTrue(GolemFormationGameTests.use(f.player,h.absolutePos(pos),Direction.UP).consumesAction() && inv.getItem(8).isEmpty(),"One real Salis paid to assemble constituent blocks");
        h.startSequence().thenIdle(55).thenExecute(() -> {
            var press=(GolemPressBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(pos));
            h.assertTrue(press!=null && press.start(f.player) && inv.isEmpty(),"Formed press consumes only actual remaining2 mechanisms+mind+3wood");
        }).thenIdle(45).thenExecute(() -> {
            var press=(GolemPressBlockEntity)h.getLevel().getBlockEntity(h.absolutePos(pos));
            h.assertTrue(!press.job().active() && jar.snapshot().amount() == 0
                && press.getItem(0).is(GolemModule.GOLEM_PLACER.get()) && press.getItem(0).getCount() == 1,
                "Paid raw materials→arcane components→8 Machina→exactly one real golem product");
        }).thenSucceed();
    }

    private static Fixture fixture(GameTestHelper h) {
        h.setBlock(BENCH, ThaumcraftBlocks.ARCANE_WORKBENCH.get());
        var bench = (ArcaneWorkbenchBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(BENCH));
        var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "ComponentCrafter"));
        player.setGameMode(GameType.SURVIVAL); player.getInventory().clearContent(); player.moveTo(Vec3.atBottomCenterOf(h.absolutePos(BENCH).east()));
        return new Fixture(bench, player, new ArcaneWorkbenchMenu(19, player.getInventory(), bench));
    }
    private static dev.ehtid.thaumcraft.port.core.PlayerKnowledgeData knowledge(Fixture f) { return f.player.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge(); }
    private static void clearGrid(Container bench) { for (int slot = 0; slot < 9; slot++) bench.setItem(slot, ItemStack.EMPTY); }
    private static void mechanism(Container bench, int copies) {
        clearGrid(bench); for (int slot : new int[]{1, 7}) bench.setItem(slot, plate(0, copies));
        for (int slot : new int[]{3, 5}) bench.setItem(slot, plate(1, copies)); bench.setItem(4, new ItemStack(Items.STICK, copies));
    }
    private static void mind(Container bench, int copies) {
        clearGrid(bench); for (int slot : new int[]{1, 3, 5}) bench.setItem(slot, new ItemStack(Items.GLASS_PANE, copies));
        for (int slot : new int[]{6, 8}) bench.setItem(slot, plate(0, copies)); bench.setItem(4, new ItemStack(GolemModule.SIMPLE_MECHANISM.get(), copies));
        bench.setItem(7, new ItemStack(Items.COMPARATOR, copies));
    }
    private static ItemStack plate(int meta, int count) { ItemStack stack = PlateItem.create(meta); stack.setCount(count); return stack; }
    private static void crystals(Container bench, int count) {
        for (int i = 0; i < 6; i++) { var stack = CrystalEssenceItem.ofAspect(ArcaneWorkbenchRuntime.PRIMAL_ORDER[i]); stack.setCount(count); bench.setItem(9 + i, stack); }
    }
    private static int count(Inventory inventory, net.minecraft.world.item.Item item) {
        int count = 0; for (var stack : inventory.items) if (stack.is(item)) count += stack.getCount(); return count;
    }
}
