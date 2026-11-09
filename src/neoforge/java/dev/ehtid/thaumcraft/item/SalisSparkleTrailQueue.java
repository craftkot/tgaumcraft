package dev.ehtid.thaumcraft.item;

import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.port.core.visual.SalisSparkleDelayRules;
import dev.ehtid.thaumcraft.port.core.visual.SalisSparkleFaceDelayRules;
import dev.ehtid.thaumcraft.util.ThaumcraftSmokeAudit;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Actor-only, short-lived delayed Salis trail and block-face fallback.
 * BETA26 FXDispatcher delays each drawSimpleSparkle by 0..4 ticks.
 * ParticleOptions keeps the queue generic for the original sprite trail and face fallback.
 */
@EventBusSubscriber(modid = ThaumcraftPort.MOD_ID)
public final class SalisSparkleTrailQueue {
    private record Pending(ResourceKey<Level> dimension, ServerLevel sourceLevel, UUID playerId, ServerPlayer actor,
                           ParticleOptions color, Vec3 origin, Vec3 velocity,
                           long dueTick, boolean face) {}

    private static final List<Pending> PENDING = new ArrayList<>();

    private SalisSparkleTrailQueue() {}

    public static void emitOrQueue(ServerLevel level, ServerPlayer player, ParticleOptions color,
                                   Vec3 origin, Vec3 velocity, int delayTicks) {
        long due = SalisSparkleDelayRules.dueTick(level.getGameTime(), delayTicks);
        enqueueOrEmit(level, player, color, origin, velocity, due, false);
    }

    public static void emitFaceOrQueue(ServerLevel level, ServerPlayer player, ParticleOptions color,
                                       Vec3 origin, Vec3 velocity, int delayTicks) {
        long due = SalisSparkleFaceDelayRules.dueTick(level.getGameTime(), delayTicks);
        enqueueOrEmit(level, player, color, origin, velocity, due, true);
    }

    private static void enqueueOrEmit(ServerLevel level, ServerPlayer player, ParticleOptions color,
                                      Vec3 origin, Vec3 velocity, long due, boolean face) {
        // Reject callers holding a ServerLevel that the server has already replaced.
        // This applies to immediate FX too: the queued path alone previously
        // rejected replaced worlds, but a zero-delay send could still target one.
        if (level.getServer().getLevel(level.dimension()) != level) return;
        if (due == level.getGameTime()) {
            // Apply the same loaded-chunk guard to zero-delay particles.
            // A stale caller must never emit into a chunk that is no longer loaded.
            if (!level.hasChunkAt(net.minecraft.core.BlockPos.containing(origin))) return;
            // Zero-delay effects must obey the same world/actor guard as queued ones.
            // A stale caller may hold a player that has already changed dimension.
            if (player.level() != level) return;
            // An obsolete player session may still reference the same level.
            if (level.getServer().getPlayerList().getPlayer(player.getUUID()) != player) return;
            emit(level, player, color, origin, velocity);
            return;
        }
        // No chunk tickets or cross-player broadcasts; the short queue expires
        // naturally on server ticks and is cleared on server shutdown.
        // Discard effects whose source is already unloaded rather than retaining
        // them in the pending queue until their scheduled tick.
        if (!level.hasChunkAt(net.minecraft.core.BlockPos.containing(origin))) return;
        // Do not queue a delayed effect for a player who has already left this level.
        if (player.level() != level) return;
        // Reject a disconnected or replaced actor at enqueue time as well as at due tick.
        // Otherwise an obsolete caller can enqueue effects that survive a brief reconnect.
        if (level.getServer().getPlayerList().getPlayer(player.getUUID()) != player) return;
        PENDING.add(new Pending(level.dimension(), level, player.getUUID(), player, color, origin, velocity, due, face));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        int trailEmitted = 0;
        int faceEmitted = 0;
        Iterator<Pending> iterator = PENDING.iterator();
        while (iterator.hasNext()) {
            Pending pending = iterator.next();
            // The queue is JVM-static; ticks from other servers must not
            // consume pending effects owned by this server instance.
            if (pending.sourceLevel().getServer() != server) continue;
            ServerLevel level = server.getLevel(pending.dimension());
            if (level == null || level != pending.sourceLevel()) {
                // Dimension keys are shared across server instances; never replay an
                // old world's pending FX against a replacement ServerLevel.
                iterator.remove();
                continue;
            }
            // Cancel as soon as the origin chunk unloads. If it reloads before
            // the due tick, an old visual effect must not reappear there.
            if (!level.hasChunkAt(net.minecraft.core.BlockPos.containing(pending.origin()))) {
                iterator.remove();
                // Server-side evidence of cancellation, not evidence of a client packet.
                ThaumcraftSmokeAudit.mark("SALIS_DELAY_UNLOADED_CANCELLED");
                continue;
            }
            if (level.getGameTime() < pending.dueTick()) continue;
            iterator.remove();
            ServerPlayer player = server.getPlayerList().getPlayer(pending.playerId());
            // A reconnect can reuse the same UUID with a new player session.
            // Effects from the old session must not leak into the new one.
            if (player == null || player != pending.actor() || player.level() != level) continue;
            // A delayed effect must not resurrect or address an unloaded chunk.
            // hasChunkAt is a loaded-chunk check, not a ticket or force-load.
            if (!level.hasChunkAt(net.minecraft.core.BlockPos.containing(pending.origin()))) continue;
            emit(level, player, pending.color(), pending.origin(), pending.velocity());
            if (pending.face()) faceEmitted++;
            else trailEmitted++;
        }
        if (trailEmitted > 0) ThaumcraftSmokeAudit.mark("SALIS_TRAIL_DELAY_QUEUE_DRAINED");
        if (faceEmitted > 0) ThaumcraftSmokeAudit.mark("SALIS_FACE_DELAY_QUEUE_DRAINED");
    }

    /** Cancel immediately on the actual chunk-unload event, including unload/reload
     * cycles occurring between server ticks. Tick-time checks remain a fallback.
     */
    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var chunkPos = event.getChunk().getPos();
        Iterator<Pending> iterator = PENDING.iterator();
        while (iterator.hasNext()) {
            Pending pending = iterator.next();
            if (pending.dimension() != level.dimension() || pending.sourceLevel() != level) continue;
            if (!new net.minecraft.world.level.ChunkPos(net.minecraft.core.BlockPos.containing(pending.origin())).equals(chunkPos)) continue;
            iterator.remove();
            ThaumcraftSmokeAudit.mark("SALIS_DELAY_UNLOADED_CANCELLED");
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        MinecraftServer stopped = event.getServer();
        PENDING.removeIf(pending -> pending.sourceLevel().getServer() == stopped);
    }

    private static void emit(ServerLevel level, ServerPlayer player, ParticleOptions color,
                             Vec3 origin, Vec3 velocity) {
        // count=0 sends one particle with this exact velocity, actor only.
        level.sendParticles(player, color, false, origin.x, origin.y, origin.z, 0,
                velocity.x, velocity.y, velocity.z, 1.0D);
        // Server-side send invocation only: not proof of packet delivery or rendering.
        ThaumcraftSmokeAudit.mark("SALIS_PARTICLE_SEND_INVOKED");
    }
}
