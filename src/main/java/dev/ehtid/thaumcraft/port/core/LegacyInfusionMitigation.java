package dev.ehtid.thaumcraft.port.core;

/** BETA26 TileStabilizer recharge/debit and BlockInlay charge propagation. */
public final class LegacyInfusionMitigation {
    private LegacyInfusionMitigation() {}

    public static final int CAPACITY = 15;
    public static final int RECHARGE_TICKS = 20;
    public static final float FLUX_PER_CHARGE = 0.25F;

    public record Recharge(int energy, float pollution) {}

    public static Recharge recharge(int energy, int ticks) {
        checkCharge(energy);
        return energy < CAPACITY && ticks > 0 && ticks % RECHARGE_TICKS == 0
                ? new Recharge(energy + 1, FLUX_PER_CHARGE)
                : new Recharge(energy, 0.0F);
    }

    /** Failed mitigation leaves the physical device's energy unchanged. */
    public static int afterMitigation(int energy, int cost) {
        checkCharge(energy);
        if (cost <= 0) throw new IllegalArgumentException("Mitigation cost must be positive");
        return energy >= cost ? energy - cost : energy;
    }

    /**
     * Equivalent to BETA26 BlockInlay.calculateChanges, with its arguments
     * pos1=pos2 (the updateSurroundingInlay entry point). A directly adjacent
     * source retains its energy; every inlay/pedestal hop loses one charge.
     * Old charge decays one step per notification after losing its source.
     */
    public static int nextCharge(int current, int source, int neighborMaximum) {
        checkCharge(current);
        checkCharge(source);
        checkCharge(neighborMaximum);
        return Math.max(source, Math.max(0, Math.max(current, neighborMaximum) - 1));
    }

    private static void checkCharge(int charge) {
        if (charge < 0 || charge > CAPACITY) throw new IllegalArgumentException("charge");
    }
}
