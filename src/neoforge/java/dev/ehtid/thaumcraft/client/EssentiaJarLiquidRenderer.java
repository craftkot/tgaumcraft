package dev.ehtid.thaumcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.blockentity.EssentiaJarBlockEntity;
import dev.ehtid.thaumcraft.port.core.AspectRegistry;
import dev.ehtid.thaumcraft.port.core.LegacyEssentiaJarLogic;
import dev.ehtid.thaumcraft.port.core.LegacyEssentiaJarRenderLogic;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** BETA26 TileJarRenderer.renderLiquid port; the block model still renders the glass. */
public final class EssentiaJarLiquidRenderer implements BlockEntityRenderer<EssentiaJarBlockEntity> {
    private static final ResourceLocation LIQUID_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ThaumcraftPort.MOD_ID, "block/animatedglow");
    private static final AspectRegistry ASPECTS = new AspectRegistry();
    private static final boolean SMOKE_PROBE = Boolean.getBoolean("thaumcraft.jarLiquidSmoke");
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

    public EssentiaJarLiquidRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(EssentiaJarBlockEntity jar, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        LegacyEssentiaJarLogic.State state = jar.snapshot();
        int amount = state.amount();
        BlockPos pos = jar.getBlockPos();

        if (!LegacyEssentiaJarRenderLogic.hasLiquid(amount)) {
            if (SMOKE_PROBE && REPORTED.add("empty:" + pos.asLong())) {
                System.out.printf(Locale.ROOT,
                        "TC_SMOKE_JAR_LIQUID_EMPTY_SKIPPED=YES pos=%d,%d,%d amount=%d%n",
                        pos.getX(), pos.getY(), pos.getZ(), amount);
            }
            return;
        }

        TextureAtlasSprite sprite = Minecraft.getInstance()
                .getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(LIQUID_TEXTURE);
        if (!sprite.contents().name().equals(LIQUID_TEXTURE)) {
            throw new IllegalStateException("Missing BETA26 essentia jar liquid atlas sprite: " + LIQUID_TEXTURE);
        }

        int rgb = LegacyEssentiaJarRenderLogic.aspectColor(ASPECTS, state.aspect());
        int red = (rgb >>> 16) & 0xFF;
        int green = (rgb >>> 8) & 0xFF;
        int blue = rgb & 0xFF;

        float minX = LegacyEssentiaJarRenderLogic.LIQUID_MIN_X;
        float maxX = LegacyEssentiaJarRenderLogic.LIQUID_MAX_X;
        float minY = LegacyEssentiaJarRenderLogic.LIQUID_BASE_Y;
        float maxY = LegacyEssentiaJarRenderLogic.liquidTopY(amount);
        float minZ = LegacyEssentiaJarRenderLogic.LIQUID_MIN_Z;
        float maxZ = LegacyEssentiaJarRenderLogic.LIQUID_MAX_Z;

        // BETA26 passes block lightmap value 200 (about 12.5/15); 13 is
        // the nearest representable modern block-light level, with no sky light.
        int light = LightTexture.pack(13, 0);
        VertexConsumer out = buffers.getBuffer(RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS));
        float u0 = lerp(sprite.getU0(), sprite.getU1(), 0.25F);
        float u1 = lerp(sprite.getU0(), sprite.getU1(), 0.75F);
        float v0 = lerp(sprite.getV0(), sprite.getV1(), 0.25F);
        float v1 = lerp(sprite.getV0(), sprite.getV1(), 0.75F);

        // BETA26 RenderCubes emits all six faces for the 0.25..0.75 inner volume.
        quad(out, poseStack, light, packedOverlay, red, green, blue, 0, -1, 0,
                minX, minY, maxZ, u0, v1,
                minX, minY, minZ, u0, v0,
                maxX, minY, minZ, u1, v0,
                maxX, minY, maxZ, u1, v1);
        quad(out, poseStack, light, packedOverlay, red, green, blue, 0, 1, 0,
                minX, maxY, minZ, u0, v0,
                minX, maxY, maxZ, u0, v1,
                maxX, maxY, maxZ, u1, v1,
                maxX, maxY, minZ, u1, v0);
        quad(out, poseStack, light, packedOverlay, red, green, blue, 0, 0, -1,
                minX, minY, minZ, u0, v1,
                minX, maxY, minZ, u0, v0,
                maxX, maxY, minZ, u1, v0,
                maxX, minY, minZ, u1, v1);
        quad(out, poseStack, light, packedOverlay, red, green, blue, 0, 0, 1,
                maxX, minY, maxZ, u1, v1,
                maxX, maxY, maxZ, u1, v0,
                minX, maxY, maxZ, u0, v0,
                minX, minY, maxZ, u0, v1);
        quad(out, poseStack, light, packedOverlay, red, green, blue, -1, 0, 0,
                minX, minY, maxZ, u1, v1,
                minX, maxY, maxZ, u1, v0,
                minX, maxY, minZ, u0, v0,
                minX, minY, minZ, u0, v1);
        quad(out, poseStack, light, packedOverlay, red, green, blue, 1, 0, 0,
                maxX, minY, minZ, u0, v1,
                maxX, maxY, minZ, u0, v0,
                maxX, maxY, maxZ, u1, v0,
                maxX, minY, maxZ, u1, v1);

        if (SMOKE_PROBE) {
            String key = pos.asLong() + ":" + amount + ":" + state.aspect();
            if (REPORTED.add(key)) {
                System.out.printf(Locale.ROOT,
                        "TC_SMOKE_JAR_LIQUID_RENDERED=YES pos=%d,%d,%d amount=%d aspect=%s rgb=%06X topY=%.4f texture=%s%n",
                        pos.getX(), pos.getY(), pos.getZ(), amount,
                        state.aspect() == null ? "none" : state.aspect(), rgb, maxY, LIQUID_TEXTURE);
            }
        }
    }

    private static float lerp(float from, float to, float amount) {
        return from + (to - from) * amount;
    }

    private static void quad(VertexConsumer out, PoseStack pose, int light, int overlay,
                             int red, int green, int blue, float nx, float ny, float nz,
                             float ax, float ay, float az, float au, float av,
                             float bx, float by, float bz, float bu, float bv,
                             float cx, float cy, float cz, float cu, float cv,
                             float dx, float dy, float dz, float du, float dv) {
        vertex(out, pose, ax, ay, az, au, av, light, overlay, red, green, blue, nx, ny, nz);
        vertex(out, pose, bx, by, bz, bu, bv, light, overlay, red, green, blue, nx, ny, nz);
        vertex(out, pose, cx, cy, cz, cu, cv, light, overlay, red, green, blue, nx, ny, nz);
        vertex(out, pose, dx, dy, dz, du, dv, light, overlay, red, green, blue, nx, ny, nz);
    }

    private static void vertex(VertexConsumer out, PoseStack pose, float x, float y, float z,
                               float u, float v, int light, int overlay,
                               int red, int green, int blue, float nx, float ny, float nz) {
        out.addVertex(pose.last(), x, y, z)
                .setColor(red, green, blue, 255)
                .setUv(u, v)
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(pose.last(), nx, ny, nz);
    }
}
