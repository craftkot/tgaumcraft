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
