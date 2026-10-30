package dev.ehtid.thaumcraft.infusion.test;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Matrix ordering and saved-output identity exercised by the actual server ticker. */
@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class InfusionCycleGameTests {
    @GameTest(template="infusion_paid_blank", batch="infusion_cycle", timeoutTicks=200)
    public static void missingCenterTriggersFailureWithoutAnotherDebit(GameTestHelper h) {
        var f = new PaidInfusionFixture(h.getLevel(), h.absolutePos(new BlockPos(12,8,12)), "aer");
        f.constructAndStart();
        ItemStack removed = f.pedestal(0).extractToPlayer();
        h.assertTrue(!removed.isEmpty(), "Remove the actual central input after starting");
        h.runAfterDelay(15, () -> {
            h.assertTrue(!f.matrix().crafting(), "Original invalid-center branch stops after its forced hazard");
            h.assertTrue(f.totalEssentia() == 25, "Failed center does not debit one additional jar unit");
            h.assertTrue(f.pedestal(0).displayedItem().isEmpty(), "Failure cannot replace missing input with output");
            h.assertTrue(!f.tag(f.matrix()).contains("PortInfusionLedger"), "Failed recipe state cleared");
            h.succeed();
        });
    }

    @GameTest(template="infusion_paid_blank", batch="infusion_cycle", timeoutTicks=200)
    public static void changedFrozenOutputCannotSpendOrPublish(GameTestHelper h) {
        var f = new PaidInfusionFixture(h.getLevel(), h.absolutePos(new BlockPos(12,8,12)), "aer");
        f.constructAndStart();
        CompoundTag saved = f.tag(f.matrix());
        CompoundTag pending = saved.getCompound("PortInfusionLedger");
        h.assertTrue(pending.getInt("Format") == 3, "New saves include a full frozen output");
        var outputItems = pending.getCompound("PlannedOutput").getList("Items", 10);
        h.assertTrue(outputItems.size() == 1, "Actual serialized output present");
        outputItems.getCompound(0).putInt("count", 2);
        f.matrix().loadWithComponents(saved, h.getLevel().registryAccess());
        h.assertTrue(!f.matrix().crafting(), "Altered output count rejected against source definition");
        h.runAfterDelay(30, () -> {
            h.assertTrue(f.totalEssentia() == 25 && f.pedestal(1).isOccupied()
                    && f.pedestal(2).isOccupied(), "Rejected checkpoint spends nothing");
            h.succeed();
        });
    }
    @GameTest(template="infusion_paid_blank", batch="infusion_cycle_retry", timeoutTicks=1400)
    public static void missingFirstIngredientSkipsRetriesPaysSurchargeAndRestores(GameTestHelper h) {
        var f = new PaidInfusionFixture(h.getLevel(), h.absolutePos(new BlockPos(12,8,12)), "aer");
        f.constructAndStart();
        ItemStack seeds = f.pedestal(1).extractToPlayer();
        h.getLevel().removeBlock(f.pedestals.get(1), false); // Source skips the now-missing unpaid tile.
        for (int i = 0; i < 3; i++) {
            String aspect = new String[]{"aer", "vitreus", "vinculum"}[i];
            h.assertTrue(f.jar(i).addFromTransport(aspect, 100, net.minecraft.core.Direction.UP) == 100,
                    "Real surplus jars fund original missing-ingredient surcharges");
        }
        BlockPos moved = f.matrixPos.offset(-5,-2,4);
        boolean[] restored = {false}; int[] paid = {0}; int[] completion = {-1}; int[] tick = {0};
        h.onEachTick(() -> {
            tick[0]++;
            if (f.matrix().crafting()) {
                var pending = f.ledger();
                paid[0] = pending.getInt("Paid");
                h.assertTrue(paid[0] + f.totalEssentia() == 325, "Source surcharge payment conserves every physical jar unit");
                if (!restored[0] && pending.getInt("ConsumedItems") == 2) {
                    h.assertTrue(!pending.getCompound("Surcharges").isEmpty(), "Missing first source ingredient adds actual payable essentia");
                    var saved = f.tag(f.matrix());
                    f.matrix().loadWithComponents(saved, h.getLevel().registryAccess());
                    h.assertTrue(f.matrix().crafting() && f.ledger().getInt("ConsumedItems") == 2,
                            "Out-of-order debit and outstanding source surcharge survive real NBT decoding");
                    h.getLevel().setBlockAndUpdate(moved, dev.ehtid.thaumcraft.registry.ThaumcraftBlocks.PEDESTAL_ARCANE.get().defaultBlockState());
                    var newPedestal = (dev.ehtid.thaumcraft.blockentity.PedestalBlockEntity) h.getLevel().getBlockEntity(moved);
                    h.assertTrue(newPedestal.insertFromPlayer(seeds), "Return the same removed item to another real pedestal");
                    restored[0] = true;
                }
                return;
            }
            h.assertTrue(restored[0], "Recipe continued after missing first item rather than stalling all later inputs");
            h.assertTrue(paid[0] > 25 && paid[0] + f.totalEssentia() == 325, "All source base and penalty costs truly paid");
            var replacement = (dev.ehtid.thaumcraft.blockentity.PedestalBlockEntity) h.getLevel().getBlockEntity(moved);
            h.assertTrue(replacement.hasInfusionRemovalReceipt(f.ritual, 1)
                    && f.pedestal(2).hasInfusionRemovalReceipt(f.ritual, 2), "Moved unpaid item and skipped second item have their own physical receipts");
            h.assertTrue(f.pedestal(0).displayedItem().is(dev.ehtid.thaumcraft.registry.ThaumcraftBlocks.CRYSTAL_AER.get().asItem())
                    && f.pedestal(0).displayedItem().getCount() == 1, "Retry publishes exactly one original output");
            if (completion[0] < 0) completion[0] = tick[0];
            if (tick[0] - completion[0] >= 30) h.succeed();
        });
    }
}
