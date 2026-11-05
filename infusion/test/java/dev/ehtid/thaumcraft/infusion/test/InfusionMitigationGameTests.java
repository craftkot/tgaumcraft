package dev.ehtid.thaumcraft.infusion.test;

import com.mojang.logging.LogUtils;
import dev.ehtid.thaumcraft.block.PedestalBlock;
import dev.ehtid.thaumcraft.blockentity.InfusionStabilizerBlockEntity;
import dev.ehtid.thaumcraft.crafting.InfusionStabilizerAdapter;
import dev.ehtid.thaumcraft.infusion.InfusionStabilityBlocks;
import dev.ehtid.thaumcraft.registry.ThaumcraftBlocks;
import dev.ehtid.thaumcraft.world.ThaumcraftAuraSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual BE ticks, stored energy, physical inlay/pedestal gradient and pollution. */
@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class InfusionMitigationGameTests {
    private InfusionMitigationGameTests() {}

    @GameTest(template = "infusion_paid_blank", batch = "infusion_mitigation", timeoutTicks = 360)
    public static void realRechargePollutionAndSavedDebitPowerOnlyConnectedPedestals(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos source = h.absolutePos(new BlockPos(8, 5, 8));
        BlockPos first = source.east();
        BlockPos second = source.east(2);
        BlockPos pedestal = source.east(3);
        BlockPos detached = source.east(5);
        var aura = ThaumcraftAuraSavedData.get(level);
        // Aura is an explicit fixture; the device never creates a synthetic aura chunk.
        aura.putChunk(source, (short) 100, 100.0F, 0.0F);
        for (BlockPos at : new BlockPos[]{source, first, second, pedestal, detached})
            level.setBlockAndUpdate(at.below(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(source, InfusionStabilityBlocks.STABILIZER.get().defaultBlockState());
        level.setBlockAndUpdate(first, InfusionStabilityBlocks.INLAY.get().defaultBlockState());
        level.setBlockAndUpdate(second, InfusionStabilityBlocks.INLAY.get().defaultBlockState());
        level.setBlockAndUpdate(pedestal, ThaumcraftBlocks.PEDESTAL_ARCANE.get().defaultBlockState());
        level.setBlockAndUpdate(detached, ThaumcraftBlocks.PEDESTAL_ARCANE.get().defaultBlockState());
        h.assertTrue(device(h, source).energy() == 0 && charge(h, pedestal) == 0,
                "New physical device has no free energy and no powered pedestal");

        h.runAfterDelay(305, () -> {
            var tile = device(h, source);
            h.assertTrue(tile.energy() == 15, "Server ticks refill original device to exact cap15");
            h.assertTrue(Float.compare(aura.getFlux(source), 3.75F) == 0,
                    "Fifteen real recharge units pollute existing aura by exactly3.75 flux");
            h.assertTrue(charge(h, first) == 15 && charge(h, second) == 14 && charge(h, pedestal) == 13,
                    "Physical inlay and pedestal carry original descending charge gradient");
            h.assertTrue(charge(h, detached) == 0,
                    "Nearby disconnected pedestal receives no energy through empty air");
            h.assertTrue(tile == InfusionStabilizerAdapter.findMitigator(level, pedestal),
                    "Actual pedestal source search follows charged inlay back to physical device");
            h.assertTrue(!tile.mitigate(16) && tile.energy() == 15,
                    "Insufficient energy preserves actual stored energy");
            h.assertTrue(tile.mitigate(10) && tile.energy() == 5,
                    "Successful original mitigation debits ten physical energy units");
            var saved = tile.saveWithFullMetadata(level.registryAccess());
            var loaded = new InfusionStabilizerBlockEntity(source, tile.getBlockState());
            loaded.loadWithComponents(saved, level.registryAccess());
            h.assertTrue(loaded.energy() == 5 && saved.getInt("energy") == 5,
                    "Debited energy survives original energy NBT into a fresh block entity");
        });

        h.runAfterDelay(306, () -> {
            h.assertTrue(charge(h, first) == 5 && charge(h, second) == 4 && charge(h, pedestal) == 3,
                    "A real energy debit recomputes every connected charge");
            h.assertTrue(device(h, source).mitigate(5), "Last five energy units can be spent once");
            h.assertTrue(!device(h, source).mitigate(5), "Spent device cannot replay mitigation");
            level.removeBlock(source, false);
        });

        h.runAfterDelay(307, () -> {
            h.assertTrue(charge(h, first) == 0 && charge(h, second) == 0 && charge(h, pedestal) == 0,
                    "Removing real source drains remaining conductor charge");
            h.assertTrue(InfusionStabilizerAdapter.findMitigator(level, pedestal) == null,
                    "Disconnected pedestal cannot find or manufacture a mitigation source");
            LogUtils.getLogger().info("INFUSION_MITIGATION_NATIVE_OK recharge=15 flux=3.75 debit=15 savedEnergy=5 sourceRemoval=drained");
            h.succeed();
        });
    }

    private static int charge(GameTestHelper h, BlockPos pos) {
        return h.getLevel().getBlockState(pos).getValue(PedestalBlock.CHARGE);
    }

    private static InfusionStabilizerBlockEntity device(GameTestHelper h, BlockPos pos) {
        return (InfusionStabilizerBlockEntity) h.getLevel().getBlockEntity(pos);
    }
}
