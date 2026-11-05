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
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** Actual workbench menu acquisition with paid aura, crystals, research and inventory ingredients. */
@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class InfusionStabilityArcaneGameTests {
    private InfusionStabilityArcaneGameTests() {}

    @GameTest(template = "infusion_paid_blank", batch = "infusion_stability_arcane", timeoutTicks = 20)
    public static void speedGlyphRequiresDiamondAndPaysActualWorkbenchMenu(GameTestHelper helper) {
        glyph(helper, Kind.SPEED);
    }

    @GameTest(template = "infusion_paid_blank", batch = "infusion_stability_arcane", timeoutTicks = 20)
    public static void costGlyphRequiresAllThreePrimalCrystals(GameTestHelper helper) {
        glyph(helper, Kind.COST);
    }

    private static void glyph(GameTestHelper helper, Kind kind) {
        BlockPos pos = helper.absolutePos(new BlockPos(12, 5, 12));
        helper.getLevel().setBlockAndUpdate(pos, ThaumcraftBlocks.ARCANE_WORKBENCH.get().defaultBlockState());
        var bench = (ArcaneWorkbenchBlockEntity) helper.getLevel().getBlockEntity(pos);
        ServerPlayer actor = player(helper, pos);
        var knowledge = actor.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge();
        for (int slot : new int[]{0, 2, 6, 8}) bench.setItem(slot, new ItemStack(ThaumcraftItems.STONE_ARCANE.get(), 2));
        for (int slot : new int[]{1, 3, 5, 7}) bench.setItem(slot, new ItemStack(kind == Kind.SPEED
                ? ThaumcraftItems.NITOR.get(DyeColor.BLUE).get() : ThaumcraftItems.ALUMENTUM.get(), 2));
        bench.setItem(4, new ItemStack(Items.GLOWSTONE, 2));
        bench.setVis(1000);
        crystals(bench, kind);
        helper.assertTrue(InfusionStabilityArcaneAdapter.selected(bench) == null,
                "Glowstone cannot substitute for original diamond block glyph center");
        bench.setItem(4, new ItemStack(Items.DIAMOND_BLOCK, 2));
        var menu = new ArcaneWorkbenchMenu(1, actor.getInventory(), bench);
        menu.broadcastChanges();
        helper.assertTrue(menu.displayedVisCost() == 500 && menu.requiredCrystalMask() == kind.crystalMask,
                "Source price and aspect mask are shown before research gates");
        helper.assertTrue(!menu.getSlot(0).hasItem(), "Missing source research hides paid result");
        helper.assertTrue(bench.getVis() == 1000 && bench.getItem(4).getCount() == 2,
                "Research rejection preserves physical aura and diamond blocks");
        knowledge.addResearch(kind.research); // Explicit prerequisite fixture, not natural research progression.
        int required = kind == Kind.SPEED ? 13 : 14;
        ItemStack held = bench.removeItem(required, 2);
        menu.broadcastChanges();
        helper.assertTrue(!menu.getSlot(0).hasItem() && bench.getVis() == 1000,
                "Missing one actual required crystal prevents every debit");
        bench.setItem(required, held);
        bench.setVis(499);
        menu.broadcastChanges();
        helper.assertTrue(!menu.getSlot(0).hasItem(), "Insufficient actual aura cannot grant glyph");
        bench.setVis(1000);
        menu.broadcastChanges();
        helper.assertTrue(menu.getSlot(0).getItem().is(InfusionStabilityArcaneAdapter.output(kind).getItem()),
                "Authorized source recipe appears in actual result slot");
        menu.clicked(0, 0, ClickType.PICKUP, actor);
        helper.assertTrue(ItemStack.matches(menu.getCarried(), InfusionStabilityArcaneAdapter.output(kind)),
                "Actual menu click receives exactly one source glyph");
        helper.assertTrue(bench.getVis() == 500, "Workbench click pays exactly five hundred real vis");
        for (int slot = 0; slot < 9; slot++) helper.assertTrue(bench.getItem(slot).getCount() == 1,
                "Every original ingredient physically loses exactly one");
        for (int primal = 0; primal < 6; primal++) if ((kind.crystalMask & 1 << primal) != 0)
            helper.assertTrue(bench.getItem(9 + primal).getCount() == 1, "Required primal crystal loses exactly one");
        actor.getInventory().clearContent();
        helper.succeed();
    }

    @GameTest(template = "infusion_paid_blank", batch = "infusion_stability_arcane", timeoutTicks = 20)
    public static void shapelessInlayShiftClickReturnsExactlyTwoAfterOnePaidCraft(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(12, 5, 12));
        helper.getLevel().setBlockAndUpdate(pos, ThaumcraftBlocks.ARCANE_WORKBENCH.get().defaultBlockState());
        var bench = (ArcaneWorkbenchBlockEntity) helper.getLevel().getBlockEntity(pos);
        ServerPlayer actor = player(helper, pos);
        actor.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge().addResearch("INFUSIONSTABLE");
        bench.setItem(2, new ItemStack(Items.GOLD_INGOT, 2));
        bench.setItem(7, new ItemStack(Items.REDSTONE, 2));
        ItemStack aqua = CrystalEssenceItem.ofAspect("aqua"); aqua.setCount(2);
        bench.setItem(11, aqua);
        bench.setVis(50);
        var menu = new ArcaneWorkbenchMenu(2, actor.getInventory(), bench);
        menu.broadcastChanges();
        helper.assertTrue(menu.displayedVisCost() == 25 && menu.requiredCrystalMask() == 4,
                "Shapeless inlay displays source vis and Water crystal");
        menu.clicked(0, 1, ClickType.PICKUP, actor);
        var inlay = InfusionStabilityArcaneAdapter.output(Kind.INLAY).getItem();
        helper.assertTrue(menu.getCarried().getCount() + actor.getInventory().countItem(inlay) == 2
                        && bench.getVis() == 25 && bench.getItem(2).getCount() == 1,
                "Normal right-click splitting cannot grant unpaid inlay or discard half the paid output");
        actor.getInventory().add(menu.getCarried());
        menu.setCarried(ItemStack.EMPTY);
        ItemStack result = menu.quickMoveStack(actor, 0);
        helper.assertTrue(ItemStack.matches(result, InfusionStabilityArcaneAdapter.output(Kind.INLAY)),
                "Real shift-click commits exactly two original inlay items");
        helper.assertTrue(actor.getInventory().countItem(result.getItem()) == 4 && bench.getVis() == 0,
                "Two paid crafts yield four total items and exactly fifty aura leaves");
        helper.assertTrue(bench.getItem(2).isEmpty() && bench.getItem(7).isEmpty() && bench.getItem(11).isEmpty(),
                "Actual gold, redstone and aqua crystal are consumed");
        helper.assertTrue(menu.quickMoveStack(actor, 0).isEmpty(), "Consumed recipe cannot replay inlay output");
        actor.getInventory().clearContent();
        helper.succeed();
    }

    @GameTest(template = "infusion_paid_blank", batch = "infusion_stability_arcane", timeoutTicks = 20)
    public static void mirroredGlassIngredientHasActualPaidBaseArtificeRecipe(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(12, 5, 12));
        helper.getLevel().setBlockAndUpdate(pos, ThaumcraftBlocks.ARCANE_WORKBENCH.get().defaultBlockState());
        var bench = (ArcaneWorkbenchBlockEntity) helper.getLevel().getBlockEntity(pos);
        ServerPlayer actor = player(helper, pos);
        actor.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge().addResearch("BASEARTIFICE");
        bench.setItem(0, new ItemStack(Items.GLASS_PANE));
        bench.setItem(8, new ItemStack(ThaumcraftItems.QUICKSILVER.get()));
        bench.setItem(11, CrystalEssenceItem.ofAspect("aqua"));
        bench.setItem(13, CrystalEssenceItem.ofAspect("ordo"));
        bench.setVis(50);
        var menu = new ArcaneWorkbenchMenu(3, actor.getInventory(), bench);
        menu.broadcastChanges();
        helper.assertTrue(menu.displayedVisCost() == 50 && menu.requiredCrystalMask() == 20,
                "Mirrored glass exposes exact source vis and aqua/ordo crystals");
        ItemStack result = menu.quickMoveStack(actor, 0);
        helper.assertTrue(ItemStack.matches(result, InfusionStabilityArcaneAdapter.output(Kind.MIRRORED_GLASS)),
                "Real workbench shift-click grants original mirrored glass ingredient");
        helper.assertTrue(bench.getVis() == 0 && bench.getItem(0).isEmpty() && bench.getItem(8).isEmpty()
                        && bench.getItem(11).isEmpty() && bench.getItem(13).isEmpty(),
                "All source inputs, aqua/ordo crystals and fifty aura are physically consumed");
        helper.assertTrue(actor.getInventory().countItem(result.getItem()) == 1,
                "Exactly one paid mirror ingredient enters inventory");
        actor.getInventory().clearContent();
        helper.succeed();
    }

    private static void crystals(ArcaneWorkbenchBlockEntity bench, Kind kind) {
        String[] aspects = {"aer", "ignis", "aqua", "terra", "ordo", "perditio"};
        for (int primal = 0; primal < 6; primal++) if ((kind.crystalMask & 1 << primal) != 0) {
            ItemStack crystal = CrystalEssenceItem.ofAspect(aspects[primal]);
            crystal.setCount(2);
            bench.setItem(9 + primal, crystal);
        }
    }

    private static ServerPlayer player(GameTestHelper helper, BlockPos bench) {
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "StabilityArcane"));
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        player.moveTo(Vec3.atBottomCenterOf(bench.north()));
        return player;
    }
}
