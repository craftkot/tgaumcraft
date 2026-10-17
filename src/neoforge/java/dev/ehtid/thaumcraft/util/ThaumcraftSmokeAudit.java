package dev.ehtid.thaumcraft.util;

/** Opt-in diagnostics for the REAL CircleCI Minecraft client, never active in player runs.
 * Markers are emitted only after authoritative server-side transitions, so a
 * successful shell click/typed chat command cannot counterfeit gameplay. */
public final class ThaumcraftSmokeAudit {
    private static final boolean ENABLED = "1".equals(System.getenv("THAUMCRAFT_SMOKE"));
    private ThaumcraftSmokeAudit() {}
    public static void mark(String marker) {
        if (ENABLED) System.out.println("TC_SMOKE_" + marker + "=YES");
    }
}
