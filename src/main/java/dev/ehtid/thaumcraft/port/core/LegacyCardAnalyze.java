package dev.ehtid.thaumcraft.port.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Original BETA26 CardAnalyze, with one targeted original bug fix.
 *
 * The 1.12 initialize bytecode loops over `ResearchCategories.values()`,
 * skipping BASICS, but mistakenly calls getKnowledge(OBSERVATION,
 * ResearchCategories.get(card.cat)) while card.cat is still NULL instead of
 * the category under inspection. That incorrectly tests GENERAL observation
 * for every category and often rejects a valid analysis entirely.
 *
 * We use the loop's actual category key as intended. Costs and progression
 * remain original: 2 inspiration, consumes 16 raw OBSERVATION from chosen
 * category, adds +5 BASICS, and +25..50 category theory.
 */
public final class LegacyCardAnalyze {
    private LegacyCardAnalyze() {}

    public static final String CARD_KEY =
            "thaumcraft.api.research.theorycraft.CardAnalyze";
    public static final int INSPIRATION_COST = 2;

    /** Stable original insertion order is supplied by ResearchCategoryRegistry. */
    public static String initialize(long seed, PlayerKnowledgeData playerKnowledge,
                                    ResearchCategoryRegistry categories) {
        Objects.requireNonNull(playerKnowledge);
        Objects.requireNonNull(categories);
        List<String> eligible = new ArrayList<>();
        for (ResearchCategoryData category : categories.values()) {
            if ("BASICS".equals(category.key())) continue;
            // BUGFIX: BETA26 had getKnowledge(OBS, get(card.cat)) here,
            // although card.cat remained null while building eligible list.
            if (playerKnowledge.getKnowledge(KnowledgeType.OBSERVATION,
                    category.key()) > 0) eligible.add(category.key());
        }
        if (eligible.isEmpty()) return null;
        return eligible.get(new Random(seed).nextInt(eligible.size()));
    }

    public static boolean activate(String chosenCategory,
                                   PlayerKnowledgeData playerKnowledge,
                                   LegacyResearchTableData table,
                                   LegacyAdditionalTheoryCards.BoundedRandom random) {
        Objects.requireNonNull(playerKnowledge);
        Objects.requireNonNull(table);
        Objects.requireNonNull(random);
        if (chosenCategory == null || chosenCategory.isBlank()
                || "BASICS".equals(chosenCategory)) return false;
        if (playerKnowledge.getKnowledge(KnowledgeType.OBSERVATION,
                chosenCategory) < 1) return false;
        // Subtract the actual BETA26 single-unit raw progression (16), not 1
        // raw point; addKnowledge rejects negative outcomes atomically.
        if (!playerKnowledge.addKnowledge(KnowledgeType.OBSERVATION,
                chosenCategory, -KnowledgeType.OBSERVATION.progression()))
            return false;
        table.addTotal("BASICS", 5);
        table.addTotal(chosenCategory, 25 + random.nextInt(26));
        return true;
    }
}
