package dev.ehtid.thaumcraft.client;

import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.item.JarLabelItem;
import dev.ehtid.thaumcraft.port.core.LegacyLabelTint;
import dev.ehtid.thaumcraft.registry.ThaumcraftItems;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/**
 * Original BETA26 ItemLabel: preserve paper on layer0 and dye only the
 * filled label's layer1 overlay with its NBT-selected aspect color.
 * Independent client subscriber avoids modifying shared client registries.
 */
@EventBusSubscriber(modid = ThaumcraftPort.MOD_ID, bus = EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT)
public final class JarLabelColorEvents {
    private JarLabelColorEvents() {}

    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) ->
                        LegacyLabelTint.colorFor(JarLabelItem.selectedAspect(stack), tintIndex),
                ThaumcraftItems.JAR_LABEL.get());
    }
}
