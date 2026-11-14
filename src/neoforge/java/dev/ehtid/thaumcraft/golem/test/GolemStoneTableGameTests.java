package dev.ehtid.thaumcraft.golem.test;

import com.mojang.authlib.GameProfile;
import dev.ehtid.thaumcraft.golem.GolemModule;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.UUID;

@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class GolemStoneTableGameTests {
    private GolemStoneTableGameTests() {}

    @GameTest(template="golem_test", batch="stone_table_recipe", timeoutTicks=40)
    public static void ordinaryCraftingMenuPaysExactLegacySlabsAndStoneWithBothRowOffsets(GameTestHelper h) {
        BlockPos pos = h.absolutePos(new BlockPos(5, 1, 5)); h.getLevel().setBlockAndUpdate(pos, Blocks.CRAFTING_TABLE.defaultBlockState());
        var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "StoneTableCrafter"));
        player.setGameMode(GameType.SURVIVAL); player.moveTo(Vec3.atBottomCenterOf(pos.east())); player.getInventory().clearContent();
        var menu = new CraftingMenu(23, player.getInventory(), ContainerLevelAccess.create(h.getLevel(), pos));
        for (int i = 0; i < 3; i++) menu.getSlot(i + 1).set(new ItemStack(Items.SMOOTH_STONE_SLAB, 2));
        menu.getSlot(4).set(new ItemStack(Items.STONE, 2)); menu.getSlot(6).set(new ItemStack(Items.STONE, 2));
        menu.broadcastChanges();
        h.assertTrue(menu.getSlot(0).getItem().is(GolemModule.STONE_TABLE_ITEM.get()) && menu.getSlot(0).getItem().getCount() == 1,
                "Source SSS/W W recipe discovered through real vanilla crafting menu");
        menu.getSlot(1).set(new ItemStack(Items.STONE_SLAB, 2)); menu.broadcastChanges();
        h.assertTrue(menu.getSlot(0).getItem().isEmpty(), "Modern rough stone slab cannot replace legacy stone_slab@0→smooth_stone_slab");
        menu.getSlot(1).set(new ItemStack(Items.SMOOTH_STONE_SLAB, 2)); menu.getSlot(4).set(new ItemStack(Items.COBBLESTONE, 2)); menu.broadcastChanges();
        h.assertTrue(menu.getSlot(0).getItem().isEmpty(), "Cobblestone is not original stone ore entry");
        menu.getSlot(4).set(new ItemStack(Items.ANDESITE, 2)); menu.broadcastChanges();
        h.assertTrue(menu.getSlot(0).getItem().isEmpty(), "Modern c:stones expansion is not silently substituted for the original vanilla stone-only entry");
        menu.getSlot(4).set(new ItemStack(Items.STONE, 2)); menu.broadcastChanges(); menu.clicked(0, 0, ClickType.PICKUP, player);
        h.assertTrue(menu.getCarried().is(GolemModule.STONE_TABLE_ITEM.get()) && menu.getCarried().getCount() == 1, "Normal take returns exactly one paid table");
        for (int slot : new int[]{1, 2, 3, 4, 6}) h.assertTrue(menu.getSlot(slot).getItem().getCount() == 1, "Exactly one from each of five source slots debited");
        h.assertTrue(player.getInventory().add(menu.getCarried()), "Keep first paid table"); menu.setCarried(ItemStack.EMPTY);
        ItemStack[] remainder = new ItemStack[9]; for (int i = 0; i < 9; i++) remainder[i] = menu.getSlot(i + 1).remove(64);
        for (int i = 0; i < 6; i++) menu.getSlot(i + 4).set(remainder[i]);
        menu.broadcastChanges(); h.assertTrue(menu.getSlot(0).getItem().is(GolemModule.STONE_TABLE_ITEM.get()), "Source3×2 pattern also fits lower two rows");
        menu.quickMoveStack(player, 0);
        int tables = 0; for (var stack : player.getInventory().items) if (stack.is(GolemModule.STONE_TABLE_ITEM.get())) tables += stack.getCount();
        h.assertTrue(tables == 2, "Normal and shift take together yield two tables for six slabs and four stone");
        for (int i = 1; i <= 9; i++) h.assertTrue(menu.getSlot(i).getItem().isEmpty(), "No ingredients remain to replay");
        player.getInventory().clearContent(); h.succeed();
    }

    @GameTest(template="golem_test", batch="stone_table_world", timeoutTicks=40)
    public static void realTablePlacementHasTopSupportAndDropsOneItemWithoutBlockEntity(GameTestHelper h) {
        BlockPos support = h.absolutePos(new BlockPos(6, 1, 6)), pos = support.above();
        h.getLevel().setBlockAndUpdate(support, Blocks.STONE.defaultBlockState());
        var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "StoneTablePlacer"));
        player.setGameMode(GameType.SURVIVAL); player.moveTo(Vec3.atBottomCenterOf(pos.east())); player.getInventory().clearContent();
        var items = new ItemStack(GolemModule.STONE_TABLE_ITEM.get(), 2); player.setItemInHand(InteractionHand.MAIN_HAND, items);
        var placed = GolemModule.STONE_TABLE_ITEM.get().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(support).add(0, .5, 0), Direction.UP, support, false)));
        h.assertTrue(placed.consumesAction() && items.getCount() == 1 && h.getLevel().getBlockState(pos).is(GolemModule.STONE_TABLE.get()), "One item becomes a real placed table");
        var state = h.getLevel().getBlockState(pos);
        for (Direction face : Direction.values()) h.assertTrue(state.isFaceSturdy(h.getLevel(), pos, face) == (face == Direction.UP), "Only UP supports attachments: " + face);
        h.assertTrue(state.isCollisionShapeFullBlock(h.getLevel(), pos) && !state.canOcclude() && h.getLevel().getBlockEntity(pos) == null,
                "Original full collision/nonopaque table needs no duplicated inventory or block entity");
        h.assertTrue(player.hasCorrectToolForDrops(state) && new ItemStack(Items.IRON_PICKAXE).getDestroySpeed(state) > 1,
                "Source allows harvest without required tier; pickaxe tag provides mining speed");
        h.assertTrue(h.getLevel().destroyBlock(pos, true, player), "Actual world removal");
        int drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, h.getBounds()).stream()
                .filter(e -> e.getItem().is(GolemModule.STONE_TABLE_ITEM.get())).mapToInt(e -> e.getItem().getCount()).sum();
        h.assertTrue(drops == 1 && items.getCount() == 1 && h.getLevel().getBlockState(pos).isAir(), "Exactly two initial items conserved as one held and one dropped");
        player.getInventory().clearContent(); h.succeed();
    }
}
