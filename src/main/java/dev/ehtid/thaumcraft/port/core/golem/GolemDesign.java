package dev.ehtid.thaumcraft.port.core.golem;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/** Original six-byte BETA26 construction encoding; upper two bytes survive round-trips. */
public record GolemDesign(long packed) {
    public GolemDesign {
        GolemMaterial.byId(byteAt(packed, 0));
        GolemCatalog.head(byteAt(packed, 1));
        GolemCatalog.arms(byteAt(packed, 2));
        GolemCatalog.legs(byteAt(packed, 3));
        GolemCatalog.addon(byteAt(packed, 4));
        int rank = byteAt(packed, 5);
        if (rank > 10) throw new IllegalArgumentException("Golem rank outside normal BETA26 range: " + rank);
    }
    private static int byteAt(long packed, int index) { return (int) ((packed >>> (index * 8)) & 0xffL); }
    public static GolemDesign defaultWood() { return new GolemDesign(0L); }
    public static GolemDesign fromLong(long packed) { return new GolemDesign(packed); }
    public static GolemDesign fromIds(int material, int head, int arms, int legs, int addon, int rank) {
        int[] ids = {material, head, arms, legs, addon, rank};
        long data = 0L;
        for (int i = 0; i < ids.length; i++) {
            if (ids[i] < 0 || ids[i] > 255) throw new IllegalArgumentException("ID outside byte range");
            data |= ((long) ids[i]) << (8 * i);
        }
        return new GolemDesign(data);
    }
    public long toLong() { return packed; }
    public GolemMaterial material() { return GolemMaterial.byId(byteAt(packed, 0)); }
    public GolemPart head() { return GolemCatalog.head(byteAt(packed, 1)); }
    public GolemPart arms() { return GolemCatalog.arms(byteAt(packed, 2)); }
    public GolemPart legs() { return GolemCatalog.legs(byteAt(packed, 3)); }
    public GolemPart addon() { return GolemCatalog.addon(byteAt(packed, 4)); }
    public int rank() { return byteAt(packed, 5); }
    public GolemDesign withRank(int rank) {
        if (rank < 0 || rank > 10) throw new IllegalArgumentException("Rank outside 0..10");
        return new GolemDesign((packed & ~(0xffL << 40)) | ((long) rank << 40));
    }
    public Set<GolemTrait> traits() {
        var result = EnumSet.noneOf(GolemTrait.class);
        addTraits(result, material().traits());
        addTraits(result, head().traits());
        addTraits(result, arms().traits());
        addTraits(result, legs().traits());
        addTraits(result, addon().traits());
        return Collections.unmodifiableSet(result);
    }
    private static void addTraits(EnumSet<GolemTrait> result, Iterable<GolemTrait> traits) {
        for (var trait : traits) {
            var opposite = trait.opposite();
            if (opposite == null || !result.remove(opposite)) result.add(trait);
        }
    }
    public boolean hasTrait(GolemTrait trait) { return traits().contains(trait); }
    public GolemStats stats() { return GolemStats.derive(this); }
}
