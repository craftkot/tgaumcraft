package dev.ehtid.thaumcraft.client.golem;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

import java.util.EnumMap;

/**
 * Own cuboid construction model. All dimensions and surface details are newly authored.
 * Source anchors: BETA26 RenderThaumcraftGolem.renderParts/createPartsCache and
 * PartModel attachment points; GolemCatalog preserves their registered IDs.
 * The floor is y=24 and the head is about .9 blocks tall, including the armored crest.
 */
public final class GolemModel extends HierarchicalModel<ThaumcraftGolem> implements ArmedModel {
    public enum Surface { MATERIAL, DARK, METAL, EYES, BRAIN, PAINT }

    private final EnumMap<Surface, Skeleton> surfaces = new EnumMap<>(Surface.class);
    private final Skeleton body;
    private int material, head, arms, legs, addon;

    public GolemModel() {
        for (Surface surface : Surface.values()) surfaces.put(surface, new Skeleton(layer(surface).bakeRoot()));
        body = surfaces.get(Surface.MATERIAL);
    }

    public int selectedMaterial() { return material; }
    public int selectedHead() { return head; }
    public int selectedArms() { return arms; }
    public int selectedLegs() { return legs; }
    public int selectedAddon() { return addon; }

    /** Vanilla iron supplies texture variation; this tint distinguishes thaumium from iron. */
    public int materialTint() { return material == 4 ? 0xff8975bd : 0xffffffff; }

    @Override public ModelPart root() { return body.root; }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int light, int overlay, int color) {
        body.root.render(pose, buffer, light, overlay, FastColor.ARGB32.multiply(color, materialTint()));
    }

    public void renderSurface(Surface surface, PoseStack pose, VertexConsumer buffer,
                              int light, int overlay, int color) {
        surfaces.get(surface).root.render(pose, buffer, light, overlay, color);
    }

    @Override
    public void setupAnim(ThaumcraftGolem golem, float limbSwing, float limbAmount,
                          float ageInTicks, float headYaw, float headPitch) {
        var design = golem.design();
        material = design.material().id();
        head = design.head().id();
        arms = design.arms().id();
        legs = design.legs().id();
        addon = design.addon().id();
        body.resetAnimation();
        for (Skeleton skeleton : surfaces.values()) skeleton.select(head, arms, legs, addon);
        body.head.yRot = Mth.clamp(headYaw, -75, 75) * Mth.DEG_TO_RAD;
        body.head.xRot = Mth.clamp(headPitch, -40, 40) * Mth.DEG_TO_RAD;
        float stride = Mth.cos(limbSwing * .6662f) * limbAmount;
        float idle = Mth.sin(ageInTicks * .067f) * .03f;
        body.leftArm.xRot = idle;
        body.rightArm.xRot = -idle;
        body.leftArm.zRot = .06f;
        body.rightArm.zRot = -.06f;
        // Source suppresses the walk cycle for rollers/flyers, including their arms.
        if (legs == 0 || legs == 2) {
            body.leftLeg.xRot = stride * 1.1f;
            body.rightLeg.xRot = -stride * 1.1f;
            body.leftArm.xRot -= stride;
            body.rightArm.xRot += stride;
        } else if (legs == 1) {
            // Distance-based phase stays still when stopped and is independent of entity IDs.
            body.leftWheel.xRot = limbSwing * 1.65f;
            body.rightWheel.xRot = limbSwing * 1.65f;
        } else {
            body.chassis.y -= .35f + Mth.sin(ageInTicks * .12f) * .13f;
            body.leftRotor.yRot = ageInTicks * .19f;
            body.rightRotor.yRot = -ageInTicks * .19f;
            body.leftArm.zRot = .14f;
            body.rightArm.zRot = -.14f;
        }
        if (legs == 2) {
            body.leftLeg.zRot = -.16f;
            body.rightLeg.zRot = .16f;
            if (golem.onClimbable()) {
                body.leftArm.xRot = -2.3f + stride * .35f;
                body.rightArm.xRot = -2.3f - stride * .35f;
                body.leftLeg.xRot = .45f - stride * .45f;
                body.rightLeg.xRot = .45f + stride * .45f;
                body.chassis.xRot = -.12f;
            }
        }
        // WORKING is task ownership, not a synced impact timer: show only a ready stance.
        if (golem.hasWorkTask() && limbAmount < .12f && !golem.onClimbable()) {
            body.leftArm.xRot = -.42f + idle;
            body.rightArm.xRot = -.42f - idle;
        }
        boolean mainCarry = !golem.getMainHandItem().isEmpty();
        boolean offCarry = !golem.getOffhandItem().isEmpty();
        if (mainCarry) {
            // Source main carry raises both arms. Offhand is the actual hauler second slot.
            body.leftArm.xRot = -1.25f + idle * .5f;
            body.rightArm.xRot = -1.25f - idle * .5f;
            body.leftArm.yRot = -.12f;
            body.rightArm.yRot = .12f;
        }
        if (offCarry) {
            (golem.getMainArm() == HumanoidArm.RIGHT ? body.leftArm : body.rightArm).xRot = -1.25f;
        }
        // Vanilla's real swing packet drives melee/work swings; never infer a hit from a task.
        if (attackTime > 0) {
            ModelPart striking = golem.getMainArm() == HumanoidArm.RIGHT ? body.rightArm : body.leftArm;
            float swing = Mth.sin(Mth.sqrt(attackTime) * Mth.PI);
            striking.xRot -= swing * 1.8f;
            body.chassis.yRot = Mth.sin(attackTime * Mth.TWO_PI) * .14f;
        }
        for (Skeleton skeleton : surfaces.values()) if (skeleton != body) skeleton.copyAnimation(body);
    }

    @Override
    public void translateToHand(HumanoidArm arm, PoseStack pose) {
        body.chassis.translateAndRotate(pose);
        (arm == HumanoidArm.RIGHT ? body.rightArm : body.leftArm).translateAndRotate(pose);
        pose.translate(0, 4.75f / 16, 0);
    }

    private static LayerDefinition layer(Surface surface) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition chassis = bone(mesh.getRoot(), "chassis", 0, 18, 0);
        PartDefinition head = bone(chassis, "head", 0, -3.6f, 0);
        PartDefinition leftArm = bone(chassis, "left_arm", 2.65f, -2.7f, 0);
        PartDefinition rightArm = bone(chassis, "right_arm", -2.65f, -2.7f, 0);
        PartDefinition leftLeg = bone(chassis, "left_leg", 1.05f, 1.2f, 0);
        PartDefinition rightLeg = bone(chassis, "right_leg", -1.05f, 1.2f, 0);
        for (int i = 0; i < 5; i++) {
            headGeometry(bone(head, "variant_" + i, 0, 0, 0), surface, i);
            armGeometry(bone(leftArm, "variant_" + i, 0, 0, 0), surface, i);
            armGeometry(bone(rightArm, "variant_" + i, 0, 0, 0), surface, i);
        }
        for (int i = 0; i < 4; i++) {
            legGeometry(bone(leftLeg, "variant_" + i, 0, 0, 0), surface, i);
            legGeometry(bone(rightLeg, "variant_" + i, 0, 0, 0), surface, i);
            addonGeometry(bone(chassis, "addon_" + i, 0, 0, 0), surface, i);
        }
        if (surface == Surface.MATERIAL) {
            box(chassis, "torso", -2.2f, -3.4f, -1.6f, 4.4f, 4.6f, 3.2f);
            box(chassis, "waist", -1.6f, 1.2f, -1.1f, 3.2f, .6f, 2.2f);
        } else if (surface == Surface.DARK) {
            box(chassis, "neck", -.9f, -3.9f, -.9f, 1.8f, .6f, 1.8f);
            box(chassis, "waist_seam", -1.65f, 1.05f, -1.15f, 3.3f, .4f, 2.3f);
        } else if (surface == Surface.METAL) {
            for (int x : new int[]{-1, 1}) {
                box(chassis, "rivet_" + x, x * 1.55f - .22f, -2.8f, -1.72f, .44f, .44f, .22f);
            }
        } else if (surface == Surface.PAINT) {
            box(chassis, "front_band", -2.23f, -.7f, -1.66f, 4.46f, .65f, .12f);
            box(chassis, "back_band", -2.23f, -.7f, 1.54f, 4.46f, .65f, .12f);
            box(chassis, "left_band", -2.26f, -.7f, -1.6f, .12f, .65f, 3.2f);
            box(chassis, "right_band", 2.14f, -.7f, -1.6f, .12f, .65f, 3.2f);
        }
        return LayerDefinition.create(mesh, 16, 16);
    }

    private static void headGeometry(PartDefinition part, Surface surface, int kind) {
        boolean smart = kind == 1 || kind == 4;
        boolean scout = kind == 3 || kind == 4;
        if (surface == Surface.MATERIAL) {
            box(part, "case", -2.2f, smart ? -2.7f : -4.4f, -1.9f, 4.4f, smart ? 2.7f : 4.4f, 3.8f);
            if (kind == 2) {
                box(part, "helmet", -2.4f, -4.6f, -2.05f, 4.8f, 1.3f, 4.1f);
                box(part, "cheek_left", -2.4f, -3.3f, -2.05f, .6f, 2.8f, .6f);
                box(part, "cheek_right", 1.8f, -3.3f, -2.05f, .6f, 2.8f, .6f);
            }
        } else if (surface == Surface.DARK) {
            if (scout) {
                box(part, "goggle_left", -1.95f, -2.8f, -2.9f, 1.65f, 1.65f, 1.2f);
                box(part, "goggle_right", .3f, -2.8f, -2.9f, 1.65f, 1.65f, 1.2f);
            } else {
                box(part, "visor", -1.65f, -2.65f, -2.02f, 3.3f, 1.05f, .22f);
            }
            box(part, "mouth", -.75f, -.95f, -2.01f, 1.5f, .25f, .2f);
        } else if (surface == Surface.EYES) {
            float eyeZ = scout ? -3.01f : -2.14f;
            for (int side : new int[]{-1, 1}) {
                box(part, "eye_" + side, side * 1.08f - .32f, -2.38f, eyeZ, .64f, .6f, .16f);
            }
        } else if (surface == Surface.METAL) {
            if (smart) {
                box(part, "brain_rim", -2.3f, -2.9f, -2, 4.6f, .35f, 4);
                for (int side : new int[]{-1, 1}) {
                    box(part, "brain_guard_" + side, side * 1.9f - .15f, -4.5f, -.25f, .3f, 1.7f, .5f);
                }
            }
            if (scout) box(part, "goggle_bridge", -.45f, -2.3f, -2.7f, .9f, .5f, .5f);
            if (kind == 2) box(part, "helmet_crest", -.4f, -4.9f, -1.4f, .8f, .35f, 2.8f);
        } else if (surface == Surface.BRAIN && smart) {
            box(part, "brain_left", -1.6f, -4.35f, -1.35f, 1.45f, 1.55f, 2.7f);
            box(part, "brain_right", .15f, -4.35f, -1.35f, 1.45f, 1.55f, 2.7f);
        }
    }

    private static void armGeometry(PartDefinition part, Surface surface, int kind) {
        if (surface == Surface.MATERIAL) {
            box(part, "upper", -.6f, 0, -.65f, 1.2f, 2.5f, 1.3f);
            switch (kind) {
                case 0 -> box(part, "hand", -.75f, 2.8f, -.8f, 1.5f, 2.2f, 1.6f);
                case 1 -> {
                    box(part, "fine_wrist", -.45f, 2.7f, -.55f, .9f, 1.35f, 1.1f);
                    for (int i = 0; i < 3; i++) box(part, "finger_" + i, -.65f + i * .5f, 4, -.6f, .3f, 1.1f, 1.15f);
                }
                case 2 -> box(part, "claw_cuff", -.95f, 2.5f, -1, 1.9f, 1.8f, 2);
                case 3 -> {
                    box(part, "breaker", -1.2f, 2.7f, -1.2f, 2.4f, 2.5f, 2.4f);
                    box(part, "breaker_tip", -1.35f, 4.55f, -1.35f, 2.7f, .65f, 2.7f);
                }
                case 4 -> box(part, "dart_barrel", -.9f, 2.1f, -.9f, 1.8f, 2.75f, 1.8f);
                default -> throw new IllegalArgumentException("Unknown arm " + kind);
            }
        } else if (surface == Surface.DARK) {
            box(part, "shoulder_joint", -.7f, -.2f, -.75f, 1.4f, .65f, 1.5f);
            box(part, "elbow", -.6f, 2.3f, -.65f, 1.2f, .5f, 1.3f);
            if (kind == 3) box(part, "breaker_band", -1.25f, 3.65f, -1.25f, 2.5f, .45f, 2.5f);
            if (kind == 4) box(part, "dart_muzzle", -.7f, 4.85f, -.7f, 1.4f, .4f, 1.4f);
        } else if (surface == Surface.METAL) {
            if (kind == 1) box(part, "fine_cuff", -.65f, 2.9f, -.75f, 1.3f, .55f, 1.5f);
            if (kind == 2) {
                for (int i = 0; i < 3; i++) {
                    box(part, "claw_" + i, -.85f + i * .65f, 4.1f, -.9f, .4f, 1.3f, .5f);
                    box(part, "claw_hook_" + i, -.85f + i * .65f, 5.05f, -1.25f, .4f, .35f, .6f);
                }
            }
            if (kind == 4) {
                box(part, "dart_ring", -1.05f, 2.45f, -1.05f, 2.1f, .55f, 2.1f);
                box(part, "dart_tip", -.45f, 5.15f, -.45f, .9f, .25f, .9f);
            }
        }
    }

    private static void legGeometry(PartDefinition part, Surface surface, int kind) {
        PartDefinition wheel = kind == 1 ? bone(part, "wheel", 0, 2.8f, 0) : null;
        PartDefinition rotor = kind == 3 ? bone(part, "rotor", 0, 3, 0) : null;
        if (surface == Surface.MATERIAL) {
            if (kind == 0 || kind == 2) {
                box(part, "shin", -.55f, 0, -.65f, 1.1f, 3.6f, 1.3f);
                box(part, "foot", -.8f, 3.3f, -1.2f, 1.6f, 1.5f, 2.4f);
            } else if (kind == 1) {
                box(part, "wheel_fork", -.45f, 0, -.4f, .9f, 2.8f, .8f);
            } else {
                box(part, "levitator_stem", -.5f, 0, -.5f, 1, 2.5f, 1);
                box(part, "levitator_pod", -.9f, 2.1f, -.9f, 1.8f, .9f, 1.8f);
            }
        } else if (surface == Surface.DARK) {
            if (kind == 1) {
                // Every corner stays within radius 2px, even while rotating at floor level.
                box(wheel, "tyre_center", -.7f, -1.4f, -1.4f, 1.4f, 2.8f, 2.8f);
                box(wheel, "tyre_vertical", -.7f, -1.85f, -.75f, 1.4f, 3.7f, 1.5f);
                box(wheel, "tyre_horizontal", -.7f, -.75f, -1.85f, 1.4f, 1.5f, 3.7f);
            } else if (kind == 3) {
                box(part, "levitator_nozzle", -.6f, 3.1f, -.6f, 1.2f, .7f, 1.2f);
            } else {
                box(part, "knee", -.6f, 1.5f, -.7f, 1.2f, .6f, 1.4f);
            }
        } else if (surface == Surface.METAL) {
            if (kind == 1) {
                box(wheel, "axle", -.88f, -.5f, -.5f, 1.76f, 1, 1);
                for (int side : new int[]{-1, 1}) {
                    box(wheel, "spoke_vertical_" + side, side * .75f - .08f, -1.5f, -.17f, .16f, 3, .34f);
                    box(wheel, "spoke_horizontal_" + side, side * .75f - .08f, -.17f, -1.5f, .16f, .34f, 3);
                }
            } else if (kind == 2) {
                for (int i = 0; i < 3; i++) box(part, "grip_" + i, -.72f + i * .55f, 4.3f, -1.75f, .34f, .45f, 1.1f);
                box(part, "heel_grip", -.3f, 4.3f, .9f, .6f, .45f, .8f);
            } else if (kind == 3) {
                box(rotor, "rotor_x", -1.5f, -.1f, -.25f, 3, .2f, .5f);
                box(rotor, "rotor_z", -.25f, -.1f, -1.5f, .5f, .2f, 3);
            }
        } else if (surface == Surface.EYES && kind == 3) {
            box(part, "levitator_core", -.42f, 3.8f, -.42f, .84f, .45f, .84f);
        }
    }

    private static void addonGeometry(PartDefinition part, Surface surface, int kind) {
        if (kind == 0) return;
        if (surface == Surface.MATERIAL) {
            if (kind == 1) {
                box(part, "breastplate", -2.45f, -3.1f, -1.95f, 4.9f, 4.1f, .65f);
                box(part, "backplate", -2.45f, -3.1f, 1.3f, 4.9f, 4.1f, .65f);
            } else if (kind == 3) {
                box(part, "hauler_pack", -2, -3, 1.8f, 4, 3.8f, 1.8f);
                box(part, "hauler_left_pouch", -3.1f, -.8f, -.3f, 1, 2, 1.8f);
                box(part, "hauler_right_pouch", 2.1f, -.8f, -.3f, 1, 2, 1.8f);
            }
        } else if (surface == Surface.DARK && kind == 3) {
            box(part, "pack_lid", -2.15f, -3.15f, 1.7f, 4.3f, .55f, 2);
            for (int side : new int[]{-1, 1}) {
                box(part, "strap_" + side, side * 1.35f - .2f, -3.5f, -1.78f, .4f, 4.7f, .25f);
            }
        } else if (surface == Surface.METAL) {
            if (kind == 1) {
                box(part, "armor_collar", -2.5f, -3.45f, -2, 5, .5f, 4);
                box(part, "armor_boss", -.7f, -2.55f, -2.1f, 1.4f, 1.4f, .3f);
            } else if (kind == 2) {
                box(part, "fighter_crest", -.5f, -3.1f, -1.9f, 1, 2.2f, .4f);
                box(part, "fighter_crossbar", -1.3f, -2.5f, -1.95f, 2.6f, .5f, .5f);
                box(part, "fighter_left_guard", -2.9f, -3.7f, -.7f, .65f, 1.3f, 1.4f);
                box(part, "fighter_right_guard", 2.25f, -3.7f, -.7f, .65f, 1.3f, 1.4f);
            } else {
                box(part, "pack_clasp", -.4f, -2.2f, 3.55f, .8f, .9f, .2f);
            }
        } else if (surface == Surface.PAINT && kind == 1) {
            box(part, "armor_band", -2.46f, -.7f, -2.02f, 4.92f, .65f, .12f);
        }
    }

    private static PartDefinition bone(PartDefinition parent, String name, float x, float y, float z) {
        return parent.addOrReplaceChild(name, CubeListBuilder.create(), PartPose.offset(x, y, z));
    }

    private static void box(PartDefinition parent, String name, float x, float y, float z,
                            float width, float height, float depth) {
        parent.addOrReplaceChild(name, CubeListBuilder.create().texOffs(0, 0)
                .addBox(x, y, z, width, height, depth), PartPose.ZERO);
    }

    /** Identical attachment skeletons keep material and inlay passes aligned without entity caches. */
    private static final class Skeleton {
        final ModelPart root, chassis, head, leftArm, rightArm, leftLeg, rightLeg;
        final ModelPart leftWheel, rightWheel, leftRotor, rightRotor;

        Skeleton(ModelPart root) {
            this.root = root;
            chassis = root.getChild("chassis");
            head = chassis.getChild("head");
            leftArm = chassis.getChild("left_arm");
            rightArm = chassis.getChild("right_arm");
            leftLeg = chassis.getChild("left_leg");
            rightLeg = chassis.getChild("right_leg");
            leftWheel = leftLeg.getChild("variant_1").getChild("wheel");
            rightWheel = rightLeg.getChild("variant_1").getChild("wheel");
            leftRotor = leftLeg.getChild("variant_3").getChild("rotor");
            rightRotor = rightLeg.getChild("variant_3").getChild("rotor");
        }

        void select(int headId, int armId, int legId, int addonId) {
            for (int i = 0; i < 5; i++) {
                head.getChild("variant_" + i).visible = i == headId;
                leftArm.getChild("variant_" + i).visible = i == armId;
                rightArm.getChild("variant_" + i).visible = i == armId;
            }
            for (int i = 0; i < 4; i++) {
                leftLeg.getChild("variant_" + i).visible = i == legId;
                rightLeg.getChild("variant_" + i).visible = i == legId;
                chassis.getChild("addon_" + i).visible = i == addonId;
            }
        }

        void copyAnimation(Skeleton from) {
            chassis.copyFrom(from.chassis);
            head.copyFrom(from.head);
            leftArm.copyFrom(from.leftArm);
            rightArm.copyFrom(from.rightArm);
            leftLeg.copyFrom(from.leftLeg);
            rightLeg.copyFrom(from.rightLeg);
            leftWheel.copyFrom(from.leftWheel);
            rightWheel.copyFrom(from.rightWheel);
            leftRotor.copyFrom(from.leftRotor);
            rightRotor.copyFrom(from.rightRotor);
        }

        void resetAnimation() {
            chassis.resetPose();
            head.resetPose();
            leftArm.resetPose();
            rightArm.resetPose();
            leftLeg.resetPose();
            rightLeg.resetPose();
            leftWheel.resetPose();
            rightWheel.resetPose();
            leftRotor.resetPose();
            rightRotor.resetPose();
        }
    }
}
