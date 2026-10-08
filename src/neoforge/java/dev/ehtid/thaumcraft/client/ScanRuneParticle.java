package dev.ehtid.thaumcraft.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

/**
 * BETA26 FXBlockRunes projection for Thaumometer scan feedback:
 * lifetime=3*15, gravity=.03, fullbright, random one of sixteen original rune sprites,
 * quarter-turn rotation, 0.3 scale with gaussian variation and the exact fade curve.
 */
public final class ScanRuneParticle extends TextureSheetParticle {
    private ScanRuneParticle(ClientLevel level, double x, double y, double z,
                             double red, double green, double blue, SpriteSet sprites) {
        super(level, x, y, z, 0.0D, 0.0D, 0.0D);
        this.setColor((float) red, (float) green, (float) blue);
        this.gravity = 0.03F;
        this.hasPhysics = false;
        this.lifetime = 45;
        this.quadSize = 0.3F * (1.0F + (float) this.random.nextGaussian() * 0.1F);
        this.roll = this.oRoll = this.random.nextInt(4) * Mth.HALF_PI;
        this.alpha = 0.0F;
        this.pickSprite(sprites);

        // Original FXBlockRunes local offsets: ofx=[0,.2), ofy=[-.3,.3).
        this.x += this.random.nextFloat() * 0.2F;
        this.y += -0.3F + this.random.nextFloat() * 0.6F;
        this.setPos(this.x, this.y, this.z);
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
                                                 double red, double green, double blue,
                                                 RandomSource random) {
            return new ScanRuneParticle(level, x, y, z, red, green, blue, sprites);
        }
    }
}
