package dev.ehtid.thaumcraft.port.core;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/** Insertion-ordered category registry mirroring TC6 ResearchCategories ordering behavior. */
public final class ResearchCategoryRegistry {
    private final Map<String, ResearchCategoryData> categories = new LinkedHashMap<>();

    public ResearchCategoryRegistry register(ResearchCategoryData category) {
        if (categories.putIfAbsent(category.key(), category) != null) {
            throw new IllegalArgumentException("Duplicate research category: " + category.key());
        }
        return this;
    }

    public ResearchCategoryData get(String key) {
        return categories.get(key);
    }

    public boolean contains(String key) {
        return categories.containsKey(key);
    }

    public Collection<ResearchCategoryData> values() {
        return categories.values();
    }

    public int size() {
        return categories.size();
    }
}
