package dev.ehtid.thaumcraft.infusion;

import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.block.InfusionGlyphBlock;
import dev.ehtid.thaumcraft.block.InfusionInlayBlock;
import dev.ehtid.thaumcraft.block.InfusionStabilizerBlock;
import dev.ehtid.thaumcraft.blockentity.InfusionStabilizerBlockEntity;
import dev.ehtid.thaumcraft.port.core.LegacyInfusionStability;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Isolated original infusion stability content; call register once from mod bootstrap. */
public final class InfusionStabilityBlocks {
    private InfusionStabilityBlocks() {}

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ThaumcraftPort.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ThaumcraftPort.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ThaumcraftPort.MOD_ID);

    public static final DeferredBlock<InfusionGlyphBlock> MATRIX_SPEED = BLOCKS.registerBlock("matrix_speed",
            properties -> new InfusionGlyphBlock(properties, LegacyInfusionStability.Glyph.SPEED),
            BlockBehaviour.Properties.of().strength(2.5F).sound(SoundType.STONE));
    public static final DeferredBlock<InfusionGlyphBlock> MATRIX_COST = BLOCKS.registerBlock("matrix_cost",
            properties -> new InfusionGlyphBlock(properties, LegacyInfusionStability.Glyph.COST),
            BlockBehaviour.Properties.of().strength(2.5F).sound(SoundType.STONE));
    public static final DeferredBlock<InfusionInlayBlock> INLAY = BLOCKS.registerBlock("inlay",
            InfusionInlayBlock::new,
            BlockBehaviour.Properties.of().strength(.5F).noCollission().noOcclusion().sound(SoundType.METAL));
    public static final DeferredBlock<InfusionStabilizerBlock> STABILIZER = BLOCKS.registerBlock("stabilizer",
            InfusionStabilizerBlock::new,
            BlockBehaviour.Properties.of().strength(2.5F, 20).noOcclusion().sound(SoundType.STONE));

    public static final DeferredItem<BlockItem> MATRIX_SPEED_ITEM = ITEMS.registerSimpleBlockItem(MATRIX_SPEED, new Item.Properties());
    public static final DeferredItem<BlockItem> MATRIX_COST_ITEM = ITEMS.registerSimpleBlockItem(MATRIX_COST, new Item.Properties());
    public static final DeferredItem<BlockItem> INLAY_ITEM = ITEMS.registerSimpleBlockItem(INLAY, new Item.Properties());
    public static final DeferredItem<BlockItem> STABILIZER_ITEM = ITEMS.registerSimpleBlockItem(STABILIZER, new Item.Properties());
    /** Original plain crafting ingredient, consumed by the functional essentia mirror recipe. */
    public static final DeferredItem<Item> MIRRORED_GLASS = ITEMS.registerSimpleItem("mirrored_glass", new Item.Properties());

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<InfusionStabilizerBlockEntity>>
            STABILIZER_ENTITY = ENTITIES.register("stabilizer", () -> BlockEntityType.Builder.of(
                    InfusionStabilizerBlockEntity::new, STABILIZER.get()).build(null));

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        ENTITIES.register(modBus);
        modBus.addListener(InfusionStabilityBlocks::creativeContents);
    }

    private static void creativeContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().location().toString().equals("thaumcraft:thaumcraft")) {
            event.accept(MATRIX_SPEED_ITEM.get());
            event.accept(MATRIX_COST_ITEM.get());
            event.accept(INLAY_ITEM.get());
            event.accept(STABILIZER_ITEM.get());
            event.accept(MIRRORED_GLASS.get());
        }
    }
}
