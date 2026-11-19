package dev.ehtid.thaumcraft.item;

import com.mojang.logging.LogUtils;
import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.player.ThaumcraftPlayerAttachments;
import dev.ehtid.thaumcraft.registry.SalisSparkleParticleRegistration;
import dev.ehtid.thaumcraft.registry.ThaumcraftItems;
import dev.ehtid.thaumcraft.registry.ThaumcraftSounds;
import dev.ehtid.thaumcraft.util.ThaumcraftSmokeAudit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;

/**
 * Dedicated-server fixture for two separately launched Minecraft clients.
 *
 * The actor and observer are real authenticated network sessions (the CI
 * server runs offline-mode); both receive independent positive-control
 * packets before the real Salis bookshelf interaction is attempted.
 */
@EventBusSubscriber(modid = ThaumcraftPort.MOD_ID, value = Dist.DEDICATED_SERVER)
public final class SalisTwoClientServerProbe {
    private static final boolean ENABLED = Boolean.getBoolean("thaumcraft.salisTwoClientScenario");
    private static final String ACTOR_NAME = "TC_SalisActor";
    private static final String OBSERVER_NAME = "TC_SalisObserver";
    private static final BlockPos TARGET = new BlockPos(0, 100, 0);
    private static final BlockPos CONTROL = new BlockPos(20, 100, 20);
    private static final long LOGIN_TIMEOUT_TICKS = 3600L;
    private static final long ACTION_TIMEOUT_TICKS = 600L;
    private static final long CLIENT_SETTLE_TICKS = 40L;

    private static MinecraftServer active;
    private static ServerPlayer actor;
    private static ServerPlayer observer;
    private static long serverStartedAt;
    private static long bothPlayersSeenAt = -1L;
    private static long actionObservedAt;
    private static long phaseStartedAt;
    private static int phase;
    private static boolean finished;

    private SalisTwoClientServerProbe() {}

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        if (!ENABLED) return;
        active = event.getServer();
        actor = null;
        observer = null;
        serverStartedAt = active.overworld().getGameTime();
        bothPlayersSeenAt = -1L;
        actionObservedAt = 0L;
        phaseStartedAt = 0L;
        phase = 0;
        finished = false;
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        if (active == event.getServer()) {
            active = null;
            actor = null;
            observer = null;
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (!ENABLED || active != server || finished) return;

        ServerLevel level = server.overworld();
        long now = level.getGameTime();
        try {
            if (phase == 0) {
                if (now - serverStartedAt > LOGIN_TIMEOUT_TICKS) {
                    fail("two named real clients did not join before the login deadline", null);
                    return;
                }

                actor = findPlayer(server, ACTOR_NAME);
                observer = findPlayer(server, OBSERVER_NAME);
                if (actor == null || observer == null) return;
                if (actor.level() != level || observer.level() != level) return;

                List<ServerPlayer> players = server.getPlayerList().getPlayers();
                int actorIndex = players.indexOf(actor);
                int observerIndex = players.indexOf(observer);
                require(actorIndex >= 0 && observerIndex >= 0 && actorIndex < observerIndex,
                        "actor must be the first connected player so the native chunk probe selects it");

                if (bothPlayersSeenAt < 0L) {
                    bothPlayersSeenAt = now;
                    return;
                }
                if (now - bothPlayersSeenAt < CLIENT_SETTLE_TICKS) return;

                prepareFixture(level);
                sendControlPackets(level, actor);
                sendControlPackets(level, observer);
                phaseStartedAt = now;
                phase = 1;
                ThaumcraftSmokeAudit.mark("SALIS_TWO_CLIENT_FIXTURE_READY");
                return;
            }

            if (phase == 1) {
                if (now - phaseStartedAt > ACTION_TIMEOUT_TICKS) {
                    fail("actor did not use Salis Mundus on the prepared bookshelf", null);
                    return;
                }
                if (ThaumcraftSmokeAudit.count("BOOKSHELF_SALIS_USED") == 0) return;

                require(isOnline(server, actor), "actor session ended before Salis acceptance");
                require(isOnline(server, observer), "observer session ended before Salis acceptance");
                require(actor.getMainHandItem().is(ThaumcraftItems.SALIS_MUNDUS.get())
                                && actor.getMainHandItem().getCount() == 1,
                        "real bookshelf use did not consume exactly one of the two prepared Salis dust items");
                require(level.getBlockState(TARGET).is(Blocks.BOOKSHELF)
                                || ThaumcraftSmokeAudit.count("BOOKSHELF_TRANSFORMED") > 0,
                        "Salis trigger marker appeared without the bookshelf fixture");

                actionObservedAt = now;
                phase = 2;
                ThaumcraftSmokeAudit.mark("SALIS_TWO_CLIENT_ITEM_USE_CONFIRMED");
                return;
            }

            if (phase == 2) {
                if (now - actionObservedAt < 12L) return;
                require(isOnline(server, observer), "observer disconnected during actor-only packet window");
                ThaumcraftSmokeAudit.mark("SALIS_TWO_CLIENT_SERVER_ACTION_CONFIRMED");
                finished = true;
            }
        } catch (RuntimeException | AssertionError failure) {
            fail("two-client Salis server fixture failed", failure);
        }
    }

    private static void prepareFixture(ServerLevel level) {
        // A disposable flat test area: solid support, clear ray corridor and
        // the original BETA26 bookshelf trigger at a known block position.
        for (int x = -1; x <= 7; x++) {
            for (int z = -1; z <= 8; z++) {
                level.setBlock(new BlockPos(x, TARGET.getY() - 1, z),
                        Blocks.STONE.defaultBlockState(), 3);
                for (int y = TARGET.getY(); y <= TARGET.getY() + 3; y++) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        level.setBlock(TARGET, Blocks.BOOKSHELF.defaultBlockState(), 3);

        actor.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        observer.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        actor.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge().addResearch("!gotdream");
        actor.setItemInHand(InteractionHand.MAIN_HAND,
                new ItemStack(ThaumcraftItems.SALIS_MUNDUS.get(), 2));
        actor.inventoryMenu.broadcastChanges();

        // At z=4 the bookshelf's front face is 3 blocks away. The eye-to-block
        // center angle is about 20.5 degrees; 21 degrees points inside the
        // one-block-high bookshelf rather than above it.
        actor.teleportTo(level, 0.5D, 100.0D, 4.0D, 180.0F, 21.0F);
        observer.teleportTo(level, 5.5D, 100.0D, 4.0D, 180.0F, 21.0F);
        require(actor.getMainHandItem().is(ThaumcraftItems.SALIS_MUNDUS.get())
                        && actor.getMainHandItem().getCount() == 2,
                "actor inventory fixture did not retain two Salis dust items");
        require(level.getBlockState(TARGET).is(Blocks.BOOKSHELF),
                "bookshelf fixture was not placed");
    }

    private static void sendControlPackets(ServerLevel level, ServerPlayer player) {
        double x = CONTROL.getX() + 0.5D;
        double y = CONTROL.getY() + 0.5D;
        double z = CONTROL.getZ() + 0.5D;
        // Separate unambiguous packet controls prove that each independent
        // client-side recorder is active before the actor-only interaction.
        level.sendParticles(player, SalisSparkleParticleRegistration.TYPE,
                false, x, y, z, 0, 0.0D, 0.0D, 0.0D, 1.0D);
        player.connection.send(new ClientboundSoundPacket(
                BuiltInRegistries.SOUND_EVENT.wrapAsHolder(ThaumcraftSounds.DUST.get()),
                SoundSource.PLAYERS, x, y, z, 0.33F, 1.0F, level.getRandom().nextLong()));
    }

    private static ServerPlayer findPlayer(MinecraftServer server, String name) {
        return server.getPlayerList().getPlayers().stream()
                .filter(player -> player.getGameProfile().getName().equals(name))
                .findFirst()
                .orElse(null);
    }

    private static boolean isOnline(MinecraftServer server, ServerPlayer player) {
        return player != null
                && server.getPlayerList().getPlayer(player.getUUID()) == player;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static void fail(String message, Throwable cause) {
        if (finished) return;
        finished = true;
        ThaumcraftSmokeAudit.mark("SALIS_TWO_CLIENT_FAILED");
        if (cause == null) {
            LogUtils.getLogger().error("SALIS_TWO_CLIENT_FAILED: {}", message);
        } else {
            LogUtils.getLogger().error("SALIS_TWO_CLIENT_FAILED: {}", message, cause);
        }
        if (active != null) active.halt(false);
    }
}
