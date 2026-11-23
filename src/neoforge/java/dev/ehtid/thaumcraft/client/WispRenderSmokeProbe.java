package dev.ehtid.thaumcraft.client;

import dev.ehtid.thaumcraft.ThaumcraftPort;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Smoke-only completion marker for a Wisp frame. The entity renderer callback
 * runs before LevelRenderer finishes submitting/flushing the frame; AFTER_LEVEL
 * is the first safe point for a screenshot to observe the completed world pass.
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
        System.out.println("TC_SMOKE_WISP_FRAME_COMPLETE=YES id=" + pendingEntityId
                + " tick=" + pendingTick);
        pending = false;
    }
}
