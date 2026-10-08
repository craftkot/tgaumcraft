package dev.ehtid.thaumcraft.registry;

import dev.ehtid.thaumcraft.ThaumcraftPort;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/** BETA26 sound surface currently required by the FIRSTSTEPS path. */
public final class ThaumcraftSounds {
    private ThaumcraftSounds() {}

    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(BuiltInRegistries.SOUND_EVENT, ThaumcraftPort.MOD_ID);

    public static final Supplier<SoundEvent> SCAN = SOUND_EVENTS.register("scan",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(ThaumcraftPort.MOD_ID, "scan")));

    public static void register(IEventBus modBus) {
        SOUND_EVENTS.register(modBus);
    }
}
