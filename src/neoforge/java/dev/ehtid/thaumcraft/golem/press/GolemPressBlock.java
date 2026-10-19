package dev.ehtid.thaumcraft.golem.press;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import dev.ehtid.thaumcraft.golem.GolemModule;

/** Interim processor block. Original five-part Salis Mundus formation is not yet exposed. */
public final class GolemPressBlock extends BaseEntityBlock {
    public static final MapCodec<GolemPressBlock> CODEC = simpleCodec(GolemPressBlock::new);
    public GolemPressBlock(Properties properties) { super(properties); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new GolemPressBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level instanceof ServerLevel ? createTickerHelper(type, GolemModule.PRESS_ENTITY.get(),
                (world, pos, block, press) -> GolemPressBlockEntity.tick((ServerLevel) world, pos, block, press)) : null;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.mayInteract(level, pos)) return InteractionResult.FAIL;
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof GolemPressBlockEntity press && press.stillValid(player)) {
            player.openMenu(new SimpleMenuProvider((id, inv, viewer) -> new GolemPressMenu(id, inv, press),
                    Component.translatable("container.thaumcraft.golem_press")));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override protected void onRemove(BlockState old, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!old.is(next.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof GolemPressBlockEntity press) {
            Containers.dropContents(level, pos, press);
            press.clearContent(); // No second drop if another notification arrives during removal.
        }
        super.onRemove(old, level, pos, next, moving);
    }
}
