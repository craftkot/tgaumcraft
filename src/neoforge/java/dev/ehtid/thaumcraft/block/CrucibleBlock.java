package dev.ehtid.thaumcraft.block;

import com.mojang.serialization.MapCodec;
import dev.ehtid.thaumcraft.blockentity.CrucibleBlockEntity;
import dev.ehtid.thaumcraft.registry.ThaumcraftBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;

/**
 * Port of the BETA26 BlockCrucible's hollow bowl geometry and water-bucket
 * interaction; its alchemy processing is a separate unfinished subsystem.
 */
public final class CrucibleBlock extends BaseEntityBlock {
    public static final MapCodec<CrucibleBlock> CODEC = simpleCodec(CrucibleBlock::new);
    private static final VoxelShape SHAPE = Shapes.or(
            box(0, 0, 0, 16, 3, 16),
            box(0, 3, 0, 2, 16, 16),
            box(14, 3, 0, 16, 16, 16),
            box(2, 3, 0, 14, 16, 2),
            box(2, 3, 14, 14, 16, 16));

    public CrucibleBlock(BlockBehaviour.Properties props) { super(props); }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                         CollisionContext context) { return SHAPE; }
    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter level,
                                                   BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CrucibleBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type,
                ThaumcraftBlockEntities.CRUCIBLE.get(),
                (world, pos, actualState, tile) -> {
                    if (world instanceof ServerLevel server)
                        CrucibleBlockEntity.serverTick(server, pos, actualState, tile);
                });
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                               BlockPos pos, Player player, InteractionHand hand,
                                               BlockHitResult hit) {
        if (!stack.is(Items.WATER_BUCKET)
                || !(level.getBlockEntity(pos) instanceof CrucibleBlockEntity crucible)
                || crucible.waterMb() != 0)
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!level.isClientSide && crucible.fillBucket()) {
            if (!player.getAbilities().instabuild)
                player.setItemInHand(hand, new ItemStack(Items.BUCKET));
            level.playSound(null, pos, SoundEvents.BUCKET_EMPTY,
                    SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level,
                                                BlockPos pos, Player player,
                                                BlockHitResult hit) {
        // Original sneak-empty-hand spills remnants; the fully faithful
        // essentia release and effects depend on the later alchemy port.
        return InteractionResult.PASS;
    }
}
