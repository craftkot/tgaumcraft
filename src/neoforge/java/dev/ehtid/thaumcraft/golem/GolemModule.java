package dev.ehtid.thaumcraft.golem;

import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import dev.ehtid.thaumcraft.item.GolemPlacerItem;
import dev.ehtid.thaumcraft.item.GolemSealItem;
import dev.ehtid.thaumcraft.item.GolemBellItem;
import dev.ehtid.thaumcraft.port.core.golem.GolemDesign;
import dev.ehtid.thaumcraft.golem.press.GolemPressBlock;
import dev.ehtid.thaumcraft.golem.press.GolemPressBlockEntity;
import dev.ehtid.thaumcraft.golem.press.GolemPressMenu;
import dev.ehtid.thaumcraft.golem.press.GolemStoneTableBlock;
import dev.ehtid.thaumcraft.golem.press.GolemPressPlaceholderBlock;
import dev.ehtid.thaumcraft.port.core.golem.GolemPressBlueprint.Part;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.item.BlockItem;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.wrapper.SidedInvWrapper;
import net.minecraft.core.Direction;
import dev.ehtid.thaumcraft.registry.ThaumcraftCreativeTabs;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.flag.FeatureFlags;
import dev.ehtid.thaumcraft.golem.menu.GolemSealMenu;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Lane-local registrations: the shared mod entry point needs only register(bus). */
public final class GolemModule {
    private GolemModule() {}

    private static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, ThaumcraftPort.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ThaumcraftPort.MOD_ID);
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, ThaumcraftPort.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ThaumcraftPort.MOD_ID);
    public static final DeferredHolder<Block, GolemPressBlock> PRESS = BLOCKS.register("golem_builder",
            () -> new GolemPressBlock(BlockBehaviour.Properties.of().strength(3.5F)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GolemPressBlockEntity>> PRESS_ENTITY = BLOCK_ENTITIES.register(
            "golem_builder", () -> BlockEntityType.Builder.of(GolemPressBlockEntity::new, PRESS.get()).build(null));
    public static final DeferredItem<BlockItem> PRESS_ITEM = ITEMS.registerSimpleBlockItem(PRESS, new Item.Properties());
    public static final DeferredHolder<Block, GolemStoneTableBlock> STONE_TABLE = BLOCKS.register("table_stone",
            () -> new GolemStoneTableBlock(BlockBehaviour.Properties.of().strength(2.5F, 1.2F).noOcclusion()
                    .isRedstoneConductor((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false).isViewBlocking((state, level, pos) -> false)));
    public static final DeferredItem<BlockItem> STONE_TABLE_ITEM = ITEMS.registerSimpleBlockItem(STONE_TABLE, new Item.Properties());
    private static DeferredHolder<Block, GolemPressPlaceholderBlock> placeholder(String name, Part part) {
        return BLOCKS.register(name, () -> new GolemPressPlaceholderBlock(BlockBehaviour.Properties.of().strength(2.5F, 1.2F)
                .noOcclusion().pushReaction(PushReaction.BLOCK).lightLevel(state -> part == Part.CAULDRON ? 13 : 0), part));
    }
    public static final DeferredHolder<Block, GolemPressPlaceholderBlock> PLACEHOLDER_BARS = placeholder("placeholder_bars", Part.BARS);
    public static final DeferredHolder<Block, GolemPressPlaceholderBlock> PLACEHOLDER_ANVIL = placeholder("placeholder_anvil", Part.ANVIL);
    public static final DeferredHolder<Block, GolemPressPlaceholderBlock> PLACEHOLDER_CAULDRON = placeholder("placeholder_cauldron", Part.CAULDRON);
    public static final DeferredHolder<Block, GolemPressPlaceholderBlock> PLACEHOLDER_TABLE = placeholder("placeholder_table", Part.TABLE);
    public static final DeferredItem<Item> SIMPLE_MECHANISM = ITEMS.registerSimpleItem("mechanism_simple");
    /** Basic mind@0 is a distinct modern item; advanced metadata variants are not exposed. */
    public static final DeferredItem<Item> CLOCKWORK_MIND = ITEMS.registerSimpleItem("mind");
    /** Source mind@1 and modules@0/1. Their infusion acquisition belongs to the M06 integration. */
    public static final DeferredItem<Item> BIOTHAUMIC_MIND=ITEMS.registerSimpleItem("mind_biothaumic");
    public static final DeferredItem<Item> VISION_MODULE=ITEMS.registerSimpleItem("module_vision");
    public static final DeferredItem<Item> AGGRESSION_MODULE=ITEMS.registerSimpleItem("module_aggression");

    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, ThaumcraftPort.MOD_ID);
    public static final DeferredHolder<MenuType<?>, MenuType<GolemSealMenu>> SEAL_MENU = MENUS.register(
            "golem_seal", () -> new MenuType<>(GolemSealMenu::new, FeatureFlags.VANILLA_SET));
    public static final DeferredHolder<MenuType<?>, MenuType<GolemPressMenu>> PRESS_MENU = MENUS.register(
            "golem_press", () -> new MenuType<>(GolemPressMenu::new, FeatureFlags.VANILLA_SET));
    public static final DeferredHolder<MenuType<?>,MenuType<dev.ehtid.thaumcraft.golem.menu.GolemLogisticsMenu>> LOGISTICS_MENU=MENUS.register(
            "golem_logistics",()->new MenuType<>(dev.ehtid.thaumcraft.golem.menu.GolemLogisticsMenu::new,FeatureFlags.VANILLA_SET));

    public static final DeferredHolder<EntityType<?>, EntityType<ThaumcraftGolem>> GOLEM = ENTITIES.register(
            "golem", () -> EntityType.Builder.of(ThaumcraftGolem::new, MobCategory.CREATURE)
                    .sized(0.4F, 0.9F).eyeHeight(0.7F).clientTrackingRange(8).build("thaumcraft:golem"));
    public static final DeferredItem<GolemPlacerItem> GOLEM_PLACER = ITEMS.registerItem(
            "golem", GolemPlacerItem::new, new Item.Properties());
    public static final DeferredItem<GolemBellItem> GOLEM_BELL = ITEMS.registerItem("golem_bell", GolemBellItem::new, new Item.Properties().stacksTo(1));
    public static final DeferredItem<Item> BLANK_SEAL = ITEMS.registerSimpleItem("seal_blank");
    public static final DeferredItem<GolemSealItem> PICKUP_SEAL = ITEMS.registerItem(
            "seal_pickup", GolemSealItem::new, new Item.Properties());
    public static final DeferredItem<GolemSealItem> FILL_SEAL = ITEMS.registerItem(
            "seal_fill", p -> new GolemSealItem(p, dev.ehtid.thaumcraft.golem.seal.GolemSeals.Type.FILL), new Item.Properties());
    public static final DeferredItem<GolemSealItem> EMPTY_SEAL = ITEMS.registerItem(
            "seal_empty", p -> new GolemSealItem(p, dev.ehtid.thaumcraft.golem.seal.GolemSeals.Type.EMPTY), new Item.Properties());
    public static final DeferredItem<GolemSealItem> EMPTY_ADVANCED_SEAL=ITEMS.registerItem("seal_empty_advanced",
            p->new GolemSealItem(p,dev.ehtid.thaumcraft.golem.seal.GolemSeals.Type.EMPTY_ADVANCED),new Item.Properties());
    public static final DeferredItem<GolemSealItem> PROVIDE_SEAL=ITEMS.registerItem("seal_provider",
            p->new GolemSealItem(p,dev.ehtid.thaumcraft.golem.seal.GolemSeals.Type.PROVIDE),new Item.Properties());
    public static final DeferredItem<GolemSealItem> STOCK_SEAL=ITEMS.registerItem("seal_stock",
            p->new GolemSealItem(p,dev.ehtid.thaumcraft.golem.seal.GolemSeals.Type.STOCK),new Item.Properties());

    public static Item sealItem(dev.ehtid.thaumcraft.golem.seal.GolemSeals.Type type) {
        return switch(type) { case PICKUP->PICKUP_SEAL.get(); case FILL->FILL_SEAL.get(); case EMPTY->EMPTY_SEAL.get();
            case EMPTY_ADVANCED->EMPTY_ADVANCED_SEAL.get(); case PROVIDE->PROVIDE_SEAL.get(); case STOCK->STOCK_SEAL.get(); };
    }

    public static void register(IEventBus bus) {
        ENTITIES.register(bus);
        ITEMS.register(bus);
        MENUS.register(bus);
        BLOCKS.register(bus);
        BLOCK_ENTITIES.register(bus);
        bus.addListener(GolemModule::capabilities);
        bus.addListener(GolemModule::attributes);
        bus.addListener(GolemModule::creativeContents);
    }

    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, PRESS_ENTITY.get(),
                (press, face) -> new SidedInvWrapper(press, face == null ? Direction.DOWN : face));
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(GOLEM.get(), ThaumcraftGolem.attributes().build());
    }

    private static void creativeContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTab() == ThaumcraftCreativeTabs.MAIN.get()) {
            event.accept(GolemPlacerItem.stack(GolemDesign.defaultWood(), 0));
            event.accept(GOLEM_BELL.get());
            event.accept(BLANK_SEAL.get());
            event.accept(PICKUP_SEAL.get());
            event.accept(FILL_SEAL.get());
            event.accept(EMPTY_SEAL.get());
            event.accept(EMPTY_ADVANCED_SEAL.get()); event.accept(PROVIDE_SEAL.get()); event.accept(STOCK_SEAL.get());
            event.accept(PRESS_ITEM.get());
            event.accept(STONE_TABLE_ITEM.get());
            event.accept(SIMPLE_MECHANISM.get());
            event.accept(CLOCKWORK_MIND.get());
            event.accept(BIOTHAUMIC_MIND.get()); event.accept(VISION_MODULE.get()); event.accept(AGGRESSION_MODULE.get());
        }
    }

    /** Avoid advertising unimplemented flight/combat/body functions through arbitrary props. */
    public static boolean supportsRuntime(GolemDesign design) {
        return dev.ehtid.thaumcraft.port.core.golem.GolemRuntimeRules.groundWorker(design);
    }
}
