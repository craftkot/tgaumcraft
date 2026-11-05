package dev.ehtid.thaumcraft.block;

import dev.ehtid.thaumcraft.api.InfusionStabilizer;
import dev.ehtid.thaumcraft.blockentity.InfusionStabilizerBlockEntity;
import dev.ehtid.thaumcraft.port.core.LegacyInfusionMitigation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** BETA26 floor inlay: .025 passive stabilization and horizontal charge wire. */
public final class InfusionInlayBlock extends Block implements InfusionStabilizer {
    // Source BlockPedestal and BlockInlay share the SAME property instance.
    public static final IntegerProperty CHARGE = PedestalBlock.CHARGE;
    private static final Direction[] SOURCE_ORDER = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    private static final VoxelShape CENTER = box(6, 0, 6, 10, 1, 10);

    public InfusionInlayBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(CHARGE, 0));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CHARGE);
    }

    @Override public float stabilizationAmount(LevelReader level, BlockPos pos, BlockState state) {
        return 0.025F;
    }

    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                         CollisionContext context) {
        VoxelShape shape = CENTER;
        for (Direction direction : SOURCE_ORDER) {
            Block neighbor = level.getBlockState(pos.relative(direction)).getBlock();
            if (!(neighbor instanceof InfusionInlayBlock) && !(neighbor instanceof PedestalBlock)
                    && !(neighbor instanceof InfusionStabilizerBlock)) continue;
            shape = Shapes.or(shape, switch (direction) {
                case SOUTH -> box(6, 0, 10, 10, 1, 16);
                case WEST -> box(0, 0, 6, 6, 1, 10);
                case NORTH -> box(6, 0, 0, 10, 1, 6);
                case EAST -> box(10, 0, 6, 16, 1, 10);
                default -> Shapes.empty();
            });
        }
        return shape;
    }

    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                                  CollisionContext context) {
        return Shapes.empty();
    }

    @Override protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor,
                                               LevelAccessor level, BlockPos pos, BlockPos from) {
        return direction == Direction.DOWN && !state.canSurvive(level, pos)
                ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, direction, neighbor, level, pos, from);
    }

    @Override protected void onPlace(BlockState state, Level level, BlockPos pos,
                                       BlockState previous, boolean moved) {
        super.onPlace(state, level, pos, previous, moved);
        if (!level.isClientSide && !previous.is(this)) updateSurroundingInlay(level, pos, state);
    }

    @Override protected void neighborChanged(BlockState state, Level level, BlockPos pos,
                                              Block neighbor, BlockPos from, boolean moved) {
        if (level.isClientSide) return;
        if (!state.canSurvive(level, pos)) {
            dropResources(state, level, pos);
            level.removeBlock(pos, false);
        } else updateSurroundingInlay(level, pos, state);
    }

    /** Shared source update entry point for inlay AND all three pedestal variants. */
    public static BlockState updateSurroundingInlay(Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide || !isConductor(state)) return state;
        int source = sourceStrength(level, pos);
        int adjacent = 0;
        for (Direction direction : SOURCE_ORDER) {
            BlockPos at = pos.relative(direction);
            if (!level.hasChunkAt(at)) continue;
            BlockState neighbor = level.getBlockState(at);
            if (isConductor(neighbor)) adjacent = Math.max(adjacent, neighbor.getValue(CHARGE));
        }
        int charge = LegacyInfusionMitigation.nextCharge(state.getValue(CHARGE), source, adjacent);
        if (charge == state.getValue(CHARGE) || level.getBlockState(pos) != state) return state;
        BlockState next = state.setValue(CHARGE, charge);
        level.setBlock(pos, next, Block.UPDATE_CLIENTS);
        // Vanilla's queued neighbor updater settles both rising and decaying loops.
        level.updateNeighborsAt(pos, next.getBlock());
        for (Direction direction : Direction.values())
            level.updateNeighborsAt(pos.relative(direction), next.getBlock());
        return next;
    }

    public static boolean isConductor(BlockState state) {
        return state.hasProperty(CHARGE)
                && (state.getBlock() instanceof InfusionInlayBlock || state.getBlock() instanceof PedestalBlock);
    }

    /** Original Plane.HORIZONTAL returns first positive N-E-S-W source, not maximum. */
    public static int sourceStrength(LevelReader level, BlockPos pos) {
        for (Direction direction : SOURCE_ORDER) {
            BlockPos at = pos.relative(direction);
            if (!level.hasChunkAt(at)) continue;
            int energy = sourceStrengthAt(level, at);
            if (energy > 0) return energy;
        }
        return 0;
    }

    public static int sourceStrengthAt(LevelReader level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof InfusionStabilizerBlock
                && level.getBlockEntity(pos) instanceof InfusionStabilizerBlockEntity stabilizer
                ? stabilizer.energy() : 0;
    }
}
