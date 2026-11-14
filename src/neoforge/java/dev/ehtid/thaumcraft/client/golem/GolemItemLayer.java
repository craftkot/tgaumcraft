package dev.ehtid.thaumcraft.client.golem;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Render each actual synchronized carry slot at this small model's hand attachment. */
public final class GolemItemLayer extends ItemInHandLayer<ThaumcraftGolem, GolemModel> {
    private final ItemInHandRenderer items;

    public GolemItemLayer(RenderLayerParent<ThaumcraftGolem, GolemModel> parent, ItemInHandRenderer items) {
        super(parent, items);
        this.items = items;
    }

    @Override
    protected void renderArmWithItem(LivingEntity golem, ItemStack stack, ItemDisplayContext display,
                                     HumanoidArm arm, PoseStack pose, MultiBufferSource buffers, int light) {
        if (stack.isEmpty()) return;
        pose.pushPose();
        getParentModel().translateToHand(arm, pose);
        pose.mulPose(Axis.XP.rotationDegrees(-90));
        pose.mulPose(Axis.YP.rotationDegrees(180));
        pose.scale(.45f, .45f, .45f);
        // Vanilla's humanoid offset is 10px from its wrist; use this model's actual hand origin.
        pose.translate(0, .04f, -.1f);
        items.renderItem(golem, stack, display, arm == HumanoidArm.LEFT, pose, buffers, light);
        pose.popPose();
    }
}
