package dev.ehtid.thaumcraft.golem.press;

import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.golem.inventory.GolemContainers;
import dev.ehtid.thaumcraft.golem.seal.GolemSeals.SealKey;
import dev.ehtid.thaumcraft.registry.ThaumcraftItems;
import dev.ehtid.thaumcraft.port.core.golem.GolemAssembly;
import dev.ehtid.thaumcraft.port.core.golem.GolemDesign;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;

/** Server-thread preflight followed by one direct debit of trusted Container slots.
 * Capability-only third-party inventories need a separate recovery transaction and are not consumed.
 */
final class GolemPressPayment {
    private GolemPressPayment() {}
    private record Source(Container inventory, Direction face) {}
    private record Debit(Container inventory, int slot, ItemStack expected, int count) {}

    static boolean pay(ServerLevel level, BlockPos pos, Player player) {
        var adjacent = new ArrayList<Source>();
        var seen = Collections.newSetFromMap(new IdentityHashMap<Container, Boolean>());
        for (Direction face : Direction.values()) {
            if (face == Direction.UP) continue; // BETA26 InventoryUtils excludes the top.
            BlockPos next = pos.relative(face);
            if (!GolemContainers.loaded(level, new SealKey(next, face.getOpposite()))) continue;
            var blockEntity = level.getBlockEntity(next);
            // Vanilla containers expose mutable authoritative slots. An arbitrary mod Container may
            // return copies or require callbacks; it needs an explicit recovery-aware adapter first.
            if (blockEntity instanceof Container inv && BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType())
                    .getNamespace().equals("minecraft") && seen.add(inv)) adjacent.add(new Source(inv, face.getOpposite()));
        }
        var debits = new ArrayList<Debit>();
        for (var component : GolemAssembly.components(GolemDesign.defaultWood())) {
            ItemStack required = new ItemStack(switch (component.legacyItemId()) {
                case "thaumcraft:plank_greatwood" -> ThaumcraftItems.PLANK_GREATWOOD.get();
                case "thaumcraft:mechanism_simple" -> GolemModule.SIMPLE_MECHANISM.get();
                case "thaumcraft:mind" -> GolemModule.CLOCKWORK_MIND.get();
                default -> throw new IllegalStateException("Unmapped verified component");
            }, component.count());
            var plan = plan(adjacent, required);
            // Each component is either fully adjacent or fully in the player's inventory, as in BETA26.
            if (plan == null) plan = plan(List.of(new Source(player.getInventory(), null)), required);
            if (plan == null) return false;
            debits.addAll(plan);
        }
        // Validate all observations before writing anything. No extraction callbacks between debit slots.
        for (var debit : debits) {
            ItemStack actual = debit.inventory.getItem(debit.slot);
            if (!ItemStack.isSameItemSameComponents(actual, debit.expected) || actual.getCount() != debit.expected.getCount()) return false;
        }
        for (var debit : debits) debit.inventory.getItem(debit.slot).shrink(debit.count);
        var changed = Collections.newSetFromMap(new IdentityHashMap<Container, Boolean>());
        for (var debit : debits) if (changed.add(debit.inventory)) debit.inventory.setChanged();
        return true;
    }

    private static List<Debit> plan(List<Source> sources, ItemStack required) {
        var result = new ArrayList<Debit>();
        int remaining = required.getCount();
        for (var source : sources) {
            int[] slots;
            if (source.inventory instanceof WorldlyContainer sided && source.face != null) slots = sided.getSlotsForFace(source.face);
            else slots = java.util.stream.IntStream.range(0, source.inventory.getContainerSize()).toArray();
            var seenSlots = new java.util.HashSet<Integer>();
            for (int slot : slots) {
                if (slot < 0 || slot >= source.inventory.getContainerSize() || !seenSlots.add(slot)) continue;
                ItemStack actual = source.inventory.getItem(slot);
                if (!ItemStack.isSameItemSameComponents(required, actual)) continue;
                if (source.face != null && source.inventory instanceof WorldlyContainer sided
                        && !sided.canTakeItemThroughFace(slot, actual, source.face)) continue;
                int count = Math.min(remaining, actual.getCount());
                result.add(new Debit(source.inventory, slot, actual.copy(), count));
                remaining -= count;
                if (remaining == 0) return result;
            }
        }
        return null;
    }
}
