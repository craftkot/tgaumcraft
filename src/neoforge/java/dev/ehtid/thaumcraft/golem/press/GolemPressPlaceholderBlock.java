package dev.ehtid.thaumcraft.golem.press;

import dev.ehtid.thaumcraft.port.core.golem.GolemPressBlueprint.Part;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Persistent ordinary placeholder; ownership/footprint resides once in dimension SavedData. */
public final class GolemPressPlaceholderBlock extends Block {
    private final Part part;
    public GolemPressPlaceholderBlock(Properties properties, Part part) { super(properties); this.part = part; }
    public Part part() { return part; }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server && player.mayInteract(level, pos)) {
            BlockPos controller = GolemPressStructures.get(server).controllerFor(pos);
            if (controller != null && server.hasChunkAt(controller)) GolemPressBlock.open(server, controller, player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override protected void onRemove(BlockState old, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!old.is(next.getBlock()) && level instanceof ServerLevel server) GolemPressStructures.get(server).removed(server, pos);
        super.onRemove(old, level, pos, next, moving);
    }
}
