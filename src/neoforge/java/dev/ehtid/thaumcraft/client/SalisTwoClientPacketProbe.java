package dev.ehtid.thaumcraft.client;

import com.mojang.logging.LogUtils;
import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.registry.SalisSparkleParticleRegistration;
import dev.ehtid.thaumcraft.registry.ThaumcraftSounds;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Client-side packet receipt recorder for two independent real Minecraft
 * processes. It observes decoded inbound packets before packet_handler and
 * always forwards them unchanged to vanilla/NeoForge.
 */
@EventBusSubscriber(modid = ThaumcraftPort.MOD_ID, value = Dist.CLIENT)
public final class SalisTwoClientPacketProbe {
    private static final boolean ENABLED = Boolean.getBoolean("thaumcraft.salisTwoClientScenario");
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String HANDLER_NAME = "thaumcraft_salis_two_client_inbound";

    private static final BlockPos TARGET = new BlockPos(0, 100, 0);
    private static final BlockPos CONTROL = new BlockPos(20, 100, 20);
    private static final BlockPos CHUNK_ORIGIN =
            new BlockPos(512 * 16 + 15, 100, 512 * 16 + 8);

    private static final AtomicBoolean INSTALL_QUEUED = new AtomicBoolean();
    private static final AtomicInteger CONTROL_PARTICLES = new AtomicInteger();
    private static final AtomicInteger CONTROL_SOUNDS = new AtomicInteger();
    private static final AtomicInteger TARGET_PARTICLES = new AtomicInteger();
    private static final AtomicInteger TARGET_SOUNDS = new AtomicInteger();
    private static final AtomicInteger CHUNK_PARTICLES = new AtomicInteger();

    private static volatile Channel installedChannel;

    private SalisTwoClientPacketProbe() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!ENABLED) return;
        var listener = Minecraft.getInstance().getConnection();
        if (listener == null) return;

        Connection connection = listener.getConnection();
        Channel channel = connection.channel();
        if (channel == null || !channel.isActive()) return;
        if (channel == installedChannel && channel.pipeline().get(HANDLER_NAME) != null) return;
        if (!INSTALL_QUEUED.compareAndSet(false, true)) return;

        channel.eventLoop().execute(() -> {
            try {
                if (!channel.isActive()) return;
                var pipeline = channel.pipeline();
                if (pipeline.get("packet_handler") == null) {
                    LOGGER.error("SALIS_TWO_CLIENT_PACKET_PROBE_INSTALL_FAILED: packet_handler missing");
                    return;
                }
                if (pipeline.get(HANDLER_NAME) != null) pipeline.remove(HANDLER_NAME);
                String clientName = Minecraft.getInstance().getUser().getName();
                pipeline.addBefore("packet_handler", HANDLER_NAME, new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelRead(ChannelHandlerContext context, Object message) throws Exception {
                        if (message instanceof ClientboundLevelParticlesPacket packet
                                && packet.getParticle() == SalisSparkleParticleRegistration.TYPE) {
                            inspectParticle(clientName, packet.getX(), packet.getY(), packet.getZ());
                        } else if (message instanceof ClientboundSoundPacket packet
                                && packet.getSound().value() == ThaumcraftSounds.DUST.get()) {
                            inspectSound(clientName, packet.getX(), packet.getY(), packet.getZ());
                        }
                        context.fireChannelRead(message);
                    }
                });
                installedChannel = channel;
                LOGGER.info("TC_SMOKE_SALIS_CLIENT_PACKET_PROBE_INSTALLED=YES client={}", clientName);
            } finally {
                INSTALL_QUEUED.set(false);
            }
        });
    }

    private static void inspectParticle(String clientName, double x, double y, double z) {
        if (near(x, y, z, CONTROL, 2.0D)) {
            LOGGER.info("TC_SMOKE_SALIS_CLIENT_CONTROL_PARTICLE=YES client={} count={}",
                    clientName, CONTROL_PARTICLES.incrementAndGet());
        } else if (near(x, y, z, TARGET, 6.0D)) {
            LOGGER.info("TC_SMOKE_SALIS_CLIENT_TARGET_PARTICLE=YES client={} count={}",
                    clientName, TARGET_PARTICLES.incrementAndGet());
        } else if (near(x, y, z, CHUNK_ORIGIN, 2.0D)) {
            LOGGER.info("TC_SMOKE_SALIS_CLIENT_CHUNK_PARTICLE=YES client={} count={}",
                    clientName, CHUNK_PARTICLES.incrementAndGet());
        }
    }

    private static void inspectSound(String clientName, double x, double y, double z) {
        if (near(x, y, z, CONTROL, 2.0D)) {
            LOGGER.info("TC_SMOKE_SALIS_CLIENT_CONTROL_SOUND=YES client={} count={}",
                    clientName, CONTROL_SOUNDS.incrementAndGet());
        } else if (near(x, y, z, TARGET, 2.0D)) {
            LOGGER.info("TC_SMOKE_SALIS_CLIENT_TARGET_SOUND=YES client={} count={}",
                    clientName, TARGET_SOUNDS.incrementAndGet());
        }
    }

    private static boolean near(double x, double y, double z, BlockPos center, double radius) {
        double dx = x - (center.getX() + 0.5D);
        double dy = y - (center.getY() + 0.5D);
        double dz = z - (center.getZ() + 0.5D);
        return dx * dx + dy * dy + dz * dz <= radius * radius;
    }
}
