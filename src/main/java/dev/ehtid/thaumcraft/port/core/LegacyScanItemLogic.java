package dev.ehtid.thaumcraft.port.core;

import java.util.Map;
import java.util.Objects;

/** Loader-neutral projection of ScanItem + ThaumcraftInvHelper crafting equality quirks. */
public final class LegacyScanItemLogic {
    public static final int WILDCARD_META = 32767;

    private LegacyScanItemLogic() {}

    /**
     * The template is the second argument in BETA26 areItemStacksEqualForCrafting.
     * Its tags are requirements; extra tags on the scanned stack are ignored.
     */
    public static boolean matches(String scannedItemId, int scannedMeta, Map<String, String> scannedTags,
                                  String templateItemId, int templateMeta, Map<String, String> templateTags) {
        if (!Objects.equals(scannedItemId, templateItemId)) return false;
        if (!templateTagsMatch(scannedTags, templateTags)) return false;
        return templateMeta == WILDCARD_META || templateMeta == scannedMeta;
    }

    /** BETA26 compares NBTBase#toString values and only iterates keys present on the template. */
    public static boolean templateTagsMatch(Map<String, String> scannedTags, Map<String, String> templateTags) {
        if (templateTags == null) return true;
        if (scannedTags == null) return false;
        for (Map.Entry<String, String> e : templateTags.entrySet()) {
            if (!scannedTags.containsKey(e.getKey())) return false;
            if (!Objects.equals(scannedTags.get(e.getKey()), e.getValue())) return false;
        }
        return true;
    }
}
