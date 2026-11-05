package dev.ehtid.thaumcraft.crafting;

import dev.ehtid.thaumcraft.api.InfusionStabilizer;
import dev.ehtid.thaumcraft.block.InfusionGlyphBlock;
import dev.ehtid.thaumcraft.block.InfusionInlayBlock;
import dev.ehtid.thaumcraft.block.InfusionPillarBlock;
import dev.ehtid.thaumcraft.block.PedestalBlock;
import dev.ehtid.thaumcraft.block.TallowCandleBlock;
import dev.ehtid.thaumcraft.blockentity.InfusionStabilizerBlockEntity;
import dev.ehtid.thaumcraft.blockentity.PedestalBlockEntity;
import dev.ehtid.thaumcraft.port.core.LegacyInfusionMatrixLayout;
import dev.ehtid.thaumcraft.port.core.LegacyInfusionStability;
import dev.ehtid.thaumcraft.port.core.LegacyInfusionSurroundings;
import dev.ehtid.thaumcraft.registry.ThaumcraftBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Actual loaded-world BETA26 surroundings scan and charged-inlay mitigation lookup. */
public final class InfusionStabilizerAdapter {
    private InfusionStabilizerAdapter() {}

    private static final Set<Block> ORIGINAL_SKULLS = Set.of(
            Blocks.SKELETON_SKULL, Blocks.SKELETON_WALL_SKULL,
            Blocks.WITHER_SKELETON_SKULL, Blocks.WITHER_SKELETON_WALL_SKULL,
            Blocks.ZOMBIE_HEAD, Blocks.ZOMBIE_WALL_HEAD,
            Blocks.PLAYER_HEAD, Blocks.PLAYER_WALL_HEAD,
            Blocks.CREEPER_HEAD, Blocks.CREEPER_WALL_HEAD,
            Blocks.DRAGON_HEAD, Blocks.DRAGON_WALL_HEAD);
    // EnumFacing.HORIZONTALS in 1.12, not modern Direction enum declaration order.
    private static final Direction[] LEGACY_HORIZONTAL = {
            Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST};

    public record Snapshot(LegacyInfusionStability.Modifiers modifiers,
                           LegacyInfusionStability.Balance balance,
                           List<BlockPos> pedestals, List<BlockPos> problemPositions) {
        public Snapshot {
            pedestals = List.copyOf(pedestals);
            problemPositions = List.copyOf(problemPositions);
        }
        public float replenishment() { return modifiers.replenish() + balance.replenishment(); }
    }

    /** Unloaded neighboring chunks are not force-loaded by a matrix scan. */
    public static Snapshot scan(Level level, BlockPos matrix) {
        if (level == null || matrix == null) throw new IllegalArgumentException("level/matrix");
        var corners = new ArrayList<LegacyInfusionStability.Pillar>();
        for (var offset : LegacyInfusionMatrixLayout.requiredPillarOffsets()) {
            var pillar = stateAt(level, at(matrix, offset)).getBlock();
            corners.add(pillar instanceof InfusionPillarBlock block ? switch (block.variant()) {
                case ARCANE -> LegacyInfusionStability.Pillar.ARCANE;
                case ANCIENT -> LegacyInfusionStability.Pillar.ANCIENT;
                case ELDRITCH -> LegacyInfusionStability.Pillar.ELDRITCH;
            } : LegacyInfusionStability.Pillar.MISSING);
        }
        var glyphs = new ArrayList<LegacyInfusionStability.Glyph>();
        // Source glyph slot order matters to float cost arithmetic.
        for (int[] offset : new int[][]{{-1, -1}, {1, -1}, {1, 1}, {-1, 1}}) {
            var glyph = stateAt(level, matrix.offset(offset[0], -3, offset[1])).getBlock();
            glyphs.add(glyph instanceof InfusionGlyphBlock block ? block.glyph() : LegacyInfusionStability.Glyph.NONE);
        }
        var modifiers = corners.contains(LegacyInfusionStability.Pillar.MISSING)
                ? new LegacyInfusionStability.Modifiers(10, 1, 0, 5)
                : LegacyInfusionStability.cornerModifiers(corners);
        modifiers = LegacyInfusionStability.withGlyphs(modifiers, glyphs);

        Map<LegacyInfusionMatrixLayout.Offset, BlockState> states = new HashMap<>();
        Map<LegacyInfusionMatrixLayout.Offset, LegacyInfusionSurroundings.Sample> samples = new HashMap<>();
        var pedestals = new ArrayList<BlockPos>();
        for (var offset : LegacyInfusionSurroundings.scanOffsets()) {
            BlockPos pos = at(matrix, offset);
            BlockState state = stateAt(level, pos);
            Block block = state.getBlock();
            states.put(offset, state);
            if (block instanceof PedestalBlock) {
                pedestals.add(pos);
                // Original adjusts cost for each scanned pedestal, in scan order.
                modifiers = LegacyInfusionStability.withPedestals(modifiers,
                        block == ThaumcraftBlocks.PEDESTAL_ANCIENT.get() ? 1 : 0,
                        block == ThaumcraftBlocks.PEDESTAL_ELDRITCH.get() ? 1 : 0);
            }
            samples.put(offset, sample(level, pos, state));
        }
        var balance = LegacyInfusionSurroundings.balance(matrix.getX(), matrix.getY(), matrix.getZ(),
                samples::get, (one, mirror) -> hasSymmetryPenalty(level, at(matrix, one),
                        at(matrix, mirror), states.get(one).getBlock()));
        var problems = balance.problemPositions().stream().map(offset -> at(matrix, offset)).toList();
        return new Snapshot(modifiers, balance, pedestals, problems);
    }

    private static BlockState stateAt(Level level, BlockPos pos) {
        return level.hasChunkAt(pos) ? level.getBlockState(pos) : Blocks.AIR.defaultBlockState();
    }

    private static BlockPos at(BlockPos matrix, LegacyInfusionMatrixLayout.Offset offset) {
        return matrix.offset(offset.x(), offset.y(), offset.z());
    }

    private static LegacyInfusionSurroundings.Sample sample(Level level, BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        String key = BuiltInRegistries.BLOCK.getKey(block).toString();
        if (ORIGINAL_SKULLS.contains(block)) return new LegacyInfusionSurroundings.Sample("minecraft:skull", .1F, true, 0);
        if (block instanceof InfusionStabilizer provider) return new LegacyInfusionSurroundings.Sample(key,
                provider.stabilizationAmount(level, pos, state), provider.canStabilizeInfusion(level, pos, state),
                provider.symmetryPenalty(level, pos));
        if (block instanceof PedestalBlock pedestal) return new LegacyInfusionSurroundings.Sample(key,
                pedestal.getStabilizationAmount(), true, pedestal.getSymmetryPenalty());
        if (block instanceof TallowCandleBlock candle) return new LegacyInfusionSurroundings.Sample(key,
                candle.getStabilizationAmount(), true, 0);
        return new LegacyInfusionSurroundings.Sample(key, LegacyInfusionStability.DEFAULT_UNMATCHED_STRENGTH, false, 0);
    }

    private static boolean hasSymmetryPenalty(Level level, BlockPos one, BlockPos mirror, Block block) {
        if (block instanceof InfusionStabilizer provider) return provider.hasSymmetryPenalty(level, one, mirror);
        return block instanceof PedestalBlock pedestal
                && level.getBlockEntity(one) instanceof PedestalBlockEntity first
                && level.getBlockEntity(mirror) instanceof PedestalBlockEntity second
                && pedestal.hasSymmetryPenalty(first, second);
    }

    /**
     * Source pedestal.findInstabilityMitigator. Finding energy >=5 does not
     * guarantee that the later random debit of 5..10 succeeds. Caller owns RNG.
     */
    public static InfusionStabilizerBlockEntity findMitigator(ServerLevel level, BlockPos pedestal) {
        BlockState state = stateAt(level, pedestal);
        if (!(state.getBlock() instanceof PedestalBlock) || state.getValue(PedestalBlock.CHARGE) <= 0) return null;
        return seekMitigator(level, pedestal, state.getValue(PedestalBlock.CHARGE), new HashSet<>());
    }

    private static InfusionStabilizerBlockEntity seekMitigator(ServerLevel level, BlockPos current,
                                                              int charge, Set<BlockPos> visited) {
        if (!visited.add(current)) return null;
        for (Direction direction : LEGACY_HORIZONTAL) {
            BlockPos next = current.relative(direction);
            if (!level.hasChunkAt(next)) continue;
            if (InfusionInlayBlock.sourceStrengthAt(level, next) >= 5
                    && level.getBlockEntity(next) instanceof InfusionStabilizerBlockEntity source)
                return source;
            BlockState state = level.getBlockState(next);
            if (state.hasProperty(InfusionInlayBlock.CHARGE) && state.getValue(InfusionInlayBlock.CHARGE) > charge) {
                var result = seekMitigator(level, next, state.getValue(InfusionInlayBlock.CHARGE), visited);
                if (result != null) return result;
            }
        }
        return null;
    }
}
