package dev.ehtid.thaumcraft.registry;

import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.blockentity.ArcaneWorkbenchBlockEntity;
import dev.ehtid.thaumcraft.blockentity.ResearchTableBlockEntity;
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

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }
}
