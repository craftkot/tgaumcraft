package dev.ehtid.thaumcraft.item;

import dev.ehtid.thaumcraft.blockentity.EssentiaMirrorBlockEntity;
import dev.ehtid.thaumcraft.registry.ThaumcraftBlocks;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;

/**
 * BETA26 BlockMirrorItem: touch an unpaired mirror with a mirror item to
 * record its destination, then place that item as the linked partner.
 */
public final class EssentiaMirrorItem extends BlockItem {
    public EssentiaMirrorItem(Block block,Properties properties) {
        super(block,properties);
    }
    @Override public InteractionResult useOn(UseOnContext context) {
        var level=context.getLevel();
        var pos=context.getClickedPos();
        if(level.getBlockState(pos).is(ThaumcraftBlocks.MIRROR_ESSENTIA.get())
                &&level.getBlockEntity(pos) instanceof EssentiaMirrorBlockEntity mirror) {
            if(context.getPlayer()==null||!context.getPlayer().mayInteract(level,pos))
                return InteractionResult.FAIL;
            if(!(level instanceof ServerLevel server))
                return InteractionResult.SUCCESS;
            if(!mirror.canSupplyLink())return InteractionResult.FAIL;
            ItemStack held=context.getItemInHand();
            CompoundTag data=new CompoundTag();
            data.putString("linkDim",server.dimension().location().toString());
            data.putInt("linkX",pos.getX());
            data.putInt("linkY",pos.getY());
            data.putInt("linkZ",pos.getZ());
            held.set(DataComponents.CUSTOM_DATA,CustomData.of(data));
            return InteractionResult.SUCCESS;
        }
        return super.useOn(context);
    }
}
