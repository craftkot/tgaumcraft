package dev.ehtid.thaumcraft.port.core.golem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Ephemeral board owned by one loaded server world and its server thread. Close
 * on world unload, never serialize tasks/leases. The adapter owns world identity.
 * Tokens prevent a stopped goal from releasing work acquired by another golem.
 */
public final class TaskBoard implements AutoCloseable {
    public static final int MAX_TASKS = 10_000;
    public static final int DEFAULT_LEASE_SECONDS = 120;
    // Process-wide tokens also reject a lease retained from a closed world board.
    private static final AtomicLong TOKENS = new AtomicLong();

    public record Lease(int taskId, UUID owner, long token) {
        public Lease { Objects.requireNonNull(owner, "owner"); }
    }

    private record Reservation(Lease lease, long expiresAt) {}

    private final Thread serverThread = Thread.currentThread();
    private final Map<Integer, GolemTask> tasks = new LinkedHashMap<>();
    private final Map<Integer, Reservation> reservations = new LinkedHashMap<>();
    private final int leaseSeconds;
    private long seconds;
    private boolean closed;

    public TaskBoard() { this(DEFAULT_LEASE_SECONDS); }

    /** Port watchdog, separate from the source Task's lifespan extension arithmetic. */
    public TaskBoard(int leaseSeconds) {
        if (leaseSeconds <= 0) throw new IllegalArgumentException("Nonpositive lease duration");
        this.leaseSeconds = leaseSeconds;
    }

    private void checkThread() {
        if (Thread.currentThread() != serverThread) {
            throw new IllegalStateException("Golem tasks belong to the server world thread");
        }
    }

    public boolean add(GolemTask task) {
        checkThread();
        Objects.requireNonNull(task, "task");
        // Replacing an ID could transfer a stale reservation to unrelated work.
        // Unlike the original arbitrary eviction at 10001, reject excess work.
        if (closed || tasks.size() >= MAX_TASKS || tasks.containsKey(task.getId())) return false;
        tasks.put(task.getId(), task);
        return true;
    }

    public Optional<GolemTask> get(int taskId) {
        checkThread();
        return Optional.ofNullable(tasks.get(taskId));
    }

    public List<GolemTask> snapshot() {
        checkThread();
        return List.copyOf(tasks.values());
    }

    public List<GolemTask> sorted(GolemTask.Type type, TaskSelection.Context context) {
        checkThread();
        return TaskSelection.sorted(tasks.values(), type, context);
    }

    public Optional<Lease> claim(int taskId, UUID owner) {
        checkThread();
        Objects.requireNonNull(owner, "owner");
        GolemTask task = tasks.get(taskId);
        if (closed || task == null || task.isReserved() || task.isSuspended()
                || task.isCompleted() || task.getLifespan() <= 0
                || reservations.containsKey(taskId)
                || task.getGolemUUID() != null && !owner.equals(task.getGolemUUID())) {
            return Optional.empty();
        }
        Lease lease = new Lease(taskId, owner, TOKENS.incrementAndGet());
        reservations.put(taskId, new Reservation(lease, seconds + leaseSeconds));
        task.setReserved(true);
        return Optional.of(lease);
    }

    public Optional<GolemTask> task(Lease lease) {
        checkThread();
        return owns(lease) ? Optional.ofNullable(tasks.get(lease.taskId)) : Optional.empty();
    }

    private boolean owns(Lease lease) {
        if (closed || lease == null) return false;
        Reservation reservation = reservations.get(lease.taskId);
        return reservation != null && reservation.lease.equals(lease);
    }

    public boolean release(Lease lease) {
        checkThread();
        if (!owns(lease)) return false;
        reservations.remove(lease.taskId);
        tasks.get(lease.taskId).setReserved(false);
        return true;
    }

    /** As in completeTask, suspended/completed work cannot be completed again. */
    public boolean complete(Lease lease, boolean fulfilled) {
        checkThread();
        if (!owns(lease)) return false;
        GolemTask task = tasks.get(lease.taskId);
        if (task.isSuspended() || task.isCompleted()) return false;
        task.setCompletion(fulfilled);
        return true;
    }

    public boolean suspend(Lease lease) {
        checkThread();
        if (!owns(lease)) return false;
        tasks.get(lease.taskId).setSuspended(true);
        return true;
    }

    /** Call exactly once per 20 server ticks (world END), not once per golem. */
    public void tickSecond(Consumer<GolemTask> onSuspension) {
        checkThread();
        Objects.requireNonNull(onSuspension, "onSuspension");
        if (closed) return;
        seconds++;
        // Snapshot permits the callback to enqueue successor work safely.
        for (GolemTask task : new ArrayList<>(tasks.values())) {
            if (task.isSuspended() || task.getLifespan() <= 0) {
                tasks.remove(task.getId());
                Reservation reservation = reservations.remove(task.getId());
                if (reservation != null) task.setReserved(false);
                onSuspension.accept(task);
                continue;
            }
            task.setLifespan((short) (task.getLifespan() - 1));
            Reservation reservation = reservations.get(task.getId());
            if (reservation != null && reservation.expiresAt <= seconds) {
                release(reservation.lease);
            }
        }
    }

    public boolean isClosed() { return closed; }

    @Override
    public void close() {
        checkThread();
        if (closed) return;
        for (Reservation reservation : reservations.values()) {
            GolemTask task = tasks.get(reservation.lease.taskId);
            if (task != null) task.setReserved(false);
        }
        reservations.clear();
        tasks.clear();
        closed = true;
    }
}
