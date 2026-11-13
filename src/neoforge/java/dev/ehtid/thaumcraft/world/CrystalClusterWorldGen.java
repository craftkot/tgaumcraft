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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

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
    private static final List<Pending> PENDING = new ArrayList<>();

    private record Pending(ServerLevel level, ChunkPos chunkPos, int queuedAtTick) {}

    private CrystalClusterWorldGen() {}

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!event.isNewChunk()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        // BETA26 generateOres returns immediately in the Nether.
        if (level.dimension().equals(Level.NETHER)) return;

        ChunkPos chunkPos = event.getChunk().getPos();
        for (Pending pending : PENDING) {
            if (pending.level() == level && pending.chunkPos().equals(chunkPos)) return;
        }
        PENDING.add(new Pending(level, chunkPos, level.getServer().getTickCount()));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        int processed = 0;
        Iterator<Pending> iterator = PENDING.iterator();
        while (iterator.hasNext() && processed < MAX_CHUNKS_PER_TICK) {
            Pending pending = iterator.next();
            ServerLevel level = pending.level();
            ChunkPos chunkPos = pending.chunkPos();
            if (level.getServer() != server) continue;
            if (server.getTickCount() - pending.queuedAtTick() > MAX_PENDING_AGE_TICKS) {
                iterator.remove();
                continue;
            }
            // Both checks are non-loading. If the new chunk unloads before the
            // deferred pass, keep the request briefly and wait for it to return.
            if (!level.hasChunk(chunkPos.x, chunkPos.z)
                    || level.getChunkSource().getChunkNow(chunkPos.x, chunkPos.z) == null) {
                continue;
            }

            iterator.remove();
            generateChunk(level, chunkPos, RandomSource.create(chunkSeed(level, chunkPos)));
            processed++;
        }
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
            int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
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
