package dev.ehtid.thaumcraft.port.core;

import java.util.List;
import java.util.Set;

/**
 * Standalone Java 21 regression of the original M07 aid-card draw core.
 * Requires only LegacyTheorycraftDrawEngine: intentionally independent
 * of the still-blocked GitHub runner, NeoForge and a Minecraft client.
 * This does NOT test the physical crucible/matrix or item/payment wiring.
 */
public final class ResearchAidDrawStandaloneTest {
    private static int checks;

    private static void check(boolean test, String reason) {
        checks++;
        if (!test) throw new AssertionError(reason);
    }

    private static final class FixedRolls implements LegacyTheorycraftDrawEngine.RandomSource {
        private long seed = 100L;
        @Override public float nextFloat() { return 0.0F; }
        @Override public int nextInt(int bound) { return 0; }
        @Override public long nextLong() { return seed++; }
    }

    public static void main(String[] args) {
        var cards = List.of("CardConcentrate", "CardReactions", "CardSynthesis");
        LegacyTheorycraftDrawEngine.CardFactory factory = (key, rng) ->
                new LegacyTheorycraftDrawEngine.Candidate(
                        key, rng.nextLong(), 1, null, true);

        var drawn = LegacyTheorycraftDrawEngine.draw(2, 0, 4,
                cards, List.of(), Set.of(), List.of(), new FixedRolls(), factory);
        check(drawn.attempts() == 2, "one attempt for each accepted aid");
        check(drawn.choices().size() == 2, "two playable choices");
        check(drawn.choices().get(0).fromAid() && drawn.choices().get(1).fromAid(),
                "true aid provenance");
        check(!drawn.choices().get(0).selected(), "not activated by drawing");
        check(drawn.choices().get(0).card().seed() == 100L, "first original seed");
        check(drawn.choices().get(1).card().seed() == 101L, "second original seed");
        check(drawn.choices().get(0).card().key().equals(cards.get(0)), "first card");
        check(drawn.choices().get(1).card().key().equals(cards.get(1)), "second card");
        check(drawn.remainingAidCards().equals(cards.subList(2, 3)), "remaining aid");
        check(cards.size() == 3, "source pool untouched");

        var bonus = LegacyTheorycraftDrawEngine.draw(3, 1, 4,
                cards, List.of(), Set.of(), List.of(), new FixedRolls(), factory);
        check(bonus.choices().size() == 3, "third card requires a bonus");
        check(bonus.remainingBonusDraws() == 0, "bonus is consumed once");
        check(bonus.remainingAidCards().isEmpty(), "all helper cards consumed");

        var normal = LegacyTheorycraftDrawEngine.draw(3, 0, 4,
                cards, List.of(), Set.of(), List.of(), new FixedRolls(), factory);
        check(normal.choices().size() == 2, "third choice degrades without bonus");

        var empty = LegacyTheorycraftDrawEngine.draw(3, 1, 4,
                List.of(), List.of(), Set.of(), List.of(), new FixedRolls(), factory);
        check(empty.choices().isEmpty(), "empty registry cannot create cards");
        check(empty.attempts() == 0, "empty registry exits without busy-loop");

        var expensive = LegacyTheorycraftDrawEngine.draw(2, 0, 0,
                cards, List.of(), Set.of(), List.of(), new FixedRolls(), factory);
        check(expensive.choices().isEmpty(), "cost above inspiration is rejected");
        check(expensive.attempts() == LegacyTheorycraftDrawEngine.MAX_ATTEMPTS,
                "failed offers have bounded retry count");
        System.out.println("M07 original aid draw standalone: OK (" + checks + " assertions)");
    }
}
