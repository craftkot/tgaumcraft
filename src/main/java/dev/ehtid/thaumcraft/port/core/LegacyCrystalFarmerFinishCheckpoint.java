package dev.ehtid.thaumcraft.port.core;

import java.util.Objects;

/**
 * A deliberately conservative two-stage completion barrier for the seven
 * original BETA26 Crystal Farmer outputs. A COMPLETION RESERVATION is written
 * to the matrix before the real center pedestal is replaced, and cannot be
 * replayed. In the event of a power loss mid-commit a reserved output may
 * require operator repair, but cannot be minted a second time automatically.
 *
 * Unlike the original 1.12 in-memory TileInfusionMatrix, our 1.21 world save
 * does not offer atomic transactions across different block entities.
 * Reserve-before-publish chooses "at most once" over duplication.
 */
public final class LegacyCrystalFarmerFinishCheckpoint {
    private LegacyCrystalFarmerFinishCheckpoint() {}

    public enum Phase { WAITING, RESERVED, STALLED, FINISHED }

    public record State(Phase phase) {
        public State { Objects.requireNonNull(phase); }
        public boolean canReserve() { return phase == Phase.WAITING; }
        public boolean mayPublishOneItem() { return phase == Phase.RESERVED; }
        public boolean finished() { return phase == Phase.FINISHED; }
        public State reserve(boolean allEssentiaPaid, boolean twoItemsConsumed,
                             boolean centralCrystalVerified) {
            if (!canReserve() || !allEssentiaPaid || !twoItemsConsumed
                    || !centralCrystalVerified)
                throw new IllegalStateException("Unpaid, mismatched, or already reserved");
            return new State(Phase.RESERVED);
        }
        public State finishAfterActualCenterReplacement(boolean replacementSucceeded) {
            if (!mayPublishOneItem() || !replacementSucceeded)
                throw new IllegalStateException("Cannot complete an unpaid/replayed output");
            return new State(Phase.FINISHED);
        }
    }

    public static State initial() { return new State(Phase.WAITING); }

    /**
     * Important: a saved RESERVED state is not permission to mint an output.
     * A chunk may have saved its reservation before the center write;
     * recovery must stop instead of guessing whether delivery happened.
     * Source operators can inspect STALLED, but automation cannot replay it.
     */
    public static State restore(String saved) {
        try {
            Phase state = Phase.valueOf(saved);
            return new State(state == Phase.RESERVED ? Phase.STALLED : state);
        } catch (RuntimeException bad) {
            throw new IllegalArgumentException("Bad finish phase", bad);
        }
    }
}
