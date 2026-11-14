package dev.ehtid.thaumcraft.port.core.golem;

/** BETA26 ConfigAspects.dyes (black..white), EntityThaumcraftGolem.processInteract. */
public final class GolemColorRules {
    private GolemColorRules() {}

    /** Zero is the unpainted wildcard, never produced by a dye. */
    public static byte fromLegacyDyeIndex(int index) {
        if (index < 0 || index > 15) throw new IllegalArgumentException("Invalid legacy dye index: " + index);
        return (byte) (16 - index);
    }

    /** Modern DyeColor IDs run white..black, the reverse of the original dye list. */
    public static byte fromModernDyeId(int id) {
        if (id < 0 || id > 15) throw new IllegalArgumentException("Invalid modern dye ID: " + id);
        return fromLegacyDyeIndex(15 - id);
    }
}
