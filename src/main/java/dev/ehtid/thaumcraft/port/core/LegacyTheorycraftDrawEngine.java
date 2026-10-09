package dev.ehtid.thaumcraft.port.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Loader-neutral implementation of BETA26 ResearchTableData.drawCards.
 *
 * The original rolls a 25% chance for an eligible aid-card on each attempt,
 * otherwise samples the TheorycraftManager.cards HashMap key array. Every
 * rejected card RETRIES (maximum 10,000 attempts), without decreasing the
 * requested number. A selected aid card is removed from the available pool;
 * a rejected aid remains. Both pools share a single per-draw duplicate set.
 *
 * IMPORTANT: The caller supplies the normal card list IN the source HashMap's
 * keySet().toArray() order. Sorting card IDs would change the seeded results.
 * The card factory must initialize original card subclasses and preserve
 * each subclass's original NBT, not invent placeholder card results.
 */
public final class LegacyTheorycraftDrawEngine {
    private LegacyTheorycraftDrawEngine() {}

    public static final int MAX_ATTEMPTS = 10000;

    public interface RandomSource {
        float nextFloat();
        int nextInt(int bound);
        long nextLong();
    }

    /** Candidate resulting from the card subclass's initialize call. */
    public record Candidate(String key, long seed, int inspirationCost,
                            String researchCategory, boolean aidOnly) {
        public Candidate {
            Objects.requireNonNull(key, "key");
        }
    }

    /**
     * Null means the card failed initialization or its original factory
     * didn't recognize the key. The 1.12 generateCard consumes nextLong()
     * after a recognized class has been successfully instantiated; therefore
     * seed acquisition is delegated to this factory instead of done eagerly.
     */
    @FunctionalInterface
    public interface CardFactory {
        Candidate generate(String cardKey, RandomSource random);
    }

    public record CardChoice(Candidate card, boolean fromAid, boolean selected) {
        public CardChoice {
            Objects.requireNonNull(card, "card");
        }
    }

    public record Result(List<CardChoice> choices, List<String> remainingAidCards,
                         int remainingBonusDraws, int attempts) {
        public Result {
            choices = List.copyOf(choices);
            remainingAidCards = List.copyOf(remainingAidCards);
        }
    }

    public static Result draw(int requestedCards, int bonusDraws, int inspiration,
                              List<String> aidCards, List<String> normalCardKeys,
                              Set<String> availableCategories, List<String> blockedCategories,
                              RandomSource random, CardFactory factory) {
        Objects.requireNonNull(aidCards, "aidCards");
        Objects.requireNonNull(normalCardKeys, "normalCardKeys");
        Objects.requireNonNull(availableCategories, "availableCategories");
        Objects.requireNonNull(blockedCategories, "blockedCategories");
        Objects.requireNonNull(random, "random");
        Objects.requireNonNull(factory, "factory");

        int remainingCards = requestedCards;
        int remainingBonuses = bonusDraws;
        // Original: request 3 degrades to 2 unless a bonus draw is available.
        if (remainingCards == 3) {
            if (remainingBonuses > 0) remainingBonuses--;
            else remainingCards = 2;
        }

        ArrayList<String> aids = new ArrayList<>(aidCards);
        ArrayList<CardChoice> choices = new ArrayList<>();
        Set<String> chosenKeys = new HashSet<>();
        int attempts = 0;
        // BETA26 burns 10,000 attempts on an EMPTY card registry, catching
        // Random.nextInt(0) every time. This is a confirmed no-op: no card
        // can appear and no RNG call advances. Return the same empty choice
        // list immediately instead of blocking the server thread.
        if (remainingCards > 0 && aids.isEmpty() && normalCardKeys.isEmpty()) {
            return new Result(choices, aids, remainingBonuses, attempts);
        }
        while (remainingCards > 0 && attempts < MAX_ATTEMPTS) {
            // Can also become empty halfway through a draw when its LAST aid
            // is consumed. The old code would then spin until attempt 10000.
            // This exit changes neither selections nor the RNG sequence.
            if (aids.isEmpty() && normalCardKeys.isEmpty()) break;
            attempts++;
            boolean tryAid = !aids.isEmpty() && random.nextFloat() <= 0.25F;
            if (tryAid) {
                int index = random.nextInt(aids.size());
                String key = aids.get(index);
                // Original never attempts the normal branch on a failed aid.
                Candidate card = factory.generate(key, random);
                if (card == null || card.inspirationCost() > inspiration
                        || containsLegacyNullable(blockedCategories, card.researchCategory())
                        || chosenKeys.contains(key)) continue;
                chosenKeys.add(key);
                choices.add(new CardChoice(card, true, false));
                aids.remove(index);
                remainingCards--;
                continue;
            }

            if (normalCardKeys.isEmpty()) {
                // Vanilla catches Random.nextInt(0) on every attempt and
                // retries until attempt 10000 without changing game state.
                continue;
            }
            String key = normalCardKeys.get(random.nextInt(normalCardKeys.size()));
            Candidate card;
            try {
                card = factory.generate(key, random);
            } catch (RuntimeException oldGenerateCardException) {
                // BETA26 catches Exceptions in the ordinary-card branch
                // and retries; it does not swallow JVM Errors.
                continue;
            }
            if (card == null || card.aidOnly() || card.inspirationCost() > inspiration)
                continue;
            String category = card.researchCategory();
            if (category != null && !availableCategories.contains(category)) continue;
            if (chosenKeys.contains(key)) continue;
            chosenKeys.add(key);
            choices.add(new CardChoice(card, false, false));
            remainingCards--;
        }
        return new Result(choices, aids, remainingBonuses, attempts);
    }

    /** Original BETA26 ArrayList.contains(null) does not throw; List.of does. */
    private static boolean containsLegacyNullable(List<String> list, String sought) {
        for (String entry : list) {
            if (Objects.equals(entry, sought)) return true;
        }
        return false;
    }

    public static RandomSource fromJavaRandom(java.util.Random random) {
        Objects.requireNonNull(random, "random");
        return new RandomSource() {
            @Override public float nextFloat() { return random.nextFloat(); }
            @Override public int nextInt(int bound) { return random.nextInt(bound); }
            @Override public long nextLong() { return random.nextLong(); }
        };
    }
}
