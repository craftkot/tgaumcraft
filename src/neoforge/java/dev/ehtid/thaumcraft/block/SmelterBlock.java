package dev.ehtid.thaumcraft.block;

import com.mojang.serialization.MapCodec;
import dev.ehtid.thaumcraft.blockentity.SmelterBlockEntity;
import dev.ehtid.thaumcraft.registry.ThaumcraftBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import dev.ehtid.thaumcraft.menu.SmelterMenu;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.item.context.BlockPlaceContext;

/** BETA26 base/thaumium/void furnace share original TileSmelter semantics. */
public final class SmelterBlock extends BaseEntityBlock {
    public static final MapCodec<SmelterBlock> CODEC=simpleCodec(SmelterBlock::new);
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty LIT=BlockStateProperties.LIT;
    public SmelterBlock(BlockBehaviour.Properties props){
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(LIT,false));
    }
    @Override protected MapCodec<? extends BaseEntityBlock> codec(){return CODEC;}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<
            net.minecraft.world.level.block.Block,BlockState> builder){
        builder.add(FACING,LIT);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext ctx){
        return defaultBlockState().setValue(FACING,ctx.getHorizontalDirection().getOpposite());
    }
    @Override public RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){
        return new SmelterBlockEntity(pos,state);
    }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level,BlockState state,BlockEntityType<T> type){
        return level.isClientSide()?null:createTickerHelper(type,
                ThaumcraftBlockEntities.SMELTER_BASIC.get(),
                (world,pos,actual,tile)->{
                    if(world instanceof ServerLevel server)
                        SmelterBlockEntity.serverTick(server,pos,actual,tile);
                });
    }
    /** BETA26 right-click opens the two-slot furnace inventory. */
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,
            BlockPos pos,Player player,BlockHitResult hit) {
        if(!level.isClientSide() && !player.isShiftKeyDown()
                && level.getBlockEntity(pos) instanceof SmelterBlockEntity tile)
            player.openMenu(new SimpleMenuProvider(
                    (id,inventory,viewer)->new SmelterMenu(id,inventory,tile),
                    Component.translatable("container.thaumcraft.smelter")));
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,
                                      BlockState next,boolean moving){
        if(!state.is(next.getBlock())){
            if(!level.isClientSide() && level.getBlockEntity(pos)
                    instanceof SmelterBlockEntity tile){
                if(level instanceof ServerLevel server)tile.spillOnBreak(server);
                Containers.dropContents(level,pos,tile);
            }
            super.onRemove(state,level,pos,next,moving);
        }
    }
}
