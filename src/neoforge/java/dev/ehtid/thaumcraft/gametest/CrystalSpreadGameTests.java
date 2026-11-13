package dev.ehtid.thaumcraft.gametest;

import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.block.CrystalClusterBlock;
import dev.ehtid.thaumcraft.registry.ThaumcraftBlocks;
import dev.ehtid.thaumcraft.world.ThaumcraftAuraSavedData;
import dev.ehtid.thaumcraft.world.CrystalClusterWorldGen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
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
            timeoutTicks = 60
    )
    public static void spreadDoesNotForceLoadUnloadedNeighborChunks(GameTestHelper helper) {
        if ("read".equals(System.getenv("TC_AURA_COLD_RESTART_PHASE"))) {
            System.out.println("TC_GAMETEST_CRYSTAL_SPREAD_NO_FORCE_LOAD_SKIPPED_PHASE=read");
            helper.succeed();
            return;
        }

        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(new BlockPos(8, TEST_Y, 8));
        int sourceChunkX = (anchor.getX() >> 4) + 64;
        int sourceChunkZ = (anchor.getZ() >> 4) + 64;
        LevelChunk sourceChunk = level.getChunk(sourceChunkX, sourceChunkZ); // explicit fixture preload
        int neighborChunkX = sourceChunkX + 1;
        requireBoundaryNeighborUnloaded(level, neighborChunkX, sourceChunkZ, "after-source-preload");

        int baseX = sourceChunkX * 16;
        int baseZ = sourceChunkZ * 16;
        CrystalClusterBlock crystal = ThaumcraftBlocks.CRYSTAL_AER.get();
        BlockPos unsupportedBoundaryPos = new BlockPos(baseX + 15, 20, baseZ + 4);
        BlockState unsupportedBoundaryState = crystal.defaultBlockState()
                .setValue(CrystalClusterBlock.SIZE, 3)
                .setValue(CrystalClusterBlock.GENERATION, 3);
        boolean unsupportedSurvives = unsupportedBoundaryState.canSurvive(level, unsupportedBoundaryPos);
        requireBoundaryNeighborUnloaded(level, neighborChunkX, sourceChunkZ, "after-unsupported-can-survive");
        if (unsupportedSurvives) {
            throw new AssertionError("Server placement support check accepted an unsupported crystal");
        }
        System.out.println("TC_GAMETEST_CRYSTAL_PLACEMENT_UNLOADED_SUPPORT=PASS no_force_load=YES");

        BlockPos destinationBoundarySource = new BlockPos(baseX + 15, 20, baseZ + 4);
        BlockPos supportBoundarySource = new BlockPos(baseX + 14, 20, baseZ + 12);
        for (BlockPos fixturePos : new BlockPos[]{
                unsupportedBoundaryPos, destinationBoundarySource, supportBoundarySource}) {
            if ((fixturePos.getX() >> 4) != sourceChunkX
                    || (fixturePos.getZ() >> 4) != sourceChunkZ) {
                throw new AssertionError(
                        "Crystal boundary fixture escaped its preloaded source chunk: " + fixturePos);
            }
        }
        // Avoid neighbor notifications while constructing this fixture: an update
        // from local X=15 would legitimately load the east chunk before randomTick.
        BlockState destinationSourceState = placeBoundaryCrystal(
                level, sourceChunk, sourceChunkX, sourceChunkZ, crystal, destinationBoundarySource, 3, 3,
                neighborChunkX, "destination-source");
        BlockState supportSourceState = placeBoundaryCrystal(
                level, sourceChunk, sourceChunkX, sourceChunkZ, crystal, supportBoundarySource, 3, 3,
                neighborChunkX, "support-source");

        ThaumcraftAuraSavedData aura = ThaumcraftAuraSavedData.get(level);
        requireBoundaryNeighborUnloaded(level, neighborChunkX, sourceChunkZ, "after-aura-get");
        aura.putChunk(destinationBoundarySource, (short) 0, 100.0F, 0.0F);
        requireBoundaryNeighborUnloaded(level, neighborChunkX, sourceChunkZ, "after-aura-seed");
        long eastSpreadSeed = findBoundarySpreadSeed();
        requireBoundaryNeighborUnloaded(level, neighborChunkX, sourceChunkZ, "after-seed-search");

        // A one-tick gap distinguishes synchronous fixture side effects from
        // neighbour generation/ticket work that completes asynchronously.
        helper.runAfterDelay(1, () -> {
            requireBoundaryNeighborUnloaded(level, neighborChunkX, sourceChunkZ, "after-one-server-tick");

            // Source at local X=15 chooses target X=16 in an entirely unloaded chunk.
            crystal.randomTick(destinationSourceState, level, destinationBoundarySource,
                    RandomSource.create(eastSpreadSeed));
            requireBoundaryNeighborUnloaded(level, neighborChunkX, sourceChunkZ, "after-destination-random-tick");
            if (aura.getVis(destinationBoundarySource) != 100.0F) {
                throw new AssertionError("Skipped crystal spread spent Vis");
            }
            System.out.println("TC_GAMETEST_CRYSTAL_SPREAD_UNLOADED_DESTINATION=PASS no_force_load=YES vis=100.0");

            // Source at local X=14 chooses target X=15 in the loaded chunk. Its only
            // possible support face across the east boundary is in the unloaded chunk.
            // The chosen seed keeps the target above the source's support block.
            crystal.randomTick(supportSourceState, level, supportBoundarySource,
                    RandomSource.create(eastSpreadSeed));
            requireBoundaryNeighborUnloaded(level, neighborChunkX, sourceChunkZ, "after-support-random-tick");
            if (aura.getVis(supportBoundarySource) != 100.0F) {
                throw new AssertionError("Crystal support lookup spent Vis");
            }
            System.out.println("TC_GAMETEST_CRYSTAL_SPREAD_UNLOADED_SUPPORT=PASS no_force_load=YES vis=100.0");
            System.out.println("TC_GAMETEST_CRYSTAL_SPREAD_NO_FORCE_LOAD_ACCEPTED=YES");
            helper.succeed();
        });
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
                Blocks.BLACK_CONCRETE.defaultBlockState(),
                Blocks.BONE_BLOCK.defaultBlockState(),
                Blocks.MAGMA_BLOCK.defaultBlockState(),
                Blocks.OBSERVER.defaultBlockState(),
                Blocks.END_STONE_BRICKS.defaultBlockState(),
                Blocks.PURPUR_BLOCK.defaultBlockState(),
                Blocks.PURPUR_PILLAR.defaultBlockState(),
                Blocks.PURPUR_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE)
        };
        String[] names = {
                "stone", "mossy", "cracked", "chiseled",
                "white concrete", "orange concrete", "magenta concrete", "light blue concrete",
                "yellow concrete", "lime concrete", "pink concrete", "gray concrete",
                "light gray concrete", "cyan concrete", "purple concrete", "blue concrete",
                "brown concrete", "green concrete", "red concrete", "black concrete",
                "bone block", "magma block", "observer", "end stone bricks",
                "purpur block", "purpur pillar", "purpur double slab"
        };
        if (supports.length != names.length) {
            throw new AssertionError("Support fixture names do not match the block-state list");
        }
        BlockState crystalState = crystal.defaultBlockState()
                .setValue(CrystalClusterBlock.SIZE, 0)
                .setValue(CrystalClusterBlock.GENERATION, 3);
        BlockPos crystalPos = helper.absolutePos(new BlockPos(8, TEST_Y, 8));
        int faceChecks = 0;

        // Every full-cube legacy ROCK block must support crystals on all six
        // faces. The old single purpur slab is directional; its two supported
        // faces are checked separately below, matching BlockSlab's 1.12 shape.
        for (int i = 0; i < supports.length; i++) {
            for (Direction direction : Direction.values()) {
                assertCrystalSupport(level, crystalPos, crystalState, supports[i], direction, names[i]);
                faceChecks++;
            }
        }

        BlockState purpurBottomSlab = Blocks.PURPUR_SLAB.defaultBlockState()
                .setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        assertCrystalSupport(level, crystalPos, crystalState, purpurBottomSlab,
                Direction.UP, "purpur bottom slab underside");
        faceChecks++;
        BlockState purpurTopSlab = Blocks.PURPUR_SLAB.defaultBlockState()
                .setValue(SlabBlock.TYPE, SlabType.TOP);
        assertCrystalSupport(level, crystalPos, crystalState, purpurTopSlab,
                Direction.DOWN, "purpur top slab top face");
        faceChecks++;

        int expectedFaceChecks = supports.length * Direction.values().length + 2;
        if (faceChecks != expectedFaceChecks) {
            throw new AssertionError("Expected " + expectedFaceChecks + " legacy ROCK face checks, got " + faceChecks);
        }
        System.out.println("TC_GAMETEST_CRYSTAL_LEGACY_ROCK_VARIANTS=PASS count=27 faces=" + faceChecks);
        if (!"read".equals(System.getenv("TC_AURA_COLD_RESTART_PHASE"))) {
            verifyCrystalClusterWorldGen(level, helper);
        }
        helper.succeed();
    }


    private static void verifyCrystalClusterWorldGen(ServerLevel level, GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(8, 4, 8));
        // A 5x5x5 stone shell creates a supported 3x3x3 cavity, like the
        // underground air/rock boundary used by BETA26 crystal clusters.
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    level.setBlock(center.offset(dx, dy, dz), Blocks.STONE.defaultBlockState(), 3);
                }
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    level.setBlock(center.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        level.setBlock(center, Blocks.WATER.defaultBlockState(), 3);

        int loadedChunksBefore = level.getChunkSource().getLoadedChunksCount();
        int crystalAmount = CrystalClusterWorldGen.generateClusterAt(
                level, center, ThaumcraftBlocks.CRYSTAL_AER.get(), RandomSource.create(0xC0FFEE));
        int crystalBlocks = 0;
        int summedSize = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(pos);
                    if (!(state.getBlock() instanceof CrystalClusterBlock)) continue;
                    int size = state.getValue(CrystalClusterBlock.SIZE);
                    if (size < 1 || size > 3
                            || state.getValue(CrystalClusterBlock.GENERATION) != 1
                            || !state.canSurvive(level, pos)) {
                        throw new AssertionError("Generated BETA26 crystal has invalid state/support: " + pos + " " + state);
                    }
                    crystalBlocks++;
                    summedSize += size;
                }
            }
        }
        if (crystalBlocks == 0 || crystalAmount != summedSize) {
            throw new AssertionError("Crystal cluster generator placed no valid blocks or miscounted SIZE: "
                    + crystalBlocks + "/" + crystalAmount + "/" + summedSize);
        }
        if (!level.getBlockState(center).is(Blocks.WATER)) {
            throw new AssertionError("BETA26 crystal generation must not replace liquid blocks");
        }
        if (loadedChunksBefore != level.getChunkSource().getLoadedChunksCount()) {
            throw new AssertionError("Crystal generation loaded a chunk while checking support");
        }

        System.out.println("TC_GAMETEST_CRYSTAL_WORLDGEN=PASS blocks=" + crystalBlocks
                + " amount=" + crystalAmount + " water=preserved no_force_load=YES");

        // Do not leave the fixture in the cold-restart world.
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    level.removeBlock(center.offset(dx, dy, dz), false);
                }
            }
        }
    }

    private static void assertCrystalSupport(
            ServerLevel level,
            BlockPos crystalPos,
            BlockState crystalState,
            BlockState support,
            Direction supportDirection,
            String name
    ) {
        BlockPos supportPos = crystalPos.relative(supportDirection);
        level.setBlock(supportPos, support, 3);
        level.setBlock(crystalPos, crystalState, 3);
        boolean supported = CrystalClusterBlock.hasCrystalSupport(level, crystalPos, supportDirection);
        boolean survives = crystalState.canSurvive(level, crystalPos);
        BlockState actual = level.getBlockState(crystalPos);
        level.removeBlock(crystalPos, false);
        level.removeBlock(supportPos, false);
        if (!supported || !survives || !actual.equals(crystalState)) {
            throw new AssertionError("BETA26 Material.ROCK face must support crystals: "
                    + name + " direction=" + supportDirection + " state=" + support);
        }
    }

    private static void requireBoundaryNeighborUnloaded(
            ServerLevel level, int neighborChunkX, int sourceChunkZ, String stage) {
        boolean loaded = level.getChunkSource().getChunkNow(neighborChunkX, sourceChunkZ) != null;
        System.out.println("TC_GAMETEST_CRYSTAL_BOUNDARY_FIXTURE stage=" + stage
                + " east_loaded=" + loaded
                + " loaded_chunks=" + level.getChunkSource().getLoadedChunksCount());
        if (loaded) {
            throw new AssertionError("Crystal fixture setup loaded the east neighbour at stage " + stage);
        }
    }

    private static BlockState placeBoundaryCrystal(
            ServerLevel level, LevelChunk sourceChunk, int sourceChunkX, int sourceChunkZ,
            CrystalClusterBlock crystal, BlockPos pos, int size, int generation,
            int neighborChunkX, String fixtureName) {
        if ((pos.getX() >> 4) != sourceChunkX || (pos.getZ() >> 4) != sourceChunkZ) {
            throw new AssertionError("Boundary fixture write escaped its already-loaded chunk: " + pos);
        }
        // Level#setBlock runs the world update/light path even with UPDATE_CLIENTS;
        // at local X=15 that path can bring in the east neighbour. This test is
        // about randomTick's chunk access, so write fixture states directly into
        // the already-loaded LevelChunk and deliberately skip world notifications.
        sourceChunk.setBlockState(pos.below(), Blocks.STONE.defaultBlockState(), false);
        requireBoundaryNeighborUnloaded(level, neighborChunkX, sourceChunkZ,
                fixtureName + "-after-support-set");
        BlockState state = crystal.defaultBlockState()
                .setValue(CrystalClusterBlock.SIZE, size)
                .setValue(CrystalClusterBlock.GENERATION, generation);
        sourceChunk.setBlockState(pos, state, false);
        requireBoundaryNeighborUnloaded(level, neighborChunkX, sourceChunkZ,
                fixtureName + "-after-crystal-set");
        BlockState placed = sourceChunk.getBlockState(pos);
        requireBoundaryNeighborUnloaded(level, neighborChunkX, sourceChunkZ,
                fixtureName + "-after-state-read");
        boolean survives = state.canSurvive(level, pos);
        requireBoundaryNeighborUnloaded(level, neighborChunkX, sourceChunkZ,
                fixtureName + "-after-can-survive");
        if (!placed.equals(state) || !survives) {
            throw new AssertionError("Failed to place supported crystal fixture " + fixtureName
                    + " at " + pos + ": " + state);
        }
        return state;
    }

    private static BlockState placeCrystal(
            ServerLevel level, CrystalClusterBlock crystal, BlockPos pos, int size, int generation) {
        return placeCrystal(level, crystal, pos, size, generation, 3);
    }

    private static BlockState placeCrystal(
            ServerLevel level, CrystalClusterBlock crystal, BlockPos pos,
            int size, int generation, int updateFlags) {
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), updateFlags);
        BlockState state = crystal.defaultBlockState()
                .setValue(CrystalClusterBlock.SIZE, size)
                .setValue(CrystalClusterBlock.GENERATION, generation);
        level.setBlock(pos, state, updateFlags);
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

    private static long findBoundarySpreadSeed() {
        for (long seed = 0; seed < 100_000L; seed++) {
            RandomSource random = RandomSource.create(seed);
            if (random.nextInt(6) != 0) continue;
            int dx = random.nextInt(3) - 1;
            int dy = random.nextInt(3) - 1;
            random.nextInt(3); // dz; any horizontal depth is valid for this boundary fixture
            if (dx != 1 || dy < 0 || random.nextInt(16) != 0) continue;
            return seed;
        }
        throw new AssertionError("Could not find deterministic east-boundary crystal-spread seed");
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
