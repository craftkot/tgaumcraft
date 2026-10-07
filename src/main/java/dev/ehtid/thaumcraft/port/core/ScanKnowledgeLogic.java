package dev.ehtid.thaumcraft.port.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Loader-neutral knowledge side effects used by BETA26 ScanGeneric and ScanAspect. */
public final class ScanKnowledgeLogic {
    private ScanKnowledgeLogic() {}

    /** Raw knowledge delta: unlike research reward_knowledge JSON, this amount is already in raw points. */
    public record RawKnowledgeGain(KnowledgeType type, String categoryKey, int rawAmount) {}

    /** ScanGeneric.onSuccess calls addKnowledge for every registered category, including categories whose formula yields 0. */
    public static List<RawKnowledgeGain> genericObservationGains(
            AspectListData scannedAspects, ResearchCategoryRegistry categories) {
        Objects.requireNonNull(scannedAspects, "scannedAspects");
        Objects.requireNonNull(categories, "categories");
        List<RawKnowledgeGain> out = new ArrayList<>();
        for (ResearchCategoryData category : categories.values()) {
            out.add(new RawKnowledgeGain(
                    KnowledgeType.OBSERVATION,
                    category.key(),
                    category.applyFormula(scannedAspects)));
        }
        return List.copyOf(out);
    }

    /** ScanAspect.onSuccess always attempts +1 raw Observation in these three categories, in this exact order. */
    public static List<RawKnowledgeGain> aspectDiscoveryGains() {
        return List.of(
                new RawKnowledgeGain(KnowledgeType.OBSERVATION, "AUROMANCY", 1),
                new RawKnowledgeGain(KnowledgeType.OBSERVATION, "BASICS", 1),
                new RawKnowledgeGain(KnowledgeType.OBSERVATION, "ALCHEMY", 1));
    }
}
