package dev.ehtid.thaumcraft.golem.test;

import com.mojang.authlib.GameProfile;
import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.golem.inventory.GolemContainers;
import dev.ehtid.thaumcraft.golem.menu.GolemSealMenu;
import dev.ehtid.thaumcraft.golem.seal.GolemSeals;
import dev.ehtid.thaumcraft.golem.seal.SealFilter;
import dev.ehtid.thaumcraft.port.core.golem.GolemDesign;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.UUID;

@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class GolemFilterGameTests {
    private GolemFilterGameTests() {}

    @GameTest(template = "golem_test", batch = "filter_pickup", timeoutTicks = 60)
    public static void pickupFilterRejectsComponentsAndCancelsOldLease(GameTestHelper h) {
        floor(h);
        var level = h.getLevel();
        var key = new GolemSeals.SealKey(h.absolutePos(new BlockPos(6, 0, 6)), Direction.UP);
        var seals = GolemSeals.get(level);
        UUID owner = UUID.randomUUID();
        seals.place(key.pos(), key.face(), owner);
        var worker = worker(h);
        ItemStack match = named(7);
        var policy = new SealFilter(match, false, 1);
        seals.configure(seals.get(key), owner, policy);
        match.setCount(35);
        h.assertTrue(policy.item().getCount() == 1, "Filter owns a normalized copy");
        policy.item().setCount(0);
        h.assertTrue(policy.accepts(named(1)) && !policy.accepts(new ItemStack(Items.DIAMOND)), "Default component-sensitive matching");
        var accepted = drop(h, named(7));
        var rejected = drop(h, new ItemStack(Items.DIAMOND, 5));
        h.runAfterDelay(10, () -> {
            var old = seals.claim(worker, ignored -> true).orElseThrow();
            h.assertTrue(old.targetId().equals(accepted.getUUID()), "Only matching ground stack becomes eligible");
            var forged = new GolemSeals.Assignment(old.lease(), rejected.getUUID(), old.seal());
            h.assertTrue(seals.target(level, worker, forged) == null && !seals.pickup(level, worker, forged),
                    "Valid lease cannot redirect to a rejected target");
            accepted.setItem(new ItemStack(Items.DIAMOND, 7));
            h.assertTrue(seals.target(level, worker, old) == null, "Changed ground components rechecked before pickup");
            accepted.setItem(named(7));
            h.assertTrue(seals.configure(seals.get(key), owner, new SealFilter(named(1), true, 1)), "Change to blacklist");
            h.assertTrue(seals.target(level, worker, old) == null && !seals.pickup(level, worker, old), "Edited policy invalidates reserved work");
            h.assertTrue(accepted.getItem().getCount() == 7 && worker.getMainHandItem().isEmpty(), "Cancellation transfers nothing");
        });
        h.runAfterDelay(20, () -> {
            var plain = seals.claim(worker, ignored -> true).orElseThrow();
            h.assertTrue(plain.targetId().equals(rejected.getUUID()), "Blacklist rejects its named sample and permits the plain item");
            h.assertTrue(seals.pickup(level, worker, plain), "Real filtered pickup");
            h.assertTrue(worker.getMainHandItem().getCount() == 5 && !rejected.isAlive(), "Pickup has no stack-size limiter");
            seals.remove(key.pos(), key.face(), owner);
            h.succeed();
        });
    }

    @GameTest(template = "golem_test", batch = "filter_stock", timeoutTicks = 60)
    public static void reservedWorkersRecountTargetStockAtCompletion(GameTestHelper h) {
        floor(h);
        var level = h.getLevel();
        BlockPos pos = h.absolutePos(new BlockPos(6, 1, 6));
        h.setBlock(new BlockPos(6, 1, 6), Blocks.BARREL);
        var key = new GolemSeals.SealKey(pos, Direction.WEST);
        var seals = GolemSeals.get(level);
        UUID owner = UUID.randomUUID();
        seals.place(pos, Direction.WEST, owner, GolemSeals.Type.FILL);
        seals.configure(seals.get(key), owner, new SealFilter(named(1), false, 12));
        var handler = GolemContainers.handler(level, key);
        h.assertTrue(handler != null, "Real barrel capability");
        handler.insertItem(0, named(5), false);
        handler.insertItem(1, new ItemStack(Items.DIAMOND, 30), false);
        var first = worker(h);
        var second = worker(h);
        first.holdItem(named(5));
        second.holdItem(named(5));
        h.runAfterDelay(25, () -> {
            var a = seals.claimFill(first, ignored -> true).orElseThrow();
            var b = seals.claimFill(second, ignored -> true).orElseThrow();
            h.assertTrue(seals.fill(level, first, a) && seals.fill(level, second, b), "Both reserved tasks finish using current stock");
            h.assertTrue(handler.getStackInSlot(0).getCount() == 12 && handler.getStackInSlot(1).getCount() == 30,
                    "Target cap counts only equal components");
            h.assertTrue(first.getMainHandItem().isEmpty() && second.getMainHandItem().getCount() == 3,
                    "Fresh budget retains exact unpaid remainder; no loss or overfill");
            h.assertTrue(seals.claimFill(second, ignored -> true).isEmpty(), "Full stock cap produces no eligible transfer");
            h.assertTrue(!seals.fill(level, first, a), "Completed lease cannot replay");
            seals.remove(pos, Direction.WEST, owner);
            h.succeed();
        });
    }

    @GameTest(template = "golem_test", batch = "filter_remainder", timeoutTicks = 40)
    public static void filteredOffhandAndActualRemainderPreserveAllCargo(GameTestHelper h) {
        floor(h);
        var worker = worker(h);
        worker.initialize(GolemDesign.fromIds(0, 0, 0, 0, 3, 0), UUID.randomUUID(), worker.home(), 0);
        worker.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE, 11));
        worker.setItemSlot(EquipmentSlot.OFFHAND, named(10));
        var handler = new ItemStackHandler(1) {
            @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                if (simulate) return super.insertItem(slot, stack, true);
                int n = Math.min(stack.getCount(), Math.max(0, 2 - getStackInSlot(0).getCount()));
                var rest = stack.copy();
                super.insertItem(slot, rest.split(n), false);
                return rest;
            }
        };
        h.assertTrue(GolemContainers.insertCarried(worker, handler, new SealFilter(named(1), false, 6)) == 2,
                "Actual acceptance overrides simulated/filter allowance");
        h.assertTrue(worker.getMainHandItem().is(Items.STONE) && worker.getMainHandItem().getCount() == 11
                && ItemStack.matches(worker.getOffhandItem(), named(8)) && ItemStack.matches(handler.getStackInSlot(0), named(2)),
                "Select first matching hand, retain withheld and actual remainder with components");
        h.assertTrue(GolemContainers.insertCarried(worker, handler, new SealFilter(ItemStack.EMPTY, false, 0)) == 0,
                "Empty whitelist accepts nothing");
        var unlimited = new ItemStackHandler(1);
        h.assertTrue(GolemContainers.insertCarried(worker, unlimited, new SealFilter(named(1), false, 0)) == 8,
                "Zero whitelist limit is unlimited");
        h.assertTrue(worker.getOffhandItem().isEmpty() && unlimited.getStackInSlot(0).getCount() == 8, "All paid items accounted for");
        h.succeed();
    }

    @GameTest(template = "golem_test", batch = "filter_menu", timeoutTicks = 40)
    public static void menuGhostCannotMintItemsAndRejectsUnauthorizedOrStaleEdits(GameTestHelper h) {
        floor(h);
        var level = h.getLevel();
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "GolemFilterOwner"));
        var stranger = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "GolemFilterOther"));
        var key = new GolemSeals.SealKey(h.absolutePos(new BlockPos(6, 0, 6)), Direction.UP);
        player.moveTo(Vec3.atBottomCenterOf(key.pos().above()));
        stranger.moveTo(player.position());
        var seals = GolemSeals.get(level);
        seals.place(key.pos(), key.face(), player.getUUID(), GolemSeals.Type.FILL);
        var expected = seals.get(key);
        var menu = new GolemSealMenu(3, player.getInventory(), level, expected);
        h.assertTrue(menu.stillValid(player) && !menu.stillValid(stranger), "Only nearby seal owner can configure");
        menu.setCarried(named(19));
        menu.clicked(0, 0, ClickType.PICKUP, player);
        h.assertTrue(menu.getCarried().getCount() == 19 && ItemStack.matches(seals.get(key).filter().item(), named(1)), "Ghost copies rather than consumes cursor");
        for (var click : ClickType.values()) if (click != ClickType.PICKUP) menu.clicked(0, 0, click, player);
        h.assertTrue(menu.getCarried().getCount() == 19 && menu.getSlot(0).getItem().getCount() == 1, "Throw/swap/clone/drag/quickmove cannot withdraw ghost");
        h.assertTrue(!menu.clickMenuButton(stranger, 0) && !menu.clickMenuButton(player, 999), "Reject wrong player and arbitrary action IDs");
        h.assertTrue(menu.clickMenuButton(player, 0) && menu.clickMenuButton(player, 4), "Whitelist +10 via vanilla menu action");
        h.assertTrue(!seals.get(key).filter().blacklist() && seals.get(key).filter().limit() == 10, "Server stores requested policy");
        seals.configure(expected, player.getUUID(), new SealFilter(named(1), false, 70000));
        menu.broadcastChanges();
        h.assertTrue(menu.limit() == 70000, "Count survives split 16-bit data slots");
        seals.configure(expected, player.getUUID(), new SealFilter(named(1), false, Integer.MAX_VALUE));
        menu.broadcastChanges();
        h.assertTrue(menu.clickMenuButton(player, 4) && menu.limit() == Integer.MAX_VALUE, "Increment saturates without overflow");
        player.moveTo(player.position().add(20, 0, 0));
        h.assertTrue(!menu.clickMenuButton(player, 0), "Distant edits fail");
        player.moveTo(Vec3.atBottomCenterOf(key.pos().above()));
        player.getInventory().setItem(9, new ItemStack(Items.IRON_INGOT, 23));
        menu.quickMoveStack(player, 1);
        h.assertTrue(player.getInventory().getItem(9).getCount() == 23 && seals.get(key).filter().item().is(Items.IRON_INGOT), "Shift copy preserves source inventory");
        seals.remove(key.pos(), key.face(), player.getUUID());
        seals.place(key.pos(), key.face(), player.getUUID(), GolemSeals.Type.FILL);
        h.assertTrue(!menu.stillValid(player) && !menu.clickMenuButton(player, 0), "Delete/recreate invalidates old menu token");
        h.assertTrue(seals.get(key).filter().blacklist() && seals.get(key).filter().item().isEmpty(), "Replacement configuration unchanged");
        seals.remove(key.pos(), key.face(), player.getUUID());
        menu.setCarried(ItemStack.EMPTY);
        player.getInventory().clearContent();
        h.succeed();
    }

    @GameTest(template = "golem_test", batch = "filter_save", timeoutTicks = 40)
    public static void filterNbtPreservesGhostComponentsAndLoadsOldRows(GameTestHelper h) {
        floor(h);
        var level = h.getLevel();
        var seals = GolemSeals.get(level);
        var key = new GolemSeals.SealKey(h.absolutePos(new BlockPos(6, 0, 6)), Direction.UP);
        UUID owner = UUID.randomUUID();
        seals.place(key.pos(), key.face(), owner);
        seals.configure(seals.get(key), owner, new SealFilter(named(64), false, 70000));
        CompoundTag tag = seals.save(new CompoundTag(), level.registryAccess());
        var restored = GolemSeals.load(tag, level.registryAccess());
        var policy = restored.get(key).filter();
        h.assertTrue(!policy.blacklist() && policy.limit() == 70000 && ItemStack.matches(policy.item(), named(1)),
                "Ghost normalized; components, mode and full integer limit round trip");
        h.assertTrue(restored.pendingTasks() == 0 && restored.reservedTasks() == 0, "No persisted work replay");
        var rows = tag.getList("seals", 10);
        for (int i = 0; i < rows.size(); i++) {
            var row = rows.getCompound(i);
            row.remove("Items"); row.remove("Sizes"); row.remove("bl");
        }
        var old = GolemSeals.load(tag, level.registryAccess()).get(key).filter();
        h.assertTrue(old.blacklist() && old.item().isEmpty() && old.limit() == 0, "M10 old rows retain accept-all default");
        seals.remove(key.pos(), key.face(), owner);
        h.succeed();
    }

    @GameTest(template = "golem_test", batch = "filter_ground", timeoutTicks = 40)
    public static void noHandlerRetainsConfirmedBeta26GroundCountDefect(GameTestHelper h) {
        floor(h);
        var worker = worker(h);
        worker.holdItem(named(9));
        var key = new GolemSeals.SealKey(h.absolutePos(new BlockPos(6, 0, 6)), Direction.UP);
        var filter = new SealFilter(named(1), false, 3);
        drop(h, named(10));
        h.assertTrue(GolemContainers.deposit(h.getLevel(), worker, key, filter) == 3, "Source defective world count does not include existing nonempty items");
        h.assertTrue(GolemContainers.deposit(h.getLevel(), worker, key, filter) == 3 && worker.getMainHandItem().getCount() == 3,
                "Confirmed source fallback caps each drop, not ground stock");
        int ground = h.getLevel().getEntitiesOfClass(ItemEntity.class, h.getBounds()).stream().mapToInt(e -> e.getItem().getCount()).sum();
        h.assertTrue(ground + worker.getMainHandItem().getCount() == 19, "Source quirk still conserves every item");
        h.succeed();
    }

    @GameTest(template = "golem_test", batch = "filter_ai_cycle", timeoutTicks = 260)
    public static void navigatingWorkerHonorsBothFiltersAndStopsAtStockCap(GameTestHelper h) {
        floor(h);
        var level = h.getLevel();
        var seals = GolemSeals.get(level);
        var pickup = new GolemSeals.SealKey(h.absolutePos(new BlockPos(5, 0, 6)), Direction.UP);
        var fill = new GolemSeals.SealKey(h.absolutePos(new BlockPos(10, 1, 6)), Direction.WEST);
        h.setBlock(new BlockPos(10, 1, 6), Blocks.BARREL);
        UUID owner = UUID.randomUUID();
        seals.place(pickup.pos(), pickup.face(), owner);
        seals.place(fill.pos(), fill.face(), owner, GolemSeals.Type.FILL);
        seals.configure(seals.get(pickup), owner, new SealFilter(named(1), false, 1));
        seals.configure(seals.get(fill), owner, new SealFilter(named(1), false, 12));
        var worker = worker(h);
        worker.moveTo(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(2, 1, 6))));
        worker.setNoAi(false);
        var source = drop(h, named(17));
        // A distinct named sample cannot merge with ordinary diamond fixtures in neighboring tests.
        ItemStack excluded = new ItemStack(Items.DIAMOND, 5);
        excluded.set(DataComponents.CUSTOM_NAME, Component.literal("Excluded AI sample"));
        var ignored = drop(h, excluded);
        var handler = GolemContainers.handler(level, fill);
        h.assertTrue(handler != null, "Destination fixture");
        h.onEachTick(() -> {
            int count = worker.getMainHandItem().getCount() + (source.isAlive() ? source.getItem().getCount() : 0);
            for (int i = 0; i < handler.getSlots(); i++) count += handler.getStackInSlot(i).getCount();
            h.assertTrue(count == 17 && ignored.isAlive() && ignored.getItem().getCount() == 5,
                    "Cargo conservation: accounted=" + count + ", held=" + worker.getMainHandItem()
                            + ", source=" + source.getItem() + "/alive=" + source.isAlive()
                            + ", rejected=" + ignored.getItem() + "/alive=" + ignored.isAlive()
                            + ", ground=" + level.getEntitiesOfClass(ItemEntity.class, h.getBounds()).stream()
                                    .map(e -> e.getItem().toString()).toList());
        });
        // Sequences finish after tick callbacks. Direct succeed() inside runAfterDelay
        // discards ItemEntities before the last onEachTick callback in the same tick.
        h.startSequence().thenIdle(200).thenExecute(() -> {
            int stock = 0;
            for (int i = 0; i < handler.getSlots(); i++) stock += handler.getStackInSlot(i).getCount();
            h.assertTrue(stock == 12 && ItemStack.matches(worker.getMainHandItem(), named(5)) && !source.isAlive(),
                    "Actual AI walks through filtered pickup/fill and retains exact amount above stock cap");
            h.assertTrue(!worker.hasWorkTask(), "Reached stock cap releases work instead of looping on an impossible task");
            seals.remove(pickup.pos(), pickup.face(), owner);
            seals.remove(fill.pos(), fill.face(), owner);
        }).thenSucceed();
    }

    private static ItemStack named(int count) {
        ItemStack stack = new ItemStack(Items.DIAMOND, count);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Filter sample"));
        return stack;
    }
    private static ItemEntity drop(GameTestHelper h, ItemStack stack) {
        Vec3 pos = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(5, 1, 6)));
        var entity = new ItemEntity(h.getLevel(), pos.x, pos.y, pos.z, stack);
        entity.setNoPickUpDelay(); entity.setDeltaMovement(Vec3.ZERO); entity.setOnGround(true);
        h.assertTrue(h.getLevel().addFreshEntity(entity), "Actual source accepted");
        return entity;
    }
    private static ThaumcraftGolem worker(GameTestHelper h) {
        var entity = GolemModule.GOLEM.get().create(h.getLevel());
        h.assertTrue(entity != null, "Registered worker");
        var pos = h.absolutePos(new BlockPos(5, 1, 6));
        entity.initialize(GolemDesign.defaultWood(), UUID.randomUUID(), pos, 0);
        entity.moveTo(Vec3.atBottomCenterOf(pos)); entity.setNoAi(true);
        h.assertTrue(h.getLevel().addFreshEntity(entity), "Actual worker accepted");
        return entity;
    }
    private static void floor(GameTestHelper h) {
        for (int x = 0; x < 12; x++) for (int z = 0; z < 12; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
    }
}
