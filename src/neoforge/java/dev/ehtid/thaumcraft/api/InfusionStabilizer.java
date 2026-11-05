package dev.ehtid.thaumcraft.api;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Block extension equivalent to BETA26 IInfusionStabiliser/Ext. Implement on
 * a registered block; the matrix queries the actual world on every scan.
 * A matched pair must be the same block and report exactly equal strength.
 */
public interface InfusionStabilizer {
    default boolean canStabilizeInfusion(LevelReader world, BlockPos pos, BlockState state) {
        return true;
    }

    default float stabilizationAmount(LevelReader world, BlockPos pos, BlockState state) {
        return 0.1F;
    }

    default boolean hasSymmetryPenalty(LevelReader world, BlockPos pos, BlockPos mirror) {
        return false;
    }

    default float symmetryPenalty(LevelReader world, BlockPos pos) {
        return 0.0F;
    }
}
