package dev.ehtid.thaumcraft.port.core;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Compatibility mapping for legacy 1.12 resource ids whose paths are illegal in modern ResourceLocation.
 *
 * This does not guess registry contents. It only supplies a deterministic modern path and collision checks;
 * callers still own the explicit registration/recipe mapping.
 */
public final class LegacyResourceIdCompat {
    private final Map<String, String> legacyToModern = new LinkedHashMap<>();
    private final Map<String, String> modernToLegacy = new LinkedHashMap<>();

    public String map(String legacyId) {
        return legacyToModern.computeIfAbsent(legacyId, this::createCheckedMapping);
    }

    public String lookup(String legacyId) {
        return legacyToModern.get(legacyId);
    }

    public Map<String, String> mappings() {
        return Map.copyOf(legacyToModern);
    }

    public static boolean isModernResourceId(String id) {
        if (id == null || id.isEmpty()) return false;
        int colon = id.indexOf(':');
        String namespace = colon < 0 ? "minecraft" : id.substring(0, colon);
        String path = colon < 0 ? id : id.substring(colon + 1);
        return namespace.matches("[a-z0-9_.-]+") && path.matches("[a-z0-9/._-]+");
    }

    /** Conservative conversion: lowercase only. No camelCase-to-snake_case invention. */
    public static String canonicalLowercase(String legacyId) {
        int colon = legacyId.indexOf(':');
        if (colon < 0) return legacyId.toLowerCase(Locale.ROOT);
        return legacyId.substring(0, colon).toLowerCase(Locale.ROOT) + ":"
                + legacyId.substring(colon + 1).toLowerCase(Locale.ROOT);
    }

    private String createCheckedMapping(String legacyId) {
        String modern = canonicalLowercase(legacyId);
        if (!isModernResourceId(modern)) {
            throw new IllegalArgumentException("Cannot normalize legacy resource id safely: " + legacyId);
        }
        String previous = modernToLegacy.putIfAbsent(modern, legacyId);
        if (previous != null && !previous.equals(legacyId)) {
            throw new IllegalStateException("Legacy id collision: " + previous + " and " + legacyId + " -> " + modern);
        }
        return modern;
    }
}
