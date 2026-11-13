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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;

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
        if ("read".equals(System.getenv("TC_AURA_COLD_RESTART_PHASE"))) {
            System.out.println("TC_GAMETEST_CRYSTAL_SPREAD_SKIPPED_PHASE=read");
            helper.succeed();
            return;
        }
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

    @SuppressWarnings("deprecation")
    @PrefixGameTestTemplate(false)
    @GameTest(
            template = "crystal_spread_empty",
            templateNamespace = ThaumcraftPort.MOD_ID,
            timeoutTicks = 100
    )
    public static void missingAuraChunkUsesBETA26ZeroAuraStarvation(GameTestHelper helper) {
        if ("read".equals(System.getenv("TC_AURA_COLD_RESTART_PHASE"))) {
            System.out.println("TC_GAMETEST_CRYSTAL_STARVATION_SKIPPED_PHASE=read");
            helper.succeed();
            return;
        }

        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(new BlockPos(8, TEST_Y, 8));
        // Load a separate, otherwise untouched chunk as part of the fixture.
        // The behavior under test is a LOADED chunk with no AuraSavedData entry.
        int chunkX = (anchor.getX() >> 4) + 32;
        int chunkZ = (anchor.getZ() >> 4) + 32;
        level.getChunk(chunkX, chunkZ);
        int baseX = chunkX * 16;
        int baseZ = chunkZ * 16;
        int y = anchor.getY();

        BlockPos aerPos = new BlockPos(baseX + 2, y, baseZ + 2);
        BlockPos vitiumPos = new BlockPos(baseX + 6, y, baseZ + 2);
        BlockPos isolatedPos = new BlockPos(baseX + 10, y, baseZ + 2);
        BlockPos sameVariantPos = new BlockPos(baseX + 2, y, baseZ + 8);
        BlockPos sameVariantNeighbor = sameVariantPos.east();
        BlockPos otherVariantPos = new BlockPos(baseX + 10, y, baseZ + 8);
        BlockPos otherVariantNeighbor = otherVariantPos.east();

        ThaumcraftAuraSavedData aura = ThaumcraftAuraSavedData.get(level);
        requireMissingAura(aura, aerPos, vitiumPos, isolatedPos,
                sameVariantPos, sameVariantNeighbor, otherVariantPos, otherVariantNeighbor);

        CrystalClusterBlock aer = ThaumcraftBlocks.CRYSTAL_AER.get();
        CrystalClusterBlock vitium = ThaumcraftBlocks.CRYSTAL_VITIUM.get();
        CrystalClusterBlock ignis = ThaumcraftBlocks.CRYSTAL_IGNIS.get();
        long starvationSeed = findStarvationSeed();

        BlockState aerState = placeCrystal(level, aer, aerPos, 3, 3);
        aer.randomTick(aerState, level, aerPos, RandomSource.create(starvationSeed));
        BlockState aerAfter = level.getBlockState(aerPos);
        if (!aerAfter.is(aer) || aerAfter.getValue(CrystalClusterBlock.SIZE) != 2
                || aura.hasChunk(aerPos) || aura.getVis(aerPos) != 0.0F) {
            throw new AssertionError("BETA26 missing-aura Aer crystal did not starve from zero Vis: " + aerAfter);
        }
        System.out.println("TC_GAMETEST_CRYSTAL_STARVATION_AER_MISSING_AURA=PASS size=2");

        BlockState vitiumState = placeCrystal(level, vitium, vitiumPos, 3, 3);
        vitium.randomTick(vitiumState, level, vitiumPos, RandomSource.create(starvationSeed));
        BlockState vitiumAfter = level.getBlockState(vitiumPos);
        if (!vitiumAfter.is(vitium) || vitiumAfter.getValue(CrystalClusterBlock.SIZE) != 2
                || aura.hasChunk(vitiumPos) || aura.getFlux(vitiumPos) != 0.0F) {
            throw new AssertionError("BETA26 missing-aura Vitium crystal did not starve from zero Flux: " + vitiumAfter);
        }
        System.out.println("TC_GAMETEST_CRYSTAL_STARVATION_VITIUM_MISSING_AURA=PASS size=2");

        BlockState isolatedState = placeCrystal(level, aer, isolatedPos, 0, 3);
        aer.randomTick(isolatedState, level, isolatedPos, RandomSource.create(starvationSeed));
        if (!level.getBlockState(isolatedPos).equals(isolatedState)) {
            throw new AssertionError("An isolated size-zero crystal must remain when no same block touches it");
        }
        System.out.println("TC_GAMETEST_CRYSTAL_STARVATION_ZERO_ISOLATED=PASS");

        BlockState sameVariantState = placeCrystal(level, vitium, sameVariantPos, 0, 3);
        BlockState sameVariantNeighborState = placeCrystal(level, vitium, sameVariantNeighbor, 1, 3);
        vitium.randomTick(sameVariantState, level, sameVariantPos, RandomSource.create(starvationSeed));
        if (!level.getBlockState(sameVariantPos).isAir()
                || !level.getBlockState(sameVariantNeighbor).equals(sameVariantNeighborState)) {
            throw new AssertionError("A size-zero crystal touching the same BETA26 block variant must be removed alone");
        }
        System.out.println("TC_GAMETEST_CRYSTAL_STARVATION_ZERO_TOUCHING_SAME=PASS");

        BlockState otherVariantState = placeCrystal(level, aer, otherVariantPos, 0, 3);
        BlockState otherVariantNeighborState = placeCrystal(level, ignis, otherVariantNeighbor, 1, 3);
        aer.randomTick(otherVariantState, level, otherVariantPos, RandomSource.create(starvationSeed));
        if (!level.getBlockState(otherVariantPos).equals(otherVariantState)
                || !level.getBlockState(otherVariantNeighbor).equals(otherVariantNeighborState)) {
            throw new AssertionError("A different crystal variant must not satisfy BlockUtils.isBlockTouching(this)");
        }
        System.out.println("TC_GAMETEST_CRYSTAL_STARVATION_ZERO_TOUCHING_OTHER_VARIANT=PASS");

        for (BlockPos pos : new BlockPos[]{aerPos, vitiumPos, isolatedPos,
                sameVariantPos, sameVariantNeighbor, otherVariantPos, otherVariantNeighbor}) {
            requireNoItemDrops(level, pos);
        }
        requireMissingAura(aura, aerPos, vitiumPos, isolatedPos,
                sameVariantPos, sameVariantNeighbor, otherVariantPos, otherVariantNeighbor);
        System.out.println("TC_GAMETEST_CRYSTAL_STARVATION_NO_DROPS=PASS");
        System.out.println("TC_GAMETEST_CRYSTAL_STARVATION_NO_AURA_CREATED=PASS");
        System.out.println("TC_GAMETEST_CRYSTAL_STARVATION_ACCEPTED=YES");
        helper.succeed();
    }


    @PrefixGameTestTemplate(false)
    @GameTest(
            template = "crystal_spread_empty",
            templateNamespace = ThaumcraftPort.MOD_ID,
            timeoutTicks = 40
    )
    public static void legacyRockVariantsRemainCrystalSupports(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        CrystalClusterBlock crystal = ThaumcraftBlocks.CRYSTAL_AER.get();
        BlockState[] supports = {
                Blocks.STONE_BRICKS.defaultBlockState(),
                Blocks.MOSSY_STONE_BRICKS.defaultBlockState(),
                Blocks.CRACKED_STONE_BRICKS.defaultBlockState(),
                Blocks.CHISELED_STONE_BRICKS.defaultBlockState(),
                Blocks.WHITE_CONCRETE.defaultBlockState(),
                Blocks.ORANGE_CONCRETE.defaultBlockState(),
                Blocks.MAGENTA_CONCRETE.defaultBlockState(),
                Blocks.LIGHT_BLUE_CONCRETE.defaultBlockState(),
                Blocks.YELLOW_CONCRETE.defaultBlockState(),
                Blocks.LIME_CONCRETE.defaultBlockState(),
                Blocks.PINK_CONCRETE.defaultBlockState(),
                Blocks.GRAY_CONCRETE.defaultBlockState(),
                Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState(),
                Blocks.CYAN_CONCRETE.defaultBlockState(),
                Blocks.PURPLE_CONCRETE.defaultBlockState(),
                Blocks.BLUE_CONCRETE.defaultBlockState(),
                Blocks.BROWN_CONCRETE.defaultBlockState(),
                Blocks.GREEN_CONCRETE.defaultBlockState(),
                Blocks.RED_CONCRETE.defaultBlockState(),
                Blocks.BLACK_CONCRETE.defaultBlockState()
        };
        String[] names = {
                "stone", "mossy", "cracked", "chiseled",
                "white concrete", "orange concrete", "magenta concrete", "light blue concrete",
                "yellow concrete", "lime concrete", "pink concrete", "gray concrete",
                "light gray concrete", "cyan concrete", "purple concrete", "blue concrete",
                "brown concrete", "green concrete", "red concrete", "black concrete"
        };
        if (supports.length != names.length) {
            throw new AssertionError("Support fixture names do not match the block-state list");
        }
        BlockState crystalState = crystal.defaultBlockState()
                .setValue(CrystalClusterBlock.SIZE, 0)
                .setValue(CrystalClusterBlock.GENERATION, 3);

        // Keep the 20 independent placements inside the 16x16 empty GameTest
        // template while checking every 1.12.2 concrete metadata color.
        int columns = 5;
        for (int i = 0; i < supports.length; i++) {
            BlockPos pos = helper.absolutePos(new BlockPos(
                    2 + (i % columns) * 3, TEST_Y, TEST_Z + (i / columns) * 3));
            level.setBlock(pos.below(), supports[i], 3);
            level.setBlock(pos, crystalState, 3);

            if (!CrystalClusterBlock.hasCrystalSupport(level, pos, Direction.DOWN)
                    || !crystalState.canSurvive(level, pos)
                    || !level.getBlockState(pos).equals(crystalState)) {
                throw new AssertionError("BETA26 Material.ROCK variant must support crystals: "
                        + names[i] + " state=" + supports[i]);
            }
        }

        System.out.println("TC_GAMETEST_CRYSTAL_LEGACY_ROCK_VARIANTS=PASS count=" + supports.length);
        helper.succeed();
    }

    private static BlockState placeCrystal(
            ServerLevel level, CrystalClusterBlock crystal, BlockPos pos, int size, int generation) {
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        BlockState state = crystal.defaultBlockState()
                .setValue(CrystalClusterBlock.SIZE, size)
                .setValue(CrystalClusterBlock.GENERATION, generation);
        level.setBlock(pos, state, 3);
        if (!level.getBlockState(pos).equals(state) || !state.canSurvive(level, pos)) {
            throw new AssertionError("Failed to place supported crystal fixture at " + pos + ": " + state);
        }
        return state;
    }

    private static void requireMissingAura(ThaumcraftAuraSavedData aura, BlockPos... positions) {
        for (BlockPos pos : positions) {
            if (aura.hasChunk(pos)) {
                throw new AssertionError("Crystal starvation fixture unexpectedly has an aura entry at " + pos);
            }
        }
    }

    private static void requireNoItemDrops(ServerLevel level, BlockPos pos) {
        AABB bounds = new AABB(
                pos.getX() - 1.0D, pos.getY() - 1.0D, pos.getZ() - 1.0D,
                pos.getX() + 2.0D, pos.getY() + 2.0D, pos.getZ() + 2.0D);
        if (!level.getEntitiesOfClass(ItemEntity.class, bounds).isEmpty()) {
            throw new AssertionError("Starvation must not produce manual crystal item drops at " + pos);
        }
    }

    private static long findStarvationSeed() {
        for (long seed = 0; seed < 100_000L; seed++) {
            if (RandomSource.create(seed).nextInt(6) == 0) {
                return seed;
            }
        }
        throw new AssertionError("Could not find deterministic size-zero/three starvation tick seed");
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
