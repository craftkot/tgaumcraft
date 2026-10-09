package dev.ehtid.thaumcraft.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * BETA26 BlockTable(tableWood). Vanilla placement is a wooden 3D model; using
 * ItemScribingTools on it changes this block to research_table and transfers
 * the held tool to the new TileResearchTable's slot zero.
 */
public final class TableWoodBlock extends Block {
    public TableWoodBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }
}
