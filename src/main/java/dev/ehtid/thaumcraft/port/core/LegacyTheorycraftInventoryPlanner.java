package dev.ehtid.thaumcraft.port.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;

/**
 * Server-side BETA26 theory card item costs, planned before any inventory
 * mutation. In the original ContainerResearchTable each requirement was
 * checked in isolation and consumption failures were ignored; duplicate
 * inputs could therefore award research without actually paying the cost.
 *
 * This bridge preserves the requirement array order, checks unconsumed
 * catalyst presence and atomically reserves ALL consumed stacks. It never
 * selects armor/offhand: BETA26 InventoryUtils searched mainInventory only.
 */
public final class LegacyTheorycraftInventoryPlanner {
    private LegacyTheorycraftInventoryPlanner() {}

    public record Slot<T>(T identity, int count) {
        public Slot {
            if (count < 0) throw new IllegalArgumentException("negative item count");
            if (count > 0) Objects.requireNonNull(identity, "nonempty identity");
        }
    }

    public record Ingredient<T>(T identity, int count, boolean consumed) {
        public Ingredient {
            Objects.requireNonNull(identity, "required identity");
            if (count <= 0) throw new IllegalArgumentException("required count");
        }
    }

    public static final class Plan {
        private final int[] consumedBySlot;

        private Plan(int[] consumedBySlot) { this.consumedBySlot = consumedBySlot.clone(); }
        public int slotCount() { return consumedBySlot.length; }
        public int consumedAt(int slot) { return consumedBySlot[slot]; }
        public int[] snapshot() { return consumedBySlot.clone(); }
        public int totalConsumed() {
            int count = 0;
            for (int n : consumedBySlot) count += n;
            return count;
        }
    }

    /**
     * Null plan means some original required stack is missing. A retained
     * catalyst needs to exist before the transaction, but may share its
     * pre-transaction stack with a consumed ingredient of the same type,
     * matching BETA26's distinct presence/consumption operations.
     */
    public static <T> Plan plan(List<Slot<T>> inventory,
                                List<Ingredient<T>> requirements,
                                BiPredicate<T, T> sameItem) {
        Objects.requireNonNull(inventory);
        Objects.requireNonNull(requirements);
        Objects.requireNonNull(sameItem);
        int[] reserved = new int[inventory.size()];
        for (Ingredient<T> ingredient : requirements) {
            Objects.requireNonNull(ingredient, "requirement");
            int available = 0;
            for (Slot<T> slot : inventory) {
                Objects.requireNonNull(slot, "slot");
                if (slot.count() > 0 && sameItem.test(slot.identity(), ingredient.identity()))
                    available += slot.count();
            }
            if (available < ingredient.count()) return null;
            if (!ingredient.consumed()) continue;

            int need = ingredient.count();
            for (int i = 0; i < inventory.size() && need > 0; i++) {
                Slot<T> slot = inventory.get(i);
                if (slot.count() == 0 || !sameItem.test(slot.identity(), ingredient.identity()))
                    continue;
                int taken = Math.min(need, slot.count() - reserved[i]);
                if (taken <= 0) continue;
                reserved[i] += taken;
                need -= taken;
            }
            if (need > 0) return null;
        }
        return new Plan(reserved);
    }

    /** Convenience for Java item identities with value equality. */
    public static <T> Plan plan(List<Slot<T>> inventory, List<Ingredient<T>> requirements) {
        return plan(inventory, requirements, Objects::equals);
    }
}
