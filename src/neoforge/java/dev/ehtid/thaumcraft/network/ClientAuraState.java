package dev.ehtid.thaumcraft.network;

/**
 * Client mirror of BETA26 HudHandler.currentAura. It intentionally stores only
 * the latest PacketAuraToClient triple; rendering stays client-only and can be
 * ported without changing the wire format.
 */
public final class ClientAuraState {
    private ClientAuraState() {}

    public record Snapshot(short base, float vis, float flux) {}

    private static volatile Snapshot current;

    public static void set(short base, float vis, float flux) {
        current = new Snapshot(base, vis, flux);
    }

    public static Snapshot current() {
        return current;
    }

    public static void clear() {
        current = null;
    }
}
