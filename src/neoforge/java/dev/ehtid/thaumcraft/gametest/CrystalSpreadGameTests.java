package dev.ehtid.thaumcraft.gametest;

import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.block.CrystalClusterBlock;
import dev.ehtid.thaumcraft.registry.ThaumcraftBlocks;
import dev.ehtid.thaumcraft.world.ThaumcraftAuraSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Executes the production CrystalClusterBlock.randomTick method in a real
 * NeoForge server world with a deterministic RandomSource. This is not a
 * scheduler-frequency test; it exercises the actual spread branch, block
 * states, support tag, persisted aura transaction, and resulting world state.
 */
@GameTestHolder(ThaumcraftPort.MOD_ID)
public final class CrystalSpreadGameTests {
    private static final int TEST_Y = 2;
    private static final int TEST_Z = 2;

    private record SpreadPlan(long seed, BlockPos offset) {}

    private CrystalSpreadGameTests() {}

    @SuppressWarnings("deprecation")
    @PrefixGameTestTemplate(false)
    @GameTest(
            template = "crystal_spread_empty",
            templateNamespace = ThaumcraftPort.MOD_ID,
            timeoutTicks = 40
    )
    public static void spreadUsesLegacyBlockMaterial(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SpreadPlan plan = findSpreadPlan();

        BlockState seagrass = Blocks.SEAGRASS.defaultBlockState();
        BlockState water = Blocks.WATER.defaultBlockState();
        BlockState lava = Blocks.LAVA.defaultBlockState();

        // Seagrass is a real vanilla fluid-bearing, replaceable, non-liquid
        // block state. BETA26 tests the block material, not FluidState.
        requireFluidFixture("seagrass", seagrass, false);
        requireFluidFixture("water", water, true);
        requireFluidFixture("lava", lava, true);

        runScenario(helper, level, plan, 2, seagrass, true, "seagrass");
        runScenario(helper, level, plan, 7, water, false, "water");
        runScenario(helper, level, plan, 12, lava, false, "lava");

        System.out.println("TC_GAMETEST_CRYSTAL_SPREAD_ACCEPTED=YES");
        helper.succeed();
    }

    private static void requireFluidFixture(String name, BlockState state, boolean liquid) {
        if (state.isAir()
                || state.getFluidState().isEmpty()
                || state.liquid() != liquid
                || !state.canBeReplaced()) {
            throw new AssertionError("Invalid " + name + " fixture: " + state
                    + " liquid=" + state.liquid()
                    + " fluidEmpty=" + state.getFluidState().isEmpty()
                    + " replaceable=" + state.canBeReplaced());
        }
    }

    private static void runScenario(
            GameTestHelper helper,
            ServerLevel level,
            SpreadPlan plan,
            int sourceX,
            BlockState destination,
            boolean shouldSpread,
            String name
    ) {
        BlockPos sourcePos = helper.absolutePos(new BlockPos(sourceX, TEST_Y, TEST_Z));
        BlockPos targetPos = sourcePos.offset(
                plan.offset().getX(), plan.offset().getY(), plan.offset().getZ());

        level.setBlock(sourcePos.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(targetPos, destination, 3);

        Direction supportDirection = supportDirectionAwayFrom(targetPos, sourcePos);
        BlockPos supportPos = targetPos.relative(supportDirection);
        level.setBlock(supportPos, Blocks.STONE.defaultBlockState(), 3);
        if (!CrystalClusterBlock.hasCrystalSupport(level, targetPos, supportDirection)) {
            throw new AssertionError(name + " target has no valid BETA26 stone support");
        }

        CrystalClusterBlock crystal = ThaumcraftBlocks.CRYSTAL_AER.get();
        BlockState sourceState = crystal.defaultBlockState()
                .setValue(CrystalClusterBlock.SIZE, 3)
                .setValue(CrystalClusterBlock.GENERATION, 3);
        level.setBlock(sourcePos, sourceState, 3);

        ThaumcraftAuraSavedData aura = ThaumcraftAuraSavedData.get(level);
        aura.putChunk(sourcePos, (short) 0, 100.0F, 0.0F);
        if (!aura.hasChunk(sourcePos) || aura.getVis(sourcePos) != 100.0F) {
            throw new AssertionError(name + " failed to seed source chunk aura");
        }

        // The plan matches randomTick's exact draw order:
        // tick gate (nextInt(6)), three offsets (nextInt(3)), then spread chance
        // (nextInt(16)==0). No production random or aura path is mocked.
        crystal.randomTick(sourceState, level, sourcePos, RandomSource.create(plan.seed()));

        BlockState after = level.getBlockState(targetPos);
        float remainingVis = aura.getVis(sourcePos);
        if (shouldSpread) {
            if (!after.is(crystal)
                    || after.getValue(CrystalClusterBlock.SIZE) != 0
                    || after.getValue(CrystalClusterBlock.GENERATION) < 3
                    || after.getValue(CrystalClusterBlock.GENERATION) > 4) {
                throw new AssertionError(name + " did not receive a BETA26 child crystal: " + after);
            }
            if (remainingVis != 90.0F) {
                throw new AssertionError(name + " spread did not debit exactly 10 Vis: " + remainingVis);
            }
        } else {
            if (!after.equals(destination)) {
                throw new AssertionError(name + " liquid destination was replaced: " + after);
            }
            if (remainingVis != 100.0F) {
                throw new AssertionError(name + " liquid rejection spent Vis: " + remainingVis);
            }
        }

        System.out.println("TC_GAMETEST_CRYSTAL_SPREAD_CASE=" + name
                + " result=" + (shouldSpread ? "SPREAD" : "REJECTED")
                + " vis=" + remainingVis
                + " offset=" + plan.offset());
    }

    private static Direction supportDirectionAwayFrom(BlockPos target, BlockPos source) {
        for (Direction direction : Direction.values()) {
            if (!target.relative(direction).equals(source)) {
                return direction;
            }
        }
        throw new AssertionError("No support direction available");
    }

    private static SpreadPlan findSpreadPlan() {
        for (long seed = 0; seed < 100_000L; seed++) {
            RandomSource random = RandomSource.create(seed);
            if (random.nextInt(6) != 0) {
                continue;
            }
            int dx = random.nextInt(3) - 1;
            int dy = random.nextInt(3) - 1;
            int dz = random.nextInt(3) - 1;
            if (dx == 0 && dy == 0 && dz == 0) {
                continue;
            }
            if (random.nextInt(16) != 0) {
                continue;
            }
            return new SpreadPlan(seed, new BlockPos(dx, dy, dz));
        }
        throw new AssertionError("Could not find deterministic crystal-spread seed");
    }
}
