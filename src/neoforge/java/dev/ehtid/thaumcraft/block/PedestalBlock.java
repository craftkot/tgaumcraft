package dev.ehtid.thaumcraft.block;

import com.mojang.serialization.MapCodec;
import dev.ehtid.thaumcraft.blockentity.PedestalBlockEntity;
import dev.ehtid.thaumcraft.port.core.LegacyInfusionPedestalLogic;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;

/**
 * Original BETA26 BlockPedestal:
 * - a base, neck, top-cap block model (non-solid, undefined face shape),
 * - CHARGE 0..15 for the infusion inlay network (no inlay port yet),
 * - empty slot + held item -> exactly one item, held stack shrinks,
 * - occupied slot -> drop entire item at clicking player, regardless of hand,
 * - all three variants share the same authentic one-slot TilePedestal.
 */
public final class PedestalBlock extends BaseEntityBlock {
    public static final MapCodec<PedestalBlock> CODEC = simpleCodec(PedestalBlock::new);
    public static final IntegerProperty CHARGE = IntegerProperty.create("charge", 0, 15);
    private static final VoxelShape ARCANE_SHAPE = Shapes.or(
            box(0,0,0,16,4,16), box(4,4,4,12,12,12), box(2,12,2,14,16,14));
    private static final VoxelShape ELDRITCH_SHAPE = Shapes.or(
            box(0,0,0,16,4,16), box(2,4,2,14,8,14), box(4,8,4,12,12,12));

    private final LegacyInfusionPedestalLogic.Variant variant;

    public PedestalBlock(BlockBehaviour.Properties properties) {
        this(properties, LegacyInfusionPedestalLogic.Variant.ARCANE);
    }

    public PedestalBlock(BlockBehaviour.Properties properties,
                         LegacyInfusionPedestalLogic.Variant variant) {
        super(properties);
        this.variant = variant;
        registerDefaultState(stateDefinition.any().setValue(CHARGE, 0));
    }

    public float getStabilizationAmount() {
        return LegacyInfusionPedestalLogic.stabilizingStrength(variant);
    }
    public float getSymmetryPenalty() { return LegacyInfusionPedestalLogic.SYMMETRY_PENALTY; }
    public boolean hasSymmetryPenalty(PedestalBlockEntity one, PedestalBlockEntity mirror) {
        return one != null && mirror != null
                && LegacyInfusionPedestalLogic.hasSymmetryPenalty(
                        one.isOccupied(), mirror.isOccupied());
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block,BlockState> builder) {
        builder.add(CHARGE);
    }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level,
                                         BlockPos pos, CollisionContext context) {
        return variant == LegacyInfusionPedestalLogic.Variant.ARCANE
                ? ARCANE_SHAPE : ELDRITCH_SHAPE;
    }
    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter level,
                                                   BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PedestalBlockEntity(pos, state);
    }

    @Override protected ItemInteractionResult useItemOn(ItemStack held, BlockState state,
        Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof PedestalBlockEntity pedestal))
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (level.isClientSide)
            return ItemInteractionResult.SUCCESS;
        if (pedestal.isOccupied()) {
            ejectToPlayer(pedestal, level, player);
        } else if (!held.isEmpty()) {
            pedestal.insertFromPlayer(held);
            playClick(level, pos);
        }
        return ItemInteractionResult.SUCCESS;
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level,
        BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof PedestalBlockEntity pedestal
                && pedestal.isOccupied()) ejectToPlayer(pedestal, level, player);
        return InteractionResult.SUCCESS;
    }

    private static void ejectToPlayer(PedestalBlockEntity pedestal, Level level, Player player) {
        ItemStack stack = pedestal.extractToPlayer();
        if (stack.isEmpty()) return;
        // BETA26 InventoryUtils.dropItemsAtEntity: EJECT, not sneak or give/dupe.
        ItemEntity ejected = new ItemEntity(level, player.getX(), player.getY() + 0.25,
                player.getZ(), stack);
        level.addFreshEntity(ejected);
        playClick(level, pedestal.getBlockPos());
    }
    private static void playClick(Level level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS,
                0.2F, (1F + (level.random.nextFloat() - level.random.nextFloat()) * 0.7F) * 1.6F);
    }

    @Override protected void onRemove(BlockState original, Level level, BlockPos pos,
                                       BlockState replacement, boolean moving) {
        if (!original.is(replacement.getBlock()) && !level.isClientSide
                && level.getBlockEntity(pos) instanceof PedestalBlockEntity pedestal) {
            Containers.dropContents(level, pos, pedestal);
            level.updateNeighbourForOutputSignal(pos, this);
        }
        super.onRemove(original, level, pos, replacement, moving);
    }
}
