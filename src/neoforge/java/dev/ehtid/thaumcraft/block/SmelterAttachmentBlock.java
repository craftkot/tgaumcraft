package dev.ehtid.thaumcraft.block;

import com.mojang.serialization.MapCodec;
import dev.ehtid.thaumcraft.registry.ThaumcraftBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** BETA26 SmelterVent and SmelterAux share horizontal attachment facing. */
public final class SmelterAttachmentBlock extends Block {
    public static final MapCodec<SmelterAttachmentBlock> CODEC=simpleCodec(SmelterAttachmentBlock::new);
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    public SmelterAttachmentBlock(BlockBehaviour.Properties props){
        super(props);registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH));
    }
    @Override protected MapCodec<? extends Block> codec(){return CODEC;}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){
        builder.add(FACING);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext ctx){
        if(!ctx.getClickedFace().getAxis().isHorizontal())return null;
        Direction back=ctx.getClickedFace().getOpposite();
        BlockPos furnace=ctx.getClickedPos().relative(back);
        BlockState host=ctx.getLevel().getBlockState(furnace);
        if(!(host.getBlock() instanceof SmelterBlock)
                ||host.getValue(SmelterBlock.FACING)==ctx.getClickedFace())return null;
        return defaultBlockState().setValue(FACING,back);
    }
    @Override public VoxelShape getShape(BlockState state,BlockGetter level,
            BlockPos pos,CollisionContext context) {
        if(state.is(ThaumcraftBlocks.SMELTER_VENT.get())){
            return switch(state.getValue(FACING)){
                case NORTH -> box(2,2,0,14,14,8);
                case SOUTH -> box(2,2,8,14,14,16);
                case WEST -> box(0,2,2,8,14,14);
                case EAST -> box(8,2,2,16,14,14);
                default -> box(2,2,0,14,14,8);
            };
        }
        return box(1,1,1,15,16,15);
    }
}
