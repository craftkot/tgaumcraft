package dev.ehtid.thaumcraft.client;

import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.blockentity.EssentiaJarBlockEntity;
import dev.ehtid.thaumcraft.registry.ThaumcraftBlockEntities;
import dev.ehtid.thaumcraft.registry.ThaumcraftBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import java.util.Locale;
import java.util.Objects;

/**
 * Smoke-only client-side inspection of the jar fixture after the server has
 * teleported the player back to its forced-loaded chunk. This separates a
 * missing client BlockEntity/update packet from a renderer dispatch problem.
 */
@EventBusSubscriber(modid = ThaumcraftPort.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class EssentiaJarClientSmokeProbe {
    private static final boolean SMOKE_PROBE =
            Boolean.getBoolean("thaumcraft.jarLiquidSmoke");
    private static final BlockPos AER_POS = new BlockPos(249, 150, 251);
    private static final BlockPos IGNIS_POS = new BlockPos(250, 150, 251);
    private static final BlockPos EMPTY_POS = new BlockPos(251, 150, 251);

    private static String lastObservation;

    private EssentiaJarClientSmokeProbe() {}

    @SubscribeEvent
    public static void afterGui(RenderGuiEvent.Post event) {
        if (!SMOKE_PROBE) return;

        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (level == null || player == null
                || player.distanceToSqr(250.5D, 150.5D, 251.5D) > 256.0D) {
            return;
        }

        Observation aer = inspect(level, AER_POS, 125, "aer");
        Observation ignis = inspect(level, IGNIS_POS, 250, "ignis");
        Observation empty = inspect(level, EMPTY_POS, 0, null);
        boolean ready = aer.matches() && ignis.matches() && empty.matches();

        String observation = String.format(Locale.ROOT,
                "TC_SMOKE_JAR_LIQUID_CLIENT_STATE=%s player=%.2f,%.2f,%.2f aer={%s} ignis={%s} empty={%s}",
                ready ? "READY" : "WAIT",
                player.getX(), player.getY(), player.getZ(),
                aer.details(), ignis.details(), empty.details());
        if (!observation.equals(lastObservation)) {
            System.out.println(observation);
            lastObservation = observation;
        }
    }

    private static Observation inspect(ClientLevel level, BlockPos pos,
                                       int expectedAmount, String expectedAspect) {
        if (!level.hasChunkAt(pos)) {
            return new Observation(false, "pos=" + coordinates(pos) + " chunk=UNLOADED");
        }

        BlockState state = level.getBlockState(pos);
        String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof EssentiaJarBlockEntity jar)) {
            return new Observation(false, "pos=" + coordinates(pos)
                    + " chunk=LOADED block=" + blockId
                    + " be=" + (blockEntity == null ? "null" : blockEntity.getClass().getName()));
        }

        var contents = jar.snapshot();
        boolean typeMatches = jar.getType() == ThaumcraftBlockEntities.JAR_NORMAL.get();
        boolean blockMatches = state.is(ThaumcraftBlocks.JAR_NORMAL.get());
        boolean contentsMatch = contents.amount() == expectedAmount
                && Objects.equals(contents.aspect(), expectedAspect);
        boolean matches = typeMatches && blockMatches && contentsMatch;
        return new Observation(matches, "pos=" + coordinates(pos)
                + " chunk=LOADED block=" + blockId
                + " be=" + jar.getClass().getSimpleName()
                + " typeMatch=" + typeMatches
                + " amount=" + contents.amount()
                + " aspect=" + (contents.aspect() == null ? "none" : contents.aspect())
                + " expectedAmount=" + expectedAmount
                + " expectedAspect=" + (expectedAspect == null ? "none" : expectedAspect));
    }

    private static String coordinates(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private record Observation(boolean matches, String details) {}
}
