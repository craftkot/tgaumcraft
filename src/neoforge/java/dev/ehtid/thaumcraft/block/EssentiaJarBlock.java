package dev.ehtid.thaumcraft.block;
import com.mojang.serialization.MapCodec;
import dev.ehtid.thaumcraft.blockentity.EssentiaJarBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
/** BETA26 normal jar, original geometry bounds. */
public final class EssentiaJarBlock extends BaseEntityBlock {
 public static final MapCodec<EssentiaJarBlock> CODEC = simpleCodec(EssentiaJarBlock::new);
 private static final VoxelShape SHAPE = box(3,0,3,13,14,13);
 public EssentiaJarBlock(BlockBehaviour.Properties props) { super(props); }
 @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
 @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
 @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }
 @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new EssentiaJarBlockEntity(pos, state); }
}
