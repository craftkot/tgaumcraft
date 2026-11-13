package dev.ehtid.thaumcraft.world;

import dev.ehtid.thaumcraft.port.core.AuraGenerationLogic;
import dev.ehtid.thaumcraft.port.core.LegacyModernBiomeCompatibility;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

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

    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 20 != 0) return;
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

        // BETA26 AuraThread runs approximately once per second. Process every
        // persisted aura record in each dimension that already has loaded chunks;
        // getIfPresent avoids manufacturing empty SavedData in untouched dimensions.
        for (ServerLevel level : event.getServer().getAllLevels()) {
            if (level.getChunkSource().getLoadedChunksCount() == 0) continue;
            ThaumcraftAuraSavedData aura = ThaumcraftAuraSavedData.getIfPresent(level);
            if (aura != null) {
                regenerateLoadedAuraChunks(level, aura, level.getMoonPhase());
            }
        }
    }

    static int regenerateLoadedAuraChunks(ServerLevel level, ThaumcraftAuraSavedData aura, int moonPhase) {
        return aura.regenerateLoadedChunks(level, moonPhase);
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

    private static boolean fiveChunksLoaded(ServerLevel level, int x, int z) {
        return level.hasChunk(x, z)
                && level.hasChunk(x - 1, z) && level.hasChunk(x + 1, z)
                && level.hasChunk(x, z - 1) && level.hasChunk(x, z + 1);
    }

    private static float modifier(ServerLevel level, BlockPos pos) {
        String id = level.getBiome(pos).unwrapKey()
                .map(key -> key.location().toString()).orElse("");
        return LegacyModernBiomeCompatibility.auraModifier(id);
    }
}
