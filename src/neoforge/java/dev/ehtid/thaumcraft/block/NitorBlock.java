package dev.ehtid.thaumcraft.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * BETA26 BlockNitor's floating, incorporeal, full-light source.
 * Original bounds are 0.33..0.66 on all axes and have NO collision.
 *
 * Original TileNitor used custom FXDispatcher flame+core particles. This
 * port initially uses vanilla luminous particles on the same approximate
 * center while the full original custom particle renderer is pending.
 */
public final class NitorBlock extends Block {
    private static final VoxelShape OUTLINE =
            box(5.28, 5.28, 5.28, 10.56, 10.56, 10.56);

    public NitorBlock(BlockBehaviour.Properties props) {
        super(props);
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

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        double x = pos.getX() + 0.5D + random.nextGaussian() * 0.025D;
        double y = pos.getY() + 0.45D + random.nextGaussian() * 0.025D;
        double z = pos.getZ() + 0.5D + random.nextGaussian() * 0.025D;
        level.addParticle(ParticleTypes.FLAME, x, y, z,
                random.nextGaussian() * 0.0025D,
                0.01D + random.nextDouble() * 0.035D,
                random.nextGaussian() * 0.0025D);
        if (random.nextInt(10) == 0) {
            level.addParticle(ParticleTypes.END_ROD,
                    pos.getX() + 0.5D, pos.getY() + 0.49D, pos.getZ() + 0.5D,
                    0.0D, 0.0D, 0.0D);
        }
    }
}
