package dev.ehtid.thaumcraft.registry;

import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.block.ArcaneWorkbenchBlock;
import dev.ehtid.thaumcraft.block.ResearchTableBlock;
import dev.ehtid.thaumcraft.block.TableWoodBlock;
import dev.ehtid.thaumcraft.block.CrucibleBlock;
import dev.ehtid.thaumcraft.block.NitorBlock;
import dev.ehtid.thaumcraft.block.PedestalBlock;
import dev.ehtid.thaumcraft.block.InfusionPillarBlock;
import dev.ehtid.thaumcraft.block.InfusionMatrixBlock;
import dev.ehtid.thaumcraft.port.core.LegacyInfusionPillarLogic;
import dev.ehtid.thaumcraft.port.core.LegacyInfusionPedestalLogic;
import dev.ehtid.thaumcraft.port.core.LegacyNitorColors;
import net.minecraft.world.item.DyeColor;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
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

    // All three original BETA26 infusion pedestals share TilePedestal.
    public static final DeferredBlock<PedestalBlock> PEDESTAL_ARCANE = BLOCKS.registerBlock(
            "pedestal_arcane",
            props -> new PedestalBlock(props, LegacyInfusionPedestalLogic.Variant.ARCANE),
            BlockBehaviour.Properties.of().strength(2.5F).noOcclusion().sound(SoundType.STONE));
    public static final DeferredBlock<PedestalBlock> PEDESTAL_ANCIENT = BLOCKS.registerBlock(
            "pedestal_ancient",
            props -> new PedestalBlock(props, LegacyInfusionPedestalLogic.Variant.ANCIENT),
            BlockBehaviour.Properties.of().strength(2.5F).noOcclusion().sound(SoundType.STONE));
    public static final DeferredBlock<PedestalBlock> PEDESTAL_ELDRITCH = BLOCKS.registerBlock(
            "pedestal_eldritch",
            props -> new PedestalBlock(props, LegacyInfusionPedestalLogic.Variant.ELDRITCH),
            BlockBehaviour.Properties.of().strength(2.5F).noOcclusion().sound(SoundType.STONE));

    /** Source BETA26 runic matrix with a persistent Tile and layout gate. */
    public static final DeferredBlock<InfusionMatrixBlock> INFUSION_MATRIX = BLOCKS.registerBlock(
            "infusion_matrix", InfusionMatrixBlock::new,
            BlockBehaviour.Properties.of().strength(2.5F).noOcclusion().sound(SoundType.STONE));

    /** Original BETA26 pillars (all satisfy TileInfusionMatrix.validLocation). */
    public static final DeferredBlock<InfusionPillarBlock> PILLAR_ARCANE = BLOCKS.registerBlock(
            "pillar_arcane",
            props -> new InfusionPillarBlock(props, LegacyInfusionPillarLogic.Variant.ARCANE),
            BlockBehaviour.Properties.of().strength(2.5F).noOcclusion().sound(SoundType.STONE));
    public static final DeferredBlock<InfusionPillarBlock> PILLAR_ANCIENT = BLOCKS.registerBlock(
            "pillar_ancient",
            props -> new InfusionPillarBlock(props, LegacyInfusionPillarLogic.Variant.ANCIENT),
            BlockBehaviour.Properties.of().strength(2.5F).noOcclusion().sound(SoundType.STONE));
    public static final DeferredBlock<InfusionPillarBlock> PILLAR_ELDRITCH = BLOCKS.registerBlock(
            "pillar_eldritch",
            props -> new InfusionPillarBlock(props, LegacyInfusionPillarLogic.Variant.ELDRITCH),
            BlockBehaviour.Properties.of().strength(2.5F).noOcclusion().sound(SoundType.STONE));

    // BETA26 registers one separate BlockNitor for every EnumDyeColor.
    // The original names use silver/lightblue, not light_gray/light_blue.
    public static final Map<DyeColor, DeferredBlock<NitorBlock>> NITOR =
            registerAllNitorVariants();

    // Preserve the existing production/recipe identifier and Java field.
    public static final DeferredBlock<NitorBlock> NITOR_YELLOW = NITOR.get(DyeColor.YELLOW);

    private static Map<DyeColor, DeferredBlock<NitorBlock>> registerAllNitorVariants() {
        Map<DyeColor, DeferredBlock<NitorBlock>> registered =
                new EnumMap<>(DyeColor.class);
        for (LegacyNitorColors.Variant color : LegacyNitorColors.variants()) {
            DyeColor dye = DyeColor.valueOf(color.modernDye().toUpperCase(Locale.ROOT));
            var entry = BLOCKS.registerBlock("nitor_" + color.legacy(),
                    props -> new NitorBlock(props, color.particleRgb()),
                    BlockBehaviour.Properties.of().strength(0.1f)
                            .lightLevel(state -> 15)
                            .noCollission().noOcclusion().sound(SoundType.GLASS));
            registered.put(dye, entry);
        }
        if (registered.size() != 16) throw new IllegalStateException("Missing original Nitor color");
        return Collections.unmodifiableMap(registered);
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
    }
}
