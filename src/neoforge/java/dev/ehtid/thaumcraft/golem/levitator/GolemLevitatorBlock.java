package dev.ehtid.thaumcraft.golem.levitator;

import com.mojang.serialization.MapCodec;
import dev.ehtid.thaumcraft.golem.GolemModule;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Six-way BETA26 device. Empty-hand use replaces the original small knob hit target. */
public final class GolemLevitatorBlock extends BaseEntityBlock {
    public static final MapCodec<GolemLevitatorBlock> CODEC = simpleCodec(GolemLevitatorBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public GolemLevitatorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP));
    }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getNearestLookingDirection().getOpposite();
        if (context.getPlayer() != null && context.getPlayer().isShiftKeyDown()) facing = facing.getOpposite();
        return defaultBlockState().setValue(FACING, facing);
    }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case UP -> box(0, 2, 0, 16, 16, 16);
            case DOWN -> box(0, 0, 0, 16, 14, 16);
            case EAST -> box(2, 0, 0, 16, 16, 16);
            case WEST -> box(0, 0, 0, 14, 16, 16);
            case SOUTH -> box(0, 0, 2, 16, 16, 16);
            case NORTH -> box(0, 0, 0, 16, 16, 14);
        };
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new GolemLevitatorBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level instanceof ServerLevel ? createTickerHelper(type, GolemModule.LEVITATOR_ENTITY.get(),
                (world, pos, current, device) -> GolemLevitatorBlockEntity.tick((ServerLevel) world, pos, current, device)) : null;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.mayInteract(level, pos)) return InteractionResult.FAIL;
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof GolemLevitatorBlockEntity device) {
            device.cycleRange();
            player.displayClientMessage(Component.translatable("block.thaumcraft.levitator.range", device.range(), device.cost()), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
