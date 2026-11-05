package dev.ehtid.thaumcraft.infusion.test;

import dev.ehtid.thaumcraft.blockentity.PedestalBlockEntity;
import dev.ehtid.thaumcraft.crafting.InfusionStabilizerAdapter;
import dev.ehtid.thaumcraft.infusion.InfusionStabilityBlocks;
import dev.ehtid.thaumcraft.registry.ThaumcraftBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real source-supported decorations, glyph slots, and occupied pedestal symmetry. */
@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class InfusionStabilityGameTests {
    private InfusionStabilityGameTests() {}

    private static void near(GameTestHelper helper, float expected, float actual, String description) {
        helper.assertTrue(Math.abs(expected - actual) < .00001F,
                description + ": expected " + expected + ", actual " + actual);
    }

    private static void set(GameTestHelper helper, BlockPos pos, Block block) {
        helper.getLevel().setBlock(pos, block.defaultBlockState(), 3);
    }

    private static BlockPos matrix(GameTestHelper helper) {
        BlockPos matrix = helper.absolutePos(new BlockPos(12, 8, 12));
        // Real structure; adapter also works independently of active/crafting state.
        set(helper, matrix.below(2), ThaumcraftBlocks.PEDESTAL_ARCANE.get());
        for (int x : new int[]{-1, 1}) for (int z : new int[]{-1, 1})
            set(helper, matrix.offset(x, -2, z), ThaumcraftBlocks.PILLAR_ARCANE.get());
        return matrix;
    }

    @GameTest(template = "infusion_paid_blank", batch = "infusion_stability", timeoutTicks = 20)
    public static void originalDecorationsAndFourActualGlyphSlots(GameTestHelper helper) {
        BlockPos matrix = matrix(helper);
        var plain = InfusionStabilizerAdapter.scan(helper.getLevel(), matrix);
        near(helper, 0, plain.replenishment(), "Bare arcane structure");
        helper.assertTrue(plain.modifiers().cycleTime() == 10, "Original base cycle");

        for (int sign : new int[]{-1, 1}) {
            BlockPos candle = matrix.offset(3 * sign, -2, sign);
            set(helper, candle.below(), Blocks.STONE);
            set(helper, candle, ThaumcraftBlocks.CANDLE_WHITE.get());
            set(helper, matrix.offset(4 * sign, -2, sign), InfusionStabilityBlocks.STABILIZER.get());
            BlockPos inlay = matrix.offset(5 * sign, -2, sign);
            set(helper, inlay.below(), Blocks.STONE);
            set(helper, inlay, InfusionStabilityBlocks.INLAY.get());
        }
        set(helper, matrix.offset(-6, -2, -1), Blocks.PLAYER_HEAD);
        set(helper, matrix.offset(6, -2, 1), Blocks.DRAGON_HEAD);
        near(helper, .475F, InfusionStabilizerAdapter.scan(helper.getLevel(), matrix).replenishment(),
                "Candle .1 + uncharged device .25 + inlay .025 + legacy skull variants .1");

        set(helper, matrix.offset(-1, -3, -1), InfusionStabilityBlocks.MATRIX_SPEED.get());
        set(helper, matrix.offset(1, -3, -1), InfusionStabilityBlocks.MATRIX_SPEED.get());
        set(helper, matrix.offset(1, -3, 1), InfusionStabilityBlocks.MATRIX_COST.get());
        var glyphs = InfusionStabilizerAdapter.scan(helper.getLevel(), matrix);
        helper.assertTrue(glyphs.modifiers().cycleTime() == 9 && glyphs.modifiers().countDelay() == 4,
                "Two speed and one cost blocks use actual pillar foundation slots");
        near(helper, 1, glyphs.modifiers().essentiaCostMult(), "Source glyph cost arithmetic");
        set(helper, matrix.offset(-1, -4, 1), InfusionStabilityBlocks.MATRIX_SPEED.get());
        helper.assertTrue(InfusionStabilizerAdapter.scan(helper.getLevel(), matrix).modifiers().cycleTime() == 9,
                "A glyph outside its source slot has no speed effect");
        helper.succeed();
    }

    @GameTest(template = "infusion_paid_blank", batch = "infusion_stability", timeoutTicks = 20)
    public static void actualInventorySymmetryAndUnsupportedCrystals(GameTestHelper helper) {
        BlockPos matrix = matrix(helper);
        BlockPos first = matrix.offset(2, -2, 0), mirror = matrix.offset(-2, -2, 0);
        set(helper, first, ThaumcraftBlocks.PEDESTAL_ARCANE.get());
        set(helper, mirror, ThaumcraftBlocks.PEDESTAL_ARCANE.get());
        near(helper, 0, InfusionStabilizerAdapter.scan(helper.getLevel(), matrix).replenishment(),
                "Empty arcane pedestals have zero strength");
        ((PedestalBlockEntity) helper.getLevel().getBlockEntity(first)).insertFromPlayer(new ItemStack(Items.DIAMOND));
        var unequal = InfusionStabilizerAdapter.scan(helper.getLevel(), matrix);
        near(helper, -.1F, unequal.replenishment(), "Actual occupancy mismatch incurs source penalty");
        helper.assertTrue(unequal.problemPositions().size() == 1, "World position of asymmetric pair is exposed");
        ((PedestalBlockEntity) helper.getLevel().getBlockEntity(mirror)).insertFromPlayer(new ItemStack(Items.DIRT));
        near(helper, 0, InfusionStabilizerAdapter.scan(helper.getLevel(), matrix).replenishment(),
                "Source compares occupancy, not item identity");
        set(helper, matrix.offset(-3, -2, 1), ThaumcraftBlocks.CRYSTAL_AER.get());
        set(helper, matrix.offset(3, -2, -1), ThaumcraftBlocks.CRYSTAL_AER.get());
        set(helper, matrix.offset(0, -4, 0), InfusionStabilityBlocks.STABILIZER.get());
        near(helper, 0, InfusionStabilizerAdapter.scan(helper.getLevel(), matrix).replenishment(),
                "Ore crystals are not source stabilizers and entire central column is excluded");
        helper.succeed();
    }
}
