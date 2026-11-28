package dev.ehtid.thaumcraft.world;

import dev.ehtid.thaumcraft.ThaumcraftPort;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Comparator;
import java.util.concurrent.atomic.AtomicInteger;

/** Native server proof for the BETA26 chunk-load aura population path. */
@GameTestHolder(ThaumcraftPort.MOD_ID)
public final class AuraChunkPopulationQueueGameTests {
    private static final TicketType<ChunkPos> TEST_CHUNK_TICKET = TicketType.create(
            "thaumcraft_aura_queue_gametest", Comparator.comparingLong(ChunkPos::toLong));

    private AuraChunkPopulationQueueGameTests() {}

    @SuppressWarnings("deprecation")
    @PrefixGameTestTemplate(false)
    @GameTest(
            template = "crystal_spread_empty",
            templateNamespace = ThaumcraftPort.MOD_ID,
            timeoutTicks = 100
    )
    public static void loadedChunkWaitsForFiveFullyLoadedBiomeSamples(GameTestHelper helper) {
        if ("read".equals(System.getenv("TC_AURA_COLD_RESTART_PHASE"))) {
            System.out.println("TC_GAMETEST_AURA_CHUNK_QUEUE_SKIPPED_PHASE=read");
            helper.succeed();
            return;
        }

        ServerLevel level = helper.getLevel();
        ChunkPos target = findUnreadyChunkWithNoAura(level,
                helper.absolutePos(new BlockPos(8, 2, 8)));
        BlockPos center = ThaumcraftAuraChunkPopulationQueue.sampleCenter(target);
        if (!ThaumcraftAuraChunkPopulationQueue.isPending(level, target)) {
            throw new AssertionError("ChunkEvent.Load did not enqueue the missing aura chunk " + target);
        }
        if (ThaumcraftAuraWorldEvents.fiveChunksLoaded(level, target.x, target.z)) {
            throw new AssertionError("Aura fixture unexpectedly starts with all five sample chunks loaded");
        }

        int loadedBefore = level.getChunkSource().getLoadedChunksCount();
        AtomicInteger gaussianCalls = new AtomicInteger();
        boolean initializedEarly = ThaumcraftAuraChunkPopulationQueue.initializeAuraChunk(
                level, target, () -> {
                    gaussianCalls.incrementAndGet();
                    return 0.0D;
                });
        if (initializedEarly || gaussianCalls.get() != 0
                || level.getChunkSource().getLoadedChunksCount() != loadedBefore
                || ThaumcraftAuraChunkPopulationQueue.hasAuraRecord(level, target)) {
            throw new AssertionError("Aura population must defer without loading a missing sample chunk"
                    + " initialized=" + initializedEarly
                    + " gaussianCalls=" + gaussianCalls.get()
                    + " loadedBefore=" + loadedBefore
                    + " loadedAfter=" + level.getChunkSource().getLoadedChunksCount());
        }
        System.out.println("TC_GAMETEST_AURA_CHUNK_QUEUE_DEFERRED=PASS no_force_load=YES");

        // Retain the five sample chunks for the duration of this asynchronous
        // assertion. getChunk() alone only loads them synchronously; it does not
        // keep distant test chunks resident until the server tick processes the
        // ready queue. These test-only tickets never enter production code.
        ChunkPos[] retainedChunks = {
                target,
                new ChunkPos(target.x - 1, target.z),
                new ChunkPos(target.x + 1, target.z),
                new ChunkPos(target.x, target.z - 1),
                new ChunkPos(target.x, target.z + 1)
        };
        for (ChunkPos retained : retainedChunks) {
            level.getChunkSource().addRegionTicket(TEST_CHUNK_TICKET, retained, 0, retained);
        }
        boolean[] ticketsReleased = {false};
        Runnable releaseTickets = () -> {
            if (ticketsReleased[0]) return;
            ticketsReleased[0] = true;
            for (ChunkPos retained : retainedChunks) {
                level.getChunkSource().removeRegionTicket(TEST_CHUNK_TICKET, retained, 0, retained);
            }
            System.out.println("TC_GAMETEST_AURA_CHUNK_QUEUE_TICKETS=RELEASED count=5");
        };
        System.out.println("TC_GAMETEST_AURA_CHUNK_QUEUE_TICKETS=HELD count=5");
        helper.runAfterDelay(80, releaseTickets);

        // The missing sample chunks are explicitly loaded by the fixture, not
        // by production. Their ChunkEvent.Load callbacks also re-enqueue the
        // center, proving that a deferred boundary request can become ready.
        level.getChunk(target.x - 1, target.z);
        level.getChunk(target.x + 1, target.z);
        level.getChunk(target.x, target.z - 1);
        level.getChunk(target.x, target.z + 1);
        if (!ThaumcraftAuraWorldEvents.fiveChunksLoaded(level, target.x, target.z)
                || !ThaumcraftAuraChunkPopulationQueue.isPending(level, target)) {
            throw new AssertionError("Loading the four neighbors did not make the queued center ready");
        }
        if (!ThaumcraftAuraChunkPopulationQueue.PENDING.isPrioritized(level, target)) {
            throw new AssertionError("A newly ready aura center was not promoted ahead of the pending world-load backlog");
        }
        System.out.println("TC_GAMETEST_AURA_CHUNK_QUEUE_PRIORITY=PASS pending="
                + ThaumcraftAuraChunkPopulationQueue.PENDING.size());

        awaitPopulation(helper, level, target, center, 40, releaseTickets);
    }

    private static ChunkPos findUnreadyChunkWithNoAura(ServerLevel level, BlockPos anchor) {
        int anchorX = anchor.getX() >> 4;
        int anchorZ = anchor.getZ() >> 4;
        for (int offset = 64; offset <= 512; offset += 32) {
            int x = anchorX + offset;
            int z = anchorZ + offset;
            if (level.getChunkSource().getChunkNow(x, z) != null) continue;

            level.getChunk(x, z);
            ChunkPos candidate = new ChunkPos(x, z);
            if (ThaumcraftAuraWorldEvents.fiveChunksLoaded(level, x, z)) continue;
            if (ThaumcraftAuraChunkPopulationQueue.hasAuraRecord(level, candidate)) continue;
            if (ThaumcraftAuraChunkPopulationQueue.isPending(level, candidate)) return candidate;
        }
        throw new AssertionError("Could not create a loaded chunk with an unloaded cardinal aura sample");
    }

    private static void awaitPopulation(
            GameTestHelper helper,
            ServerLevel level,
            ChunkPos target,
            BlockPos center,
            int remainingTicks,
            Runnable releaseTickets
    ) {
        helper.runAfterDelay(1, () -> {
            ThaumcraftAuraSavedData aura = ThaumcraftAuraSavedData.getIfPresent(level);
            if (aura != null && aura.hasChunk(center)) {
                if (ThaumcraftAuraChunkPopulationQueue.isPending(level, target)) {
                    throw new AssertionError("Aura record was created but its queue entry was not removed");
                }
                int base = aura.getBase(center);
                if (base < 0 || base > 500
                        || aura.getVis(center) != base
                        || aura.getFlux(center) != 0.0F) {
                    throw new AssertionError("New chunk aura must start with Vis=base and Flux=0"
                            + " base=" + base + " vis=" + aura.getVis(center)
                            + " flux=" + aura.getFlux(center));
                }
                System.out.println("TC_GAMETEST_AURA_CHUNK_QUEUE_INITIALIZED=PASS base=" + base
                        + " vis=" + aura.getVis(center) + " flux=" + aura.getFlux(center));
                System.out.println("TC_GAMETEST_AURA_CHUNK_QUEUE_ACCEPTED=YES");
                releaseTickets.run();
                helper.succeed();
                return;
            }
            if (remainingTicks <= 1) {
                releaseTickets.run();
                throw new AssertionError("Chunk-load aura queue did not initialize the center after neighbors loaded"
                        + " pending=" + ThaumcraftAuraChunkPopulationQueue.isPending(level, target)
                        + " prioritized=" + ThaumcraftAuraChunkPopulationQueue.PENDING.isPrioritized(level, target)
                        + " fiveChunksLoaded=" + ThaumcraftAuraWorldEvents.fiveChunksLoaded(level, target.x, target.z)
                        + " pendingEntries=" + ThaumcraftAuraChunkPopulationQueue.PENDING.size()
                        + " loadedChunks=" + level.getChunkSource().getLoadedChunksCount());
            }
            awaitPopulation(helper, level, target, center, remainingTicks - 1, releaseTickets);
        });
    }
}
