package dev.ehtid.thaumcraft.golem;

import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import dev.ehtid.thaumcraft.item.GolemPlacerItem;
import dev.ehtid.thaumcraft.item.GolemSealItem;
import dev.ehtid.thaumcraft.port.core.golem.GolemDesign;
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

    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, ThaumcraftPort.MOD_ID);
    public static final DeferredHolder<MenuType<?>, MenuType<GolemSealMenu>> SEAL_MENU = MENUS.register(
            "golem_seal", () -> new MenuType<>(GolemSealMenu::new, FeatureFlags.VANILLA_SET));

    public static final DeferredHolder<EntityType<?>, EntityType<ThaumcraftGolem>> GOLEM = ENTITIES.register(
            "golem", () -> EntityType.Builder.of(ThaumcraftGolem::new, MobCategory.CREATURE)
                    .sized(0.4F, 0.9F).eyeHeight(0.7F).clientTrackingRange(8).build("thaumcraft:golem"));
    public static final DeferredItem<GolemPlacerItem> GOLEM_PLACER = ITEMS.registerItem(
            "golem", GolemPlacerItem::new, new Item.Properties());
    public static final DeferredItem<GolemSealItem> PICKUP_SEAL = ITEMS.registerItem(
            "seal_pickup", GolemSealItem::new, new Item.Properties());
    public static final DeferredItem<GolemSealItem> FILL_SEAL = ITEMS.registerItem(
            "seal_fill", p -> new GolemSealItem(p, dev.ehtid.thaumcraft.golem.seal.GolemSeals.Type.FILL), new Item.Properties());

    public static void register(IEventBus bus) {
        ENTITIES.register(bus);
        ITEMS.register(bus);
        MENUS.register(bus);
        bus.addListener(GolemModule::attributes);
        bus.addListener(GolemModule::creativeContents);
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(GOLEM.get(), ThaumcraftGolem.attributes().build());
    }

    private static void creativeContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTab() == ThaumcraftCreativeTabs.MAIN.get()) {
            event.accept(GolemPlacerItem.stack(GolemDesign.defaultWood(), 0));
            event.accept(PICKUP_SEAL.get());
            event.accept(FILL_SEAL.get());
        }
    }

    /** Avoid advertising unimplemented flight/combat/body functions through arbitrary props. */
    public static boolean supportsRuntime(GolemDesign design) {
        return design.toLong() == GolemDesign.defaultWood().toLong();
    }
}
