package dev.ehtid.thaumcraft.client;

import dev.ehtid.thaumcraft.port.core.LegacyScanRuneDuration;
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

/**
 * BETA26 FXBlockRunes projection for Thaumometer scan feedback:
 * lifetime=3*m (m=15 for blocks, floor(entity height*15) for entities),
 * gravity=.03, fullbright, random one of sixteen original rune sprites,
 * quarter-turn rotation, 0.3 scale with gaussian variation and the exact fade curve.
 */
public final class ScanRuneParticle extends TextureSheetParticle {
    // BETA26 rotates the rune's XY quad around WORLD Y, then tips it 90°
    // around local Z. A vanilla billboard's roll rotates around the camera
    // instead, changing the rune's appearance as the player looks around.
    private final float legacyWorldYaw;
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
        this.legacyWorldYaw = yawQuarter * Mth.HALF_PI;
        this.roll = this.oRoll = 0.0F;
        this.alpha = 0.0F;
        this.pickSprite(sprites);

        // BETA26 FXBlockRunes.renderParticle translates (ofx, ofy, -0.51)
        // AFTER rotating the quad by world-Y (0/90/180/270) and local-Z 90.
        // Moving the offsets directly in world X/Y loses the -0.51 face
        // displacement and makes runes appear inside the scanned block.
        double ofx = this.random.nextFloat() * 0.2F;
        double ofy = -0.3F + this.random.nextFloat() * 0.6F;
        double dx, dz;
        switch (yawQuarter) {
            case 0 -> { dx = -ofy; dz = -0.51D; }
            case 1 -> { dx = -0.51D; dz = ofy; }
            case 2 -> { dx = ofy; dz = 0.51D; }
            default -> { dx = 0.51D; dz = -ofy; }
        }
        this.setPos(this.x + dx, this.y + ofx, this.z + dz);
        // Prevent first-frame interpolation from the unshifted spawn anchor.
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        this.smokeStartX = this.x;
        this.smokeStartZ = this.z;
        if (SMOKE_PROBE) {
            System.out.println("TC_SMOKE_SCAN_RUNE_CREATED=YES face=" + smokeFace
                    + " lifetime=" + lifetime + " x=" + x() + " y=" + y() + " z=" + z());
        }
    }

    // Keep probe formatting independent from Minecraft API getters.
    private double x() { return this.x; }
    private double y() { return this.y; }
    private double z() { return this.z; }

    @Override
    public SingleQuadParticle.FacingCameraMode getFacingCameraMode() {
        // FXBlockRunes.renderParticle: glRotatef(rotation, 0,1,0),
        // then glRotatef(90, 0,0,1). No camera-facing billboard.
        return (rotation, camera, partialTick) -> {
            rotation.rotationY(this.legacyWorldYaw).rotateZ(Mth.HALF_PI);
            if (SMOKE_PROBE && !smokeRendered) {
                smokeRendered = true;
                System.out.println("TC_SMOKE_SCAN_RUNE_RENDERED=YES face=" + smokeFace);
            }
        };
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
