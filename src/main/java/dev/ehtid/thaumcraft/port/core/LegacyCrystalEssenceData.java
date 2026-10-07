package dev.ehtid.thaumcraft.port.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/** BETA26 crystal_essence legacy Aspects payload kept inside modern custom data. */
public final class LegacyCrystalEssenceData {
    private LegacyCrystalEssenceData() {}
    public static Map<String,Object> singleAspectPayload(String tag) {
        Objects.requireNonNull(tag, "tag");
        Map<String,Object> row = new LinkedHashMap<>(); row.put("key", tag); row.put("amount", 1);
        Map<String,Object> root = new LinkedHashMap<>(); root.put("Aspects", List.of(row)); return root;
    }
    @SuppressWarnings("unchecked")
    public static String singleAspectTag(Map<String,?> customData) {
        if (customData == null) return null;
        Object raw = customData.get("Aspects");
        if (!(raw instanceof List<?> l) || l.isEmpty() || !(l.get(0) instanceof Map<?,?> m)) return null;
        Object key=m.get("key"); return key instanceof String s ? s : null;
    }
    public static String assignRandomIfAspectless(Map<String,Object> customData, List<String> registeredTags, Random random) {
        String old=singleAspectTag(customData); if (old!=null) return old;
        if (registeredTags==null || registeredTags.isEmpty()) return null;
        String tag=registeredTags.get(random.nextInt(registeredTags.size()));
        customData.clear(); customData.putAll(singleAspectPayload(tag)); return tag;
    }
}
