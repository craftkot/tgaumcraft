package dev.ehtid.thaumcraft.port.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Function;

/**
 * BETA26 TileInfusionMatrix.craftCycle item stage, bytecode offsets
 * 1152..1554. This pure planner models the pedestal slot to be consumed;
 * the future live server executor MUST validate the matching real
 * ItemStack again and replace it atomically with its actual container item.
 *
 * The ORIGINAL itemCount is shared by the ritual (not per pedestal):
 *   0 -> set 5 and emit source FX, return;
 *   5 -> 4 -> 3 -> 2 -> 1 -> 0;
 *   on transition 1 -> 0, consume matching item, leave container item
 *   (or empty) on pedestal, remove one recipe ingredient, return.
 * Search is ordered by remaining recipeIngredients then pedestal scan order.
 */
public final class LegacyInfusionIngredientCycle {
    private LegacyInfusionIngredientCycle() {}
    public static final int FIRST_COUNTDOWN = 5;

    public enum Action { READY_TO_FINISH, SOURCE_EFFECT, COUNTDOWN, CONSUME, NOT_FOUND }

    public record Result<T>(Action action, int nextCountdown,
                            int ingredientIndex, int pedestalIndex,
                            T replacement, List<T> remainingIngredients) {
        public Result {
            Objects.requireNonNull(action);
            remainingIngredients = List.copyOf(remainingIngredients);
        }
        @Override public List<T> remainingIngredients() {
            return List.copyOf(remainingIngredients);
        }
    }

    /**
     * Components must already be snapshots (not live mutable ItemStacks).
     * A null pedestal is considered empty; matcher must retain original
     * ThaumcraftInvHelper.areItemStacksEqualForCrafting semantics.
     */
    public static <T> Result<T> cycle(List<T> required, List<T> pedestals,
                                      int itemCount, BiPredicate<T,T> matches,
                                      Function<T,T> containerItem) {
        Objects.requireNonNull(required);
        Objects.requireNonNull(pedestals);
        Objects.requireNonNull(matches);
        Objects.requireNonNull(containerItem);
        if (itemCount < 0) throw new IllegalArgumentException("itemCount");
        if (required.isEmpty())
            return new Result<>(Action.READY_TO_FINISH, itemCount,-1,-1,null,List.of());
        for (int recipe = 0; recipe < required.size(); recipe++) {
            T ingredient = required.get(recipe);
            if (ingredient == null) continue;
            for (int slot = 0; slot < pedestals.size(); slot++) {
                T presented = pedestals.get(slot);
                if (presented == null || !matches.test(presented,ingredient)) continue;
                if (itemCount == 0)
                    return new Result<>(Action.SOURCE_EFFECT,FIRST_COUNTDOWN,
                            recipe,slot,null,required);
                if (itemCount > 1)
                    return new Result<>(Action.COUNTDOWN,itemCount-1,
                            recipe,slot,null,required);
                ArrayList<T> remaining = new ArrayList<>(required);
                remaining.remove(recipe);
                // In BETA26 the emptied pedestal is given the original
                // Item.getContainerItem(stack) result; null means empty.
                return new Result<>(Action.CONSUME,0,recipe,slot,
                        containerItem.apply(presented),remaining);
            }
        }
        return new Result<>(Action.NOT_FOUND,itemCount,-1,-1,null,required);
    }
}
