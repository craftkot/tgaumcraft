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
        assertSetVisClampsToLegacyCeiling();
        assertMoonPhaseRegeneration(level, center);
        assertVisNeighborRedistribution(level, center);

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

    private static void assertMoonPhaseRegeneration(ServerLevel level, BlockPos loadedPos) {
        int chunkX = loadedPos.getX() >> 4;
        int chunkZ = loadedPos.getZ() >> 4;
        if (!level.hasChunk(chunkX, chunkZ)) {
            throw new AssertionError("Moon-phase fixture chunk must already be loaded");
        }

        float[] phaseVis = {0.25F, 0.15F, 0.10F, 0.05F, 0.00F, 0.05F, 0.10F, 0.15F};
        float[] phaseMax = {1.15F, 1.05F, 1.00F, 0.95F, 0.85F, 0.95F, 1.00F, 1.05F};
        ThaumcraftAuraSavedData isolated = new ThaumcraftAuraSavedData();

        for (int phase = 0; phase < 8; phase++) {
            isolated.putChunk(loadedPos, (short) 100, 0.0F, 0.0F);
            int changed = ThaumcraftAuraWorldEvents.regenerateLoadedAuraChunks(level, isolated, phase);
            float expected = phaseVis[phase];
            int expectedChanges = expected > 0.0F ? 1 : 0;
            if (changed != expectedChanges || Math.abs(isolated.getVis(loadedPos) - expected) > 0.00001F
                    || isolated.getBase(loadedPos) != 100 || isolated.getFlux(loadedPos) != 0.0F) {
                throw new AssertionError("BETA26 moon phase " + phase + " recovery mismatch: changed="
                        + changed + " base=" + isolated.getBase(loadedPos) + " vis=" + isolated.getVis(loadedPos)
                        + " flux=" + isolated.getFlux(loadedPos));
            }
        }

        // phaseMax controls the recovery ceiling, not only the increment.
        for (int phase = 0; phase < 8; phase++) {
            isolated.putChunk(loadedPos, (short) 100, 100.5F, 0.0F);
            int changed = ThaumcraftAuraWorldEvents.regenerateLoadedAuraChunks(level, isolated, phase);
            float expected = 100.5F;
            float target = 100.0F * phaseMax[phase];
            if (expected < target && phaseVis[phase] > 0.0F) {
                expected += Math.min(target - expected, phaseVis[phase]);
            }
            int expectedChanges = expected > 100.5F ? 1 : 0;
            if (changed != expectedChanges || Math.abs(isolated.getVis(loadedPos) - expected) > 0.0001F) {
                throw new AssertionError("BETA26 moon phase " + phase + " ceiling mismatch: changed="
                        + changed + " expectedVis=" + expected + " actualVis=" + isolated.getVis(loadedPos));
            }
        }

        isolated.putChunk(loadedPos, (short) 100, 100.5F, 0.25F);
        int fluxChanged = ThaumcraftAuraWorldEvents.regenerateLoadedAuraChunks(level, isolated, 0);
        if (fluxChanged != 1 || Math.abs(isolated.getVis(loadedPos) - 100.75F) > 0.0001F
                || isolated.getFlux(loadedPos) != 0.25F) {
            throw new AssertionError("Vis recovery must account for total Vis+Flux and preserve Flux");
        }

        // Valid BETA26 base is at most 500, so the full-moon recovery target is at most 575.
        // A record already above that target must not be changed by the recovery branch.
        isolated.putChunk(loadedPos, (short) 500, 32_765.9F, 0.0F);
        int aboveTarget = ThaumcraftAuraWorldEvents.regenerateLoadedAuraChunks(level, isolated, 0);
        if (aboveTarget != 0 || Math.abs(isolated.getVis(loadedPos) - 32_765.9F) > 0.001F) {
            throw new AssertionError("Moon-phase recovery changed a chunk already above its phase target");
        }

        // At the valid maximum base, recovery stops exactly at the phase target (575 Vis).
        isolated.putChunk(loadedPos, (short) 500, 574.9F, 0.0F);
        int nearTarget = ThaumcraftAuraWorldEvents.regenerateLoadedAuraChunks(level, isolated, 0);
        if (nearTarget != 1 || Math.abs(isolated.getVis(loadedPos) - 575.0F) > 0.001F) {
            throw new AssertionError("Moon-phase recovery overshot the valid maximum-base target");
        }

        int farChunkX = chunkX + 10_000;
        int farChunkZ = chunkZ + 10_000;
        BlockPos unloadedPos = new BlockPos(farChunkX * 16 + 8, 50, farChunkZ * 16 + 8);
        if (level.hasChunk(farChunkX, farChunkZ)) {
            throw new AssertionError("Distant moon-phase fixture unexpectedly starts loaded");
        }

        ThaumcraftAuraSavedData unloadedOnly = new ThaumcraftAuraSavedData();
        unloadedOnly.putChunk(unloadedPos, (short) 100, 0.0F, 0.0F);
        int unloadedChanges = ThaumcraftAuraWorldEvents.regenerateLoadedAuraChunks(level, unloadedOnly, 0);
        if (unloadedChanges != 0 || level.hasChunk(farChunkX, farChunkZ)
                || unloadedOnly.getVis(unloadedPos) != 0.0F) {
            throw new AssertionError("Moon-phase recovery loaded or modified an unloaded chunk");
        }

        ThaumcraftAuraSavedData mixed = new ThaumcraftAuraSavedData();
        mixed.putChunk(loadedPos, (short) 100, 0.0F, 0.0F);
        mixed.putChunk(unloadedPos, (short) 100, 0.0F, 0.0F);
        int mixedChanges = ThaumcraftAuraWorldEvents.regenerateLoadedAuraChunks(level, mixed, 0);
        if (mixedChanges != 1 || Math.abs(mixed.getVis(loadedPos) - 0.25F) > 0.00001F
                || mixed.getVis(unloadedPos) != 0.0F || level.hasChunk(farChunkX, farChunkZ)) {
            throw new AssertionError("Loaded-only aura recovery did not isolate the distant unloaded record");
        }

        ThaumcraftAuraSavedData wrapped = new ThaumcraftAuraSavedData();
        wrapped.putChunk(loadedPos, (short) 100, 0.0F, 0.0F);
        ThaumcraftAuraWorldEvents.regenerateLoadedAuraChunks(level, wrapped, -1);
        if (Math.abs(wrapped.getVis(loadedPos) - 0.15F) > 0.00001F) {
            throw new AssertionError("Negative moon phase must wrap to BETA26 phase 7");
        }
        wrapped.putChunk(loadedPos, (short) 100, 0.0F, 0.0F);
        ThaumcraftAuraWorldEvents.regenerateLoadedAuraChunks(level, wrapped, 8);
        if (Math.abs(wrapped.getVis(loadedPos) - 0.25F) > 0.00001F) {
            throw new AssertionError("Moon phase 8 must wrap to BETA26 phase 0");
        }

        if (ThaumcraftAuraWorldEvents.shouldProcessAuraWorldTime(0L, 0L)
                || !ThaumcraftAuraWorldEvents.shouldProcessAuraWorldTime(0L, 1L)
                || ThaumcraftAuraWorldEvents.shouldProcessAuraWorldTime(1L, 1L)
                || !ThaumcraftAuraWorldEvents.shouldProcessAuraWorldTime(1L, 0L)) {
            throw new AssertionError("Aura recovery must follow BETA26 lastWorldTime gating");
        }
        System.out.println("TC_GAMETEST_AURA_WORLD_TIME_GATE=PASS frozen=0 advanced=1 reverse=1");
        System.out.println("TC_GAMETEST_AURA_MOON_PHASE_REGEN=PASS phases=8 loaded=1 unloaded=0");
    }

    private static void assertVisNeighborRedistribution(ServerLevel level, BlockPos loadedPos) {
        int chunkX = loadedPos.getX() >> 4;
        int chunkZ = loadedPos.getZ() >> 4;
        BlockPos source = sampleCenter(chunkX, chunkZ);
        BlockPos north = sampleCenter(chunkX, chunkZ - 1);
        BlockPos south = sampleCenter(chunkX, chunkZ + 1);
        BlockPos west = sampleCenter(chunkX - 1, chunkZ);
        BlockPos east = sampleCenter(chunkX + 1, chunkZ);

        ThaumcraftAuraSavedData selected = new ThaumcraftAuraSavedData();
        selected.putChunk(source, (short) 100, 10.0F, 0.0F);
        selected.putChunk(north, (short) 100, 5.0F, 0.0F);
        selected.putChunk(south, (short) 100, 2.0F, 0.0F);
        selected.putChunk(west, (short) 100, 3.0F, 0.0F);
        selected.putChunk(east, (short) 100, 1.0F, 0.0F);
        float beforeTotal = selected.getVis(source) + selected.getVis(north)
                + selected.getVis(south) + selected.getVis(west) + selected.getVis(east);
        if (!selected.redistributeVisFromChunk(chunkX, chunkZ, 1.15F, bound -> 0)
                || selected.getVis(source) != 9.0F
                || selected.getVis(east) != 2.0F
                || selected.getVis(north) != 5.0F
                || selected.getVis(south) != 2.0F
                || selected.getVis(west) != 3.0F) {
            throw new AssertionError("BETA26 aura flow must select the lowest eligible neighbor and cap one transfer at 1 Vis");
        }
        float afterTotal = selected.getVis(source) + selected.getVis(north)
                + selected.getVis(south) + selected.getVis(west) + selected.getVis(east);
        if (beforeTotal != afterTotal) {
            throw new AssertionError("BETA26 neighbor Vis transfer must conserve total Vis");
        }

        ThaumcraftAuraSavedData threshold = new ThaumcraftAuraSavedData();
        threshold.putChunk(source, (short) 100, 10.0F, 0.0F);
        threshold.putChunk(east, (short) 100, 7.5F, 0.0F);
        if (threshold.redistributeVisFromChunk(chunkX, chunkZ, 1.15F, bound -> 0)
                || threshold.getVis(source) != 10.0F || threshold.getVis(east) != 7.5F) {
            throw new AssertionError("BETA26 neighbor flow threshold is strict: neighborVis/currentVis must be below 0.75");
        }

        ThaumcraftAuraSavedData capacity = new ThaumcraftAuraSavedData();
        capacity.putChunk(source, (short) 100, 10.0F, 0.0F);
        capacity.putChunk(east, (short) 100, 100.0F, 15.0F);
        if (capacity.redistributeVisFromChunk(chunkX, chunkZ, 1.15F, bound -> 0)
                || capacity.getVis(source) != 10.0F || capacity.getVis(east) != 100.0F
                || capacity.getFlux(east) != 15.0F) {
            throw new AssertionError("A neighbor at its Vis+Flux phase ceiling must not receive Vis");
        }

        ThaumcraftAuraSavedData runtime = new ThaumcraftAuraSavedData();
        runtime.putChunk(source, (short) 100, 10.0F, 0.0F);
        runtime.putChunk(east, (short) 100, 0.0F, 0.0F);
        int runtimeChanges = runtime.processAuraChunks(level, 0, bound -> 0);
        if (runtimeChanges != 3
                || Math.abs(runtime.getVis(source) - 9.25F) > 0.0001F
                || Math.abs(runtime.getVis(east) - 1.25F) > 0.0001F) {
            throw new AssertionError("Production aura pass must run Vis transfer before loaded moon recovery: changes="
                    + runtimeChanges + " source=" + runtime.getVis(source) + " neighbor=" + runtime.getVis(east));
        }

        int farX = chunkX + 64;
        int farZ = chunkZ + 64;
        if (level.hasChunk(farX, farZ) || level.hasChunk(farX + 1, farZ)) {
            throw new AssertionError("Unloaded neighbor-flow fixture unexpectedly starts loaded");
        }
        BlockPos farSource = sampleCenter(farX, farZ);
        BlockPos farEast = sampleCenter(farX + 1, farZ);
        ThaumcraftAuraSavedData unloaded = new ThaumcraftAuraSavedData();
        unloaded.putChunk(farSource, (short) 100, 10.0F, 0.0F);
        unloaded.putChunk(farEast, (short) 100, 0.0F, 0.0F);
        int unloadedChanges = unloaded.processAuraChunks(level, 0, bound -> 0);
        if (unloadedChanges != 1
                || unloaded.getVis(farSource) != 9.0F
                || unloaded.getVis(farEast) != 1.0F
                || level.hasChunk(farX, farZ) || level.hasChunk(farX + 1, farZ)) {
            throw new AssertionError("Aura Vis flow must update known unloaded records without force-loading either chunk");
        }

        System.out.println("TC_GAMETEST_AURA_VIS_NEIGHBOR=PASS lowest=2.0 transfer=1.0 conserved=YES threshold=0.75 ceiling=115 unloaded=updated no_force_load=YES");
    }

    private static void assertSetVisClampsToLegacyCeiling() {
        ThaumcraftAuraSavedData isolated = new ThaumcraftAuraSavedData();
        BlockPos existing = new BlockPos(0, 64, 0);
        isolated.putChunk(existing, (short) 120, 17.0F, 4.0F);
        isolated.setVis(existing, 40_000.0F);
        if (isolated.getVis(existing) != 32_766.0F
                || isolated.getBase(existing) != 120
                || isolated.getFlux(existing) != 4.0F) {
            throw new AssertionError("BETA26 AuraChunk.setVis must clamp existing chunks to 32766 while preserving base/flux");
        }

        BlockPos migration = new BlockPos(32, 64, 0);
        isolated.setVis(migration, 40_000.0F);
        if (isolated.getVis(migration) != 32_766.0F
                || isolated.getBase(migration) != 500
                || isolated.getFlux(migration) != 0.0F) {
            throw new AssertionError("Aura migration setVis path must obey the same 32766 ceiling");
        }
        System.out.println("TC_GAMETEST_AURA_SET_VIS_CLAMP=PASS existing=32766.0 migration=32766.0");
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
