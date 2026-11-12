package dev.ehtid.thaumcraft.item;

import com.mojang.logging.LogUtils;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.registry.SalisSparkleParticleRegistration;
import dev.ehtid.thaumcraft.util.ThaumcraftSmokeAudit;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import java.util.concurrent.atomic.AtomicInteger;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Opt-in real-client proof for Salis queue cancellation across native chunk unload/reload. */
@EventBusSubscriber(modid = ThaumcraftPort.MOD_ID)
public final class SalisSparkleChunkUnloadProbe {
    private static final boolean ENABLED = Boolean.getBoolean("thaumcraft.salisChunkUnloadScenario");
    private static final int CHUNK_X = 512;
    private static final int CHUNK_Z = 512;
    private static final int DELAY_TICKS = 1200;
    private static final int UNLOAD_TIMEOUT_TICKS = 900;
    private static final BlockPos ORIGIN = new BlockPos(CHUNK_X * 16 + 8, 100, CHUNK_Z * 16 + 8);
    private static final String OUTBOUND_HANDLER_NAME = "thaumcraft_salis_chunk_unload_probe";
    private static final AtomicInteger OUTBOUND_PROBE_PACKET_COUNT = new AtomicInteger();

    private static MinecraftServer active;
    private static ServerLevel targetLevel;
    private static int phase;
    private static long phaseStarted;
    private static long scheduledAt;
    private static long reloadedAt;
    private static int sendsBefore;
    private static int cancellationsBefore;
    private static int enqueuedBefore;
    private static boolean chunkUnloaded;
    private static boolean fixtureTicketHeld;
    private static boolean finished;
    private static volatile Channel outboundProbeChannel;
    private static int outboundPacketsBefore;

    private SalisSparkleChunkUnloadProbe() {}

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        if (!ENABLED) return;
        active = event.getServer();
        targetLevel = null;
        phase = 0;
        phaseStarted = scheduledAt = reloadedAt = 0L;
        sendsBefore = cancellationsBefore = enqueuedBefore = 0;
        chunkUnloaded = false;
        fixtureTicketHeld = false;
        finished = false;
        OUTBOUND_PROBE_PACKET_COUNT.set(0);
        outboundProbeChannel = null;
        outboundPacketsBefore = 0;
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        if (active == event.getServer()) {
            removeOutboundParticleRecorder();
            cleanupFixtureTicketOnStop();
            active = null;
            targetLevel = null;
        }
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (!ENABLED || active == null || targetLevel == null) return;
        if (event.getLevel() == targetLevel
                && event.getChunk().getPos().equals(new ChunkPos(CHUNK_X, CHUNK_Z))) {
            chunkUnloaded = true;
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!ENABLED || active != event.getServer() || finished) return;
        ServerLevel level = active.overworld();
        long now = level.getGameTime();
        try {
            if (phase == 0) {
                ServerPlayer actor = active.getPlayerList().getPlayers().stream()
                        .filter(player -> player.level() == level)
                        .findFirst()
                        .orElse(null);
                if (actor == null
                        || ThaumcraftSmokeAudit.count("BOOKSHELF_SALIS_USED") == 0
                        || SalisSparkleTrailQueue.pendingCountForServer(active) != 0) return;

                installOutboundParticleRecorder(actor);
                outboundPacketsBefore = OUTBOUND_PROBE_PACKET_COUNT.get();
                require(outboundPacketsBefore == 0,
                        "probe-origin Salis packet was emitted before the unload scenario");
                targetLevel = level;
                require(level.setChunkForced(CHUNK_X, CHUNK_Z, true),
                        "fixture could not acquire its one remote chunk ticket");
                fixtureTicketHeld = true;
                level.getChunk(CHUNK_X, CHUNK_Z);
                require(level.getChunkSource().getChunkNow(CHUNK_X, CHUNK_Z) != null,
                        "fixture chunk was not loaded before queue admission");

                sendsBefore = ThaumcraftSmokeAudit.count("SALIS_PARTICLE_SEND_INVOKED");
                cancellationsBefore = ThaumcraftSmokeAudit.count("SALIS_DELAY_UNLOADED_CANCELLED");
                enqueuedBefore = ThaumcraftSmokeAudit.count("SALIS_DELAY_ENQUEUED");
                SalisSparkleTrailQueue.emitFaceOrQueue(level, actor,
                        SalisSparkleParticleRegistration.TYPE, Vec3.atCenterOf(ORIGIN),
                        new Vec3(2000.0D, 0.0025D, 0.0D), DELAY_TICKS);
                require(ThaumcraftSmokeAudit.count("SALIS_DELAY_ENQUEUED") == enqueuedBefore + 1,
                        "long-delay Salis face particle was not admitted");
                require(SalisSparkleTrailQueue.pendingCountForServer(active) == 1,
                        "expected exactly one pending Salis effect for this server");

                scheduledAt = now;
                require(releaseFixtureTicket(),
                        "fixture could not release its only remote chunk ticket");
                ThaumcraftSmokeAudit.mark("SALIS_NATIVE_CHUNK_TICKETS_RELEASED");
                phaseStarted = now;
                phase = 1;
                return;
            }

            if (phase == 1) {
                require(now - phaseStarted < UNLOAD_TIMEOUT_TICKS,
                        "remote chunk did not unload after releasing the fixture ticket");
                if (!chunkUnloaded
                        || level.getChunkSource().getChunkNow(CHUNK_X, CHUNK_Z) != null
                        || level.areEntitiesLoaded(ChunkPos.asLong(CHUNK_X, CHUNK_Z))) return;
                require(ThaumcraftSmokeAudit.count("SALIS_DELAY_UNLOADED_CANCELLED") == cancellationsBefore + 1,
                        "native unload did not cancel exactly the pending Salis effect");
                ThaumcraftSmokeAudit.mark("SALIS_NATIVE_CHUNK_UNLOADED");
                ThaumcraftSmokeAudit.mark("SALIS_NATIVE_CHUNK_CANCELLED");
                phase = 2;
                return;
            }

            if (phase == 2) {
                require(level.getChunkSource().getChunkNow(CHUNK_X, CHUNK_Z) == null
                                && !level.areEntitiesLoaded(ChunkPos.asLong(CHUNK_X, CHUNK_Z)),
                        "remote chunk loaded before the Salis due tick");
                require(ThaumcraftSmokeAudit.count("SALIS_PARTICLE_SEND_INVOKED") == sendsBefore,
                        "a Salis particle send occurred after the origin chunk unloaded");
                if (now - scheduledAt < DELAY_TICKS + 10L) return;
                require(ThaumcraftSmokeAudit.count("SALIS_DELAY_UNLOADED_CANCELLED") == cancellationsBefore + 1,
                        "unload cancellation count changed while chunk stayed absent");
                require(SalisSparkleTrailQueue.pendingCountForServer(active) == 0,
                        "cancelled Salis effect remained queued");
                require(OUTBOUND_PROBE_PACKET_COUNT.get() == outboundPacketsBefore,
                        "cancelled Salis effect produced an outbound particle packet while its origin chunk was unloaded");
                ThaumcraftSmokeAudit.mark("SALIS_NATIVE_NO_OUTBOUND_PACKET_AFTER_UNLOAD");
                require(level.setChunkForced(CHUNK_X, CHUNK_Z, true),
                        "fixture could not reacquire remote chunk ticket");
                fixtureTicketHeld = true;
                level.getChunk(CHUNK_X, CHUNK_Z);
                require(level.getChunkSource().getChunkNow(CHUNK_X, CHUNK_Z) != null,
                        "remote chunk did not reload under fixture ticket");
                reloadedAt = now;
                ThaumcraftSmokeAudit.mark("SALIS_NATIVE_CHUNK_RELOADED");
                phase = 3;
                return;
            }

            if (phase == 3) {
                if (now - reloadedAt < 10L) return;
                require(ThaumcraftSmokeAudit.count("SALIS_PARTICLE_SEND_INVOKED") == sendsBefore,
                        "cancelled Salis effect replayed after reload");
                require(ThaumcraftSmokeAudit.count("SALIS_DELAY_UNLOADED_CANCELLED") == cancellationsBefore + 1,
                        "expected exactly one native unload cancellation");
                require(SalisSparkleTrailQueue.pendingCountForServer(active) == 0,
                        "Salis queue retained a stale effect after reload");
                require(OUTBOUND_PROBE_PACKET_COUNT.get() == outboundPacketsBefore,
                        "cancelled Salis effect emitted a real outbound particle packet after chunk reload");
                ThaumcraftSmokeAudit.mark("SALIS_NATIVE_NO_OUTBOUND_PACKET_AFTER_RELOAD");
                removeOutboundParticleRecorder();
                ThaumcraftSmokeAudit.mark("SALIS_NATIVE_OUTBOUND_PACKET_RECORDER_REMOVED");
                require(releaseFixtureTicket(),
                        "fixture could not release its final remote chunk ticket after reload");
                ThaumcraftSmokeAudit.mark("SALIS_NATIVE_CHUNK_TICKET_CLEANED");
                ThaumcraftSmokeAudit.mark("SALIS_NATIVE_CHUNK_RELOADED_NO_REPLAY");
                ThaumcraftSmokeAudit.mark("SALIS_NATIVE_CHUNK_UNLOAD_ACCEPTED");
                finished = true;
                LogUtils.getLogger().info("SALIS_NATIVE_CHUNK_UNLOAD_ASSERTIONS_OK");
                active.halt(false);
            }
        } catch (RuntimeException | AssertionError ex) {
            removeOutboundParticleRecorder();
            cleanupFixtureTicketAfterFailure();
            finished = true;
            ThaumcraftSmokeAudit.mark("SALIS_NATIVE_CHUNK_UNLOAD_FAILED");
            LogUtils.getLogger().error("SALIS_NATIVE_CHUNK_UNLOAD_ASSERTIONS_FAILED", ex);
            active.halt(false);
        }
    }

    private static void installOutboundParticleRecorder(ServerPlayer actor) {
        Connection connection = actor.connection.getConnection();
        Channel channel = connection.channel();
        require(channel != null && channel.isActive(),
                "actor connection has no active Netty channel for outbound packet capture");
        Runnable install = () -> {
            var pipeline = channel.pipeline();
            if (pipeline.get(OUTBOUND_HANDLER_NAME) != null) {
                pipeline.remove(OUTBOUND_HANDLER_NAME);
            }
            pipeline.addBefore("packet_handler", OUTBOUND_HANDLER_NAME, new ChannelOutboundHandlerAdapter() {
                @Override
                public void write(ChannelHandlerContext context, Object message, ChannelPromise promise)
                        throws Exception {
                    if (message instanceof ClientboundLevelParticlesPacket packet
                            && isProbeParticlePacket(packet)) {
                        int count = OUTBOUND_PROBE_PACKET_COUNT.incrementAndGet();
                        System.out.println("TC_SMOKE_SALIS_OUTBOUND_PACKET=YES count=" + count
                                + " x=" + packet.getX()
                                + " y=" + packet.getY()
                                + " z=" + packet.getZ()
                                + " particle=thaumcraft:salis_sparkle"
                                + " particle_count=" + packet.getCount()
                                + " long_distance=" + packet.isLongDistance());
                    }
                    context.write(message, promise);
                }
            });
        };
        if (channel.eventLoop().inEventLoop()) {
            install.run();
        } else {
            channel.eventLoop().submit(install).syncUninterruptibly();
        }
        outboundProbeChannel = channel;
        require(channel.pipeline().get(OUTBOUND_HANDLER_NAME) != null,
                "outbound Salis packet recorder was not installed");
        ThaumcraftSmokeAudit.mark("SALIS_NATIVE_OUTBOUND_PACKET_RECORDER_INSTALLED");
    }

    private static boolean isProbeParticlePacket(ClientboundLevelParticlesPacket packet) {
        return packet.getParticle() == SalisSparkleParticleRegistration.TYPE
                && packet.getCount() == 0
                && Double.compare(packet.getX(), ORIGIN.getX() + 0.5D) == 0
                && Double.compare(packet.getY(), ORIGIN.getY() + 0.5D) == 0
                && Double.compare(packet.getZ(), ORIGIN.getZ() + 0.5D) == 0;
    }

    private static void removeOutboundParticleRecorder() {
        Channel channel = outboundProbeChannel;
        outboundProbeChannel = null;
        if (channel == null || !channel.isOpen()) return;
        Runnable remove = () -> {
            if (channel.pipeline().get(OUTBOUND_HANDLER_NAME) != null) {
                channel.pipeline().remove(OUTBOUND_HANDLER_NAME);
            }
        };
        try {
            if (channel.eventLoop().inEventLoop()) {
                remove.run();
            } else {
                channel.eventLoop().submit(remove).syncUninterruptibly();
            }
        } catch (RuntimeException failure) {
            ThaumcraftSmokeAudit.mark("SALIS_NATIVE_OUTBOUND_PACKET_RECORDER_CLEANUP_FAILED");
            LogUtils.getLogger().error("SALIS_NATIVE_OUTBOUND_PACKET_RECORDER_CLEANUP_FAILED", failure);
        }
    }

    private static boolean releaseFixtureTicket() {
        if (!fixtureTicketHeld) return true;
        if (targetLevel == null || !targetLevel.setChunkForced(CHUNK_X, CHUNK_Z, false)) return false;
        fixtureTicketHeld = false;
        return true;
    }

    private static void cleanupFixtureTicketAfterFailure() {
        if (!fixtureTicketHeld) return;
        try {
            if (!releaseFixtureTicket()) {
                ThaumcraftSmokeAudit.mark("SALIS_NATIVE_CHUNK_TICKET_CLEANUP_FAILED");
                LogUtils.getLogger().error("SALIS_NATIVE_CHUNK_TICKET_CLEANUP_FAILED");
            }
        } catch (RuntimeException cleanupFailure) {
            ThaumcraftSmokeAudit.mark("SALIS_NATIVE_CHUNK_TICKET_CLEANUP_FAILED");
            LogUtils.getLogger().error("SALIS_NATIVE_CHUNK_TICKET_CLEANUP_FAILED", cleanupFailure);
        }
    }

    private static void cleanupFixtureTicketOnStop() {
        if (!fixtureTicketHeld) return;
        try {
            if (!releaseFixtureTicket()) {
                ThaumcraftSmokeAudit.mark("SALIS_NATIVE_CHUNK_TICKET_CLEANUP_FAILED");
                LogUtils.getLogger().error("SALIS_NATIVE_CHUNK_TICKET_CLEANUP_FAILED during server stop");
            } else {
                ThaumcraftSmokeAudit.mark("SALIS_NATIVE_CHUNK_TICKET_CLEANED_ON_STOP");
            }
        } catch (RuntimeException cleanupFailure) {
            ThaumcraftSmokeAudit.mark("SALIS_NATIVE_CHUNK_TICKET_CLEANUP_FAILED");
            LogUtils.getLogger().error("SALIS_NATIVE_CHUNK_TICKET_CLEANUP_FAILED during server stop", cleanupFailure);
        }
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
