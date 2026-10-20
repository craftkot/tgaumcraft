package dev.ehtid.thaumcraft.golem.seal;

import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import dev.ehtid.thaumcraft.golem.inventory.GolemContainers;
import dev.ehtid.thaumcraft.port.core.golem.GolemTask;
import dev.ehtid.thaumcraft.port.core.golem.GolemTrait;
import dev.ehtid.thaumcraft.port.core.golem.SealArea;
import dev.ehtid.thaumcraft.port.core.golem.SealAreaControls;
import dev.ehtid.thaumcraft.port.core.golem.SealEmptyRules;
import dev.ehtid.thaumcraft.port.core.golem.TaskBoard;
import dev.ehtid.thaumcraft.port.core.golem.TaskSelection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * BETA26 pickup seal -> ephemeral task board -> golem goal bridge.
 *
 * Basic one-slot filters use the original default strict matching mode.
 * Seals persist; task leases and numeric entity IDs deliberately do not survive
 * restart. Modern UUID target identity, exclusive leases and copied ItemStacks
 * prevent stale reservations and sharing a mutable ground stack with equipment.
 */
@EventBusSubscriber(modid = ThaumcraftPort.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class GolemSeals extends SavedData {
    private static final String DATA_NAME = "thaumcraft_golem_seals";
    private static final Factory<GolemSeals> FACTORY = new Factory<>(GolemSeals::new, GolemSeals::load);
    public static final String PICKUP_KEY = "thaumcraft:pickup";
    public static final String FILL_KEY = "thaumcraft:fill";
    public static final String EMPTY_KEY = "thaumcraft:empty";
    public enum Type {
        PICKUP(PICKUP_KEY), FILL(FILL_KEY), EMPTY(EMPTY_KEY), EMPTY_ADVANCED("thaumcraft:empty_advanced"),
        PROVIDE("thaumcraft:provider"), STOCK("thaumcraft:stock"), HARVEST("thaumcraft:harvest"), PICKUP_ADVANCED("thaumcraft:pickup_advanced"), FILL_ADVANCED("thaumcraft:fill_advanced"), BREAKER("thaumcraft:breaker"), LUMBER("thaumcraft:lumber"), BREAKER_ADVANCED("thaumcraft:breaker_advanced"), USE("thaumcraft:use");
        public final String key;
        Type(String key) { this.key = key; }
        public boolean breaksBlocks() { return this==BREAKER || this==BREAKER_ADVANCED; }
        public boolean forestry() { return breaksBlocks() || this==LUMBER; }
        public boolean areaControl() { return picksUp() || this==HARVEST || forestry(); }
        public boolean logistics() { return this==PROVIDE || this==STOCK || advanced(); }
        public boolean picksUp() { return this==PICKUP || this==PICKUP_ADVANCED; }
        public boolean fills() { return this==FILL || this==FILL_ADVANCED; }
        public boolean advanced() { return this==EMPTY_ADVANCED || this==PICKUP_ADVANCED || this==FILL_ADVANCED || this==BREAKER_ADVANCED; }
        public boolean extracts() { return this==EMPTY || this==EMPTY_ADVANCED; }
    }

    public record SealKey(BlockPos pos, Direction face) {
        public SealKey { pos = pos.immutable(); }
    }

    public record Seal(SealKey key, UUID owner, byte priority, byte color, boolean locked, Type type,
                       SealFilter filter, UUID instanceId, boolean redstone, BlockPos area, boolean leaveOne, LogisticsPolicy logistics, HarvestSettings harvest) {
        public Seal {
            area = area.immutable();
            if (!validArea(area)) throw new IllegalArgumentException("Seal area outside1..8");
        }
        public Seal(SealKey key, UUID owner, byte priority, byte color, boolean locked, Type type) {
            this(key, owner, priority, color, locked, type, SealFilter.defaults(), UUID.randomUUID(), false, defaultArea(key), false, LogisticsPolicy.defaults(type==Type.STOCK),HarvestSettings.defaults());
        }
    }

    public record Assignment(TaskBoard.Lease lease, UUID targetId, SealKey seal) {}
    public record BlockAssignment(TaskBoard.Lease lease, SealKey seal) {}

    private final Map<SealKey, Seal> seals = new LinkedHashMap<>();
    private final TaskBoard board = new TaskBoard();
    private final GolemLogistics logistics=new GolemLogistics(this);
    private final Map<SealKey,Integer> emptyCycles=new HashMap<>();
    private final GolemFarm farm=new GolemFarm(this);
    private final GolemBreaker breaker=new GolemBreaker(this);
    private final GolemLumber lumber=new GolemLumber(this);
    private final GolemUse use=new GolemUse(this);
    private final Map<Integer, UUID> targets = new HashMap<>();
    private final Map<UUID, Integer> itemTasks = new HashMap<>();
    private final Map<Integer, SealKey> taskSeals = new HashMap<>();
    private final Map<Integer, Map<UUID, Long>> retryAfter = new HashMap<>();
    private final Map<Integer, UUID> workers = new HashMap<>();
    private final Map<SealKey, Integer> watchedFillTasks = new HashMap<>();
    private final Map<Integer, ItemStack> emptyCandidates = new HashMap<>();
    private int nextTaskId;

    public static GolemSeals get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) tick(level);
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) get(level).board.close();
    }

    /** Called once per server-level tick; never requests an unloaded chunk. */
    public static void tick(ServerLevel level) {
        get(level).serverTick(level);
    }

    public boolean contains(BlockPos pos, Direction face) {
        return seals.containsKey(new SealKey(pos, face));
    }

    /** Mutation is confined to the server thread by the item and event adapters. */
    public boolean place(BlockPos pos, Direction face, UUID owner) {
        return place(pos, face, owner, Type.PICKUP);
    }

    public boolean place(BlockPos pos, Direction face, UUID owner, Type type) {
        SealKey key = new SealKey(pos, face);
        if (seals.containsKey(key)) return false;
        seals.put(key, new Seal(key, owner, (byte) 0, (byte) 0, false, java.util.Objects.requireNonNull(type)));
        setDirty();
        return true;
    }

    public boolean remove(BlockPos pos, Direction face, UUID owner) {
        SealKey key = new SealKey(pos, face);
        Seal seal = seals.get(key);
        if (seal == null || !seal.owner().equals(owner)) return false;
        removeSeal(key);
        return true;
    }

    public boolean remove(BlockPos pos, Direction face, UUID owner, Type expectedType) {
        Seal seal = seals.get(new SealKey(pos, face));
        return seal != null && seal.type() == expectedType && remove(pos, face, owner);
    }

    public Seal get(SealKey key) { return seals.get(key); }

    /** Instance token prevents a stale menu from editing a replacement at the same face. */
    public boolean configure(Seal expected, UUID owner, SealFilter filter) {
        Seal current = expected == null ? null : seals.get(expected.key());
        if (current == null || !current.instanceId().equals(expected.instanceId()) || !current.owner().equals(owner))
            return false;
        return replace(current, new Seal(current.key(), current.owner(), current.priority(), current.color(),
                current.locked(), current.type(), java.util.Objects.requireNonNull(filter), current.instanceId(), current.redstone(), current.area(), current.leaveOne(), current.logistics(),current.harvest()));
    }

    public boolean configureControls(Seal expected, UUID owner, int priority, int color, boolean locked, boolean redstone) {
        Seal current = expected == null ? null : seals.get(expected.key());
        if (current == null || !current.instanceId().equals(expected.instanceId()) || !current.owner().equals(owner)
                || priority < -5 || priority > 5 || color < 0 || color > 16) return false;
        return replace(current, new Seal(current.key(), current.owner(), (byte) priority, (byte) color,
                locked, current.type(), current.filter(), current.instanceId(), redstone, current.area(), current.leaveOne(), current.logistics(),current.harvest()));
    }

    public boolean configureArea(Seal expected, UUID owner, BlockPos area) {
        Seal current = expected == null ? null : seals.get(expected.key());
        if (current == null || !current.type().areaControl() || !current.owner().equals(owner)
                || !current.instanceId().equals(expected.instanceId()) || !validArea(area)) return false;
        return replace(current, new Seal(current.key(), current.owner(), current.priority(), current.color(),
                current.locked(), current.type(), current.filter(), current.instanceId(), current.redstone(), area, current.leaveOne(), current.logistics(),current.harvest()));
    }

    public boolean configureLeaveOne(Seal expected, UUID owner, boolean enabled) {
        Seal current = expected == null ? null : seals.get(expected.key());
        if (current == null || !current.type().extracts() && current.type()!=Type.PROVIDE || !current.owner().equals(owner)
                || !current.instanceId().equals(expected.instanceId())) return false;
        return replace(current, new Seal(current.key(), current.owner(), current.priority(), current.color(),
                current.locked(), current.type(), current.filter(), current.instanceId(), current.redstone(), current.area(), enabled, current.logistics(),current.harvest()));
    }

    public boolean configureLogistics(Seal expected, UUID owner, LogisticsPolicy policy) {
        Seal current=expected==null?null:seals.get(expected.key());
        if(current==null || !current.type().logistics() || !current.owner().equals(owner)
                || !current.instanceId().equals(expected.instanceId()) || policy==null
                || current.type()==Type.STOCK && (policy.blacklist() || policy.single())) return false;
        return replace(current,new Seal(current.key(),current.owner(),current.priority(),current.color(),current.locked(),
                current.type(),current.filter(),current.instanceId(),current.redstone(),current.area(),current.leaveOne(),policy,current.harvest()));
    }

    public boolean configureHarvest(Seal expected,UUID owner,HarvestSettings settings) {
        var current=expected==null?null:seals.get(expected.key());
        if(current==null || current.type()!=Type.HARVEST || !current.instanceId().equals(expected.instanceId())
                || !current.owner().equals(owner) || settings==null) return false;
        return replace(current,new Seal(current.key(),current.owner(),current.priority(),current.color(),current.locked(),
                current.type(),current.filter(),current.instanceId(),current.redstone(),current.area(),current.leaveOne(),current.logistics(),settings));
    }

    public dev.ehtid.thaumcraft.port.core.golem.GolemUseRules.Settings useSettings(Seal seal) { return use.settings(seal); }
    public boolean configureUse(Seal expected,UUID owner,dev.ehtid.thaumcraft.port.core.golem.GolemUseRules.Settings value) {
        var current=expected==null?null:seals.get(expected.key());
        if(current==null||current.type()!=Type.USE||!current.instanceId().equals(expected.instanceId())||!current.owner().equals(owner)) return false;
        replace(current,current); use.configure(current,java.util.Objects.requireNonNull(value)); setDirty(); return true;
    }

    private boolean replace(Seal current, Seal replacement) {
        logistics.changed(current.key());
        seals.put(current.key(), replacement);
        watchedFillTasks.remove(current.key());
        for (var entry : new ArrayList<>(taskSeals.entrySet())) {
            if (entry.getValue().equals(current.key())) suspend(entry.getKey());
        }
        setDirty();
        return true;
    }

    public boolean remove(Seal expected, UUID owner) {
        Seal current = expected == null ? null : seals.get(expected.key());
        if (current == null || !current.instanceId().equals(expected.instanceId()) || !current.owner().equals(owner)) return false;
        removeSeal(current.key());
        return true;
    }

    public List<Seal> all() {
        return List.copyOf(seals.values());
    }

    public int pendingTasks() { return taskSeals.size(); }
    public long reservedTasks() { return board.snapshot().stream().filter(GolemTask::isReserved).count(); }

    private void removeSeal(SealKey key) {
        logistics.changed(key);
        farm.removed(key); breaker.removed(key); lumber.removed(key); use.removed(key);
        emptyCycles.remove(key);
        seals.remove(key);
        watchedFillTasks.remove(key);
        for (var entry : new ArrayList<>(taskSeals.entrySet())) {
            if (entry.getValue().equals(key)) suspend(entry.getKey());
        }
        setDirty();
    }

    private void serverTick(ServerLevel level) {
        // Missing entities include unloaded entities. New tasks are created only
        // after they actually return to a loaded seal area, without chunk loads.
        for (var entry : new ArrayList<>(taskSeals.entrySet())) {
            UUID target = targets.get(entry.getKey());
            ItemEntity item = item(level, target);
            SealKey key = taskSeals.get(entry.getKey());
            if (key == null || !seals.containsKey(key) || !level.hasChunkAt(key.pos()) || stopped(level, seals.get(key))
                    || target != null && (item == null || !item.isAlive() || !level.hasChunkAt(item.blockPosition()))) {
                suspend(entry.getKey());
                continue;
            }
            UUID worker = workers.get(entry.getKey());
            if (worker != null && (!(level.getEntity(worker) instanceof ThaumcraftGolem golem)
                    || !golem.isAlive() || golem.isRemoved())) suspend(entry.getKey());
        }
        if (level.getGameTime() % 20 == 0) board.tickSecond(task -> forget(task.getId()));
        int phase = 0;
        for (Seal seal : new ArrayList<>(seals.values())) {
            if (!level.hasChunkAt(seal.key().pos())) continue;
            if (level.getGameTime() % 20 == 0 && level.isEmptyBlock(seal.key().pos()) && seal.type()!=Type.USE) {
                removeSeal(seal.key());
                continue;
            }
            if (stopped(level, seal)) continue;
            if (seal.type().fills()) {
                if (level.getGameTime() % 20 == 0) createFillTask(seal);
                continue;
            }
            if (seal.type().extracts()) {
                if (level.getGameTime() % SealEmptyRules.INTERVAL_TICKS == 0) createEmptyTask(level, seal);
                continue;
            }
            if(!seal.type().picksUp()) continue;
            if (Math.floorMod(level.getGameTime() + phase++, 5) != 0) continue;
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, bounds(seal),
                    entity -> entity.isAlive() && entity.onGround() && !entity.hasPickUpDelay()
                            && pickupAccepts(seal,entity.getItem()))) {
                if (!level.hasChunkAt(item.blockPosition()) || itemTasks.containsKey(item.getUUID())) continue;
                int id = allocateTaskId();
                GolemTask task = GolemTask.entity(id, corePos(seal.key()), item.getUUID());
                task.setPriority(seal.priority());
                if (!board.add(task)) continue;
                targets.put(id, item.getUUID());
                itemTasks.put(item.getUUID(), id);
                taskSeals.put(id, seal.key());
                break; // Original SealPickup emits at most one task per scan.
            }
        }
        farm.tick(level); breaker.tick(level); lumber.tick(level); use.tick(level);
        logistics.tick(level);
    }

    private static boolean pickupAccepts(Seal seal,ItemStack actual) {
        return seal.type()==Type.PICKUP_ADVANCED?seal.logistics().accepts(actual):seal.filter().accepts(actual);
    }
    private static boolean canFill(ServerLevel level,ThaumcraftGolem golem,Seal seal) {
        return seal.type()==Type.FILL_ADVANCED?GolemContainers.canDeposit(level,golem,seal.key(),seal.logistics())
                :GolemContainers.canDeposit(level,golem,seal.key(),seal.filter());
    }

    public Optional<Assignment> claim(ThaumcraftGolem golem, Predicate<ItemEntity> canReach) {
        if (!(golem.level() instanceof ServerLevel level) || golem.design().hasTrait(GolemTrait.CLUMSY))
            return Optional.empty();
        TaskSelection.Context context = new TaskSelection.Context(golem.getUUID(), golem.golemColor(),
                new TaskSelection.Position(golem.getX(), golem.getY(), golem.getZ()),
                task -> entityPosition(level,task),
                task -> Optional.ofNullable(seals.get(taskSeals.get(task.getId())))
                        .map(seal -> new TaskSelection.SealRule(seal.color(),
                                ignored -> eligible(level, golem, task, seal))));
        for (GolemTask task : board.sorted(GolemTask.Type.ENTITY, context)) {
            Seal seal = seals.get(taskSeals.get(task.getId()));
            if (seal == null || !eligible(level, golem, task, seal)) continue;
            ItemEntity item = item(level, task.getEntityId());
            if (item == null || !canReach.test(item)) continue;
            Optional<TaskBoard.Lease> lease = board.claim(task.getId(), golem.getUUID());
            if (lease.isPresent()) {
                workers.put(task.getId(), golem.getUUID());
                return Optional.of(new Assignment(lease.get(), item.getUUID(), seal.key()));
            }
        }
        return Optional.empty();
    }

    private boolean eligible(ServerLevel level, ThaumcraftGolem golem, GolemTask task, Seal seal) {
        if (golem.isFollowingOwner() || !golem.isAlive() || golem.isRemoved() || task.isSuspended() || task.isCompleted()
                || !targets.containsKey(task.getId())
                || !level.hasChunkAt(seal.key().pos()) || level.isEmptyBlock(seal.key().pos()) || stopped(level, seal)
                || golem.design().hasTrait(GolemTrait.CLUMSY)) return false;
        if(seal.type().advanced() && !golem.design().hasTrait(GolemTrait.SMART)) return false;
        // BETA26 accidentally UUID.equals(String) for locked owners. UUID identity
        // fixes that old bug, while the original unlocked default remains public.
        if (seal.locked() && !seal.owner().equals(golem.ownerId())) return false;
        if (golem.golemColor() > 0 && seal.color() > 0 && golem.golemColor() != seal.color()) return false;
        if (retryAfter.getOrDefault(task.getId(), Map.of()).getOrDefault(golem.getUUID(), 0L)
                > level.getGameTime()) return false;
        ItemEntity item = item(level, task.getEntityId());
        if (item == null || !item.isAlive() || !pickupAccepts(seal,item.getItem())
                || !level.hasChunkAt(item.blockPosition()) || golem.canCarryAmount(item.getItem()) <= 0)
            return false;
        double range = golem.design().hasTrait(GolemTrait.SCOUT) ? 48 : 32;
        return golem.home().distSqr(item.blockPosition()) < range * range;
    }

    public ItemEntity target(ServerLevel level, ThaumcraftGolem golem, Assignment assignment) {
        if (golem.level() != level || !assignment.lease().owner().equals(golem.getUUID())
                || !assignment.seal().equals(taskSeals.get(assignment.lease().taskId()))) return null;
        GolemTask task = board.task(assignment.lease()).orElse(null);
        Seal seal = seals.get(assignment.seal());
        return task != null && assignment.targetId().equals(task.getEntityId())
                && seal != null && eligible(level, golem, task, seal)
                ? item(level, assignment.targetId()) : null;
    }

    /** A server-thread transfer from exactly one existing entity into equipment. */
    public boolean pickup(ServerLevel level, ThaumcraftGolem golem, Assignment assignment) {
        ItemEntity item = target(level, golem, assignment);
        if (item == null || golem.distanceToSqr(item) > reachSquared(item)) return false;
        ItemStack before = item.getItem();
        int originalCount = before.getCount();
        ItemStack remainder = golem.holdItem(before.copy());
        if (remainder.getCount() >= originalCount) return false;
        if (remainder.isEmpty()) item.discard();
        else item.setItem(remainder);
        board.complete(assignment.lease(), true);
        board.suspend(assignment.lease());
        forget(assignment.lease().taskId());
        golem.swing(InteractionHand.MAIN_HAND);
        level.playSound(null, golem.getX(), golem.getY(), golem.getZ(), SoundEvents.ITEM_PICKUP,
                SoundSource.NEUTRAL, 0.125F,
                ((level.random.nextFloat() - level.random.nextFloat()) * 0.7F + 1.0F) * 2.0F);
        return true;
    }

    public void release(ServerLevel level, ThaumcraftGolem golem, Assignment assignment, boolean failed) {
        if (failed) retryAfter.computeIfAbsent(assignment.lease().taskId(), ignored -> new HashMap<>())
                .put(golem.getUUID(), level.getGameTime() + 100);
        if (board.release(assignment.lease())) { workers.remove(assignment.lease().taskId(), golem.getUUID()); breaker.released(assignment.lease().taskId()); lumber.released(assignment.lease().taskId()); }
    }

    /** SealFill watches one available task and creates its successor when work starts. */
    private void createFillTask(Seal seal) {
        Integer watched = watchedFillTasks.get(seal.key());
        GolemTask old = watched == null ? null : board.get(watched).orElse(null);
        if (old != null && !old.isReserved() && !old.isCompleted() && !old.isSuspended()) return;
        int id = allocateTaskId();
        var pos = corePos(seal.key());
        GolemTask task = GolemTask.block(id, pos, pos.pos());
        task.setPriority(seal.priority());
        if (!board.add(task)) return;
        taskSeals.put(id, seal.key());
        watchedFillTasks.put(seal.key(), id);
    }

    private void createEmptyTask(ServerLevel level, Seal seal) {
        ItemStack cached = seal.type()==Type.EMPTY_ADVANCED
                ? GolemContainers.emptyCandidate(level,seal.key(),seal.logistics(),seal.leaveOne(),emptyCycles.getOrDefault(seal.key(),0))
                : GolemContainers.emptyCandidate(level, seal.key(), seal.filter(), seal.leaveOne());
        if (cached.isEmpty()) return;
        int id = allocateTaskId();
        var pos = corePos(seal.key());
        GolemTask task = GolemTask.block(id, pos, pos.pos());
        task.setPriority(seal.priority());
        task.setLifespan(SealEmptyRules.TASK_LIFESPAN_SECONDS);
        if (!board.add(task)) return;
        taskSeals.put(id, seal.key());
        emptyCandidates.put(id, cached.copy());
    }

    public Optional<BlockAssignment> claimFill(ThaumcraftGolem golem, Predicate<BlockPos> canReach) {
        return claimBlock(golem, canReach, Type.FILL);
    }

    public Optional<BlockAssignment> claimEmpty(ThaumcraftGolem golem, Predicate<BlockPos> canReach) {
        return claimBlock(golem, canReach, Type.EMPTY);
    }

    public Optional<BlockAssignment> claimBlock(ThaumcraftGolem golem, Predicate<BlockPos> canReach) {
        return claimBlock(golem, canReach, null);
    }

    private static boolean matchesType(Type restricted,Type actual) {
        return restricted==null || restricted==actual || restricted==Type.FILL&&actual.fills() || restricted==Type.EMPTY&&actual.extracts();
    }

    private Optional<BlockAssignment> claimBlock(ThaumcraftGolem golem, Predicate<BlockPos> canReach, Type restrictedType) {
        if (!(golem.level() instanceof ServerLevel level)) return Optional.empty();
        var context = new TaskSelection.Context(golem.getUUID(), golem.golemColor(),
                new TaskSelection.Position(golem.getX(), golem.getY(), golem.getZ()),
                task -> entityPosition(level,task), task -> Optional.ofNullable(seals.get(taskSeals.get(task.getId())))
                        .map(seal -> new TaskSelection.SealRule(seal.color(),
                                ignored -> matchesType(restrictedType,seal.type()) && blockEligible(level, golem, task, seal))));
        for (GolemTask task : board.sorted(null, context)) {
            Seal seal = seals.get(taskSeals.get(task.getId()));
            if (seal == null || !matchesType(restrictedType,seal.type())
                    || !blockEligible(level, golem, task, seal)) continue;
            SealKey destination=destination(level,task,seal);
            if(destination==null || !canReach.test(destination.pos())) continue;
            var lease = board.claim(task.getId(), golem.getUUID());
            if (lease.isEmpty()) continue;
            workers.put(task.getId(), golem.getUUID());
            if (seal.type().fills()) createFillTask(seal);
            return Optional.of(new BlockAssignment(lease.get(), seal.key()));
        }
        return Optional.empty();
    }

    private SealKey destination(ServerLevel level,GolemTask task,Seal seal) {
        return switch(seal.type()) { case PROVIDE->logistics.destination(level,task); case HARVEST->farm.destination(task); case BREAKER,BREAKER_ADVANCED->breaker.destination(task); case LUMBER->lumber.destination(task); default->seal.key(); };
    }

    private boolean blockEligible(ServerLevel level, ThaumcraftGolem golem, GolemTask task, Seal seal) {
        return !golem.isFollowingOwner() && !seal.type().picksUp() && seal.type()!=Type.STOCK
                && (!seal.type().advanced() || golem.design().hasTrait(GolemTrait.SMART)) && !stopped(level, seal) && golem.isAlive() && !golem.isRemoved()
                && !task.isSuspended() && !task.isCompleted() && taskSeals.containsKey(task.getId())
                && (seal.type().forestry() || !golem.design().hasTrait(GolemTrait.CLUMSY))
                && (!seal.locked() || seal.owner().equals(golem.ownerId()))
                && TaskSelection.colorMatches(golem.golemColor(), seal.color())
                && retryAfter.getOrDefault(task.getId(), Map.of()).getOrDefault(golem.getUUID(), 0L) <= level.getGameTime()
                && golem.home().distSqr(seal.key().pos()) < (double) golem.design().stats().homeRadius()
                        * golem.design().stats().homeRadius()
                && (seal.type()==Type.USE ? use.eligible(level,golem,task,seal)
                    : seal.type()==Type.LUMBER ? lumber.eligible(level,golem,task,seal)
                    : seal.type().breaksBlocks() ? breaker.eligible(level,golem,task,seal)
                    : seal.type()==Type.HARVEST ? farm.eligible(level,golem,task,seal)
                    : seal.type()==Type.PROVIDE ? logistics.eligible(level,golem,task,seal)
                    : seal.type().fills() ? canFill(level,golem,seal)
                    : GolemContainers.canExtract(level, golem, seal.key(), emptyCandidates.get(task.getId()), extractionPolicy(seal,task.getId()), seal.leaveOne()));
    }

    public SealKey blockTarget(ServerLevel level, ThaumcraftGolem golem, BlockAssignment assignment) {
        if (golem.level() != level || !assignment.lease().owner().equals(golem.getUUID())
                || !assignment.seal().equals(taskSeals.get(assignment.lease().taskId()))) return null;
        GolemTask task = board.task(assignment.lease()).orElse(null);
        Seal seal = seals.get(assignment.seal());
        return task != null && seal != null && blockEligible(level, golem, task, seal) ? destination(level,task,seal) : null;
    }

    /** A false Breaker callback may mean another hardness step, rather than a failed transaction. */
    public boolean blockWorkOngoing(ServerLevel level,ThaumcraftGolem golem,BlockAssignment assignment) {
        return breaker.ongoing(level,golem,assignment) || lumber.ongoing(level,golem,assignment);
    }

    public SealKey fillTarget(ServerLevel level, ThaumcraftGolem golem, BlockAssignment assignment) {
        SealKey key = blockTarget(level, golem, assignment);
        return key != null && seals.get(assignment.seal()).type().fills() ? key : null;
    }

    public SealKey emptyTarget(ServerLevel level, ThaumcraftGolem golem, BlockAssignment assignment) {
        SealKey key = blockTarget(level, golem, assignment);
        return key != null && seals.get(assignment.seal()).type().extracts() ? key : null;
    }

    public boolean completeBlock(ServerLevel level, ThaumcraftGolem golem, BlockAssignment assignment) {
        Seal seal = seals.get(assignment.seal());
        return seal != null && (seal.type()==Type.USE ? use.complete(level,golem,assignment)
                    : seal.type()==Type.LUMBER ? lumber.complete(level,golem,assignment)
                : seal.type().breaksBlocks() ? breaker.complete(level,golem,assignment)
                : seal.type()==Type.HARVEST ? farm.complete(level,golem,assignment)
                : seal.type()==Type.PROVIDE ? logistics.complete(level,golem,assignment)
                : seal.type().fills() ? fill(level, golem, assignment) : empty(level, golem, assignment));
    }

    public boolean fill(ServerLevel level, ThaumcraftGolem golem, BlockAssignment assignment) {
        SealKey target = fillTarget(level, golem, assignment);
        if (target == null || golem.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(target.pos())) > 4)
            return false;
        var seal=seals.get(target);
        int moved = seal.type()==Type.FILL_ADVANCED?GolemContainers.deposit(level,golem,target,seal.logistics()):GolemContainers.deposit(level, golem, target, seal.filter());
        board.complete(assignment.lease(), true);
        board.suspend(assignment.lease());
        forget(assignment.lease().taskId());
        if (moved <= 0) return false;
        golem.addRankXp(1);
        golem.swing(InteractionHand.MAIN_HAND);
        level.playSound(null, golem.getX(), golem.getY(), golem.getZ(), SoundEvents.ITEM_PICKUP,
                SoundSource.NEUTRAL, 0.125F, 1.0F);
        return true;
    }

    public boolean empty(ServerLevel level, ThaumcraftGolem golem, BlockAssignment assignment) {
        SealKey target = emptyTarget(level, golem, assignment);
        if (target == null || golem.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(target.pos())) > 4) return false;
        int moved = GolemContainers.extractInto(golem, GolemContainers.handler(level, target),
                emptyCandidates.get(assignment.lease().taskId()), extractionPolicy(seals.get(target),assignment.lease().taskId()), seals.get(target).leaveOne());
        emptyCycles.merge(target,1,(old,one)->old+one);
        board.complete(assignment.lease(), true);
        board.suspend(assignment.lease());
        forget(assignment.lease().taskId());
        if (moved <= 0) return false;
        golem.swing(InteractionHand.MAIN_HAND);
        level.playSound(null, golem.getX(), golem.getY(), golem.getZ(), SoundEvents.ITEM_PICKUP,
                SoundSource.NEUTRAL, 0.125F, 1.0F);
        return true;
    }

    public void releaseFill(ServerLevel level, ThaumcraftGolem golem, BlockAssignment assignment, boolean failed) {
        releaseBlock(level, golem, assignment, failed);
    }

    public void releaseBlock(ServerLevel level, ThaumcraftGolem golem, BlockAssignment assignment, boolean failed) {
        if (failed) retryAfter.computeIfAbsent(assignment.lease().taskId(), ignored -> new HashMap<>())
                .put(golem.getUUID(), level.getGameTime() + 100);
        if (board.release(assignment.lease())) { workers.remove(assignment.lease().taskId(), golem.getUUID()); breaker.released(assignment.lease().taskId()); lumber.released(assignment.lease().taskId()); }
    }

    /** BETA26 tests support and outward block. Unknown signal chunks pause without loading. */
    public static boolean stopped(ServerLevel level, Seal seal) {
        if (seal == null || !seal.redstone()) return false;
        BlockPos support = seal.key().pos(), outward = support.relative(seal.key().face());
        // Vanilla conductor signal queries can inspect a second neighbor layer.
        for (BlockPos pos : List.of(support, outward)) {
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++)
                if (!level.hasChunkAt(pos.offset(dx, 0, dz))) return true;
        }
        return level.hasNeighborSignal(support) || level.hasNeighborSignal(outward);
    }

    public static double reachSquared(ItemEntity item) {
        return 3.5D + (item.getBbWidth() / 2.0F) * (item.getBbWidth() / 2.0F);
    }

    private void suspend(int id) {
        board.get(id).ifPresent(task -> task.setSuspended(true));
        forget(id);
    }

    private void forget(int id) {
        logistics.forget(id);
        farm.forget(id); breaker.forget(id); lumber.forget(id); use.forget(id);
        emptyCandidates.remove(id);
        UUID item = targets.remove(id);
        if (item != null) itemTasks.remove(item, id);
        taskSeals.remove(id);
        retryAfter.remove(id);
        workers.remove(id);
    }

    private SealFilter extractionPolicy(Seal seal,int taskId) {
        if(seal.type()!=Type.EMPTY_ADVANCED) return seal.filter();
        var candidate=emptyCandidates.get(taskId);
        return new SealFilter(candidate==null?ItemStack.EMPTY:candidate,false,0,seal.logistics().options());
    }
    private static Optional<TaskSelection.Position> entityPosition(ServerLevel level,GolemTask task) {
        var entity=task.getEntityId()==null?null:level.getEntity(task.getEntityId());
        return entity==null || !entity.isAlive()?Optional.empty():Optional.of(new TaskSelection.Position(
                entity.blockPosition().getX(),entity.blockPosition().getY(),entity.blockPosition().getZ()));
    }
    TaskBoard tasks() { return board; }
    int nextId() { return allocateTaskId(); }
    boolean enqueue(GolemTask task,Seal seal) {
        if(!board.add(task)) return false;
        taskSeals.put(task.getId(),seal.key()); return true;
    }
    void cancel(int id) { suspend(id); }
    void finish(TaskBoard.Lease lease) {
        board.complete(lease,true); board.suspend(lease); forget(lease.taskId());
    }

    private int allocateTaskId() {
        while (board.get(++nextTaskId).isPresent()) { /* skip live IDs on integer wrap */ }
        return nextTaskId;
    }

    private static ItemEntity item(ServerLevel level, UUID id) {
        return id != null && level.getEntity(id) instanceof ItemEntity item ? item : null;
    }

    private static SealArea.SealPos corePos(SealKey key) {
        return new SealArea.SealPos(new SealArea.BlockPos(key.pos().getX(), key.pos().getY(), key.pos().getZ()),
                SealArea.Facing.valueOf(key.face().name()));
    }

    /** Original default area: one outward block, 5 blocks on both tangent axes. */
    public static AABB bounds(SealKey key) {
        return bounds(key, defaultArea(key));
    }

    public static AABB bounds(Seal seal) { return bounds(seal.key(), seal.area()); }

    private static AABB bounds(SealKey key, BlockPos area) {
        SealArea.Bounds bounds = new SealArea(corePos(key), coreArea(area)).bounds();
        return new AABB(bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX(), bounds.maxY(), bounds.maxZ());
    }

    private static BlockPos defaultArea(SealKey key) {
        var size = SealArea.defaults(corePos(key)).size();
        return new BlockPos(size.x(), size.y(), size.z());
    }

    private static SealArea.BlockPos coreArea(BlockPos area) {
        return new SealArea.BlockPos(area.getX(), area.getY(), area.getZ());
    }

    private static boolean validArea(BlockPos area) { return area != null && SealAreaControls.valid(coreArea(area)); }

    public static GolemSeals load(CompoundTag tag, HolderLookup.Provider registries) {
        GolemSeals data = new GolemSeals();
        ListTag rows = tag.getList("seals", Tag.TAG_COMPOUND);
        for (int i = 0; i < rows.size(); i++) {
            CompoundTag row = rows.getCompound(i);
            int face = row.getByte("face");
            String typeKey = row.getString("type");
            Type loadedType=java.util.Arrays.stream(Type.values()).filter(t->t.key.equals(typeKey)).findFirst().orElse(null);
            if (loadedType==null || face < 0 || face >= Direction.values().length
                    || row.getString("owner").isEmpty()) continue;
            SealKey key = new SealKey(BlockPos.of(row.getLong("pos")), Direction.values()[face]);
            BlockPos area = row.contains("area", Tag.TAG_LONG) ? BlockPos.of(row.getLong("area")) : defaultArea(key);
            // Old worlds used default sizes. Malformed/oversized external data cannot grow a scan unboundedly.
            if (!loadedType.areaControl() || !validArea(area)) area = defaultArea(key);
            try {
                data.seals.putIfAbsent(key, new Seal(key, UUID.fromString(row.getString("owner")),
                        row.getByte("priority"), row.getByte("color"), row.getBoolean("locked"),
                        loadedType,
                        SealFilter.load(row, registries), UUID.randomUUID(), row.getBoolean("redstone"), area,
                        (loadedType.extracts() || loadedType==Type.PROVIDE) && row.getBoolean("pleave"),
                        LogisticsPolicy.load(row,registries,loadedType==Type.STOCK).withCycle(loadedType==Type.EMPTY_ADVANCED&&row.getBoolean("pcycle")).withSilk(loadedType==Type.BREAKER_ADVANCED&&row.getBoolean("psilk")).withExistingOnly(loadedType==Type.FILL_ADVANCED&&row.getBoolean("pexist")),
                        new HarvestSettings(!row.contains("prep") || row.getBoolean("prep"),row.getBoolean("ppro"))));
                if(loadedType==Type.HARVEST) data.farm.loadRow(row,data.seals.get(key));
                if(loadedType==Type.USE) data.use.loadRow(row,data.seals.get(key));
            } catch (IllegalArgumentException ignored) {
                // Invalid external data must not become a seal owned by everybody.
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag rows = new ListTag();
        for (Seal seal : seals.values()) {
            CompoundTag row = new CompoundTag();
            row.putString("type", seal.type().key);
            row.putLong("pos", seal.key().pos().asLong());
            row.putByte("face", (byte) seal.key().face().ordinal());
            row.putString("owner", seal.owner().toString());
            row.putByte("priority", seal.priority());
            row.putByte("color", seal.color());
            row.putBoolean("locked", seal.locked());
            row.putBoolean("redstone", seal.redstone());
            row.putBoolean("pleave", seal.leaveOne());
            seal.filter().save(row, registries);
            if(seal.type().logistics()) seal.logistics().save(row,registries);
            if(seal.type()==Type.HARVEST) { row.putBoolean("prep",seal.harvest().replant()); row.putBoolean("ppro",seal.harvest().provision()); farm.saveRow(row,seal); }
            if(seal.type()==Type.USE) use.saveRow(row,seal);
            row.putLong("area", seal.area().asLong());
            rows.add(row);
        }
        tag.put("seals", rows);
        return tag;
    }
}
