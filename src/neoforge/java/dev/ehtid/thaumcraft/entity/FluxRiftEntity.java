package dev.ehtid.thaumcraft.entity;

import dev.ehtid.thaumcraft.port.core.LegacyFluxRiftGeometry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Initial BETA26 Flux Rift entity state.
 *
 * This slice ports the synced seed/size/stability/collapse state, source-seeded
 * polycone geometry, per-tick segment entity damage/item removal and timed
 * stability decay. Block ray-trace/destruction, growth, weighted events and
 * collapse behavior remain separate follow-up work; this entity is not full parity.
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
        entityData.set(COLLAPSE, tag.getBoolean("collapse"));
    }

    @Override
    public void tick() {
        // SynchedEntityData updates arrive without calling the public setters.
        // Rebuild the geometry bounds on the first tick that observes them.
        if (getRiftSeed() != cachedSeed || getRiftSize() != cachedSize) {
            refreshDimensions();
        }
        super.tick();
        if (!level().isClientSide) {
            if (getRiftSeed() == 0) {
                setRiftSeed(level().getRandom().nextInt());
            }
            applySegmentEntityEffects();
            // BETA26 EntityFluxRift.onUpdate applies this on ticksExisted % 120 == 0.
            if (tickCount % 120 == 0) {
                setRiftStability(getRiftStability() - 0.2F);
            }
        }
    }

    @Override
    public void move(MoverType type, Vec3 movement) {
        // BETA26 EntityFluxRift.move(MoverType, double, double, double) is
        // intentionally empty: a Rift is anchored and cannot be pushed or moved.
    }

    /**
     * Applies the BETA26 per-tick entity effect at one randomly selected
     * adjacent pair of seeded polycone points. The source EntityUtils query is
     * an axis-aligned cube with a 0.5-block half-extent, not a sphere.
     */
    private void applySegmentEntityEffects() {
        List<LegacyFluxRiftGeometry.Point> points = getGeometry();
        if (points.size() < 2) {
            return;
        }

        int pointIndex = level().getRandom().nextInt(points.size() - 1);
        LegacyFluxRiftGeometry.Point point = points.get(pointIndex);
        double x = getX() + point.x();
        double y = getY() + point.y();
        double z = getZ() + point.z();
        AABB query = new AABB(x, y, z, x, y, z).inflate(0.5D);

        for (Entity target : level().getEntitiesOfClass(Entity.class, query)) {
            if (target == this || !target.isAlive()) {
                continue;
            }
            if (target instanceof Player player && player.isCreative()) {
                continue;
            }

            try {
                target.hurt(level().damageSources().fellOutOfWorld(), 2.0F);
                if (target instanceof ItemEntity) {
                    target.discard();
                }
            } catch (Exception ignored) {
                // BETA26 isolates failures per target and continues the tick.
            }
        }
    }

    @Override
    protected AABB makeBoundingBox() {
        // Entity's constructor can dispatch here before subclass field initializers.
        if (geometry == null) {
            return super.makeBoundingBox();
        }

        List<LegacyFluxRiftGeometry.Point> points = getGeometry();
        if (points.isEmpty()) {
            return super.makeBoundingBox();
        }

        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (LegacyFluxRiftGeometry.Point point : points) {
            minX = Math.min(minX, point.x());
            minY = Math.min(minY, point.y());
            minZ = Math.min(minZ, point.z());
            maxX = Math.max(maxX, point.x());
            maxY = Math.max(maxY, point.y());
            maxZ = Math.max(maxZ, point.z());
        }

        // BETA26 EntityFluxRift.setSize builds the entity AABB from the same
        // seeded polycone points, not from the fixed 2x2 EntityType dimensions.
        return new AABB(
                getX() + minX, getY() + minY, getZ() + minZ,
                getX() + maxX, getY() + maxY, getZ() + maxZ
        );
    }

    public int getRiftSeed() {
        return entityData.get(SEED);
    }

    public void setRiftSeed(int seed) {
        entityData.set(SEED, seed);
        cachedSeed = Integer.MIN_VALUE;
        refreshDimensions();
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
