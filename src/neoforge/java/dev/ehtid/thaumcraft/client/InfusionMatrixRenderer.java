package dev.ehtid.thaumcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.ehtid.thaumcraft.blockentity.InfusionMatrixBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.phys.AABB;

import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;

/**
 * Procedural BETA26 matrix motion: eight .45 cubes, source startup/oscillation,
 * active additive glow and crafting halo. Uses the existing interim stone
 * sprite; the original infuser textures and their UV layout are not ported here.
 */
public final class InfusionMatrixRenderer implements BlockEntityRenderer<InfusionMatrixBlockEntity> {
    private static final ResourceLocation STONE = ResourceLocation.fromNamespaceAndPath(
            "thaumcraft", "block/pedestal_side");
    private static final RenderType GLOW = RenderType.EYES.apply(
            InventoryMenu.BLOCK_ATLAS, RenderStateShard.LIGHTNING_TRANSPARENCY);
    private final Map<InfusionMatrixBlockEntity, Motion> motion = new WeakHashMap<>();

    public InfusionMatrixRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public AABB getRenderBoundingBox(InfusionMatrixBlockEntity tile) {
        // Rotated corner cubes and the source halo extend outside the unit block.
        return new AABB(tile.getBlockPos()).inflate(1D);
    }

    @Override
    public void render(InfusionMatrixBlockEntity tile, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light, int overlay) {
        // The static MODEL must be disabled by the block before replacing it.
        // Keeping this guard makes an incomplete integration render once.
        if (tile.getLevel() == null || tile.getBlockState().getRenderShape() != RenderShape.ENTITYBLOCK_ANIMATED)
            return;
        long tick = tile.getLevel().getGameTime();
        Motion state = motion.computeIfAbsent(tile, ignored -> new Motion(tick));
        state.advance(tick, tile.active(), tile.crafting());
        float startup = Mth.lerp(partialTick, state.previousStartup, state.startup);
        float craftCount = Mth.lerp(partialTick, state.previousCraftCount, state.craftCount);
        var camera = Minecraft.getInstance().getCameraEntity();
        float age = (camera == null ? tick : camera.tickCount) + partialTick;
        float instability = Math.min(6F, 1F + (tile.stability() < 0F ? -tile.stability() * .66F : 1F)
                * Math.min(craftCount, 50F) / 50F);
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(STONE);

        pose.pushPose();
        pose.translate(.5, .5, .5);
        // TileInfusionMatrixRenderer.renderInfusionMatrix:69..108.
        pose.mulPose(Axis.YP.rotationDegrees((age % 360F) * startup));
        pose.mulPose(Axis.XP.rotationDegrees(35F * startup));
        pose.mulPose(Axis.ZP.rotationDegrees(45F * startup));
        // Finish all opaque parts before acquiring the glow buffer. BufferSource
        // may flush its preceding non-fixed layer when a different type is requested.
        for (int pass = 0; pass < (tile.active() ? 2 : 1); pass++) {
            VertexConsumer vertices = buffers.getBuffer(pass == 0
                    ? RenderType.entitySolid(InventoryMenu.BLOCK_ATLAS) : GLOW);
            for (int x = 0; x < 2; x++) for (int y = 0; y < 2; y++) for (int z = 0; z < 2; z++) {
                pose.pushPose();
                float dx = tile.active() ? Mth.sin((age + x * 10F) / 15F) * .01F * startup * instability : 0F;
                float dy = tile.active() ? Mth.sin((age + y * 10F) / 14F) * .01F * startup * instability : 0F;
                float dz = tile.active() ? Mth.sin((age + z * 10F) / 13F) * .01F * startup * instability : 0F;
                pose.translate((x == 0 ? -.25F : .25F) + dx,
                        (y == 0 ? -.25F : .25F) + dy, (z == 0 ? -.25F : .25F) + dz);
                if (x != 0) pose.mulPose(Axis.XP.rotationDegrees(90F));
                if (y != 0) pose.mulPose(Axis.YP.rotationDegrees(90F));
                if (z != 0) pose.mulPose(Axis.ZP.rotationDegrees(90F));
                if (pass == 0) {
                    cube(vertices, pose, sprite, .225F, light, overlay, 255, 255, 255, 255);
                } else {
                    int alpha = Math.round(255F * (.2F + .1F * Mth.sin(
                            (age + x * 2F + y * 3F + z * 4F) / 4F)) * startup);
                    // Source additive SRC_ALPHA/ONE; tiny expansion avoids depth fighting.
                    cube(vertices, pose, sprite, .22525F,
                            LightTexture.FULL_BRIGHT, overlay, 204, 26, 255, alpha);
                }
                pose.popPose();
            }
        }
        pose.popPose();
        if (tile.crafting()) halo(pose, buffers, craftCount);
    }

    /** Original drawHalo uses seeded triangular fans, not a texture or a particle asset. */
    private static void halo(PoseStack pose, MultiBufferSource buffers, float craftCount) {
        int alpha = Math.round(255F * Math.max(0F, 1F - craftCount / 500F));
        float growth = Math.min(craftCount, 50F) / 1000F;
        if (alpha <= 0 || growth <= 0F) return;
        var random = new Random(245L);
        VertexConsumer triangles = buffers.getBuffer(RenderType.dragonRays());
        pose.pushPose();
        pose.translate(.5, .5, .5);
        int rays = Minecraft.useFancyGraphics() ? 20 : 10;
        for (int ray = 0; ray < rays; ray++) {
            pose.mulPose(Axis.XP.rotationDegrees(random.nextFloat() * 360F));
            pose.mulPose(Axis.YP.rotationDegrees(random.nextFloat() * 360F));
            pose.mulPose(Axis.ZP.rotationDegrees(random.nextFloat() * 360F));
            pose.mulPose(Axis.XP.rotationDegrees(random.nextFloat() * 360F));
            pose.mulPose(Axis.YP.rotationDegrees(random.nextFloat() * 360F));
            pose.mulPose(Axis.ZP.rotationDegrees(random.nextFloat() * 360F + craftCount / 500F * 360F));
            float length = (random.nextFloat() * 20F + 5F) * growth;
            float width = (random.nextFloat() * 2F + 1F) * growth;
            float ax = -.866F * width, az = -.5F * width;
            float bx = .866F * width, bz = -.5F * width;
            triangle(triangles, pose, ax, length, az, bx, length, bz, alpha);
            triangle(triangles, pose, bx, length, bz, 0F, length, width, alpha);
            triangle(triangles, pose, 0F, length, width, ax, length, az, alpha);
        }
        pose.popPose();
    }

    private static void triangle(VertexConsumer out, PoseStack pose, float ax, float ay, float az,
                                 float bx, float by, float bz, int alpha) {
        out.addVertex(pose.last(), 0F, 0F, 0F).setColor(255, 255, 255, alpha);
        out.addVertex(pose.last(), ax, ay, az).setColor(255, 0, 255, 0);
        out.addVertex(pose.last(), bx, by, bz).setColor(255, 0, 255, 0);
    }

    private static void cube(VertexConsumer out, PoseStack pose, TextureAtlasSprite sprite,
                             float h, int light, int overlay, int r, int g, int b, int a) {
        quad(out,pose,sprite,light,overlay,r,g,b,a,0,1,0, -h,h,-h, -h,h,h, h,h,h, h,h,-h);
        quad(out,pose,sprite,light,overlay,r,g,b,a,0,-1,0, -h,-h,h, -h,-h,-h, h,-h,-h, h,-h,h);
        quad(out,pose,sprite,light,overlay,r,g,b,a,0,0,-1, h,-h,-h, -h,-h,-h, -h,h,-h, h,h,-h);
        quad(out,pose,sprite,light,overlay,r,g,b,a,0,0,1, -h,-h,h, h,-h,h, h,h,h, -h,h,h);
        quad(out,pose,sprite,light,overlay,r,g,b,a,-1,0,0, -h,-h,-h, -h,-h,h, -h,h,h, -h,h,-h);
        quad(out,pose,sprite,light,overlay,r,g,b,a,1,0,0, h,-h,h, h,-h,-h, h,h,-h, h,h,h);
    }

    private static void quad(VertexConsumer out, PoseStack pose, TextureAtlasSprite sprite,
                             int light, int overlay, int r, int g, int b, int a, float nx, float ny, float nz,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz) {
        vertex(out,pose,ax,ay,az,sprite.getU0(),sprite.getV1(),light,overlay,r,g,b,a,nx,ny,nz);
        vertex(out,pose,bx,by,bz,sprite.getU1(),sprite.getV1(),light,overlay,r,g,b,a,nx,ny,nz);
        vertex(out,pose,cx,cy,cz,sprite.getU1(),sprite.getV0(),light,overlay,r,g,b,a,nx,ny,nz);
        vertex(out,pose,dx,dy,dz,sprite.getU0(),sprite.getV0(),light,overlay,r,g,b,a,nx,ny,nz);
    }

    private static void vertex(VertexConsumer out, PoseStack pose, float x, float y, float z, float u, float v,
                               int light, int overlay, int r, int g, int b, int a, float nx, float ny, float nz) {
        out.addVertex(pose.last(),x,y,z).setColor(r,g,b,a).setUv(u,v)
                .setOverlay(overlay).setLight(light).setNormal(pose.last(),nx,ny,nz);
    }

    /** Client-only display state; it never changes the matrix or spends resources. */
    private static final class Motion {
        private long lastTick;
        private float startup, previousStartup;
        private int craftCount, previousCraftCount;
        private Motion(long tick) { lastTick = tick - 1; }
        private void advance(long tick, boolean active, boolean crafting) {
            long elapsed = tick - lastTick;
            if (elapsed <= 0) return;
            int steps = (int)Math.min(elapsed, 256L);
            for (int step = 0; step < steps; step++) {
                previousStartup = startup;
                previousCraftCount = craftCount;
                // TileInfusionMatrix.doEffects:207..372, one update per client tick.
                if (crafting && craftCount < Integer.MAX_VALUE) craftCount++;
                else if (craftCount > 0) craftCount = Math.min(50, Math.max(0, craftCount - 2));
                if (active && startup < 1F) {
                    startup += Math.max(startup / 10F, .001F);
                    if (startup > .999F) startup = 1F;
                } else if (!active && startup > 0F) {
                    startup -= startup / 10F;
                    if (startup < .001F) startup = 0F;
                }
            }
            if (elapsed > steps) {
                // Both startup recurrences have settled after 256 identical updates.
                previousStartup = startup = active ? 1F : 0F;
                craftCount = crafting ? (int)Math.min(Integer.MAX_VALUE,
                        (long)craftCount + elapsed - steps) : 0;
                previousCraftCount = crafting ? Math.max(0, craftCount - 1) : 0;
            }
            lastTick = tick;
        }
    }
}
