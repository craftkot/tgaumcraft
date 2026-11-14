package dev.ehtid.thaumcraft.golem.network;

import dev.ehtid.thaumcraft.golem.seal.GolemSeals;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import java.util.*;

/** Client-thread mirror, independent of ServerLevel and server SavedData, including in singleplayer. */
public final class GolemSealViewCache {
    public static final GolemSealViewCache CLIENT = new GolemSealViewCache();
    private ResourceLocation dimension;
    private final Map<GolemSeals.SealKey,GolemSealView> entries = new LinkedHashMap<>();
    public void clear() { dimension=null; entries.clear(); }
    public void unload(ResourceLocation dim,ChunkPos chunk) {
        if (dim.equals(dimension)) entries.keySet().removeIf(k->new ChunkPos(k.pos()).equals(chunk));
    }
    public void apply(GolemSealViewPayload packet,ResourceLocation currentDimension) {
        if (!packet.dimension().equals(currentDimension)) return;
        if (!currentDimension.equals(dimension)) { clear(); dimension=currentDimension; }
        switch (packet.action()) {
            case UPSERT -> entries.put(packet.key(),packet.view());
            case REMOVE -> entries.remove(packet.key());
            case CLEAR_CHUNK -> unload(currentDimension,new ChunkPos(packet.key().pos()));
        }
    }
    public List<GolemSealView> snapshot(ResourceLocation dim) {
        return dim.equals(dimension) ? List.copyOf(entries.values()) : List.of();
    }
}
