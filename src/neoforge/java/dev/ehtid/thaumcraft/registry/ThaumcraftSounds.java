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

    // BETA26 SoundsTC.JAR: four original random clips shared by all five BlockJar sounds.
    public static final Supplier<SoundEvent> JAR = SOUND_EVENTS.register("jar",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(ThaumcraftPort.MOD_ID, "jar")));

    // BETA26 SoundsTC.key: client feedback when a jar brace is applied.
    public static final Supplier<SoundEvent> KEY = SOUND_EVENTS.register("key",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(ThaumcraftPort.MOD_ID, "key")));

    // BETA26 ItemMagicDust#doSparkles, valid Salis Mundus use only.
    public static final Supplier<SoundEvent> DUST = SOUND_EVENTS.register("dust",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(ThaumcraftPort.MOD_ID, "dust")));

    public static void register(IEventBus modBus) {
        SOUND_EVENTS.register(modBus);
    }
}
