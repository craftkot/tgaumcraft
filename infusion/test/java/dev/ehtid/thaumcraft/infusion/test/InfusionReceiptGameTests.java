package dev.ehtid.thaumcraft.infusion.test;

import dev.ehtid.thaumcraft.blockentity.PedestalBlockEntity;
import dev.ehtid.thaumcraft.registry.ThaumcraftBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** Actual world pedestal inventory/persistence checks, separate from paid recipe acceptance. */
@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class InfusionReceiptGameTests {
    private InfusionReceiptGameTests() {}

    @GameTest(template="infusion_paid_blank", batch="infusion_receipts", timeoutTicks=40)
    public static void completeOutputCountAndComponentsSurviveReload(GameTestHelper h) {
        PedestalBlockEntity pedestal = pedestal(h);
        UUID ritual = UUID.randomUUID();
        ItemStack input = new ItemStack(Items.DIAMOND);
        ItemStack output = named(new ItemStack(Items.EMERALD, 4), "Exact four-item result");
        CompoundTag data = new CompoundTag();
        data.putString("receipt_test", "original components");
        output.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        pedestal.setItem(0, input);
        h.assertTrue(pedestal.publishInfusionResultWithReceipt(input, output, ritual),
                "Verified publication accepts the complete four-item result");
        h.assertTrue(ItemStack.matches(pedestal.displayedItem(), output), "Publication retains count and every component");
        h.assertTrue(pedestal.hasInfusionOutputReceipt(ritual, output), "Exact physical output has publication proof");
        h.assertTrue(!pedestal.hasInfusionOutputReceipt(UUID.randomUUID(), output), "Foreign ritual cannot claim output");
        h.assertTrue(!pedestal.hasInfusionOutputReceipt(ritual, output.copyWithCount(1)), "Wrong count cannot acknowledge output");
        h.assertTrue(!pedestal.hasInfusionOutputReceipt(ritual, named(output.copy(), "Changed result")),
                "Wrong components cannot acknowledge output");
        pedestal = reload(h, pedestal, save(h, pedestal));
        h.assertTrue(ItemStack.matches(pedestal.displayedItem(), output)
                        && pedestal.hasInfusionOutputReceipt(ritual, output),
                "Reconstructed world pedestal retains complete output and its exact proof");
        h.assertTrue(!pedestal.publishInfusionResultWithReceipt(input, output, ritual), "Repeated publication cannot replay input");
        ItemStack extracted = pedestal.extractToPlayer();
        h.assertTrue(ItemStack.matches(extracted, output) && pedestal.isEmpty(), "Player extraction receives all four exact items once");
        h.assertTrue(!pedestal.hasInfusionOutputReceipt(ritual, output) && pedestal.extractToPlayer().isEmpty(),
                "Extraction erases proof and cannot be repeated");
        h.succeed();
    }

    @GameTest(template="infusion_paid_blank", batch="infusion_receipts", timeoutTicks=40)
    public static void realMilkBucketRemainderSurvivesReloadWithoutWorldDropOrReplay(GameTestHelper h) {
        PedestalBlockEntity pedestal = pedestal(h);
        UUID ritual = UUID.randomUUID();
        ItemStack input = named(new ItemStack(Items.MILK_BUCKET), "Real source milk");
        ItemStack remainder = input.copy().getCraftingRemainingItem();
        h.assertTrue(ItemStack.matches(remainder, new ItemStack(Items.BUCKET)), "Actual vanilla milk bucket returns one bucket");
        pedestal.setItem(0, input);
        h.assertTrue(pedestal.consumeInfusionIngredientWithReceipt(input, ritual, 4), "Actual side ingredient debit succeeds");
        h.assertTrue(ItemStack.matches(pedestal.displayedItem(), remainder), "Actual container replaces the entire side slot");
        h.assertTrue(pedestal.hasInfusionRemovalReceipt(ritual, 4, remainder), "Receipt binds real bucket to ritual and ingredient");
        h.assertTrue(!pedestal.hasInfusionRemovalReceipt(ritual, 4), "Legacy empty-slot query cannot acknowledge a bucket");
        h.assertTrue(!pedestal.hasInfusionRemovalReceipt(UUID.randomUUID(), 4, remainder)
                        && !pedestal.hasInfusionRemovalReceipt(ritual, 2, remainder),
                "Foreign ritual and wrong ingredient bit cannot acknowledge remainder");
        pedestal = reload(h, pedestal, save(h, pedestal));
        h.assertTrue(ItemStack.matches(pedestal.displayedItem(), remainder)
                        && pedestal.hasInfusionRemovalReceipt(ritual, 4, remainder),
                "Reconstructed world pedestal retains actual crafting remainder and proof");
        h.assertTrue(!pedestal.consumeInfusionIngredientWithReceipt(input, ritual, 4), "Reloaded debit cannot consume milk twice");
        h.assertTrue(!pedestal.consumeInfusionIngredientWithReceipt(remainder, ritual, 4),
                "Receipt also prevents replay using its own physical remainder as a new input");
        h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class,
                        new AABB(pedestal.getBlockPos()).inflate(2)).isEmpty(),
                "Container remains in pedestal with no additional dropped world items");
        ItemStack removed = pedestal.extractToPlayer();
        h.assertTrue(ItemStack.matches(removed, remainder) && !pedestal.hasInfusionRemovalReceipt(ritual, 4, remainder),
                "Player takes the actual bucket and invalidates receipt");
        h.succeed();
    }

    @GameTest(template="infusion_paid_blank", batch="infusion_receipts", timeoutTicks=40)
    public static void changedRemainderCountComponentsAndMissingSnapshotRejectReload(GameTestHelper h) {
        PedestalBlockEntity pedestal = pedestal(h);
        UUID ritual = UUID.randomUUID();
        ItemStack milk = new ItemStack(Items.MILK_BUCKET);
        ItemStack bucket = new ItemStack(Items.BUCKET);
        pedestal.setItem(0, milk);
        h.assertTrue(pedestal.consumeInfusionIngredientWithReceipt(milk, ritual, 1), "Real milk consumed before corruption fixtures");
        CompoundTag original = save(h, pedestal);
        for (ItemStack replacement : new ItemStack[]{bucket.copyWithCount(2), named(bucket.copy(), "Forged bucket"),
                new ItemStack(Items.GLASS_BOTTLE), ItemStack.EMPTY}) {
            CompoundTag changed = original.copy();
            overwriteInventory(h, changed, replacement);
            pedestal = reload(h, pedestal, changed);
            h.assertTrue(!pedestal.hasInfusionRemovalReceipt(ritual, 1, bucket)
                            && !pedestal.hasInfusionRemovalReceipt(ritual, 1, replacement)
                            && !save(h, pedestal).contains("PortInfusionRemovalRitual"),
                    "Changed count/item/components/empty slot cannot retain or substitute saved remainder proof");
        }
        CompoundTag missing = original.copy();
        missing.remove("PortInfusionRemovalRemainder");
        pedestal = reload(h, pedestal, missing);
        h.assertTrue(!pedestal.hasInfusionRemovalReceipt(ritual, 1, bucket),
                "Bare UUID/bit cannot forge a nonempty container receipt");
        CompoundTag invalid = original.copy();
        invalid.putString("PortInfusionRemovalRemainder", "not an ItemStack");
        pedestal = reload(h, pedestal, invalid);
        h.assertTrue(!pedestal.hasInfusionRemovalReceipt(ritual, 1, bucket), "Malformed remainder snapshot is rejected");
        h.succeed();
    }

    @GameTest(template="infusion_paid_blank", batch="infusion_receipts", timeoutTicks=40)
    public static void changedOutputCannotBorrowSavedPublicationProof(GameTestHelper h) {
        PedestalBlockEntity pedestal = pedestal(h);
        UUID ritual = UUID.randomUUID();
        ItemStack input = new ItemStack(Items.DIAMOND);
        ItemStack output = named(new ItemStack(Items.EMERALD, 4), "Original publication");
        pedestal.setItem(0, input);
        h.assertTrue(pedestal.publishInfusionResultWithReceipt(input, output, ritual), "Real complete output published before corruption fixtures");
        CompoundTag original = save(h, pedestal);
        for (ItemStack replacement : new ItemStack[]{output.copyWithCount(1), named(output.copy(), "Changed publication"),
                new ItemStack(Items.GOLD_INGOT, 4)}) {
            CompoundTag changed = original.copy();
            overwriteInventory(h, changed, replacement);
            pedestal = reload(h, pedestal, changed);
            h.assertTrue(!pedestal.hasInfusionOutputReceipt(ritual, output)
                            && !pedestal.hasInfusionOutputReceipt(ritual, pedestal.displayedItem())
                            && !save(h, pedestal).contains("PortInfusionOutputRitual"),
                    "Changed count/item/components reject saved output proof rather than rebinding it");
        }
        CompoundTag missing = original.copy();
        missing.remove("PortInfusionOutputStack");
        pedestal = reload(h, pedestal, missing);
        h.assertTrue(pedestal.displayedItem().getCount() == 1
                        && !pedestal.hasInfusionOutputReceipt(ritual, pedestal.displayedItem()),
                "Unproven multi-count inventory cannot bypass normal pedestal limit");
        h.succeed();
    }

    @GameTest(template="infusion_paid_blank", batch="infusion_receipts", timeoutTicks=40)
    public static void emptyRemovalCompatibilityAndExplicitClearInvalidateReceipt(GameTestHelper h) {
        PedestalBlockEntity pedestal = pedestal(h);
        UUID ritual = UUID.randomUUID();
        ItemStack input = new ItemStack(Items.DIAMOND);
        pedestal.setItem(0, input);
        h.assertTrue(pedestal.consumeInfusionIngredientWithReceipt(input, ritual, 1)
                        && pedestal.hasInfusionRemovalReceipt(ritual, 1),
                "Non-container input retains the existing empty-slot receipt API");
        CompoundTag legacy = save(h, pedestal);
        legacy.remove("PortInfusionRemovalRemainder");
        pedestal = reload(h, pedestal, legacy);
        h.assertTrue(pedestal.hasInfusionRemovalReceipt(ritual, 1), "Legacy empty-slot receipt safely reloads");
        pedestal.clearContent();
        h.assertTrue(!pedestal.hasInfusionRemovalReceipt(ritual, 1), "Explicit clear invalidates proof even when already empty");
        pedestal = reload(h, pedestal, save(h, pedestal));
        h.assertTrue(!pedestal.hasInfusionRemovalReceipt(ritual, 1), "Cleared receipt cannot reappear after reload");
        pedestal.setItem(0, new ItemStack(Items.BUCKET));
        h.assertTrue(!pedestal.hasInfusionRemovalReceipt(ritual, 1, new ItemStack(Items.BUCKET)),
                "An ordinary matching bucket without receipt cannot authorize recovery");
        h.succeed();
    }

    @GameTest(template="infusion_paid_blank", batch="infusion_receipts", timeoutTicks=40)
    public static void legacySingleOutputReceiptMigratesAndStillChecksExpectedComponents(GameTestHelper h) {
        PedestalBlockEntity pedestal = pedestal(h);
        UUID ritual = UUID.randomUUID();
        ItemStack input = new ItemStack(Items.DIAMOND);
        ItemStack output = named(new ItemStack(Items.EMERALD), "Legacy exact output");
        pedestal.setItem(0, input);
        h.assertTrue(pedestal.publishInfusionResultWithReceipt(input, output, ritual), "Real single output establishes original receipt");
        CompoundTag legacy = save(h, pedestal);
        legacy.remove("PortInfusionOutputStack");
        pedestal = reload(h, pedestal, legacy);
        h.assertTrue(pedestal.hasInfusionOutputReceipt(ritual, output), "Legacy single-count receipt migrates to exact physical snapshot");
        h.assertTrue(!pedestal.hasInfusionOutputReceipt(ritual, new ItemStack(Items.EMERALD))
                        && !pedestal.hasInfusionOutputReceipt(UUID.randomUUID(), output),
                "Migrated receipt still requires the matrix's full expected components and same ritual");
        h.assertTrue(save(h, pedestal).contains("PortInfusionOutputStack"), "Next save upgrades legacy receipt to explicit stack snapshot");
        h.succeed();
    }

    @GameTest(template="infusion_paid_blank", batch="infusion_receipts", timeoutTicks=40)
    public static void directInventoryMutationAndSameItemPublicationCannotReplay(GameTestHelper h) {
        PedestalBlockEntity pedestal = pedestal(h);
        UUID ritual = UUID.randomUUID();
        ItemStack input = new ItemStack(Items.DIAMOND);
        pedestal.setItem(0, input);
        h.assertTrue(pedestal.publishInfusionResultWithReceipt(input, input, ritual), "Same-item result can be published once");
        h.assertTrue(!pedestal.publishInfusionResultWithReceipt(input, input, ritual),
                "Same-item output cannot publish again using its already consumed input identity");
        pedestal.getItem(0).set(DataComponents.CUSTOM_NAME, Component.literal("External mutation"));
        pedestal.setChanged();
        h.assertTrue(!pedestal.hasInfusionOutputReceipt(ritual, pedestal.getItem(0))
                        && !save(h, pedestal).contains("PortInfusionOutputRitual"),
                "Live container mutation invalidates proof before persistence");
        pedestal.setItem(0, new ItemStack(Items.MILK_BUCKET));
        UUID sideRitual = UUID.randomUUID();
        h.assertTrue(pedestal.consumeInfusionIngredientWithReceipt(new ItemStack(Items.MILK_BUCKET), sideRitual, 2),
                "Real side consume prepares remainder mutation case");
        pedestal.getItem(0).setCount(2);
        pedestal.setChanged();
        h.assertTrue(!pedestal.hasInfusionRemovalReceipt(sideRitual, 2, pedestal.getItem(0))
                        && !save(h, pedestal).contains("PortInfusionRemovalRitual"),
                "Live remainder count mutation cannot rebind historical proof");
        h.succeed();
    }

    private static PedestalBlockEntity pedestal(GameTestHelper h) {
        BlockPos pos = h.absolutePos(new BlockPos(12, 6, 12));
        h.getLevel().setBlockAndUpdate(pos, ThaumcraftBlocks.PEDESTAL_ARCANE.get().defaultBlockState());
        return (PedestalBlockEntity) h.getLevel().getBlockEntity(pos);
    }

    private static ItemStack named(ItemStack stack, String name) {
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }

    private static CompoundTag save(GameTestHelper h, PedestalBlockEntity pedestal) {
        return pedestal.saveWithFullMetadata(h.getLevel().registryAccess());
    }

    private static PedestalBlockEntity reload(GameTestHelper h, PedestalBlockEntity old, CompoundTag saved) {
        var restored = new PedestalBlockEntity(old.getBlockPos(), old.getBlockState());
        restored.loadWithComponents(saved, h.getLevel().registryAccess());
        h.getLevel().setBlockEntity(restored);
        h.assertTrue(h.getLevel().getBlockEntity(old.getBlockPos()) == restored, "New deserialized pedestal is installed in the actual world");
        return restored;
    }

    private static void overwriteInventory(GameTestHelper h, CompoundTag saved, ItemStack replacement) {
        NonNullList<ItemStack> inventory = NonNullList.withSize(1, ItemStack.EMPTY);
        inventory.set(0, replacement.copy());
        ContainerHelper.saveAllItems(saved, inventory, h.getLevel().registryAccess());
    }
}
