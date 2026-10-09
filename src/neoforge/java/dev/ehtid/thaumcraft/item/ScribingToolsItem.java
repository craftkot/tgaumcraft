package dev.ehtid.thaumcraft.item;

import dev.ehtid.thaumcraft.block.ResearchTableBlock;
import dev.ehtid.thaumcraft.blockentity.ResearchTableBlockEntity;
import dev.ehtid.thaumcraft.registry.ThaumcraftBlocks;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * BETA26 BlockTable#onBlockActivated: when the player uses IScribeTools on
 * tableWood, replace the wooden table with researchTable facing that player,
 * transfer a COPY of the entire scribing tool into slot 0, then clear the
 * player's hand. Unlike theory-card activation this does not consume ink.
 */
public final class ScribingToolsItem extends Item {
    public ScribingToolsItem(Properties properties) { super(properties); }

    @Override public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockState existing = level.getBlockState(context.getClickedPos());
        if (!existing.is(ThaumcraftBlocks.TABLE_WOOD.get())) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;

        BlockState upgraded = ThaumcraftBlocks.RESEARCH_TABLE.get()
                .defaultBlockState()
                .setValue(ResearchTableBlock.FACING, player.getDirection());
        if (!level.setBlock(context.getClickedPos(), upgraded, 3))
            return InteractionResult.FAIL;

        if (!(level.getBlockEntity(context.getClickedPos()) instanceof ResearchTableBlockEntity table))
            return InteractionResult.FAIL;

        ItemStack tool = player.getItemInHand(context.getHand());
        table.setItem(ResearchTableBlockEntity.SCRIBING_SLOT, tool.copy());
        player.setItemInHand(context.getHand(), ItemStack.EMPTY);
        player.getInventory().setChanged();
        table.setChanged();
        return InteractionResult.SUCCESS;
    }
}
