package dev.ehtid.thaumcraft.golem.test;

import com.mojang.authlib.GameProfile;
import dev.ehtid.thaumcraft.blockentity.EssentiaJarBlockEntity;
import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.golem.press.*;
import dev.ehtid.thaumcraft.item.GolemPlacerItem;
import dev.ehtid.thaumcraft.port.core.golem.GolemDesign;
import dev.ehtid.thaumcraft.registry.ThaumcraftBlocks;
import dev.ehtid.thaumcraft.registry.ThaumcraftItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.UUID;

@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class GolemPressGameTests {
    private static final BlockPos POS = new BlockPos(5, 2, 5);
    private GolemPressGameTests() {}

    @GameTest(template="golem_test", batch="press_payment", timeoutTicks=40)
    public static void fullPreflightPaysAdjacentComponentsOrPlayerWithoutPartialDebit(GameTestHelper h) {
        var press = press(h); var owner = player(h); var inv = owner.getInventory();
        ingredients(inv, 1);
        inv.setItem(2, ItemStack.EMPTY);
        h.assertTrue(!press.start(owner) && inv.getItem(0).getCount() == 3 && inv.getItem(1).getCount() == 2,
                "Missing last component cannot partially debit earlier components");
        h.setBlock(POS.above(), Blocks.BARREL);
        Container top = (Container) h.getLevel().getBlockEntity(h.absolutePos(POS.above()));
        top.setItem(0, new ItemStack(GolemModule.CLOCKWORK_MIND.get()));
        h.assertTrue(!press.start(owner) && top.getItem(0).getCount() == 1, "Top ingredient container excluded in BETA26");
        h.setBlock(POS.west(), Blocks.BARREL);
        h.setBlock(POS.north(), Blocks.BARREL);
        Container west = (Container) h.getLevel().getBlockEntity(h.absolutePos(POS.west()));
        Container north = (Container) h.getLevel().getBlockEntity(h.absolutePos(POS.north()));
        west.setItem(0, new ItemStack(GolemModule.CLOCKWORK_MIND.get()));
        west.setItem(1, new ItemStack(ThaumcraftItems.PLANK_GREATWOOD.get(), 1));
        inv.getItem(0).setCount(2);
        h.assertTrue(!press.start(owner) && inv.getItem(0).getCount() == 2 && west.getItem(1).getCount() == 1,
                "A component cannot combine a partial adjacent supply with a partial player supply");
        inv.getItem(0).setCount(3);
        north.setItem(0, new ItemStack(ThaumcraftItems.PLANK_GREATWOOD.get(), 2));
        var menu = new GolemPressMenu(10, inv, press);
        h.assertTrue(menu.clickMenuButton(owner, 0), "Real menu starts one paid default wood");
        h.assertTrue(west.isEmpty() && north.isEmpty() && inv.getItem(0).getCount() == 3 && inv.getItem(1).isEmpty()
                && top.getItem(0).getCount() == 1, "Adjacent totals first; complete mechanism component paid from player");
        ingredients(inv, 1);
        h.assertTrue(!menu.clickMenuButton(owner, 0) && inv.getItem(0).getCount() == 3 && menu.remaining() == 8,
                "Busy button replay cannot reset job or spend twice");
        inv.clearContent(); h.succeed();
    }

    @GameTest(template="golem_test", batch="press_guards", timeoutTicks=40)
    public static void menuAuthorityOutputGuardAndInventoryBoundaryRejectUnpaidStarts(GameTestHelper h) {
        var press = press(h); var owner = player(h); ingredients(owner.getInventory(), 2);
        var menu = new GolemPressMenu(11, owner.getInventory(), press); var other = player(h);
        h.assertTrue(!menu.clickMenuButton(other, 0) && !menu.clickMenuButton(owner, -1), "Viewer identity and action checked");
        press.setItem(0, new ItemStack(Items.DIAMOND));
        h.assertTrue(!menu.clickMenuButton(owner, 0) && owner.getInventory().getItem(0).getCount() == 6, "Foreign output costs nothing");
        press.setItem(0, GolemPlacerItem.stack(GolemDesign.defaultWood(), 0)); press.getItem(0).setCount(64);
        h.assertTrue(!menu.clickMenuButton(owner, 0), "Full matching output cannot start");
        press.clearContent(); owner.setGameMode(GameType.SPECTATOR);
        h.assertTrue(!menu.clickMenuButton(owner, 0), "Spectator cannot pay/start");
        owner.setGameMode(GameType.SURVIVAL); owner.moveTo(Vec3.atBottomCenterOf(h.absolutePos(POS).offset(20, 0, 0)));
        h.assertTrue(!menu.clickMenuButton(owner, 0), "Distance rechecked");
        owner.moveTo(Vec3.atBottomCenterOf(h.absolutePos(POS).east()));
        h.setBlock(POS, Blocks.AIR); h.setBlock(POS, GolemModule.PRESS.get());
        h.assertTrue(!menu.clickMenuButton(owner, 0), "Replaced block entity invalidates old menu");
        owner.getInventory().clearContent(); h.succeed();
    }

    @GameTest(template="golem_test", batch="press_real_cycle", timeoutTicks=120)
    public static void actualServerTicksDebitJarEightMachinaAndEmitExactlyOneUsableGolem(GameTestHelper h) {
        var press = press(h); var owner = player(h); ingredients(owner.getInventory(), 1);
        var jar = jar(h, POS.below(), "machina", 10);
        h.assertTrue(press.start(owner) && owner.getInventory().isEmpty(), "Six components paid exactly once");
        h.assertTrue(press.transportSuction(Direction.DOWN) == 128 && press.transportSuction(Direction.UP) == 0, "Source suction and top exclusion");
        h.onEachTick(() -> h.assertTrue(jar.snapshot().amount() + (press.job().active()
                        ? 8 - press.job().remaining() + (press.job().buffered() ? 1 : 0) : 8) == 10,
                "Exact source debit equals consumed or buffered Machina throughout the cycle"));
        h.startSequence().thenIdle(44).thenExecute(() -> {
            h.assertTrue(jar.snapshot().amount() == 2 && !press.job().active() && press.getItem(0).getCount() == 1
                    && ItemStack.isSameItemSameComponents(press.getItem(0), GolemPlacerItem.stack(GolemDesign.defaultWood(), 0)),
                    "Actual tick loop converts8 Machina into one exact props/xp product");
        }).thenIdle(25).thenExecute(() -> {
            h.assertTrue(jar.snapshot().amount() == 2 && press.getItem(0).getCount() == 1, "Idle job cannot replay output");
            var handler = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(POS), Direction.SOUTH);
            h.assertTrue(handler != null && handler.insertItem(0, new ItemStack(Items.DIRT), false).getCount() == 1, "Output-only capability rejects insertion");
            ItemStack product = handler.extractItem(0, 64, false);
            h.assertTrue(product.is(GolemModule.GOLEM_PLACER.get()) && product.getCount() == 1 && press.isEmpty(), "Sided extraction obtains one paid golem");
            BlockPos support = h.absolutePos(new BlockPos(8, 1, 8));
            h.getLevel().setBlockAndUpdate(support, Blocks.STONE.defaultBlockState());
            owner.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, product);
            var placed = GolemModule.GOLEM_PLACER.get().useOn(new net.minecraft.world.item.context.UseOnContext(owner,
                    net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.phys.BlockHitResult(
                    Vec3.atCenterOf(support).add(0, .5, 0), Direction.UP, support, false)));
            h.assertTrue(placed.consumesAction() && product.isEmpty(), "Real paid placer spawns and is consumed");
            var spawned = h.getLevel().getEntitiesOfClass(dev.ehtid.thaumcraft.entity.ThaumcraftGolem.class,
                    new net.minecraft.world.phys.AABB(support.above()).inflate(.2));
            h.assertTrue(spawned.size() == 1 && spawned.getFirst().getMaxHealth() == 16 && !spawned.getFirst().isNoAi(),
                    "Exactly one live16HP wood golem with enabled AI");
            owner.getInventory().clearContent();
        }).thenSucceed();
    }

    @GameTest(template="golem_test", batch="press_blocked", timeoutTicks=120)
    public static void blockedCompletionWaitsWithoutDiscardingOrChargingAgain(GameTestHelper h) {
        var press = press(h); var owner = player(h); ingredients(owner.getInventory(), 1);
        var jar = jar(h, POS.below(), "machina", 10); h.assertTrue(press.start(owner), "Paid start");
        press.setItem(0, new ItemStack(Items.EMERALD)); // External interference after start.
        h.startSequence().thenIdle(45).thenExecute(() -> {
            h.assertTrue(press.job().ready() && jar.snapshot().amount() == 2 && press.getItem(0).is(Items.EMERALD), "Paid result retained when blocked");
            ingredients(owner.getInventory(), 1);
            h.assertTrue(!press.start(owner) && owner.getInventory().getItem(2).getCount() == 1, "Ready job cannot be overwritten");
        }).thenIdle(15).thenExecute(() -> {
            h.assertTrue(jar.snapshot().amount() == 2 && press.job().ready(), "Blocked job draws no additional Machina");
            press.removeItem(0, 1);
        }).thenIdle(6).thenExecute(() -> {
            h.assertTrue(!press.job().active() && press.getItem(0).is(GolemModule.GOLEM_PLACER.get()) && jar.snapshot().amount() == 2,
                    "Free output slot commits already paid product without a second charge");
            owner.getInventory().clearContent();
        }).thenSucceed();
    }

    @GameTest(template="golem_test", batch="press_buffer", timeoutTicks=40)
    public static void oneUnitBufferAndPaidJobSurviveNbtWhileMalformedJobsCannotCreateOutput(GameTestHelper h) {
        var press = press(h); var owner = player(h); ingredients(owner.getInventory(), 1); h.assertTrue(press.start(owner), "Paid job");
        h.assertTrue(press.addTransport("aer", 1, Direction.DOWN) == 0 && press.addTransport("machina", 0, Direction.DOWN) == 0
                && press.addTransport("machina", 1, Direction.UP) == 0, "Wrong aspect/zero/top cannot credit");
        h.assertTrue(press.addTransport("machina", 9, Direction.DOWN) == 1 && press.addTransport("machina", 1, Direction.DOWN) == 0,
                "Exactly one buffered credit; caller must debit actual accepted amount");
        var saved = press.saveWithoutMetadata(h.getLevel().registryAccess());
        var restored = new GolemPressBlockEntity(h.absolutePos(POS), press.getBlockState());
        restored.loadWithComponents(saved, h.getLevel().registryAccess());
        h.assertTrue(restored.job().equals(press.job()) && restored.job().buffered(), "Buffer persisted in addition to original cost/props");
        saved.putInt("cost", -1); restored.loadWithComponents(saved, h.getLevel().registryAccess());
        h.assertTrue(!restored.job().active() && restored.isEmpty(), "Negative or unsupported saved jobs never become free products");
        owner.getInventory().clearContent(); h.succeed();
    }

    @GameTest(template="golem_test", batch="press_wrong_source", timeoutTicks=80)
    public static void wrongEssentiaStallsAndRemovalDropsOutputExactlyOnce(GameTestHelper h) {
        var press = press(h); var owner = player(h); ingredients(owner.getInventory(), 1);
        var jar = jar(h, POS.below(), "aer", 10); h.assertTrue(press.start(owner), "Paid job");
        h.startSequence().thenIdle(30).thenExecute(() -> {
            h.assertTrue(jar.snapshot().amount() == 10 && press.job().remaining() == 8 && press.isEmpty(), "Wrong source cannot progress");
            press.setItem(0, GolemPlacerItem.stack(GolemDesign.defaultWood(), 0));
            h.setBlock(POS, Blocks.AIR);
            long count = h.getLevel().getEntitiesOfClass(ItemEntity.class, h.getBounds()).stream()
                    .filter(e -> e.getItem().is(GolemModule.GOLEM_PLACER.get())).mapToLong(e -> e.getItem().getCount()).sum();
            h.assertTrue(count == 1 && press.isEmpty(), "Removal drops exactly the real stored product, no unfinished refund/output");
            owner.getInventory().clearContent();
        }).thenSucceed();
    }

    private static GolemPressBlockEntity press(GameTestHelper h) {
        h.setBlock(POS, GolemModule.PRESS.get()); return (GolemPressBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(POS));
    }
    private static FakePlayer player(GameTestHelper h) {
        var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "PressTester"));
        player.setGameMode(GameType.SURVIVAL); player.getInventory().clearContent(); player.moveTo(Vec3.atBottomCenterOf(h.absolutePos(POS).east())); return player;
    }
    public static void ingredients(Container inventory, int copies) {
        inventory.setItem(0, new ItemStack(ThaumcraftItems.PLANK_GREATWOOD.get(), 3 * copies));
        inventory.setItem(1, new ItemStack(GolemModule.SIMPLE_MECHANISM.get(), 2 * copies));
        inventory.setItem(2, new ItemStack(GolemModule.CLOCKWORK_MIND.get(), copies));
    }
    private static EssentiaJarBlockEntity jar(GameTestHelper h, BlockPos pos, String aspect, int count) {
        h.setBlock(pos, ThaumcraftBlocks.JAR_NORMAL.get());
        var jar = (EssentiaJarBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(pos));
        for (int i = 0; i < count; i++) h.assertTrue(jar.addFromTransport(aspect, 1, Direction.UP) == 1, "Fixture physical source credit");
        return jar;
    }
}
