package dev.ehtid.thaumcraft.entity;

import dev.ehtid.thaumcraft.port.core.LegacyFluxRiftGeometry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Initial BETA26 Flux Rift entity state.
 *
 * This slice ports the synced seed/size/stability/collapse state and the
 * source-seeded polycone geometry. Rift damage, growth, events and collapse
 * are deliberately separate follow-up work; this entity is not full parity.
 */
public final class FluxRiftEntity extends Entity {
    private static final EntityDataAccessor<Integer> SEED =
            SynchedEntityData.defineId(FluxRiftEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SIZE =
            SynchedEntityData.defineId(FluxRiftEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> STABILITY =
            SynchedEntityData.defineId(FluxRiftEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> COLLAPSE =
            SynchedEntityData.defineId(FluxRiftEntity.class, EntityDataSerializers.BOOLEAN);

    private int maxSize;
    private int cachedSeed = Integer.MIN_VALUE;
    private int cachedSize = Integer.MIN_VALUE;
    private List<LegacyFluxRiftGeometry.Point> geometry = List.of();

    public FluxRiftEntity(EntityType<? extends FluxRiftEntity> type, Level level) {
        super(type, level);
        this.maxSize = 0;
        setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(SEED, 0);
        builder.define(SIZE, 5);
        builder.define(STABILITY, 0.0F);
        builder.define(COLLAPSE, false);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("MaxSize", maxSize);
        tag.putInt("RiftSize", getRiftSize());
        tag.putInt("RiftSeed", getRiftSeed());
        tag.putFloat("Stability", getRiftStability());
        tag.putBoolean("collapse", getCollapse());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        maxSize = tag.getInt("MaxSize");
        setRiftSize(tag.getInt("RiftSize"));
        setRiftSeed(tag.getInt("RiftSeed"));
        setRiftStability(tag.getFloat("Stability"));
        setCollapse(tag.getBoolean("collapse"));
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && getRiftSeed() == 0) {
            setRiftSeed(random.nextInt());
        }
    }

    public int getRiftSeed() {
        return entityData.get(SEED);
    }

    public void setRiftSeed(int seed) {
        entityData.set(SEED, seed);
    }

    public int getRiftSize() {
        return entityData.get(SIZE);
    }

    public void setRiftSize(int size) {
        entityData.set(SIZE, Math.max(0, size));
        cachedSize = Integer.MIN_VALUE;
        refreshDimensions();
    }

    public float getRiftStability() {
        return entityData.get(STABILITY);
    }

    public void setRiftStability(float stability) {
        entityData.set(STABILITY, Math.max(-100.0F, Math.min(100.0F, stability)));
    }

    public boolean getCollapse() {
        return entityData.get(COLLAPSE);
    }

    public void setCollapse(boolean collapse) {
        if (collapse) maxSize = getRiftSize();
        entityData.set(COLLAPSE, collapse);
    }

    public int getMaxSize() {
        return maxSize;
    }

    public List<LegacyFluxRiftGeometry.Point> getGeometry() {
        int seed = getRiftSeed();
        int size = getRiftSize();
        if (seed != cachedSeed || size != cachedSize) {
            geometry = LegacyFluxRiftGeometry.generate(size, seed);
            cachedSeed = seed;
            cachedSize = size;
        }
        return geometry;
    }
}
