package dev.ehtid.thaumcraft.block;

import com.mojang.serialization.MapCodec;
import dev.ehtid.thaumcraft.blockentity.EssentiaMirrorBlockEntity;
import dev.ehtid.thaumcraft.registry.ThaumcraftBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import java.util.List;
import net.minecraft.server.level.ServerLevel;

/** BETA26 Essentia Mirror block: six face mounting directions, linked item placement. */
public final class EssentiaMirrorBlock extends BaseEntityBlock {
    public static final MapCodec<EssentiaMirrorBlock> CODEC = simpleCodec(EssentiaMirrorBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    private static final VoxelShape SHAPE = box(0,0,0,16,16,16);

    public EssentiaMirrorBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH));
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block,BlockState> b) {
        b.add(FACING);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING,context.getClickedFace());
    }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public VoxelShape getShape(BlockState state, BlockGetter getter,BlockPos pos,CollisionContext context) {
        return SHAPE;
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state) {
        return new EssentiaMirrorBlockEntity(pos,state);
    }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level,BlockState state,BlockEntityType<T> type) {
        return level.isClientSide()?null:createTickerHelper(type,ThaumcraftBlockEntities.MIRROR_ESSENTIA.get(),
                (world,pos,current,tile) -> {
                    if(world instanceof ServerLevel server)
                        EssentiaMirrorBlockEntity.serverTick(server,pos,tile);
                });
    }
    /** Disconnect the partner only on actual replacement/destruction, not
     * on merely asking for loot (e.g. /loot without removing this mirror).
     */
    @Override protected void onRemove(BlockState previous,Level level,BlockPos pos,
                                       BlockState replacement,boolean moving) {
        if(previous.getBlock()!=replacement.getBlock()&&!level.isClientSide()
                &&level.getBlockEntity(pos) instanceof EssentiaMirrorBlockEntity mirror)
            mirror.disconnectOnRemoval();
        super.onRemove(previous,level,pos,replacement,moving);
    }

    /** BETA26 BlockMirror.dropMirror preserves the partner address on its dropped item.
     * Never manufacture a drop: only modify the drops approved by the loot table. */
    @Override protected List<ItemStack> getDrops(BlockState state,LootParams.Builder params) {
        List<ItemStack> drops=super.getDrops(state,params);
        BlockEntity saved=params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if(saved instanceof EssentiaMirrorBlockEntity mirror) {
            for(ItemStack stack:drops)
                if(stack.is(this.asItem()))mirror.writeLinkedItem(stack);
        }
        return drops;
    }

    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                                      LivingEntity entity, ItemStack item) {
        super.setPlacedBy(level,pos,state,entity,item);
        if(!(level instanceof ServerLevel)
                ||!(level.getBlockEntity(pos) instanceof EssentiaMirrorBlockEntity mirror))return;
        CustomData data=item.get(DataComponents.CUSTOM_DATA);
        if(data==null)return;
        CompoundTag tag=data.copyTag();
        if(tag.contains("linkDim",Tag.TAG_STRING)
                &&tag.contains("linkX",Tag.TAG_INT)
                &&tag.contains("linkY",Tag.TAG_INT)
                &&tag.contains("linkZ",Tag.TAG_INT)) {
            mirror.linkTo(tag.getString("linkDim"),
                    new BlockPos(tag.getInt("linkX"),tag.getInt("linkY"),tag.getInt("linkZ")));
        }
    }
}
