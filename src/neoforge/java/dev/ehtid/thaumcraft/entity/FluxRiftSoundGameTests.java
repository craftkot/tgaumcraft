package dev.ehtid.thaumcraft.entity;

import com.mojang.authlib.GameProfile;
import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.registry.FluxRiftRegistration;
import dev.ehtid.thaumcraft.registry.WispRegistration;
import dev.ehtid.thaumcraft.entity.WispEntity;
import dev.ehtid.thaumcraft.registry.ThaumcraftSounds;
import dev.ehtid.thaumcraft.registry.WispSoundRegistration;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
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
        ConnectedPlayer recipient = createConnectedPlayer(level, pos, "rift-sound-recipient");
        FluxRiftEntity rift = new FluxRiftEntity(FluxRiftRegistration.get(), level);
        rift.setRiftSeed(1234567);
        rift.setRiftSize(5);
        rift.moveTo(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 0.0F, 0.0F);
        if (!level.addFreshEntity(rift)) {
            disconnect(level, recipient, "Flux Rift sound fixture could not be added");
            throw new AssertionError("Unable to register Flux Rift sound fixture");
        }

        long seed = FluxRiftEntity.smokeAmbientSoundSeed();
        if (seed != FluxRiftEntity.smokeAmbientSoundSeed()) {
            throw new AssertionError("BETA26 smoke sound seed must be repeatable");
        }
        RandomSource expectedRandom = RandomSource.create(seed);
        float expectedVolume = (float) (0.15F + expectedRandom.nextGaussian() * 0.066D);
        float expectedPitch = (float) (0.75D + expectedRandom.nextGaussian() * 0.1D);
        if (!(expectedVolume > 0.05F && expectedVolume < 0.35F
                && expectedPitch > 0.2F && expectedPitch < 1.3F)) {
            throw new AssertionError("Deterministic BETA26 smoke sample is not audible: volume="
                    + expectedVolume + " pitch=" + expectedPitch);
        }
        ConcurrentLinkedQueue<SoundPacketEvidence> observed = new ConcurrentLinkedQueue<>();
        try {
            installRecorder(recipient, observed);
            flush(recipient.channel());
        } catch (RuntimeException | Error failure) {
            disconnect(level, recipient, "Flux Rift sound recorder setup failed");
            rift.discard();
            throw failure;
        }

        // BETA26 Entity.playSound routes through World.playSound at the entity
        // position. Accept either modern position or entity packet encoding.
        helper.runAfterDelay(2, () -> {
            try {
                Vec3 positionBefore299 = rift.position();
                Vec3 packetPositionBefore299 = quantizeSoundPacketPosition(positionBefore299);
                rift.tickCount = 299;
                rift.playAmbientSoundIfDue(RandomSource.create(1L));
                flush(recipient.channel());
                if (countPacketsFromRift(observed, rift.getId(), packetPositionBefore299) != 0) {
                    throw new AssertionError("BETA26 Flux Rift ambience must not play at tick 299: entityPosition="
                            + positionBefore299 + " packetPosition=" + packetPositionBefore299
                            + " current=" + rift.position() + " observed=" + describePackets(observed));
                }

                Vec3 soundPosition = rift.position();
                Vec3 soundPacketPosition = quantizeSoundPacketPosition(soundPosition);
                rift.tickCount = 300;
                rift.playAmbientSoundIfDue(RandomSource.create(seed));
                flush(recipient.channel());

                int packetsAt300 = countPacketsFromRift(observed, rift.getId(), soundPacketPosition);
                SoundPacketEvidence packet = findPacketFromRift(observed, rift.getId(), soundPacketPosition);
                if (packetsAt300 != 1 || packet == null) {
                    throw new AssertionError("BETA26 Flux Rift ambience packet missing/duplicated at tick 300: count="
                            + packetsAt300 + " entityPosition=" + soundPosition
                            + " packetPosition=" + soundPacketPosition + " current=" + rift.position()
                            + " recipient=" + recipient.player().position()
                            + " entityId=" + rift.getId() + " observed=" + describePackets(observed));
                }
                if (packet.source() != SoundSource.NEUTRAL
                        || Float.compare(packet.volume(), expectedVolume) != 0
                        || Float.compare(packet.pitch(), expectedPitch) != 0) {
                    throw new AssertionError("BETA26 Flux Rift ambience packet mismatch: source="
                            + packet.source() + " volume=" + packet.volume()
                            + " pitch=" + packet.pitch());
                }
                if (packet.entityId() >= 0 && packet.entityId() != rift.getId()) {
                    throw new AssertionError("Flux Rift ambience was attached to the wrong entity");
                }
                if (packet.entityId() < 0
                        && (Math.abs(packet.x() - soundPacketPosition.x) > 0.125D
                        || Math.abs(packet.y() - soundPacketPosition.y) > 0.125D
                        || Math.abs(packet.z() - soundPacketPosition.z) > 0.125D)) {
                    throw new AssertionError("Flux Rift ambience position mismatch: packet=("
                            + packet.x() + "," + packet.y() + "," + packet.z() + ") entityPosition="
                            + soundPosition + " packetPosition=" + soundPacketPosition
                            + " current=" + rift.position()
                            + " recipient=" + recipient.player().position());
                }

                rift.tickCount = 301;
                rift.playAmbientSoundIfDue(RandomSource.create(seed));
                flush(recipient.channel());
                if (countPacketsFromRift(observed, rift.getId(), soundPacketPosition) != 1) {
                    throw new AssertionError("BETA26 Flux Rift ambience must not repeat at tick 301: entityPosition="
                            + soundPosition + " packetPosition=" + soundPacketPosition
                            + " current=" + rift.position() + " observed=" + describePackets(observed));
                }
            } finally {
                removeRecorder(recipient);
                disconnect(level, recipient, "Flux Rift ambient sound GameTest finished");
                rift.discard();
            }
            helper.succeed();
            System.out.println("TC_GAMETEST_FLUX_RIFT_AMBIENT_SOUND=PASS cadence=300 source=NEUTRAL event=evilportal");
        });
    }


    @PrefixGameTestTemplate(false)
    @GameTest(
            template = "crystal_spread_empty",
            templateNamespace = ThaumcraftPort.MOD_ID,
            timeoutTicks = 80
    )
    public static void wispUsesOriginalSoundSurfaceAndEmitsZapPacket(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(8, 2, 8));
        WispEntity wisp = new WispEntity(WispRegistration.get(), level);
        wisp.moveTo(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 0.0F, 0.0F);
        if (!level.addFreshEntity(wisp)) {
            throw new AssertionError("Unable to add Wisp sound fixture");
        }

        if (wisp.getAmbientSound() != WispSoundRegistration.WISP_LIVE.get()
                || wisp.getHurtSound(null) != SoundEvents.LAVA_EXTINGUISH
                || wisp.getDeathSound() != WispSoundRegistration.WISP_DEAD.get()
                || Float.compare(wisp.getSoundVolume(), 0.25F) != 0) {
            wisp.discard();
            throw new AssertionError("Wisp ambient/hurt/death sound contract differs from BETA26");
        }

        ConnectedPlayer recipient = createConnectedPlayer(level, pos, "wisp-sound-recipient");
        ConcurrentLinkedQueue<SoundPacketEvidence> observed = new ConcurrentLinkedQueue<>();
        try {
            installWispZapRecorder(recipient, observed);
            flush(recipient.channel());
        } catch (RuntimeException | Error failure) {
            disconnect(level, recipient, "Wisp zap sound recorder setup failed");
            wisp.discard();
            throw failure;
        }

        helper.runAfterDelay(2, () -> {
            try {
                wisp.playZapSound();
                flush(recipient.channel());
                if (observed.size() != 1) {
                    throw new AssertionError("Expected one BETA26 Wisp zap packet, got "
                            + observed.size() + " packets=" + describePackets(observed));
                }

                SoundPacketEvidence packet = observed.peek();
                if (packet == null || Float.compare(packet.volume(), 1.0F) != 0
                        || Float.compare(packet.pitch(), 1.1F) != 0) {
                    throw new AssertionError("BETA26 Wisp zap packet volume/pitch mismatch: " + packet);
                }
                if (packet.entityId() >= 0 && packet.entityId() != wisp.getId()) {
                    throw new AssertionError("Wisp zap sound packet is attached to the wrong entity");
                }
                if (packet.entityId() < 0
                        && (Math.abs(packet.x() - wisp.getX()) > 0.125D
                        || Math.abs(packet.y() - wisp.getY()) > 0.125D
                        || Math.abs(packet.z() - wisp.getZ()) > 0.125D)) {
                    throw new AssertionError("Wisp zap sound packet position mismatch: " + packet);
                }
            } finally {
                removeWispZapRecorder(recipient);
                disconnect(level, recipient, "Wisp zap sound GameTest finished");
                wisp.discard();
            }
            helper.succeed();
            System.out.println("TC_GAMETEST_WISP_SOUND=PASS ambient=wisplive hurt=lava_extinguish "
                    + "death=wispdead zap=1.0@1.1 volume=0.25");
        });
    }

    private static Vec3 quantizeSoundPacketPosition(Vec3 entityPosition) {
        // The server's sound path converts large world coordinates to float before
        // ClientboundSoundPacket's 1/8-block fixed-point coordinate encoding.
        return new Vec3(
                (double) (float) entityPosition.x,
                (double) (float) entityPosition.y,
                (double) (float) entityPosition.z
        );
    }

    private static int countPacketsFromRift(
            ConcurrentLinkedQueue<SoundPacketEvidence> packets,
            int riftEntityId,
            Vec3 expectedPosition
    ) {
        int count = 0;
        for (SoundPacketEvidence packet : packets) {
            if (isFromRift(packet, riftEntityId, expectedPosition)) count++;
        }
        return count;
    }

    private static SoundPacketEvidence findPacketFromRift(
            ConcurrentLinkedQueue<SoundPacketEvidence> packets,
            int riftEntityId,
            Vec3 expectedPosition
    ) {
        for (SoundPacketEvidence packet : packets) {
            if (isFromRift(packet, riftEntityId, expectedPosition)) return packet;
        }
        return null;
    }

    private static boolean isFromRift(
            SoundPacketEvidence packet,
            int riftEntityId,
            Vec3 expectedPosition
    ) {
        if (packet.entityId() >= 0) return packet.entityId() == riftEntityId;
        if (!Double.isFinite(packet.x()) || !Double.isFinite(packet.y()) || !Double.isFinite(packet.z())) {
            return false;
        }
        // ClientboundSoundPacket stores fixed coordinates at 8 units per block.
        return Math.abs(packet.x() - expectedPosition.x) <= 1.0D / 8.0D
                && Math.abs(packet.y() - expectedPosition.y) <= 1.0D / 8.0D
                && Math.abs(packet.z() - expectedPosition.z) <= 1.0D / 8.0D;
    }

    private static String describePackets(ConcurrentLinkedQueue<SoundPacketEvidence> packets) {
        StringBuilder description = new StringBuilder();
        int shown = 0;
        for (SoundPacketEvidence packet : packets) {
            if (shown++ == 8) {
                description.append("; ...");
                break;
            }
            if (description.length() > 0) description.append("; ");
            description.append(packet);
        }
        return description.toString();
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

        player.gameMode.changeGameModeForPlayer(GameType.CREATIVE);
        player.setPos(target.getX() + 0.5D, target.getY() + 1.0D, target.getZ() + 0.5D);
        level.getChunkSource().move(player);
        player.connection.chunkSender.sendNextChunks(player);
        player.connection.chunkSender.onChunkBatchReceivedByClient(64.0F);
        return new ConnectedPlayer(player, connection, channel);
    }


    private static void installWispZapRecorder(
            ConnectedPlayer recipient,
            ConcurrentLinkedQueue<SoundPacketEvidence> observed
    ) {
        EmbeddedChannel channel = recipient.channel();
        if (!channel.isActive()) {
            throw new AssertionError("Wisp zap sound recipient connection is not active");
        }
        Runnable install = () -> channel.pipeline().addLast(RECORDER, new ChannelOutboundHandlerAdapter() {
            @Override
            public void write(ChannelHandlerContext context, Object message, ChannelPromise promise)
                    throws Exception {
                if (message instanceof ClientboundSoundPacket packet
                        && packet.getSound().value() == WispSoundRegistration.WISP_ZAP.get()) {
                    observed.add(new SoundPacketEvidence(
                            packet.getSource(), packet.getVolume(), packet.getPitch(), -1,
                            packet.getX(), packet.getY(), packet.getZ()
                    ));
                } else if (message instanceof ClientboundSoundEntityPacket packet
                        && packet.getSound().value() == WispSoundRegistration.WISP_ZAP.get()) {
                    observed.add(new SoundPacketEvidence(
                            packet.getSource(), packet.getVolume(), packet.getPitch(), packet.getId(),
                            Double.NaN, Double.NaN, Double.NaN
                    ));
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
            throw new AssertionError("Wisp zap sound packet recorder was not installed");
        }
    }

    private static void removeWispZapRecorder(ConnectedPlayer recipient) {
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

    private static void installRecorder(
            ConnectedPlayer recipient,
            ConcurrentLinkedQueue<SoundPacketEvidence> observed
    ) {
        EmbeddedChannel channel = recipient.channel();
        if (!channel.isActive()) {
            throw new AssertionError("Flux Rift sound recipient connection is not active");
        }
        Runnable install = () -> channel.pipeline().addLast(RECORDER, new ChannelOutboundHandlerAdapter() {
            @Override
            public void write(ChannelHandlerContext context, Object message, ChannelPromise promise)
                    throws Exception {
                if (message instanceof ClientboundSoundPacket packet
                        && packet.getSound().value() == ThaumcraftSounds.EVILPORTAL.get()) {
                    observed.add(new SoundPacketEvidence(
                            packet.getSource(), packet.getVolume(), packet.getPitch(), -1,
                            packet.getX(), packet.getY(), packet.getZ()
                    ));
                } else if (message instanceof ClientboundSoundEntityPacket packet
                        && packet.getSound().value() == ThaumcraftSounds.EVILPORTAL.get()) {
                    observed.add(new SoundPacketEvidence(
                            packet.getSource(), packet.getVolume(), packet.getPitch(), packet.getId(),
                            Double.NaN, Double.NaN, Double.NaN
                    ));
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

    private record SoundPacketEvidence(
            SoundSource source,
            float volume,
            float pitch,
            int entityId,
            double x,
            double y,
            double z
    ) {}

    private record ConnectedPlayer(
            ServerPlayer player,
            Connection connection,
            EmbeddedChannel channel
    ) {}
}
