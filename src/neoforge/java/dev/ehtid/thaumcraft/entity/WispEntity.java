package dev.ehtid.thaumcraft.entity;

import dev.ehtid.thaumcraft.item.CrystalEssenceItem;
import dev.ehtid.thaumcraft.network.WispZapPayload;
import dev.ehtid.thaumcraft.port.core.AspectData;
import dev.ehtid.thaumcraft.port.core.AspectRegistry;
import dev.ehtid.thaumcraft.port.core.LegacyWispSpawnRules;
import dev.ehtid.thaumcraft.registry.WispSoundRegistration;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.FlyingMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Server-side BETA26 EntityWisp port used by Flux Rift event 0.
 *
 * The original aspect tag is synchronized and persisted as "Type". Flight,
 * target acquisition, the 20-tick zap wind-up, movement-dependent damage,
 * the eight-nearby cap, dark-light spawn gate, and aspect crystal drop are
 * retained. The BETA26 two-entity zap packet is restored separately; the
 * continuous ambient wispy-mote field remains client presentation work.
 */
public final class WispEntity extends FlyingMob implements Enemy {
    /** Exact strict movement threshold from BETA26 EntityWisp.java. */
    private static final double BETA26_MOVING_TARGET_THRESHOLD = 0.10000000149011612D;
    private static final int BETA26_THUNDER_SKYLIGHT_SUBTRACTION = 10;
    private static final boolean SMOKE_PROBE = "1".equals(System.getenv("THAUMCRAFT_SMOKE"));

    private static final AspectRegistry ASPECTS = new AspectRegistry();
    private static final EntityDataAccessor<String> ASPECT_TYPE =
            SynchedEntityData.defineId(WispEntity.class, EntityDataSerializers.STRING);

    private int aggroCooldown;
    private int attackCounter;
    private BlockPos currentFlightTarget;
    private boolean crystalDropped;

    public WispEntity(EntityType<? extends WispEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 22.0D)
                .add(Attributes.ATTACK_DAMAGE, 3.0D);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return WispSoundRegistration.WISP_LIVE;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.LAVA_EXTINGUISH;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return WispSoundRegistration.WISP_DEAD;
    }

    @Override
    protected float getSoundVolume() {
        return 0.25F;
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.HOSTILE;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ASPECT_TYPE, "");
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Type", getAspectType());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setAspectType(tag.getString("Type"));
    }

    public String getAspectType() {
        return entityData.get(ASPECT_TYPE);
    }

    public void setAspectType(String type) {
        entityData.set(ASPECT_TYPE, type == null ? "" : type);
    }

    public int getAspectColor() {
        AspectData aspect = ASPECTS.get(getAspectType());
        return aspect == null ? 0xFFFFFF : aspect.color();
    }

    public boolean canSpawnFromRift() {
        return canSpawnFromRift(random);
    }

    /**
     * BETA26 EntityWisp.getCanSpawnHere: fewer than eight nearby Wisps,
     * non-peaceful difficulty, dark sky/local light, then a collision-free body.
     * Thunderstorms temporarily use the source's fixed skylight subtraction of 10
     * for the local-light check, rather than the world's current sky-darken value.
     */
    public boolean canSpawnFromRift(RandomSource spawnRandom) {
        if (level().isClientSide || level().getDifficulty() == Difficulty.PEACEFUL) {
            return false;
        }

        int nearby = level().getEntitiesOfClass(
                WispEntity.class, getBoundingBox().inflate(16.0D)).size();
        if (nearby >= 8) {
            return false;
        }

        BlockPos spawnPos = BlockPos.containing(getX(), getBoundingBox().minY, getZ());
        int skyLight = level().getBrightness(LightLayer.SKY, spawnPos);
        int skyRoll = spawnRandom.nextInt(32);
        if (skyLight > skyRoll) {
            return false;
        }

        int localLight = getLocalLightForSpawn(spawnPos);
        int localRoll = spawnRandom.nextInt(8);
        return LegacyWispSpawnRules.canSpawn(
                false, nearby, skyLight, skyRoll, localLight, localRoll)
                && level().noCollision(this);
    }

    int getLocalLightForSpawn(BlockPos spawnPos) {
        return level().isThundering()
                ? level().getMaxLocalRawBrightness(
                        spawnPos, BETA26_THUNDER_SKYLIGHT_SUBTRACTION)
                : level().getMaxLocalRawBrightness(spawnPos);
    }

    @Override
    public void tick() {
        super.tick();
        // BETA26 EntityWisp.onUpdate damps vertical motion on both logical sides
        // before its server-only onLivingUpdate movement/attack logic.
        setDeltaMovement(applyBETA26VerticalDamping(getDeltaMovement()));
        if (level().isClientSide) {
            return;
        }

        tickServer();
    }

    static Vec3 applyBETA26VerticalDamping(Vec3 velocity) {
        // Preserve the original float 0.6 coefficient widened to double.
        return new Vec3(velocity.x, velocity.y * 0.6000000238418579D, velocity.z);
    }

    private void tickServer() {
        if (level().getDifficulty() == Difficulty.PEACEFUL) {
            discard();
            return;
        }

        if (ASPECTS.get(getAspectType()) == null) {
            List<AspectData> choices = random.nextInt(10) != 0
                    ? ASPECTS.primalAspects() : ASPECTS.compoundAspects();
            if (!choices.isEmpty()) {
                setAspectType(choices.get(random.nextInt(choices.size())).tag());
            }
        }

        LivingEntity target = getTarget();
        boolean canSeeTarget = target != null && hasLineOfSight(target);
        if (target == null || !canSeeTarget) {
            if (currentFlightTarget != null) {
                int ceiling = level().getHeight(
                        Heightmap.Types.MOTION_BLOCKING,
                        currentFlightTarget.getX(),
                        currentFlightTarget.getZ()) + 8;
                if (!level().isEmptyBlock(currentFlightTarget)
                        || currentFlightTarget.getY() < 1
                        || currentFlightTarget.getY() > ceiling) {
                    currentFlightTarget = null;
                }
            }

            if (currentFlightTarget == null || random.nextInt(30) == 0
                    || distanceToSqr(currentFlightTarget.getX() + 0.5D,
                    currentFlightTarget.getY() + 0.1D,
                    currentFlightTarget.getZ() + 0.5D) < 4.0D) {
                currentFlightTarget = new BlockPos(
                        blockPosition().getX() + random.nextInt(7) - random.nextInt(7),
                        blockPosition().getY() + random.nextInt(6) - 2,
                        blockPosition().getZ() + random.nextInt(7) - random.nextInt(7));
            }

            accelerateToward(
                    currentFlightTarget.getX() + 0.5D,
                    currentFlightTarget.getY() + 0.1D,
                    currentFlightTarget.getZ() + 0.5D);
        } else if (distanceToSqr(target) > 128.0D) {
            accelerateToward(
                    target.getX(),
                    target.getY() + target.getEyeHeight() * 0.66D,
                    target.getZ());
        }

        if (target instanceof Player player && (player.isCreative() || player.isSpectator())) {
            setTarget(null);
        }
        if (target != null && !target.isAlive()) {
            setTarget(null);
        }

        --aggroCooldown;
        if (random.nextInt(1000) == 0 && (getTarget() == null || aggroCooldown-- <= 0)) {
            Player closest = level().getNearestPlayer(this, 16.0D);
            setTarget(closest);
            if (closest != null) {
                aggroCooldown = 50;
            }
        }

        target = getTarget();
        if (isAlive() && target != null && distanceToSqr(target) < 256.0D) {
            double dx = target.getX() - getX();
            double dz = target.getZ() - getZ();
            float yaw = -(float) Math.atan2(dx, dz) * 180.0F / (float) Math.PI;
            setYRot(yaw);
            setYHeadRot(yaw);

            if (hasLineOfSight(target)) {
                attackCounter++;
                if (attackCounter == 20) {
                    playZapSound();
                    sendZapVisual(target);
                    tryZap(target, random.nextFloat());
                    attackCounter = -20 + random.nextInt(20);
                }
            } else if (attackCounter > 0) {
                attackCounter--;
            }
        }
    }

    private void accelerateToward(double targetX, double targetY, double targetZ) {
        Vec3 velocity = getDeltaMovement();
        double dx = targetX - getX();
        double dy = targetY - getY();
        double dz = targetZ - getZ();
        setDeltaMovement(
                velocity.x + (Math.signum(dx) * 0.5D - velocity.x) * 0.1D,
                velocity.y + (Math.signum(dy) * 0.7D - velocity.y) * 0.1D,
                velocity.z + (Math.signum(dz) * 0.5D - velocity.z) * 0.1D);

        Vec3 nextVelocity = getDeltaMovement();
        float desiredYaw = (float) (Math.atan2(nextVelocity.z, nextVelocity.x)
                * 180.0D / Math.PI) - 90.0F;
        setYRot(getYRot() + Mth.wrapDegrees(desiredYaw - getYRot()));
    }

    void playZapSound() {
        playSound(WispSoundRegistration.WISP_ZAP, 1.0F, 1.1F);
    }

    /** BETA26 PacketFXWispZap: send source/target IDs to players within 32 blocks. */
    private void sendZapVisual(LivingEntity target) {
        if (level() instanceof ServerLevel serverLevel) {
            if (SMOKE_PROBE) {
                System.out.println("TC_SMOKE_WISP_ZAP_SENT=YES source=" + getId()
                        + " target=" + target.getId() + " aspect=" + getAspectType());
            }
            PacketDistributor.sendToPlayersNear(
                    serverLevel,
                    null,
                    getX(),
                    getY(),
                    getZ(),
                    32.0D,
                    new WispZapPayload(getId(), target.getId())
            );
        }
    }

    /**
     * BETA26 damage branch: moving targets receive 3 damage at 40%; stationary
     * targets receive 4 damage at 66%. The caller supplies the original RNG roll.
     */
    boolean tryZap(LivingEntity target, float damageRoll) {
        if (!Float.isFinite(damageRoll) || damageRoll < 0.0F || damageRoll >= 1.0F) {
            throw new IllegalArgumentException("damageRoll must be finite and in [0, 1)");
        }

        Vec3 motion = target.getDeltaMovement();
        boolean moving = Math.abs(motion.x) > BETA26_MOVING_TARGET_THRESHOLD
                || Math.abs(motion.y) > BETA26_MOVING_TARGET_THRESHOLD
                || Math.abs(motion.z) > BETA26_MOVING_TARGET_THRESHOLD;
        float chance = moving ? 0.4F : 0.66F;
        if (damageRoll >= chance) {
            return false;
        }

        float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE)
                + (moving ? 0.0F : 1.0F);
        return target.hurt(level().damageSources().mobAttack(this), damage);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && source.getEntity() instanceof LivingEntity attacker) {
            setTarget(attacker);
            aggroCooldown = 200;
            if (SMOKE_PROBE && attacker instanceof Player) {
                System.out.println("TC_SMOKE_WISP_TARGET_SET=YES source=" + getId()
                        + " target=" + attacker.getId() + " aspect=" + getAspectType());
            }
        }
        return hurt;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        dropAspectCrystal();
    }

    private void dropAspectCrystal() {
        if (crystalDropped || level().isClientSide) {
            return;
        }
        ItemStack crystal = createAspectCrystalDrop();
        if (crystal.isEmpty()) {
            return;
        }
        crystalDropped = true;
        ItemEntity item = new ItemEntity(level(), getX(), getY(), getZ(), crystal);
        item.setDefaultPickUpDelay();
        level().addFreshEntity(item);
    }

    ItemStack createAspectCrystalDrop() {
        return ASPECTS.get(getAspectType()) == null
                ? ItemStack.EMPTY : CrystalEssenceItem.ofAspect(getAspectType());
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return true;
    }
}
