package dev.ehtid.thaumcraft.port.core.golem;

/** BETA26 EntityThaumcraftGolem.createAI / AIFollowOwner and FLAGS bit index 1. */
public final class GolemBellRules {
    private GolemBellRules() {}
    public static final String RESEARCH = "GOLEMDIRECT";
    public static final int FOLLOW_MASK = 2;
    public static final double START_DISTANCE_SQUARED = 100, STOP_DISTANCE_SQUARED = 4;
    public static final double TELEPORT_DISTANCE_SQUARED = 144, SPEED = 1, SEAL_REACH_SQUARED = 25;
    public static final int REPATH_TICKS = 10;
    public static boolean following(byte flags) { return (flags & FOLLOW_MASK) != 0; }
    public static byte following(byte flags, boolean value) {
        return (byte) (value ? flags | FOLLOW_MASK : flags & ~FOLLOW_MASK);
    }
}
