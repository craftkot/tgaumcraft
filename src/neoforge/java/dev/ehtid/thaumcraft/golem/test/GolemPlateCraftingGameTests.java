package dev.ehtid.thaumcraft.golem.test;

import com.mojang.authlib.GameProfile;
import dev.ehtid.thaumcraft.blockentity.ArcaneWorkbenchBlockEntity;
import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.item.CrystalEssenceItem;
import dev.ehtid.thaumcraft.item.PlateItem;
import dev.ehtid.thaumcraft.item.ThaumcraftIngotItem;
import dev.ehtid.thaumcraft.menu.ArcaneWorkbenchMenu;
import dev.ehtid.thaumcraft.player.ThaumcraftPlayerAttachments;
import dev.ehtid.thaumcraft.registry.ThaumcraftBlocks;
import dev.ehtid.thaumcraft.registry.ThaumcraftItems;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.UUID;

/** BETA26 ConfigRecipes.initializeNormalRecipes: BBB pays three ingots for three plates. */
@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class GolemPlateCraftingGameTests {
    private GolemPlateCraftingGameTests() {}
    private record Fixture(FakePlayer player, CraftingMenu menu) {}

    @GameTest(template="golem_test", batch="plate_brass", timeoutTicks=40)
    public static void brassRowPaysThreeActualIngotsForThreePlatesWithoutResearch(GameTestHelper h) {
        requireRecipe(h, "brassplate");
        // Recipe sync must preserve the predicate, rather than flatten all native ingot metadata.
        for (String recipe : new String[]{"brassplate", "thaumiumplate", "voidplate"}) {
            var ingredient = h.getLevel().getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath("thaumcraft", recipe))
                    .orElseThrow().value().getIngredients().getFirst();
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
            try {
                Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, ingredient);
                var restored = Ingredient.CONTENTS_STREAM_CODEC.decode(buffer);
                h.assertTrue(buffer.readableBytes() == 0 && !restored.isSimple(), "Native recipe sync retains component-aware ingredient");
                int expected = recipe.equals("brassplate") ? 2 : recipe.equals("thaumiumplate") ? 0 : 1;
                for (int meta = 0; meta < 3; meta++) h.assertTrue(restored.test(ingot(meta, 1)) == (meta == expected),
                        "Synced " + recipe + " preserves material distinction for native ingot metadata " + meta);
            } finally { buffer.release(); }
        }
        var f = fixture(h); row(f.menu, ingot(2, 3), 0);
        plates(h, f.menu.getSlot(0).getItem(), 0, 3, "Registered brass recipe preview");
        f.menu.clicked(0, 0, ClickType.PICKUP, f.player);
        plates(h, f.menu.getCarried(), 0, 3, "One ordinary take returns three brass plates");
        for (int slot = 1; slot <= 3; slot++)
            h.assertTrue(ThaumcraftIngotItem.legacyMetadata(f.menu.getSlot(slot).getItem()) == 2
                    && f.menu.getSlot(slot).getItem().getCount() == 2, "Exactly one brass ingot paid from each source slot");
        h.assertTrue(f.player.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge().getResearchList().isEmpty(),
                "Normal plate crafting needs no research grant");
        h.succeed();
    }

    @GameTest(template="golem_test", batch="plate_thaumium", timeoutTicks=40)
    public static void bareAndExplicitThaumiumRowsBothPayForPlateMetadataTwo(GameTestHelper h) {
        requireRecipe(h, "thaumiumplate");
        var f = fixture(h);
        for (int offset = 0; offset < 3; offset++) {
            row(f.menu, offset == 0 ? new ItemStack(ThaumcraftItems.INGOT.get()) : ingot(0, 1), offset);
            f.menu.clicked(0, 0, ClickType.PICKUP, f.player);
            plates(h, f.menu.getCarried(), 2, 3, "Bare metadata0 and explicit metadata0 both yield thaumium plates at every row offset");
            for (int slot = 1; slot <= 9; slot++) h.assertTrue(f.menu.getSlot(slot).getItem().isEmpty(), "Thaumium row debited once");
            h.assertTrue(f.player.getInventory().add(f.menu.getCarried()), "Keep actual paid plates");
            f.menu.setCarried(ItemStack.EMPTY);
        }
        h.assertTrue(f.player.getInventory().getItem(0).getCount() == 9, "Three paid batches total nine thaumium plates");
        h.succeed();
    }

    @GameTest(template="golem_test", batch="plate_void", timeoutTicks=40)
    public static void voidMetadataOneCannotBecomeFreeBrassAndPaysForPlateMetadataThree(GameTestHelper h) {
        requireRecipe(h, "voidplate");
        var f = fixture(h); row(f.menu, ingot(1, 2), 2);
        plates(h, f.menu.getSlot(0).getItem(), 3, 3, "Void input selects void plate recipe");
        f.menu.clicked(0, 1, ClickType.PICKUP, f.player);
        plates(h, f.menu.getCarried(), 3, 3, "Right click takes one complete void batch");
        for (int slot = 7; slot <= 9; slot++)
            h.assertTrue(ThaumcraftIngotItem.legacyMetadata(f.menu.getSlot(slot).getItem()) == 1
                    && f.menu.getSlot(slot).getItem().getCount() == 1, "Exactly three void ingots paid");
        h.succeed();
    }

    @GameTest(template="golem_test", batch="plate_shape", timeoutTicks=40)
    public static void mixedMetalsIncompleteRowsAndExtraInputsRejectWithoutPayment(GameTestHelper h) {
        requireRecipe(h, "brassplate"); requireRecipe(h, "thaumiumplate"); requireRecipe(h, "voidplate");
        var f = fixture(h);
        row(f.menu, ingot(2, 2), 0); f.menu.getSlot(2).set(ingot(1, 2));
        rejected(h, f, "Mixed brass and void row");
        row(f.menu, ingot(2, 2), 0); f.menu.getSlot(3).set(ItemStack.EMPTY);
        rejected(h, f, "Two ingots cannot pay for three plates");
        row(f.menu, ingot(2, 2), 0); f.menu.getSlot(5).set(new ItemStack(Items.STICK, 2));
        rejected(h, f, "Extra ingredient outside source row");
        row(f.menu, ingot(2, 2), 0); f.menu.getSlot(2).set(new ItemStack(Items.GOLD_INGOT, 2));
        rejected(h, f, "Vanilla gold cannot replace brass");
        h.succeed();
    }

    @GameTest(template="golem_test", batch="plate_components", timeoutTicks=40)
    public static void legacyMetadataAllowsNamedIngotsButRejectsMalformedAndWrongVariants(GameTestHelper h) {
        requireRecipe(h, "brassplate");
        var f = fixture(h); var named = ingot(2, 2);
        var custom = named.get(DataComponents.CUSTOM_DATA).copyTag(); custom.putString("Origin", "actual named brass");
        named.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Workshop brass"));
        row(f.menu, named, 1); f.menu.clicked(0, 0, ClickType.PICKUP, f.player);
        plates(h, f.menu.getCarried(), 0, 3, "Original ore ingredient ignores unrelated ingot NBT");
        var outputData = f.menu.getCarried().get(DataComponents.CUSTOM_DATA);
        h.assertTrue(!f.menu.getCarried().has(DataComponents.CUSTOM_NAME)
                && (outputData == null || !outputData.copyTag().contains("Origin")),
                "Crafted plates do not inherit input name or unrelated custom data");
        for (int slot = 4; slot <= 6; slot++) h.assertTrue(ItemStack.matches(f.menu.getSlot(slot).getItem(), named.copyWithCount(1)),
                "Unconsumed named ingots retain their real components");
        f.menu.setCarried(ItemStack.EMPTY);
        var malformed = ingot(2, 2); var invalid = new CompoundTag(); invalid.putString("LegacyIngotMeta", "2");
        malformed.set(DataComponents.CUSTOM_DATA, CustomData.of(invalid)); row(f.menu, malformed, 0);
        rejected(h, f, "String metadata is not brass metadata2");
        invalid = new CompoundTag(); invalid.putInt("LegacyIngotMeta", 3);
        malformed.set(DataComponents.CUSTOM_DATA, CustomData.of(invalid)); row(f.menu, malformed, 0);
        rejected(h, f, "Out-of-range ingot metadata cannot select any plate");
        invalid = new CompoundTag(); invalid.putInt("LegacyIngotMeta", -1);
        malformed.set(DataComponents.CUSTOM_DATA, CustomData.of(invalid)); row(f.menu, malformed, 0);
        rejected(h, f, "Negative ingot metadata cannot select any plate");
        row(f.menu, ingot(0, 2), 0);
        plates(h, f.menu.getSlot(0).getItem(), 2, 3, "Thaumium metadata0 remains thaumium and never becomes brass");
        h.succeed();
    }

    @GameTest(template="golem_test", batch="plate_component_chain", timeoutTicks=40)
    public static void ninePaidBrassPlatesFeedThreeMechanismsAndOneClockworkMind(GameTestHelper h) {
        requireRecipe(h, "brassplate");
        var f = fixture(h); row(f.menu, ingot(2, 3), 0);
        for (int craft = 0; craft < 3; craft++) {
            f.menu.clicked(0, 0, ClickType.PICKUP, f.player);
            plates(h, f.menu.getCarried(), 0, 3, "Each source row pays for three actual brass plates");
            h.assertTrue(f.player.getInventory().add(f.menu.getCarried()), "Store earned plate batch");
            f.menu.setCarried(ItemStack.EMPTY);
        }
        for (int slot = 1; slot <= 9; slot++) h.assertTrue(f.menu.getSlot(slot).getItem().isEmpty(), "All nine ingots paid");
        var inventory = f.player.getInventory(); var earned = inventory.getItem(0);
        plates(h, earned, 0, 9, "Three paid rows supply nine real plates");
        requireRecipe(h, "ironplate"); row(f.menu, new ItemStack(Items.IRON_INGOT, 2), 0);
        for (int craft = 0; craft < 2; craft++) {
            f.menu.clicked(0, 0, ClickType.PICKUP, f.player);
            plates(h, f.menu.getCarried(), 1, 3, "Iron row returns three usable plates with integer metadata");
            h.assertTrue(inventory.add(f.menu.getCarried()), "Keep actual paid iron plates"); f.menu.setCarried(ItemStack.EMPTY);
        }
        var iron = inventory.getItem(1);
        plates(h, iron, 1, 6, "Six actual iron ingots funded six usable iron plates");
        for (int slot = 1; slot <= 9; slot++) h.assertTrue(f.menu.getSlot(slot).getItem().isEmpty(), "All six iron ingots paid");
        h.assertTrue(f.player.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge().getResearchList().isEmpty(),
                "Earned plates preceded all arcane research fixture state");

        // Only the existing arcane gate is fixture state; no plates or mechanisms are supplied here.
        var knowledge = f.player.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge();
        knowledge.addResearch("BASEARTIFICE"); knowledge.addResearch("MINDCLOCKWORK"); knowledge.setResearchStage("MINDCLOCKWORK", 2);
        var pos = h.absolutePos(new BlockPos(6, 1, 6)); h.getLevel().setBlockAndUpdate(pos, ThaumcraftBlocks.ARCANE_WORKBENCH.get().defaultBlockState());
        var bench = (ArcaneWorkbenchBlockEntity) h.getLevel().getBlockEntity(pos);
        f.player.moveTo(Vec3.atBottomCenterOf(pos.east()));
        var menu = new ArcaneWorkbenchMenu(32, inventory, bench); bench.setVis(100);
        bench.setItem(10, CrystalEssenceItem.ofAspect("ignis").copyWithCount(4));
        bench.setItem(11, CrystalEssenceItem.ofAspect("aqua").copyWithCount(3));
        bench.setItem(13, CrystalEssenceItem.ofAspect("ordo"));
        var sticks = new ItemStack(Items.STICK, 3);
        for (int craft = 0; craft < 3; craft++) {
            for (int slot : new int[]{1, 7}) bench.setItem(slot, earned.split(1));
            for (int slot : new int[]{3, 5}) bench.setItem(slot, iron.split(1));
            bench.setItem(4, sticks.split(1)); menu.broadcastChanges(); menu.clicked(0, 0, ClickType.PICKUP, f.player);
            h.assertTrue(menu.getCarried().is(GolemModule.SIMPLE_MECHANISM.get()) && menu.getCarried().getCount() == 1,
                    "Earned brass plates become one paid mechanism per click");
            h.assertTrue(inventory.add(menu.getCarried()), "Keep actual paid mechanism"); menu.setCarried(ItemStack.EMPTY);
        }
        var mechanisms = inventory.getItem(2);
        h.assertTrue(mechanisms.is(GolemModule.SIMPLE_MECHANISM.get()) && mechanisms.getCount() == 3
                && earned.getCount() == 3 && iron.isEmpty() && sticks.isEmpty() && bench.getVis() == 70,
                "Three real mechanisms consumed six of nine plates and thirty vis");
        for (int slot : new int[]{1, 3, 5}) bench.setItem(slot, new ItemStack(Items.GLASS_PANE));
        for (int slot : new int[]{6, 8}) bench.setItem(slot, earned.split(1));
        bench.setItem(4, mechanisms.split(1)); bench.setItem(7, new ItemStack(Items.COMPARATOR));
        menu.broadcastChanges(); menu.clicked(0, 0, ClickType.PICKUP, f.player);
        h.assertTrue(menu.getCarried().is(GolemModule.CLOCKWORK_MIND.get()) && menu.getCarried().getCount() == 1
                && mechanisms.getCount() == 2 && earned.getCount() == 1 && bench.getVis() == 45,
                "Eight of nine earned plates plus one actual mechanism fund mind; two mechanisms and one plate remain");
        h.assertTrue(bench.getItem(10).isEmpty() && bench.getItem(11).isEmpty() && bench.getItem(13).isEmpty(),
                "Full chain pays exactly four Ignis, three Aqua and one Ordo crystal");
        for (int slot = 0; slot < 9; slot++) h.assertTrue(bench.getItem(slot).isEmpty(), "Mind input payment cannot replay");
        h.succeed();
    }

    private static Fixture fixture(GameTestHelper h) {
        var pos = h.absolutePos(new BlockPos(3, 1, 3)); h.getLevel().setBlockAndUpdate(pos, Blocks.CRAFTING_TABLE.defaultBlockState());
        var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "PlateCrafter"));
        player.setGameMode(GameType.SURVIVAL); player.getInventory().clearContent();
        player.moveTo(Vec3.atBottomCenterOf(pos.east()));
        return new Fixture(player, new CraftingMenu(31, player.getInventory(), ContainerLevelAccess.create(h.getLevel(), pos)));
    }
    private static void requireRecipe(GameTestHelper h, String path) {
        h.assertTrue(h.getLevel().getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath("thaumcraft", path)).isPresent(),
                "Missing original paid recipe thaumcraft:" + path);
    }
    private static ItemStack ingot(int metadata, int count) { return ThaumcraftIngotItem.create(metadata).copyWithCount(count); }
    private static void row(CraftingMenu menu, ItemStack ingredient, int offset) {
        for (int slot = 1; slot <= 9; slot++) menu.getSlot(slot).set(ItemStack.EMPTY);
        for (int column = 0; column < 3; column++) menu.getSlot(1 + 3 * offset + column).set(ingredient.copy());
        menu.broadcastChanges();
    }
    private static void plates(GameTestHelper h, ItemStack actual, int metadata, int count, String message) {
        h.assertTrue(actual.is(ThaumcraftItems.PLATE.get()) && PlateItem.legacyMetadata(actual) == metadata
                && actual.getCount() == count, message + "; actual=" + actual + ", legacyMetadata=" + PlateItem.legacyMetadata(actual));
    }
    private static void rejected(GameTestHelper h, Fixture f, String message) {
        ItemStack[] before = new ItemStack[9];
        for (int slot = 0; slot < 9; slot++) before[slot] = f.menu.getSlot(slot + 1).getItem().copy();
        f.menu.broadcastChanges(); h.assertTrue(f.menu.getSlot(0).getItem().isEmpty(), message + " has no output");
        f.menu.clicked(0, 0, ClickType.PICKUP, f.player);
        h.assertTrue(f.menu.getCarried().isEmpty(), message + " cannot take a free plate");
        for (int slot = 0; slot < 9; slot++) h.assertTrue(ItemStack.matches(before[slot], f.menu.getSlot(slot + 1).getItem()),
                message + " leaves every actual input intact");
    }
}
