package dev.ehtid.thaumcraft.client;

import dev.ehtid.thaumcraft.ThaumcraftPort;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Smoke-only completion marker for a Wisp frame. The entity renderer callback
 * runs before LevelRenderer finishes submitting/flushing the frame; AFTER_LEVEL
 * is the first safe point for a screenshot to observe the completed world pass.
 *
 * The hit result is sampled at that same point so a geometric camera dot alone
 * can never authorize a real attack click.
 */
@EventBusSubscriber(
        modid = ThaumcraftPort.MOD_ID,
        bus = EventBusSubscriber.Bus.GAME,
        value = Dist.CLIENT
)
public final class WispRenderSmokeProbe {
    private static final boolean ENABLED = "1".equals(System.getenv("THAUMCRAFT_SMOKE"));
    private static int pendingEntityId = -1;
    private static int pendingTick = -1;
    private static boolean pending;

    private WispRenderSmokeProbe() {}

    static void recordRendered(int entityId, int tick) {
        if (!ENABLED) {
            return;
        }
        pendingEntityId = entityId;
        pendingTick = tick;
        pending = true;
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (!ENABLED || !pending || event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        HitResult hit = minecraft.hitResult;
        Entity target = hit instanceof EntityHitResult entityHit ? entityHit.getEntity() : null;
        String hitType = hit == null ? "null" : hit.getType().name();
        String targetType = target == null
                ? "none"
                : String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()));
        String blockPos = hit instanceof BlockHitResult blockHit
                ? blockHit.getBlockPos().getX() + "," + blockHit.getBlockPos().getY() + "," + blockHit.getBlockPos().getZ()
                : "none";
        long gameTime = minecraft.level == null ? -1L : minecraft.level.getGameTime();
        int playerTick = minecraft.player == null ? -1 : minecraft.player.tickCount;

        System.out.println("TC_SMOKE_WISP_FRAME_COMPLETE=YES id=" + pendingEntityId
                + " tick=" + pendingTick
                + " gameTime=" + gameTime
                + " playerTick=" + playerTick
                + " hitType=" + hitType
                + " targetId=" + (target == null ? -1 : target.getId())
                + " targetType=" + targetType
                + " blockPos=" + blockPos);
        pending = false;
    }
}
