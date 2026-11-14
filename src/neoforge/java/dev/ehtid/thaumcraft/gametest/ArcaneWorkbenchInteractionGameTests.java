package dev.ehtid.thaumcraft.gametest;

import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.menu.ArcaneWorkbenchMenu;
import dev.ehtid.thaumcraft.registry.ThaumcraftBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** BETA26 Arcane Workbench interaction must not depend on an empty hand. */
@GameTestHolder(ThaumcraftPort.MOD_ID)
public final class ArcaneWorkbenchInteractionGameTests {
    private ArcaneWorkbenchInteractionGameTests() {}

    @SuppressWarnings("deprecation")
    @PrefixGameTestTemplate(false)
    @GameTest(
            template = "crystal_spread_empty",
            templateNamespace = ThaumcraftPort.MOD_ID,
            timeoutTicks = 40
    )
    public static void heldItemStillOpensWorkbenchMenu(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockState state = ThaumcraftBlocks.ARCANE_WORKBENCH.get().defaultBlockState();
        helper.setBlock(pos, state);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setPos(pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 2.5D);
        ItemStack held = new ItemStack(Items.STICK);
        player.setItemInHand(InteractionHand.MAIN_HAND, held);

        BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(pos), Direction.UP, pos, false);
        // BlockStateBase.useItemOn is the same item-held interaction dispatch
        // used by the server's right-click-block path in Minecraft 1.21.1.
        state.useItemOn(held, level, player, InteractionHand.MAIN_HAND, hit);

        if (!(player.containerMenu instanceof ArcaneWorkbenchMenu)) {
            throw new AssertionError(
                    "BETA26 Arcane Workbench did not open when the player held a stick");
        }
        ItemStack remaining = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (!remaining.is(Items.STICK) || remaining.getCount() != 1) {
            throw new AssertionError(
                    "Opening the Arcane Workbench consumed or replaced the held item: " + remaining);
        }

        System.out.println("TC_GAMETEST_WORKBENCH_HELD_ITEM_OPEN=PASS item_preserved=YES");
        player.closeContainer();
        helper.succeed();
    }
}
