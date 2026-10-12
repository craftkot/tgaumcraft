package dev.ehtid.thaumcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ehtid.thaumcraft.blockentity.CrucibleBlockEntity;
import dev.ehtid.thaumcraft.port.core.LegacyCrucibleSurface;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;

/**
 * BETA26 TileCrucibleRenderer's water plane: real water atlas texture,
 * fluid-tank-dependent height, and essentia-dependent color/opacity.
 * Empty crucibles render no water. All numbers come from BETA26 bytecode.
 */
public final class CrucibleFluidRenderer implements BlockEntityRenderer<CrucibleBlockEntity> {
    private static final ResourceLocation STILL_WATER =
            ResourceLocation.withDefaultNamespace("block/water_still");

    public CrucibleFluidRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(CrucibleBlockEntity tile, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        if (tile.waterMb() <= 0) return;
        int stored = tile.getAspects().visSize();
        float y = LegacyCrucibleSurface.height(tile.waterMb(), stored);
        TextureAtlasSprite sprite = Minecraft.getInstance()
                .getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(STILL_WATER);
        VertexConsumer vertices = buffers.getBuffer(RenderType.translucent());
        int r = Math.round(255.0f * LegacyCrucibleSurface.red(stored));
        int g = Math.round(255.0f * LegacyCrucibleSurface.green(stored));
        int b = Math.round(255.0f * LegacyCrucibleSurface.blue(stored));
        int a = Math.round(255.0f * LegacyCrucibleSurface.alpha(stored));
        // The bowl's inside walls begin at 2px and end at 14px.
        // Inset 1/1024 further to avoid depth-fighting with interior edges.
        float min = 2.0f / 16.0f + 0.001f;
        float max = 14.0f / 16.0f - 0.001f;
        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();

        vertex(vertices, pose, min, y, min, u0, v0, light, overlay, r, g, b, a);
        vertex(vertices, pose, min, y, max, u0, v1, light, overlay, r, g, b, a);
        vertex(vertices, pose, max, y, max, u1, v1, light, overlay, r, g, b, a);
        vertex(vertices, pose, max, y, min, u1, v0, light, overlay, r, g, b, a);
    }

    private static void vertex(VertexConsumer buffer, PoseStack pose,
                               float x, float y, float z, float u, float v,
                               int light, int overlay, int r, int g, int b, int a) {
        buffer.addVertex(pose.last(), x, y, z)
                .setColor(r, g, b, a)
                .setUv(u, v)
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(pose.last(), 0.0f, 1.0f, 0.0f);
    }
}
