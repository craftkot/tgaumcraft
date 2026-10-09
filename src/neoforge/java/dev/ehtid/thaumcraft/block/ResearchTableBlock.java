package dev.ehtid.thaumcraft.block;

import com.mojang.serialization.MapCodec;
import dev.ehtid.thaumcraft.blockentity.ResearchTableBlockEntity;
import dev.ehtid.thaumcraft.menu.ResearchTableMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;

/**
 * BETA26 BlockResearchTable: wooden, facing-horizontal, model-rendered, not
 * a solid face and opens TileResearchTable's 2-slot container on interaction.
 */
public final class ResearchTableBlock extends BaseEntityBlock {
    public static final MapCodec<ResearchTableBlock> CODEC = simpleCodec(ResearchTableBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public ResearchTableBlock(BlockBehaviour.Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ResearchTableBlockEntity(pos, state);
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                            Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ResearchTableBlockEntity table) {
            player.openMenu(new SimpleMenuProvider(
                    (id, inventory, viewer) -> new ResearchTableMenu(id, inventory, table),
                    Component.translatable("container.thaumcraft.research_table")));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override protected void onRemove(BlockState oldState, Level level, BlockPos pos,
                                       BlockState newState, boolean moving) {
        if (!oldState.is(newState.getBlock()) && !level.isClientSide
                && level.getBlockEntity(pos) instanceof ResearchTableBlockEntity table) {
            Containers.dropContents(level, pos, table);
            level.updateNeighbourForOutputSignal(pos, this);
        }
        super.onRemove(oldState, level, pos, newState, moving);
    }
}
