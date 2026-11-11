package dev.ehtid.thaumcraft.block;

import dev.ehtid.thaumcraft.blockentity.NitorBlockEntity;
import dev.ehtid.thaumcraft.registry.NitorBlockEntityRegistration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import org.joml.Vector3f;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * BETA26 BlockNitor's floating, incorporeal, full-light source.
 * Original bounds are 0.33..0.66 on all axes and have NO collision.
 *
 * BETA26 TileNitor is a client-ticking block entity: one flame effect per
 * client tick and one core effect every tenth tick. The current vanilla
 * particle fallback keeps that cadence; exact FXGeneric atlas sprites are
 * still a separate M16 task.
 */
public final class NitorBlock extends BaseEntityBlock {
    private static final VoxelShape OUTLINE =
            box(5.28, 5.28, 5.28, 10.56, 10.56, 10.56);

    private final int flameRgb;
    private final DustParticleOptions coloredGlow;

    public NitorBlock(BlockBehaviour.Properties props, int flameRgb) {
        super(props);
        this.flameRgb = flameRgb & 0xFFFFFF;
        this.coloredGlow = new DustParticleOptions(new Vector3f(
                ((this.flameRgb >> 16) & 0xFF) / 255.0f,
                ((this.flameRgb >> 8) & 0xFF) / 255.0f,
                (this.flameRgb & 0xFF) / 255.0f), 0.85f);
    }

    public int flameRgb() { return flameRgb; }

    public DustParticleOptions coloredGlow() { return coloredGlow; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new NitorBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, NitorBlockEntityRegistration.TYPE, NitorBlockEntity::clientTick)
                : null;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world,
                               BlockPos pos, CollisionContext context) {
        return OUTLINE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world,
                                        BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }
}
