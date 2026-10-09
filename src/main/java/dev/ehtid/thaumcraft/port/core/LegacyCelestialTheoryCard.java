package dev.ehtid.thaumcraft.port.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/**
 * Exact loader-independent port of BETA26 CardCelestial.
 *
 * The original card picks TWO distinct celestial note metadata values (0..12)
 * from new Random(getSeed()); selects the maximum theory category total with
 * TreeMap ascending-key tie ordering; then consumes the two notes on activation.
 *
 * Base gain: player.random.nextInt(26)+25. Any star note (meta 1..4)
 * also rolls random.nextInt(6), adds twice that amount to ELDRITCH theory,
 * and awards the same amount of TEMPORARY warp. A Sun note (meta 0) adds
 * one penaltyStart; any Moon note (meta 5..12) adds one bonusDraws.
 *
 * None of this randomizes the ORIGINAL ScanSky phase-dependent acquisition.
 * The user's requested optional alternative remains a final-polish feature.
 */
public final class LegacyCelestialTheoryCard {
    private LegacyCelestialTheoryCard() {}

    public record Selection(int firstMetadata, int secondMetadata, String category) {
        public Selection {
            if (firstMetadata < 0 || firstMetadata > 12 ||
                    secondMetadata < 0 || secondMetadata > 12 ||
                    firstMetadata == secondMetadata)
                throw new IllegalArgumentException("two distinct BETA26 celestial note variants required");
            Objects.requireNonNull(category, "category");
        }

        public boolean requiresSun() { return firstMetadata == 0 || secondMetadata == 0; }
        public boolean requiresStars() {
            return (firstMetadata > 0 && firstMetadata < 5) ||
                    (secondMetadata > 0 && secondMetadata < 5);
        }
        public boolean requiresMoon() {
            return firstMetadata > 4 || secondMetadata > 4;
        }
    }

    public record Activation(int categoryGain, int eldritchGain, int temporaryWarp,
                             int penaltyStartIncrement, int bonusDrawsIncrement,
                             String category, int[] consumedSlots) {
        public Activation {
            Objects.requireNonNull(category, "category");
            Objects.requireNonNull(consumedSlots, "consumedSlots");
            if (consumedSlots.length != 2 || consumedSlots[0] == consumedSlots[1])
                throw new IllegalArgumentException("two distinct inventory slots required");
            consumedSlots = consumedSlots.clone();
        }

        @Override public int[] consumedSlots() { return consumedSlots.clone(); }
    }

    public static Selection initialize(boolean knowsCelestialScanning,
                                       Map<String, Integer> categoryTotals, long cardSeed) {
        Objects.requireNonNull(categoryTotals, "categoryTotals");
        // CardCelestial.initialize: if categoryTotals.isEmpty() or prerequisite
        // missing, refuse the card before consuming its seeded randomness.
        if (categoryTotals.isEmpty() || !knowsCelestialScanning) return null;

        Random random = new Random(cardSeed);
        int first = random.nextInt(13);
        int second = first;
        while (second == first) second = random.nextInt(13);

        // Original data.categoryTotals is TreeMap, and ties preserve the
        // lexicographically earliest category because comparison is strict >.
        String best = "";
        int maximum = 0;
        for (Map.Entry<String, Integer> entry : categoryTotals.entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).toList()) {
            if (entry.getValue() > maximum) {
                maximum = entry.getValue();
                best = entry.getKey();
            }
        }
        // Original CardCelestial returns cat != null, even if cat is empty.
        return new Selection(first, second, best);
    }

    /**
     * Inventory matcher for the two original note ItemStacks with distinct
     * metadata. Returns first matching slots in inventory iteration order,
     * or null if either note is missing. Reading slots has no side effects.
     */
    public static int[] requiredNoteSlots(Selection selected,
                                          List<Integer> celestialNoteMetadataBySlot) {
        Objects.requireNonNull(selected, "selected");
        Objects.requireNonNull(celestialNoteMetadataBySlot, "celestialNoteMetadataBySlot");
        int firstSlot = -1;
        int secondSlot = -1;
        for (int slot = 0; slot < celestialNoteMetadataBySlot.size(); slot++) {
            Integer metadata = celestialNoteMetadataBySlot.get(slot);
            if (metadata == null) continue;
            if (firstSlot < 0 && metadata == selected.firstMetadata()) firstSlot = slot;
            else if (secondSlot < 0 && metadata == selected.secondMetadata()) secondSlot = slot;
            if (firstSlot >= 0 && secondSlot >= 0) break;
        }
        return firstSlot < 0 || secondSlot < 0 ? null : new int[]{firstSlot, secondSlot};
    }

    /**
     * Calculate activate() effects, retaining the original player's Random
     * stream usage. The loader must debit the required note slots and apply
     * the returned theory totals / temporary warp in the SAME server action.
     */
    @FunctionalInterface
    public interface BoundedRandom {
        int nextInt(int bound);
    }

    public static Activation activate(Selection selected, Random playerRandom, int[] noteSlots) {
        Objects.requireNonNull(playerRandom, "playerRandom");
        return activate(selected, (BoundedRandom) playerRandom::nextInt, noteSlots);
    }

    /**
     * The NeoForge bridge delegates this directly to the living player's
     * RandomSource, preserving the number and order of randomness calls.
     */
    public static Activation activate(Selection selected, BoundedRandom playerRandom,
                                      int[] noteSlots) {
        Objects.requireNonNull(selected, "selected");
        Objects.requireNonNull(playerRandom, "playerRandom");
        if (noteSlots == null || noteSlots.length != 2 || noteSlots[0] == noteSlots[1])
            throw new IllegalArgumentException("both note slots must be available");
        int gain = 25 + playerRandom.nextInt(26);
        int temporaryWarp = selected.requiresStars() ? playerRandom.nextInt(6) : 0;
        return new Activation(gain, temporaryWarp * 2, temporaryWarp,
                selected.requiresSun() ? 1 : 0, selected.requiresMoon() ? 1 : 0,
                selected.category(), noteSlots);
    }
}
