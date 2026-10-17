package dev.ehtid.thaumcraft.block;
import com.mojang.serialization.MapCodec;
import dev.ehtid.thaumcraft.blockentity.EssentiaJarBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import java.util.List;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
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
 /** Vanilla comparator semantics, sourced from BETA26 BlockJar. */
 @Override protected boolean hasAnalogOutputSignal(BlockState state) { return true; }
 @Override protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
  return level.getBlockEntity(pos) instanceof EssentiaJarBlockEntity jar
    ? dev.ehtid.thaumcraft.port.core.LegacyEssentiaJarLogic.comparatorOutput(jar.snapshot())
    : 0;
 }
 /**
  * A filled jar must not silently delete its essentia when mined.
  * Keep vanilla loot conditions (silk touch, explosions, creative mode), and
  * only attach the saved BlockEntity NBT to the jar item that actually drops.
  * Vanilla BlockItem applies minecraft:block_entity_data on placement.
  */
 @Override protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
  List<ItemStack> drops = super.getDrops(state, params);
  BlockEntity blockEntity = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
  if (blockEntity instanceof EssentiaJarBlockEntity jar && jar.hasStoredData()) {
   for (ItemStack stack : drops) {
    if (stack.is(this.asItem()) && jar.getLevel() != null
            && jar.saveToItem(stack, jar.getLevel().registryAccess()))
     dev.ehtid.thaumcraft.util.ThaumcraftSmokeAudit.mark("JAR_DROP_SAVED_COMPONENT");
   }
  }
  return drops;
 }
}
