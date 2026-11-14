package dev.ehtid.thaumcraft.golem.inventory;

import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** One synchronous owner for cargo while external capability or spawn callbacks can reenter. */
public final class GolemInventoryTransfer {
    private static final ThreadLocal<Set<ThaumcraftGolem>> ACTIVE=new ThreadLocal<>();
    private GolemInventoryTransfer() {}

    public static Scope begin(ThaumcraftGolem golem) {
        if(golem.level().isClientSide||!golem.isAlive()||golem.isRemoved())return null;
        var active=ACTIVE.get();
        if(active==null){active=Collections.newSetFromMap(new IdentityHashMap<>());ACTIVE.set(active);}
        return active.add(golem)?new Scope(golem):null;
    }

    /** Caller has already removed this actual payload from the worker's hands/recovery. */
    public static boolean publishCargo(ThaumcraftGolem golem,ItemEntity drop) {
        if(golem.level().addFreshEntity(drop))return true;
        var returned=drop.getItem();drop.setItem(ItemStack.EMPTY);
        golem.retainTransfer(returned);
        return false;
    }

    public static final class Scope implements AutoCloseable {
        private final ThaumcraftGolem golem;
        private boolean closed;
        private Scope(ThaumcraftGolem golem){this.golem=golem;}
        @Override public void close(){
            if(closed)return;closed=true;
            var active=ACTIVE.get();
            if(active!=null){active.remove(golem);if(active.isEmpty())ACTIVE.remove();}
        }
    }
}
