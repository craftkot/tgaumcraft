package dev.ehtid.thaumcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Provisional original model for the first wooden runtime golem.
 * Geometry and animations are newly authored; this is not BETA26 visual parity.
 * Vanilla wood keeps the renderer independent of original texture permissions.
 */
@EventBusSubscriber(modid = ThaumcraftPort.MOD_ID,
        bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class GolemRenderer extends MobRenderer<ThaumcraftGolem, GolemRenderer.Model> {
    private static final ResourceLocation WOOD =
            ResourceLocation.withDefaultNamespace("textures/block/oak_planks.png");

    public GolemRenderer(EntityRendererProvider.Context context) {
        super(context, new Model(Model.layer().bakeRoot()), 0.2f);
        addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
    }

    @SubscribeEvent
    public static void registerRenderer(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(GolemModule.GOLEM.get(), GolemRenderer::new);
    }

    @Override
    public ResourceLocation getTextureLocation(ThaumcraftGolem golem) {
        return WOOD;
    }

    public static final class Model extends HierarchicalModel<ThaumcraftGolem> implements ArmedModel {
        private final ModelPart root;
        private final ModelPart head;
        private final ModelPart leftArm;
        private final ModelPart rightArm;
        private final ModelPart leftLeg;
        private final ModelPart rightLeg;

        private Model(ModelPart root) {
            this.root = root;
            head = root.getChild("head");
            leftArm = root.getChild("left_arm");
            rightArm = root.getChild("right_arm");
            leftLeg = root.getChild("left_leg");
            rightLeg = root.getChild("right_leg");
        }

        private static LayerDefinition layer() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();
            // Model floor y=24; total height 14.4px=.9 blocks, width6.4px=.4 blocks.
            root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0)
                    .addBox(-2, -5.4f, -2, 4, 5.4f, 4), PartPose.offset(0, 15, 0));
            root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0)
                    .addBox(-2, -4, -1.5f, 4, 4, 3), PartPose.offset(0, 19, 0));
            root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(0, 0)
                    .addBox(0, 0, -0.75f, 1.2f, 4.5f, 1.5f), PartPose.offset(2, 16, 0));
            root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(0, 0)
                    .addBox(-1.2f, 0, -0.75f, 1.2f, 4.5f, 1.5f), PartPose.offset(-2, 16, 0));
            root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(0, 0)
                    .addBox(-0.75f, 0, -1, 1.5f, 5, 2), PartPose.offset(1, 19, 0));
            root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 0)
                    .addBox(-0.75f, 0, -1, 1.5f, 5, 2), PartPose.offset(-1, 19, 0));
            return LayerDefinition.create(mesh, 16, 16);
        }

        @Override
        public ModelPart root() {
            return root;
        }

        @Override
        public void setupAnim(ThaumcraftGolem golem, float limbSwing, float limbAmount,
                              float ageInTicks, float headYaw, float headPitch) {
            root.getAllParts().forEach(ModelPart::resetPose);
            head.yRot = headYaw * Mth.DEG_TO_RAD;
            head.xRot = headPitch * Mth.DEG_TO_RAD;
            float stride = Mth.cos(limbSwing * 0.6662f) * limbAmount * 1.2f;
            leftLeg.xRot = stride;
            rightLeg.xRot = -stride;
            leftArm.xRot = -stride;
            rightArm.xRot = stride;
            if (!golem.getMainHandItem().isEmpty()) {
                (golem.getMainArm() == HumanoidArm.RIGHT ? rightArm : leftArm).xRot = -1.0f;
            }
            if (!golem.getOffhandItem().isEmpty()) {
                (golem.getMainArm() == HumanoidArm.RIGHT ? leftArm : rightArm).xRot = -1.0f;
            }
        }

        @Override
        public void translateToHand(HumanoidArm arm, PoseStack pose) {
            (arm == HumanoidArm.RIGHT ? rightArm : leftArm).translateAndRotate(pose);
            pose.translate(0, 0.17, 0);
        }
    }
}
