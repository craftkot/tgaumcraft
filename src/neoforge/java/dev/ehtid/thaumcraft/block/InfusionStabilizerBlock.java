package dev.ehtid.thaumcraft.block;

import com.mojang.serialization.MapCodec;
import dev.ehtid.thaumcraft.api.InfusionStabilizer;
import dev.ehtid.thaumcraft.blockentity.InfusionStabilizerBlockEntity;
import dev.ehtid.thaumcraft.infusion.InfusionStabilityBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** Original stabilizer: .25 passive contribution plus energy for inlay mitigation. */
public final class InfusionStabilizerBlock extends BaseEntityBlock implements InfusionStabilizer {
    public static final MapCodec<InfusionStabilizerBlock> CODEC = simpleCodec(InfusionStabilizerBlock::new);

    public InfusionStabilizerBlock(BlockBehaviour.Properties properties) { super(properties); }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public float stabilizationAmount(LevelReader level, BlockPos pos, BlockState state) {
        return 0.25F;
    }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new InfusionStabilizerBlockEntity(pos, state);
    }

    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type,
                InfusionStabilityBlocks.STABILIZER_ENTITY.get(),
                (world, pos, actualState, tile) -> {
                    if (world instanceof ServerLevel server)
                        InfusionStabilizerBlockEntity.serverTick(server, pos, actualState, tile);
                });
    }
}
