package dev.ehtid.thaumcraft.registry;

import dev.ehtid.thaumcraft.ThaumcraftPort;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Critical FIRSTSTEPS block registrations. */
public final class ThaumcraftBlocks {
    private ThaumcraftBlocks() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ThaumcraftPort.MOD_ID);

    public static final DeferredBlock<Block> ARCANE_WORKBENCH = BLOCKS.registerSimpleBlock(
            "arcane_workbench",
            BlockBehaviour.Properties.of()
                    .destroyTime(2.5F)
                    .explosionResistance(2.5F)
                    .sound(SoundType.WOOD));

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
    }
}
