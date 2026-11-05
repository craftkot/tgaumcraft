package dev.ehtid.thaumcraft.port.core;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * BETA26 TileInfusionMatrix.craftCycle() instability gate and event parameters.
 * World mutation belongs to the server adapter. In particular, execute the
 * selected event with the SAME random source before calling recoverAfterEvent;
 * its intervening random calls are part of the original sequence.
 */
public final class LegacyInfusionInstability {
    private LegacyInfusionInstability() {}

    public static final int CHANCE_BOUND = 1500;
    public static final int EVENT_COUNT = 24;
    public static final int EJECTION_ATTEMPTS = 25;
    /** Inflate the unit block AABB by this amount; this is not a sphere. */
    public static final int TARGET_RANGE = 10;
    public static final int FX_RANGE = 32;
    public static final int MITIGATED_BLOCK_EVENT = 5;
    public static final int EJECTED_BLOCK_EVENT = 11;
    public static final boolean EXPLOSION_DAMAGES_BLOCKS = false;
    public static final String RESEARCH_KEY = "!INSTABILITY";

    /** Bridge directly to the real server world's RNG in production. */
    public interface RandomSource {
        int nextInt(int bound);
        float nextFloat();
        boolean nextBoolean();
    }

    public enum Event {
        NONE(-1, false),
        EJECT_ITEM(0, false),
        WARP(-1, false),
        ZAP_ONE(-1, false),
        ZAP_ALL(-1, true),
        EJECT_ITEM_GOO(1, false),
        EJECT_ITEM_FLUX(2, false),
        EJECT_ITEM_GOO_ALT(3, false),
        DESTROY_ITEM_FLUX(4, false),
        HARM_ONE(-1, false),
        EJECT_ITEM_EXPLOSION(5, false),
        HARM_ALL(-1, true),
        MATRIX_EXPLOSION(-1, false);

        private final int ejectionMode;
        private final boolean allTargets;
        Event(int ejectionMode, boolean allTargets) {
            this.ejectionMode = ejectionMode;
            this.allTargets = allTargets;
        }
        public int ejectionMode() { return ejectionMode; }
        /** Zap/harm ONE uses the first world query entry, without shuffling. */
        public boolean allTargets() { return allTargets; }
    }

    /**
     * An event consumes this craft cycle, even if no entity/pedestal was found.
     * Invalid central input executes its forced event before aborting the recipe.
     */
    public record Cycle(float stability, Event event, boolean centralInputValid) {
        public Cycle {
            finite(stability);
            Objects.requireNonNull(event, "event");
        }
        public boolean continueRecipe() { return centralInputValid && event == Event.NONE; }
        public boolean abortRecipe() { return !centralInputValid; }
    }

    /** Source craftCycle offsets 0..217, reusing the existing loss/rating math. */
    public static Cycle beginCycle(float current, int recipeInstability, float replenish,
                                   int cap, boolean centralInputValid, RandomSource random) {
        Objects.requireNonNull(random, "random");
        float stability = LegacyInfusionStability.afterCraftCycle(current, recipeInstability,
                replenish, cap, random.nextFloat());
        return new Cycle(stability, selectEvent(stability, centralInputValid, random),
                centralInputValid);
    }

    /**
     * Accepts stability AFTER the craft-cycle loss/replenishment/clamp.
     * For invalid input the original skips the 1500-way roll completely.
     * For valid input the comparison is inclusive: -0.01 can trigger on zero.
     */
    public static Event selectEvent(float stability, boolean centralInputValid,
                                     RandomSource random) {
        finite(stability);
        Objects.requireNonNull(random, "random");
        if (centralInputValid && (stability >= 0
                || random.nextInt(CHANCE_BOUND) > Math.abs(stability))) return Event.NONE;
        return switch (random.nextInt(EVENT_COUNT)) {
            case 0, 1, 2, 3 -> Event.EJECT_ITEM;
            case 4, 5, 6 -> Event.WARP;
            case 7, 8, 9 -> Event.ZAP_ONE;
            case 10, 11 -> Event.ZAP_ALL;
            case 12, 13 -> Event.EJECT_ITEM_GOO;
            case 14, 15 -> Event.EJECT_ITEM_FLUX;
            case 16 -> Event.EJECT_ITEM_GOO_ALT;
            case 17 -> Event.DESTROY_ITEM_FLUX;
            case 18, 19 -> Event.HARM_ONE;
            case 20, 21 -> Event.EJECT_ITEM_EXPLOSION;
            case 22 -> Event.HARM_ALL;
            case 23 -> Event.MATRIX_EXPLOSION;
            default -> throw new IllegalArgumentException("Random event outside bound");
        };
    }

    /** Source craftCycle offsets 479..506. Deliberately NOT clamped to the cap. */
    public static float recoverAfterEvent(float stability, RandomSource random) {
        finite(stability);
        return stability + (5.0F + nextFloat(random) * 5.0F);
    }

    /**
     * The pool is ALL scanned side pedestals, including empty/non-recipe ones,
     * in the original scan order. Recheck the real tile and slot in occupied.
     * Sampling is with replacement, and a missing/empty tile costs an attempt.
     * null means no target; recovery/research still occur in that case.
     */
    public static <T> T findEjectionPedestal(List<T> scannedPedestals,
                                             Predicate<T> occupied, RandomSource random) {
        Objects.requireNonNull(scannedPedestals, "scannedPedestals");
        Objects.requireNonNull(occupied, "occupied");
        Objects.requireNonNull(random, "random");
        for (int attempt = 0; attempt < EJECTION_ATTEMPTS && !scannedPedestals.isEmpty(); attempt++) {
            T pedestal = scannedPedestals.get(random.nextInt(scannedPedestals.size()));
            if (occupied.test(pedestal)) return pedestal;
        }
        return null;
    }

    /** Apply only after a real stabilizer fails to mitigate, or none exists. */
    public record EjectionEffect(boolean dropItem, boolean placeFluxGoo,
                                  boolean polluteAura, float explosionStrength) {}

    /** Source inEvEjectItem offsets 374..559; mode 4 uniquely destroys the item. */
    public static EjectionEffect ejectionEffect(int mode) {
        if (mode < 0 || mode > 5) throw new IllegalArgumentException("ejection mode");
        return new EjectionEffect(mode != 4, mode == 1 || mode == 3,
                mode == 2 || mode == 4, mode == 5 ? 1.0F : 0.0F);
    }

    /** Call only when a real pedestal mitigator exists; original range is inclusive. */
    public static int rollMitigationCost(RandomSource random) {
        return 5 + Objects.requireNonNull(random, "random").nextInt(6);
    }

    /** polluteAura at the pedestal, with original effect=true. */
    public static int rollFluxPollution(RandomSource random) {
        return 5 + Objects.requireNonNull(random, "random").nextInt(5);
    }

    public enum WarpType { NORMAL, TEMPORARY }
    public record Warp(WarpType type, int amount) {}

    /** Call AFTER selecting one real nearby player using nextInt(playerCount). */
    public static Warp rollWarp(RandomSource random) {
        if (nextFloat(random) < 0.25F) return new Warp(WarpType.NORMAL, 1);
        return new Warp(WarpType.TEMPORARY, 2 + random.nextInt(4));
    }

    public enum Harm {
        FLUX_TAINT(120, false, false),
        VIS_EXHAUST(2400, true, true);

        private final int duration;
        private final boolean ambient;
        private final boolean clearCuratives;
        Harm(int duration, boolean ambient, boolean clearCuratives) {
            this.duration = duration;
            this.ambient = ambient;
            this.clearCuratives = clearCuratives;
        }
        public int duration() { return duration; }
        public int amplifier() { return 0; }
        public boolean ambient() { return ambient; }
        public boolean visible() { return true; }
        public boolean clearCuratives() { return clearCuratives; }
    }

    /** One independent boolean per affected living entity. */
    public static Harm rollHarm(RandomSource random) {
        return Objects.requireNonNull(random, "random").nextBoolean()
                ? Harm.FLUX_TAINT : Harm.VIS_EXHAUST;
    }

    /** Magic damage; roll AFTER that target's arc color. */
    public static int rollZapDamage(RandomSource random) {
        return 4 + Objects.requireNonNull(random, "random").nextInt(4);
    }

    public record ArcColor(float red, float green, float blue) {}

    /** Each arc consumes two float rolls, even in a server without FX clients. */
    public static ArcColor rollArcColor(RandomSource random) {
        return new ArcColor(0.3F - nextFloat(random) * 0.1F, 0,
                0.3F - nextFloat(random) * 0.1F);
    }

    /** Matrix center + (0.5,0.5,0.5), null explosion source, terrain=false. */
    public static float rollMatrixExplosionStrength(RandomSource random) {
        return 1.5F + nextFloat(random);
    }

    private static float nextFloat(RandomSource random) {
        float value = Objects.requireNonNull(random, "random").nextFloat();
        if (!Float.isFinite(value) || value < 0 || value >= 1)
            throw new IllegalArgumentException("Random float outside [0,1)");
        return value;
    }
    private static void finite(float value) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException("Non-finite stability");
    }
}
