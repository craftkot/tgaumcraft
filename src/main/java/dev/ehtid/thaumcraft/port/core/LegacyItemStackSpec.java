package dev.ehtid.thaumcraft.port.core;

/**
 * Parsed form of ResearchManager.parseJSONtoItemStack's legacy string syntax.
 * The SNBT is kept verbatim for the later 1.12-NBT -> 1.21-components migration layer.
 */
public record LegacyItemStackSpec(String itemId, int count, int meta, String snbt) {
    public LegacyItemStackSpec {
        if (itemId == null || itemId.isEmpty()) throw new IllegalArgumentException("itemId is required");
    }
}
