package dev.ehtid.thaumcraft.registry;

import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.menu.ArcaneWorkbenchMenu;
import dev.ehtid.thaumcraft.menu.ResearchTableMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** FIRSTSTEPS server/client workbench menu registration. */
public final class ThaumcraftMenus {
    private ThaumcraftMenus() {}

    public static final DeferredRegister<MenuType<?>> TYPES =
            DeferredRegister.create(Registries.MENU, ThaumcraftPort.MOD_ID);
    public static final DeferredHolder<MenuType<?>, MenuType<ArcaneWorkbenchMenu>> ARCANE_WORKBENCH =
            TYPES.register("arcane_workbench",
                    () -> new MenuType<>(ArcaneWorkbenchMenu::new, FeatureFlags.VANILLA_SET));

    public static final DeferredHolder<MenuType<?>, MenuType<ResearchTableMenu>> RESEARCH_TABLE =
            TYPES.register("research_table",
                    () -> new MenuType<>(ResearchTableMenu::new, FeatureFlags.VANILLA_SET));

    public static void register(IEventBus bus) {
        TYPES.register(bus);
    }
}
