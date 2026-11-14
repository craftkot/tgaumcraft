package dev.ehtid.thaumcraft.golem.inventory;

import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import dev.ehtid.thaumcraft.golem.seal.GolemSeals;
import dev.ehtid.thaumcraft.golem.seal.GolemSeals.SealKey;
import dev.ehtid.thaumcraft.golem.seal.SealFilter;
import dev.ehtid.thaumcraft.golem.seal.LogisticsPolicy;
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

    private static int transfer(ThaumcraftGolem golem,java.util.function.IntSupplier operation) {
        var scope=GolemInventoryTransfer.begin(golem);
        if(scope==null)return 0;
        try(scope){return operation.getAsInt();}
    }

    public static ItemStack emptyCandidate(ServerLevel level, SealKey key, LogisticsPolicy policy, boolean leaveOne) {
        return emptyCandidate(level,key,policy,leaveOne,0);
    }
    public static ItemStack emptyCandidate(ServerLevel level, SealKey key, LogisticsPolicy policy, boolean leaveOne,int completed) {
        var handler=handler(level,key);
        if(handler==null) return ItemStack.EMPTY;
        for(int i=0;i<handler.getSlots();i++) {
            var actual=handler.getStackInSlot(i);
            if(!policy.extractionAccepts(actual,completed)) continue;
            var filter=new SealFilter(actual,false,0,policy.options());
            if(leaveOne && matchingStock(handler,actual,filter)<2) continue;
            var preview=handler.extractItem(i,actual.getCount(),true);
            if(!preview.isEmpty() && filter.matches(actual,preview)) return actual.copy();
        }
        return ItemStack.EMPTY;
    }

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
        return transfer(golem,()->extractActual(golem,source,cached,filter,leaveOne));
    }

    private static int extractActual(ThaumcraftGolem golem,IItemHandler source,ItemStack cached,SealFilter filter,boolean leaveOne) {
        if (golem.level().isClientSide || source == null || golem.hasTransferRecovery() || !filter.accepts(cached)) return 0;
        int remaining = SealEmptyRules.requested(cached.getCount(), golem.canCarryAmount(cached), matchingStock(source, cached, filter), leaveOne);
        int moved = 0;
        for (int slot = 0; slot < source.getSlots() && remaining > 0 && !golem.hasTransferRecovery(); slot++) {
            if (!filter.matches(cached, source.getStackInSlot(slot))) continue;
            int requested = Math.min(remaining, golem.canCarryAmount(cached));
            if (requested <= 0) break;
            ItemStack actual = source.extractItem(slot, requested, false);
            if (actual.isEmpty()) continue;
            int count = actual.getCount();
            golem.retainTransfer(actual);
            moved += count;
            remaining -= count;
        }
        return moved;
    }

    private static long matchingStock(IItemHandler source, ItemStack template, SealFilter filter) {
        long count = 0;
        for (int slot = 0; slot < source.getSlots(); slot++) {
            ItemStack stack = source.getStackInSlot(slot);
            if (filter.matches(template, stack)) count += stack.getCount();
        }
        return count;
    }

    public static boolean loaded(ServerLevel level, SealKey key) {
        if (!GolemSeals.active(level,key.pos()) || !GolemSeals.active(level,key.pos().relative(key.face()))) return false;
        var state = level.getBlockState(key.pos());
        if (state.isAir()) return false;
        // Vanilla double-chest capability joins both halves. Never request its unloaded partner.
        return !(state.getBlock() instanceof ChestBlock) || state.getValue(ChestBlock.TYPE) == ChestType.SINGLE
                || GolemSeals.active(level,key.pos().relative(ChestBlock.getConnectedDirection(state)));
    }

    public static IItemHandler handler(ServerLevel level, SealKey key) {
        return loaded(level, key) ? level.getCapability(Capabilities.ItemHandler.BLOCK, key.pos(), key.face()) : null;
    }

    private static EquipmentSlot firstSlot(ThaumcraftGolem golem, SealFilter filter) {
        if (filter.accepts(golem.getMainHandItem())) return EquipmentSlot.MAINHAND;
        return golem.design().stats().carrySlots() > 1 && filter.accepts(golem.getOffhandItem())
                ? EquipmentSlot.OFFHAND : null;
    }

    private static EquipmentSlot validSlot(ThaumcraftGolem golem,SealFilter filter,EquipmentSlot slot) {
        return (slot==EquipmentSlot.MAINHAND || slot==EquipmentSlot.OFFHAND&&golem.design().stats().carrySlots()>1)
                && filter.accepts(golem.getItemBySlot(slot))?slot:null;
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

    private record FillSelection(EquipmentSlot slot,SealFilter filter) {}
    private static FillSelection advancedFillFilter(ThaumcraftGolem golem,IItemHandler target,LogisticsPolicy policy) {
        for(var slot:new EquipmentSlot[]{EquipmentSlot.MAINHAND,EquipmentSlot.OFFHAND}) {
            if(slot==EquipmentSlot.OFFHAND && golem.design().stats().carrySlots()<2) continue;
            var actual=golem.getItemBySlot(slot); var filter=policy.fillFilter(actual); if(filter==null) continue;
            // Source pexist gates only an actual handler, not fallback world drops.
            if(policy.existingOnly() && target!=null && matchingStock(target,actual,filter)<=0) return null;
            return new FillSelection(slot,filter);
        }
        return null;
    }
    public static boolean canDeposit(ServerLevel level,ThaumcraftGolem golem,SealKey key,LogisticsPolicy policy) {
        if(!loaded(level,key)) return false;
        var selected=advancedFillFilter(golem,handler(level,key),policy);
        return selected!=null && canDeposit(level,golem,key,selected.filter,selected.slot);
    }
    public static int deposit(ServerLevel level,ThaumcraftGolem golem,SealKey key,LogisticsPolicy policy) {
        return transfer(golem,()->{
            if(!loaded(level,key)) return 0;
            var selected=advancedFillFilter(golem,handler(level,key),policy);
            return selected==null?0:depositActual(level,golem,key,selected.filter,selected.slot);
        });
    }

    public static boolean canDeposit(ServerLevel level, ThaumcraftGolem golem, SealKey key) {
        return canDeposit(level, golem, key, SealFilter.defaults());
    }

    public static boolean canDeposit(ServerLevel level, ThaumcraftGolem golem, SealKey key, SealFilter filter) { return canDeposit(level,golem,key,filter,null); }
    private static boolean canDeposit(ServerLevel level,ThaumcraftGolem golem,SealKey key,SealFilter filter,EquipmentSlot selected) {
        if (!loaded(level, key)) return false;
        EquipmentSlot slot = selected==null?firstSlot(golem,filter):validSlot(golem,filter,selected);
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
        return insertCarried(golem,target,filter,Integer.MAX_VALUE);
    }

    public static int insertCarried(ThaumcraftGolem golem, IItemHandler target, SealFilter filter,int maximum) {
        return transfer(golem,()->insertActual(golem,target,filter,maximum,null));
    }
    private static int insertActual(ThaumcraftGolem golem,IItemHandler target,SealFilter filter,int maximum,EquipmentSlot selected) {
        if (golem.level().isClientSide) return 0;
        EquipmentSlot slot = selected==null?firstSlot(golem,filter):validSlot(golem,filter,selected);
        if (slot == null) return 0;
        ItemStack original = golem.getItemBySlot(slot);
        int requested = Math.min(maximum,amount(target, original, filter)); // Fresh count at actual completion, after other workers.
        // Stock getters are external callbacks too: retainTransfer may replace this hand.
        // Abort the stale operation; a fresh retry will use the actual newly funded cargo.
        if (requested <= 0 || golem.getItemBySlot(slot)!=original || !filter.accepts(original)) return 0;
        ItemStack extracted = original.split(requested);
        golem.setItemSlot(slot, original);
        ItemStack remainder = ItemHandlerHelper.insertItemStacked(target, extracted, false);
        int moved=requested-remainder.getCount();
        // The callback may have changed the actual remainder or funded new cargo/recovery.
        // Return what it really left, without overwriting current hands from an old snapshot.
        golem.retainTransfer(remainder);
        return moved;
    }

    public static int deposit(ServerLevel level, ThaumcraftGolem golem, SealKey key) {
        return deposit(level, golem, key, SealFilter.defaults());
    }

    public static int deposit(ServerLevel level, ThaumcraftGolem golem, SealKey key, SealFilter filter) { return deposit(level,golem,key,filter,null); }
    private static int deposit(ServerLevel level,ThaumcraftGolem golem,SealKey key,SealFilter filter,EquipmentSlot selected) {
        return transfer(golem,()->depositActual(level,golem,key,filter,selected));
    }
    private static int depositActual(ServerLevel level,ThaumcraftGolem golem,SealKey key,SealFilter filter,EquipmentSlot selected) {
        if (!loaded(level, key)) return 0;
        IItemHandler target = handler(level, key); // Re-resolve at completion; do not cache a removed container.
        if (target != null) return insertActual(golem, target, filter,Integer.MAX_VALUE,selected);
        EquipmentSlot slot = selected==null?firstSlot(golem,filter):validSlot(golem,filter,selected);
        if (slot == null) return 0;
        ItemStack original = golem.getItemBySlot(slot);
        int requested = amount(null, original, filter);
        if (requested <= 0) return 0;
        ItemStack actual=original.split(requested);
        golem.setItemSlot(slot,original);
        ItemEntity drop = new ItemEntity(level, key.pos().getX() + .5 + key.face().getStepX(),
                key.pos().getY() + .5 + key.face().getStepY(),
                key.pos().getZ() + .5 + key.face().getStepZ(), actual);
        drop.setDeltaMovement(drop.getDeltaMovement().multiply(.2, .5, .2));
        if (!GolemInventoryTransfer.publishCargo(golem,drop)) return 0;
        return requested;
    }
}
