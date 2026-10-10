package dev.ehtid.thaumcraft.scanning;

import dev.ehtid.thaumcraft.port.core.LegacyTheorycraftInventoryPlanner;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Server-only BETA26 research-card ingredient bridge. Use the original
 * InventoryPlayer.mainInventory (the modern 36 main slots), not armor,
 * ender chest, curios or a client-provided count. Missing original mod item
 * mappings MUST fail closed; no substitute item is silently accepted.
 *
 * This adapter uses exact item + component identity. Original TC6 relaxed
 * NBT matching and enchanted-placeholder exceptions need separate audited
 * match policies when those specific legacy cards are wired.
 */
public final class NeoForgeTheorycraftItemTransaction {
    private NeoForgeTheorycraftItemTransaction() {}

    public static LegacyTheorycraftInventoryPlanner.Plan prepare(
            ServerPlayer player, List<ItemStack> required, boolean[] consumed) {
        Objects.requireNonNull(player);
        Objects.requireNonNull(required);
        if (consumed != null && consumed.length != required.size()) return null;

        var inventory = player.getInventory().items;
        List<LegacyTheorycraftInventoryPlanner.Slot<ItemStack>> slots =
                new ArrayList<>(inventory.size());
        for (ItemStack stack : inventory) {
            slots.add(new LegacyTheorycraftInventoryPlanner.Slot<>(
                    stack, stack.isEmpty() ? 0 : stack.getCount()));
        }
        List<LegacyTheorycraftInventoryPlanner.Ingredient<ItemStack>> needs =
                new ArrayList<>();
        for (int i = 0; i < required.size(); i++) {
            ItemStack stack = required.get(i);
            if (stack == null || stack.isEmpty()) continue;
            needs.add(new LegacyTheorycraftInventoryPlanner.Ingredient<>(
                    stack, stack.getCount(), consumed != null && consumed[i]));
        }
        return LegacyTheorycraftInventoryPlanner.plan(slots, needs,
                ItemStack::isSameItemSameComponents);
    }

    /**
     * Recheck the WHOLE cost against the inventory at commit-time before any
     * item is shrunk. Expected to run on the server game thread as part of a
     * single validated card activation, not as an async callback.
     */
    public static boolean commit(ServerPlayer player, List<ItemStack> required,
                                 boolean[] consumed) {
        LegacyTheorycraftInventoryPlanner.Plan plan = prepare(player, required, consumed);
        if (plan == null) return false;
        var inventory = player.getInventory();
        for (int i = 0; i < plan.slotCount(); i++) {
            int amount = plan.consumedAt(i);
            if (amount > 0) inventory.items.get(i).shrink(amount);
        }
        if (plan.totalConsumed() > 0) inventory.setChanged();
        return true;
    }
}
