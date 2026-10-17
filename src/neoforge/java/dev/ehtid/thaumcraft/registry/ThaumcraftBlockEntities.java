package dev.ehtid.thaumcraft.registry;

import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.blockentity.ArcaneWorkbenchBlockEntity;
import dev.ehtid.thaumcraft.blockentity.ResearchTableBlockEntity;
import dev.ehtid.thaumcraft.blockentity.CrucibleBlockEntity;
import dev.ehtid.thaumcraft.blockentity.InfusionMatrixBlockEntity;
import dev.ehtid.thaumcraft.blockentity.PedestalBlockEntity;
import dev.ehtid.thaumcraft.blockentity.EssentiaJarBlockEntity;
import dev.ehtid.thaumcraft.blockentity.EssentiaMirrorBlockEntity;
import dev.ehtid.thaumcraft.blockentity.EssentiaTubeBlockEntity;
import dev.ehtid.thaumcraft.blockentity.EssentiaBufferBlockEntity;
import dev.ehtid.thaumcraft.blockentity.AlembicBlockEntity;
import dev.ehtid.thaumcraft.blockentity.SmelterBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Persistent server-side block entities for the FIRSTSTEPS slice. */
public final class ThaumcraftBlockEntities {
    private ThaumcraftBlockEntities() {}

    public static final DeferredRegister<BlockEntityType<?>> TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ThaumcraftPort.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ArcaneWorkbenchBlockEntity>>
            ARCANE_WORKBENCH = TYPES.register("arcane_workbench",
            () -> BlockEntityType.Builder.of(ArcaneWorkbenchBlockEntity::new,
                    ThaumcraftBlocks.ARCANE_WORKBENCH.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ResearchTableBlockEntity>>
            RESEARCH_TABLE = TYPES.register("research_table",
            () -> BlockEntityType.Builder.of(ResearchTableBlockEntity::new,
                    ThaumcraftBlocks.RESEARCH_TABLE.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CrucibleBlockEntity>>
            CRUCIBLE = TYPES.register("crucible",
            () -> BlockEntityType.Builder.of(CrucibleBlockEntity::new,
                    ThaumcraftBlocks.CRUCIBLE.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PedestalBlockEntity>>
            PEDESTAL = TYPES.register("pedestal",
            () -> BlockEntityType.Builder.of(PedestalBlockEntity::new,
                    ThaumcraftBlocks.PEDESTAL_ARCANE.get(),
                    ThaumcraftBlocks.PEDESTAL_ANCIENT.get(),
                    ThaumcraftBlocks.PEDESTAL_ELDRITCH.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<InfusionMatrixBlockEntity>>
            INFUSION_MATRIX = TYPES.register("infusion_matrix",
            () -> BlockEntityType.Builder.of(InfusionMatrixBlockEntity::new,
                    ThaumcraftBlocks.INFUSION_MATRIX.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EssentiaMirrorBlockEntity>>
            MIRROR_ESSENTIA = TYPES.register("mirror_essentia",
            () -> BlockEntityType.Builder.of(EssentiaMirrorBlockEntity::new,
                    ThaumcraftBlocks.MIRROR_ESSENTIA.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EssentiaJarBlockEntity>>
            JAR_NORMAL = TYPES.register("jar_normal",
            () -> BlockEntityType.Builder.of(EssentiaJarBlockEntity::new,
                    ThaumcraftBlocks.JAR_NORMAL.get(),
                    ThaumcraftBlocks.JAR_VOID.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EssentiaTubeBlockEntity>>
            TUBE = TYPES.register("tube",
            () -> BlockEntityType.Builder.of(EssentiaTubeBlockEntity::new,
                    ThaumcraftBlocks.TUBE.get(),
                    ThaumcraftBlocks.TUBE_VALVE.get(),
                    ThaumcraftBlocks.TUBE_FILTER.get(),
                    ThaumcraftBlocks.TUBE_RESTRICT.get(),
                    ThaumcraftBlocks.TUBE_ONEWAY.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AlembicBlockEntity>>
            ALEMBIC = TYPES.register("alembic",
            () -> BlockEntityType.Builder.of(AlembicBlockEntity::new,
                    ThaumcraftBlocks.ALEMBIC.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SmelterBlockEntity>>
            SMELTER_BASIC = TYPES.register("smelter_basic",
            () -> BlockEntityType.Builder.of(SmelterBlockEntity::new,
                    ThaumcraftBlocks.SMELTER_BASIC.get(),
                    ThaumcraftBlocks.SMELTER_THAUMIUM.get(),
                    ThaumcraftBlocks.SMELTER_VOID.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<EssentiaBufferBlockEntity>>
            TUBE_BUFFER = TYPES.register("tube_buffer",
            () -> BlockEntityType.Builder.of(EssentiaBufferBlockEntity::new,
                    ThaumcraftBlocks.TUBE_BUFFER.get()).build(null));

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }
}
