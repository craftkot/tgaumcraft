package dev.ehtid.thaumcraft.block;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Original BETA26 BlockCandle: 4x8x4 wax body, 14 light, smoke/flame,
 * requires a solid base, stabilizes Infusion at +0.1 per matched pair.
 */
public final class TallowCandleBlock extends Block {
    private static final VoxelShape SHAPE = box(6,0,6,10,8,10);
    private final int rgb;
    public TallowCandleBlock(BlockBehaviour.Properties properties, int rgb) {
        super(properties); this.rgb=rgb & 0xFFFFFF;
    }
    public int waxRgb() { return rgb; }
    public float getStabilizationAmount() { return 0.1F; }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level,
               BlockPos pos, CollisionContext context) { return SHAPE; }
    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter level,
               BlockPos pos, CollisionContext context) { return Shapes.empty(); }
    @Override protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below=pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        var state=defaultBlockState();
        return state.canSurvive(context.getLevel(),context.getClickedPos())?state:null;
    }
    @Override protected BlockState updateShape(BlockState state, Direction direction,
                     BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos from) {
        if (direction==Direction.DOWN && !state.canSurvive(level,pos))
            return Blocks.AIR.defaultBlockState();
        return super.updateShape(state,direction,neighbor,level,pos,from);
    }
    @Override public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource rng) {
        double x=pos.getX()+0.5, y=pos.getY()+0.7, z=pos.getZ()+0.5;
        level.addParticle(ParticleTypes.SMOKE,x,y,z,0,0,0);
        level.addParticle(ParticleTypes.FLAME,x,y,z,0,0,0);
    }
}
