package dev.ehtid.thaumcraft.port.core.golem;

import java.util.Objects;
import java.util.UUID;

/**
 * Ephemeral BETA26 Task data. Deliberately retains the source's short arithmetic;
 * safe ownership belongs to TaskBoard rather than changing these source rules.
 * No task or lease is persisted across a world/server restart.
 */
public final class GolemTask {
    public enum Type { BLOCK, ENTITY }

    private final int id;
    private final Type type;
    private final SealArea.SealPos sealPos;
    private final SealArea.BlockPos pos;
    private final UUID entityId;
    private UUID golemUUID;
    private boolean reserved;
    private boolean suspended;
    private boolean completed;
    private int data;
    private Object linkedProvision;
    private short lifespan = 300;
    private byte priority;

    private GolemTask(int id, Type type, SealArea.SealPos sealPos,
                      SealArea.BlockPos pos, UUID entityId) {
        this.id = id;
        this.type = type;
        this.sealPos = sealPos;
        this.pos = pos;
        this.entityId = entityId;
    }

    /** IDs are allocated by the server adapter, avoiding source millisecond hash collisions. */
    public static GolemTask block(int id, SealArea.SealPos sealPos, SealArea.BlockPos target) {
        return new GolemTask(id, Type.BLOCK, sealPos, Objects.requireNonNull(target), null);
    }

    public static GolemTask entity(int id, SealArea.SealPos sealPos, UUID target) {
        return new GolemTask(id, Type.ENTITY, sealPos, null, Objects.requireNonNull(target));
    }

    public int getId() { return id; }
    public Type getType() { return type; }
    public SealArea.SealPos getSealPos() { return sealPos; }
    /** Entity positions are resolved from the current server world by TaskSelection. */
    public SealArea.BlockPos getPos() { return pos; }
    public UUID getEntityId() { return entityId; }
    public UUID getGolemUUID() { return golemUUID; }
    public void setGolemUUID(UUID golemUUID) { this.golemUUID = golemUUID; }
    public byte getPriority() { return priority; }
    public void setPriority(byte priority) { this.priority = priority; }
    public int getData() { return data; }
    public void setData(int data) { this.data = data; }
    public long getLifespan() { return lifespan; }
    public void setLifespan(short lifespan) { this.lifespan = lifespan; }
    public boolean isReserved() { return reserved; }
    public boolean isSuspended() { return suspended; }
    public boolean isCompleted() { return completed; }
    public Object getLinkedProvision() { return linkedProvision; }
    public void setLinkedProvision(Object provision) { linkedProvision = provision; }

    public void setReserved(boolean reserved) {
        this.reserved = reserved;
        lifespan += 120;
    }

    public void setCompletion(boolean completed) {
        this.completed = completed;
        lifespan += 1;
    }

    public void setSuspended(boolean suspended) {
        linkedProvision = null;
        this.suspended = suspended;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof GolemTask task && task.id == id;
    }

    @Override
    public int hashCode() { return Integer.hashCode(id); }
}
