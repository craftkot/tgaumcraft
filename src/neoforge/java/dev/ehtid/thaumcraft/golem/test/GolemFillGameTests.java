package dev.ehtid.thaumcraft.golem.test;

import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.golem.inventory.GolemContainers;
import dev.ehtid.thaumcraft.golem.seal.GolemSeals;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class GolemFillGameTests {
    private GolemFillGameTests() {}

    @GameTest(template = "golem_test", batch = "fill_cycle", timeoutTicks = 300)
    public static void pickupToGolemToChestConservesNamedCargo(GameTestHelper h) {
        floor(h);
        var level = h.getLevel();
        BlockPos pickup = h.absolutePos(new BlockPos(6, 0, 6));
        BlockPos chest = h.absolutePos(new BlockPos(10, 1, 6));
        h.setBlock(new BlockPos(10, 1, 6), Blocks.CHEST);
        var seals = GolemSeals.get(level);
        UUID owner = UUID.randomUUID();
        h.assertTrue(seals.place(pickup, Direction.UP, owner), "Place pickup");
        h.assertTrue(seals.place(chest, Direction.WEST, owner, GolemSeals.Type.FILL), "Place fill");
        var golem = worker(h, new BlockPos(2, 1, 6), false);
        ItemStack cargo = new ItemStack(Items.DIAMOND, 17);
        cargo.set(DataComponents.CUSTOM_NAME, Component.literal("One paid transfer"));
        Vec3 pos = Vec3.atBottomCenterOf(pickup.above());
        var item = new ItemEntity(level, pos.x, pos.y, pos.z, cargo.copy());
        item.setNoPickUpDelay();
        item.setDeltaMovement(Vec3.ZERO);
        item.setOnGround(true);
        h.assertTrue(level.addFreshEntity(item), "Actual ground source");
        var handler = GolemContainers.handler(level, new GolemSeals.SealKey(chest, Direction.WEST));
        h.assertTrue(handler != null, "Vanilla chest has a sided NeoForge capability");
        Vec3 start = golem.position();
        h.onEachTick(() -> h.assertTrue(count(handler, cargo) + held(golem) + (item.isAlive() ? item.getItem().getCount() : 0) == 17,
                "Every tick conserves source + hands + chest"));
        h.succeedWhen(() -> {
            h.assertTrue(count(handler, cargo) == 17 && held(golem) == 0 && !item.isAlive(), "Complete real pickup/fill cycle");
            h.assertTrue(golem.position().distanceToSqr(start) > 16, "Real navigation to the destination");
            h.assertTrue(golem.rankXp() == 0, "Ordinary head receives no SMART XP");
            h.assertTrue(seals.remove(pickup, Direction.UP, owner) && seals.remove(chest, Direction.WEST, owner), "Remove fixtures");
        });
    }

    @GameTest(template = "golem_test", batch = "fill_partial", timeoutTicks = 60)
    public static void twoWorkersShareOnlyActualRemainingChestCapacity(GameTestHelper h) {
        floor(h);
        BlockPos pos = h.absolutePos(new BlockPos(6, 1, 6));
        h.setBlock(new BlockPos(6, 1, 6), Blocks.CHEST);
        var key = new GolemSeals.SealKey(pos, Direction.WEST);
        var handler = GolemContainers.handler(h.getLevel(), key);
        h.assertTrue(handler instanceof net.neoforged.neoforge.items.IItemHandlerModifiable, "Chest fixture is mutable");
        var fixture = (net.neoforged.neoforge.items.IItemHandlerModifiable) handler;
        for (int i = 0; i < handler.getSlots(); i++) fixture.setStackInSlot(i, new ItemStack(Items.STONE, 64));
        fixture.setStackInSlot(0, new ItemStack(Items.DIAMOND, 59));
        var first = worker(h, new BlockPos(5, 1, 6), false);
        var second = worker(h, new BlockPos(5, 1, 7), false);
        first.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND, 10));
        second.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND, 10));
        UUID owner = UUID.randomUUID();
        h.assertTrue(GolemSeals.get(h.getLevel()).place(pos, Direction.WEST, owner, GolemSeals.Type.FILL), "Place fill");
        h.succeedWhen(() -> {
            h.assertTrue(handler.getStackInSlot(0).getCount() + held(first) + held(second) == 79, "Two workers conserve all cargo");
            h.assertTrue(handler.getStackInSlot(0).getCount() == 64 && held(first) + held(second) == 15,
                    "Only the five actual available slots leave the workers");
            h.assertTrue(GolemSeals.get(h.getLevel()).remove(pos, Direction.WEST, owner), "Remove fill");
        });
    }

    @GameTest(template = "golem_test", batch = "fill_adapters", timeoutTicks = 40)
    public static void actualRemainderOverridesSimulationAndHonorsComponents(GameTestHelper h) {
        floor(h);
        var golem = worker(h, new BlockPos(3, 1, 3), true);
        var destination = new ItemStackHandler(1) {
            @Override public ItemStack insertItem(int slot, ItemStack input, boolean simulate) {
                if (simulate) return super.insertItem(slot, input, true);
                int accepted = Math.min(input.getCount(), Math.max(0, 2 - getStackInSlot(slot).getCount()));
                ItemStack remainder = input.copy();
                super.insertItem(slot, remainder.split(accepted), false);
                return remainder;
            }
        };
        ItemStack input = new ItemStack(Items.DIAMOND, 10);
        h.assertTrue(destination.insertItem(0, input, true).isEmpty(), "Simulation advertises room for ten");
        golem.holdItem(input);
        h.assertTrue(GolemContainers.insertCarried(golem, destination) == 2, "Actual result accepts only two");
        h.assertTrue(held(golem) == 8 && destination.getStackInSlot(0).getCount() == 2 && input.getCount() == 10,
                "Remainder returns intact, without aliasing caller or equipment");
        ItemStack named = new ItemStack(Items.DIAMOND, 7);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Different components"));
        golem.setItemSlot(EquipmentSlot.MAINHAND, named.copy());
        var full = new ItemStackHandler(1);
        full.setStackInSlot(0, new ItemStack(Items.DIAMOND, 60));
        h.assertTrue(GolemContainers.insertCarried(golem, full) == 0 && ItemStack.matches(named, golem.getMainHandItem()),
                "Components mismatch cannot merge or destroy cargo");
        h.succeed();
    }

    @GameTest(template = "golem_test", batch = "fill_sides", timeoutTicks = 40)
    public static void furnaceSideRestrictionsAreNotBypassed(GameTestHelper h) {
        floor(h);
        BlockPos pos = h.absolutePos(new BlockPos(6, 1, 6));
        h.setBlock(new BlockPos(6, 1, 6), Blocks.FURNACE);
        var golem = worker(h, new BlockPos(5, 1, 6), true);
        var bottom = new GolemSeals.SealKey(pos, Direction.DOWN);
        var top = new GolemSeals.SealKey(pos, Direction.UP);
        golem.holdItem(new ItemStack(Items.RAW_IRON, 3));
        h.assertTrue(!GolemContainers.canDeposit(h.getLevel(), golem, bottom), "Bottom cannot accept smelting input");
        h.assertTrue(GolemContainers.deposit(h.getLevel(), golem, bottom) == 0 && held(golem) == 3,
                "Rejected sided insertion leaves cargo intact");
        h.assertTrue(GolemContainers.deposit(h.getLevel(), golem, top) == 3 && held(golem) == 0, "Top accepts real input");
        golem.holdItem(new ItemStack(Items.COAL, 4));
        h.assertTrue(GolemContainers.deposit(h.getLevel(), golem, new GolemSeals.SealKey(pos, Direction.WEST)) == 4,
                "Side accepts fuel through the real capability");
        h.succeed();
    }

    @GameTest(template = "golem_test", batch = "fill_stale", timeoutTicks = 60)
    public static void removedSealAndNewlyFullContainerKeepCargo(GameTestHelper h) {
        floor(h);
        BlockPos pos = h.absolutePos(new BlockPos(6, 1, 6));
        h.setBlock(new BlockPos(6, 1, 6), Blocks.BARREL);
        var key = new GolemSeals.SealKey(pos, Direction.WEST);
        var seals = GolemSeals.get(h.getLevel());
        UUID owner = UUID.randomUUID();
        seals.place(pos, Direction.WEST, owner, GolemSeals.Type.FILL);
        var golem = worker(h, new BlockPos(5, 1, 6), true);
        golem.holdItem(new ItemStack(Items.DIAMOND, 9));
        h.runAfterDelay(25, () -> {
            var assignment = seals.claimFill(golem, ignored -> true).orElseThrow();
            seals.place(pos, Direction.UP, owner, GolemSeals.Type.FILL);
            var redirected = new GolemSeals.FillAssignment(assignment.lease(), new GolemSeals.SealKey(pos, Direction.UP));
            h.assertTrue(!seals.fill(h.getLevel(), golem, redirected) && held(golem) == 9,
                    "A valid token cannot be redirected to a different seal face");
            seals.remove(pos, Direction.UP, owner);
            var fixture = (net.neoforged.neoforge.items.IItemHandlerModifiable) GolemContainers.handler(h.getLevel(), key);
            for (int i = 0; i < fixture.getSlots(); i++) fixture.setStackInSlot(i, new ItemStack(Items.STONE, 64));
            h.assertTrue(!seals.fill(h.getLevel(), golem, assignment) && held(golem) == 9,
                    "Completion rechecks a container filled after task selection");
            h.assertTrue(!seals.remove(pos, Direction.WEST, owner, GolemSeals.Type.PICKUP), "Wrong seal item cannot mint another type");
            h.assertTrue(seals.remove(pos, Direction.WEST, owner), "Owner removes fill");
            h.assertTrue(!seals.fill(h.getLevel(), golem, assignment) && held(golem) == 9, "Stale lease cannot move cargo");
            h.succeed();
        });
    }

    @GameTest(template = "golem_test", batch = "fill_drop", timeoutTicks = 60)
    public static void noContainerDropsOnceAndSuccessfulFillAwardsSmartXp(GameTestHelper h) {
        floor(h);
        BlockPos pos = h.absolutePos(new BlockPos(6, 1, 6));
        h.setBlock(new BlockPos(6, 1, 6), Blocks.STONE);
        var golem = worker(h, new BlockPos(5, 1, 6), true);
        golem.initialize(GolemDesign.fromIds(0, 1, 0, 0, 0, 0), UUID.randomUUID(), golem.home(), 999);
        golem.holdItem(new ItemStack(Items.PAPER, 9));
        var seals = GolemSeals.get(h.getLevel());
        UUID owner = UUID.randomUUID();
        seals.place(pos, Direction.WEST, owner, GolemSeals.Type.FILL);
        h.runAfterDelay(25, () -> {
            var assignment = seals.claimFill(golem, ignored -> true).orElseThrow();
            h.assertTrue(seals.fill(h.getLevel(), golem, assignment), "Source fallback spawns a real item at the selected face");
            h.assertTrue(held(golem) == 0 && golem.design().rank() == 1 && golem.rankXp() == 0,
                    "Exactly one SMART task XP crosses the 1000 XP threshold");
            h.assertTrue(golem.getHealth() == 12 && golem.getMaxHealth() == 13, "Rank attributes update without free healing");
            h.assertTrue(!seals.fill(h.getLevel(), golem, assignment) && golem.rankXp() == 0, "Replay cannot mint items or XP");
            var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, h.getBounds());
            h.assertTrue(drops.size() == 1 && drops.getFirst().getItem().is(Items.PAPER)
                    && drops.getFirst().getItem().getCount() == 9, "One actual ground stack");
            seals.remove(pos, Direction.WEST, owner);
            h.succeed();
        });
    }

    @GameTest(template = "golem_test", batch = "fill_save", timeoutTicks = 60)
    public static void typedSealNbtLoadsWithoutOldReservationsOrForcedChunks(GameTestHelper h) {
        floor(h);
        var level = h.getLevel();
        var seals = GolemSeals.get(level);
        BlockPos pos = h.absolutePos(new BlockPos(6, 1, 6));
        h.setBlock(new BlockPos(6, 1, 6), Blocks.BARREL);
        UUID owner = UUID.randomUUID();
        h.assertTrue(seals.place(pos, Direction.WEST, owner, GolemSeals.Type.FILL), "Fill stored");
        h.assertTrue(!seals.place(pos, Direction.WEST, owner), "One seal of any type per face");
        var restored = GolemSeals.load(seals.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
        h.assertTrue(restored.all().stream().anyMatch(s -> s.key().pos().equals(pos)
                && s.type() == GolemSeals.Type.FILL && s.owner().equals(owner)), "Fill type, face and owner round trip");
        h.assertTrue(restored.pendingTasks() == 0, "Do not deserialize tasks or leases");
        BlockPos distant = pos.offset(16000, 0, 16000);
        h.assertTrue(!level.hasChunkAt(distant), "Fixture starts outside loaded chunks");
        seals.place(distant, Direction.UP, owner, GolemSeals.Type.FILL);
        h.runAfterDelay(25, () -> {
            h.assertTrue(!level.hasChunkAt(distant), "Producer never forces a remote chunk");
            h.assertTrue(!GolemContainers.loaded(level, new GolemSeals.SealKey(distant, Direction.UP)), "Adapter refuses unloaded targets");
            seals.remove(pos, Direction.WEST, owner);
            seals.remove(distant, Direction.UP, owner);
            h.succeed();
        });
    }

    private static void floor(GameTestHelper h) {
        for (int x = 0; x < 12; x++) for (int z = 0; z < 12; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
    }

    private static ThaumcraftGolem worker(GameTestHelper h, BlockPos relative, boolean noAi) {
        var golem = GolemModule.GOLEM.get().create(h.getLevel());
        h.assertTrue(golem != null, "Entity factory");
        BlockPos pos = h.absolutePos(relative);
        golem.moveTo(Vec3.atBottomCenterOf(pos));
        golem.initialize(GolemDesign.defaultWood(), UUID.randomUUID(), pos, 0);
        golem.setNoAi(noAi);
        h.assertTrue(h.getLevel().addFreshEntity(golem), "Real server worker");
        return golem;
    }

    private static int held(ThaumcraftGolem golem) { return golem.getMainHandItem().getCount() + golem.getOffhandItem().getCount(); }
    private static int count(IItemHandler handler, ItemStack match) {
        int total = 0;
        for (int i = 0; i < handler.getSlots(); i++) if (ItemStack.isSameItemSameComponents(match, handler.getStackInSlot(i)))
            total += handler.getStackInSlot(i).getCount();
        return total;
    }
}
