package dev.ehtid.thaumcraft.world;

import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.port.core.AuraGenerationLogic;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.DoubleSupplier;

/**
 * Deferred BETA26 ChunkEvents.chunkLoad aura population.
 *
 * The source restores an existing per-chunk aura record or calls
 * AuraHandler.generateAura for every loaded chunk that has no saved aura.
 * Modern sampling needs the center plus four cardinal biome chunks. Queue
 * those positions during ChunkEvent.Load, then sample only after all five
 * chunks are fully available through getChunkNow; never join a chunk future
 * or force-load a neighbor from the load callback.
 */
@EventBusSubscriber(modid = ThaumcraftPort.MOD_ID)
public final class ThaumcraftAuraChunkPopulationQueue {
    static final int MAX_PENDING_AGE_TICKS = 12_000;
    static final int MAX_PENDING_INSPECTIONS_PER_TICK = 128;
    static final int MAX_INITIALIZATIONS_PER_TICK = 16;

    static final PendingQueue PENDING = new PendingQueue();

    record Pending(ServerLevel level, ChunkPos chunkPos, int queuedAtTick) {}

    /**
     * The tick examines a bounded prefix and rotates chunks whose sample cross
     * is not fully loaded. Loading any member of that cross enqueues the center
     * again, so an expired edge request is retried when its missing neighbor arrives.
     */
    static final class PendingQueue {
        private final Deque<Pending> entries = new ArrayDeque<>();
        private final Set<PendingKey> queued = new HashSet<>();

        boolean enqueue(Pending pending) {
            PendingKey key = new PendingKey(pending.level(), pending.chunkPos());
            if (!queued.add(key)) return false;
            entries.addLast(pending);
            return true;
        }

        int process(
                MinecraftServer server,
                int currentTick,
                int maxInspections,
                int maxInitializations,
                BiPredicate<ServerLevel, ChunkPos> initializer
        ) {
            if (maxInspections <= 0 || maxInitializations <= 0) return 0;
            int batchSize = Math.min(entries.size(), maxInspections);
            int initialized = 0;
            for (int index = 0; index < batchSize && initialized < maxInitializations; index++) {
                Pending pending = entries.pollFirst();
                if (pending == null) break;

                ServerLevel level = pending.level();
                ChunkPos chunkPos = pending.chunkPos();
                PendingKey key = new PendingKey(level, chunkPos);
                if (level.getServer() != server) {
                    entries.addLast(pending);
                    continue;
                }
                if (currentTick - pending.queuedAtTick() > MAX_PENDING_AGE_TICKS) {
                    queued.remove(key);
                    continue;
                }
                if (hasAuraRecord(level, chunkPos)) {
                    queued.remove(key);
                    initialized++;
                    continue;
                }
                if (!ThaumcraftAuraWorldEvents.fiveChunksLoaded(level, chunkPos.x, chunkPos.z)) {
                    entries.addLast(pending);
                    continue;
                }

                final boolean handled;
                try {
                    handled = initializer.test(level, chunkPos);
                } catch (RuntimeException | Error failure) {
                    entries.addLast(pending);
                    throw failure;
                }
                if (handled) {
                    queued.remove(key);
                    initialized++;
                } else {
                    entries.addLast(pending);
                }
            }
            return initialized;
        }

        boolean contains(ServerLevel level, ChunkPos chunkPos) {
            return queued.contains(new PendingKey(level, chunkPos));
        }

        int size() {
            return entries.size();
        }

        void clear() {
            entries.clear();
            queued.clear();
        }

        private record PendingKey(ServerLevel level, ChunkPos chunkPos) {}
    }

    private ThaumcraftAuraChunkPopulationQueue() {}

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        enqueueLoadedChunk(PENDING, level, event.getChunk().getPos(), level.getServer().getTickCount());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        PENDING.process(
                server,
                server.getTickCount(),
                MAX_PENDING_INSPECTIONS_PER_TICK,
                MAX_INITIALIZATIONS_PER_TICK,
                (level, chunkPos) -> initializeAuraChunk(
                        level, chunkPos, level.getRandom()::nextGaussian)
        );
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PENDING.clear();
    }

    /**
     * Enqueue the loaded chunk and the four centers whose source-era biome
     * sample crosses include it. This lets a later neighbor load retry a
     * previously deferred center without scanning or loading the world.
     */
    static int enqueueLoadedChunk(
            PendingQueue queue, ServerLevel level, ChunkPos loadedChunk, int currentTick
    ) {
        ChunkPos[] cross = {
                loadedChunk,
                new ChunkPos(loadedChunk.x - 1, loadedChunk.z),
                new ChunkPos(loadedChunk.x + 1, loadedChunk.z),
                new ChunkPos(loadedChunk.x, loadedChunk.z - 1),
                new ChunkPos(loadedChunk.x, loadedChunk.z + 1)
        };
        int added = 0;
        for (ChunkPos candidate : cross) {
            if (hasAuraRecord(level, candidate)) continue;
            if (queue.enqueue(new Pending(level, candidate, currentTick))) added++;
        }
        return added;
    }

    static boolean initializeAuraChunk(
            ServerLevel level, ChunkPos chunkPos, DoubleSupplier gaussianSource
    ) {
        BlockPos center = sampleCenter(chunkPos);
        ThaumcraftAuraSavedData aura = ThaumcraftAuraSavedData.getIfPresent(level);
        if (aura != null && aura.hasChunk(center)) return true;
        if (!ThaumcraftAuraWorldEvents.fiveChunksLoaded(level, chunkPos.x, chunkPos.z)) return false;

        if (aura == null) aura = ThaumcraftAuraSavedData.get(level);
        if (aura.hasChunk(center)) return true;
        return ThaumcraftAuraWorldEvents.initializeChunkIfReady(
                level, aura, chunkPos.x, chunkPos.z, gaussianSource) || aura.hasChunk(center);
    }

    static boolean hasAuraRecord(ServerLevel level, ChunkPos chunkPos) {
        ThaumcraftAuraSavedData aura = ThaumcraftAuraSavedData.getIfPresent(level);
        return aura != null && aura.hasChunk(sampleCenter(chunkPos));
    }

    static boolean isPending(ServerLevel level, ChunkPos chunkPos) {
        return PENDING.contains(level, chunkPos);
    }

    static BlockPos sampleCenter(ChunkPos chunkPos) {
        return new BlockPos(
                chunkPos.getMinBlockX() + 8,
                AuraGenerationLogic.SOURCE_BIOME_SAMPLE_Y,
                chunkPos.getMinBlockZ() + 8
        );
    }
}
