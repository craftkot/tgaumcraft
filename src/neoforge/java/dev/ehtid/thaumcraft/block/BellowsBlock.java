package dev.ehtid.thaumcraft.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;

/** BETA26 Bellows facing and enabled state, usable by smelter and tube buffer. */
public final class BellowsBlock extends Block {
    public static final MapCodec<BellowsBlock> CODEC=simpleCodec(BellowsBlock::new);
    public static final DirectionProperty FACING=BlockStateProperties.FACING;
    public static final BooleanProperty ENABLED=BooleanProperty.create("enabled");
    public BellowsBlock(BlockBehaviour.Properties props){
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH)
                .setValue(ENABLED,true));
    }
    @Override protected MapCodec<? extends Block> codec(){return CODEC;}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){
        builder.add(FACING,ENABLED);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext ctx){
        return defaultBlockState().setValue(FACING,ctx.getClickedFace().getOpposite());
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,
            BlockPos pos,Player player,BlockHitResult hit){
        if(!player.mayInteract(level, pos))return InteractionResult.FAIL;
        if(!level.isClientSide())
            level.setBlock(pos,state.setValue(ENABLED,!state.getValue(ENABLED)),3);
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
    public static int countActive(Level level,BlockPos pos,Direction[] faces){
        int count=0;
        for(Direction side:faces){
            BlockState neighbour=level.getBlockState(pos.relative(side));
            if(neighbour.getBlock() instanceof BellowsBlock
                    &&neighbour.getValue(ENABLED)
                    &&neighbour.getValue(FACING)==side.getOpposite())count++;
        }
        return count;
    }
}
