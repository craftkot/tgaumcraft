package dev.ehtid.thaumcraft.port.core;

import dev.ehtid.thaumcraft.port.core.golem.GolemTask;
import dev.ehtid.thaumcraft.port.core.golem.GolemColorRules;
import dev.ehtid.thaumcraft.port.core.golem.ProvisionBoard;
import dev.ehtid.thaumcraft.port.core.golem.ProvisionRequest;
import dev.ehtid.thaumcraft.port.core.golem.ProvisionRules;
import dev.ehtid.thaumcraft.port.core.golem.SealArea;
import dev.ehtid.thaumcraft.port.core.golem.SealAreaControls;
import dev.ehtid.thaumcraft.port.core.golem.TaskBoard;
import dev.ehtid.thaumcraft.port.core.golem.TaskSelection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicLong;

/** Official BETA26 API rules plus deliberately separate modern server-safety invariants. */
public final class LegacyGolemTaskTest {
    private static int assertions;
    private static final UUID ALICE = new UUID(0, 1);
    private static final UUID BOB = new UUID(0, 2);
    private static final SealArea.BlockPos ORIGIN = new SealArea.BlockPos(0, 0, 0);

    private static void eq(Object expected, Object actual) {
        assertions++;
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError("Expected " + expected + ", got " + actual);
        }
    }

    private static void rejects(Class<? extends Throwable> expected, Runnable action) {
        assertions++;
        try {
            action.run();
        } catch (Throwable failure) {
            if (expected.isInstance(failure)) return;
            throw new AssertionError("Wrong exception", failure);
        }
        throw new AssertionError("Expected " + expected.getSimpleName());
    }

    private static GolemTask block(int id, int x) {
        return GolemTask.block(id, null, new SealArea.BlockPos(x, 0, 0));
    }

    private static ProvisionRequest<String> provision() {
        return new ProvisionBoard<String>(() -> 0).request(new ProvisionRequest.EntityTarget(BOB), "iron", 1, 0).orElseThrow();
    }

    private static TaskSelection.Context permissive(UUID owner) {
        return new TaskSelection.Context(owner, (byte) 0, new TaskSelection.Position(.5, .5, .5),
                task -> Optional.of(new TaskSelection.Position(0, 0, 0)), task -> Optional.empty());
    }

    private static void taskSourceRules() {
        GolemTask task = block(17, 2);
        eq(300L, task.getLifespan());
        eq((byte) 0, task.getPriority());
        eq(false, task.isReserved());
        eq(false, task.isCompleted());
        eq(false, task.isSuspended());
        eq(null, task.getGolemUUID());
        eq(GolemTask.Type.BLOCK, task.getType());
        task.setReserved(true);
        eq(420L, task.getLifespan());
        task.setReserved(false);
        eq(540L, task.getLifespan());
        // Both false calls also extend lifespan in the actual public API.
        task.setReserved(false);
        eq(660L, task.getLifespan());
        task.setCompletion(false);
        eq(661L, task.getLifespan());
        task.setCompletion(true);
        eq(662L, task.getLifespan());
        eq(true, task.isCompleted());
        task.setLifespan(Short.MAX_VALUE);
        task.setCompletion(false);
        eq((long) Short.MIN_VALUE, task.getLifespan());
        task.setLifespan((short) 32_700);
        task.setReserved(true);
        eq((long) (short) 32_820, task.getLifespan());
        task.setLinkedProvision(provision());
        task.setSuspended(false);
        eq(null, task.getLinkedProvision());
        task.setLinkedProvision(provision());
        task.setSuspended(true);
        eq(null, task.getLinkedProvision());
        task.setData(42);
        eq(42, task.getData());
        task.setPriority((byte) -128);
        eq((byte) -128, task.getPriority());
        task.setGolemUUID(ALICE);
        eq(ALICE, task.getGolemUUID());
        eq(task, block(17, 200));
        eq(task.hashCode(), block(17, 200).hashCode());
        eq(false, task.equals(block(18, 2)));
        GolemTask entity = GolemTask.entity(18, null, BOB);
        eq(GolemTask.Type.ENTITY, entity.getType());
        eq(BOB, entity.getEntityId());
        eq(null, entity.getPos());
    }

    private static void allFaceAreas() {
        SealArea.BlockPos anchor = new SealArea.BlockPos(20, 70, -30);
        for (SealArea.Facing face : SealArea.Facing.values()) {
            SealArea.SealPos seal = new SealArea.SealPos(anchor, face);
            SealArea defaults = SealArea.defaults(seal);
            eq(25, defaults.count());
            eq(anchor.offset(face.dx() == 0 ? -2 : face.dx(),
                    face.dy() == 0 ? -2 : face.dy(), face.dz() == 0 ? -2 : face.dz()),
                    defaults.positionAt(0));
            // An asymmetric area catches accidental orientation or axis swaps.
            SealArea area = new SealArea(seal, new SealArea.BlockPos(2, 3, 4));
            int[] offsets = {face.dx(), face.dy(), face.dz()};
            int[] sizes = {2, 3, 4};
            int[] base = {anchor.x(), anchor.y(), anchor.z()};
            int[] lo = new int[3];
            int[] hi = new int[3];
            for (int axis = 0; axis < 3; axis++) {
                if (offsets[axis] > 0) {
                    lo[axis] = base[axis] + 1;
                    hi[axis] = base[axis] + sizes[axis] + 1;
                } else if (offsets[axis] < 0) {
                    lo[axis] = base[axis] - sizes[axis];
                    hi[axis] = base[axis];
                } else {
                    lo[axis] = base[axis] - sizes[axis] + 1;
                    hi[axis] = base[axis] + sizes[axis];
                }
            }
            eq(new SealArea.Bounds(lo[0], lo[1], lo[2], hi[0], hi[1], hi[2]), area.bounds());
            Set<SealArea.BlockPos> expected = new HashSet<>();
            for (int x = lo[0]; x < hi[0]; x++) {
                for (int y = lo[1]; y < hi[1]; y++) {
                    for (int z = lo[2]; z < hi[2]; z++) {
                        expected.add(new SealArea.BlockPos(x, y, z));
                    }
                }
            }
            Set<SealArea.BlockPos> actual = new HashSet<>();
            for (SealArea.BlockPos pos : area) {
                eq(true, area.bounds().contains(pos));
                eq(true, actual.add(pos));
            }
            eq(expected, actual);
            eq(expected.size(), area.count());
            eq(area.positionAt(0), area.positionAt(area.count()));
            eq(area.positionAt(1), area.positionAt(area.count() + 1));
            // The first varying coordinate is Z, then X, then Y, even for negative faces.
            eq(area.positionAt(0).offset(0, 0, face.dz() < 0 ? -1 : 1), area.positionAt(1));
        }
        SealArea unit = new SealArea(new SealArea.SealPos(ORIGIN, SealArea.Facing.UP),
                new SealArea.BlockPos(1, 1, 1));
        List<SealArea.BlockPos> unitPositions = new ArrayList<>();
        unit.forEach(unitPositions::add);
        eq(List.of(new SealArea.BlockPos(0, 1, 0)), unitPositions);
        rejects(IllegalArgumentException.class, () -> unit.positionAt(-1));
        rejects(IllegalArgumentException.class, () -> new SealArea(unit.seal(),
                new SealArea.BlockPos(0, 1, 1)));
        rejects(ArithmeticException.class, () -> new SealArea(unit.seal(),
                new SealArea.BlockPos(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE)));
    }

    private static void sortingAndSealPolicy() {
        GolemTask near = block(1, 1);
        GolemTask distantHigh = block(2, 16);
        distantHigh.setPriority((byte) 1);
        GolemTask equal = block(3, -1);
        GolemTask lower = block(4, 0);
        lower.setPriority((byte) -1);
        // scores: 0, 1, 1, 256. One source priority unit offsets 256 squared blocks.
        eq(List.of(distantHigh, near, equal, lower), TaskSelection.sorted(
                List.of(near, distantHigh, equal, lower), GolemTask.Type.BLOCK, permissive(ALICE)));
        eq(0.75, TaskSelection.score(new TaskSelection.Position(0, 0, 0),
                new TaskSelection.Position(0, 0, 0), (byte) 0));
        distantHigh.setGolemUUID(BOB);
        eq(List.of(near), TaskSelection.sorted(List.of(distantHigh, near),
                GolemTask.Type.BLOCK, permissive(ALICE)));
        // Null selector UUID is used by source seal chaining and does not impose affinity.
        eq(List.of(distantHigh, near), TaskSelection.sorted(List.of(near, distantHigh),
                GolemTask.Type.BLOCK, permissive(null)));
        eq(true, TaskSelection.colorMatches((byte) 0, (byte) 5));
        eq(true, TaskSelection.colorMatches((byte) 5, (byte) 0));
        eq(true, TaskSelection.colorMatches((byte) 5, (byte) 5));
        eq(false, TaskSelection.colorMatches((byte) 5, (byte) 4));
        TaskSelection.Context red = new TaskSelection.Context(ALICE, (byte) 2,
                new TaskSelection.Position(0, 0, 0), task -> Optional.empty(),
                task -> Optional.of(new TaskSelection.SealRule((byte) 3, ignored -> true)));
        eq(List.of(), TaskSelection.sorted(List.of(near), GolemTask.Type.BLOCK, red));
        TaskSelection.Context forbiddenTrait = new TaskSelection.Context(ALICE, (byte) 0,
                new TaskSelection.Position(0, 0, 0), task -> Optional.empty(),
                task -> Optional.of(new TaskSelection.SealRule((byte) 0, ignored -> false)));
        eq(List.of(), TaskSelection.sorted(List.of(near), GolemTask.Type.BLOCK, forbiddenTrait));
        GolemTask entity = GolemTask.entity(5, null, BOB);
        eq(List.of(entity), TaskSelection.sorted(List.of(entity), GolemTask.Type.ENTITY, permissive(ALICE)));
        entity.setLinkedProvision(provision());
        eq(List.of(), TaskSelection.sorted(List.of(entity), GolemTask.Type.ENTITY, red));
        eq(true, entity.isSuspended());
        eq(null, entity.getLinkedProvision());
        GolemTask suspended = block(6, 0);
        suspended.setSuspended(true);
        GolemTask completed = block(7, 0);
        completed.setCompletion(true);
        GolemTask expired = block(8, 0);
        expired.setLifespan((short) 0);
        GolemTask reserved = block(9, 0);
        reserved.setReserved(true);
        eq(List.of(near), TaskSelection.sorted(List.of(suspended, completed, expired, reserved, entity, near),
                GolemTask.Type.BLOCK, permissive(ALICE)));
    }

    private static void exclusiveLeasesAndLifecycle() throws InterruptedException {
        TaskBoard board = new TaskBoard();
        GolemTask task = block(1, 0);
        eq(true, board.add(task));
        eq(false, board.add(block(1, 20)));
        TaskBoard.Lease first = board.claim(1, ALICE).orElseThrow();
        eq(420L, task.getLifespan());
        eq(Optional.empty(), board.claim(1, ALICE));
        eq(Optional.empty(), board.claim(1, BOB));
        eq(420L, task.getLifespan());
        eq(Optional.of(task), board.task(first));
        eq(true, board.release(first));
        eq(540L, task.getLifespan());
        eq(false, board.release(first));
        eq(540L, task.getLifespan());
        TaskBoard.Lease second = board.claim(1, BOB).orElseThrow();
        eq(false, board.complete(first, true));
        eq(false, board.suspend(first));
        eq(false, board.release(first));
        eq(true, task.isReserved());
        eq(true, board.complete(second, false));
        eq(false, task.isCompleted());
        eq(true, board.complete(second, true));
        long lifespan = task.getLifespan();
        eq(false, board.complete(second, true));
        eq(lifespan, task.getLifespan());
        eq(true, board.suspend(second));
        eq(true, board.release(second));
        eq(Optional.empty(), board.claim(1, ALICE));
        List<Integer> cleanup = new ArrayList<>();
        board.tickSecond(removed -> cleanup.add(removed.getId()));
        eq(List.of(1), cleanup);
        eq(List.of(), board.snapshot());
        eq(false, board.complete(second, true));

        GolemTask owned = block(2, 0);
        owned.setGolemUUID(ALICE);
        board.add(owned);
        eq(Optional.empty(), board.claim(2, BOB));
        TaskBoard.Lease beforeUnload = board.claim(2, ALICE).orElseThrow();
        AtomicReference<Throwable> wrongThread = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try { board.release(beforeUnload); } catch (Throwable failure) { wrongThread.set(failure); }
        });
        thread.start();
        thread.join();
        eq(true, wrongThread.get() instanceof IllegalStateException);
        eq(true, owned.isReserved());
        board.close();
        eq(true, board.isClosed());
        eq(false, owned.isReserved());
        eq(false, board.release(beforeUnload));
        eq(List.of(), board.snapshot());
        eq(false, board.add(block(3, 0)));
        board.close();
        // Closing/reopening a world with matching task+owner cannot revive an old token.
        TaskBoard reloaded = new TaskBoard();
        GolemTask newTask = block(2, 0);
        reloaded.add(newTask);
        TaskBoard.Lease afterReload = reloaded.claim(2, ALICE).orElseThrow();
        eq(false, reloaded.release(beforeUnload));
        eq(true, newTask.isReserved());
        eq(true, reloaded.release(afterReload));
        reloaded.close();
    }

    private static void expiryAndWatchdog() {
        TaskBoard board = new TaskBoard(2);
        GolemTask expires = block(1, 0);
        expires.setLifespan((short) 1);
        board.add(expires);
        List<Integer> cleanup = new ArrayList<>();
        board.tickSecond(task -> cleanup.add(task.getId()));
        eq(0L, expires.getLifespan());
        eq(Optional.of(expires), board.get(1));
        eq(List.of(), cleanup);
        eq(Optional.empty(), board.claim(1, ALICE));
        board.tickSecond(task -> cleanup.add(task.getId()));
        eq(Optional.empty(), board.get(1));
        eq(List.of(1), cleanup);

        GolemTask stuck = block(2, 0);
        board.add(stuck);
        TaskBoard.Lease stale = board.claim(2, ALICE).orElseThrow();
        // API completion(false) increases the source lifespan but never renews the lease watchdog.
        board.complete(stale, false);
        board.tickSecond(task -> {});
        eq(true, stuck.isReserved());
        board.complete(stale, false);
        board.tickSecond(task -> {});
        eq(false, stuck.isReserved());
        eq(Optional.empty(), board.task(stale));
        TaskBoard.Lease current = board.claim(2, BOB).orElseThrow();
        eq(false, board.release(stale));
        eq(false, board.complete(stale, true));
        eq(Optional.of(stuck), board.task(current));
        board.suspend(current);
        board.tickSecond(task -> {
            cleanup.add(task.getId());
            eq(true, board.add(block(3, 0))); // seal may enqueue successor during suspension callback
        });
        eq(Optional.empty(), board.task(current));
        eq(List.of(1, 2), cleanup);
        eq(300L, board.get(3).orElseThrow().getLifespan());
        board.close();
        rejects(IllegalArgumentException.class, () -> new TaskBoard(0));

        TaskBoard bounded = new TaskBoard();
        for (int id = 0; id < TaskBoard.MAX_TASKS; id++) {
            if (!bounded.add(block(id, 0))) throw new AssertionError("Premature task rejection");
        }
        eq(TaskBoard.MAX_TASKS, bounded.snapshot().size());
        eq(false, bounded.add(block(TaskBoard.MAX_TASKS, 0)));
        eq(Optional.of(block(0, 0)), bounded.get(0));
        bounded.close();
    }

    private static void provisioning() throws InterruptedException {
        var now = new AtomicLong(500);
        var board = new ProvisionBoard<String>(now::get);
        var location = new SealArea.SealPos(ORIGIN, SealArea.Facing.UP);
        var target = new ProvisionRequest.BlockTarget(location);
        var request = board.request(target, "iron:name=ore", 16, 0).orElseThrow();
        eq(10_500L, request.timeout());
        eq(16, request.count());
        eq(target, request.target());
        eq(null, request.linkedTask());
        now.set(600);
        eq(true, request == board.request(target, "iron:name=ore", 16, 0).orElseThrow());
        eq(10_500L, request.timeout());
        // Exact identity includes count, components, side, UI and target kind.
        eq(false, request == board.request(target, "iron:name=ore", 15, 0).orElseThrow());
        eq(false, request == board.request(target, "iron", 16, 0).orElseThrow());
        eq(false, request == board.request(target, "iron:name=ore", 16, 1).orElseThrow());
        var otherSide = new ProvisionRequest.BlockTarget(new SealArea.SealPos(ORIGIN, SealArea.Facing.DOWN));
        eq(false, request == board.request(otherSide, "iron:name=ore", 16, 0).orElseThrow());
        var entity = board.request(new ProvisionRequest.EntityTarget(BOB), "iron:name=ore", 16, 0).orElseThrow();
        var seal = board.request(new ProvisionRequest.SealTarget(location, ALICE), "iron", 16, 0).orElseThrow();
        var replacement = board.request(new ProvisionRequest.SealTarget(location, BOB), "iron", 16, 0).orElseThrow();
        eq(false, seal == replacement);
        var pickup = ProvisionRules.pickup(500, location, request, (byte) -3);
        eq(0, pickup.getData());
        eq(GolemTask.Type.BLOCK, pickup.getType());
        eq(ORIGIN, pickup.getPos());
        eq((byte) 5, pickup.getPriority());
        eq(31_000L, pickup.getLifespan());
        var sealPickup = ProvisionRules.pickup(501, location, seal, (byte) -3);
        eq((byte) -3, sealPickup.getPriority());
        eq(10L, sealPickup.getLifespan());
        eq(true, board.link(request, null, pickup));
        eq(120_600L, request.timeout());
        eq(request, pickup.getLinkedProvision());
        eq(pickup, request.linkedTask());
        var delivery = ProvisionRules.delivery(502, pickup, request);
        eq(2, delivery.getData());
        eq(ORIGIN, delivery.getPos());
        eq(location, delivery.getSealPos());
        eq((byte) 5, delivery.getPriority());
        eq(31_000L, delivery.getLifespan());
        eq(false, board.link(request, null, delivery));
        now.set(700);
        eq(true, board.link(request, pickup, delivery));
        eq(null, pickup.getLinkedProvision());
        pickup.setSuspended(true);
        eq(false, board.unlink(request, pickup));
        eq(delivery, request.linkedTask());
        eq(120_700L, request.timeout());
        eq(false, board.link(request, delivery, delivery));
        var entityDelivery = ProvisionRules.delivery(503, pickup, entity);
        eq(1, entityDelivery.getData());
        eq(GolemTask.Type.ENTITY, entityDelivery.getType());
        eq(BOB, entityDelivery.getEntityId());
        rejects(IllegalArgumentException.class, () -> ProvisionRules.delivery(504, pickup, seal));
        eq(false, board.link(entity, null, delivery));
        var dead = block(505, 0); dead.setSuspended(true);
        eq(false, board.link(entity, null, dead));
        now.set(800);
        eq(true, board.unlink(request, delivery));
        eq(120_800L, request.timeout());
        eq(null, delivery.getLinkedProvision());
        eq(false, board.unlink(request, delivery));
        eq(true, board.link(request, null, delivery));
        // Unlinked requests are swept even before the initial deadline, matching BETA26.
        board.sweep();
        eq(List.of(request), board.snapshot());
        eq(true, entity.invalid());
        eq(false, board.link(entity, null, entityDelivery));
        now.set(request.timeout()); board.sweep();
        eq(1, board.snapshot().size()); // Strict timeout < now, equality is still live.
        now.incrementAndGet(); board.sweep();
        eq(List.of(), board.snapshot());
        eq(true, delivery.isSuspended());
        eq(null, delivery.getLinkedProvision());
        var fresh = board.request(target, "iron:name=ore", 16, 0).orElseThrow();
        eq(false, fresh.id() == request.id());
        eq(false, board.link(request, null, block(506, 0)));
        eq(true, board.invalidate(fresh));
        eq(false, board.invalidate(fresh));
        eq(false, board.link(fresh, null, block(507, 0)));
        board.sweep();
        var done = board.request(target, "gold", 2, 0).orElseThrow();
        var doneTask = block(508, 0); board.link(done, null, doneTask); doneTask.setCompletion(true);
        board.sweep(); eq(List.of(), board.snapshot());
        var suspended = board.request(target, "gold", 2, 0).orElseThrow();
        var suspendedTask = block(509, 0); board.link(suspended, null, suspendedTask); suspendedTask.setSuspended(true);
        board.sweep(); eq(List.of(), board.snapshot());
        var closed = board.request(target, "gold", 2, 0).orElseThrow();
        var closeTask = block(510, 0); board.link(closed, null, closeTask);
        var failure = new AtomicReference<Throwable>();
        var foreignThread = new Thread(() -> {
            try { board.snapshot(); } catch (Throwable ex) { failure.set(ex); }
        });
        foreignThread.start(); foreignThread.join();
        eq(true, failure.get() instanceof IllegalStateException);
        board.close(); board.close();
        eq(true, board.isClosed()); eq(List.of(), board.snapshot());
        eq(true, closeTask.isSuspended()); eq(true, closed.invalid());
        eq(null, closed.linkedTask());
        eq(Optional.empty(), board.request(target, "gold", 2, 0));
        rejects(IllegalArgumentException.class, () -> board.request(target, "gold", 0, 0));
        try (var bounded = new ProvisionBoard<String>(now::get)) {
            for (int i=0; i<ProvisionBoard.MAX_REQUESTS; i++)
                eq(true, bounded.request(target, "item"+i, 1, 0).isPresent());
            eq(Optional.empty(), bounded.request(target, "overflow", 1, 0));
            eq(ProvisionBoard.MAX_REQUESTS, bounded.snapshot().size());
            eq(true, bounded.request(target, "item0", 1, 0).isPresent());
        }
        eq(9, ProvisionRules.FILTER_SLOTS); eq(20, ProvisionRules.INTERVAL_TICKS);
        eq(100, ProvisionRules.SWEEP_TICKS);
        eq(64, ProvisionRules.stockDemand(200, 10, 64, 128));
        eq(2, ProvisionRules.stockDemand(20, 18, 64, 10));
        eq(1, ProvisionRules.stockDemand(20, 18, 64, 1));
        eq(0, ProvisionRules.stockDemand(20, 21, 64, 64));
        eq(0, ProvisionRules.stockDemand(20, Long.MAX_VALUE, 64, 64));
        eq(0, ProvisionRules.stockDemand(20, 0, 64, 0));
        eq(0, ProvisionRules.stockDemand(0, 0, 64, 64));
        eq(16, ProvisionRules.stockDemand(100, 0, 16, 64));
        eq(true, ProvisionRules.inProviderRange(4095.99));
        eq(false, ProvisionRules.inProviderRange(4096));
        eq(false, ProvisionRules.inProviderRange(Double.NaN));
        eq(false, ProvisionRules.inProviderRange(-1));
        eq(6, ProvisionRules.pickupAmount(16, 64, 7, false, true));
        eq(0, ProvisionRules.pickupAmount(16, 64, 1, false, true));
        eq(1, ProvisionRules.pickupAmount(16, 64, 7, true, true));
        eq(16, ProvisionRules.pickupAmount(16, 64, Long.MAX_VALUE, false, true));
        eq(7, ProvisionRules.pickupAmount(16, 64, 7, false, false));
        eq(2, ProvisionRules.pickupAmount(16, 2, 7, false, false));
        var registry=new AspectRegistry(); var knowledge=new PlayerKnowledgeData();
        var advanced=dev.ehtid.thaumcraft.port.core.golem.GolemSealRecipes.Conversion.EMPTY_ADVANCED;
        var aspects=new AspectListData().add(registry.SENSES,10).add(registry.MIND,10);
        var emptyCatalyst=dev.ehtid.thaumcraft.port.core.golem.GolemSealRecipes.Catalyst.EMPTY;
        knowledge.addResearch("SEALEMPTY");
        eq(null,dev.ehtid.thaumcraft.port.core.golem.GolemSealRecipes.conversion(emptyCatalyst,knowledge,aspects,registry));
        knowledge.addResearch("MINDBIOTHAUMIC");
        eq(advanced,dev.ehtid.thaumcraft.port.core.golem.GolemSealRecipes.conversion(emptyCatalyst,knowledge,aspects,registry));
        eq(List.of("sensus","cognitio"),advanced.aspects);
        eq(0L,dev.ehtid.thaumcraft.port.core.golem.GolemSealRecipes.amount(
                dev.ehtid.thaumcraft.port.core.golem.GolemSealRecipes.afterCraft(aspects,advanced,registry),"cognitio"));
        eq(10L,dev.ehtid.thaumcraft.port.core.golem.GolemSealRecipes.amount(aspects,"cognitio"));
        for(var recipe:List.of(dev.ehtid.thaumcraft.port.core.golem.GolemSealRecipes.Conversion.PROVIDE,
                dev.ehtid.thaumcraft.port.core.golem.GolemSealRecipes.Conversion.STOCK)) {
            var exact=new AspectListData();
            for(var tag:recipe.aspects) exact.add(registry.get(tag),10);
            eq(null,dev.ehtid.thaumcraft.port.core.golem.GolemSealRecipes.conversion(recipe.catalyst,knowledge,exact,registry));
            knowledge.addResearch(recipe.research);
            eq(recipe,dev.ehtid.thaumcraft.port.core.golem.GolemSealRecipes.conversion(recipe.catalyst,knowledge,exact,registry));
            eq(null,dev.ehtid.thaumcraft.port.core.golem.GolemSealRecipes.conversion(
                    dev.ehtid.thaumcraft.port.core.golem.GolemSealRecipes.Catalyst.BLANK,knowledge,exact,registry));
            var after=dev.ehtid.thaumcraft.port.core.golem.GolemSealRecipes.afterCraft(exact,recipe,registry);
            for(var tag:recipe.aspects) eq(0L,dev.ehtid.thaumcraft.port.core.golem.GolemSealRecipes.amount(after,tag));
            exact.remove(registry.get(recipe.aspects.getLast()),1);
            eq(null,dev.ehtid.thaumcraft.port.core.golem.GolemSealRecipes.conversion(recipe.catalyst,knowledge,exact,registry));
            rejects(IllegalArgumentException.class,()->dev.ehtid.thaumcraft.port.core.golem.GolemSealRecipes.afterCraft(exact,recipe,registry));
        }
        var mixedBlock=block(600,10); var mixedEntity=GolemTask.entity(601,null,BOB); mixedEntity.setPriority((byte)5);
        eq(List.of(mixedEntity,mixedBlock),TaskSelection.sorted(List.of(mixedBlock,mixedEntity),null,permissive(ALICE)));
    }

    private static void farmSourceRules() {
        var crops=dev.ehtid.thaumcraft.port.core.golem.GolemFarmRules.Crop.values();
        eq(5,crops.length);
        eq(5,dev.ehtid.thaumcraft.port.core.golem.GolemFarmRules.SCAN_TICKS);
        eq(1,dev.ehtid.thaumcraft.port.core.golem.GolemFarmRules.SEED_COST);
        for(var crop:crops) {
            for(int age=-1;age<=8;age++) eq(age==crop.maximumAge,dev.ehtid.thaumcraft.port.core.golem.GolemFarmRules.mature(crop,age));
            eq(crop!=dev.ehtid.thaumcraft.port.core.golem.GolemFarmRules.Crop.NETHER_WART,crop.farmland);
        }
        eq(false,dev.ehtid.thaumcraft.port.core.golem.GolemFarmRules.mature(null,7));
        var smart=dev.ehtid.thaumcraft.port.core.golem.GolemTrait.SMART;
        var deft=dev.ehtid.thaumcraft.port.core.golem.GolemTrait.DEFT;
        eq(false,dev.ehtid.thaumcraft.port.core.golem.GolemFarmRules.eligible(Set.of()));
        eq(false,dev.ehtid.thaumcraft.port.core.golem.GolemFarmRules.eligible(Set.of(smart)));
        eq(false,dev.ehtid.thaumcraft.port.core.golem.GolemFarmRules.eligible(Set.of(deft)));
        eq(true,dev.ehtid.thaumcraft.port.core.golem.GolemFarmRules.eligible(Set.of(smart,deft)));
    }

    public static void main(String[] args) throws InterruptedException {
        for (boolean sameItem : List.of(false, true)) for (boolean sameDamage : List.of(false, true))
            for (boolean sameComponents : List.of(false, true)) for (boolean matchNbt : List.of(false, true))
                eq(sameItem && sameDamage && (sameComponents || !matchNbt),
                        dev.ehtid.thaumcraft.port.core.golem.SealFilterRules.matches(sameItem, sameDamage, sameComponents, matchNbt));
        for (boolean blacklist : List.of(false, true)) {
            for (boolean present : List.of(false, true)) {
                for (boolean matches : List.of(false, true)) {
                    eq(blacklist ? !present || !matches : present && matches,
                            dev.ehtid.thaumcraft.port.core.golem.SealFilterRules.accepts(blacklist, present, matches));
                }
            }
        }
        eq(7, dev.ehtid.thaumcraft.port.core.golem.SealFilterRules.transferable(10, false, 12, 5));
        eq(0, dev.ehtid.thaumcraft.port.core.golem.SealFilterRules.transferable(10, false, 12, 12));
        eq(0, dev.ehtid.thaumcraft.port.core.golem.SealFilterRules.transferable(10, false, 12, Long.MAX_VALUE));
        eq(10, dev.ehtid.thaumcraft.port.core.golem.SealFilterRules.transferable(10, false, 0, 99));
        eq(10, dev.ehtid.thaumcraft.port.core.golem.SealFilterRules.transferable(10, true, 1, 99));
        eq(10, dev.ehtid.thaumcraft.port.core.golem.SealFilterRules.transferable(10, false, Integer.MAX_VALUE, 0));
        eq(0, dev.ehtid.thaumcraft.port.core.golem.SealFilterRules.transferable(0, false, 12, 0));
        var sealGrid = new ArrayList<>(java.util.Collections.nCopies(9,
                dev.ehtid.thaumcraft.port.core.golem.GolemSealRecipes.Ingredient.EMPTY));
        var ingredients = dev.ehtid.thaumcraft.port.core.golem.GolemSealRecipes.Ingredient.values();
        for (int i=1;i<=4;i++) sealGrid.set(i*2-1, ingredients[i]);
        eq(true, dev.ehtid.thaumcraft.port.core.golem.GolemSealRecipes.matchesBlank(sealGrid));
        sealGrid.set(0, ingredients[1]);
        eq(false, dev.ehtid.thaumcraft.port.core.golem.GolemSealRecipes.matchesBlank(sealGrid));
        var registry = new AspectRegistry();
        var stored = new AspectListData().add(registry.DESIRE, 10).add(registry.AVERSION, 10);
        var knowledge = new PlayerKnowledgeData();
        eq(null, dev.ehtid.thaumcraft.port.core.golem.GolemSealRecipes.conversion(knowledge, stored, registry));
        knowledge.addResearch("SEALSTORE");
        eq(dev.ehtid.thaumcraft.port.core.golem.GolemSealRecipes.Conversion.FILL,
                dev.ehtid.thaumcraft.port.core.golem.GolemSealRecipes.conversion(knowledge, stored, registry));
        knowledge.addResearch("SEALCOLLECT");
        eq(dev.ehtid.thaumcraft.port.core.golem.GolemSealRecipes.Conversion.PICKUP,
                dev.ehtid.thaumcraft.port.core.golem.GolemSealRecipes.conversion(knowledge, stored, registry));
        var afterSeal = dev.ehtid.thaumcraft.port.core.golem.GolemSealRecipes.afterCraft(stored,
                dev.ehtid.thaumcraft.port.core.golem.GolemSealRecipes.Conversion.PICKUP, registry);
        eq(0, afterSeal.getAmount(registry.DESIRE)); eq(10, afterSeal.getAmount(registry.AVERSION));
        eq(10, stored.getAmount(registry.DESIRE));
        var anotherRegistry = new AspectRegistry();
        eq(dev.ehtid.thaumcraft.port.core.golem.GolemSealRecipes.Conversion.PICKUP,
                dev.ehtid.thaumcraft.port.core.golem.GolemSealRecipes.conversion(knowledge, stored, anotherRegistry));
        var mixed = stored.copy().add(anotherRegistry.DESIRE, 5);
        var paid = dev.ehtid.thaumcraft.port.core.golem.GolemSealRecipes.afterCraft(mixed,
                dev.ehtid.thaumcraft.port.core.golem.GolemSealRecipes.Conversion.PICKUP, anotherRegistry);
        eq(5L, dev.ehtid.thaumcraft.port.core.golem.GolemSealRecipes.amount(paid, "desiderium"));
        eq(15L, dev.ehtid.thaumcraft.port.core.golem.GolemSealRecipes.amount(mixed, "desiderium"));
        eq(false, dev.ehtid.thaumcraft.port.core.golem.GolemBellRules.following((byte)1));
        eq(true, dev.ehtid.thaumcraft.port.core.golem.GolemBellRules.following((byte)2));
        eq((byte)0xa7, dev.ehtid.thaumcraft.port.core.golem.GolemBellRules.following((byte)0xa5,true));
        eq((byte)0xa5, dev.ehtid.thaumcraft.port.core.golem.GolemBellRules.following((byte)0xa7,false));
        eq((byte)0xff, dev.ehtid.thaumcraft.port.core.golem.GolemBellRules.following((byte)0xff,true));
        eq((byte)0xfd, dev.ehtid.thaumcraft.port.core.golem.GolemBellRules.following((byte)0xff,false));
        // Original dyeBlack..dyeWhite mapping, and its effect on real seal eligibility.
        for (int legacy = 0; legacy < 16; legacy++) {
            byte color = GolemColorRules.fromLegacyDyeIndex(legacy);
            eq(color, GolemColorRules.fromModernDyeId(15 - legacy));
            eq(true, TaskSelection.colorMatches(color, color));
            eq(true, TaskSelection.colorMatches(color, (byte) 0));
            eq(true, TaskSelection.colorMatches((byte) 0, color));
            eq(false, TaskSelection.colorMatches(color, (byte) (color == 16 ? 1 : color + 1)));
        }
        eq((byte) 16, GolemColorRules.fromLegacyDyeIndex(0)); // dyeBlack
        eq((byte) 15, GolemColorRules.fromLegacyDyeIndex(1)); // dyeRed
        eq((byte) 1, GolemColorRules.fromLegacyDyeIndex(15)); // dyeWhite
        rejects(IllegalArgumentException.class, () -> GolemColorRules.fromLegacyDyeIndex(-1));
        rejects(IllegalArgumentException.class, () -> GolemColorRules.fromLegacyDyeIndex(16));
        rejects(IllegalArgumentException.class, () -> GolemColorRules.fromModernDyeId(-1));
        rejects(IllegalArgumentException.class, () -> GolemColorRules.fromModernDyeId(16));
        var areaSize = new SealArea.BlockPos(2, 3, 4);
        eq(Optional.of(new SealArea.BlockPos(2, 2, 4)), SealAreaControls.adjust(areaSize, 90));
        eq(Optional.of(new SealArea.BlockPos(2, 4, 4)), SealAreaControls.adjust(areaSize, 91));
        eq(Optional.of(new SealArea.BlockPos(1, 3, 4)), SealAreaControls.adjust(areaSize, 92));
        eq(Optional.of(new SealArea.BlockPos(3, 3, 4)), SealAreaControls.adjust(areaSize, 93));
        eq(Optional.of(new SealArea.BlockPos(2, 3, 3)), SealAreaControls.adjust(areaSize, 94));
        eq(Optional.of(new SealArea.BlockPos(2, 3, 5)), SealAreaControls.adjust(areaSize, 95));
        for (int axis = 0; axis < 3; axis++) {
            eq(Optional.empty(), SealAreaControls.adjust(new SealArea.BlockPos(1, 1, 1), 90 + axis * 2));
            eq(Optional.empty(), SealAreaControls.adjust(new SealArea.BlockPos(8, 8, 8), 91 + axis * 2));
        }
        eq(Optional.empty(), SealAreaControls.adjust(areaSize, 96));
        eq(Optional.empty(), SealAreaControls.adjust(new SealArea.BlockPos(Integer.MAX_VALUE, 1, 1), 93));
        eq(7, dev.ehtid.thaumcraft.port.core.golem.SealEmptyRules.requested(10, 7));
        eq(10, dev.ehtid.thaumcraft.port.core.golem.SealEmptyRules.requested(10, 128));
        eq(0, dev.ehtid.thaumcraft.port.core.golem.SealEmptyRules.requested(10, 0));
        eq(0, dev.ehtid.thaumcraft.port.core.golem.SealEmptyRules.requested(-1, 64));
        eq(20, dev.ehtid.thaumcraft.port.core.golem.SealEmptyRules.INTERVAL_TICKS);
        eq((short) 5, dev.ehtid.thaumcraft.port.core.golem.SealEmptyRules.TASK_LIFESPAN_SECONDS);
        var emptyKnowledge = new PlayerKnowledgeData();
        var vacuos = new AspectListData().add(registry.VOID, 10);
        eq(null, dev.ehtid.thaumcraft.port.core.golem.GolemSealRecipes.conversion(emptyKnowledge, vacuos, registry));
        emptyKnowledge.addResearch("SEALEMPTY");
        eq(dev.ehtid.thaumcraft.port.core.golem.GolemSealRecipes.Conversion.EMPTY,
                dev.ehtid.thaumcraft.port.core.golem.GolemSealRecipes.conversion(emptyKnowledge, vacuos, registry));
        eq(0L, dev.ehtid.thaumcraft.port.core.golem.GolemSealRecipes.amount(
                dev.ehtid.thaumcraft.port.core.golem.GolemSealRecipes.afterCraft(vacuos,
                        dev.ehtid.thaumcraft.port.core.golem.GolemSealRecipes.Conversion.EMPTY, registry), "vacuos"));
        eq(10L, dev.ehtid.thaumcraft.port.core.golem.GolemSealRecipes.amount(vacuos, "vacuos"));
        eq(6, dev.ehtid.thaumcraft.port.core.golem.SealEmptyRules.requested(7, 64, 7, true));
        eq(0, dev.ehtid.thaumcraft.port.core.golem.SealEmptyRules.requested(7, 64, 1, true));
        eq(1, dev.ehtid.thaumcraft.port.core.golem.SealEmptyRules.requested(7, 64, 1, false));
        eq(64, dev.ehtid.thaumcraft.port.core.golem.SealEmptyRules.requested(64, 64, 70, true));
        eq(5, dev.ehtid.thaumcraft.port.core.golem.SealEmptyRules.requested(64, 64, 6, true));
        eq(2, dev.ehtid.thaumcraft.port.core.golem.SealEmptyRules.requested(2, 64, 7, true));
        eq(0, dev.ehtid.thaumcraft.port.core.golem.SealEmptyRules.requested(7, 0, 7, true));
        eq(0, dev.ehtid.thaumcraft.port.core.golem.SealEmptyRules.requested(7, 64, 0, false));
        eq(0, dev.ehtid.thaumcraft.port.core.golem.SealEmptyRules.requested(7, 64, -1, true));
        eq(64, dev.ehtid.thaumcraft.port.core.golem.SealEmptyRules.requested(64, 64, Long.MAX_VALUE, true));
        farmSourceRules();
        taskSourceRules();
        allFaceAreas();
        sortingAndSealPolicy();
        exclusiveLeasesAndLifecycle();
        expiryAndWatchdog();
        provisioning();
        System.out.println("BETA26 golem task rules, six-face seal areas and exclusive server leases: OK ("
                + assertions + " assertions)");
    }
}
