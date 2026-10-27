package dev.ehtid.thaumcraft.client;

import com.mojang.blaze3d.vertex.*;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL11;
import net.minecraft.client.renderer.RenderType;
import java.util.OptionalDouble;

/** Scoped render states restore depth/blend automatically after each batch. */
final class GolemSealRenderTypes extends RenderType {
    private GolemSealRenderTypes() {
        super("unused_golem_seal",DefaultVertexFormat.POSITION_COLOR_NORMAL,VertexFormat.Mode.LINES,256,false,false,()->{},()->{});
    }
    // Vanilla NO_DEPTH_TEST is a no-op, so AFTER_LEVEL can retain the world's enabled depth test.
    private static final DepthTestStateShard THROUGH_DEPTH = new DepthTestStateShard("golem_explicit_no_depth",519) {
        private boolean wasEnabled,checked;
        @Override public void setupRenderState() {
            RenderSystem.assertOnRenderThread();wasEnabled=GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
            RenderSystem.disableDepthTest();
            if(!checked&&Boolean.getBoolean("thaumcraft.golemClientProbe")) {
                if(GL11.glIsEnabled(GL11.GL_DEPTH_TEST)) throw new AssertionError("Actual seal through-depth draw kept GL depth testing enabled");
                checked=true;com.mojang.logging.LogUtils.getLogger().info("GOLEM_CLIENT_SEAL_EXPLICIT_DEPTH_DISABLED previous={}",wasEnabled);
            }
        }
        @Override public void clearRenderState() {
            if(wasEnabled)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();
        }
    };
    static final RenderType NORMAL = lines(false), THROUGH = lines(true);
    private static RenderType lines(boolean through) {
        return create(through?"golem_seal_through":"golem_seal",DefaultVertexFormat.POSITION_COLOR_NORMAL,VertexFormat.Mode.LINES,1536,
                CompositeState.builder().setShaderState(RENDERTYPE_LINES_SHADER)
                        .setLineState(new LineStateShard(OptionalDouble.empty())).setLayeringState(VIEW_OFFSET_Z_LAYERING)
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setDepthTestState(through?THROUGH_DEPTH:LEQUAL_DEPTH_TEST)
                        .setWriteMaskState(COLOR_WRITE).setCullState(NO_CULL).createCompositeState(false));
    }
}
