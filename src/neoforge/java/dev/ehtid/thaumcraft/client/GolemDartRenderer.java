package dev.ehtid.thaumcraft.client;

import dev.ehtid.thaumcraft.entity.GolemDart;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;

/** Provisional vanilla arrow visual; original Thaumcraft dart artwork is not redistributed. */
public final class GolemDartRenderer extends ArrowRenderer<GolemDart> {
    private static boolean probeChecked;
    public GolemDartRenderer(EntityRendererProvider.Context context) { super(context); }
    @Override public ResourceLocation getTextureLocation(GolemDart dart) { return ResourceLocation.withDefaultNamespace("textures/entity/projectiles/arrow.png"); }
    @Override public void render(GolemDart dart,float yaw,float partialTick,PoseStack pose,MultiBufferSource buffers,int light) {
        super.render(dart,yaw,partialTick,pose,buffers,light);
        if(!probeChecked&&Boolean.getBoolean("thaumcraft.golemClientProbe")) {
            if(!dart.level().isClientSide()||Math.abs(dart.getBbWidth()-.2F)>1e-6||Minecraft.getInstance().getResourceManager().getResource(getTextureLocation(dart)).isEmpty()) throw new AssertionError("Actual rendered dart must have expected client type/size/resolved texture");
            probeChecked=true; LogUtils.getLogger().info("GOLEM_CLIENT_DART_RENDER_ASSERTIONS_OK");
        }
    }
}
