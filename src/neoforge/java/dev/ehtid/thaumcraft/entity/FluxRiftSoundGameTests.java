package dev.ehtid.thaumcraft.entity;

import com.mojang.authlib.GameProfile;
import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.registry.FluxRiftRegistration;
import dev.ehtid.thaumcraft.registry.ThaumcraftSounds;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameType;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import org.jetbrains.annotations.Nullable;

/** Native packet-level acceptance for the original BETA26 Flux Rift ambience cadence. */
@GameTestHolder(ThaumcraftPort.MOD_ID)
public final class FluxRiftSoundGameTests {
    private static final String RECORDER = "thaumcraft_flux_rift_ambient_sound_probe";

    private FluxRiftSoundGameTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(
            template = "crystal_spread_empty",
            templateNamespace = ThaumcraftPort.MOD_ID,
            timeoutTicks = 80,
            required = false
    )
    public static void emitsOriginalAmbientSoundEvery300Ticks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(8, 2, 8));
        FluxRiftEntity rift = new FluxRiftEntity(FluxRiftRegistration.get(), level);
        rift.setRiftSeed(1234567);
        rift.setRiftSize(5);
        rift.moveTo(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 0.0F, 0.0F);
        if (!level.addFreshEntity(rift)) {
            throw new AssertionError("Unable to register Flux Rift sound fixture");
        }

        ConnectedPlayer recipient = createConnectedPlayer(level, pos, "rift-sound-recipient");
        AtomicInteger matchingPackets = new AtomicInteger();
        AtomicReference<ClientboundSoundEntityPacket> observed = new AtomicReference<>();
        try {
            installRecorder(recipient, rift, matchingPackets, observed);
            flush(recipient.channel());

            rift.tickCount = 299;
            rift.playAmbientSoundIfDue(RandomSource.create(1L));
            flush(recipient.channel());
            if (matchingPackets.get() != 0) {
                throw new AssertionError("BETA26 Flux Rift ambience must not play at tick 299");
            }

            long seed = findPositiveVolumeSeed();
            RandomSource expectedRandom = RandomSource.create(seed);
            float expectedVolume = (float) (0.15F + expectedRandom.nextGaussian() * 0.066D);
            float expectedPitch = (float) (0.75D + expectedRandom.nextGaussian() * 0.1D);

            rift.tickCount = 300;
            rift.playAmbientSoundIfDue(RandomSource.create(seed));
            flush(recipient.channel());

            ClientboundSoundEntityPacket packet = observed.get();
            if (matchingPackets.get() != 1 || packet == null) {
                throw new AssertionError("BETA26 Flux Rift ambience packet missing at tick 300: count="
                        + matchingPackets.get());
            }
            if (packet.getSound().value() != ThaumcraftSounds.EVILPORTAL.get()
                    || packet.getId() != rift.getId()
                    || packet.getSource() != SoundSource.NEUTRAL
                    || Float.compare(packet.getVolume(), expectedVolume) != 0
                    || Float.compare(packet.getPitch(), expectedPitch) != 0) {
                throw new AssertionError("BETA26 Flux Rift ambience packet mismatch: source="
                        + packet.getSource() + " volume=" + packet.getVolume()
                        + " pitch=" + packet.getPitch());
            }

            rift.tickCount = 301;
            rift.playAmbientSoundIfDue(RandomSource.create(seed));
            flush(recipient.channel());
            if (matchingPackets.get() != 1) {
                throw new AssertionError("BETA26 Flux Rift ambience must not repeat at tick 301");
            }

        } finally {
            removeRecorder(recipient);
            disconnect(level, recipient, "Flux Rift ambient sound GameTest finished");
            rift.discard();
        }
        helper.succeed();
        System.out.println("TC_GAMETEST_FLUX_RIFT_AMBIENT_SOUND=PASS cadence=300 source=NEUTRAL event=evilportal");
    }

    private static long findPositiveVolumeSeed() {
        for (long seed = 0; seed < 100_000L; seed++) {
            RandomSource probe = RandomSource.create(seed);
            float volume = (float) (0.15F + probe.nextGaussian() * 0.066D);
            float pitch = (float) (0.75D + probe.nextGaussian() * 0.1D);
            if (volume > 0.05F && volume < 0.35F && pitch > 0.2F && pitch < 1.3F) {
                return seed;
            }
        }
        throw new AssertionError("Unable to find deterministic BETA26 positive-volume sound seed");
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
            FluxRiftEntity rift,
            AtomicInteger count,
            AtomicReference<ClientboundSoundEntityPacket> observed
    ) {
        EmbeddedChannel channel = recipient.channel();
        if (!channel.isActive()) {
            throw new AssertionError("Flux Rift sound recipient connection is not active");
        }
        Runnable install = () -> channel.pipeline().addLast(RECORDER, new ChannelOutboundHandlerAdapter() {
            @Override
            public void write(ChannelHandlerContext context, Object message, ChannelPromise promise)
                    throws Exception {
                if (message instanceof ClientboundSoundEntityPacket packet
                        && packet.getSound().value() == ThaumcraftSounds.EVILPORTAL.get()
                        && packet.getId() == rift.getId()) {
                    count.incrementAndGet();
                    observed.set(packet);
                }
                context.write(message, promise);
            }
        });
        if (channel.eventLoop().inEventLoop()) {
            install.run();
        } else {
            channel.eventLoop().submit(install).syncUninterruptibly();
        }
        if (channel.pipeline().get(RECORDER) == null) {
            throw new AssertionError("Flux Rift sound packet recorder was not installed");
        }
    }

    private static void flush(EmbeddedChannel channel) {
        channel.runPendingTasks();
        channel.flushOutbound();
        channel.runPendingTasks();
    }

    private static void removeRecorder(ConnectedPlayer recipient) {
        EmbeddedChannel channel = recipient.channel();
        if (!channel.isOpen()) return;
        Runnable remove = () -> {
            if (channel.pipeline().get(RECORDER) != null) channel.pipeline().remove(RECORDER);
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
