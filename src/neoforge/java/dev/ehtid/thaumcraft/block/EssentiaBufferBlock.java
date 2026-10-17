package dev.ehtid.thaumcraft.block;

import com.mojang.serialization.MapCodec;
import dev.ehtid.thaumcraft.blockentity.EssentiaBufferBlockEntity;
import dev.ehtid.thaumcraft.registry.ThaumcraftBlockEntities;
import dev.ehtid.thaumcraft.registry.ThaumcraftItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** BETA26 TubeBuffer reuses tube geometry, with its own mixed inventory tile. */
public final class EssentiaBufferBlock extends EssentiaTubeBlock {
    public static final MapCodec<EssentiaBufferBlock> CODEC=simpleCodec(EssentiaBufferBlock::new);
    public EssentiaBufferBlock(BlockBehaviour.Properties props){super(props);}
    @Override protected MapCodec<? extends net.minecraft.world.level.block.BaseEntityBlock> codec(){
        return CODEC;
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){
        return new EssentiaBufferBlockEntity(pos,state);
    }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level,BlockState state,BlockEntityType<T> type){
        return level.isClientSide()?null:createTickerHelper(type,
                ThaumcraftBlockEntities.TUBE_BUFFER.get(),
                (world,pos,current,tile)->{
                    if(world instanceof ServerLevel server)
                        EssentiaBufferBlockEntity.serverTick(server,pos,current,tile);
                });
    }
    /** BETA26 caster side hit: normal toggles port, sneaking cycles 0/1/2 choke. */
    @Override protected ItemInteractionResult useItemOn(ItemStack held,BlockState state,
            Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(!held.is(ThaumcraftItems.CASTER_BASIC.get()))
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if(!player.mayInteract(level, pos))return ItemInteractionResult.FAIL;
        if(!level.isClientSide()
                &&level.getBlockEntity(pos) instanceof EssentiaBufferBlockEntity buffer){
            if(player.isShiftKeyDown())buffer.cycleChoke(hit.getDirection());
            else buffer.toggleSide(hit.getDirection());
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }
}
