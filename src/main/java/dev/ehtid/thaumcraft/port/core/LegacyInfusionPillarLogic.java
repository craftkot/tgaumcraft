package dev.ehtid.thaumcraft.port.core;

/**
 * Disassembled from Thaumcraft 6.1.BETA26 BlockPillar.
 * Selection AABB: (0,0,0)-(1,0.5,1).
 * Collision AABB: (0,0,0)-(1,2,1).
 * Horizontal facing is the opposite of placement-player facing.
 */
public final class LegacyInfusionPillarLogic {
    private LegacyInfusionPillarLogic() {}
    public enum Variant { ARCANE, ANCIENT, ELDRITCH }
    public static final int ORIGINAL_HARDNESS_TENTHS = 25;
    public static final int ORIGINAL_STONE_DROP_COUNT = 2;
    public static final double SELECTION_HEIGHT = 0.5;
    public static final double COLLISION_HEIGHT = 2.0;
    public static int oppositeHorizontal(int horizontal) {
        if (horizontal < 0 || horizontal > 3)
            throw new IllegalArgumentException("horizontal must be 0..3");
        return (horizontal + 2) & 3;
    }
    public static String originalDrop(Variant type) {
        return switch (type) {
            case ARCANE -> "thaumcraft:stone_arcane";
            case ANCIENT -> "thaumcraft:stone_ancient";
            case ELDRITCH -> "thaumcraft:stone_eldritch_tile";
        };
    }
}
