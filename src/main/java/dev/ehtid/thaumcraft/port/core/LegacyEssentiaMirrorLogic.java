package dev.ehtid.thaumcraft.port.core;

import java.util.Objects;

/**
 * Core source parity for Thaumcraft 6.1.BETA26 TileMirrorEssentia.
 *
 * The original mirror is NOT a jar: it has no local essentia buffer. It
 * transfers at most one unit through its reciprocal partner, looking for
 * IAspectSource within radius 8 of the other mirror. Both mirror chunks
 * must be loaded; a remote destination is never force-loaded.
 *
 * The NeoForge world adapter must validate/commit the actual remote
 * IEssentiaTransportPort before returning success, to prevent duplication.
 */
public final class LegacyEssentiaMirrorLogic {
    private LegacyEssentiaMirrorLogic() {}

    public static final int TRANSFER_MAX = 1;
    public static final int SEARCH_RANGE = 8;
    public static final int LINK_RETRY_START = 40;
    public static final int LINK_RETRY_MAX = 600;
    public static final int LINK_RETRY_STEP = 20;
    public static final int INSTABILITY_THRESHOLD = 64;
    public static final int INSTABILITY_DECAY_INTERVAL = 100;

    public record Endpoint(String dimension, int x, int y, int z) {
        public Endpoint {
            if (dimension == null || dimension.isBlank())
                throw new IllegalArgumentException("Mirror dimension is required");
        }
    }

    public record Link(Endpoint destination, boolean linked) {}

    /** TileMirrorEssentia.isLinkValidSimple: both ends point at each other. */
    public static boolean reciprocal(Endpoint self, Link selfLink,
                                     Endpoint other, Link otherLink,
                                     boolean otherWorldAndChunkLoaded) {
        return self != null && other != null && selfLink != null
                && otherLink != null && selfLink.linked() && otherLink.linked()
                && otherWorldAndChunkLoaded
                && !self.equals(other)
                && other.equals(selfLink.destination())
                && self.equals(otherLink.destination());
    }

    /** One active endpoint may link to an available, currently unpaired target. */
    public static boolean canRestore(Endpoint self, Endpoint destination,
                                     boolean worldAndChunkLoaded,
                                     boolean destinationIsSameKind,
                                     boolean destinationAlreadyValidlyLinked) {
        return self != null && destination != null && !self.equals(destination)
                && worldAndChunkLoaded && destinationIsSameKind
                && !destinationAlreadyValidlyLinked;
    }

    /** Original addToContainer/takeFromContainer refuse batches bigger than one. */
    public static boolean canTransfer(int amount, boolean reciprocalLink,
                                      boolean partnerFound, boolean partnerCanTransfer) {
        return amount == TRANSFER_MAX && reciprocalLink
                && partnerFound && partnerCanTransfer;
    }

    /** Source TileMirrorEssentia: decaying retry backoff 40,60,...600. */
    public static int nextLinkCheckInterval(int current, boolean validLink) {
        if (validLink) return LINK_RETRY_START;
        if (current < LINK_RETRY_START) current = LINK_RETRY_START;
        return Math.min(LINK_RETRY_MAX, current + LINK_RETRY_STEP);
    }

    public record Instability(int value, int emittedFlux) {
        public Instability {
            if (value < 0 || emittedFlux < 0) throw new IllegalArgumentException();
        }
    }

    /**
     * One server tick of checkInstability(). BETA26 emits one flux if >64,
     * subtracts 64 (not 65) and decays by one on every 100th tick.
     * The transport adapter must increment instability ONLY after a
     * confirmed successful one-unit debit/credit.
     */
    public static Instability checkInstability(int instability, long tick) {
        if (instability < 0 || tick < 0)
            throw new IllegalArgumentException("negative mirror state");
        int flux = instability > INSTABILITY_THRESHOLD ? 1 : 0;
        int remaining = instability - flux * INSTABILITY_THRESHOLD;
        if (remaining > 0 && tick % INSTABILITY_DECAY_INTERVAL == 0)
            remaining--;
        return new Instability(remaining, flux);
    }

    /** Only a confirmed successful one-unit transaction increases instability. */
    public static int afterTransfer(int instability, int amount, boolean completed) {
        if (instability < 0 || amount < 0 || amount > TRANSFER_MAX)
            throw new IllegalArgumentException("invalid transfer");
        return completed && amount == TRANSFER_MAX
                ? Math.addExact(instability, 1) : instability;
    }
}
