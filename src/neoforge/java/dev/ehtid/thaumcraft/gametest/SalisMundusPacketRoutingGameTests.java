package dev.ehtid.thaumcraft.gametest;

import com.mojang.authlib.GameProfile;
import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.item.SalisSparkleTrailQueue;
import dev.ehtid.thaumcraft.registry.SalisSparkleParticleRegistration;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import org.jetbrains.annotations.Nullable;

/**
 * Native ServerLevel packet-routing regression for BETA26 actor-only Salis FX.
 *
 * Both recipients are real ServerPlayer instances with independent Netty
 * Connection pipelines. EmbeddedChannel keeps the test deterministic; this
 * proves server packet routing, not two separately rendered client processes.
 */
@GameTestHolder(ThaumcraftPort.MOD_ID)
public final class SalisMundusPacketRoutingGameTests {
    private static final String ACTOR_HANDLER = "thaumcraft_salis_actor_packet_probe";
    private static final String BYSTANDER_HANDLER = "thaumcraft_salis_bystander_packet_probe";

    private SalisMundusPacketRoutingGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(
            template = "crystal_spread_empty",
            templateNamespace = ThaumcraftPort.MOD_ID,
            timeoutTicks = 80,
            required = true
    )
    public static void routesSalisParticlesOnlyToTheSelectedPlayer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos target = helper.absolutePos(new BlockPos(8, 2, 8));
        Vec3 origin = Vec3.atCenterOf(target);

        ConnectedPlayer actor = createConnectedPlayer(level, target, "salis-actor");
        ConnectedPlayer bystander = createConnectedPlayer(level, target, "salis-bystander");
        AtomicInteger actorPackets = new AtomicInteger();
        AtomicInteger bystanderPackets = new AtomicInteger();

        try {
            installRecorder(actor, ACTOR_HANDLER, origin, actorPackets);
            installRecorder(bystander, BYSTANDER_HANDLER, origin, bystanderPackets);
            flush(actor.channel());
            flush(bystander.channel());

            // Positive control: the ordinary broadcast overload must reach both
            // connected sessions, proving that neither recorder is a dead channel.
            level.sendParticles(
                    SalisSparkleParticleRegistration.TYPE,
                    origin.x, origin.y, origin.z,
                    0, 0.0D, 0.0D, 0.0D, 1.0D
            );
            flush(actor.channel());
            flush(bystander.channel());
            if (actorPackets.get() != 1 || bystanderPackets.get() != 1) {
                throw new AssertionError("Salis packet recorders failed broadcast positive control"
                        + " actor=" + actorPackets.get()
                        + " bystander=" + bystanderPackets.get());
            }

            // The production queue's player-targeted send must increment only
            // the selected actor's actual outbound Connection pipeline.
            SalisSparkleTrailQueue.emitOrQueue(
                    level, actor.player(), SalisSparkleParticleRegistration.TYPE,
                    origin, Vec3.ZERO, 0
            );
            flush(actor.channel());
            flush(bystander.channel());
            if (actorPackets.get() != 2 || bystanderPackets.get() != 1) {
                throw new AssertionError("Salis actor packet leaked to the bystander or was not delivered"
                        + " actor=" + actorPackets.get()
                        + " bystander=" + bystanderPackets.get());
            }

            // Reverse the recipient as a second positive control. This rejects
            // a recorder accidentally attached only to the first player.
            SalisSparkleTrailQueue.emitOrQueue(
                    level, bystander.player(), SalisSparkleParticleRegistration.TYPE,
                    origin, Vec3.ZERO, 0
            );
            flush(actor.channel());
            flush(bystander.channel());
            if (actorPackets.get() != 2 || bystanderPackets.get() != 2) {
                throw new AssertionError("Salis bystander-targeted control reached the wrong connection"
                        + " actor=" + actorPackets.get()
                        + " bystander=" + bystanderPackets.get());
            }

            System.out.println(
                    "TC_GAMETEST_SALIS_PACKET_RECIPIENT=PASS connections=2 broadcast=2 actor_only=YES"
            );
        } finally {
            removeRecorder(actor, ACTOR_HANDLER);
            removeRecorder(bystander, BYSTANDER_HANDLER);
            disconnect(level, actor, "Salis actor packet-routing GameTest finished");
            disconnect(level, bystander, "Salis bystander packet-routing GameTest finished");
        }
        helper.succeed();
    }

    private static ConnectedPlayer createConnectedPlayer(ServerLevel level, BlockPos target, String name) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(
                new GameProfile(UUID.randomUUID(), name), false
        );
        ServerPlayer player = new ServerPlayer(
                level.getServer(), level, cookie.gameProfile(), cookie.clientInformation()
        );
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
                    player.connection.handleKeepAlive(
                            new ServerboundKeepAlivePacket(keepAlive.getId())
                    );
                }
            }
        };

        EmbeddedChannel channel = new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        level.getServer().getConnection().getConnections().add(connection);

        player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        player.setPos(target.getX() + 0.5D, target.getY() + 1.0D, target.getZ() + 0.5D);
        level.getChunkSource().move(player);
        player.connection.chunkSender.sendNextChunks(player);
        player.connection.chunkSender.onChunkBatchReceivedByClient(64.0F);
        return new ConnectedPlayer(player, connection, channel);
    }

    private static void installRecorder(
            ConnectedPlayer recipient,
            String name,
            Vec3 origin,
            AtomicInteger counter
    ) {
        EmbeddedChannel channel = recipient.channel();
        if (!channel.isActive()) {
            throw new AssertionError("Salis recipient connection is not active: " + name);
        }
        Runnable install = () -> channel.pipeline().addBefore(
                "packet_handler",
                name,
                new ChannelOutboundHandlerAdapter() {
                    @Override
                    public void write(ChannelHandlerContext context, Object message, ChannelPromise promise)
                            throws Exception {
                        if (message instanceof ClientboundLevelParticlesPacket packet
                                && packet.getParticle() == SalisSparkleParticleRegistration.TYPE
                                && packet.getCount() == 0
                                && Double.compare(packet.getX(), origin.x) == 0
                                && Double.compare(packet.getY(), origin.y) == 0
                                && Double.compare(packet.getZ(), origin.z) == 0) {
                            counter.incrementAndGet();
                        }
                        context.write(message, promise);
                    }
                }
        );
        if (channel.eventLoop().inEventLoop()) {
            install.run();
        } else {
            channel.eventLoop().submit(install).syncUninterruptibly();
        }
        if (channel.pipeline().get(name) == null) {
            throw new AssertionError("Salis packet recorder was not installed: " + name);
        }
    }

    private static void flush(EmbeddedChannel channel) {
        channel.runPendingTasks();
        channel.flushOutbound();
        channel.runPendingTasks();
    }

    private static void removeRecorder(ConnectedPlayer recipient, String name) {
        EmbeddedChannel channel = recipient.channel();
        if (!channel.isOpen()) return;
        Runnable remove = () -> {
            if (channel.pipeline().get(name) != null) channel.pipeline().remove(name);
        };
        if (channel.eventLoop().inEventLoop()) {
            remove.run();
        } else {
            channel.eventLoop().submit(remove).syncUninterruptibly();
        }
    }

    private static void disconnect(ServerLevel level, ConnectedPlayer recipient, String reason) {
        try {
            recipient.player().connection.disconnect(Component.literal(reason));
        } finally {
            level.getServer().getConnection().getConnections().remove(recipient.connection());
            recipient.channel().finishAndReleaseAll();
        }
    }

    private record ConnectedPlayer(
            ServerPlayer player,
            Connection connection,
            EmbeddedChannel channel
    ) {}
}
