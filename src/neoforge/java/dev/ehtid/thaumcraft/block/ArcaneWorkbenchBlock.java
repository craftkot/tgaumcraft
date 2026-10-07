package dev.ehtid.thaumcraft.block;

import com.mojang.serialization.MapCodec;
import dev.ehtid.thaumcraft.blockentity.ArcaneWorkbenchBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** FIRSTSTEPS Arcane Workbench block: its fifteen slots belong to the server BlockEntity. */
public final class ArcaneWorkbenchBlock extends BaseEntityBlock {
    public static final MapCodec<ArcaneWorkbenchBlock> CODEC = simpleCodec(ArcaneWorkbenchBlock::new);

    public ArcaneWorkbenchBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ArcaneWorkbenchBlockEntity(pos, state);
    }

    @Override
    protected void onRemove(BlockState oldState, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!oldState.is(newState.getBlock())) {
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof ArcaneWorkbenchBlockEntity bench) {
                Containers.dropContents(level, pos, bench);
                level.updateNeighbourForOutputSignal(pos, this);
            }
        }
        super.onRemove(oldState, level, pos, newState, moving);
    }
}
