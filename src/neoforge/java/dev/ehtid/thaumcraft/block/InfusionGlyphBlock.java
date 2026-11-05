package dev.ehtid.thaumcraft.block;

import dev.ehtid.thaumcraft.port.core.LegacyInfusionStability;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** Source matrix_speed/matrix_cost stone blocks. Their effect is read below each pillar. */
public final class InfusionGlyphBlock extends Block {
    private final LegacyInfusionStability.Glyph glyph;

    public InfusionGlyphBlock(BlockBehaviour.Properties properties, LegacyInfusionStability.Glyph glyph) {
        super(properties);
        if (glyph == null || glyph == LegacyInfusionStability.Glyph.NONE)
            throw new IllegalArgumentException("speed or cost glyph required");
        this.glyph = glyph;
    }

    public LegacyInfusionStability.Glyph glyph() { return glyph; }
}
