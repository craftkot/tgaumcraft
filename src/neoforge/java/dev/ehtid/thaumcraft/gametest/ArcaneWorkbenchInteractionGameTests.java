package dev.ehtid.thaumcraft.gametest;

import com.mojang.authlib.GameProfile;
import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.blockentity.ArcaneWorkbenchBlockEntity;
import dev.ehtid.thaumcraft.menu.ArcaneWorkbenchMenu;
import dev.ehtid.thaumcraft.registry.ThaumcraftBlocks;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import org.jetbrains.annotations.Nullable;

/** BETA26 Arcane Workbench interaction must not depend on an empty hand. */
@GameTestHolder(ThaumcraftPort.MOD_ID)
public final class ArcaneWorkbenchInteractionGameTests {
    private ArcaneWorkbenchInteractionGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(
            template = "crystal_spread_empty",
            templateNamespace = ThaumcraftPort.MOD_ID,
            timeoutTicks = 40
    )
    public static void heldItemStillOpensWorkbenchMenu(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        helper.setBlock(pos, ThaumcraftBlocks.ARCANE_WORKBENCH.get().defaultBlockState());
        BlockState state = level.getBlockState(pos);
        if (!(level.getBlockEntity(pos) instanceof ArcaneWorkbenchBlockEntity)) {
            throw new AssertionError("Arcane Workbench test fixture did not create its block entity");
        }

        ServerPlayer player = createConnectedTestPlayer(level, pos);
        try {
            ItemStack held = new ItemStack(Items.STICK);
            player.setItemInHand(InteractionHand.MAIN_HAND, held);

            BlockHitResult hit = new BlockHitResult(
                    Vec3.atCenterOf(pos), Direction.UP, pos, false);
            // BlockStateBase.useItemOn is the item-held block interaction dispatch
            // used by the server's right-click-block path in Minecraft 1.21.1.
            state.useItemOn(held, level, player, InteractionHand.MAIN_HAND, hit);

            if (!(player.containerMenu instanceof ArcaneWorkbenchMenu)) {
                throw new AssertionError(
                        "BETA26 Arcane Workbench did not open when the player held a stick; menu="
                                + player.containerMenu.getClass().getName());
            }
            ItemStack remaining = player.getItemInHand(InteractionHand.MAIN_HAND);
            if (!remaining.is(Items.STICK) || remaining.getCount() != 1) {
                throw new AssertionError(
                        "Opening the Arcane Workbench consumed or replaced the held item: " + remaining);
            }

            System.out.println("TC_GAMETEST_WORKBENCH_HELD_ITEM_OPEN=PASS item_preserved=YES");
        } finally {
            player.closeContainer();
            player.connection.disconnect(Component.literal("Workbench GameTest finished"));
        }
        helper.succeed();
    }

    /**
     * Mirrors NeoForge 21.1.1 ExtendedGameTestHelper's connected mock-player
     * setup. The vanilla GameTestHelper factory does not configure NeoForge
     * payload channels before PlayerList.placeNewPlayer; a FakePlayer avoids
     * login but its openMenu implementation deliberately returns empty.
     */
    private static ServerPlayer createConnectedTestPlayer(ServerLevel level, BlockPos target) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(
                new GameProfile(UUID.randomUUID(), "workbench-gametest"), false);
        ServerPlayer player = new ServerPlayer(
                level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND) {
            @Override
            public void tick() {
                super.tick();
                player.resetLastActionTime();
            }

            @Override
            public boolean isMemoryConnection() {
                return true;
            }

            @Override
            public void send(Packet<?> packet, @Nullable PacketSendListener listener, boolean flush) {
                super.send(packet, listener, flush);
                if (packet instanceof ClientboundKeepAlivePacket keepAlive) {
                    player.connection.handleKeepAlive(new ServerboundKeepAlivePacket(keepAlive.getId()));
                }
            }
        };

        new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        level.getServer().getConnection().getConnections().add(connection);

        player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        player.setPos(target.getX() + 0.5D, target.getY() + 1.0D, target.getZ() + 2.5D);
        player.setYRot(180.0F);
        level.getChunkSource().move(player);
        player.connection.chunkSender.sendNextChunks(player);
        player.connection.chunkSender.onChunkBatchReceivedByClient(64.0F);
        return player;
    }
}
