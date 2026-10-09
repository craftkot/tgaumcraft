package dev.ehtid.thaumcraft.port.core;

import java.util.List;
import java.util.Objects;

/**
 * BETA26 ItemGenericEssentiaContainer.getAspects -> AspectList.readFromNBT,
 * then ThaumcraftCraftingManager.getBonusTags / capAspects.
 *
 * The NeoForge caller reads the original Aspects:[{key,amount}] NBT list and
 * passes the entries unchanged. This pure Java bridge intentionally retains
 * AspectList.add accumulation for duplicate keys; nonpositive final amounts
 * are stripped by getBonusTags before the 500-per-aspect object-tag cap.
 */
public final class LegacyContainedAspectTags {
    private LegacyContainedAspectTags() {}

    public record Entry(String key, int amount) {}

    public static AspectListData fromLegacyNbt(AspectRegistry registry, List<Entry> entries) {
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(entries, "entries");
        AspectListData parsed = new AspectListData();
        for (Entry entry : entries) {
            if (entry == null || entry.key() == null) continue;
            AspectData aspect = registry.get(entry.key());
            // The original aspect map can only resolve registered tags;
            // never synthesize new ones for corrupt or foreign NBT.
            if (aspect == null) continue;
            parsed.add(aspect, entry.amount());
        }
        AspectListData effective = new AspectListData();
        for (AspectData aspect : parsed.getAspects()) {
            int amount = parsed.getAmount(aspect);
            if (amount > 0) effective.add(aspect, Math.min(500, amount));
        }
        return effective;
    }
}
