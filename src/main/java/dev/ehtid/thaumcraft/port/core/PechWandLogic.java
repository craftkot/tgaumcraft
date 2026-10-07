package dev.ehtid.thaumcraft.port.core;

import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;

/** Pure gameplay result calculation for ItemPechWand, extracted from BETA26 bytecode. */
public final class PechWandLogic {
    private PechWandLogic() {}

    public record KnowledgeGain(KnowledgeType type, String categoryKey, int rawAmount) {}
    public record Result(boolean usable, boolean unlockFocusPech, KnowledgeGain observation, KnowledgeGain theory) {}

    public static Result use(boolean knowsBaseAuromancy, boolean knowsFocusPech, List<String> researchCategories, RandomGenerator random) {
        Objects.requireNonNull(researchCategories, "researchCategories");
        Objects.requireNonNull(random, "random");
        if (!knowsBaseAuromancy) return new Result(false, false, null, null);
        if (researchCategories.isEmpty()) throw new IllegalArgumentException("TC6 requires at least one research category");

        int obsProg = KnowledgeType.OBSERVATION.progression(); // 16
        int theoryProg = KnowledgeType.THEORY.progression();  // 32

        String obsCategory = researchCategories.get(random.nextInt(researchCategories.size()));
        int obsAmount = randomInclusive(random, obsProg / 3, obsProg / 2); // 5..8 raw

        String theoryCategory = researchCategories.get(random.nextInt(researchCategories.size()));
        int theoryAmount = randomInclusive(random, theoryProg / 5, theoryProg / 4); // 6..8 raw

        return new Result(
                true,
                !knowsFocusPech,
                new KnowledgeGain(KnowledgeType.OBSERVATION, obsCategory, obsAmount),
                new KnowledgeGain(KnowledgeType.THEORY, theoryCategory, theoryAmount));
    }

    private static int randomInclusive(RandomGenerator random, int min, int max) {
        return min + random.nextInt(max - min + 1);
    }
}
