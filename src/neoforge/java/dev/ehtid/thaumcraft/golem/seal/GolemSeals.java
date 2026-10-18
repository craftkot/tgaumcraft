package dev.ehtid.thaumcraft.golem.seal;

import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import dev.ehtid.thaumcraft.golem.inventory.GolemContainers;
import dev.ehtid.thaumcraft.port.core.golem.GolemTask;
import dev.ehtid.thaumcraft.port.core.golem.GolemTrait;
import dev.ehtid.thaumcraft.port.core.golem.SealArea;
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
    public enum Type {
        PICKUP(PICKUP_KEY), FILL(FILL_KEY);
        public final String key;
        Type(String key) { this.key = key; }
    }

    public record SealKey(BlockPos pos, Direction face) {
        public SealKey { pos = pos.immutable(); }
    }

    public record Seal(SealKey key, UUID owner, byte priority, byte color, boolean locked, Type type,
                       SealFilter filter, UUID instanceId, boolean redstone) {
        public Seal(SealKey key, UUID owner, byte priority, byte color, boolean locked, Type type) {
            this(key, owner, priority, color, locked, type, SealFilter.defaults(), UUID.randomUUID(), false);
        }
    }

    public record Assignment(TaskBoard.Lease lease, UUID targetId, SealKey seal) {}
    public record FillAssignment(TaskBoard.Lease lease, SealKey seal) {}

    private final Map<SealKey, Seal> seals = new LinkedHashMap<>();
    private final TaskBoard board = new TaskBoard();
    private final Map<Integer, UUID> targets = new HashMap<>();
    private final Map<UUID, Integer> itemTasks = new HashMap<>();
    private final Map<Integer, SealKey> taskSeals = new HashMap<>();
    private final Map<Integer, Map<UUID, Long>> retryAfter = new HashMap<>();
    private final Map<Integer, UUID> workers = new HashMap<>();
    private final Map<SealKey, Integer> watchedFillTasks = new HashMap<>();
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
                current.locked(), current.type(), java.util.Objects.requireNonNull(filter), current.instanceId(), current.redstone()));
    }

    public boolean configureControls(Seal expected, UUID owner, int priority, int color, boolean locked, boolean redstone) {
        Seal current = expected == null ? null : seals.get(expected.key());
        if (current == null || !current.instanceId().equals(expected.instanceId()) || !current.owner().equals(owner)
                || priority < -5 || priority > 5 || color < 0 || color > 16) return false;
        return replace(current, new Seal(current.key(), current.owner(), (byte) priority, (byte) color,
                locked, current.type(), current.filter(), current.instanceId(), redstone));
    }

    private boolean replace(Seal current, Seal replacement) {
        seals.put(current.key(), replacement);
        watchedFillTasks.remove(current.key());
        for (var entry : new ArrayList<>(taskSeals.entrySet())) {
            if (entry.getValue().equals(current.key())) suspend(entry.getKey());
        }
        setDirty();
        return true;
    }

    public List<Seal> all() {
        return List.copyOf(seals.values());
    }

    public int pendingTasks() { return taskSeals.size(); }
    public long reservedTasks() { return board.snapshot().stream().filter(GolemTask::isReserved).count(); }

    private void removeSeal(SealKey key) {
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
            if (level.getGameTime() % 20 == 0 && level.isEmptyBlock(seal.key().pos())) {
                removeSeal(seal.key());
                continue;
            }
            if (stopped(level, seal)) continue;
            if (seal.type() == Type.FILL) {
                if (level.getGameTime() % 20 == 0) createFillTask(seal);
                continue;
            }
            if (Math.floorMod(level.getGameTime() + phase++, 5) != 0) continue;
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, bounds(seal.key()),
                    entity -> entity.isAlive() && entity.onGround() && !entity.hasPickUpDelay()
                            && seal.filter().accepts(entity.getItem()))) {
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
    }

    public Optional<Assignment> claim(ThaumcraftGolem golem, Predicate<ItemEntity> canReach) {
        if (!(golem.level() instanceof ServerLevel level) || golem.design().hasTrait(GolemTrait.CLUMSY))
            return Optional.empty();
        TaskSelection.Context context = new TaskSelection.Context(golem.getUUID(), golem.golemColor(),
                new TaskSelection.Position(golem.getX(), golem.getY(), golem.getZ()),
                task -> Optional.ofNullable(item(level, task.getEntityId()))
                        .map(item -> new TaskSelection.Position(item.blockPosition().getX(),
                                item.blockPosition().getY(), item.blockPosition().getZ())),
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
        if (!golem.isAlive() || golem.isRemoved() || task.isSuspended() || task.isCompleted()
                || !targets.containsKey(task.getId())
                || !level.hasChunkAt(seal.key().pos()) || level.isEmptyBlock(seal.key().pos()) || stopped(level, seal)
                || golem.design().hasTrait(GolemTrait.CLUMSY)) return false;
        // BETA26 accidentally UUID.equals(String) for locked owners. UUID identity
        // fixes that old bug, while the original unlocked default remains public.
        if (seal.locked() && !seal.owner().equals(golem.ownerId())) return false;
        if (golem.golemColor() > 0 && seal.color() > 0 && golem.golemColor() != seal.color()) return false;
        if (retryAfter.getOrDefault(task.getId(), Map.of()).getOrDefault(golem.getUUID(), 0L)
                > level.getGameTime()) return false;
        ItemEntity item = item(level, task.getEntityId());
        if (item == null || !item.isAlive() || !seal.filter().accepts(item.getItem())
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
        if (board.release(assignment.lease())) workers.remove(assignment.lease().taskId(), golem.getUUID());
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

    public Optional<FillAssignment> claimFill(ThaumcraftGolem golem, Predicate<BlockPos> canReach) {
        if (!(golem.level() instanceof ServerLevel level)) return Optional.empty();
        var context = new TaskSelection.Context(golem.getUUID(), golem.golemColor(),
                new TaskSelection.Position(golem.getX(), golem.getY(), golem.getZ()),
                ignored -> Optional.empty(), task -> Optional.ofNullable(seals.get(taskSeals.get(task.getId())))
                        .map(seal -> new TaskSelection.SealRule(seal.color(),
                                ignored -> fillEligible(level, golem, task, seal))));
        for (GolemTask task : board.sorted(GolemTask.Type.BLOCK, context)) {
            Seal seal = seals.get(taskSeals.get(task.getId()));
            if (seal == null || !fillEligible(level, golem, task, seal) || !canReach.test(seal.key().pos())) continue;
            var lease = board.claim(task.getId(), golem.getUUID());
            if (lease.isEmpty()) continue;
            workers.put(task.getId(), golem.getUUID());
            createFillTask(seal);
            return Optional.of(new FillAssignment(lease.get(), seal.key()));
        }
        return Optional.empty();
    }

    private boolean fillEligible(ServerLevel level, ThaumcraftGolem golem, GolemTask task, Seal seal) {
        return seal.type() == Type.FILL && !stopped(level, seal) && golem.isAlive() && !golem.isRemoved()
                && !task.isSuspended() && !task.isCompleted() && taskSeals.containsKey(task.getId())
                && !golem.design().hasTrait(GolemTrait.CLUMSY)
                && (!seal.locked() || seal.owner().equals(golem.ownerId()))
                && TaskSelection.colorMatches(golem.golemColor(), seal.color())
                && retryAfter.getOrDefault(task.getId(), Map.of()).getOrDefault(golem.getUUID(), 0L) <= level.getGameTime()
                && golem.home().distSqr(seal.key().pos()) < (double) golem.design().stats().homeRadius()
                        * golem.design().stats().homeRadius()
                && GolemContainers.canDeposit(level, golem, seal.key(), seal.filter());
    }

    public SealKey fillTarget(ServerLevel level, ThaumcraftGolem golem, FillAssignment assignment) {
        if (golem.level() != level || !assignment.lease().owner().equals(golem.getUUID())
                || !assignment.seal().equals(taskSeals.get(assignment.lease().taskId()))) return null;
        GolemTask task = board.task(assignment.lease()).orElse(null);
        Seal seal = seals.get(assignment.seal());
        return task != null && seal != null && fillEligible(level, golem, task, seal) ? seal.key() : null;
    }

    public boolean fill(ServerLevel level, ThaumcraftGolem golem, FillAssignment assignment) {
        SealKey target = fillTarget(level, golem, assignment);
        if (target == null || golem.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(target.pos())) > 4)
            return false;
        int moved = GolemContainers.deposit(level, golem, target, seals.get(target).filter());
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

    public void releaseFill(ServerLevel level, ThaumcraftGolem golem, FillAssignment assignment, boolean failed) {
        if (failed) retryAfter.computeIfAbsent(assignment.lease().taskId(), ignored -> new HashMap<>())
                .put(golem.getUUID(), level.getGameTime() + 100);
        if (board.release(assignment.lease())) workers.remove(assignment.lease().taskId(), golem.getUUID());
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
        UUID item = targets.remove(id);
        if (item != null) itemTasks.remove(item, id);
        taskSeals.remove(id);
        retryAfter.remove(id);
        workers.remove(id);
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
        SealArea.Bounds bounds = SealArea.defaults(corePos(key)).bounds();
        return new AABB(bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX(), bounds.maxY(), bounds.maxZ());
    }

    public static GolemSeals load(CompoundTag tag, HolderLookup.Provider registries) {
        GolemSeals data = new GolemSeals();
        ListTag rows = tag.getList("seals", Tag.TAG_COMPOUND);
        for (int i = 0; i < rows.size(); i++) {
            CompoundTag row = rows.getCompound(i);
            int face = row.getByte("face");
            String typeKey = row.getString("type");
            if ((!PICKUP_KEY.equals(typeKey) && !FILL_KEY.equals(typeKey)) || face < 0 || face >= Direction.values().length
                    || row.getString("owner").isEmpty()) continue;
            SealKey key = new SealKey(BlockPos.of(row.getLong("pos")), Direction.values()[face]);
            try {
                data.seals.putIfAbsent(key, new Seal(key, UUID.fromString(row.getString("owner")),
                        row.getByte("priority"), row.getByte("color"), row.getBoolean("locked"),
                        FILL_KEY.equals(typeKey) ? Type.FILL : Type.PICKUP, SealFilter.load(row, registries), UUID.randomUUID(), row.getBoolean("redstone")));
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
            seal.filter().save(row, registries);
            // Configurable areas remain deferred.
            row.putLong("area", new BlockPos(seal.key().face().getStepX() == 0 ? 3 : 1,
                    seal.key().face().getStepY() == 0 ? 3 : 1,
                    seal.key().face().getStepZ() == 0 ? 3 : 1).asLong());
            rows.add(row);
        }
        tag.put("seals", rows);
        return tag;
    }
}
