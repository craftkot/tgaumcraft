package dev.ehtid.thaumcraft.golem.inventory;

import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import dev.ehtid.thaumcraft.golem.seal.GolemSeals.SealKey;
import dev.ehtid.thaumcraft.golem.seal.SealFilter;
import dev.ehtid.thaumcraft.port.core.golem.SealEmptyRules;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/** BETA26 SealFill basic strict filter, projected onto sided NeoForge item capabilities. */
public final class GolemContainers {
    private GolemContainers() {}

    /** Source's first matching visible stack, cached per task. Counts are not filter limits. */
    public static ItemStack emptyCandidate(ServerLevel level, SealKey key, SealFilter filter) {
        return emptyCandidate(level, key, filter, false);
    }

    public static ItemStack emptyCandidate(ServerLevel level, SealKey key, SealFilter filter, boolean leaveOne) {
        IItemHandler source = handler(level, key);
        if (source == null) return ItemStack.EMPTY;
        for (int slot = 0; slot < source.getSlots(); slot++) {
            ItemStack visible = source.getStackInSlot(slot);
            if (!filter.accepts(visible)) continue;
            if (leaveOne && matchingStock(source, visible, filter) < 2) continue;
            // Modern guard: do not produce impossible work for a side that forbids extraction.
            ItemStack preview = source.extractItem(slot, visible.getCount(), true);
            return preview.isEmpty() || !ItemStack.isSameItemSameComponents(visible, preview)
                    ? ItemStack.EMPTY : visible.copy();
        }
        return ItemStack.EMPTY;
    }

    public static boolean canExtract(ServerLevel level, ThaumcraftGolem golem, SealKey key, ItemStack cached, SealFilter filter) {
        return canExtract(level, golem, key, cached, filter, false);
    }

    public static boolean canExtract(ServerLevel level, ThaumcraftGolem golem, SealKey key, ItemStack cached, SealFilter filter, boolean leaveOne) {
        if (cached == null || !filter.accepts(cached) || golem.hasTransferRecovery()) return false;
        IItemHandler source = handler(level, key);
        if (source == null) return false;
        int requested = SealEmptyRules.requested(cached.getCount(), golem.canCarryAmount(cached), matchingStock(source, cached, filter), leaveOne);
        if (requested <= 0) return false;
        for (int slot = 0; slot < source.getSlots(); slot++) {
            if (!filter.matches(cached, source.getStackInSlot(slot))) continue;
            ItemStack preview = source.extractItem(slot, requested, true);
            if (!preview.isEmpty() && filter.matches(cached, preview)) return true;
        }
        return false;
    }

    /** Server-thread extraction: retain actual payload/components, including an unexpected remainder. */
    public static int extractInto(ThaumcraftGolem golem, IItemHandler source, ItemStack cached, SealFilter filter) {
        return extractInto(golem, source, cached, filter, false);
    }

    public static int extractInto(ThaumcraftGolem golem, IItemHandler source, ItemStack cached, SealFilter filter, boolean leaveOne) {
        if (golem.level().isClientSide || source == null || golem.hasTransferRecovery() || !filter.accepts(cached)) return 0;
        int remaining = SealEmptyRules.requested(cached.getCount(), golem.canCarryAmount(cached), matchingStock(source, cached, filter), leaveOne);
        int moved = 0;
        for (int slot = 0; slot < source.getSlots() && remaining > 0 && !golem.hasTransferRecovery(); slot++) {
            if (!filter.matches(cached, source.getStackInSlot(slot))) continue;
            int requested = Math.min(remaining, golem.canCarryAmount(cached));
            if (requested <= 0) break;
            ItemStack actual = source.extractItem(slot, requested, false).copy();
            if (actual.isEmpty()) continue;
            int count = actual.getCount();
            golem.holdExtracted(actual);
            moved += count;
            remaining -= count;
        }
        return moved;
    }

    private static long matchingStock(IItemHandler source, ItemStack template, SealFilter filter) {
        long count = 0;
        for (int slot = 0; slot < source.getSlots(); slot++) {
            ItemStack stack = source.getStackInSlot(slot);
            if (filter.matches(stack, template)) count += stack.getCount();
        }
        return count;
    }

    public static boolean loaded(ServerLevel level, SealKey key) {
        if (!level.hasChunkAt(key.pos()) || !level.hasChunkAt(key.pos().relative(key.face()))) return false;
        var state = level.getBlockState(key.pos());
        if (state.isAir()) return false;
        // Vanilla double-chest capability joins both halves. Never request its unloaded partner.
        return !(state.getBlock() instanceof ChestBlock) || state.getValue(ChestBlock.TYPE) == ChestType.SINGLE
                || level.hasChunkAt(key.pos().relative(ChestBlock.getConnectedDirection(state)));
    }

    public static IItemHandler handler(ServerLevel level, SealKey key) {
        return loaded(level, key) ? level.getCapability(Capabilities.ItemHandler.BLOCK, key.pos(), key.face()) : null;
    }

    private static EquipmentSlot firstSlot(ThaumcraftGolem golem, SealFilter filter) {
        if (filter.accepts(golem.getMainHandItem())) return EquipmentSlot.MAINHAND;
        return golem.design().stats().carrySlots() > 1 && filter.accepts(golem.getOffhandItem())
                ? EquipmentSlot.OFFHAND : null;
    }

    private static int amount(IItemHandler target, ItemStack carried, SealFilter filter) {
        long existing = 0;
        if (target != null && !filter.blacklist() && filter.limit() > 0) {
            for (int i = 0; i < target.getSlots(); i++) {
                ItemStack stack = target.getStackInSlot(i);
                if (filter.matches(carried, stack)) existing += stack.getCount();
            }
        }
        // BETA26 countStackInWorld skips nonempty stacks (verified bytecode).
        // Preserve that source defect for no-handler drops: size limits each drop,
        // not accumulated ground stock. Do not advertise a ground-stock guarantee.
        return filter.transferable(carried, existing);
    }

    public static boolean canDeposit(ServerLevel level, ThaumcraftGolem golem, SealKey key) {
        return canDeposit(level, golem, key, SealFilter.defaults());
    }

    public static boolean canDeposit(ServerLevel level, ThaumcraftGolem golem, SealKey key, SealFilter filter) {
        if (!loaded(level, key)) return false;
        EquipmentSlot slot = firstSlot(golem, filter);
        if (slot == null) return false;
        ItemStack stack = golem.getItemBySlot(slot);
        IItemHandler target = handler(level, key);
        int amount = amount(target, stack, filter);
        if (amount <= 0) return false;
        return target == null || ItemHandlerHelper.insertItemStacked(target, stack.copyWithCount(amount), true).getCount() < amount;
    }

    /** Actual remainder, not simulate's estimate, determines how much leaves the golem. */
    public static int insertCarried(ThaumcraftGolem golem, IItemHandler target) {
        return insertCarried(golem, target, SealFilter.defaults());
    }

    public static int insertCarried(ThaumcraftGolem golem, IItemHandler target, SealFilter filter) {
        if (golem.level().isClientSide) return 0;
        EquipmentSlot slot = firstSlot(golem, filter);
        if (slot == null) return 0;
        ItemStack original = golem.getItemBySlot(slot).copy();
        int requested = amount(target, original, filter); // Fresh count at actual completion, after other workers.
        if (requested <= 0) return 0;
        ItemStack retained = original.copy();
        ItemStack extracted = retained.split(requested);
        golem.setItemSlot(slot, retained);
        ItemStack remainder = ItemHandlerHelper.insertItemStacked(target, extracted.copy(), false);
        // EMPTY is a singleton: when everything was extracted, use a copy with the exact new count.
        golem.setItemSlot(slot, original.copyWithCount(original.getCount() - requested + remainder.getCount()));
        return requested - remainder.getCount();
    }

    public static int deposit(ServerLevel level, ThaumcraftGolem golem, SealKey key) {
        return deposit(level, golem, key, SealFilter.defaults());
    }

    public static int deposit(ServerLevel level, ThaumcraftGolem golem, SealKey key, SealFilter filter) {
        if (!loaded(level, key)) return 0;
        IItemHandler target = handler(level, key); // Re-resolve at completion; do not cache a removed container.
        if (target != null) return insertCarried(golem, target, filter);
        EquipmentSlot slot = firstSlot(golem, filter);
        if (slot == null) return 0;
        ItemStack original = golem.getItemBySlot(slot).copy();
        int requested = amount(null, original, filter);
        if (requested <= 0) return 0;
        ItemEntity drop = new ItemEntity(level, key.pos().getX() + .5 + key.face().getStepX(),
                key.pos().getY() + .5 + key.face().getStepY(),
                key.pos().getZ() + .5 + key.face().getStepZ(), original.copyWithCount(requested));
        drop.setDeltaMovement(drop.getDeltaMovement().multiply(.2, .5, .2));
        if (!level.addFreshEntity(drop)) return 0;
        golem.setItemSlot(slot, original.copyWithCount(original.getCount() - requested));
        return requested;
    }
}
