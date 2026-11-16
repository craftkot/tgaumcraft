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

    /**
     * Source EssentiaHandler.getSources(null facing, range=8) enumerates a
     * rectangular box, not a Euclidean sphere: x/z are inclusive [-8,8],
     * Y is asymmetric [-8,7], and the partner's own block is excluded.
     * Re-validate cached candidates against the CURRENT partner position;
     * otherwise a link changed via NBT in the same tick could relay from an
     * unrelated old source that is still a live BlockEntity.
     */
    public static boolean inPartnerSearchBox(int dx, int dy, int dz) {
        int r = SEARCH_RANGE;
        return dx >= -r && dx <= r && dy >= -r && dy < r
                && dz >= -r && dz <= r
                && (dx != 0 || dy != 0 || dz != 0);
    }

    public record Endpoint(String dimension, int x, int y, int z) {
        public Endpoint {
            if (dimension == null || dimension.isBlank())
                throw new IllegalArgumentException("Mirror dimension is required");
        }
    }

    public record Link(Endpoint destination, boolean linked) {}

    /**
     * Cache hits are valid ONLY for the specific reciprocal partner whose
     * source box was scanned on this same local server tick. Two distinct
     * mirrors can have overlapping 8-block search regions, so "in range"
     * is necessary but insufficient to reuse another partner's result.
     */
    public static boolean sameSourceScan(Endpoint scannedPartner,
                                         Endpoint currentPartner,
                                         long scannedTick, long currentTick) {
        return scannedPartner != null && scannedPartner.equals(currentPartner)
                && scannedTick == currentTick;
    }

    /** Preserve a destination for a placed mirror whose target chunk is still unloaded. */
    public static boolean canRememberDestination(Endpoint self, Endpoint desired,
                                                 boolean dimensionKnown) {
        return dimensionKnown && self != null && desired != null && !self.equals(desired);
    }

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


    /**
     * A mirror is just a relay. Preflight requires one existing unit in a
     * real non-mirror remote source and an exact aspect match if requested.
     */
    public static boolean mayRelayDebit(int requestedAmount, boolean reciprocalLink,
                                         boolean remoteChunkLoaded, boolean realSource,
                                         String availableAspect, int availableAmount,
                                         String requestedAspect) {
        return canTransfer(requestedAmount, reciprocalLink, remoteChunkLoaded, realSource)
                && availableAspect != null && !availableAspect.isBlank()
                && availableAmount > 0
                && (requestedAspect == null || requestedAspect.equals(availableAspect));
    }

    /**
     * Reverse relay is allowed only if the destination has space for exactly
     * one real unit and its pull pressure exceeds the source pressure.
     */
    public static boolean mayRelayCredit(int amount, boolean reciprocalLink,
                                         boolean remoteChunkLoaded, boolean safeSink,
                                         boolean acceptsAspect, String aspect,
                                         int sourceAmount, int sourceSuction,
                                         int destinationSuction) {
        return canTransfer(amount,reciprocalLink,remoteChunkLoaded,safeSink)
                && acceptsAspect && aspect!=null && !aspect.isBlank()
                && sourceAmount>0 && sourceSuction>=0
                && destinationSuction>sourceSuction;
    }

    /**
     * A connected jar must not immediately pull back the very unit this
     * mirror is actively relaying into a stronger-suction remote jar.
     * Applies ONLY to a confirmed local source and real remote sink.
     */
    public static boolean suppressReturnOutput(boolean sourceHasUnit,
                                               boolean remoteSinkAccepts,
                                               int sourceSuction, int sinkSuction) {
        return sourceHasUnit && remoteSinkAccepts
                && sourceSuction>=0 && sinkSuction>sourceSuction;
    }

    /**
     * Post-commit audit for one real essentia unit. Useful in a Minecraft
     * smoke: a callback's success alone cannot prove that BOTH endpoints
     * changed by precisely one with no extra credit or duplicated debit.
     * Numbers must be read from the live source/recipient AFTER commit.
     * This method never changes the world and cannot substitute for it.
     */
    public static boolean conservedOneUnit(int sourceBefore, int sourceAfter,
                                           int sinkBefore, int sinkAfter) {
        return sourceBefore > 0 && sinkBefore >= 0
                && sourceAfter == sourceBefore - 1
                && sinkAfter == sinkBefore + 1;
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
