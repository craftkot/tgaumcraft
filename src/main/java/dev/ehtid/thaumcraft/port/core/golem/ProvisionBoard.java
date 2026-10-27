package dev.ehtid.thaumcraft.port.core.golem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/** One server world's requests, deliberately never serialized with seals or cargo. */
public final class ProvisionBoard<K> implements AutoCloseable {
    public static final int MAX_REQUESTS = 1_000;
    private static final AtomicLong IDS = new AtomicLong();
    private final Thread serverThread = Thread.currentThread();
    private final LongSupplier clock;
    private final Map<ProvisionRequest.Key<K>, ProvisionRequest<K>> requests = new LinkedHashMap<>();
    private boolean closed;

    public ProvisionBoard(LongSupplier clock) { this.clock = Objects.requireNonNull(clock); }

    private void checkThread() {
        if (Thread.currentThread() != serverThread)
            throw new IllegalStateException("Provision requests belong to the server world thread");
    }

    /** Duplicates return the original request without refreshing its deadline. */
    public Optional<ProvisionRequest<K>> request(ProvisionRequest.Target target, K item, int count, int ui) {
        checkThread();
        var key = new ProvisionRequest.Key<>(target, item, count, ui);
        if (closed) return Optional.empty();
        var old = requests.get(key);
        if (old != null) return Optional.of(old);
        // Port guard: reject excess demand rather than evict a potentially active delivery.
        if (requests.size() >= MAX_REQUESTS) return Optional.empty();
        var next = new ProvisionRequest<>(IDS.incrementAndGet(), key, clock.getAsLong());
        requests.put(key, next);
        return Optional.of(next);
    }

    public List<ProvisionRequest<K>> snapshot() {
        checkThread();
        return List.copyOf(requests.values());
    }

    private boolean owns(ProvisionRequest<K> request) {
        return !closed && request != null && requests.get(request.key()) == request;
    }

    /** Compare the previous task by identity: stale pickup callbacks cannot steal a delivery. */
    public boolean link(ProvisionRequest<K> request, GolemTask expectedPrevious, GolemTask next) {
        checkThread();
        Objects.requireNonNull(next);
        if (!owns(request) || request.invalid() || request.timeout() < clock.getAsLong()
                || request.linkedTask() != expectedPrevious || expectedPrevious == next
                || next.getLinkedProvision() != null || next.isSuspended() || next.isCompleted()
                || next.getLifespan() <= 0) return false;
        request.link(next, clock.getAsLong());
        if (expectedPrevious != null && expectedPrevious.getLinkedProvision() == request)
            expectedPrevious.setLinkedProvision(null);
        next.setLinkedProvision(request);
        return true;
    }

    /** Mirrors provider onTaskSuspension, guarded against a predecessor's late callback. */
    public boolean unlink(ProvisionRequest<K> request, GolemTask expected) {
        checkThread();
        if (!owns(request) || expected == null || request.linkedTask() != expected) return false;
        request.link(null, clock.getAsLong());
        if (expected.getLinkedProvision() == request) expected.setLinkedProvision(null);
        return true;
    }

    public boolean invalidate(ProvisionRequest<K> request) {
        checkThread();
        if (!owns(request) || request.invalid()) return false;
        request.invalidate();
        return true;
    }

    /** BETA26 provider sweep (each100 ticks): even unlinked requests are removed. */
    public void sweep() {
        checkThread();
        if (closed) return;
        long now = clock.getAsLong();
        for (var request : new ArrayList<>(requests.values())) {
            var task = request.linkedTask();
            if (request.invalid() || task == null || task.isSuspended() || task.isCompleted()
                    || request.timeout() < now) {
                requests.remove(request.key());
                retire(request);
            }
        }
    }

    private void retire(ProvisionRequest<K> request) {
        var task = request.linkedTask();
        if (task != null) {
            if (task.getLinkedProvision() == request) {
                // Modern cancellation guard; cargo remains on the worker, never recreated here.
                task.setSuspended(true);
            }
            request.link(null, clock.getAsLong());
        }
        request.invalidate();
    }

    public boolean isClosed() { checkThread(); return closed; }

    @Override public void close() {
        checkThread();
        if (closed) return;
        for (var request : requests.values()) retire(request);
        requests.clear();
        closed = true;
    }
}
