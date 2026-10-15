package dev.ehtid.thaumcraft.port.core;

import java.util.Objects;

/**
 * Thaumcraft 6.1.BETA26 TileJarFillable source semantics, extracted from the
 * original 1.12.2 bytecode (not an invented NeoForge replacement).
 *
 * This pure immutable core has NO resource creation: mutations explicitly
 * return the accepted/extracted amount. A live BlockEntity must persist the
 * returned State only after the external source/sink transfer is verified.
 *
 * Original quirks kept:
 * - capacity exactly 250; single stored aspect, clear aspect when drained;
 * - suction 64 when filtered, otherwise 32, and zero when full;
 * - the filter governs doesContainerAccept, not addToContainer directly.
 *
 * Defensive improvement: zero and negative transfers do not alter contents,
 * preventing negative-amount duplication when exposed to modern adapters.
 */
public final class LegacyEssentiaJarLogic {
    private LegacyEssentiaJarLogic() {}

    public static final int CAPACITY = 250;
    public static final int UNFILTERED_SUCTION = 32;
    public static final int FILTERED_SUCTION = 64;

    public record State(String aspect, String filter, int amount) {
        public State {
            if (amount < 0 || amount > CAPACITY)
                throw new IllegalArgumentException("Jar amount outside 0..250");
            if (amount > 0 && (aspect == null || aspect.isBlank()))
                throw new IllegalArgumentException("Nonempty jar requires an aspect");
            if (amount == 0) aspect = null;
            if (filter != null && filter.isBlank()) filter = null;
        }
        public static State empty() { return new State(null, null, 0); }
    }

    public record Transfer(State state, int transferred) {
        public Transfer {
            Objects.requireNonNull(state);
            if (transferred < 0)
                throw new IllegalArgumentException("Negative transfer");
        }
    }

    /** Source doesContainerAccept only checks the label/filter. */
    public static boolean accepts(State jar, String aspect) {
        Objects.requireNonNull(jar);
        return aspect != null && (jar.filter() == null || jar.filter().equals(aspect));
    }

    /** Matches original getMinimumSuction and getSuctionAmount. */
    public static int minimumSuction(State jar) {
        Objects.requireNonNull(jar);
        return jar.filter() == null ? UNFILTERED_SUCTION : FILTERED_SUCTION;
    }

    public static int suction(State jar) {
        Objects.requireNonNull(jar);
        return jar.amount() >= CAPACITY ? 0 : minimumSuction(jar);
    }

    public static String suctionAspect(State jar) {
        Objects.requireNonNull(jar);
        return jar.filter() != null ? jar.filter() : jar.aspect();
    }

    /** Exact BETA26 addToContainer: returns remainder in old API. */
    public static Transfer add(State jar, String aspect, int offered) {
        Objects.requireNonNull(jar);
        if (offered <= 0 || aspect == null || aspect.isBlank())
            return new Transfer(jar, 0);
        if (jar.amount() > 0 && !aspect.equals(jar.aspect()))
            return new Transfer(jar, 0);
        int accepted = Math.min(offered, CAPACITY - jar.amount());
        if (accepted == 0) return new Transfer(jar, 0);
        return new Transfer(new State(aspect, jar.filter(), jar.amount() + accepted), accepted);
    }

    /** Original takeFromContainer succeeds only for sufficient same-aspect
     * amount and atomically clears the aspect once the jar reaches zero.
     */
    public static Transfer take(State jar, String aspect, int requested) {
        Objects.requireNonNull(jar);
        if (requested <= 0 || aspect == null || !aspect.equals(jar.aspect())
                || jar.amount() < requested)
            return new Transfer(jar, 0);
        int left = jar.amount() - requested;
        return new Transfer(new State(left == 0 ? null : jar.aspect(),
                jar.filter(), left), requested);
    }

    /** The original jar exposes its transport connection only on UP. */
    public static boolean connectsOnTop(int directionIndex) {
        // Vanilla 1.12 EnumFacing.UP index 1, NeoForge Direction.UP index 1.
        return directionIndex == 1;
    }
}
