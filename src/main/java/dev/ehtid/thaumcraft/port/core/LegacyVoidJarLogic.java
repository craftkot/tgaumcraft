package dev.ehtid.thaumcraft.port.core;

import java.util.Objects;

/**
 * BETA26 TileJarFillableVoid semantics, independently checked against the original
 * thaumcraft.common.tiles.essentia.TileJarFillableVoid source.
 *
 * A void jar stores at most 250 units, but ACCEPTS and destroys excess of its
 * current aspect instead of reporting it as unconsumed. The live adapter must
 * debit exactly {@link Transfer#consumed()} from a real source and perform one
 * 1-in-250 random flux roll if {@link Transfer#overflowed()} is true.
 *
 * The original void jar's suction remains 32 even when full (filtered: 48
 * while not full). This deliberately differs from TileJarFillable.
 */
public final class LegacyVoidJarLogic {
    private LegacyVoidJarLogic() {}

    public record Transfer(LegacyEssentiaJarLogic.State state, int consumed, int destroyed) {
        public Transfer {
            Objects.requireNonNull(state);
            if (consumed < 0 || destroyed < 0 || destroyed > consumed)
                throw new IllegalArgumentException("invalid void-jar debit");
        }
        public boolean overflowed() { return destroyed > 0; }
    }

    public static int minimumSuction(LegacyEssentiaJarLogic.State jar) {
        Objects.requireNonNull(jar);
        return jar.filter() == null ? 32 : 48;
    }

    public static int suction(LegacyEssentiaJarLogic.State jar) {
        Objects.requireNonNull(jar);
        return jar.filter() != null && jar.amount() < 250 ? 48 : 32;
    }

    /**
     * Exact TileJarFillableVoid#addToContainer behavior for positive input:
     * accepts any amount of its current aspect (even if already full) and
     * destroys the excess. The low-level original method does NOT inspect the
     * label; callers must first check doesContainerAccept separately.
     */
    public static Transfer add(LegacyEssentiaJarLogic.State jar, String aspect, int offered) {
        Objects.requireNonNull(jar);
        if (offered <= 0 || aspect == null || aspect.isBlank())
            return new Transfer(jar, 0, 0);
        if (jar.amount() > 0 && !aspect.equals(jar.aspect()))
            return new Transfer(jar, 0, 0);

        int saved = Math.min(offered, LegacyEssentiaJarLogic.CAPACITY - jar.amount());
        int discarded = offered - saved;
        var next = saved == 0 ? jar : new LegacyEssentiaJarLogic.State(
                aspect, jar.filter(), jar.amount() + saved);
        return new Transfer(next, offered, discarded);
    }
}
