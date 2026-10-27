package dev.ehtid.thaumcraft.port.core.golem;

/** Confirmed BETA26 SealStock/SealProvide rules; inventory mutations belong to the server adapter. */
public final class ProvisionRules {
    public static final int FILTER_SLOTS = 9;
    public static final int INTERVAL_TICKS = 20;
    public static final int SWEEP_TICKS = 100;
    public static final double RANGE_SQUARED = 4096;
    private ProvisionRules() {}

    /** Stock requests one stack of the deficit, further bounded by simulated insertion room. */
    public static int stockDemand(int desired, long existing, int stackMaximum, int room) {
        if (desired <= 0 || stackMaximum <= 0 || room <= 0) return 0;
        return (int) Math.min(Math.min(stackMaximum, room), Math.max(0L, desired - Math.max(0L, existing)));
    }

    public static boolean inProviderRange(double distanceSquared) {
        return distanceSquared >= 0 && distanceSquared < RANGE_SQUARED;
    }

    /** psing and pleave constrain extraction, independently of the request's original count. */
    public static int pickupAmount(int requested, int capacity, long strictStock, boolean single, boolean leaveOne) {
        if (requested <= 0 || capacity <= 0 || strictStock <= 0) return 0;
        long available = strictStock - (leaveOne ? 1 : 0);
        return (int) Math.max(0L, Math.min(Math.min(single ? 1 : requested, capacity), available));
    }

    public static GolemTask pickup(int id, SealArea.SealPos provider, ProvisionRequest<?> request, byte recipientPriority) {
        var task = GolemTask.block(id, provider, provider.pos());
        boolean seal = request.target() instanceof ProvisionRequest.SealTarget;
        task.setPriority(seal ? recipientPriority : (byte) 5);
        task.setLifespan(seal ? (short) 10 : (short) 31_000);
        return task;
    }

    /** Seal recipients create their own work; only block/entity requests get provider delivery work. */
    public static GolemTask delivery(int id, GolemTask pickup, ProvisionRequest<?> request) {
        GolemTask task;
        if (request.target() instanceof ProvisionRequest.BlockTarget block) {
            task = GolemTask.block(id, pickup.getSealPos(), block.location().pos());
            task.setData(2);
        } else if (request.target() instanceof ProvisionRequest.EntityTarget entity) {
            task = GolemTask.entity(id, pickup.getSealPos(), entity.entityId());
            task.setData(1);
        } else {
            throw new IllegalArgumentException("Seal recipients do not use provider delivery tasks");
        }
        task.setPriority(pickup.getPriority());
        task.setLifespan((short) 31_000);
        return task;
    }
}
