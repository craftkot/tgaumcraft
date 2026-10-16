package dev.ehtid.thaumcraft.port.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Persistable resource accounting for an infusion that was ALREADY matched
 * against an original recipe. A separate real caster/start adapter must
 * authorize it: this ledger is NOT a recipe registry or a way to grant output.
 *
 * Its critical invariant is sum(requested)-sum(remaining)=paid. The caller
 * may persist the ledger only AFTER the associated real jar debit succeeds.
 * Reloaded records cannot silently refill already-paid resources.
 */
public final class LegacyInfusionRitualLedger {
    private final String recipeId;
    private final Map<String,Integer> requested;
    private final Map<String,Integer> remaining;
    private final int paid;

    private LegacyInfusionRitualLedger(String recipeId, Map<String,Integer> requested,
                                      Map<String,Integer> remaining, int paid) {
        if (recipeId == null || recipeId.isBlank()) throw new IllegalArgumentException("recipe");
        this.recipeId = recipeId;
        this.requested = immutableAmounts(requested);
        this.remaining = immutableAmounts(remaining);
        if (!this.requested.keySet().equals(this.remaining.keySet()))
            throw new IllegalArgumentException("Aspect keys changed");
        long computed = 0;
        for (var entry : this.requested.entrySet()) {
            int left = this.remaining.get(entry.getKey());
            if (left > entry.getValue())
                throw new IllegalArgumentException("Remaining exceeds original cost");
            computed += (long) entry.getValue() - left;
        }
        if (computed != paid || computed > Integer.MAX_VALUE || paid < 0)
            throw new IllegalArgumentException("Paid/remaining mismatch");
        this.paid = paid;
    }

    private static Map<String,Integer> immutableAmounts(Map<String,Integer> input) {
        Objects.requireNonNull(input);
        var out = new LinkedHashMap<String,Integer>();
        for (var entry : input.entrySet()) {
            String key = entry.getKey();
            Integer count = entry.getValue();
            if (key == null || key.isBlank() || count == null || count < 0
                    || count > 100000 || out.putIfAbsent(key, count) != null)
                throw new IllegalArgumentException("Invalid aspect costs");
        }
        if (out.isEmpty()) throw new IllegalArgumentException("Empty recipe essence");
        return Collections.unmodifiableMap(out);
    }

    public static LegacyInfusionRitualLedger begin(String verifiedRecipe,
                                                    AspectListData costs) {
        Objects.requireNonNull(costs);
        var amounts = new LinkedHashMap<String,Integer>();
        for (AspectData aspect : costs.getAspects()) {
            if (aspect == null || aspect.tag() == null
                    || amounts.putIfAbsent(aspect.tag(), costs.getAmount(aspect)) != null)
                throw new IllegalArgumentException("Duplicate/unknown aspect");
        }
        return new LegacyInfusionRitualLedger(verifiedRecipe, amounts, amounts, 0);
    }

    /** Read a persisted checkpoint but reject invented/deleted units. */
    public static LegacyInfusionRitualLedger restore(String verifiedRecipe,
                 Map<String,Integer> initial, Map<String,Integer> current, int paid) {
        return new LegacyInfusionRitualLedger(verifiedRecipe, initial, current, paid);
    }

    public String recipeId() { return recipeId; }
    public Map<String,Integer> requested() { return requested; }
    public Map<String,Integer> remaining() { return remaining; }
    public int paid() { return paid; }

    public int unitsLeft() {
        return remaining.values().stream().mapToInt(Integer::intValue).sum();
    }

    public boolean readyForIngredients() { return unitsLeft() == 0; }

    /**
     * Return an updated immutable ledger ONLY once a real world source has
     * successfully debited the matching unit. A stale/replayed debit cannot
     * decrement zero, affect another aspect, or grant resources.
     */
    public LegacyInfusionRitualLedger afterWorldDebit(String aspect) {
        if (aspect == null || remaining.getOrDefault(aspect, 0) <= 0)
            throw new IllegalArgumentException("Not an outstanding aspect");
        var next = new LinkedHashMap<>(remaining);
        next.put(aspect, next.get(aspect) - 1);
        return new LegacyInfusionRitualLedger(recipeId, requested, next, paid + 1);
    }
}
