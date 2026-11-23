package dev.ehtid.thaumcraft.worldgen;

import dev.ehtid.thaumcraft.port.core.GreatwoodTrunkPlan;
import dev.ehtid.thaumcraft.port.core.GreatwoodTrunkPlacement;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Unregistered M12 Greatwood trunk-only NeoForge consumer.
 * No branches, canopy, biome selection or natural placement are claimed.
 * Caller injects a log state; shared registries are intentionally untouched.
 */
public final class GreatwoodLegacyTrunkFeature extends Feature<NoneFeatureConfiguration> {
    private final BlockState log;

    public GreatwoodLegacyTrunkFeature(BlockState log) {
        super(NoneFeatureConfiguration.CODEC);
        this.log = Objects.requireNonNull(log, "log");
        if (log.isAir()) throw new IllegalArgumentException("Greatwood log must not be air");
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        // Archived Greatwood seeds a separate java.util.Random with one chunk RNG nextLong.
        Random treeRandom = new Random(context.random().nextLong());
        int heightLimit = 11 + treeRandom.nextInt(11);
        // A trunk-only prototype uses the complete height limit as a conservative
        // vertical envelope. The original branch/canopy algorithm is NOT implemented.
        List<GreatwoodTrunkPlan.Cell> plan = GreatwoodTrunkPlan.create(
                origin.getX(), origin.getY(), origin.getZ(), heightLimit,
                level.getMinBuildHeight(), level.getMaxBuildHeight());
        if (plan.isEmpty()) return false;
        // Never spill into a neighboring chunk from this unregistered prototype.
        if ((origin.getX() & 15) == 15 || (origin.getZ() & 15) == 15) return false;
        // Soil is one block below the origin; never query outside build bounds.
        if (origin.getY() <= level.getMinBuildHeight()) return false;
        // Every column of the 2x2 trunk needs its own suitable foundation.
        for (int dx = 0; dx < 2; dx++) {
            for (int dz = 0; dz < 2; dz++) {
                BlockState soil = level.getBlockState(origin.offset(dx, -1, dz));
                if (!soil.is(Blocks.GRASS_BLOCK) && !soil.is(Blocks.DIRT)) return false;
            }
        }

        return GreatwoodTrunkPlacement.place(plan, log,
                new GreatwoodTrunkPlacement.WorldAdapter<>() {
                    @Override
                    public BlockState read(GreatwoodTrunkPlan.Cell cell) {
                        return level.getBlockState(pos(cell));
                    }

                    @Override
                    public boolean canReplace(GreatwoodTrunkPlan.Cell cell, BlockState previous) {
                        return previous.isAir() || previous.canBeReplaced();
                    }

                    @Override
                    public boolean write(GreatwoodTrunkPlan.Cell cell, BlockState state) {
                        return level.setBlock(pos(cell), state, 2);
                    }

                    private BlockPos pos(GreatwoodTrunkPlan.Cell cell) {
                        return new BlockPos(cell.x(), cell.y(), cell.z());
                    }
                });
    }
}
