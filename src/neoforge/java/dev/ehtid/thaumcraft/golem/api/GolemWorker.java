package dev.ehtid.thaumcraft.golem.api;

import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import dev.ehtid.thaumcraft.golem.inventory.GolemInventoryTransfer;
import dev.ehtid.thaumcraft.port.core.golem.GolemDesign;
import dev.ehtid.thaumcraft.port.core.golem.GolemTrait;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Owner-bound modern worker operations derived from BETA26 {@code IGolemAPI}.
 * Addons can resolve a loaded worker by UUID without receiving mutable entity,
 * equipment or property handles. All calls belong to the owning server thread;
 * a removed, replaced, transferred or differently owned worker invalidates the
 * binding. The supplied owner UUID identifies the addon operation's principal;
 * this is a cooperation contract for trusted server addons, not authentication.
 *
 * <p>This functional subset does not register external seals or tasks and is not
 * the Minecraft 1.12 interface or its binary ABI. Modern capacity respects item
 * stack limits, property and carrying queries return snapshots, and cargo writes
 * share the runtime's synchronous inventory-transfer exclusion.</p>
 */
public final class GolemWorker {
    private final ServerLevel level;
    private final UUID owner;
    private final UUID workerId;
    private final ThaumcraftGolem worker;

    private GolemWorker(ServerLevel level, UUID owner, UUID workerId, ThaumcraftGolem worker) {
        this.level=level;
        this.owner=owner;
        this.workerId=workerId;
        this.worker=worker;
    }

    /** Resolves an already loaded worker; never searches or loads a chunk. */
    public static GolemWorker bind(ServerLevel level, UUID owner, UUID workerId) {
        Objects.requireNonNull(level,"level");
        checkThread(level);
        Objects.requireNonNull(owner,"owner");
        Objects.requireNonNull(workerId,"worker UUID");
        if(!(level.getEntity(workerId) instanceof ThaumcraftGolem worker))
            throw new IllegalArgumentException("No loaded golem with that UUID in this server level");
        var binding=new GolemWorker(level,owner,workerId,worker);
        binding.checkAccess();
        return binding;
    }

    private static void checkThread(ServerLevel level) {
        if(!level.getServer().isSameThread())
            throw new IllegalStateException("Golem worker operations belong to the server thread");
    }

    private void checkAccess() {
        checkThread(level);
        if(worker.level()!=level || !workerId.equals(worker.getUUID()) || !worker.isAlive()
                || worker.isRemoved() || level.getEntity(workerId)!=worker)
            throw new IllegalStateException("Golem worker binding no longer identifies the same live loaded entity");
        if(!owner.equals(worker.ownerId()))
            throw new SecurityException("Golem worker is not owned by the binding's owner");
    }

    /** Immutable runtime construction/rank view, with its current trait cancellation. */
    public GolemDesign getProperties() {
        checkAccess();
        return worker.design();
    }

    public boolean hasTrait(GolemTrait trait) {
        checkAccess();
        return worker.design().hasTrait(Objects.requireNonNull(trait,"trait"));
    }

    /** Copies of active carry slots in main-hand/offhand order, including empty slots. */
    public List<ItemStack> getCarrying() {
        checkAccess();
        var result=new ArrayList<ItemStack>();
        for(int i=0;i<worker.design().stats().carrySlots();i++)
            result.add(worker.getItemBySlot(slot(i)).copy());
        return List.copyOf(result);
    }

    /** Null/empty requests have no capacity; pending recovery excludes new cargo. */
    public int canCarryAmount(ItemStack stack) {
        checkAccess();
        return stack==null || stack.isEmpty() || worker.hasTransferRecovery()?0:worker.canCarryAmount(stack);
    }

    public boolean canCarry(ItemStack stack, boolean partial) {
        int capacity=canCarryAmount(stack);
        return capacity>0 && (partial || capacity>=stack.getCount());
    }

    /** Matches exact item components in any active carry slot; requested count is irrelevant. */
    public boolean isCarrying(ItemStack stack) {
        checkAccess();
        if(stack==null || stack.isEmpty())return false;
        for(int i=0;i<worker.design().stats().carrySlots();i++) {
            var held=worker.getItemBySlot(slot(i));
            if(!held.isEmpty() && ItemStack.isSameItemSameComponents(held,stack))return true;
        }
        return false;
    }

    /**
     * Offers caller-owned cargo to the live worker and returns its independent
     * remainder. Follows the modern runtime convention: the argument's data is
     * unchanged. The caller must relinquish its offered payload and replace its
     * own storage with the returned remainder; retaining both would duplicate
     * logical ownership. No alias to the supplied stack is stored in equipment.
     * Passing this worker's live equipment stack is rejected: it is already owned.
     * During another transfer or pending recovery, returns the entire offer.
     */
    public ItemStack holdItem(ItemStack stack) {
        checkAccess();
        if(stack==null || stack.isEmpty())return ItemStack.EMPTY;
        if(stack==worker.getMainHandItem() || stack==worker.getOffhandItem())
            throw new IllegalArgumentException("Offered cargo is already owned by this worker");
        var scope=GolemInventoryTransfer.begin(worker);
        if(scope==null)return stack.copy();
        try(scope) {
            return worker.hasTransferRecovery()?stack.copy():worker.holdItem(stack);
        }
    }

    /**
     * Detaches actual cargo without spawning or depositing it. Null/empty selects
     * the first nonempty hand; otherwise detaches up to the requested count from
     * the first exact component match. Like BETA26, one call never combines both
     * HAULER slots and compacts the second hand after freeing the first. The
     * request is unchanged; a direct live hand reference is rejected as a
     * selector. During another cargo transfer, returns empty.
     */
    public ItemStack dropItem(ItemStack request) {
        checkAccess();
        if(request!=null && !request.isEmpty()
                && (request==worker.getMainHandItem() || request==worker.getOffhandItem()))
            throw new IllegalArgumentException("Detach requests must not alias this worker's equipment");
        var scope=GolemInventoryTransfer.begin(worker);
        if(scope==null)return ItemStack.EMPTY;
        try(scope) {
            ItemStack result=ItemStack.EMPTY;
            int slots=worker.design().stats().carrySlots();
            for(int i=0;i<slots;i++) {
                var slot=slot(i);
                var held=worker.getItemBySlot(slot);
                if(held.isEmpty() || request!=null && !request.isEmpty()
                        && !ItemStack.isSameItemSameComponents(held,request))continue;
                int amount=request==null || request.isEmpty()?held.getCount():Math.min(request.getCount(),held.getCount());
                result=held.split(amount);
                worker.setItemSlot(slot,held);
                break;
            }
            if(slots>1 && worker.getMainHandItem().isEmpty() && !worker.getOffhandItem().isEmpty()) {
                var offhand=worker.getOffhandItem();
                worker.setItemSlot(EquipmentSlot.OFFHAND,ItemStack.EMPTY);
                worker.setItemSlot(EquipmentSlot.MAINHAND,offhand);
            }
            return result;
        }
    }

    /** Delegates source SMART-only rank experience and its current threshold rules. */
    public void addRankXp(int amount) {
        checkAccess();
        worker.addRankXp(amount);
    }

    public byte getGolemColor() {
        checkAccess();
        return worker.golemColor();
    }

    private static EquipmentSlot slot(int index) {
        return index==0?EquipmentSlot.MAINHAND:EquipmentSlot.OFFHAND;
    }
}
