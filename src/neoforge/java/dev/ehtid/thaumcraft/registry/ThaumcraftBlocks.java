package dev.ehtid.thaumcraft.registry;

import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.block.ArcaneWorkbenchBlock;
import dev.ehtid.thaumcraft.block.ResearchTableBlock;
import dev.ehtid.thaumcraft.block.TableWoodBlock;
import dev.ehtid.thaumcraft.block.CrucibleBlock;
import dev.ehtid.thaumcraft.block.NitorBlock;
import dev.ehtid.thaumcraft.block.TallowCandleBlock;
import dev.ehtid.thaumcraft.block.PedestalBlock;
import dev.ehtid.thaumcraft.block.InfusionPillarBlock;
import dev.ehtid.thaumcraft.block.InfusionMatrixBlock;
import dev.ehtid.thaumcraft.block.CrystalClusterBlock;
import dev.ehtid.thaumcraft.block.EssentiaJarBlock;
import dev.ehtid.thaumcraft.block.EssentiaTubeBlock;
import dev.ehtid.thaumcraft.block.EssentiaBufferBlock;
import dev.ehtid.thaumcraft.block.AlembicBlock;
import dev.ehtid.thaumcraft.block.BellowsBlock;
import dev.ehtid.thaumcraft.block.SmelterAttachmentBlock;
import dev.ehtid.thaumcraft.block.SmelterBlock;
import net.minecraft.world.level.block.Block;
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

    /** BETA26 TileJarFillable, persisted world essentia. */
    public static final DeferredBlock<EssentiaJarBlock> JAR_NORMAL = BLOCKS.registerBlock(
            "jar_normal", EssentiaJarBlock::new,
            // Source BlockJar: hardness 0.3, inherited device resistance 20.
            BlockBehaviour.Properties.of().strength(0.3F, 20.0F)
                    .noOcclusion().sound(SoundType.GLASS));

    /** Original BETA26 void jar: same glass outline, different suction/overflow. */
    public static final DeferredBlock<EssentiaJarBlock> JAR_VOID = BLOCKS.registerBlock(
            "jar_void", EssentiaJarBlock::new,
            BlockBehaviour.Properties.of().strength(0.3F, 20.0F)
                    .noOcclusion().sound(SoundType.GLASS));

    /** Original BlockTube: iron, six configurable faces and one-unit buffer. */
    public static final DeferredBlock<EssentiaTubeBlock> TUBE = BLOCKS.registerBlock(
            "tube", EssentiaTubeBlock::new,
            BlockBehaviour.Properties.of().strength(0.5F,5.0F)
                    .noOcclusion().sound(SoundType.METAL));

    /** BETA26 greatwood boards: an actual wood block, not vanilla planks. */
    public static final DeferredBlock<Block> PLANK_GREATWOOD = BLOCKS.registerSimpleBlock(
            "plank_greatwood",
            BlockBehaviour.Properties.of().strength(2.0F, 3.0F).sound(SoundType.WOOD));

    /** Distillation vessel: BETA26 BlockAlembic. */
    public static final DeferredBlock<AlembicBlock> ALEMBIC = BLOCKS.registerBlock(
            "alembic", AlembicBlock::new,
            BlockBehaviour.Properties.of().strength(2.5F).noOcclusion()
                    .sound(SoundType.WOOD));

    /** BETA26 basic essentia furnace, live fuel and distillation. */
    public static final DeferredBlock<SmelterBlock> SMELTER_BASIC = BLOCKS.registerBlock(
            "smelter_basic", SmelterBlock::new,
            BlockBehaviour.Properties.of().strength(2.5F,10.0F)
                    .sound(SoundType.METAL)
                    .lightLevel(state -> state.getValue(SmelterBlock.LIT)?13:0));

    /** Original BETA26 90% yield, 10-tick thaumium furnace. */
    public static final DeferredBlock<SmelterBlock> SMELTER_THAUMIUM = BLOCKS.registerBlock(
            "smelter_thaumium", SmelterBlock::new,
            BlockBehaviour.Properties.of().strength(2.5F,10.0F).sound(SoundType.METAL)
                    .lightLevel(state -> state.getValue(SmelterBlock.LIT)?13:0));

    /** Original BETA26 95% yield, 15-tick void furnace. */
    public static final DeferredBlock<SmelterBlock> SMELTER_VOID = BLOCKS.registerBlock(
            "smelter_void", SmelterBlock::new,
            BlockBehaviour.Properties.of().strength(2.5F,10.0F).sound(SoundType.METAL)
                    .lightLevel(state -> state.getValue(SmelterBlock.LIT)?13:0));

    /** BETA26 original tube subtypes using the same one-unit TileTube. */
    public static final DeferredBlock<EssentiaTubeBlock> TUBE_VALVE = BLOCKS.registerBlock(
            "tube_valve",EssentiaTubeBlock::new,
            BlockBehaviour.Properties.of().strength(0.5F,5.0F).noOcclusion().sound(SoundType.METAL));
    public static final DeferredBlock<EssentiaTubeBlock> TUBE_FILTER = BLOCKS.registerBlock(
            "tube_filter",EssentiaTubeBlock::new,
            BlockBehaviour.Properties.of().strength(0.5F,5.0F).noOcclusion().sound(SoundType.METAL));
    public static final DeferredBlock<EssentiaTubeBlock> TUBE_RESTRICT = BLOCKS.registerBlock(
            "tube_restrict",EssentiaTubeBlock::new,
            BlockBehaviour.Properties.of().strength(0.5F,5.0F).noOcclusion().sound(SoundType.METAL));
    public static final DeferredBlock<EssentiaTubeBlock> TUBE_ONEWAY = BLOCKS.registerBlock(
            "tube_oneway",EssentiaTubeBlock::new,
            BlockBehaviour.Properties.of().strength(0.5F,5.0F).noOcclusion().sound(SoundType.METAL));

    /** Original mixed-aspect 10-unit pipe buffer. */
    public static final DeferredBlock<EssentiaBufferBlock> TUBE_BUFFER = BLOCKS.registerBlock(
            "tube_buffer",EssentiaBufferBlock::new,
            BlockBehaviour.Properties.of().strength(0.5F,5.0F).noOcclusion()
                    .sound(SoundType.METAL));

    public static final DeferredBlock<BellowsBlock> BELLOWS = BLOCKS.registerBlock(
            "bellows",BellowsBlock::new,
            BlockBehaviour.Properties.of().strength(1.0F).noOcclusion().sound(SoundType.WOOD));
    public static final DeferredBlock<SmelterAttachmentBlock> SMELTER_AUX = BLOCKS.registerBlock(
            "smelter_aux",SmelterAttachmentBlock::new,
            BlockBehaviour.Properties.of().strength(1.0F,10.0F).noOcclusion().sound(SoundType.METAL));
    public static final DeferredBlock<SmelterAttachmentBlock> SMELTER_VENT = BLOCKS.registerBlock(
            "smelter_vent",SmelterAttachmentBlock::new,
            BlockBehaviour.Properties.of().strength(1.0F,10.0F).noOcclusion().sound(SoundType.METAL));

    // Actual BlockStoneTC materials (2.0 hardness, 10.0 resistance).
    // Pillars source drops two of these, rather than the pillar itself.
    public static final DeferredBlock<Block> STONE_ARCANE = BLOCKS.registerSimpleBlock(
            "stone_arcane", BlockBehaviour.Properties.of()
                    .strength(2.0F, 10.0F).sound(SoundType.STONE));
    public static final DeferredBlock<Block> STONE_ANCIENT = BLOCKS.registerSimpleBlock(
            "stone_ancient", BlockBehaviour.Properties.of()
                    .strength(2.0F, 10.0F).sound(SoundType.STONE));
    public static final DeferredBlock<Block> STONE_ELDRITCH_TILE = BLOCKS.registerSimpleBlock(
            "stone_eldritch_tile", BlockBehaviour.Properties.of()
                    .strength(2.0F, 10.0F).sound(SoundType.STONE));

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

    // Source BETA26 BlockCrystal: seven aspect-specific living world crystals.
    public static final DeferredBlock<CrystalClusterBlock> CRYSTAL_AER = BLOCKS.registerBlock(
            "crystal_aer", props -> new CrystalClusterBlock(props,"aer"),
            BlockBehaviour.Properties.of().strength(0.25F).randomTicks()
                    .noCollission().noOcclusion().lightLevel(state -> 1).sound(SoundType.GLASS));
    public static final DeferredBlock<CrystalClusterBlock> CRYSTAL_IGNIS = BLOCKS.registerBlock(
            "crystal_ignis", props -> new CrystalClusterBlock(props,"ignis"),
            BlockBehaviour.Properties.of().strength(0.25F).randomTicks()
                    .noCollission().noOcclusion().lightLevel(state -> 1).sound(SoundType.GLASS));
    public static final DeferredBlock<CrystalClusterBlock> CRYSTAL_AQUA = BLOCKS.registerBlock(
            "crystal_aqua", props -> new CrystalClusterBlock(props,"aqua"),
            BlockBehaviour.Properties.of().strength(0.25F).randomTicks()
                    .noCollission().noOcclusion().lightLevel(state -> 1).sound(SoundType.GLASS));
    public static final DeferredBlock<CrystalClusterBlock> CRYSTAL_TERRA = BLOCKS.registerBlock(
            "crystal_terra", props -> new CrystalClusterBlock(props,"terra"),
            BlockBehaviour.Properties.of().strength(0.25F).randomTicks()
                    .noCollission().noOcclusion().lightLevel(state -> 1).sound(SoundType.GLASS));
    public static final DeferredBlock<CrystalClusterBlock> CRYSTAL_ORDO = BLOCKS.registerBlock(
            "crystal_ordo", props -> new CrystalClusterBlock(props,"ordo"),
            BlockBehaviour.Properties.of().strength(0.25F).randomTicks()
                    .noCollission().noOcclusion().lightLevel(state -> 1).sound(SoundType.GLASS));
    public static final DeferredBlock<CrystalClusterBlock> CRYSTAL_PERDITIO = BLOCKS.registerBlock(
            "crystal_perditio", props -> new CrystalClusterBlock(props,"perditio"),
            BlockBehaviour.Properties.of().strength(0.25F).randomTicks()
                    .noCollission().noOcclusion().lightLevel(state -> 1).sound(SoundType.GLASS));
    public static final DeferredBlock<CrystalClusterBlock> CRYSTAL_VITIUM = BLOCKS.registerBlock(
            "crystal_vitium", props -> new CrystalClusterBlock(props,"vitium"),
            BlockBehaviour.Properties.of().strength(0.25F).randomTicks()
                    .noCollission().noOcclusion().lightLevel(state -> 1).sound(SoundType.GLASS));

    // BETA26 registers one separate BlockNitor for every EnumDyeColor.
    // The original names use silver/lightblue, not light_gray/light_blue.
    public static final Map<DyeColor, DeferredBlock<NitorBlock>> NITOR =
            registerAllNitorVariants();

    /** All 16 original candle variants, 14-light and +0.1 altar stabilization. */
    public static final Map<DyeColor, DeferredBlock<TallowCandleBlock>> CANDLES =
            registerAllCandleVariants();
    public static final DeferredBlock<TallowCandleBlock> CANDLE_WHITE = CANDLES.get(DyeColor.WHITE);
    private static Map<DyeColor, DeferredBlock<TallowCandleBlock>> registerAllCandleVariants() {
        Map<DyeColor, DeferredBlock<TallowCandleBlock>> map = new EnumMap<>(DyeColor.class);
        for (LegacyNitorColors.Variant variant : LegacyNitorColors.variants()) {
            DyeColor dye = DyeColor.valueOf(variant.modernDye().toUpperCase(Locale.ROOT));
            map.put(dye, BLOCKS.registerBlock("candle_" + variant.legacy(),
                    props -> new TallowCandleBlock(props, variant.particleRgb()),
                    BlockBehaviour.Properties.of().strength(0.1F)
                            .lightLevel(state -> 14).mapColor(dye.getMapColor())
                            .noCollission().noOcclusion().sound(SoundType.WOOL)));
        }
        if (map.size() != 16) throw new IllegalStateException("Missing candle");
        return Collections.unmodifiableMap(map);
    }

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
                            .mapColor(dye.getMapColor())
                            .noCollission().noOcclusion().sound(SoundType.WOOL));
            registered.put(dye, entry);
        }
        if (registered.size() != 16) throw new IllegalStateException("Missing original Nitor color");
        return Collections.unmodifiableMap(registered);
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
    }
}
