package dev.ehtid.thaumcraft.world;

import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.block.CrystalClusterBlock;
import dev.ehtid.thaumcraft.port.core.LegacyCrystalWorldGenLogic;
import dev.ehtid.thaumcraft.registry.ThaumcraftBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * BETA26 ThaumcraftWorldGenerator.generateOres crystal branch.
 *
 * NeoForge's ChunkEvent.Load exposes whether a chunk is newly generated.
 * Block placement is deferred to a server tick, then guarded by getChunkNow /
 * hasChunkAt so this adapter never requests a chunk ticket or force-loads a
 * neighbour. The source-era 100% oreDensity and 8/64 crystal defaults are used;
 * modern terrain and the deferred chunk RNG are not claimed to be 1.12-exact.
 */
@EventBusSubscriber(modid = ThaumcraftPort.MOD_ID)
public final class CrystalClusterWorldGen {
    private static final int MAX_PENDING_AGE_TICKS = 1200;
    private static final int MAX_CHUNKS_PER_TICK = 4;
    private static final PendingQueue PENDING = new PendingQueue();

    record Pending(ServerLevel level, ChunkPos chunkPos, int queuedAtTick) {}

    /**
     * A tick drains only the entries that existed at its start. Chunk generation
     * may synchronously fire ChunkEvent.Load and enqueue more work; those new
     * entries must wait until the next tick rather than mutate an active iterator.
     */
    static final class PendingQueue {
        private final Deque<Pending> entries = new ArrayDeque<>();
        private final Set<PendingKey> queued = new HashSet<>();

        void enqueue(Pending pending) {
            PendingKey key = new PendingKey(pending.level(), pending.chunkPos());
            if (queued.add(key)) entries.addLast(pending);
        }

        int process(
                MinecraftServer server,
                int currentTick,
                int maxChunks,
                BiConsumer<ServerLevel, ChunkPos> generator
        ) {
            int batchSize = entries.size();
            int processed = 0;
            for (int index = 0; index < batchSize && processed < maxChunks; index++) {
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
                // Both checks are non-loading. If the new chunk unloads before the
                // deferred pass, keep the request briefly and wait for it to return.
                if (!level.hasChunk(chunkPos.x, chunkPos.z)
                        || level.getChunkSource().getChunkNow(chunkPos.x, chunkPos.z) == null) {
                    entries.addLast(pending);
                    continue;
                }

                try {
                    generator.accept(level, chunkPos);
                    processed++;
                } finally {
                    queued.remove(key);
                }
            }
            return processed;
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

    private CrystalClusterWorldGen() {}

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!event.isNewChunk()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        // BETA26 generateOres returns immediately in the Nether.
        if (level.dimension().equals(Level.NETHER)) return;

        ChunkPos chunkPos = event.getChunk().getPos();
        PENDING.enqueue(new Pending(level, chunkPos, level.getServer().getTickCount()));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        PENDING.process(
                server,
                server.getTickCount(),
                MAX_CHUNKS_PER_TICK,
                (level, chunkPos) -> generateChunk(
                        level, chunkPos, RandomSource.create(chunkSeed(level, chunkPos)))
        );
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PENDING.clear();
    }

    /** Executes one source-era chunk's eight crystal-cluster attempts. */
    public static int generateChunk(ServerLevel level, ChunkPos chunkPos, RandomSource random) {
        if (level.dimension().equals(Level.NETHER)) return 0;

        int density = LegacyCrystalWorldGenLogic.DEFAULT_DENSITY_PERCENT;
        int maximum = LegacyCrystalWorldGenLogic.maximumCrystalAmount(density);
        int totalCrystalAmount = 0;
        int attempts = LegacyCrystalWorldGenLogic.attemptCount(density);
        for (int attempt = 0; attempt < attempts; attempt++) {
            int x = chunkPos.getMinBlockX() + 8 + random.nextInt(13) - 6;
            int z = chunkPos.getMinBlockZ() + 8 + random.nextInt(13) - 6;
            int minY = level.getMinBuildHeight();
            int surfaceY = runtimeSurfaceHeight(level, x, z);
            int y = minY + random.nextInt(LegacyCrystalWorldGenLogic.yRange(minY, surfaceY));
            BlockPos center = new BlockPos(x, y, z);

            String biomeId = level.getBiome(center).unwrapKey()
                    .map(key -> key.location().toString()).orElse("");
            int metadata = LegacyCrystalWorldGenLogic.chooseShardMetadata(biomeId, random::nextInt);
            CrystalClusterBlock crystal = blockForMetadata(metadata);
            totalCrystalAmount += generateClusterAt(level, center, crystal, random);
            // BETA26 checks the accumulated SIZE amount after each 3x3x3 attempt.
            if (totalCrystalAmount > maximum) break;
        }
        return totalCrystalAmount;
    }

    /**
     * BETA26's World#getHeight(BlockPos) samples the live generated surface.
     * This adapter runs after ChunkEvent.Load, so WORLD_SURFACE_WG is not
     * primed here; use the runtime WORLD_SURFACE heightmap instead.
     */
    static int runtimeSurfaceHeight(ServerLevel level, int x, int z) {
        return level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
    }

    /**
     * Places the source 3x3x3 cluster around one selected center. Public for
     * the native GameTest; production calls it only from generateChunk.
     */
    public static int generateClusterAt(
            ServerLevel level,
            BlockPos center,
            CrystalClusterBlock crystal,
            RandomSource random
    ) {
        int totalCrystalAmount = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    // BETA26 skips one third of candidate cells before reading state.
                    if (random.nextInt(3) == 0) continue;

                    BlockPos target = center.offset(dx, dy, dz);
                    if (!level.hasChunkAt(target)) continue;
                    BlockState existing = level.getBlockState(target);
                    boolean replaceable = existing.isAir() || existing.canBeReplaced();
                    if (existing.liquid() || !replaceable) continue;
                    if (!LegacyCrystalWorldGenLogic.canPlace(false, true, touchesRock(level, target))) {
                        continue;
                    }

                    int size = LegacyCrystalWorldGenLogic.clusterSize(random.nextInt(3));
                    BlockState placed = crystal.defaultBlockState()
                            .setValue(CrystalClusterBlock.SIZE, size)
                            .setValue(CrystalClusterBlock.GENERATION, 1);
                    if (!placed.canSurvive(level, target)) continue;
                    if (level.setBlock(target, placed, 2)) totalCrystalAmount += size;
                }
            }
        }
        return totalCrystalAmount;
    }

    private static boolean touchesRock(ServerLevel level, BlockPos target) {
        for (Direction direction : Direction.values()) {
            BlockPos supportPos = target.relative(direction);
            if (!level.hasChunkAt(supportPos)) continue;
            if (CrystalClusterBlock.hasCrystalSupport(level, target, direction)) return true;
        }
        return false;
    }

    private static CrystalClusterBlock blockForMetadata(int metadata) {
        return switch (metadata) {
            case 1 -> ThaumcraftBlocks.CRYSTAL_IGNIS.get();
            case 2 -> ThaumcraftBlocks.CRYSTAL_AQUA.get();
            case 3 -> ThaumcraftBlocks.CRYSTAL_TERRA.get();
            case 4 -> ThaumcraftBlocks.CRYSTAL_ORDO.get();
            case 5 -> ThaumcraftBlocks.CRYSTAL_PERDITIO.get();
            case 6 -> ThaumcraftBlocks.CRYSTAL_VITIUM.get();
            default -> ThaumcraftBlocks.CRYSTAL_AER.get();
        };
    }

    private static long chunkSeed(ServerLevel level, ChunkPos chunkPos) {
        // Vanilla 1.12 chunk-seed shape; terrain and Forge worldgen call order
        // still differ on 1.21, so this is deterministic rather than exact RNG parity.
        RandomSource seedSource = RandomSource.create(level.getSeed());
        long xSeed = seedSource.nextLong() / 2L * 2L + 1L;
        long zSeed = seedSource.nextLong() / 2L * 2L + 1L;
        return ((long) chunkPos.x * xSeed + (long) chunkPos.z * zSeed) ^ level.getSeed();
    }
}
