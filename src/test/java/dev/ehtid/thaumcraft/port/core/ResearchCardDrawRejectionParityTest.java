package dev.ehtid.thaumcraft.port.core;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/** Regression of the original BETA26 card draw rejection semantics. */
public final class ResearchCardDrawRejectionParityTest {
    private static int checks;
    private static void check(boolean condition, String why) {
        checks++;
        if (!condition) throw new AssertionError(why);
    }

    private static final class Rolls implements LegacyTheorycraftDrawEngine.RandomSource {
        private final float roll;
        private long seed = 100;
        Rolls(float roll) { this.roll = roll; }
        @Override public float nextFloat() { return roll; }
        @Override public int nextInt(int bound) { return 0; }
        @Override public long nextLong() { return seed++; }
    }

    public static void main(String[] args) {
        // A rejected helper stays available; its rejection still consumes a draw attempt.
        int[] attempt = {0};
        var retry = LegacyTheorycraftDrawEngine.draw(1, 0, 1, List.of("aid"), List.of(),
                Set.of(), List.of(), new Rolls(0f), (key, rng) ->
                    new LegacyTheorycraftDrawEngine.Candidate(key, rng.nextLong(),
                            ++attempt[0] == 1 ? 2 : 1, null, true));
        check(retry.attempts() == 2, "rejected helper consumes an attempt");
        check(retry.choices().size() == 1, "helper eventually accepted");
        check(retry.choices().getFirst().card().seed() == 101L,
                "rejected initialized helper consumed previous seed");
        check(retry.remainingAidCards().isEmpty(), "accepted helper consumed once");

        var blocked = LegacyTheorycraftDrawEngine.draw(1, 0, 5, List.of("aid"), List.of(),
                Set.of(), List.of("BASICS"), new Rolls(0f), (key, rng) ->
                    new LegacyTheorycraftDrawEngine.Candidate(key, rng.nextLong(),1,"BASICS",true));
        check(blocked.choices().isEmpty(), "blocked helper not offered");
        check(blocked.attempts() == 10000, "blocked helper retries to cap");
        check(blocked.remainingAidCards().equals(List.of("aid")),
                "blocked helper not removed from pool");

        var categoryMissing = LegacyTheorycraftDrawEngine.draw(1, 0, 5,
                List.of(), List.of("normal"), Set.of("BASICS"), List.of(), new Rolls(1f),
                (key, rng) -> new LegacyTheorycraftDrawEngine.Candidate(key,
                        rng.nextLong(),1,"ALCHEMY",false));
        check(categoryMissing.choices().isEmpty(), "ordinary card respects category access");
        check(categoryMissing.attempts() == 10000, "blocked ordinary card retries boundedly");
        var categoryReady = LegacyTheorycraftDrawEngine.draw(1, 0, 5,
                List.of(), List.of("normal"), Set.of("ALCHEMY"), List.of(), new Rolls(1f),
                (key, rng) -> new LegacyTheorycraftDrawEngine.Candidate(key,
                        rng.nextLong(),1,"ALCHEMY",false));
        check(categoryReady.choices().size() == 1, "unlocked ordinary category offered");
        check(!categoryReady.choices().getFirst().fromAid(), "ordinary provenance preserved");

        int[] errors = {0};
        var recover = LegacyTheorycraftDrawEngine.draw(1, 0, 5, List.of(),
                List.of("normal"), Set.of(), List.of(), new Rolls(1f), (key, rng) -> {
                    if (++errors[0] == 1) throw new IllegalStateException("original generator failed");
                    return new LegacyTheorycraftDrawEngine.Candidate(key,rng.nextLong(),1,null,false);
                });
        check(recover.choices().size() == 1, "normal generator exception retried");
        check(recover.attempts() == 2, "normal exception costs one attempt");
        check(recover.choices().getFirst().card().seed() == 100L,
                "normal failed generator did not consume seed");

        boolean aidThrows = false;
        try {
            LegacyTheorycraftDrawEngine.draw(1, 0, 5, List.of("aid"),
                    List.of(), Set.of(), List.of(), new Rolls(0f), (key,rng) -> {
                        throw new IllegalStateException("aid init failure");
                    });
        } catch (IllegalStateException expected) { aidThrows = true; }
        check(aidThrows, "aid factory exception is propagated, unlike normal branch");

        var duplicate = LegacyTheorycraftDrawEngine.draw(2,0,5,List.of(),
                List.of("normal"),Set.of(),List.of(),new Rolls(1f), (key,rng) ->
                    new LegacyTheorycraftDrawEngine.Candidate(key,rng.nextLong(),1,null,false));
        check(duplicate.choices().size() == 1, "duplicate class rejected within draw");
        check(duplicate.attempts() == 10000, "duplicate retry bounded");

        var aidBoundary = LegacyTheorycraftDrawEngine.draw(1,0,5,
                List.of("aid"),List.of("normal"),Set.of(),List.of(),new Rolls(0.25f),
                (key,rng) -> new LegacyTheorycraftDrawEngine.Candidate(
                        key,rng.nextLong(),1,null,key.equals("aid")));
        check(aidBoundary.choices().getFirst().fromAid(), "exact 25% roll picks aid");

        var nullBlocked = LegacyTheorycraftDrawEngine.draw(1,0,5,
                List.of("aid"),List.of(),Set.of(),Arrays.asList((String) null),new Rolls(0f),
                (key,rng) -> new LegacyTheorycraftDrawEngine.Candidate(key,rng.nextLong(),1,null,true));
        check(nullBlocked.choices().isEmpty(), "legacy nullable blocked category applies");
        check(nullBlocked.attempts() == 10000, "nullable block bounded");
        System.out.println("M07 original card rejection parity: OK (" + checks + " assertions)");
    }
}
