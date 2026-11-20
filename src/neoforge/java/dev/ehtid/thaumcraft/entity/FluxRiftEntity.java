package dev.ehtid.thaumcraft.entity;

import dev.ehtid.thaumcraft.port.core.LegacyFluxRiftGeometry;
import dev.ehtid.thaumcraft.port.core.LegacyFluxRiftEventLogic;
import dev.ehtid.thaumcraft.registry.ThaumcraftItems;
import dev.ehtid.thaumcraft.registry.ThaumcraftSounds;
import dev.ehtid.thaumcraft.world.ThaumcraftAuraSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.util.RandomSource;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Initial BETA26 Flux Rift entity state.
 *
 * This slice ports the synced seed/size/stability/collapse state, source-seeded
 * polycone geometry, per-tick segment block destruction/entity damage/item removal,
 * timed stability decay, and the BETA26 collapse countdown/aura/explosion phase.
 * Flux-driven growth and the BETA26 weighted collapse event are implemented. The vanilla
 * Weakness portion of collapse completion is also ported; Wisp, taint-seed, infection and
 * focus events, void-seed drops, Flux Taint and player warp remain separate follow-up work.
 * This entity is not full parity.
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
        if (!level().isClientSide && smokeProbeEnabled()
                && (tickCount == 1 || tickCount % 100 == 0)) {
            System.out.println("TC_SMOKE_RIFT_TICK=YES entityId=" + getId()
                    + " tickCount=" + tickCount + " gameTime=" + level().getGameTime()
                    + " removed=" + isRemoved() + " position=" + position());
        }
        if (!level().isClientSide) {
            if (getRiftSeed() == 0) {
                setRiftSeed(level().getRandom().nextInt());
            }
            applySegmentEffects();
            if (getGeometry().size() < 3 && !getCollapse()) {
                setCollapse(true);
            }
            if (getCollapse()) {
                tickCollapse(random);
                if (isRemoved()) {
                    return;
                }
            }
            // BETA26 EntityFluxRift.onUpdate applies this on ticksExisted % 120 == 0.
            if (tickCount % 120 == 0) {
                setRiftStability(getRiftStability() - 0.2F);
            }
            // BETA26 EntityFluxRift.onUpdate: one growth/event opportunity per
            // entity-specific 600-tick phase, after collapse and stability decay.
            if (tickCount % 600 == getId() % 600) {
                tryGrowFromFlux();
                tryExecuteInstabilityEvent();
            }
            // BETA26 EntityFluxRift.onUpdate: the living Rift emits evilportal every 300 ticks.
            playAmbientSoundIfDue(random);
        }
    }

    /**
     * BETA26 EntityFluxRift.onUpdate plays evilportal when ticksExisted is divisible by 300.
     * Entity.playSound preserves the original Entity sound category (NEUTRAL in BETA26).
     */
    private static boolean smokeProbeEnabled() {
        return "1".equals(System.getenv("THAUMCRAFT_SMOKE"));
    }

    /**
     * Finds a repeatable audible sample for the real-client smoke without changing
     * the BETA26 Gaussian formula used during ordinary gameplay.
     */
    static long smokeAmbientSoundSeed() {
        for (long seed = 0; seed < 100_000L; seed++) {
            RandomSource candidate = RandomSource.create(seed);
            float volume = (float) (0.15F + candidate.nextGaussian() * 0.066D);
            float pitch = (float) (0.75D + candidate.nextGaussian() * 0.1D);
            if (volume > 0.05F && volume < 0.35F && pitch > 0.2F && pitch < 1.3F) {
                return seed;
            }
        }
        throw new IllegalStateException("Unable to find deterministic BETA26 positive-volume sound seed");
    }

    void playAmbientSoundIfDue(RandomSource sourceRandom) {
        if (tickCount % 300 != 0 || isRemoved()) {
            return;
        }
        boolean smoke = smokeProbeEnabled();
        long smokeSeed = -1L;
        RandomSource eventRandom = sourceRandom;
        if (smoke) {
            smokeSeed = smokeAmbientSoundSeed();
            eventRandom = RandomSource.create(smokeSeed);
        }
        float volume = (float) (0.15F + eventRandom.nextGaussian() * 0.066D);
        float pitch = (float) (0.75D + eventRandom.nextGaussian() * 0.1D);
        if (smoke) {
            System.out.println("TC_SMOKE_RIFT_AMBIENT_RANDOM=DETERMINISTIC seed=" + smokeSeed);
            double audibleRadius = Math.max(0.0D, (double) volume * 16.0D);
            long eligiblePlayers = volume <= 0.0F ? 0L : level().players().stream()
                    .filter(player -> player.distanceToSqr(this) <= audibleRadius * audibleRadius)
                    .count();
            double nearestPlayerDistanceSqr = level().players().stream()
                    .mapToDouble(player -> player.distanceToSqr(this))
                    .min().orElse(Double.POSITIVE_INFINITY);
            System.out.println("TC_SMOKE_RIFT_AMBIENT_DUE=YES entityId=" + getId()
                    + " tickCount=" + tickCount + " gameTime=" + level().getGameTime()
                    + " pos=" + getX() + "," + getY() + "," + getZ()
                    + " volume=" + volume + " pitch=" + pitch + " silent=" + isSilent()
                    + " eligiblePlayers=" + eligiblePlayers
                    + " nearestPlayerDistanceSqr=" + nearestPlayerDistanceSqr);
        }
        playSound(ThaumcraftSounds.EVILPORTAL.get(), volume, pitch);
        if (smoke) {
            System.out.println("TC_SMOKE_RIFT_AMBIENT_CALL=YES entityId=" + getId()
                    + " tickCount=" + tickCount);
        }
    }

    @Override
    public void move(MoverType type, Vec3 movement) {
        // BETA26 EntityFluxRift.move(MoverType, double, double, double) is
        // intentionally empty: a Rift is anchored and cannot be pushed or moved.
    }

    /**
     * Applies the BETA26 per-tick effects to one randomly selected adjacent
     * polycone segment. The original ray trace ignores fluids and only removes
     * collidable blocks whose hardness is non-negative.
     */
    private void applySegmentEffects() {
        List<LegacyFluxRiftGeometry.Point> points = getGeometry();
        if (points.size() < 2) {
            return;
        }

        int pointIndex = level().getRandom().nextInt(points.size() - 1);
        applySegmentEffectsAt(pointIndex);
    }

    /**
     * Executes the production effects for one explicit segment. Package access
     * lets the same-package Native GameTest exercise a deterministic ray without
     * replacing the world's random source used by normal ticks.
     */
    void applySegmentEffectsAt(int pointIndex) {
        List<LegacyFluxRiftGeometry.Point> points = getGeometry();
        if (pointIndex < 0 || pointIndex >= points.size() - 1) {
            return;
        }

        LegacyFluxRiftGeometry.Point point = points.get(pointIndex);
        LegacyFluxRiftGeometry.Point nextPoint = points.get(pointIndex + 1);
        Vec3 start = new Vec3(getX() + point.x(), getY() + point.y(), getZ() + point.z());
        Vec3 end = new Vec3(getX() + nextPoint.x(), getY() + nextPoint.y(), getZ() + nextPoint.z());

        // BETA26 rayTraceBlocks(start, end, false): do not stop on fluids.
        BlockHitResult blockHit = level().clip(new ClipContext(
                start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (blockHit.getType() == HitResult.Type.BLOCK) {
            BlockPos blockPos = blockHit.getBlockPos();
            BlockState state = level().getBlockState(blockPos);
            if (!state.isAir() && state.getDestroySpeed(level(), blockPos) >= 0.0F) {
                level().levelEvent(2001, blockPos, Block.getId(state));
                level().setBlock(blockPos, Blocks.AIR.defaultBlockState(), 3);
            }
        }

        // EntityUtils.getEntitiesInRange uses this same segment start and a 0.5 AABB.
        AABB query = new AABB(start.x, start.y, start.z, start.x, start.y, start.z).inflate(0.5D);
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

    /**
     * BETA26 rolls instability events on the same entity-specific 600-tick phase
     * as Flux growth. Keep the negative-stability guard outside nextInt so a
     * stable Rift does not consume an otherwise-unused entity RNG value.
     */
    private void tryExecuteInstabilityEvent() {
        float stability = getRiftStability();
        if (stability < 0.0F) {
            int triggerRoll = random.nextInt(1000);
            if (LegacyFluxRiftEventLogic.shouldAttemptEvent(stability, getRiftSize(), triggerRoll)) {
                // BETA26 RandomItemChooser uses Math.random(), not the entity RNG.
                executeWeightedEvent(Math.random());
            }
        }
    }

    /**
     * Resolves the original five weighted entries. Only event 4 (begin collapse)
     * has its required ported foundation; events 0-3 intentionally remain no-ops
     * until their Wisp, Taint Seed, potion and focus systems exist.
     *
     * @return the BETA26 event id selected by the original weight table
     */
    int executeWeightedEvent(double weightedRoll) {
        int event = LegacyFluxRiftEventLogic.selectWeightedEvent(weightedRoll);
        if (event == 4) {
            setCollapse(true);
        }
        return event;
    }

    /**
     * Executes one BETA26 collapse tick using the entity-owned random stream.
     * Void-seed drops and nearby taint/weakness/warp effects remain separate porting tasks.
     *
     * @return the created explosion, or null when the one-in-ten roll did not fire
     */
    Explosion tickCollapse(RandomSource sourceRandom) {
        if (!(level() instanceof ServerLevel serverLevel) || !getCollapse()) {
            return null;
        }

        setRiftSize(getRiftSize() - 1);

        ThaumcraftAuraSavedData aura = ThaumcraftAuraSavedData.get(serverLevel);
        if (sourceRandom.nextBoolean()) {
            aura.addVis(blockPosition(), 1.0F);
        } else {
            aura.polluteFlux(blockPosition(), 1.0F);
        }

        Explosion explosion = null;
        if (sourceRandom.nextInt(10) == 0) {
            // BETA26 createExplosion(..., false) disables smoking/block damage;
            // entity damage remains enabled.
            explosion = level().explode(
                    this,
                    getX() + sourceRandom.nextGaussian() * 2.0D,
                    getY() + sourceRandom.nextGaussian() * 2.0D,
                    getZ() + sourceRandom.nextGaussian() * 2.0D,
                    sourceRandom.nextFloat() / 2.0F,
                    false,
                    Level.ExplosionInteraction.NONE
            );
        }

        if (getRiftSize() <= 1) {
            completeCollapse(serverLevel, sourceRandom);
        }
        return explosion;
    }

    /**
     * BETA26 completeCollapse's primordial-pearl branch. The original void-seed
     * item is not registered in this port yet, so its guaranteed qq drops and
     * the nearby visual/status effects remain separate work.
     */
    private void completeCollapse(ServerLevel serverLevel, RandomSource sourceRandom) {
        int rewardChance = (int) Math.sqrt(maxSize);
        if (sourceRandom.nextInt(100) < rewardChance) {
            ItemStack pearl = new ItemStack(ThaumcraftItems.PRIMORDIAL_PEARL.get());
            // BETA26 metadata 4..7 is represented by the modern Damage component.
            pearl.setDamageValue(4 + sourceRandom.nextInt(4));
            serverLevel.addFreshEntity(new ItemEntity(
                    serverLevel, getX(), getY(), getZ(), pearl));
        }
        applyCollapseWeakness(serverLevel);
        discard();
    }

    /**
     * BETA26 completeCollapse applies Weakness to every nearby living target for both
     * UNSTABLE and VERY_UNSTABLE rifts. The source queries an axis-aligned 32-block box,
     * then derives the effect duration from squared Euclidean distance (not radius 32).
     * The very-unstable Flux Taint effect and player warp are separate, still-unported effects.
     */
    private void applyCollapseWeakness(ServerLevel serverLevel) {
        if (getRiftStability() >= 0.0F) {
            return;
        }

        AABB bounds = new AABB(
                getX() - 32.0D, getY() - 32.0D, getZ() - 32.0D,
                getX() + 32.0D, getY() + 32.0D, getZ() + 32.0D
        );
        for (LivingEntity target : serverLevel.getEntitiesOfClass(LivingEntity.class, bounds)) {
            int weight = (int) ((1.0D - target.distanceToSqr(this) / 32.0D) * 300.0D);
            if (weight > 0) {
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, weight * 20, 0));
            }
        }
    }

    /**
     * BETA26 growth branch from EntityFluxRift.onUpdate. A rift consumes
     * sqrt(2 * size) Flux and grows by one, unless already at size 100 or
     * classified VERY_STABLE (rift stability strictly greater than 50).
     */
    void tryGrowFromFlux() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return;
        }

        int currentSize = getRiftSize();
        if (currentSize >= 100 || getRiftStability() > 50.0F) {
            return;
        }

        BlockPos pos = blockPosition();
        float requiredFlux = (float) Math.sqrt(currentSize * 2.0D);
        ThaumcraftAuraSavedData aura = ThaumcraftAuraSavedData.get(serverLevel);
        if (aura.drainFlux(pos, requiredFlux, true) < requiredFlux) {
            return;
        }

        float drained = aura.drainFlux(pos, requiredFlux, false);
        if (Float.compare(drained, requiredFlux) != 0) {
            // Preserve the all-or-nothing growth transaction if the aura
            // changed between the simulation and committed debit.
            if (drained > 0.0F) {
                aura.polluteFlux(pos, drained);
            }
            return;
        }

        setRiftSize(currentSize + 1);
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
