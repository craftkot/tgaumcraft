package dev.ehtid.thaumcraft.world;

import dev.ehtid.thaumcraft.port.core.LegacyFluxRiftTriggerLogic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.function.DoubleSupplier;
import java.util.function.IntUnaryOperator;

/**
 * Persistent per-dimension aura chunks.
 *
 * BETA26 AuraHandler stores base/vis/flux by chunk, shared by every consumer in
 * that chunk. Chunk-load population is handled separately by
 * ThaumcraftAuraChunkPopulationQueue and ThaumcraftAuraWorldEvents, so this
 * class preserves the shared storage/drain semantics without putting aura on
 * individual block entities.
 */
public final class ThaumcraftAuraSavedData extends SavedData {
    private static final String DATA_NAME = "thaumcraft_aura";
    private static final String TAG_CHUNKS = "chunks";
    private static final String TAG_KEY = "key";
    private static final String TAG_BASE = "base";
    private static final String TAG_VIS = "vis";
    private static final String TAG_FLUX = "flux";
    private static final float AURA_CHUNK_VALUE_MAX = 32766.0F;

    private static final Factory<ThaumcraftAuraSavedData> FACTORY =
            new Factory<>(ThaumcraftAuraSavedData::new, ThaumcraftAuraSavedData::load);

    private final Map<Long, AuraChunk> chunks = new HashMap<>();

    // BETA26 AuraThread.riftTrigger is transient and holds one candidate per dimension.
    // The EntityFluxRift spawn consumer is a separate, still-unported subsystem.
    private BlockPos pendingRiftTrigger;

    public record AuraChunk(short base, float vis, float flux) {}

    public static ThaumcraftAuraSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    /** Returns persisted aura data only when it already exists; does not create an empty save. */
    public static ThaumcraftAuraSavedData getIfPresent(ServerLevel level) {
        return level.getDataStorage().get(FACTORY, DATA_NAME);
    }

    /** Takes and clears the transient BETA26 per-dimension Flux-rift trigger candidate. */
    public BlockPos takePendingRiftTrigger() {
        BlockPos trigger = pendingRiftTrigger;
        pendingRiftTrigger = null;
        return trigger;
    }

    public static ThaumcraftAuraSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        ThaumcraftAuraSavedData data = new ThaumcraftAuraSavedData();
        ListTag list = tag.getList(TAG_CHUNKS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag row = list.getCompound(i);
            long key = row.getLong(TAG_KEY);
            short base = row.getShort(TAG_BASE);
            float vis = finiteNonNegative(row.getFloat(TAG_VIS));
            float flux = finiteNonNegative(row.getFloat(TAG_FLUX));
            data.chunks.put(key, new AuraChunk(base, vis, flux));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (var entry : chunks.entrySet()) {
            CompoundTag row = new CompoundTag();
            row.putLong(TAG_KEY, entry.getKey());
            row.putShort(TAG_BASE, entry.getValue().base());
            row.putFloat(TAG_VIS, entry.getValue().vis());
            row.putFloat(TAG_FLUX, entry.getValue().flux());
            list.add(row);
        }
        tag.put(TAG_CHUNKS, list);
        return tag;
    }

    public boolean hasChunk(BlockPos pos) {
        return chunks.containsKey(key(pos));
    }

    public AuraChunk getChunk(BlockPos pos) {
        return chunks.get(key(pos));
    }

    public float getVis(BlockPos pos) {
        AuraChunk chunk = getChunk(pos);
        return chunk == null ? 0.0F : chunk.vis();
    }

    public float getFlux(BlockPos pos) {
        AuraChunk c = getChunk(pos);
        return c == null ? 0.0F : c.flux();
    }

    public int getBase(BlockPos pos) {
        AuraChunk c = getChunk(pos);
        return c == null ? 0 : c.base();
    }

    /** AuraHelper.drainFlux: same per-chunk capping as Vis. */
    public float drainFlux(BlockPos pos, float amount, boolean simulate) {
        // BETA26 permits finite negative requests: they return a negative amount and
        // increase stored Flux. Preserve that odd API behavior; reject NaN/Infinity
        // so invalid floats cannot poison persistent aura data.
        if (!Float.isFinite(amount)) return 0.0F;
        long key = key(pos);
        AuraChunk c = chunks.get(key);
        if (c == null) return 0.0F;
        float removed = Math.min(amount, c.flux());
        if (!simulate) {
            chunks.put(key, new AuraChunk(c.base(), c.vis(),
                    Math.min(AURA_CHUNK_VALUE_MAX, Math.max(0.0F, c.flux()-removed))));
            setDirty();
        }
        return removed;
    }

    public void addVis(BlockPos pos, float units) {
        if (!Float.isFinite(units) || units <= 0) return;
        modifyVis(pos, units, false); // unknown aura chunks stay unknown
    }

    /** BETA26 polluteAura adds flux, including at starved crystal sites. */
    public void polluteFlux(BlockPos pos, float amount) {
        if (!Float.isFinite(amount) || amount <= 0.0F) return;
        long key=key(pos);
        AuraChunk c=chunks.get(key);
        if (c==null) return; // avoid manufactured aura on uninitialized chunks
        chunks.put(key, new AuraChunk(c.base(), c.vis(),
                Math.min(AURA_CHUNK_VALUE_MAX, c.flux() + amount)));
        setDirty();
    }

    /**
     * Atomic all-or-nothing 10-unit crystal growth/spread transaction.
     * A successful world growth MUST NOT be based on a partial aura drain.
     * Missing chunks and insufficient units do not mutate persistence.
     */
    public boolean trySpendCrystalAura(BlockPos pos, boolean flux, float required) {
        if (!Float.isFinite(required) || required <= 0.0F) return false;
        long chunkKey = key(pos);
        AuraChunk before = chunks.get(chunkKey);
        if (before == null) return false;
        float available = flux ? before.flux() : before.vis();
        if (available < required) return false;
        chunks.put(chunkKey, flux
                ? new AuraChunk(before.base(), before.vis(), before.flux() - required)
                : new AuraChunk(before.base(), before.vis() - required, before.flux()));
        setDirty();
        return true;
    }

    /** Exact population boundary used by the future BETA26 aura generator. */
    public void putChunk(BlockPos pos, short base, float vis, float flux) {
        chunks.put(key(pos), new AuraChunk(base, finiteNonNegative(vis), finiteNonNegative(flux)));
        setDirty();
    }

    /**
     * Processes the BETA26 AuraThread cache: only currently loaded source chunks
     * participate, and only loaded neighbors can receive transfers. BETA26
     * ChunkEvents.chunkSave removes an aura entry when its chunk is unloaded;
     * SavedData retains those records for persistence, so non-joining chunk
     * lookups reproduce the original loaded-cache boundary.
     */
    int processAuraChunks(
            ServerLevel level, int moonPhase, IntUnaryOperator nextInt, DoubleSupplier nextFloat) {
        int phase = Math.floorMod(moonPhase, 8);
        float phaseMax = phaseMaxFor(phase);
        int changed = 0;
        for (long chunkKey : chunks.keySet()) {
            int chunkX = ChunkPos.getX(chunkKey);
            int chunkZ = ChunkPos.getZ(chunkKey);
            // ChunkDataEvent.Save removes unloaded entries from the original
            // AuraWorld cache. SavedData keeps them for persistence, so filter
            // them with a non-joining lookup before consuming RNG or touching
            // either side of a neighbor transfer.
            if (level.getChunkSource().getChunkNow(chunkX, chunkZ) == null) continue;

            // BETA26 shuffles once, then selects both channel destinations from
            // that same order before applying Vis and Flux transfers.
            changed += Integer.bitCount(redistributeNeighborChannelsFromChunk(
                    level, chunkX, chunkZ, phaseMax, nextInt, true, true));
            if (processAuraChunkRecoveryOrConversion(chunkX, chunkZ, phase, nextFloat)) {
                changed++;
            }

            // BETA26 AuraThread checks the post-conversion Flux against 75% of
            // the phase-adjusted base, then rolls Flux / 500 / 10. The trigger
            // map is one transient position per dimension; it is not SavedData.
            AuraChunk current = chunks.get(chunkKey);
            float phaseAdjustedBase = current.base() * phaseMax;
            if (current.flux() > phaseAdjustedBase * 0.75F) {
                float roll = (float) nextFloat.getAsDouble();
                if (LegacyFluxRiftTriggerLogic.shouldTrigger(
                        phaseAdjustedBase, current.flux(), roll)) {
                    pendingRiftTrigger = new BlockPos(chunkX * 16, 0, chunkZ * 16);
                }
            }
        }
        return changed;
    }

    /** BETA26 Vis-only helper retained for focused regression tests. */
    boolean redistributeVisFromChunk(int chunkX, int chunkZ, float phaseMax, IntUnaryOperator nextInt) {
        return (redistributeNeighborChannelsFromChunk(
                chunkX, chunkZ, phaseMax, nextInt, true, false) & 1) != 0;
    }

    /** BETA26 Flux-only helper retained for focused regression tests. */
    boolean redistributeFluxFromChunk(int chunkX, int chunkZ, float phaseMax, IntUnaryOperator nextInt) {
        return (redistributeNeighborChannelsFromChunk(
                chunkX, chunkZ, phaseMax, nextInt, false, true) & 2) != 0;
    }

    /**
     * BETA26 AuraThread.processAuraChunk neighbor pass. One shuffled horizontal
     * direction order selects both destinations. Vis requires the phase ceiling;
     * Flux deliberately does not. Each channel transfers at most one unit.
     *
     * @return bit 0 when Vis moved, bit 1 when Flux moved
     */
    private int redistributeNeighborChannelsFromChunk(
            int chunkX, int chunkZ, float phaseMax, IntUnaryOperator nextInt,
            boolean transferVis, boolean transferFlux) {
        return redistributeNeighborChannelsFromChunk(
                null, chunkX, chunkZ, phaseMax, nextInt, transferVis, transferFlux);
    }

    private int redistributeNeighborChannelsFromChunk(
            ServerLevel level, int chunkX, int chunkZ, float phaseMax, IntUnaryOperator nextInt,
            boolean transferVis, boolean transferFlux) {
        long sourceKey = ChunkPos.asLong(chunkX, chunkZ);
        AuraChunk source = chunks.get(sourceKey);
        if (source == null) return 0;

        int[] directions = {0, 1, 2, 3};
        for (int i = directions.length - 1; i > 0; i--) {
            int j = Math.floorMod(nextInt.applyAsInt(i + 1), i + 1);
            int swap = directions[i];
            directions[i] = directions[j];
            directions[j] = swap;
        }

        long visTargetKey = 0L;
        AuraChunk visTarget = null;
        float lowestVis = Float.MAX_VALUE;
        long fluxTargetKey = 0L;
        AuraChunk fluxTarget = null;
        float lowestFlux = Float.MAX_VALUE;

        for (int direction : directions) {
            Direction facing = Direction.from2DDataValue(direction);
            int neighborX = chunkX + facing.getStepX();
            int neighborZ = chunkZ + facing.getStepZ();
            long neighborKey = ChunkPos.asLong(neighborX, neighborZ);
            AuraChunk neighbor = chunks.get(neighborKey);
            if (neighbor == null) continue;
            if (level != null && level.getChunkSource().getChunkNow(neighborX, neighborZ) == null) {
                continue;
            }

            if (transferVis
                    && (visTarget == null || lowestVis > neighbor.vis())
                    && neighbor.vis() + neighbor.flux() < neighbor.base() * phaseMax) {
                visTarget = neighbor;
                visTargetKey = neighborKey;
                lowestVis = neighbor.vis();
            }

            // BETA26 chooses the lowest-Flux neighbor independently of the Vis
            // capacity rule; an over-ceiling neighbor may still receive Flux.
            if (transferFlux && (fluxTarget == null || lowestFlux > neighbor.flux())) {
                fluxTarget = neighbor;
                fluxTargetKey = neighborKey;
                lowestFlux = neighbor.flux();
            }
        }

        int changed = 0;
        if (transferVis && visTarget != null && lowestVis < source.vis()
                && lowestVis / source.vis() < 0.75F) {
            float transfer = Math.min(source.vis() - lowestVis, 1.0F);
            if (transfer > 0.0F) {
                AuraChunk sourceNow = chunks.get(sourceKey);
                AuraChunk targetNow = chunks.get(visTargetKey);
                chunks.put(sourceKey, new AuraChunk(
                        sourceNow.base(), sourceNow.vis() - transfer, sourceNow.flux()));
                chunks.put(visTargetKey, new AuraChunk(
                        targetNow.base(), targetNow.vis() + transfer, targetNow.flux()));
                changed |= 1;
            }
        }

        if (transferFlux && fluxTarget != null
                && source.flux() > Math.max(5.0F, source.base() / 10.0F)
                && lowestFlux < source.flux() / 1.75F) {
            float transfer = Math.min(source.flux() - lowestFlux, 1.0F);
            if (transfer > 0.0F) {
                // The Vis branch may have updated either of these records.
                // Preserve those latest Vis values while applying Flux.
                AuraChunk sourceNow = chunks.get(sourceKey);
                AuraChunk targetNow = chunks.get(fluxTargetKey);
                float nextSourceFlux = Math.min(AURA_CHUNK_VALUE_MAX,
                        Math.max(0.0F, sourceNow.flux() - transfer));
                float nextTargetFlux = Math.min(AURA_CHUNK_VALUE_MAX,
                        Math.max(0.0F, targetNow.flux() + transfer));
                chunks.put(sourceKey, new AuraChunk(
                        sourceNow.base(), sourceNow.vis(), nextSourceFlux));
                chunks.put(fluxTargetKey, new AuraChunk(
                        targetNow.base(), targetNow.vis(), nextTargetFlux));
                changed |= 2;
            }
        }

        if (changed != 0) setDirty();
        return changed;
    }

    /**
     * BETA26 AuraThread's local recovery / Vis-to-Flux else-if chain, after neighbor
     * transfers. The caller has already established that this source chunk is
     * present in the original loaded AuraWorld cache.
     */
    private boolean processAuraChunkRecoveryOrConversion(
            int chunkX, int chunkZ, int phase, DoubleSupplier nextFloat) {
        long chunkKey = ChunkPos.asLong(chunkX, chunkZ);
        AuraChunk current = chunks.get(chunkKey);
        if (current == null) return false;

        float phaseVis = phaseVisFor(phase);
        float phaseFlux = 0.25F - phaseVis;
        float base = current.base() * phaseMaxFor(phase);
        float currentVis = current.vis();
        float currentFlux = current.flux();

        if (currentVis + currentFlux < base) {
            float increase = Math.min(base - (currentVis + currentFlux), phaseVis);
            if (!(increase > 0.0F)) return false;
            currentVis += increase;
        } else if (currentVis > base * 1.25F) {
            if (!(nextFloat.getAsDouble() < 0.1D)) return false;
            currentVis -= phaseFlux;
            currentFlux += phaseFlux;
        } else if (currentVis <= base * 0.1F && currentVis >= currentFlux) {
            if (!(nextFloat.getAsDouble() < 0.1D)) return false;
            currentFlux += phaseFlux;
        } else {
            return false;
        }

        chunks.put(chunkKey, new AuraChunk(
                current.base(),
                Math.min(AURA_CHUNK_VALUE_MAX, finiteNonNegative(currentVis)),
                Math.min(AURA_CHUNK_VALUE_MAX, finiteNonNegative(currentFlux))
        ));
        setDirty();
        return true;
    }

    /**
     * Applies the first BETA26 AuraThread recovery branch once to every known chunk
     * that is already loaded in this dimension. ChunkPos packed keys are decoded
     * without asking the chunk source to load anything.
     */
    public int regenerateLoadedChunks(ServerLevel level, int moonPhase) {
        int changed = 0;
        for (long chunkKey : chunks.keySet()) {
            if (regenerateLoadedChunk(level, ChunkPos.getX(chunkKey), ChunkPos.getZ(chunkKey), moonPhase)) {
                changed++;
            }
        }
        return changed;
    }

    /**
     * Standalone loaded-chunk recovery helper retained for focused tests. Production
     * processing uses processAuraChunkRecoveryOrConversion to preserve BETA26's
     * recovery-before-conversion else-if ordering.
     */
    public boolean regenerateLoadedChunk(ServerLevel level, int chunkX, int chunkZ, int moonPhase) {
        if (!level.hasChunk(chunkX, chunkZ)) return false;

        int phase = Math.floorMod(moonPhase, 8);
        float phaseVis = phaseVisFor(phase);
        float phaseMax = phaseMaxFor(phase);

        long chunkKey = ChunkPos.asLong(chunkX, chunkZ);
        AuraChunk current = chunks.get(chunkKey);
        if (current == null) return false;

        float target = current.base() * phaseMax;
        float total = current.vis() + current.flux();
        if (total >= target || phaseVis <= 0.0F) return false;

        float increase = Math.min(target - total, phaseVis);
        float nextVis = Math.min(AURA_CHUNK_VALUE_MAX,
                Math.max(0.0F, current.vis() + increase));
        if (nextVis == current.vis()) return false;

        chunks.put(chunkKey, new AuraChunk(current.base(), nextVis, current.flux()));
        setDirty();
        return true;
    }

    private static float phaseMaxFor(int phase) {
        return switch (phase) {
            case 0 -> 1.15F;
            case 1 -> 1.05F;
            case 2 -> 1.00F;
            case 3 -> 0.95F;
            case 4 -> 0.85F;
            case 5 -> 0.95F;
            case 6 -> 1.00F;
            default -> 1.05F;
        };
    }

    private static float phaseVisFor(int phase) {
        return switch (phase) {
            case 0 -> 0.25F;
            case 1 -> 0.15F;
            case 2 -> 0.10F;
            case 3 -> 0.05F;
            case 4 -> 0.00F;
            case 5 -> 0.05F;
            case 6 -> 0.10F;
            default -> 0.15F;
        };
    }

    /** Updates only vis, preserving generated base/flux. */
    public void setVis(BlockPos pos, float vis) {
        long key = key(pos);
        float normalized = Math.min(AURA_CHUNK_VALUE_MAX, finiteNonNegative(vis));
        AuraChunk old = chunks.get(key);
        if (old == null) {
            // Migration path for pre-aura-bridge workbenches: retain the cached
            // value as both base and vis until the real generator owns the chunk.
            short base = (short) Math.min(500, Math.max(0, (int) normalized));
            chunks.put(key, new AuraChunk(base, normalized, 0.0F));
        } else {
            chunks.put(key, new AuraChunk(old.base(), normalized, old.flux()));
        }
        setDirty();
    }

    /**
     * Exact AuraHandler.modifyVisInChunk semantics: a missing chunk returns false;
     * simulation succeeds without mutation; real writes use BETA26 AuraChunk bounds [0, 32766].
     */
    public boolean modifyVis(BlockPos pos, float amount, boolean simulate) {
        if (!Float.isFinite(amount)) return false;
        long key = key(pos);
        AuraChunk old = chunks.get(key);
        if (old == null) return false;
        if (!simulate) {
            chunks.put(key, new AuraChunk(old.base(), Math.min(AURA_CHUNK_VALUE_MAX, Math.max(0.0F, old.vis() + amount)), old.flux()));
            setDirty();
        }
        return true;
    }

    /** Exact BETA26 AuraHandler.drainVis amount capping + simulation semantics. */
    public float drainVis(BlockPos pos, float amount, boolean simulate) {
        if (!Float.isFinite(amount)) return 0.0F;
        long key = key(pos);
        AuraChunk old = chunks.get(key);
        if (old == null) return 0.0F;
        if (amount > old.vis()) amount = old.vis();
        return modifyVis(pos, -amount, simulate) ? amount : 0.0F;
    }

    public float drainVis(BlockPos pos, float amount) {
        return drainVis(pos, amount, false);
    }

    private static long key(BlockPos pos) {
        return ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
    }

    private static float finiteNonNegative(float value) {
        return Float.isFinite(value) ? Math.max(0.0F, value) : 0.0F;
    }
}
