package dev.ehtid.thaumcraft.registry;

import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.block.ArcaneWorkbenchBlock;
import dev.ehtid.thaumcraft.block.ResearchTableBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Critical FIRSTSTEPS block registrations. */
public final class ThaumcraftBlocks {
    private ThaumcraftBlocks() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ThaumcraftPort.MOD_ID);

    public static final DeferredBlock<ArcaneWorkbenchBlock> ARCANE_WORKBENCH = BLOCKS.registerBlock(
            "arcane_workbench", ArcaneWorkbenchBlock::new,
            BlockBehaviour.Properties.of()
                    .destroyTime(2.5F)
                    .explosionResistance(2.5F)
                    .sound(SoundType.WOOD));

    public static final DeferredBlock<ResearchTableBlock> RESEARCH_TABLE = BLOCKS.registerBlock(
            "research_table", ResearchTableBlock::new,
            BlockBehaviour.Properties.of()
                    .destroyTime(2.5F)
                    .explosionResistance(2.5F)
                    .noOcclusion()
                    .sound(SoundType.WOOD));

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
    }
}
