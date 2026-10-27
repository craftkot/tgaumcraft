package dev.ehtid.thaumcraft.golem.test;

import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.golem.levitator.GolemLevitatorBlock;
import dev.ehtid.thaumcraft.golem.levitator.GolemLevitatorBlockEntity;
import dev.ehtid.thaumcraft.world.ThaumcraftAuraSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class GolemLevitatorGameTests {
    private static final BlockPos DEVICE = new BlockPos(5, 5, 5);
    private GolemLevitatorGameTests() {}

    @GameTest(template="golem_test", batch="levitator_world_ticks", timeoutTicks=60)
    public static void actualWorldTicksRaiseRealItemOnlyAfterSharedAuraPayment(GameTestHelper h) {
        var device = device(h, Direction.UP); var pos = device.getBlockPos();
        var aura = ThaumcraftAuraSavedData.get(h.getLevel()); aura.putChunk(pos, (short) 100, 0, 0);
        var item = item(h, pos.above(2)); double initialHeight = item.getY();
        h.runAfterDelay(10, () -> {
            h.assertTrue(item.isAlive() && item.getY() == initialHeight && device.credits() == 0,
                    "Ten real world ticks cannot supply free upward motion from empty aura");
            aura.setVis(pos, 1);
            h.runAfterDelay(10, () -> {
                h.assertTrue(item.isAlive() && item.getY() > initialHeight + 1 && device.credits() > 0
                        && device.credits() < 1200 && aura.getVis(pos) == 0,
                        "Actual world/entity ticking raises original item and debits shared aura plus per-entity credits");
                item.discard(); h.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState()); h.succeed();
            });
        });
    }

    @GameTest(template="golem_test", batch="levitator_directions", timeoutTicks=40)
    public static void registeredTickerPaysSharedAuraAndPushesItemsInAllSixDirections(GameTestHelper h) {
        for (Direction direction : Direction.values()) {
            var device = device(h, direction); var pos = device.getBlockPos();
            var aura = ThaumcraftAuraSavedData.get(h.getLevel()); aura.putChunk(pos, (short) 100, 2, 0);
            for (int i = 0; i < 8; i++) tick(h, device);
            h.assertTrue(device.range() == 8 && device.actualRange() == 8 && device.credits() == 1200 && aura.getVis(pos) == 1,
                    "Default8range scans and one sharedVis funds1200credits");
            var item = item(h, pos.relative(direction, 2)); item.fallDistance = 9;
            tick(h, device); Vec3 motion = item.getDeltaMovement();
            close(h, direction.getStepX() * (double) .1F, motion.x);
            close(h, direction.getStepY() * (double) .1F + (direction.getAxis().isHorizontal() ? (double) .08F : 0), motion.y);
            close(h, direction.getStepZ() * (double) .1F, motion.z);
            h.assertTrue(device.credits() == 1184 && item.fallDistance == 0 && item.hurtMarked,
                    "One eligible real entity pays2*8 and clears fall damage with client velocity notification");
            item.discard(); h.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        h.succeed();
    }

    @GameTest(template="golem_test", batch="levitator_redstone", timeoutTicks=40)
    public static void redstonePausesForceButKeepsSourceScanAndAuraRefill(GameTestHelper h) {
        var device = device(h, Direction.UP); var pos = device.getBlockPos();
        var aura = ThaumcraftAuraSavedData.get(h.getLevel()); aura.putChunk(pos, (short) 100, 2, 0);
        h.getLevel().setBlockAndUpdate(pos.east(), Blocks.REDSTONE_BLOCK.defaultBlockState());
        var item = item(h, pos.above(2));
        for (int i = 0; i < 8; i++) tick(h, device);
        h.assertTrue(device.actualRange() == 8 && device.credits() == 1200 && aura.getVis(pos) == 1
                && item.getDeltaMovement().equals(Vec3.ZERO), "Power pauses force while source scan/refill continue");
        h.getLevel().setBlockAndUpdate(pos.east(), Blocks.AIR.defaultBlockState()); tick(h, device);
        close(h, .1F, item.getDeltaMovement().y);
        h.assertTrue(device.credits() == 1184, "Unpowered native neighbor state resumes paid force");
        item.discard(); h.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState()); h.succeed();
    }

    @GameTest(template="golem_test", batch="levitator_obstruction", timeoutTicks=40)
    public static void opaqueProbeLimitsBeamAndSourceRangeCyclesThroughFourSettings(GameTestHelper h) {
        var device = device(h, Direction.UP); var pos = device.getBlockPos();
        ThaumcraftAuraSavedData.get(h.getLevel()).putChunk(pos, (short) 100, 2, 0);
        h.getLevel().setBlockAndUpdate(pos.above(), Blocks.STONE.defaultBlockState());
        var inside = item(h, pos); tick(h, device);
        h.assertTrue(device.actualRange() == 0 && device.credits() == 1200 && inside.getDeltaMovement().equals(Vec3.ZERO),
                "Immediately opaque source beam has zero range and cannot charge or push an entity in the device cell");
        inside.discard(); h.getLevel().setBlockAndUpdate(pos.above(), Blocks.STONE_SLAB.defaultBlockState());
        for (int i = 0; i < 8; i++) tick(h, device);
        h.assertTrue(device.actualRange() == 8, "Non-full slab is not original isOpaqueCube and does not stop scan");
        h.getLevel().setBlockAndUpdate(pos.above(3), Blocks.STONE.defaultBlockState());
        for (int i = 0; i < 3; i++) tick(h, device);
        var near = item(h, pos.above(2)); var behind = item(h, pos.above(4)); tick(h, device);
        h.assertTrue(device.actualRange() == 3 && near.getDeltaMovement().y > 0 && behind.getDeltaMovement().equals(Vec3.ZERO),
                "Source opaque probe includes stop cell and excludes cargo behind it");
        for (int expected : new int[]{16, 32, 4, 8}) {
            device.cycleRange(); h.assertTrue(device.range() == expected && device.actualRange() == 0 && device.cost() == expected * 2,
                    "Source range cycle clears transient beam length and updates exact entity price");
        }
        near.discard(); behind.discard(); h.getLevel().setBlockAndUpdate(pos.above(3), Blocks.AIR.defaultBlockState());
        h.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState()); h.succeed();
    }

    @GameTest(template="golem_test", batch="levitator_persistence", timeoutTicks=40)
    public static void negativeCreditRemainderAndSneakUpPersistWithoutSavingTransientScan(GameTestHelper h) {
        var device = device(h, Direction.UP); var pos = device.getBlockPos();
        ThaumcraftAuraSavedData.get(h.getLevel()).putChunk(pos, (short) 100, 0, 0);
        var loaded = new CompoundTag(); loaded.putByte("range", (byte) 3); loaded.putInt("vis", 1);
        device.loadWithComponents(loaded, h.getLevel().registryAccess());
        var item = item(h, pos.above()); item.setShiftKeyDown(true); item.setDeltaMovement(1, -.2, 1); item.fallDistance = 9;
        tick(h, device);
        close(h, 1, item.getDeltaMovement().x); close(h, -.2 * (double) .9F, item.getDeltaMovement().y);
        h.assertTrue(device.credits() == -63 && item.fallDistance == 0, "SneakUP skips normal force/clamp but still pays64 from1credit");
        var saved = device.saveWithFullMetadata(h.getLevel().registryAccess());
        var restored = new GolemLevitatorBlockEntity(pos, device.getBlockState()); restored.loadWithComponents(saved, h.getLevel().registryAccess());
        h.assertTrue(saved.getByte("range") == 3 && saved.getInt("vis") == -63 && restored.range() == 32
                && restored.credits() == -63 && restored.actualRange() == 0, "NativeNBT preserves exact source keys and negative remainder; scan rebuilds");
        saved.putByte("range", (byte) 127); restored.loadWithComponents(saved, h.getLevel().registryAccess());
        h.assertTrue(restored.range() == 8, "Malformed range safely restores default rather than crashing ticker");
        item.discard(); h.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState()); h.succeed();
    }

    private static GolemLevitatorBlockEntity device(GameTestHelper h, Direction direction) {
        BlockPos pos = h.absolutePos(DEVICE);
        for (Direction face : Direction.values()) for (int i = 1; i <= 8; i++) h.getLevel().setBlockAndUpdate(pos.relative(face, i), Blocks.AIR.defaultBlockState());
        h.getLevel().setBlockAndUpdate(pos, GolemModule.LEVITATOR.get().defaultBlockState().setValue(GolemLevitatorBlock.FACING, direction));
        return (GolemLevitatorBlockEntity) h.getLevel().getBlockEntity(pos);
    }
    private static ItemEntity item(GameTestHelper h, BlockPos pos) {
        Vec3 center = Vec3.atCenterOf(pos); var item = new ItemEntity(h.getLevel(), center.x, center.y, center.z, new ItemStack(Items.IRON_INGOT));
        item.setNoGravity(true); item.setDeltaMovement(Vec3.ZERO); h.getLevel().addFreshEntity(item); return item;
    }
    private static void tick(GameTestHelper h, GolemLevitatorBlockEntity device) {
        var ticker = GolemModule.LEVITATOR.get().getTicker(h.getLevel(), device.getBlockState(), GolemModule.LEVITATOR_ENTITY.get());
        h.assertTrue(ticker != null, "Actual registered server block ticker exists");
        ticker.tick(h.getLevel(), device.getBlockPos(), device.getBlockState(), device);
    }
    private static void close(GameTestHelper h, double expected, double actual) { h.assertTrue(Math.abs(expected - actual) < 1e-10, "Expected motion " + expected + ", got " + actual); }
}
