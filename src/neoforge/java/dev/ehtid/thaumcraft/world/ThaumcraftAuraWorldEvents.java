package dev.ehtid.thaumcraft.world;

import dev.ehtid.thaumcraft.port.core.AuraGenerationLogic;
import dev.ehtid.thaumcraft.port.core.LegacyModernBiomeCompatibility;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.DoubleSupplier;

/**
 * Incremental, main-thread aura population for gameplay-loaded chunks.
 *
 * A chunk is initialized only after its own and its four sampled neighbors
 * are already loaded; NEVER cause synchronous chunk loading during the
 * chunk-load callback (which can deadlock). The persisted aura snapshot is
 * authoritative: existing Vis/Flux is never re-seeded on relog/restart.
 *
 * This is the world integration of the BETA26 base arithmetic, not a claim
 * of exact source-era worldgen RNG/biomes for Minecraft 1.21 terrain.
 */
public final class ThaumcraftAuraWorldEvents {
    private ThaumcraftAuraWorldEvents() {}
    private static final int SAMPLE_SPACING = 16;
    private static final Map<ServerLevel, Long> LAST_AURA_WORLD_TIMES = new WeakHashMap<>();

    public static void onServerTick(ServerTickEvent.Post event) {
        if (!shouldProcessAuraWorldTick(event.getServer().getTickCount())) return;
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            ServerLevel level = player.serverLevel();
            int cx = player.chunkPosition().x;
            int cz = player.chunkPosition().z;
            ThaumcraftAuraSavedData aura = ThaumcraftAuraSavedData.get(level);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int x = cx + dx, z = cz + dz;
                    initializeChunkIfReady(level, aura, x, z, level.getRandom()::nextGaussian);
                }
            }
        }

        // BETA26 AuraThread wakes approximately once per second, but it only
        // processes chunks when world.getWorldTime() changes. A frozen daylight
        // cycle therefore intentionally pauses Vis/Flux simulation. The server
        // tick gate above is only the 1.21 event-loop approximation of its timer.
        // Process persisted records only in dimensions with loaded chunks;
        // getIfPresent avoids manufacturing empty SavedData in untouched dimensions.
        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (level.getChunkSource().getLoadedChunksCount() == 0) continue;
            ThaumcraftAuraSavedData aura = ThaumcraftAuraSavedData.getIfPresent(level);
            if (aura != null) {
                long worldTime = level.getDayTime();
                Long previousWorldTime = LAST_AURA_WORLD_TIMES.put(level, worldTime);
                long lastWorldTime = previousWorldTime == null ? 0L : previousWorldTime;
                if (shouldProcessAuraWorldTime(lastWorldTime, worldTime)) {
                    processAuraChunks(level, aura, level.getMoonPhase());
                }
            }
        }
    }

    static int processAuraChunks(ServerLevel level, ThaumcraftAuraSavedData aura, int moonPhase) {
        var random = level.getRandom();
        return aura.processAuraChunks(level, moonPhase, random::nextInt, random::nextFloat);
    }

    static int regenerateLoadedAuraChunks(ServerLevel level, ThaumcraftAuraSavedData aura, int moonPhase) {
        return aura.regenerateLoadedChunks(level, moonPhase);
    }

    /** BETA26 AuraThread only processes aura records when world time changes. */
    static boolean shouldProcessAuraWorldTime(long previousWorldTime, long currentWorldTime) {
        return previousWorldTime != currentWorldTime;
    }

    /** Modern event-loop throttle approximating AuraThread's one-second wake interval. */
    static boolean shouldProcessAuraWorldTick(long serverTickCount) {
        return serverTickCount % 20L == 0L;
    }

    /**
     * The production population path, also exercised by the native GameTest.
     * The Gaussian supplier is evaluated only after the five chunks are
     * already loaded and the aura entry is absent, so skipped work consumes
     * no random value and never triggers a chunk load.
     */
    static boolean initializeChunkIfReady(
            ServerLevel level,
            ThaumcraftAuraSavedData aura,
            int x,
            int z,
            DoubleSupplier gaussianSource
    ) {
        BlockPos middle = new BlockPos(
                x * 16 + 8,
                AuraGenerationLogic.SOURCE_BIOME_SAMPLE_Y,
                z * 16 + 8
        );
        if (aura.hasChunk(middle) || !fiveChunksLoaded(level, x, z)) return false;

        float center = modifier(level, middle);
        float north = modifier(level, middle.north(SAMPLE_SPACING));
        float south = modifier(level, middle.south(SAMPLE_SPACING));
        float west = modifier(level, middle.west(SAMPLE_SPACING));
        float east = modifier(level, middle.east(SAMPLE_SPACING));
        int base = AuraGenerationLogic.generatedBase(center, north, south,
                west, east, gaussianSource.getAsDouble());
        // Initial Vis equals base; no free Vis/Flux is created on re-entry.
        aura.putChunk(middle, (short) base, base, 0.0F);
        return true;
    }

    static boolean fiveChunksLoaded(ServerLevel level, int x, int z) {
        // hasChunk/hasChunkAt may report a chunk that is still in the load
        // pipeline. getChunkNow is a non-joining lookup: never block the server
        // tick while a neighboring chunk is being completed by ChunkMap.
        var chunks = level.getChunkSource();
        return chunks.getChunkNow(x, z) != null
                && chunks.getChunkNow(x - 1, z) != null
                && chunks.getChunkNow(x + 1, z) != null
                && chunks.getChunkNow(x, z - 1) != null
                && chunks.getChunkNow(x, z + 1) != null;
    }

    private static float modifier(ServerLevel level, BlockPos pos) {
        String id = level.getBiome(pos).unwrapKey()
                .map(key -> key.location().toString()).orElse("");
        return LegacyModernBiomeCompatibility.auraModifier(id);
    }
}
