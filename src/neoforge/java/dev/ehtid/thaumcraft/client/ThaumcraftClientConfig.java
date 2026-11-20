package dev.ehtid.thaumcraft.client;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client-only graphics preferences ported from Thaumcraft 6.1.BETA26 CONFIG_GRAPHICS.
 */
public final class ThaumcraftClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue CROOKED_JAR_LABELS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("graphics");
        CROOKED_JAR_LABELS = builder
                .comment("Apply the original BETA26 crooked rotation to both essentia jar labels and aspect icons.")
                .define("crooked", true);
        builder.pop();
        SPEC = builder.build();
    }

    private ThaumcraftClientConfig() {}
}
