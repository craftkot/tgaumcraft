package dev.ehtid.thaumcraft.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ehtid.thaumcraft.port.core.LegacyScanRuneDuration;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * BETA26 FXBlockRunes projection for Thaumometer scan feedback:
 * lifetime=3*m (m=15 for blocks, floor(entity height*15) for entities),
 * gravity=.03, fullbright, random one of sixteen original rune sprites,
 * quarter-turn rotation, 0.3 scale with gaussian variation and the exact fade curve.
 */
public final class ScanRuneParticle extends TextureSheetParticle {
    // FXBlockRunes.renderParticle runs after the active camera view matrix.
    // Its Y and Z GL rotations are therefore camera-relative, not world-Y.
    final float legacyCameraYaw;
    final float localOffsetX;
    final float localOffsetY;
    static final float LOCAL_OFFSET_Z = -0.51F;
    // Opt-in CI evidence from the ACTUAL client particle, never a mock render.
    // This is silent in normal gameplay and does not change particle behavior.
    private static final boolean SMOKE_PROBE = "1".equals(System.getenv("THAUMCRAFT_SMOKE"));
    private final int smokeFace;
    private final double smokeStartX;
    private final double smokeStartZ;
    private boolean smokeRendered;

    private ScanRuneParticle(ClientLevel level, double x, double y, double z,
                             double red, double durationUnits, double blue, SpriteSet sprites) {
        super(level, x, y, z, 0.0D, 0.0D, 0.0D);
        // Minecraft's velocity constructor randomizes motion EVEN for zero
        // arguments. Original FXBlockRunes explicitly resets motionX/Y/Z=0;
        // without this the runes drift away from the scanned block in-game.
        this.xd = this.yd = this.zd = 0.0D;
        // Y-speed is a duration transport slot, never the rune's green pigment.
        this.setColor((float) red, 0.0F, (float) blue);
        this.gravity = 0.03F;
        this.hasPhysics = false;
        this.lifetime = LegacyScanRuneDuration.lifetimeTicks(durationUnits);
        this.quadSize = 0.3F * (1.0F + (float) this.random.nextGaussian() * 0.1F);
        int yawQuarter = this.random.nextInt(4);
        this.smokeFace = yawQuarter;
        this.legacyCameraYaw = yawQuarter * Mth.HALF_PI;
        this.roll = this.oRoll = 0.0F;
        this.alpha = 0.0F;
        this.pickSprite(sprites);

        // FXBlockRunes translates (ofx, ofy, -0.51) AFTER the camera view
        // matrix has been applied. Keep that vector in camera-local space:
        // its world offset changes with the active view and is applied in
        // render(), not baked into the particle's world position at spawn.
        this.localOffsetX = this.random.nextFloat() * 0.2F;
        this.localOffsetY = -0.3F + this.random.nextFloat() * 0.6F;
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        this.smokeStartX = this.x;
        this.smokeStartZ = this.z;
        if (SMOKE_PROBE) {
            System.out.println("TC_SMOKE_SCAN_RUNE_CREATED=YES face=" + smokeFace
                    + " lifetime=" + lifetime + " x=" + x() + " y=" + y() + " z=" + z()
                    + " localX=" + localOffsetX + " localY=" + localOffsetY
                    + " localZ=" + LOCAL_OFFSET_Z);
        }
    }

    // Keep probe formatting independent from Minecraft API getters.
    private double x() { return this.x; }
    private double y() { return this.y; }
    private double z() { return this.z; }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTick) {
        // In BETA26, glTranslated(ofx, ofy, -0.51) executes after the camera
        // view transform. Convert the original local displacement through
        // the CURRENT camera orientation every frame. The previous port
        // applied only the particle's random quarter-turn in world axes at
        // construction, so changing sides left the glyphs on the wrong face.
        Vector3f offset = new Vector3f(localOffsetX, localOffsetY, LOCAL_OFFSET_Z)
                .rotateZ(Mth.HALF_PI)
                .rotateY(legacyCameraYaw);
        camera.rotation().transform(offset);

        double dx = offset.x;
        double dy = offset.y;
        double dz = offset.z;
        this.x += dx;
        this.y += dy;
        this.z += dz;
        this.xo += dx;
        this.yo += dy;
        this.zo += dz;
        try {
            if (SMOKE_PROBE && !smokeRendered && this.alpha >= 0.25F) {
                smokeRendered = true;
                Vector3f look = camera.getLookVector();
                float frontDot = -offset.dot(look);
                System.out.println("TC_SMOKE_SCAN_RUNE_RENDERED=YES face=" + smokeFace
                        + " alpha=" + this.alpha + " size=" + this.quadSize
                        + " cameraYaw=" + camera.getYRot()
                        + " lookX=" + look.x + " lookY=" + look.y + " lookZ=" + look.z
                        + " worldX=" + this.x + " worldY=" + this.y + " worldZ=" + this.z
                        + " frontDot=" + frontDot);
            }
            super.render(buffer, camera, partialTick);
        } finally {
            this.x -= dx;
            this.y -= dy;
            this.z -= dz;
            this.xo -= dx;
            this.yo -= dy;
            this.zo -= dz;
        }
    }

    @Override
    public SingleQuadParticle.FacingCameraMode getFacingCameraMode() {
        // FXBlockRunes GL rotations execute after the camera view transform.
        // Seed the quad with the active camera orientation, then apply the
        // same local Y quarter-turn and Z tip in that camera-relative space.
        return (rotation, camera, partialTick) -> rotation.set(camera.rotation())
                .rotateY(this.legacyCameraYaw)
                .rotateZ(Mth.HALF_PI);
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;

        float fadeIn = this.lifetime / 5.0F;
        float legacyAlpha = this.age <= fadeIn
                ? this.age / fadeIn
                : (this.lifetime - this.age) / (float) this.lifetime;
        // FXBlockRunes renders particleAlpha / 2.
        this.alpha = Math.max(0.0F, legacyAlpha * 0.5F);

        if (this.age++ >= this.lifetime) {
            if (SMOKE_PROBE) {
                System.out.println("TC_SMOKE_SCAN_RUNE_EXPIRED=YES face=" + smokeFace
                        + " age=" + (this.age - 1) + " lifetime=" + lifetime
                        + " drift=" + Math.hypot(this.x - smokeStartX, this.z - smokeStartZ));
            }
            this.remove();
            return;
        }

        this.yd -= 0.04D * this.gravity;
        this.move(this.xd, this.yd, this.zd);
    }

    @Override
    public int getLightColor(float partialTick) {
        return 0x00F000F0;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public @Nullable Particle createParticle(SimpleParticleType type, ClientLevel level,
                                                 double x, double y, double z,
                                                 double red, double durationUnits, double blue) {
            return new ScanRuneParticle(level, x, y, z, red, durationUnits, blue, sprites);
        }
    }
}
