package dev.ehtid.thaumcraft.port.core;

/** BETA26 EntityWisp.getCanSpawnHere/isValidLightLevel spawn gate. */
public final class LegacyWispSpawnRules {
    private LegacyWispSpawnRules() {}

    public static boolean canSpawn(
            boolean peaceful,
            int nearbyWisps,
            int skyLight,
            int skyRoll,
            int localLight,
            int localRoll
    ) {
        if (nearbyWisps < 0 || skyLight < 0 || skyLight > 15 || skyRoll < 0 || skyRoll >= 32
                || localLight < 0 || localLight > 15 || localRoll < 0 || localRoll >= 8) {
            throw new IllegalArgumentException("Wisp spawn inputs are outside Minecraft light/RNG ranges");
        }
        return !peaceful && nearbyWisps < 8 && skyLight <= skyRoll && localLight <= localRoll;
    }
}
