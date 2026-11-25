package dev.ehtid.thaumcraft.client;

import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.entity.WispEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
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
 * AttackEntityEvent records the entity Player.attack actually selected. After
 * selection, the smoke-only probe switches the camera to a fixed side view so
 * the short BETA26 zap can be recorded without delaying the real attack.
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
    private static boolean aimCameraAtWisp = true;
    private static boolean sideViewLogged;

    private WispAttackSmokeProbe() {}

    static Vec3 cameraAimPoint(WispEntity wisp) {
        var bounds = wisp.getBoundingBox();
        double y = bounds.minY + (bounds.maxY - bounds.minY) * 0.25D;
        return new Vec3(wisp.getX(), y, wisp.getZ());
    }

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
        double hitDistance = minecraft.player == null || hit == null
                ? -1.0D
                : minecraft.player.getEyePosition().distanceTo(hit.getLocation());
        long monotonicNs = System.nanoTime();

        System.out.println("TC_SMOKE_WISP_ATTACK_INPUT=YES targetWisp=" + targetWisp
                + " targetId=" + (target == null ? -1 : target.getId())
                + " targetType=" + targetType
                + " hitType=" + hitType
                + " distance=" + distance
                + " hitDistance=" + hitDistance
                + " gameTime=" + gameTime
                + " blockPos=" + blockPos
                + " tick=" + (minecraft.player == null ? -1 : minecraft.player.tickCount)
                + " monotonicNs=" + monotonicNs);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        if (!ENABLED) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            trackedWispId = -1;
            aimCameraAtWisp = true;
            sideViewLogged = false;
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
                aimCameraAtWisp = true;
                sideViewLogged = false;
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
            aimCameraAtWisp = true;
            sideViewLogged = false;
            return;
        }

        if (!aimCameraAtWisp) {
            float yaw = Mth.wrapDegrees(minecraft.player.getYRot());
            float pitch = minecraft.player.getXRot();
            if (!sideViewLogged
                    && "THIRD_PERSON_BACK".equals(minecraft.options.getCameraType().name())
                    && Math.abs(Math.abs(yaw) - 90.0F) <= 5.0F
                    && Math.abs(pitch) <= 5.0F) {
                sideViewLogged = true;
                System.out.println("TC_SMOKE_WISP_CAMERA_SIDE_VIEW=YES id=" + trackedWispId
                        + " yaw=" + yaw + " pitch=" + pitch + " cameraType=THIRD_PERSON_BACK");
            }
            return;
        }

        // BETA26's visible billboard is centred at entity position. Aim at a point
        // one quarter up the hitbox: still inside the real attack AABB, but close
        // enough to the rendered quad for the client screenshot proof to be stable.
        // This only steers the smoke camera; Wisp AI, motion and hitbox are untouched.
        minecraft.player.lookAt(EntityAnchorArgument.Anchor.EYES, cameraAimPoint(wisp));
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
            aimCameraAtWisp = false;
            sideViewLogged = false;
            System.out.println("TC_SMOKE_WISP_CAMERA_TRACK=YES id=" + trackedWispId);
            System.out.println("TC_SMOKE_WISP_CAMERA_TRACKING_STOPPED=YES id=" + trackedWispId);
            long gameTime = minecraft.level == null ? -1L : minecraft.level.getGameTime();
            System.out.println("TC_SMOKE_WISP_CLIENT_ATTACK=YES player=" + minecraft.player.getId()
                    + " target=" + wisp.getId()
                    + " tick=" + wisp.tickCount
                    + " distance=" + minecraft.player.distanceTo(wisp)
                    + " gameTime=" + gameTime);

            // AttackEntityEvent fires after the real hit result selected this Wisp.
            // Change only the smoke camera now, before the server can deliver the
            // three-tick zap payload. The click target and server damage are untouched.
            // The zap follows the horizontal source-to-target axis. Capture it broadside;
            // a fixed world yaw can accidentally look almost straight down the bolt.
            Vec3 toWisp = wisp.position().subtract(minecraft.player.position());
            float beamYaw = Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(-toWisp.x, toWisp.z)));
            float sideViewYaw = Mth.wrapDegrees(beamYaw + 90.0F);
            minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            minecraft.player.setYRot(sideViewYaw);
            minecraft.player.yRotO = sideViewYaw;
            minecraft.player.setXRot(0.0F);
            minecraft.player.xRotO = 0.0F;
            sideViewLogged = true;
            long monotonicNs = System.nanoTime();
            System.out.println("TC_SMOKE_WISP_CAMERA_SIDE_VIEW=YES id=" + trackedWispId
                    + " beamYaw=" + beamYaw
                    + " yaw=" + Mth.wrapDegrees(minecraft.player.getYRot())
                    + " pitch=" + minecraft.player.getXRot()
                    + " cameraType=" + minecraft.options.getCameraType().name()
                    + " monotonicNs=" + monotonicNs);
        }
    }
}
