package dev.ehtid.thaumcraft.port.core.golem;

import java.util.Objects;
import java.util.UUID;

/** Ephemeral BETA26 provisioning demand. K must be an immutable exact item/components key. */
public final class ProvisionRequest<K> {
    public static final long INITIAL_TIMEOUT_MILLIS = 10_000;
    public static final long LINK_TIMEOUT_MILLIS = 120_000;

    public sealed interface Target permits BlockTarget, EntityTarget, SealTarget {}
    public record BlockTarget(SealArea.SealPos location) implements Target {
        public BlockTarget { Objects.requireNonNull(location); }
    }
    public record EntityTarget(UUID entityId) implements Target {
        public EntityTarget { Objects.requireNonNull(entityId); }
    }
    /** Instance identity prevents delivery to a replacement seal at the same face. */
    public record SealTarget(SealArea.SealPos location, UUID instanceId) implements Target {
        public SealTarget { Objects.requireNonNull(location); Objects.requireNonNull(instanceId); }
    }

    /** Source deduplication includes count and caller UI discriminator, not just item type. */
    public record Key<K>(Target target, K item, int count, int ui) {
        public Key {
            Objects.requireNonNull(target); Objects.requireNonNull(item);
            if (count <= 0) throw new IllegalArgumentException("Nonpositive provision demand");
        }
    }

    private final long id;
    private final Key<K> key;
    private GolemTask linkedTask;
    private boolean invalid;
    private long timeout;

    ProvisionRequest(long id, Key<K> key, long now) {
        this.id = id;
        this.key = key;
        timeout = Math.addExact(now, INITIAL_TIMEOUT_MILLIS);
    }

    public long id() { return id; }
    public Key<K> key() { return key; }
    public Target target() { return key.target(); }
    public K item() { return key.item(); }
    public int count() { return key.count(); }
    public GolemTask linkedTask() { return linkedTask; }
    public boolean invalid() { return invalid; }
    public long timeout() { return timeout; }

    void link(GolemTask task, long now) {
        // Original setLinkedTask(null) also resets this deadline.
        long deadline = Math.addExact(now, LINK_TIMEOUT_MILLIS);
        linkedTask = task;
        timeout = deadline;
    }

    void invalidate() { invalid = true; }
}
