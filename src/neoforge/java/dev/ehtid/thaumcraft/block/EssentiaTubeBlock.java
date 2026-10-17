package dev.ehtid.thaumcraft.block;

import com.mojang.serialization.MapCodec;
import dev.ehtid.thaumcraft.blockentity.EssentiaTransportPort;
import dev.ehtid.thaumcraft.blockentity.EssentiaTubeBlockEntity;
import dev.ehtid.thaumcraft.registry.ThaumcraftBlockEntities;
import dev.ehtid.thaumcraft.registry.ThaumcraftItems;
import dev.ehtid.thaumcraft.registry.ThaumcraftBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionResult;
import dev.ehtid.thaumcraft.item.PhialItem;
import dev.ehtid.thaumcraft.item.CrystalEssenceItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Original BETA26 tube connections: six face-specific multipart arms. */
public class EssentiaTubeBlock extends BaseEntityBlock {
    public static final MapCodec<EssentiaTubeBlock> CODEC = simpleCodec(EssentiaTubeBlock::new);
    public static final BooleanProperty DOWN = BooleanProperty.create("down");
    public static final BooleanProperty UP = BooleanProperty.create("up");
    public static final BooleanProperty NORTH = BooleanProperty.create("north");
    public static final BooleanProperty SOUTH = BooleanProperty.create("south");
    public static final BooleanProperty WEST = BooleanProperty.create("west");
    public static final BooleanProperty EAST = BooleanProperty.create("east");
    private static final BooleanProperty[] SIDES={DOWN,UP,NORTH,SOUTH,WEST,EAST};
    private static final VoxelShape CORE=box(4,4,4,12,12,12);

    public EssentiaTubeBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(DOWN,false).setValue(UP,false)
                .setValue(NORTH,false).setValue(SOUTH,false).setValue(WEST,false)
                .setValue(EAST,false));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) {
        builder.add(DOWN,UP,NORTH,SOUTH,WEST,EAST);
    }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                         CollisionContext context) {
        VoxelShape shape=CORE;
        if(state.getValue(DOWN)) shape=Shapes.or(shape,box(6,0,6,10,4,10));
        if(state.getValue(UP)) shape=Shapes.or(shape,box(6,12,6,10,16,10));
        if(state.getValue(NORTH)) shape=Shapes.or(shape,box(6,6,0,10,10,4));
        if(state.getValue(SOUTH)) shape=Shapes.or(shape,box(6,6,12,10,10,16));
        if(state.getValue(WEST)) shape=Shapes.or(shape,box(0,6,6,4,10,10));
        if(state.getValue(EAST)) shape=Shapes.or(shape,box(12,6,6,16,10,10));
        return shape;
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EssentiaTubeBlockEntity(pos,state);
    }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level,BlockState state,BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type,
                ThaumcraftBlockEntities.TUBE.get(),
                (world,pos,current,tile)-> {
                    if(world instanceof ServerLevel server)
                        EssentiaTubeBlockEntity.serverTick(server,pos,current,tile);
                });
    }

    public static void updateConnections(Level level,BlockPos pos) {
        if(level.isClientSide() || !(level.getBlockState(pos).getBlock() instanceof EssentiaTubeBlock)
                || !(level.getBlockEntity(pos) instanceof EssentiaTransportPort tube)) return;
        BlockState initial=level.getBlockState(pos);
        BlockState next=initial;
        for(Direction dir:Direction.values()) {
            boolean linked=false;
            if(tube.transportConnects(dir)
                    && level.getBlockEntity(pos.relative(dir)) instanceof EssentiaTransportPort neighbor)
                linked=neighbor.transportConnects(dir.getOpposite());
            next=next.setValue(SIDES[dir.get3DDataValue()],linked);
        }
        if(!initial.equals(next)) level.setBlock(pos,next,3);
    }
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,
                                              Block neighbor,BlockPos neighborPos,boolean moving) {
        super.neighborChanged(state,level,pos,neighbor,neighborPos,moving);
        updateConnections(level,pos);
    }
    @Override protected void onPlace(BlockState state,Level level,BlockPos pos,
                                     BlockState oldState,boolean moved) {
        super.onPlace(state,level,pos,oldState,moved);
        updateConnections(level,pos);
        for(Direction dir:Direction.values()) updateConnections(level,pos.relative(dir));
    }

    /** BETA26 BlockTube: valve right click, filter sneak to clear imprint. */
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,
            BlockPos pos,Player player,BlockHitResult hit) {
        if(!player.mayInteract(pos))return InteractionResult.FAIL;
        if(!(level.getBlockEntity(pos) instanceof EssentiaTubeBlockEntity tube))
            return InteractionResult.PASS;
        if(state.is(ThaumcraftBlocks.TUBE_VALVE.get()) && !player.isShiftKeyDown()){
            if(!level.isClientSide())tube.flipValve();
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        if(state.is(ThaumcraftBlocks.TUBE_FILTER.get()) && player.isShiftKeyDown()
                && tube.hasFilter()){
            if(!level.isClientSide())tube.clearFilter();
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        return InteractionResult.PASS;
    }

    /** The caster configures a specific face. Unlike a free manual inventory
     * action this keeps BETA26 tube-side interaction on the magic gauntlet.
     * Full original sub-cuboid hit tracing is a later client enhancement.
     */
    @Override protected ItemInteractionResult useItemOn(ItemStack held,BlockState state,
            Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit) {
        if(!held.is(ThaumcraftItems.CASTER_BASIC.get())){
            if(state.is(ThaumcraftBlocks.TUBE_FILTER.get())
                    &&level.getBlockEntity(pos) instanceof EssentiaTubeBlockEntity tube
                    &&!tube.hasFilter()){
                String aspect=null;
                if(held.is(ThaumcraftItems.PHIAL.get()))
                    aspect=PhialItem.containedAspect(held);
                else if(held.is(ThaumcraftItems.CRYSTAL_ESSENCE.get()))
                    aspect=CrystalEssenceItem.legacyAspect(held);
                if(aspect!=null&&!aspect.isBlank()){
                    if(!player.mayInteract(pos))return ItemInteractionResult.FAIL;
                    if(!level.isClientSide())tube.applyFilter(aspect);
                    return ItemInteractionResult.sidedSuccess(level.isClientSide());
                }
            }
            if(state.is(ThaumcraftBlocks.TUBE_VALVE.get())
                    &&!held.is(ThaumcraftItems.TUBE_VALVE.get())){
                if(!player.mayInteract(pos))return ItemInteractionResult.FAIL;
                if(!level.isClientSide()
                        &&level.getBlockEntity(pos) instanceof EssentiaTubeBlockEntity tube)
                    tube.flipValve();
                return ItemInteractionResult.sidedSuccess(level.isClientSide());
            }
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if(!player.mayInteract(pos)) return ItemInteractionResult.FAIL;
        if(!level.isClientSide()
                && level.getBlockEntity(pos) instanceof EssentiaTubeBlockEntity tube)
            if(player.isShiftKeyDown())tube.rotateFacing();
            else tube.toggleSide(hit.getDirection());
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }
}
