package dev.ehtid.thaumcraft.entity;

import dev.ehtid.thaumcraft.golem.ai.GolemHomeGoal;
import dev.ehtid.thaumcraft.golem.ai.GolemFollowOwnerGoal;
import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.golem.GolemDyes;
import dev.ehtid.thaumcraft.port.core.golem.GolemBellRules;
import dev.ehtid.thaumcraft.player.ThaumcraftPlayerAttachments;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import dev.ehtid.thaumcraft.golem.ai.GolemBlockGoal;
import dev.ehtid.thaumcraft.golem.ai.GolemPickupGoal;
import dev.ehtid.thaumcraft.item.GolemPlacerItem;
import dev.ehtid.thaumcraft.port.core.golem.GolemDesign;
import dev.ehtid.thaumcraft.port.core.golem.GolemTrait;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.UUID;

/** First ground worker adapter. Combat and flying parts are separate M10 slices. */
public final class ThaumcraftGolem extends PathfinderMob {
    private static final EntityDataAccessor<Long> DESIGN = SynchedEntityData.defineId(
            ThaumcraftGolem.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(
            ThaumcraftGolem.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Byte> COLOR = SynchedEntityData.defineId(
            ThaumcraftGolem.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> FLAGS = SynchedEntityData.defineId(
            ThaumcraftGolem.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> WORKING = SynchedEntityData.defineId(
            ThaumcraftGolem.class, EntityDataSerializers.BOOLEAN);

    private BlockPos home = BlockPos.ZERO;
    private int rankXp;
    private Long invalidDesign;
    private GolemPickupGoal pickupGoal;
    private GolemBlockGoal blockGoal;
    private ItemStack transferRecovery = ItemStack.EMPTY;
    private GolemFollowOwnerGoal followGoal;

    public ThaumcraftGolem(EntityType<? extends ThaumcraftGolem> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setCanPickUpLoot(false); // BETA26 collection goes through a seal task, never vanilla mob pickup.
        setDropChance(EquipmentSlot.MAINHAND, 2.0F);
        setDropChance(EquipmentSlot.OFFHAND, 2.0F);
        xpReward = 5;
    }

    public static AttributeSupplier.Builder attributes() {
        return createMobAttributes().add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.MAX_HEALTH, 10).add(Attributes.ATTACK_DAMAGE, 0)
                .add(Attributes.FOLLOW_RANGE, 40).add(Attributes.ARMOR, 0)
                .add(Attributes.STEP_HEIGHT, 0.6);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DESIGN, GolemDesign.defaultWood().toLong());
        builder.define(OWNER, Optional.empty());
        builder.define(COLOR, (byte) 0);
        builder.define(FLAGS, (byte) 0);
        builder.define(WORKING, false);
    }

    @Override
    protected void registerGoals() {
        pickupGoal = new GolemPickupGoal(this);
        goalSelector.addGoal(3, pickupGoal);
        blockGoal = new GolemBlockGoal(this);
        goalSelector.addGoal(4, blockGoal);
        followGoal = new GolemFollowOwnerGoal(this);
        goalSelector.addGoal(4, followGoal);
        goalSelector.addGoal(5, new GolemHomeGoal(this));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));
    }

    public GolemDesign design() { return GolemDesign.fromLong(entityData.get(DESIGN)); }
    public UUID ownerId() { return entityData.get(OWNER).orElse(null); }
    public byte golemColor() { return entityData.get(COLOR); }
    public void setGolemColor(byte color) { entityData.set(COLOR, color); }
    public BlockPos home() { return home; }
    public int rankXp() { return rankXp; }
    /** BETA26 addRankXp: server-only SMART, one rank per call, maximum ten. */
    public void addRankXp(int amount) {
        GolemDesign current = design();
        if (level().isClientSide || amount <= 0 || !current.hasTrait(GolemTrait.SMART) || current.rank() >= 10) return;
        long xp = (long) rankXp + amount;
        int threshold = (current.rank() + 1) * (current.rank() + 1) * 1000;
        if (xp >= threshold) {
            xp -= threshold;
            entityData.set(DESIGN, current.withRank(current.rank() + 1).toLong());
            applyDesign();
        }
        rankXp = (int) Math.min(Integer.MAX_VALUE, xp);
    }
    public boolean hasWorkTask() { return entityData.get(WORKING); }
    public void setWorking(boolean working) { entityData.set(WORKING, working); }
    public boolean isFollowingOwner() { return GolemBellRules.following(entityData.get(FLAGS)); }
    public void setFollowingOwner(boolean following) {
        if (level().isClientSide || isFollowingOwner() == following || invalidDesign != null) return;
        if (pickupGoal != null) pickupGoal.stop();
        if (blockGoal != null) blockGoal.stop();
        if (followGoal != null) followGoal.stop();
        getNavigation().stop();
        setWorking(false);
        entityData.set(FLAGS, GolemBellRules.following(entityData.get(FLAGS), following));
        if (!following) home = blockPosition().immutable();
    }

    public void initialize(GolemDesign design, UUID owner, BlockPos home, int xp) {
        entityData.set(DESIGN, design.toLong());
        entityData.set(OWNER, Optional.ofNullable(owner));
        this.home = home.immutable();
        rankXp = Math.max(0, xp);
        applyDesign();
        setHealth(getMaxHealth());
    }

    private void applyDesign() {
        var stats = design().stats();
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(stats.maxHealth());
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(stats.movementSpeed());
        getAttribute(Attributes.ARMOR).setBaseValue(stats.armor());
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(stats.attackDamage());
        getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(stats.followRange());
        getAttribute(Attributes.STEP_HEIGHT).setBaseValue(stats.stepHeight());
        restrictTo(home, stats.homeRadius());
    }

    private EquipmentSlot carrySlot(int i) { return i == 0 ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND; }

    public int canCarryAmount(ItemStack incoming) {
        if (incoming.isEmpty() || invalidDesign != null) return 0;
        int capacity = 0;
        for (int i = 0; i < design().stats().carrySlots(); i++) {
            ItemStack held = getItemBySlot(carrySlot(i));
            if (held.isEmpty()) capacity += incoming.getMaxStackSize();
            else if (ItemStack.isSameItemSameComponents(held, incoming))
                capacity += Math.max(0, held.getMaxStackSize() - held.getCount());
        }
        return capacity;
    }

    /** Owns unexpected actual extraction remainders; never invent a stack from a simulation. */
    public boolean hasTransferRecovery() { return !transferRecovery.isEmpty(); }
    public ItemStack transferRecovery() { return transferRecovery.copy(); }
    public void holdExtracted(ItemStack actual) {
        if (level().isClientSide || hasTransferRecovery()) throw new IllegalStateException("Extraction recovery must be drained first");
        transferRecovery = holdItem(actual);
    }

    /** Returns a new remainder. No world/container mutation, and no alias to the caller's stack. */
    public ItemStack holdItem(ItemStack incoming) {
        ItemStack remainder = incoming.copy();
        if (invalidDesign != null) return remainder;
        for (int i = 0; i < design().stats().carrySlots() && !remainder.isEmpty(); i++) {
            EquipmentSlot slot = carrySlot(i);
            ItemStack held = getItemBySlot(slot);
            if (held.isEmpty()) {
                int accepted = Math.min(remainder.getCount(), remainder.getMaxStackSize());
                setItemSlot(slot, remainder.split(accepted));
            } else if (ItemStack.isSameItemSameComponents(held, remainder)) {
                int accepted = Math.min(remainder.getCount(), Math.max(0, held.getMaxStackSize() - held.getCount()));
                ItemStack merged = held.copy();
                merged.grow(accepted);
                setItemSlot(slot, merged);
                remainder.shrink(accepted);
            }
        }
        return remainder;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && isAlive() && hasTransferRecovery()) transferRecovery = holdItem(transferRecovery);
        if (!level().isClientSide && isAlive() && invalidDesign == null
                && tickCount % (design().hasTrait(GolemTrait.REPAIR) ? 40 : 100) == 0)
            heal(1.0F);
    }

    @Override
    protected void dropCustomDeathLoot(net.minecraft.server.level.ServerLevel level,
                                       net.minecraft.world.damagesource.DamageSource source, boolean recentlyHit) {
        // Cargo is not combat equipment. BETA26 dropCarried does not apply
        // equipment drop randomness, durability damage or Vanishing to cargo.
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) {
            ItemStack held = getItemBySlot(slot);
            if (held.isEmpty()) continue;
            setItemSlot(slot, ItemStack.EMPTY);
            spawnAtLocation(held.copy());
        }
        if (hasTransferRecovery()) {
            ItemStack retained = transferRecovery;
            transferRecovery = ItemStack.EMPTY;
            spawnAtLocation(retained.copy());
        }
        super.dropCustomDeathLoot(level, source, recentlyHit);
    }

    @Override
    public boolean canDrownInFluidType(net.neoforged.neoforge.fluids.FluidType type) {
        return type != net.neoforged.neoforge.common.NeoForgeMod.WATER_TYPE.value() && type.canDrownIn(this);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag); // Hands are saved by the vanilla equipment serializer exactly once.
        tag.putLong("props", invalidDesign != null ? invalidDesign : design().toLong());
        tag.putLong("homepos", home.asLong());
        tag.putByte("gflags", entityData.get(FLAGS));
        tag.putInt("rankXP", rankXp);
        tag.putByte("color", golemColor());
        if (hasTransferRecovery()) tag.put("transferRecovery", transferRecovery.save(registryAccess()));
        else tag.remove("transferRecovery");
        if (ownerId() != null) tag.putString("OwnerUUID", ownerId().toString());
        tag.putBoolean("v", true);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        GolemDesign design;
        invalidDesign = null;
        try { design = GolemDesign.fromLong(tag.getLong("props")); }
        catch (IllegalArgumentException e) {
            // Retain malformed configuration and equipment without crashing a world
            // load or silently converting it into a different functioning worker.
            invalidDesign = tag.getLong("props");
            design = GolemDesign.defaultWood();
            setNoAi(true);
        }
        entityData.set(DESIGN, design.toLong());
        home = tag.contains("homepos") ? BlockPos.of(tag.getLong("homepos")) : blockPosition();
        UUID owner = null;
        try { owner = UUID.fromString(tag.getString("OwnerUUID")); }
        catch (IllegalArgumentException ignored) { /* Legacy ownerless or malformed owner stays unowned. */ }
        entityData.set(OWNER, Optional.ofNullable(owner));
        entityData.set(FLAGS, tag.getByte("gflags"));
        entityData.set(COLOR, tag.getByte("color"));
        transferRecovery = tag.contains("transferRecovery")
                ? ItemStack.parseOptional(registryAccess(), tag.getCompound("transferRecovery")) : ItemStack.EMPTY;
        rankXp = Math.max(0, tag.getInt("rankXP"));
        setWorking(false); // Reservations are ephemeral; reacquire after load, never replay a completed pickup.
        applyDesign();
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!isAlive() || isRemoved()) return InteractionResult.FAIL;
        if (!player.isShiftKeyDown()) {
            ItemStack held = player.getItemInHand(hand);
            byte color = GolemDyes.color(held);
            if (color != 0) {
                if (!player.getUUID().equals(ownerId()) || player.level() != level() || !player.isAlive()
                        || player.isSpectator() || player.distanceToSqr(this) > 25 || invalidDesign != null)
                    return InteractionResult.FAIL;
                if (level().isClientSide) return InteractionResult.SUCCESS;
                setGolemColor(color);
                // BETA26 consumes one even in creative or when applying the existing color again.
                held.shrink(1);
                playSound(SoundEvents.DYE_USE, 1, 1);
                player.swing(hand, true);
                // Original dye interaction does not directly cancel work. Every task callback
                // rechecks current color; an incompatible goal releases its lease on its next tick.
                return InteractionResult.CONSUME;
            }
        }
        if (!player.isShiftKeyDown() && player.getItemInHand(hand).is(GolemModule.GOLEM_BELL.get())) {
            if (!player.getUUID().equals(ownerId()) || player.distanceToSqr(this) > 25 || invalidDesign != null)
                return InteractionResult.FAIL;
            if (level().isClientSide) return InteractionResult.SUCCESS;
            if (!player.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge().isResearchKnown(GolemBellRules.RESEARCH))
                return InteractionResult.PASS;
            setFollowingOwner(!isFollowingOwner());
            playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 1, 1);
            player.displayClientMessage(Component.translatable("message.thaumcraft.golem." + (isFollowingOwner() ? "follow" : "stay")), true);
            player.swing(hand, true);
            return InteractionResult.CONSUME;
        }
        if (!player.isShiftKeyDown() || hand != InteractionHand.MAIN_HAND || !player.getUUID().equals(ownerId())
                || player.getItemInHand(hand).is(net.minecraft.world.item.Items.NAME_TAG))
            return super.mobInteract(player, hand);
        if (level().isClientSide) return InteractionResult.SUCCESS;
        // Returning to an item releases the task when the goal stops. Drop each carried slot once.
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) {
            ItemStack held = getItemBySlot(slot);
            if (held.isEmpty()) continue;
            ItemEntity drop = new ItemEntity(level(), getX(), getY() + 0.5, getZ(), held.copy());
            if (!level().addFreshEntity(drop)) return InteractionResult.FAIL;
            setItemSlot(slot, ItemStack.EMPTY);
        }
        if (hasTransferRecovery()) {
            ItemEntity drop = new ItemEntity(level(), getX(), getY() + 0.5, getZ(), transferRecovery.copy());
            if (!level().addFreshEntity(drop)) return InteractionResult.FAIL;
            transferRecovery = ItemStack.EMPTY;
        }
        ItemEntity placer = new ItemEntity(level(), getX(), getY() + 0.5, getZ(),
                GolemPlacerItem.stack(invalidDesign != null ? invalidDesign : design().toLong(), rankXp));
        if (!level().addFreshEntity(placer)) return InteractionResult.FAIL;
        discard();
        return InteractionResult.CONSUME;
    }

    @Override
    public void remove(RemovalReason reason) {
        if (pickupGoal != null && !level().isClientSide) pickupGoal.stop();
        if (blockGoal != null && !level().isClientSide) blockGoal.stop();
        if (followGoal != null && !level().isClientSide) followGoal.stop();
        super.remove(reason);
    }
}
