package dev.ehtid.thaumcraft.golem.press;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

/** BETA26 BlockTable(tableStone): ordinary block, full collision, only the top supports attachments. */
public final class GolemStoneTableBlock extends Block {
    private static final VoxelShape TOP_SUPPORT = Block.box(0, 15, 0, 16, 16, 16);
    public GolemStoneTableBlock(Properties properties) { super(properties); }
    @Override protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) { return TOP_SUPPORT; }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return InteractionResult.sidedSuccess(level.isClientSide); // Source consumes interaction without opening a GUI.
    }
}
