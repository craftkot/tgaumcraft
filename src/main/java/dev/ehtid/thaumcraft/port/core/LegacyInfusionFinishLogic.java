package dev.ehtid.thaumcraft.port.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * BETA26 TileInfusionMatrix.craftCycle at 1554..1585 and
 * craftingFinish(Object,String) at 0..390.
 *
 * The original finish supports THREE distinct recipe output types:
 * 1. ItemStack: COPY recipe output into the central pedestal.
 * 2. NBTBase: ADD/REPLACE the named NBT tag on the existing central stack.
 * 3. Enchantment: take existing level and increment by ONE (no clamp).
 *
 * This is a deterministic isolated planner, NOT a fake game recipe registry.
 * NeoForge adapters must copy genuine ItemStack, DataComponents/CustomData
 * or enchantments, then commit only after real costs/ingredients are paid.
 */
public final class LegacyInfusionFinishLogic {
    private LegacyInfusionFinishLogic() {}

    public enum Kind { ITEM, TAG, ENCHANTMENT }

    /**
     * Source craftCycle reaches craftingFinish ONLY after no outstanding
     * ingredients AND no positive outstanding essentia.
     * Inventories remain untouched until the final type-specific commit.
     */
    public static boolean ready(boolean activeCrafting, boolean centralPedestalPresent,
                                AspectListData remainingEssentia,
                                List<?> remainingIngredients) {
        return activeCrafting && centralPedestalPresent && remainingEssentia != null
                && remainingEssentia.visSize() <= 0
                && remainingIngredients != null && remainingIngredients.isEmpty();
    }

    public static <T> T itemOutput(T recipeItem, UnaryOperator<T> copier) {
        Objects.requireNonNull(recipeItem, "Original recipe output");
        Objects.requireNonNull(copier, "Copy adapter");
        T copy = Objects.requireNonNull(copier.apply(recipeItem), "copy result");
        if (copy == recipeItem)
            throw new IllegalArgumentException("Recipe output must be copied, never shared");
        return copy;
    }

    /**
     * BETA26 craftingFinish(ItemStack): preserve relative wear from an
     * existing damaged central stack when the new damageable result isn't
     * already damaged. This conditional deliberately mirrors the bytecode:
     *
     * if center.isItemStackDamageable && center.isItemDamaged:
     *     f = centerDamage / centerMaxDamage
     * if result.isItemStackDamageable && !result.isItemDamaged:
     *     result.setDamage((int)(resultMaxDamage * f))
     *
     * No arbitrary item damage is applied when original conditions fail.
     */
    public static int inheritedItemDamage(int centerDamage, int centerMaxDamage,
                                          boolean centerDamageable, boolean centerDamaged,
                                          int resultDamage, int resultMaxDamage,
                                          boolean resultDamageable, boolean resultDamaged) {
        float fraction = 1.0F;
        if (centerDamageable && centerDamaged && centerMaxDamage > 0)
            fraction = (float)centerDamage / (float)centerMaxDamage;
        if (resultDamageable && !resultDamaged) {
            return (int)(resultMaxDamage * fraction);
        }
        return resultDamage;
    }

    /** Original ItemStack.setTagInfo(label, originalNbtTag); overwrite key. */
    public static <K,V> Map<K,V> withTag(Map<K,V> current, K label, V nbtValue) {
        Objects.requireNonNull(current);
        Objects.requireNonNull(label);
        Objects.requireNonNull(nbtValue);
        Map<K,V> result = new LinkedHashMap<>(current);
        result.put(label, nbtValue);
        return Collections.unmodifiableMap(result);
    }

    /**
     * Original EnchantmentHelper.getEnchantments -> map.put(enchantment,
     * getEnchantmentLevel(enchantment, stack) + 1) -> setEnchantments.
     * NO artificial maximum level is introduced.
     */
    public static <E> Map<E,Integer> incrementEnchantment(
            Map<E,Integer> current, E enchantment) {
        Objects.requireNonNull(current);
        Objects.requireNonNull(enchantment);
        Map<E,Integer> result = new LinkedHashMap<>(current);
        int before = result.getOrDefault(enchantment, 0);
        result.put(enchantment, Math.addExact(before,1));
        return Collections.unmodifiableMap(result);
    }

    /** BETA26 after craftingFinish sets recipeEssentia=new AspectList(),
     * recipeInstability=0 and crafting=false; old unspent copied data must
     * not be reintroduced when saving and loading the next world tick.
     */
    public record Cleared(boolean crafting, AspectListData remainingEssentia,
                          int recipeInstability, boolean recipeOutputPresent) {
        public Cleared {
            remainingEssentia = remainingEssentia.copy();
        }
        @Override public AspectListData remainingEssentia() { return remainingEssentia.copy(); }
    }

    public static Cleared clearedState() {
        return new Cleared(false, new AspectListData(), 0, false);
    }
}
