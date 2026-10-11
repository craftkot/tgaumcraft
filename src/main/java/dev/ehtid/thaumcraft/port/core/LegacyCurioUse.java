package dev.ehtid.thaumcraft.port.core;

import java.util.List;
import java.util.Objects;

/**
 * Exact BETA26 ItemCurio right-click knowledge and warp roll order.
 * Original use logic was checked against ItemCurio.func_77659_a bytecode:
 * - metadata 0..5 grants category-specific O [8,16] and T [10,16];
 * - eldritch metadata 3 adds 1 normal + 5 temporary warp;
 * - Crimson Rites metadata 6 requires ACTUAL warp (normal+permanent) > 20,
 *   attempts research completion, grants ELDRITCH O/T, 1 normal and 5
 *   temporary, and then calls nextBoolean() for 1 permanent warp;
 * - after the switch ALL successful curios roll a random category
 *   INDEPENDENTLY for observation and for theory, including the Rites.
 *
 * This pure core operates only on existing knowledge/warp values; the
 * NeoForge caller handles the CrimsonRites completion transition, click
 * sound, inventory consumption and player sync.
 */
public final class LegacyCurioUse {
    private LegacyCurioUse() {}

    public static final List<String> VARIANTS =
            LegacyMaterialTheoryCards.CURIO_VARIANTS;

    public record Effect(boolean consumed, boolean crimsonRitesResearchAttempt,
                         String primaryCategory, String randomObservationCategory,
                         String randomTheoryCategory, int primaryObservation,
                         int primaryTheory, int randomObservation, int randomTheory,
                         boolean permanentWarp) {
        static Effect rejected() {
            return new Effect(false, false, null, null, null, 0, 0, 0, 0, false);
        }
    }

    public interface Source {
        int nextInt(int bound);
        boolean nextBoolean();
    }

    public static Effect activate(int metadata, PlayerKnowledgeData knowledge,
                                  PlayerWarpData warp, List<String> categoryOrder,
                                  Source random) {
        Objects.requireNonNull(knowledge);
        Objects.requireNonNull(warp);
        Objects.requireNonNull(categoryOrder);
        Objects.requireNonNull(random);
        if (metadata < 0 || metadata >= VARIANTS.size())
            throw new IllegalArgumentException("unsupported BETA26 Curio metadata");
        if (categoryOrder.isEmpty()) throw new IllegalArgumentException("no research categories");
        int actualWarp = warp.get(WarpType.NORMAL) + warp.get(WarpType.PERMANENT);
        if (metadata == 6 && actualWarp <= 20) return Effect.rejected();

        String primary = switch (metadata) {
            case 0 -> "AUROMANCY";
            case 1 -> "ALCHEMY";
            case 2 -> "GOLEMANCY";
            case 3, 6 -> "ELDRITCH";
            case 4 -> "INFUSION";
            case 5 -> "ARTIFICE";
            default -> throw new AssertionError("valid Curio metadata");
        };
        // BETA26 MathHelper.getInt(random, min, max) includes BOTH endpoints.
        int observation = between(random, KnowledgeType.OBSERVATION.progression() / 2,
                KnowledgeType.OBSERVATION.progression());
        int theory = between(random, KnowledgeType.THEORY.progression() / 3,
                KnowledgeType.THEORY.progression() / 2);
        knowledge.addKnowledge(KnowledgeType.OBSERVATION, primary, observation);
        knowledge.addKnowledge(KnowledgeType.THEORY, primary, theory);

        boolean perm = false;
        if (metadata == 3 || metadata == 6) {
            warp.add(WarpType.NORMAL, 1);
            warp.add(WarpType.TEMPORARY, 5);
            if (metadata == 6) {
                perm = random.nextBoolean();
                if (perm) warp.add(WarpType.PERMANENT, 1);
            }
        }
        String randomObsCat = categoryOrder.get(random.nextInt(categoryOrder.size()));
        int randomObs = between(random, KnowledgeType.OBSERVATION.progression() / 2,
                KnowledgeType.OBSERVATION.progression());
        knowledge.addKnowledge(KnowledgeType.OBSERVATION, randomObsCat, randomObs);

        String randomTheoryCat = categoryOrder.get(random.nextInt(categoryOrder.size()));
        int randomTheory = between(random, KnowledgeType.THEORY.progression() / 3,
                KnowledgeType.THEORY.progression() / 2);
        knowledge.addKnowledge(KnowledgeType.THEORY, randomTheoryCat, randomTheory);
        return new Effect(true, metadata == 6, primary,
                randomObsCat, randomTheoryCat, observation, theory,
                randomObs, randomTheory, perm);
    }

    private static int between(Source random, int min, int max) {
        if (max < min) throw new IllegalArgumentException("invalid random bounds");
        return min + random.nextInt(max - min + 1);
    }
}
