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

    // BETA26 SoundsTC.page, used on every Thaumonomicon right-click.
    public static final Supplier<SoundEvent> PAGE = SOUND_EVENTS.register("page",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(ThaumcraftPort.MOD_ID, "page")));

    // BETA26 SoundsTC.learn: Curio right-click knowledge/warp reveal.
    public static final Supplier<SoundEvent> LEARN = SOUND_EVENTS.register("learn",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(ThaumcraftPort.MOD_ID, "learn")));

    // BETA26 SoundsTC.CRYSTAL: one original event for all five block sounds.
    public static final Supplier<SoundEvent> CRYSTAL = SOUND_EVENTS.register("crystal",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(ThaumcraftPort.MOD_ID, "crystal")));

    public static void register(IEventBus modBus) {
        SOUND_EVENTS.register(modBus);
    }
}
