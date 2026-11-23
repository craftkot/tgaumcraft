package dev.ehtid.thaumcraft.worldgen;

import dev.ehtid.thaumcraft.port.core.SilverwoodLegacyTree;
import dev.ehtid.thaumcraft.port.core.SilverwoodCanopyRollback;
import dev.ehtid.thaumcraft.port.core.SilverwoodPlacementPreflight;
import dev.ehtid.thaumcraft.port.core.SilverwoodTrunkRollback;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import net.minecraft.util.RandomSource;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Isolated, unregistered NeoForge consumer for the M12 Silverwood write phases.
 * Block states are injected; the shared Thaumcraft registries are not modified.
 * Uses the feature's RandomSource directly for legacy nextInt calls.
 * This preserves the caller's draw stream, but does NOT prove BETA26 RNG parity.
 */
public final class SilverwoodLegacyFeature extends Feature<NoneFeatureConfiguration> {
    private final BlockState log;
    private final BlockState leaves;

    public SilverwoodLegacyFeature(BlockState log, BlockState leaves) {
        super(NoneFeatureConfiguration.CODEC);
        this.log = java.util.Objects.requireNonNull(log, "log");
        this.leaves = java.util.Objects.requireNonNull(leaves, "leaves");
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        // Do not consume an extra nextLong() to reseed a second generator.
        // The archived canopy/log phases only call nextInt(bound).
        Random random = new FeatureRandomAdapter(context.random());
        int height = 7 + random.nextInt(4);
        int x = origin.getX(), y = origin.getY(), z = origin.getZ();
        if (!SilverwoodPlacementPreflight.mayPlace(
                x, y, z, height, x >> 4, z >> 4,
                level.getMinBuildHeight(), level.getMaxBuildHeight(),
                (px, py, pz) -> {
                    BlockState soil = level.getBlockState(new BlockPos(px, py, pz));
                    return soil.is(Blocks.GRASS_BLOCK) || soil.is(Blocks.DIRT);
                },
                (px, py, pz) -> canReplace(level.getBlockState(new BlockPos(px, py, pz))))) {
            return false;
        }
        final int[] attemptedLogs = {0};
        // A rejected leaf write must not produce a success-shaped but incomplete tree.
        final boolean[] rejectedLeafWrite = {false};
        // A rejected trunk write also invalidates this attempt; stop later writes.
        final boolean[] rejectedLogWrite = {false};
        final Map<BlockPos, BlockState> originalLogs = new HashMap<>();
        final Map<BlockPos, BlockState> writtenLogs = new HashMap<>();
        // Only foliage successfully placed by this invocation may be overwritten by its logs.
        final Set<BlockPos> ownLeaves = new HashSet<>();
        // Preserve original states so a canopy-only failed attempt can be undone.
        final Map<BlockPos, BlockState> originalLeaves = new HashMap<>();
        try {
            SilverwoodLegacyTree.place(x, y, z, height, random,
                (px, py, pz) -> !rejectedLeafWrite[0]
                        && canReplace(level.getBlockState(new BlockPos(px, py, pz))),
                (px, py, pz) -> {
                    // Keep the legacy RNG traversal, but stop mutating the world
                    // after the first rejected canopy write. Rollback below still
                    // restores foliage successfully written before the rejection.
                    if (rejectedLeafWrite[0]) return;
                    BlockPos pos = new BlockPos(px, py, pz);
                    BlockState before = level.getBlockState(pos);
                    if (canReplace(before) && level.setBlock(pos, leaves, 2)) {
                        originalLeaves.putIfAbsent(pos, before);
                        ownLeaves.add(pos);
                    } else {
                        // The predicate may have observed an earlier replaceable state.
                        // A newly blocked position is a failed write, not a valid partial canopy.
                        rejectedLeafWrite[0] = true;
                    }
                },
                (px, py, pz, meta) -> {
                    // A failed canopy write already invalidates this attempt.
                    // Do not place trunk blocks that would immediately need rollback.
                    if (rejectedLeafWrite[0] || rejectedLogWrite[0]) return;
                    Direction.Axis axis = meta == 0 ? Direction.Axis.X
                            : meta == 2 ? Direction.Axis.Z : Direction.Axis.Y;
                    BlockState state = log.hasProperty(RotatedPillarBlock.AXIS)
                            ? log.setValue(RotatedPillarBlock.AXIS, axis) : log;
                    attemptedLogs[0]++;
                    BlockPos pos = new BlockPos(px, py, pz);
                    BlockState before = level.getBlockState(pos);
                    // When a log replaces our canopy, rollback must restore the
                    // pre-canopy block, not leave an orphaned leaf behind.
                    BlockState original = originalLeaves.getOrDefault(pos, before);
                    if (setLogOverOwnLeaves(level, pos, state, leaves, ownLeaves)) {
                        originalLogs.putIfAbsent(pos, original);
                        writtenLogs.put(pos, state);
                    } else {
                        rejectedLogWrite[0] = true;
                    }
                });
        } catch (RuntimeException failure) {
            // A world read/write may throw after some leaves or logs were written.
            // Best-effort rollback must not hide the original placement failure.
            try {
                rollback(level, originalLogs, writtenLogs, originalLeaves, leaves);
            } catch (RuntimeException rollbackFailure) {
                // The world callback may reuse the same exception instance. Java
                // forbids self-suppression; preserve the original placement error.
                if (rollbackFailure != failure) failure.addSuppressed(rollbackFailure);
            }
            throw failure;
        }
        // Every requested log must succeed; a partial skeleton is not a tree.
        if (rejectedLeafWrite[0] || rejectedLogWrite[0] || attemptedLogs[0] == 0 || writtenLogs.size() != attemptedLogs[0]) {
            // Restore logs first, then the remaining canopy. Both passes avoid
            // overwriting positions changed by another writer since our write.
            rollback(level, originalLogs, writtenLogs, originalLeaves, leaves);
            return false;
        }
        return true;
    }

    private static void rollback(WorldGenLevel level,
                                 Map<BlockPos, BlockState> originalLogs,
                                 Map<BlockPos, BlockState> writtenLogs,
                                 Map<BlockPos, BlockState> originalLeaves,
                                 BlockState leaves) {
        // A trunk rollback read/write may fail. Still attempt the independent
        // canopy cleanup rather than abandoning every remaining leaf.
        RuntimeException trunkFailure = null;
        try {
            SilverwoodTrunkRollback.restore(originalLogs, writtenLogs,
                    level::getBlockState, (pos, before) -> level.setBlock(pos, before, 2));
        } catch (RuntimeException failure) {
            trunkFailure = failure;
        }
        try {
            SilverwoodCanopyRollback.restore(originalLeaves, leaves,
                    level::getBlockState, (pos, before) -> level.setBlock(pos, before, 2));
        } catch (RuntimeException canopyFailure) {
            if (trunkFailure != null) {
                // Do not mask the first rollback error if callbacks throw the
                // very same RuntimeException object on both cleanup passes.
                if (canopyFailure != trunkFailure) trunkFailure.addSuppressed(canopyFailure);
            } else {
                throw canopyFailure;
            }
        }
        if (trunkFailure != null) {
            throw trunkFailure;
        }
    }

    /**
     * Bridges the core algorithm's java.util.Random signature without reseeding.
     * Only nextInt(bound) is supported by the archived Silverwood phases; fail
     * closed if a future change starts requesting other random operations.
     */
    private static final class FeatureRandomAdapter extends Random {
        private final RandomSource source;

        FeatureRandomAdapter(RandomSource source) {
            super(0L);
            this.source = java.util.Objects.requireNonNull(source, "source");
        }

        @Override
        public int nextInt(int bound) {
            return source.nextInt(bound);
        }

        @Override
        protected int next(int bits) {
            throw new UnsupportedOperationException("Silverwood RNG adapter supports nextInt(bound) only");
        }
    }

    /** Logs are emitted after canopy; permit replacing this tree's own leaf state. */
    private static boolean setLogOverOwnLeaves(WorldGenLevel level, BlockPos pos,
                                                BlockState logState, BlockState leavesState,
                                                Set<BlockPos> ownLeaves) {
        BlockState existing = level.getBlockState(pos);
        boolean ownCanopy = ownLeaves.contains(pos) && existing.equals(leavesState);
        if (!canReplace(existing) && !ownCanopy) return false;
        if (!level.setBlock(pos, logState, 2)) return false;
        // Consume provenance only after a successful write; a later callback must
        // never reuse this position as authorization to overwrite foreign foliage.
        ownLeaves.remove(pos);
        return true;
    }

    private static boolean canReplace(BlockState state) {
        return state.isAir() || state.canBeReplaced();
    }
}
