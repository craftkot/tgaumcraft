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
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;

/**
 * Smoke-only observation of the actual client attack pipeline.
 *
 * The input event records Minecraft.hitResult before target selection; the
 * AttackEntityEvent records the entity Player.attack actually selected. After
 * selection, the smoke-only probe follows the live source in a broadside view so
 * the short BETA26 zap remains inside the recorded camera frustum.
 */
@EventBusSubscriber(
        modid = ThaumcraftPort.MOD_ID,
        bus = EventBusSubscriber.Bus.GAME,
        value = Dist.CLIENT
)
public final class WispAttackSmokeProbe {
    private static final boolean ENABLED = "1".equals(System.getenv("THAUMCRAFT_SMOKE"));
    private static final String SMOKE_TARGET_NAME = "tc_wisp_zap_smoke";
    private static final float SIDE_VIEW_OFFSET_DEGREES = 50.0F;
    private static final double MIN_SIDE_VIEW_CAMERA_DISTANCE = 3.0D;
    private static int trackedWispId = -1;
    private static boolean aimCameraAtWisp = true;
    private static boolean sideViewFrameLogged;
    private static boolean sideViewTracking;
    private static int sideViewTrackingTicksRemaining;
    private static float beamYawForSideView;
    private static float beamPitchForSideView;
    private static float sideViewYaw;
    private static float sideViewPitch;

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
            sideViewFrameLogged = false;
            sideViewTracking = false;
            sideViewTrackingTicksRemaining = 0;
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
                sideViewFrameLogged = false;
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
            sideViewFrameLogged = false;
            sideViewTracking = false;
            sideViewTrackingTicksRemaining = 0;
            return;
        }

        if (sideViewTracking) {
            if (sideViewTrackingTicksRemaining <= 0) {
                sideViewTracking = false;
                return;
            }
            sideViewTrackingTicksRemaining--;
            updateSideView(minecraft, wisp);
            return;
        }

        if (!aimCameraAtWisp) {
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
            sideViewTracking = true;
            sideViewTrackingTicksRemaining = 40;
            sideViewFrameLogged = false;
            System.out.println("TC_SMOKE_WISP_CAMERA_TRACK=YES id=" + trackedWispId);
            System.out.println("TC_SMOKE_WISP_CAMERA_TRACKING_STOPPED=YES id=" + trackedWispId
                    + " mode=pre_attack_aim sideViewTracking=active");
            long gameTime = minecraft.level == null ? -1L : minecraft.level.getGameTime();
            System.out.println("TC_SMOKE_WISP_CLIENT_ATTACK=YES player=" + minecraft.player.getId()
                    + " target=" + wisp.getId()
                    + " tick=" + wisp.tickCount
                    + " distance=" + minecraft.player.distanceTo(wisp)
                    + " gameTime=" + gameTime);

            // The real hit has already selected this Wisp. Keep only the smoke camera
            // in a broadside view while BETA26 completes its wind-up and three-tick bolt.
            // Tracking is client-only: attack selection, Wisp AI, damage and payload stay real.
            updateSideView(minecraft, wisp);
            long monotonicNs = System.nanoTime();
            System.out.println("TC_SMOKE_WISP_CAMERA_SIDE_VIEW=YES id=" + trackedWispId
                    + " beamYaw=" + beamYawForSideView
                    + " beamPitch=" + beamPitchForSideView
                    + " yaw=" + Mth.wrapDegrees(minecraft.player.getYRot())
                    + " pitch=" + minecraft.player.getXRot()
                    + " cameraType=" + minecraft.options.getCameraType().name()
                    + " monotonicNs=" + monotonicNs);
        }
    }

    private static void updateSideView(Minecraft minecraft, WispEntity wisp) {
        Vec3 toWisp = wisp.position().subtract(minecraft.player.position());
        double horizontalDistance = Math.sqrt(toWisp.x * toWisp.x + toWisp.z * toWisp.z);
        float beamYaw = Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(-toWisp.x, toWisp.z)));
        float beamPitch = (float) -Math.toDegrees(
                Math.atan2(toWisp.y, Math.max(horizontalDistance, 1.0E-6D)));
        beamYawForSideView = beamYaw;
        beamPitchForSideView = beamPitch;
        sideViewYaw = Mth.wrapDegrees(beamYaw + SIDE_VIEW_OFFSET_DEGREES);
        // Frame the beam midpoint vertically while the 50-degree yaw offset keeps it broadside.
        sideViewPitch = Mth.clamp(beamPitch * 0.5F, -45.0F, 45.0F);
        minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        minecraft.player.setYRot(sideViewYaw);
        minecraft.player.yRotO = sideViewYaw;
        minecraft.player.setXRot(sideViewPitch);
        minecraft.player.xRotO = sideViewPitch;
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (!ENABLED
                || event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES
                || trackedWispId < 0
                || aimCameraAtWisp
                || sideViewFrameLogged) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null
                || minecraft.options.getCameraType() != CameraType.THIRD_PERSON_BACK) {
            return;
        }

        Entity target = minecraft.level.getEntity(trackedWispId);
        if (!(target instanceof WispEntity wisp) || !wisp.isAlive()) {
            return;
        }

        float yaw = Mth.wrapDegrees(minecraft.player.getYRot());
        float pitch = minecraft.player.getXRot();
        if (Math.abs(Mth.wrapDegrees(yaw - sideViewYaw)) > 1.0F
                || Math.abs(pitch - sideViewPitch) > 1.0F) {
            return;
        }

        Vec3 cameraPosition = event.getCamera().getPosition();
        double cameraDistance = cameraPosition.distanceTo(minecraft.player.getEyePosition());
        if (cameraDistance < MIN_SIDE_VIEW_CAMERA_DISTANCE) {
            return;
        }

        sideViewFrameLogged = true;
        System.out.println("TC_SMOKE_WISP_SIDE_VIEW_RENDERED=YES id=" + trackedWispId
                + " beamYaw=" + beamYawForSideView
                + " beamPitch=" + beamPitchForSideView
                + " yaw=" + yaw
                + " pitch=" + pitch
                + " cameraType=" + minecraft.options.getCameraType().name()
                + " cameraDistance=" + cameraDistance
                + " monotonicNs=" + System.nanoTime());
    }
}
