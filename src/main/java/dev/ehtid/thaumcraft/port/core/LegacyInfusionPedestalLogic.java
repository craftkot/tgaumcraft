package dev.ehtid.thaumcraft.port.core;

/** BETA26 BlockPedestal/TilePedestal source-derived inventory/stabilizer rules. */
public final class LegacyInfusionPedestalLogic {
    public static final int SLOT_COUNT = 1;
    public static final int SLOT_LIMIT = 1;
    public static final int MIN_CHARGE = 0;
    public static final int MAX_CHARGE = 15;
    public static final float ELDRITCH_STABILIZATION = 0.1F;
    public static final float OTHER_STABILIZATION = 0.0F;
    public static final float SYMMETRY_PENALTY = 0.1F;

    private LegacyInfusionPedestalLogic() {}

    public enum Variant { ARCANE, ANCIENT, ELDRITCH }

    public static float stabilizingStrength(Variant v) {
        return v == Variant.ELDRITCH ? ELDRITCH_STABILIZATION : OTHER_STABILIZATION;
    }

    /** Missing mirrors aren't scored here; caller checks both blocks present. */
    public static boolean hasSymmetryPenalty(boolean firstOccupied, boolean secondOccupied) {
        return firstOccupied != secondOccupied;
    }

    public static boolean canInsert(boolean slotEmpty, int heldCount) {
        return slotEmpty && heldCount > 0;
    }
}
