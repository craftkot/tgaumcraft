package dev.ehtid.thaumcraft.port.core;

import java.util.Objects;
import java.util.Set;

/**
 * Exact BETA26 ResearchTableData.getAvailableInspiration / initialize arithmetic.
 *
 * ResearchTableData begins at 5.0 inspiration and adds +0.5 for each STRICTLY
 * completed SPIKY research, +0.1 for each strictly completed HIDDEN research,
 * then returns min(15, round(total)). A completed entry with BOTH meta flags
 * contributes 0.6. Unregistered scan keys do not contribute.
 *
 * initialize subtracts chosen aid COUNT from inspiration, but leaves
 * inspirationStart at the pre-aid number. The source does not clamp below zero.
 */
public final class LegacyTheorycraftInspiration {
    private LegacyTheorycraftInspiration() {}

    public static int available(PlayerKnowledgeData knowledge, ResearchCatalog catalog) {
        Objects.requireNonNull(knowledge, "knowledge");
        Objects.requireNonNull(catalog, "catalog");
        float total = 5.0F;
        for (String key : knowledge.getResearchList()) {
            if (!knowledge.isResearchComplete(key, catalog)) continue;
            ResearchEntryData entry = catalog.get(key);
            if (entry == null) continue;
            if (entry.hasMeta(ResearchMeta.SPIKY)) total += 0.5F;
            if (entry.hasMeta(ResearchMeta.HIDDEN)) total += 0.1F;
        }
        return Math.min(15, Math.round(total));
    }

    public static LegacyResearchTableData start(PlayerKnowledgeData knowledge,
                                                ResearchCatalog catalog,
                                                Set<String> chosenAids) {
        Objects.requireNonNull(chosenAids, "chosenAids");
        LegacyResearchTableData table = new LegacyResearchTableData(available(knowledge, catalog));
        // The vanilla table's ResearchTableData.initialize subtracts the aid
        // SET size, not only the valid aids. Never invent aid card bonuses.
        table.addInspiration(-chosenAids.size());
        return table;
    }
}
