package dev.ehtid.thaumcraft.golem.seal;

import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.golem.inventory.GolemContainers;
import dev.ehtid.thaumcraft.port.core.golem.ProvisionBoard;
import dev.ehtid.thaumcraft.port.core.golem.ProvisionRequest;
import dev.ehtid.thaumcraft.port.core.golem.ProvisionRules;
import dev.ehtid.thaumcraft.port.core.golem.SealArea;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Server request API; full Provide/Stock seal items, filters UI and AI execution are separate work. */
@EventBusSubscriber(modid=ThaumcraftPort.MOD_ID, bus=EventBusSubscriber.Bus.GAME)
public final class GolemProvisioning {
    private static final Map<ServerLevel, ProvisionBoard<ItemKey>> BOARDS = new IdentityHashMap<>();
    private GolemProvisioning() {}

    /** Defensive immutable identity. No reference to inventory/ghost stacks escapes into a request. */
    public static final class ItemKey {
        private final ItemStack item;
        public ItemKey(ItemStack stack) {
            if (stack.isEmpty()) throw new IllegalArgumentException("Empty provision item");
            item = stack.copyWithCount(1);
        }
        public ItemStack stack(int count) { return item.copyWithCount(count); }
        @Override public boolean equals(Object other) {
            return other instanceof ItemKey key && ItemStack.isSameItemSameComponents(item, key.item);
        }
        @Override public int hashCode() { return 31 * item.getItem().hashCode() + item.getComponents().hashCode(); }
    }

    private static void checkThread(ServerLevel level) {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("Provision API requires server thread");
    }

    public static ProvisionBoard<ItemKey> get(ServerLevel level) {
        checkThread(level);
        return BOARDS.computeIfAbsent(level, ignored -> new ProvisionBoard<>(System::currentTimeMillis));
    }

    public static SealArea.SealPos location(GolemSeals.SealKey key) {
        var pos = key.pos();
        return new SealArea.SealPos(new SealArea.BlockPos(pos.getX(), pos.getY(), pos.getZ()),
                SealArea.Facing.values()[key.face().ordinal()]);
    }

    public static Optional<ProvisionRequest<ItemKey>> request(ServerLevel level, ProvisionRequest.Target target,
                                                             ItemStack stack, int ui) {
        checkThread(level);
        if (stack.isEmpty()) return Optional.empty();
        return get(level).request(target, new ItemKey(stack), stack.getCount(), ui);
    }

    /** Stock is always a nine-slot whitelist; each slot has its own desired total. */
    public static List<ProvisionRequest<ItemKey>> stock(ServerLevel level, GolemSeals.SealKey recipient,
                                                       List<SealFilter> filters) {
        checkThread(level);
        if (filters.size() != ProvisionRules.FILTER_SLOTS)
            throw new IllegalArgumentException("Stock needs exactly nine filter slots");
        var handler = GolemContainers.handler(level, recipient);
        if (handler == null) return List.of();
        var result = new ArrayList<ProvisionRequest<ItemKey>>();
        for (var filter : filters) {
            var template = filter.item();
            if (template.isEmpty() || filter.limit() <= 0) continue;
            long existing = 0;
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                var visible = handler.getStackInSlot(slot);
                if (filter.matches(template, visible)) existing += visible.getCount();
            }
            int deficit = ProvisionRules.stockDemand(filter.limit(), existing, template.getMaxStackSize(), Integer.MAX_VALUE);
            if (deficit == 0) continue;
            var offered = template.copyWithCount(deficit);
            var remainder = ItemHandlerHelper.insertItemStacked(handler, offered.copy(), true);
            int amount = ProvisionRules.stockDemand(filter.limit(), existing, template.getMaxStackSize(),
                    Math.max(0, deficit - remainder.getCount()));
            if (amount > 0) request(level, new ProvisionRequest.BlockTarget(location(recipient)),
                    template.copyWithCount(amount), 0).ifPresent(result::add);
        }
        return List.copyOf(result);
    }

    /** The port centralizes the source provider sweep once/world rather than once/provider. */
    @SubscribeEvent public static void tick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level && level.getGameTime() % ProvisionRules.SWEEP_TICKS == 0) {
            var board = BOARDS.get(level);
            if (board != null) board.sweep();
        }
    }

    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            checkThread(level);
            var board = BOARDS.remove(level);
            if (board != null) board.close();
        }
    }
}
