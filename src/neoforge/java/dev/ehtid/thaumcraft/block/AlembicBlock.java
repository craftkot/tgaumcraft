package dev.ehtid.thaumcraft.block;

import com.mojang.serialization.MapCodec;
import dev.ehtid.thaumcraft.blockentity.AlembicBlockEntity;
import dev.ehtid.thaumcraft.port.core.LegacyAlembicLogic;
import dev.ehtid.thaumcraft.registry.ThaumcraftItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** BETA26 BlockAlembic, original 2..14 x/z outline and 128 capacity. */
public final class AlembicBlock extends BaseEntityBlock {
    public static final MapCodec<AlembicBlock> CODEC=simpleCodec(AlembicBlock::new);
    private static final VoxelShape SHAPE=box(2,0,2,14,16,14);
    public AlembicBlock(BlockBehaviour.Properties props) {super(props);}
    @Override protected MapCodec<? extends BaseEntityBlock> codec(){return CODEC;}
    @Override public RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    @Override public VoxelShape getShape(BlockState state,BlockGetter world,BlockPos pos,
                                         CollisionContext ctx){return SHAPE;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){
        return new AlembicBlockEntity(pos,state);
    }
    @Override protected boolean hasAnalogOutputSignal(BlockState state){return true;}
    @Override protected int getAnalogOutputSignal(BlockState state,Level world,BlockPos pos){
        return world.getBlockEntity(pos) instanceof AlembicBlockEntity tile
                ? LegacyAlembicLogic.comparator(tile.snapshot()):0;
    }
    /** Sneak+empty hand detaches label from its face, or pours essence to Flux. */
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,
            BlockPos pos,Player player,BlockHitResult hit) {
        if(!player.isShiftKeyDown())return InteractionResult.PASS;
        if(level.isClientSide())return InteractionResult.SUCCESS;
        if(!player.mayInteract(pos))return InteractionResult.FAIL;
        if(!(level.getBlockEntity(pos) instanceof AlembicBlockEntity tile))
            return InteractionResult.PASS;
        if(tile.snapshot().filter()!=null
                && tile.snapshot().labelSide()==hit.getDirection().get3DDataValue()) {
            if(!tile.removeLabel(hit.getDirection()))return InteractionResult.FAIL;
            level.addFreshEntity(new ItemEntity(level,pos.getX()+0.5,pos.getY()+0.5,
                    pos.getZ()+0.5,new ItemStack(ThaumcraftItems.JAR_LABEL.get())));
            return InteractionResult.SUCCESS;
        }
        return tile.releaseToAura()?InteractionResult.SUCCESS:InteractionResult.PASS;
    }
}
