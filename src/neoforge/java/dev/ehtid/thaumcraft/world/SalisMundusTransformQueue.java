package dev.ehtid.thaumcraft.world;

import dev.ehtid.thaumcraft.port.core.FirstStepsBootstrapLogic;
import dev.ehtid.thaumcraft.registry.ThaumcraftBlocks;
import dev.ehtid.thaumcraft.registry.ThaumcraftItems;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/** BETA26 DustTriggerSimple/Ore delayed swapper boundary for the two FIRSTSTEPS transforms. */
public final class SalisMundusTransformQueue {
    private SalisMundusTransformQueue() {}

    private record Pending(ResourceKey<Level> dimension, BlockPos pos, BlockState source,
                           UUID playerId, FirstStepsBootstrapLogic.DustTarget target, long dueGameTime) {}

    private static final List<Pending> PENDING = new ArrayList<>();

    public static void schedule(ServerLevel level, BlockPos pos, BlockState source, ServerPlayer player,
                                FirstStepsBootstrapLogic.DustTarget target) {
        PENDING.add(new Pending(level.dimension(), pos.immutable(), source, player.getUUID(), target,
                level.getGameTime() + FirstStepsBootstrapLogic.dustDelayTicks()));
    }

    public static void onServerStopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        PENDING.clear();
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        Iterator<Pending> it = PENDING.iterator();
        while (it.hasNext()) {
            Pending pending = it.next();
            ServerLevel level = server.getLevel(pending.dimension());
            if (level == null || level.getGameTime() < pending.dueGameTime()) continue;
            it.remove();

            BlockState current = level.getBlockState(pending.pos());
            // TC6 VirtualSwapper requires the exact captured IBlockState to still be present.
            if (current != pending.source()) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(pending.playerId());
            if (player == null || !level.mayInteract(player, pending.pos())) continue;

            switch (pending.target()) {
                case THAUMONOMICON_ITEM -> transformToThaumonomicon(level, pending.pos());
                case ARCANE_WORKBENCH_BLOCK -> level.setBlock(pending.pos(),
                        ThaumcraftBlocks.ARCANE_WORKBENCH.get().defaultBlockState(), 3);
                default -> { }
            }
        }
    }

    private static void transformToThaumonomicon(ServerLevel level, BlockPos pos) {
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        ItemEntity entity = new ItemEntity(level, pos.getX() + 0.5D, pos.getY() + 0.1D, pos.getZ() + 0.5D,
                new ItemStack(ThaumcraftItems.THAUMONOMICON.get()));
        // EntitySpecialItem used by BETA26's swapper explicitly starts with zero motion.
        entity.setDeltaMovement(Vec3.ZERO);
        level.addFreshEntity(entity);
    }
}
