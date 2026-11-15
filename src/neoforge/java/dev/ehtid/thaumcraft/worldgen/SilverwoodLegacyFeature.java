package dev.ehtid.thaumcraft.worldgen;

import dev.ehtid.thaumcraft.port.core.SilverwoodLegacyTree;
import dev.ehtid.thaumcraft.port.core.SilverwoodPlacementPreflight;
import java.util.Random;
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
 * The RandomSource-to-Random seed bridge is NOT exact legacy RNG parity.
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
        // Explicit compatibility seam: modern RandomSource is not java.util.Random.
        Random random = new Random(context.random().nextLong());
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
        final int[] placed = {0};
        SilverwoodLegacyTree.place(x, y, z, height, random,
                (px, py, pz) -> canReplace(level.getBlockState(new BlockPos(px, py, pz))),
                (px, py, pz) -> placed[0] += setIfReplaceable(level, new BlockPos(px, py, pz), leaves) ? 1 : 0,
                (px, py, pz, meta) -> {
                    Direction.Axis axis = meta == 0 ? Direction.Axis.X
                            : meta == 2 ? Direction.Axis.Z : Direction.Axis.Y;
                    BlockState state = log.hasProperty(RotatedPillarBlock.AXIS)
                            ? log.setValue(RotatedPillarBlock.AXIS, axis) : log;
                    if (setLogOverOwnLeaves(level, new BlockPos(px, py, pz), state, leaves)) {
                        placed[0]++;
                    }
                });
        return placed[0] > 0;
    }

    /** Logs are emitted after canopy; permit replacing this tree's own leaf state. */
    private static boolean setLogOverOwnLeaves(WorldGenLevel level, BlockPos pos,
                                                BlockState logState, BlockState leafState) {
        BlockState existing = level.getBlockState(pos);
        return (canReplace(existing) || existing.is(leafState.getBlock()))
                && level.setBlock(pos, logState, 2);
    }

    private static boolean canReplace(BlockState state) {
        return state.isAir() || state.canBeReplaced();
    }

    private static boolean setIfReplaceable(WorldGenLevel level, BlockPos pos, BlockState state) {
        return canReplace(level.getBlockState(pos)) && level.setBlock(pos, state, 2);
    }
}
