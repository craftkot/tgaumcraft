package dev.ehtid.thaumcraft.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/**
 * Persistent per-dimension aura chunks.
 *
 * BETA26 AuraHandler stores base/vis/flux by chunk, shared by every consumer in
 * that chunk. Generation is intentionally separate: this class only preserves
 * storage/drain semantics so a later exact biome generator can populate it
 * without keeping aura on individual block entities.
 */
public final class ThaumcraftAuraSavedData extends SavedData {
    private static final String DATA_NAME = "thaumcraft_aura";
    private static final String TAG_CHUNKS = "chunks";
    private static final String TAG_KEY = "key";
    private static final String TAG_BASE = "base";
    private static final String TAG_VIS = "vis";
    private static final String TAG_FLUX = "flux";

    private static final Factory<ThaumcraftAuraSavedData> FACTORY =
            new Factory<>(ThaumcraftAuraSavedData::new, ThaumcraftAuraSavedData::load);

    private final Map<Long, AuraChunk> chunks = new HashMap<>();

    public record AuraChunk(short base, float vis, float flux) {}

    public static ThaumcraftAuraSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
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
        if (!Float.isFinite(amount) || amount <= 0.0F) return 0.0F;
        long key = key(pos);
        AuraChunk c = chunks.get(key);
        if (c == null) return 0.0F;
        float removed = Math.min(amount, c.flux());
        if (!simulate) {
            chunks.put(key, new AuraChunk(c.base(), c.vis(),
                    Math.max(0.0F, c.flux()-removed)));
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
        chunks.put(key,new AuraChunk(c.base(),c.vis(),c.flux()+amount));
        setDirty();
    }

    /** Exact population boundary used by the future BETA26 aura generator. */
    public void putChunk(BlockPos pos, short base, float vis, float flux) {
        chunks.put(key(pos), new AuraChunk(base, finiteNonNegative(vis), finiteNonNegative(flux)));
        setDirty();
    }

    /** Updates only vis, preserving generated base/flux. */
    public void setVis(BlockPos pos, float vis) {
        long key = key(pos);
        float normalized = finiteNonNegative(vis);
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
     * simulation succeeds without mutation; real writes clamp only the lower bound.
     */
    public boolean modifyVis(BlockPos pos, float amount, boolean simulate) {
        if (!Float.isFinite(amount)) return false;
        long key = key(pos);
        AuraChunk old = chunks.get(key);
        if (old == null) return false;
        if (!simulate) {
            chunks.put(key, new AuraChunk(old.base(), Math.max(0.0F, old.vis() + amount), old.flux()));
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
