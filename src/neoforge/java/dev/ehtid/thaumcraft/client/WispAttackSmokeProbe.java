package dev.ehtid.thaumcraft.client;

import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.entity.WispEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;

/**
 * Smoke-only observation of the actual client attack pipeline.
 *
 * The input event records Minecraft.hitResult before target selection; the
 * AttackEntityEvent records the entity Player.attack actually selected. Neither
 * handler changes or cancels the normal interaction.
 */
@EventBusSubscriber(
        modid = ThaumcraftPort.MOD_ID,
        bus = EventBusSubscriber.Bus.GAME,
        value = Dist.CLIENT
)
public final class WispAttackSmokeProbe {
    private static final boolean ENABLED = "1".equals(System.getenv("THAUMCRAFT_SMOKE"));
    private static final String SMOKE_TARGET_NAME = "tc_wisp_zap_smoke";
    private static int trackedWispId = -1;

    private WispAttackSmokeProbe() {}

    @SubscribeEvent
    public static void onAttackInput(InputEvent.InteractionKeyMappingTriggered event) {
        if (!ENABLED || !event.isAttack()) {
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
        boolean targetWisp = target instanceof WispEntity;
        double distance = minecraft.player == null || target == null
                ? -1.0D
                : minecraft.player.distanceTo(target);
        long gameTime = minecraft.level == null ? -1L : minecraft.level.getGameTime();

        System.out.println("TC_SMOKE_WISP_ATTACK_INPUT=YES targetWisp=" + targetWisp
                + " targetId=" + (target == null ? -1 : target.getId())
                + " targetType=" + targetType
                + " hitType=" + hitType
                + " distance=" + distance
                + " gameTime=" + gameTime
                + " blockPos=" + blockPos
                + " tick=" + (minecraft.player == null ? -1 : minecraft.player.tickCount));
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        if (!ENABLED) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            trackedWispId = -1;
            return;
        }

        // Scoreboard tags are server-side and are not a reliable client selector.
        // The smoke fixture gives only its target a hidden, synchronized custom name.
        // Start tracking before the first click so autonomous BETA26 flight cannot
        // outrun a one-time pre-aim while the client dispatches mouse input.
        if (trackedWispId < 0) {
            WispEntity smokeTarget = minecraft.level.getEntitiesOfClass(
                    WispEntity.class,
                    minecraft.player.getBoundingBox().inflate(32.0D),
                    candidate -> candidate.isAlive()
                            && candidate.hasCustomName()
                            && SMOKE_TARGET_NAME.equals(candidate.getCustomName().getString())
            ).stream().findFirst().orElse(null);
            if (smokeTarget != null) {
                trackedWispId = smokeTarget.getId();
                System.out.println("TC_SMOKE_WISP_CAMERA_TRACK_PRE_ATTACK=YES id=" + trackedWispId
                        + " tick=" + smokeTarget.tickCount
                        + " gameTime=" + minecraft.level.getGameTime());
            }
        }
        if (trackedWispId < 0) {
            return;
        }

        Entity target = minecraft.level.getEntity(trackedWispId);
        if (!(target instanceof WispEntity wisp) || !wisp.isAlive()) {
            trackedWispId = -1;
            return;
        }

        // Smoke-only camera tracking; the Wisp's original AI and motion remain untouched.
        minecraft.player.lookAt(EntityAnchorArgument.Anchor.EYES, wisp.getBoundingBox().getCenter());
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (!ENABLED) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || event.getEntity() != minecraft.player) {
            return;
        }

        if (event.getTarget() instanceof WispEntity wisp) {
            trackedWispId = wisp.getId();
            System.out.println("TC_SMOKE_WISP_CAMERA_TRACK=YES id=" + trackedWispId);
            long gameTime = minecraft.level == null ? -1L : minecraft.level.getGameTime();
            System.out.println("TC_SMOKE_WISP_CLIENT_ATTACK=YES player=" + minecraft.player.getId()
                    + " target=" + wisp.getId()
                    + " tick=" + wisp.tickCount
                    + " distance=" + minecraft.player.distanceTo(wisp)
                    + " gameTime=" + gameTime);
        }
    }
}
