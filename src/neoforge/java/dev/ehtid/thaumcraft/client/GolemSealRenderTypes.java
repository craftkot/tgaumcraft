package dev.ehtid.thaumcraft.client;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.RenderType;
import java.util.OptionalDouble;

/** Scoped render states restore depth/blend automatically after each batch. */
final class GolemSealRenderTypes extends RenderType {
    private GolemSealRenderTypes() {
        super("unused_golem_seal",DefaultVertexFormat.POSITION_COLOR_NORMAL,VertexFormat.Mode.LINES,256,false,false,()->{},()->{});
    }
    static final RenderType NORMAL = lines(false), THROUGH = lines(true);
    private static RenderType lines(boolean through) {
        return create(through?"golem_seal_through":"golem_seal",DefaultVertexFormat.POSITION_COLOR_NORMAL,VertexFormat.Mode.LINES,1536,
                CompositeState.builder().setShaderState(RENDERTYPE_LINES_SHADER)
                        .setLineState(new LineStateShard(OptionalDouble.empty())).setLayeringState(VIEW_OFFSET_Z_LAYERING)
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setDepthTestState(through?NO_DEPTH_TEST:LEQUAL_DEPTH_TEST)
                        .setWriteMaskState(COLOR_WRITE).setCullState(NO_CULL).createCompositeState(false));
    }
}
