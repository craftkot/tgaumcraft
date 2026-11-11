package dev.ehtid.thaumcraft.world;

import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.port.core.AuraGenerationLogic;
import dev.ehtid.thaumcraft.port.core.LegacyModernBiomeCompatibility;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.common.IOUtilities;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Exercises the production aura population helper against five real
 * ServerLevel biome samples and the production SavedData serializer/loader.
 * The test fixture explicitly preloads its five sample chunks; the production
 * helper is separately checked against an unloaded distant target.
 */
@GameTestHolder(ThaumcraftPort.MOD_ID)
public final class AuraGenerationGameTests {
    private AuraGenerationGameTests() {}

    @SuppressWarnings("deprecation")
    @PrefixGameTestTemplate(false)
    @GameTest(
            template = "crystal_spread_empty",
            templateNamespace = ThaumcraftPort.MOD_ID,
            timeoutTicks = 100
    )
    public static void samplesFiveBETA26BiomesAtY50AndPersistsAura(GameTestHelper helper) {
        if (isColdRestartReadPhase()) {
            System.out.println("TC_GAMETEST_AURA_GENERATION_SKIPPED_PHASE=read");
            helper.succeed();
            return;
        }
        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(new BlockPos(8, 2, 8));
        int chunkX = anchor.getX() >> 4;
        int chunkZ = anchor.getZ() >> 4;
        BlockPos center = sampleCenter(chunkX, chunkZ);
        BlockPos north = center.north(16);
        BlockPos south = center.south(16);
        BlockPos west = center.west(16);
        BlockPos east = center.east(16);

        var biomes = level.registryAccess().lookupOrThrow(Registries.BIOME);
        Holder<Biome> plains = biomes.getOrThrow(Biomes.PLAINS);
        Holder<Biome> desert = biomes.getOrThrow(Biomes.DESERT);
        Holder<Biome> forest = biomes.getOrThrow(Biomes.FOREST);
        Holder<Biome> ocean = biomes.getOrThrow(Biomes.OCEAN);
        Holder<Biome> swamp = biomes.getOrThrow(Biomes.SWAMP);

        // The fixture explicitly loads the five chunks required by the
        // production precondition. The production helper itself uses only
        // hasChunk and must not load chunks while checking that precondition.
        setBiomeLayers(level, center, plains, desert);
        setBiomeLayers(level, north, desert, desert);
        setBiomeLayers(level, south, forest, desert);
        setBiomeLayers(level, west, ocean, desert);
        setBiomeLayers(level, east, swamp, desert);

        assertLoaded(level, center);
        assertLoaded(level, north);
        assertLoaded(level, south);
        assertLoaded(level, west);
        assertLoaded(level, east);
        assertBiome(level, center, "minecraft:plains");
        assertBiome(level, north, "minecraft:desert");
        assertBiome(level, south, "minecraft:forest");
        assertBiome(level, west, "minecraft:ocean");
        assertBiome(level, east, "minecraft:swamp");

        int seaLevel = level.getSeaLevel();
        if (seaLevel < 56 || seaLevel > 83 || seaLevel == AuraGenerationLogic.SOURCE_BIOME_SAMPLE_Y) {
            throw new AssertionError("Test requires a distinct sea-level sample, got " + seaLevel);
        }
        assertBiome(level, center.atY(seaLevel), "minecraft:desert");

        System.out.println("TC_GAMETEST_AURA_SAMPLE=center biome=minecraft:plains y=50");
        System.out.println("TC_GAMETEST_AURA_SAMPLE=north biome=minecraft:desert y=50");
        System.out.println("TC_GAMETEST_AURA_SAMPLE=south biome=minecraft:forest y=50");
        System.out.println("TC_GAMETEST_AURA_SAMPLE=west biome=minecraft:ocean y=50");
        System.out.println("TC_GAMETEST_AURA_SAMPLE=east biome=minecraft:swamp y=50");
        System.out.println("TC_GAMETEST_AURA_SEALEVEL=center biome=minecraft:desert y=" + seaLevel);

        int expectedBase = AuraGenerationLogic.generatedBase(
                LegacyModernBiomeCompatibility.auraModifier("minecraft:plains"),
                LegacyModernBiomeCompatibility.auraModifier("minecraft:desert"),
                LegacyModernBiomeCompatibility.auraModifier("minecraft:forest"),
                LegacyModernBiomeCompatibility.auraModifier("minecraft:ocean"),
                LegacyModernBiomeCompatibility.auraModifier("minecraft:swamp"),
                0.0D
        );
        if (expectedBase != 181) {
            throw new AssertionError("BETA26 biome fixture changed unexpectedly: " + expectedBase);
        }

        ThaumcraftAuraSavedData aura = ThaumcraftAuraSavedData.get(level);
        if (aura.hasChunk(center)) {
            throw new AssertionError("Aura fixture chunk was already initialized before the test");
        }
        boolean initialized = ThaumcraftAuraWorldEvents.initializeChunkIfReady(
                level, aura, chunkX, chunkZ, () -> 0.0D);
        if (!initialized
                || aura.getBase(center) != expectedBase
                || aura.getVis(center) != expectedBase
                || aura.getFlux(center) != 0.0F) {
            throw new AssertionError("Five-biome aura initialization mismatch: base="
                    + aura.getBase(center) + " vis=" + aura.getVis(center)
                    + " flux=" + aura.getFlux(center));
        }
        System.out.println("TC_GAMETEST_AURA_GENERATION base=181 vis=181.0 flux=0.0");

        aura.setVis(center, 73.0F);
        aura.polluteFlux(center, 4.5F);
        AtomicInteger reentryRandomCalls = new AtomicInteger();
        boolean reinitialized = ThaumcraftAuraWorldEvents.initializeChunkIfReady(
                level, aura, chunkX, chunkZ, () -> {
                    reentryRandomCalls.incrementAndGet();
                    return -0.75D;
                });
        if (reinitialized || reentryRandomCalls.get() != 0
                || aura.getBase(center) != 181
                || aura.getVis(center) != 73.0F
                || aura.getFlux(center) != 4.5F) {
            throw new AssertionError("Existing aura was re-seeded or consumed RNG on re-entry");
        }
        System.out.println("TC_GAMETEST_AURA_REENTRY_PRESERVED=YES");
        assertDrainVisAndFluxSemantics(aura, center.offset(32, 0, 0));
        assertFluxPollutionBounds(aura, center.offset(48, 0, 0));

        CompoundTag serialized = aura.save(new CompoundTag(), level.registryAccess());
        ThaumcraftAuraSavedData restored = ThaumcraftAuraSavedData.load(serialized, level.registryAccess());
        if (!restored.hasChunk(center)
                || restored.getBase(center) != 181
                || restored.getVis(center) != 73.0F
                || restored.getFlux(center) != 4.5F) {
            throw new AssertionError("Aura SavedData NBT round-trip lost base/vis/flux");
        }
        System.out.println("TC_GAMETEST_AURA_SAVE_LOAD base=181 vis=73.0 flux=4.5");

        // Flush the real GameTest world's DimensionDataStorage, then discard
        // the in-memory object boundary by opening a second storage instance
        // against the same on-disk data directory.
        if (!level.dimension().equals(Level.OVERWORLD)) {
            throw new AssertionError("Disk persistence fixture requires the Overworld data directory");
        }
        var server = level.getServer();
        Path dataDirectory = server.getWorldPath(LevelResource.ROOT).resolve("data");
        Path auraFile = dataDirectory.resolve("thaumcraft_aura.dat");
        boolean dirtyBeforeSave = aura.isDirty();
        if (!dirtyBeforeSave || ThaumcraftAuraSavedData.get(level) != aura) {
            throw new AssertionError("Aura SavedData is not dirty or is not attached to this level's DimensionDataStorage"
                    + " dirty=" + dirtyBeforeSave);
        }
        System.out.println("TC_GAMETEST_AURA_DISK_DIAGNOSTIC root=" + server.getWorldPath(LevelResource.ROOT)
                + " data=" + dataDirectory + " isServerOverworld=" + (level == server.overworld())
                + " dirtyBeforeSave=" + dirtyBeforeSave);
        level.getDataStorage().save();
        // NeoForge 21.1.1 queues SavedData writes on IOUtilities' worker and clears dirty immediately.
        // Join that queue before asserting that the atomic temp-file rename reached disk.
        IOUtilities.waitUntilIOWorkerComplete();
        System.out.println("TC_GAMETEST_AURA_DISK_IO_FLUSH=COMPLETE");
        System.out.println("TC_GAMETEST_AURA_DISK_DIAGNOSTIC dirtyAfterSave=" + aura.isDirty());
        System.out.println("TC_GAMETEST_AURA_DISK_DIAGNOSTIC rootExists="
                + Files.isDirectory(server.getWorldPath(LevelResource.ROOT))
                + " dataDirectoryExists=" + Files.isDirectory(dataDirectory)
                + " auraFileExists=" + Files.isRegularFile(auraFile));
        if (Files.isDirectory(dataDirectory)) {
            try (var paths = Files.list(dataDirectory)) {
                paths.limit(50).forEach(path -> System.out.println("TC_GAMETEST_AURA_DISK_PATH=" + path));
            } catch (IOException exception) {
                throw new AssertionError("Unable to inspect the bounded aura data directory", exception);
            }
        }
        try {
            if (!Files.isRegularFile(auraFile) || Files.size(auraFile) <= 0L) {
                throw new AssertionError("DimensionDataStorage.save() did not create a non-empty thaumcraft_aura.dat"
                        + " at " + auraFile);
            }
            try (var input = Files.newInputStream(auraFile)) {
                if (input.read() != 0x1f || input.read() != 0x8b) {
                    throw new AssertionError("thaumcraft_aura.dat is not a gzip-compressed NBT file");
                }
            }
        } catch (IOException exception) {
            throw new AssertionError("Unable to inspect the physical aura SavedData file", exception);
        }
        System.out.println("TC_GAMETEST_AURA_DISK_SAVE file=thaumcraft_aura.dat bytes=" + safeFileSize(auraFile));

        DimensionDataStorage reopenedStorage = new DimensionDataStorage(
                dataDirectory.toFile(), server.getFixerUpper(), level.registryAccess());
        SavedData.Factory<ThaumcraftAuraSavedData> factory =
                new SavedData.Factory<>(ThaumcraftAuraSavedData::new, ThaumcraftAuraSavedData::load);
        ThaumcraftAuraSavedData diskRestored = reopenedStorage.computeIfAbsent(factory, "thaumcraft_aura");
        if (diskRestored == aura
                || !diskRestored.hasChunk(center)
                || diskRestored.getBase(center) != 181
                || diskRestored.getVis(center) != 73.0F
                || diskRestored.getFlux(center) != 4.5F) {
            throw new AssertionError("Fresh DimensionDataStorage did not reload physical aura state: base="
                    + diskRestored.getBase(center) + " vis=" + diskRestored.getVis(center)
                    + " flux=" + diskRestored.getFlux(center));
        }
        System.out.println("TC_GAMETEST_AURA_DISK_RELOAD base=181 vis=73.0 flux=4.5");

        AtomicInteger diskReentryRandomCalls = new AtomicInteger();
        boolean diskReinitialized = ThaumcraftAuraWorldEvents.initializeChunkIfReady(
                level, diskRestored, chunkX, chunkZ, () -> {
                    diskReentryRandomCalls.incrementAndGet();
                    return 0.75D;
                });
        if (diskReinitialized || diskReentryRandomCalls.get() != 0
                || diskRestored.getBase(center) != 181
                || diskRestored.getVis(center) != 73.0F
                || diskRestored.getFlux(center) != 4.5F) {
            throw new AssertionError("Disk-restored aura was re-seeded on production re-entry");
        }
        System.out.println("TC_GAMETEST_AURA_DISK_REENTRY_PRESERVED=YES");

        int unloadedX = chunkX + 64;
        int unloadedZ = chunkZ + 64;
        if (level.hasChunk(unloadedX, unloadedZ)) {
            throw new AssertionError("No-force-load fixture unexpectedly starts loaded");
        }
        AtomicInteger unloadedRandomCalls = new AtomicInteger();
        boolean initializedUnloaded = ThaumcraftAuraWorldEvents.initializeChunkIfReady(
                level, aura, unloadedX, unloadedZ, () -> {
                    unloadedRandomCalls.incrementAndGet();
                    return 0.0D;
                });
        if (initializedUnloaded || level.hasChunk(unloadedX, unloadedZ)
                || unloadedRandomCalls.get() != 0) {
            throw new AssertionError("Aura initialization loaded a missing chunk or consumed RNG");
        }
        System.out.println("TC_GAMETEST_AURA_NO_FORCE_LOAD=YES");
        System.out.println("TC_GAMETEST_AURA_GENERATION_ACCEPTED=YES");
        helper.succeed();
    }

    @SuppressWarnings("deprecation")
    @PrefixGameTestTemplate(false)
    @GameTest(
            template = "crystal_spread_empty",
            templateNamespace = ThaumcraftPort.MOD_ID,
            timeoutTicks = 80
    )
    public static void auraSurvivesColdServerRestart(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        if (!level.dimension().equals(Level.OVERWORLD)) {
            throw new AssertionError("Cold-restart fixture requires the Overworld");
        }

        String phase = System.getenv("TC_AURA_COLD_RESTART_PHASE");
        String markerValue = System.getenv("TC_AURA_COLD_RESTART_MARKER");
        if (phase == null || markerValue == null || markerValue.isBlank()) {
            throw new AssertionError("Cold-restart phase and marker environment are required");
        }
        Path marker = Path.of(markerValue);
        long currentPid = ProcessHandle.current().pid();

        if ("write".equals(phase)) {
            if (Files.exists(marker)) {
                throw new AssertionError("Cold-restart marker must be absent before the write process");
            }
            BlockPos persistentChunk = new BlockPos(64_008, 50, 64_008);
            ThaumcraftAuraSavedData aura = ThaumcraftAuraSavedData.get(level);
            if (aura.hasChunk(persistentChunk)) {
                throw new AssertionError("Cold-restart fixture chunk already contains aura data");
            }
            aura.putChunk(persistentChunk, (short) 137, 91.25F, 12.5F);
            level.getDataStorage().save();
            IOUtilities.waitUntilIOWorkerComplete();

            Path auraFile = level.getServer().getWorldPath(LevelResource.ROOT)
                    .resolve("data").resolve("thaumcraft_aura.dat");
            try {
                if (!Files.isRegularFile(auraFile) || Files.size(auraFile) <= 0L) {
                    throw new AssertionError("Cold-restart write did not flush thaumcraft_aura.dat");
                }
                if (marker.getParent() != null) {
                    Files.createDirectories(marker.getParent());
                }
                Files.writeString(marker, currentPid + "\n" + level.getSeed() + "\n"
                        + persistentChunk.getX() + "\n" + persistentChunk.getZ() + "\n");
            } catch (IOException exception) {
                throw new AssertionError("Unable to persist cold-restart marker", exception);
            }
            System.out.println("TC_GAMETEST_AURA_COLD_RESTART_WRITE pid=" + currentPid
                    + " seed=" + level.getSeed()
                    + " x=" + persistentChunk.getX() + " z=" + persistentChunk.getZ()
                    + " base=137 vis=91.25 flux=12.5");
        } else if ("read".equals(phase)) {
            if (!Files.isRegularFile(marker)) {
                throw new AssertionError("Cold-restart marker from the first process is missing");
            }
            try {
                var lines = Files.readAllLines(marker);
                if (lines.size() != 4) {
                    throw new AssertionError("Cold-restart marker must contain PID, seed, X and Z");
                }
                long previousPid = Long.parseLong(lines.get(0));
                long previousSeed = Long.parseLong(lines.get(1));
                int x = Integer.parseInt(lines.get(2));
                int z = Integer.parseInt(lines.get(3));
                if (previousPid <= 0L || previousPid == currentPid) {
                    throw new AssertionError("Cold-restart phase did not use a distinct JVM process");
                }
                if (ProcessHandle.of(previousPid).map(ProcessHandle::isAlive).orElse(false)) {
                    throw new AssertionError("First GameTest JVM is still alive during the reload phase");
                }
                if (level.getSeed() != previousSeed) {
                    throw new AssertionError("Second process opened a different world seed: expected "
                            + previousSeed + " but got " + level.getSeed());
                }

                BlockPos persistentChunk = new BlockPos(x, 50, z);
                ThaumcraftAuraSavedData aura = ThaumcraftAuraSavedData.get(level);
                if (!aura.hasChunk(persistentChunk)
                        || aura.getBase(persistentChunk) != 137
                        || aura.getVis(persistentChunk) != 91.25F
                        || aura.getFlux(persistentChunk) != 12.5F) {
                    throw new AssertionError("New server process did not restore aura SavedData: base="
                            + aura.getBase(persistentChunk) + " vis=" + aura.getVis(persistentChunk)
                            + " flux=" + aura.getFlux(persistentChunk));
                }
                System.out.println("TC_GAMETEST_AURA_COLD_RESTART_RESTORED pid=" + currentPid
                        + " previousPid=" + previousPid + " seed=" + previousSeed
                        + " base=137 vis=91.25 flux=12.5");
            } catch (IOException exception) {
                throw new AssertionError("Unable to read cold-restart marker", exception);
            }
        } else {
            throw new AssertionError("Unknown cold-restart phase: " + phase);
        }

        helper.succeed();
    }

    private static void assertFluxPollutionBounds(ThaumcraftAuraSavedData aura, BlockPos pos) {
        if (aura.hasChunk(pos)) {
            throw new AssertionError("Flux pollution fixture chunk must start uninitialized");
        }
        aura.putChunk(pos, (short) 120, 17.0F, 32760.0F);
        aura.polluteFlux(pos, 10.0F);
        if (aura.getFlux(pos) != 32766.0F
                || aura.getBase(pos) != 120
                || aura.getVis(pos) != 17.0F) {
            throw new AssertionError("Flux pollution exceeded the BETA26 AuraChunk ceiling or changed other channels: "
                    + "base=" + aura.getBase(pos) + " vis=" + aura.getVis(pos)
                    + " flux=" + aura.getFlux(pos));
        }
        System.out.println("TC_GAMETEST_AURA_POLLUTE_FLUX_CAPPED=PASS flux=32766.0");

        aura.polluteFlux(pos, -5.0F);
        aura.polluteFlux(pos, 0.0F);
        aura.polluteFlux(pos, Float.NaN);
        aura.polluteFlux(pos, Float.POSITIVE_INFINITY);
        aura.polluteFlux(pos, Float.NEGATIVE_INFINITY);
        if (aura.getFlux(pos) != 32766.0F
                || aura.getBase(pos) != 120
                || aura.getVis(pos) != 17.0F) {
            throw new AssertionError("Rejected Flux pollution input changed the aura state");
        }
        System.out.println("TC_GAMETEST_AURA_POLLUTE_FLUX_GUARDS=PASS");

        BlockPos missing = pos.offset(32, 0, 0);
        if (aura.hasChunk(missing)) {
            throw new AssertionError("Missing Flux pollution fixture must start absent");
        }
        aura.polluteFlux(missing, 10.0F);
        if (aura.hasChunk(missing)) {
            throw new AssertionError("Flux pollution manufactured an uninitialized aura chunk");
        }
        System.out.println("TC_GAMETEST_AURA_POLLUTE_FLUX_MISSING=PASS");
        System.out.println("TC_GAMETEST_AURA_POLLUTE_FLUX_ACCEPTED=YES");
    }

    private static void assertDrainVisAndFluxSemantics(ThaumcraftAuraSavedData aura, BlockPos pos) {
        if (aura.hasChunk(pos)) {
            throw new AssertionError("Aura drain fixture chunk must start uninitialized");
        }
        aura.putChunk(pos, (short) 100, 10.0F, 3.0F);

        float simulatedVis = aura.drainVis(pos, 4.0F, true);
        float simulatedFlux = aura.drainFlux(pos, 2.0F, true);
        if (simulatedVis != 4.0F || simulatedFlux != 2.0F
                || aura.getVis(pos) != 10.0F || aura.getFlux(pos) != 3.0F
                || aura.getBase(pos) != 100) {
            throw new AssertionError("Simulated aura drain changed state or returned the wrong amount");
        }
        System.out.println("TC_GAMETEST_AURA_DRAIN_SIMULATE=PASS vis=4.0 flux=2.0");

        float partialVis = aura.drainVis(pos, 4.0F, false);
        float partialFlux = aura.drainFlux(pos, 2.0F, false);
        if (partialVis != 4.0F || partialFlux != 2.0F
                || aura.getVis(pos) != 6.0F || aura.getFlux(pos) != 1.0F
                || aura.getBase(pos) != 100) {
            throw new AssertionError("Partial aura drain did not debit the exact requested amounts");
        }
        System.out.println("TC_GAMETEST_AURA_DRAIN_PARTIAL=PASS vis=6.0 flux=1.0");

        float cappedSimVis = aura.drainVis(pos, 20.0F, true);
        float cappedSimFlux = aura.drainFlux(pos, 20.0F, true);
        if (cappedSimVis != 6.0F || cappedSimFlux != 1.0F
                || aura.getVis(pos) != 6.0F || aura.getFlux(pos) != 1.0F) {
            throw new AssertionError("Simulated over-drain did not cap to available aura without mutation");
        }
        float cappedVis = aura.drainVis(pos, 20.0F, false);
        float cappedFlux = aura.drainFlux(pos, 20.0F, false);
        if (cappedVis != 6.0F || cappedFlux != 1.0F
                || aura.getVis(pos) != 0.0F || aura.getFlux(pos) != 0.0F
                || aura.getBase(pos) != 100) {
            throw new AssertionError("Real over-drain did not cap at zero while preserving base");
        }
        if (aura.drainVis(pos, 1.0F, false) != 0.0F
                || aura.drainFlux(pos, 1.0F, false) != 0.0F
                || aura.getVis(pos) != 0.0F || aura.getFlux(pos) != 0.0F) {
            throw new AssertionError("Exhausted aura returned or removed more than was available");
        }
        System.out.println("TC_GAMETEST_AURA_DRAIN_CAPPED=PASS vis=0.0 flux=0.0");

        float negativeSimVis = aura.drainVis(pos, -2.0F, true);
        float negativeSimFlux = aura.drainFlux(pos, -2.0F, true);
        if (negativeSimVis != -2.0F || negativeSimFlux != -2.0F
                || aura.getVis(pos) != 0.0F || aura.getFlux(pos) != 0.0F) {
            throw new AssertionError("BETA26 negative simulated drain behavior changed");
        }
        float negativeVis = aura.drainVis(pos, -2.0F, false);
        float negativeFlux = aura.drainFlux(pos, -2.0F, false);
        if (negativeVis != -2.0F || negativeFlux != -2.0F
                || aura.getVis(pos) != 2.0F || aura.getFlux(pos) != 2.0F
                || aura.getBase(pos) != 100) {
            throw new AssertionError("Finite negative drain must preserve BETA26's negative return / aura increase");
        }
        System.out.println("TC_GAMETEST_AURA_DRAIN_NEGATIVE_BETA26=PASS vis=2.0 flux=2.0");

        float negativeCapSimVis = aura.drainVis(pos, -40_000.0F, true);
        float negativeCapSimFlux = aura.drainFlux(pos, -40_000.0F, true);
        if (negativeCapSimVis != -40_000.0F || negativeCapSimFlux != -40_000.0F
                || aura.getVis(pos) != 2.0F || aura.getFlux(pos) != 2.0F) {
            throw new AssertionError("Simulated negative over-cap drain must preserve the pre-write aura value");
        }
        System.out.println("TC_GAMETEST_AURA_DRAIN_NEGATIVE_CAP_SIMULATE=PASS vis=2.0 flux=2.0");
        float negativeCapVis = aura.drainVis(pos, -40_000.0F, false);
        float negativeCapFlux = aura.drainFlux(pos, -40_000.0F, false);
        if (negativeCapVis != -40_000.0F || negativeCapFlux != -40_000.0F
                || aura.getVis(pos) != 32_766.0F || aura.getFlux(pos) != 32_766.0F
                || aura.getBase(pos) != 100) {
            throw new AssertionError("BETA26 AuraChunk setters must clamp negative-drain growth to 32766");
        }
        System.out.println("TC_GAMETEST_AURA_DRAIN_NEGATIVE_CAP=PASS vis=32766.0 flux=32766.0");

        float nanVis = aura.drainVis(pos, Float.NaN, false);
        float nanFlux = aura.drainFlux(pos, Float.NaN, false);
        float positiveInfinityVis = aura.drainVis(pos, Float.POSITIVE_INFINITY, false);
        float positiveInfinityFlux = aura.drainFlux(pos, Float.POSITIVE_INFINITY, false);
        float negativeInfinityVis = aura.drainVis(pos, Float.NEGATIVE_INFINITY, false);
        float negativeInfinityFlux = aura.drainFlux(pos, Float.NEGATIVE_INFINITY, false);
        if (nanVis != 0.0F || nanFlux != 0.0F
                || positiveInfinityVis != 0.0F || positiveInfinityFlux != 0.0F
                || negativeInfinityVis != 0.0F || negativeInfinityFlux != 0.0F
                || aura.getVis(pos) != 32_766.0F || aura.getFlux(pos) != 32_766.0F) {
            throw new AssertionError("Non-finite aura drain input must be rejected without mutation");
        }
        System.out.println("TC_GAMETEST_AURA_DRAIN_NONFINITE_GUARD=PASS");

        BlockPos missing = pos.offset(32, 0, 0);
        if (aura.hasChunk(missing)
                || aura.drainVis(missing, 1.0F, false) != 0.0F
                || aura.drainFlux(missing, 1.0F, false) != 0.0F
                || aura.hasChunk(missing)) {
            throw new AssertionError("Draining an uninitialized aura chunk must return zero without creating it");
        }
        System.out.println("TC_GAMETEST_AURA_DRAIN_MISSING=PASS");
        System.out.println("TC_GAMETEST_AURA_DRAIN_ACCEPTED=YES");
    }

    private static boolean isColdRestartReadPhase() {
        return "read".equals(System.getenv("TC_AURA_COLD_RESTART_PHASE"));
    }

    private static long safeFileSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException exception) {
            throw new AssertionError("Unable to read saved aura file size", exception);
        }
    }

    private static BlockPos sampleCenter(int chunkX, int chunkZ) {
        return new BlockPos(chunkX * 16 + 8, AuraGenerationLogic.SOURCE_BIOME_SAMPLE_Y, chunkZ * 16 + 8);
    }

    private static void assertLoaded(ServerLevel level, BlockPos pos) {
        if (!level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
            throw new AssertionError("GameTest fixture failed to preload sample chunk for " + pos);
        }
    }

    private static void assertBiome(ServerLevel level, BlockPos pos, String expected) {
        String actual = level.getBiome(pos).unwrapKey()
                .map(key -> key.location().toString()).orElse("<unkeyed>");
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected biome " + expected + " at " + pos + " but got " + actual);
        }
    }

    @SuppressWarnings("unchecked")
    private static void setBiomeLayers(
            ServerLevel level,
            BlockPos sample,
            Holder<Biome> sampleBiome,
            Holder<Biome> seaLevelBiome
    ) {
        int chunkX = sample.getX() >> 4;
        int chunkZ = sample.getZ() >> 4;
        LevelChunk chunk = level.getChunk(chunkX, chunkZ); // explicit test-fixture preload
        for (int quartY = 10; quartY <= 20; quartY++) {
            int blockY = quartY * 4;
            LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(blockY));
            PalettedContainer<Holder<Biome>> palette =
                    (PalettedContainer<Holder<Biome>>) section.getBiomes();
            Holder<Biome> selected = quartY <= 13 ? sampleBiome : seaLevelBiome;
            int localQuartY = quartY & 3;
            for (int quartX = 0; quartX < 4; quartX++) {
                for (int quartZ = 0; quartZ < 4; quartZ++) {
                    palette.set(quartX, localQuartY, quartZ, selected);
                }
            }
        }
        chunk.setUnsaved(true);
    }
}
