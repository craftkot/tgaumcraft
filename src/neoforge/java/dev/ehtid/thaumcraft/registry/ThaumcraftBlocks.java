package dev.ehtid.thaumcraft.registry;

import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.block.ArcaneWorkbenchBlock;
import dev.ehtid.thaumcraft.block.ResearchTableBlock;
import dev.ehtid.thaumcraft.block.TableWoodBlock;
import dev.ehtid.thaumcraft.block.CrucibleBlock;
import dev.ehtid.thaumcraft.block.NitorBlock;
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

    public static final DeferredBlock<TableWoodBlock> TABLE_WOOD = BLOCKS.registerBlock(
            "table_wood", TableWoodBlock::new,
            BlockBehaviour.Properties.of()
                    .destroyTime(2.5F)
                    .explosionResistance(2.5F)
                    .noOcclusion()
                    .sound(SoundType.WOOD));

    public static final DeferredBlock<ResearchTableBlock> RESEARCH_TABLE = BLOCKS.registerBlock(
            "research_table", ResearchTableBlock::new,
            BlockBehaviour.Properties.of()
                    .destroyTime(2.5F)
                    .explosionResistance(2.5F)
                    .noOcclusion()
                    .sound(SoundType.WOOD));

    public static final DeferredBlock<CrucibleBlock> CRUCIBLE = BLOCKS.registerBlock(
            "crucible", CrucibleBlock::new,
            BlockBehaviour.Properties.of()
                    .destroyTime(2.5F).explosionResistance(10.0F)
                    .noOcclusion().sound(SoundType.STONE));

    /**
     * Original BETA26 default nitor catalyst output is the floating
     * bright yellow, fully luminous variant (16 dye variants exist).
     */
    public static final DeferredBlock<NitorBlock> NITOR_YELLOW = BLOCKS.registerBlock(
            "nitor_yellow", NitorBlock::new,
            BlockBehaviour.Properties.of()
                    .strength(0.1F).lightLevel(state -> 15)
                    .noCollission().noOcclusion().sound(SoundType.GLASS));

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
    }
}
