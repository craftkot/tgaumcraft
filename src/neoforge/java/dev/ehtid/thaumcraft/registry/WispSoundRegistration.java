package dev.ehtid.thaumcraft.registry;

import dev.ehtid.thaumcraft.ThaumcraftPort;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * Isolated BETA26 Wisp sound registration.
 *
 * Kept separate from the shared ThaumcraftSounds registry so the Wisp lane
 * does not expand the common sound registry while other feature branches run.
 */
@EventBusSubscriber(modid = ThaumcraftPort.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class WispSoundRegistration {
    private static final ResourceLocation WISP_LIVE_ID =
            ResourceLocation.fromNamespaceAndPath(ThaumcraftPort.MOD_ID, "wisplive");
    private static final ResourceLocation WISP_DEAD_ID =
            ResourceLocation.fromNamespaceAndPath(ThaumcraftPort.MOD_ID, "wispdead");
    private static final ResourceLocation WISP_ZAP_ID =
            ResourceLocation.fromNamespaceAndPath(ThaumcraftPort.MOD_ID, "zap");

    public static final SoundEvent WISP_LIVE = SoundEvent.createVariableRangeEvent(WISP_LIVE_ID);
    public static final SoundEvent WISP_DEAD = SoundEvent.createVariableRangeEvent(WISP_DEAD_ID);
    public static final SoundEvent WISP_ZAP = SoundEvent.createVariableRangeEvent(WISP_ZAP_ID);

    private WispSoundRegistration() {}

    @SubscribeEvent
    public static void registerSounds(RegisterEvent event) {
        event.register(Registries.SOUND_EVENT, registry -> {
            registry.register(WISP_LIVE_ID, WISP_LIVE);
            registry.register(WISP_DEAD_ID, WISP_DEAD);
            registry.register(WISP_ZAP_ID, WISP_ZAP);
        });
    }
}
