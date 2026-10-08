package dev.ehtid.thaumcraft.port.core;

import java.util.List;
import java.util.Objects;

/**
 * Legacy 1.12 item registry identity and damage metadata for vanilla variants
 * split into separate item ids by the flattening. Used for ScanGeneric keys;
 * this does not change the physical NeoForge ItemStack.
 */
public final class LegacyVanillaItemIdentity {
    private LegacyVanillaItemIdentity() {}

    public record Identity(String itemId, int metadata) {}

    private static final List<String> DYE_COLORS = List.of(
            "white", "orange", "magenta", "light_blue", "yellow", "lime",
            "pink", "gray", "light_gray", "cyan", "purple", "blue",
            "brown", "green", "red", "black");

    public static Identity resolve(String modernId) {
        Objects.requireNonNull(modernId, "modernId");
        if (!modernId.startsWith("minecraft:")) return new Identity(modernId, 0);
        String name = modernId.substring("minecraft:".length());
        // 1.12 dye item metadata used the inverse order of block dye colors.
        if ("lapis_lazuli".equals(name)) return new Identity("minecraft:dye", 4);
        if ("cocoa_beans".equals(name)) return new Identity("minecraft:dye", 3);
        if ("ink_sac".equals(name)) return new Identity("minecraft:dye", 0);
        if ("bone_meal".equals(name)) return new Identity("minecraft:dye", 15);
        if ("terracotta".equals(name)) return new Identity("minecraft:hardened_clay", 0);
        for (int meta = 0; meta < DYE_COLORS.size(); meta++) {
            String color = DYE_COLORS.get(meta);
            if (name.equals(color + "_wool")) return new Identity("minecraft:wool", meta);
            if (name.equals(color + "_carpet")) return new Identity("minecraft:carpet", meta);
            if (name.equals(color + "_terracotta")) return new Identity("minecraft:stained_hardened_clay", meta);
            if (name.equals(color + "_stained_glass")) return new Identity("minecraft:stained_glass", meta);
            if (name.equals(color + "_stained_glass_pane")) return new Identity("minecraft:stained_glass_pane", meta);
            if (name.equals(color + "_concrete")) return new Identity("minecraft:concrete", meta);
            if (name.equals(color + "_concrete_powder")) return new Identity("minecraft:concrete_powder", meta);
            if (name.equals(color + "_dye")) return new Identity("minecraft:dye", 15 - meta);
        }
        return new Identity(modernId, 0);
    }

    public static String genericScanKey(String modernId, boolean damageable) {
        Identity identity = resolve(modernId);
        return GenericScanKeyLogic.forItem(identity.itemId(), damageable, identity.metadata());
    }
}
