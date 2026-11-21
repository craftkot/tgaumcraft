package dev.ehtid.thaumcraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.Tesselator;
import dev.ehtid.thaumcraft.port.core.visual.LegacyCrucibleBubbleRules;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.particles.SimpleParticleType;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * BETA26 FXDispatcher.crucibleBubble: particles.png cell 64, final cells 65/66,
 * aspect RGB, random 15..24 tick lifetime, 0.3..0.6 scale and gravity -0.001.
 */
public final class CrucibleBubbleParticle extends TextureSheetParticle {
    private static final int GRID_SIZE = 64;
    private static final boolean SMOKE_PROBE = "1".equals(System.getenv("THAUMCRAFT_SMOKE"));
    private static final AtomicInteger SMOKE_CREATED = new AtomicInteger();

    private static final ParticleRenderType BETA26_ADDITIVE = new ParticleRenderType() {
        @Override
        public BufferBuilder begin(Tesselator tesselator, TextureManager textureManager) {
            BufferBuilder builder = ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT.begin(
                    tesselator, textureManager);
            RenderSystem.blendFunc(770, 1);
            return builder;
        }

        @Override
        public boolean isTranslucent() {
            return true;
        }
    };

    private final TextureAtlasSprite atlas;
    private final double slowDown = 0.98D;
    private final int smokeId;
    private boolean smokeRendered;

    private CrucibleBubbleParticle(ClientLevel level, double x, double y, double z,
                                   double red, double green, double blue, SpriteSet sprites) {
        super(level, x, y, z, red, green, blue);
        this.xd = 0.0D;
        this.yd = 0.0D;
        this.zd = 0.0D;
        this.lifetime = LegacyCrucibleBubbleRules.lifetime(level.random.nextInt(10));
        this.quadSize = 0.1F * LegacyCrucibleBubbleRules.scale(level.random.nextFloat());
        this.gravity = -0.001F;
        this.setColor(channel(red), channel(green), channel(blue));
        this.alpha = 1.0F;
        this.atlas = sprites.get(0, 1);
        this.setSprite(this.atlas);
        this.hasPhysics = true;
        this.smokeId = SMOKE_PROBE ? SMOKE_CREATED.incrementAndGet() : -1;
        if (SMOKE_PROBE && smokeId <= 6) {
            System.out.println("TC_SMOKE_CRUCIBLE_BUBBLE_CREATED=YES id=" + smokeId
                    + " frame=" + currentFrame() + " lifetime=" + lifetime
                    + " scale=" + quadSize + " rgb=" + red + "," + green + "," + blue);
        }
    }

    private static float channel(double value) {
        return Math.max(0.0F, Math.min(1.0F, (float) value));
    }

    private int currentFrame() {
        return LegacyCrucibleBubbleRules.frame(this.age, this.lifetime);
    }

    private float u(int cell) {
        return atlas.getU0() + (atlas.getU1() - atlas.getU0()) * cell / GRID_SIZE;
    }

    private float v(int cell) {
        return atlas.getV0() + (atlas.getV1() - atlas.getV0()) * cell / GRID_SIZE;
    }

    @Override
    protected float getU0() {
        markRendered();
        return u(currentFrame() % GRID_SIZE);
    }

    @Override
    protected float getU1() {
        return u(currentFrame() % GRID_SIZE + 1);
    }

    @Override
    protected float getV0() {
        return v(currentFrame() / GRID_SIZE);
    }

    @Override
    protected float getV1() {
        return v(currentFrame() / GRID_SIZE + 1);
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;

        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }

        // FXGeneric.onUpdate: negative gravity, move, 0.98 slowdown,
        // then 0.002 Gaussian drift on all three axes.
        this.yd -= 0.04D * this.gravity;
        this.move(this.xd, this.yd, this.zd);
        this.xd *= this.slowDown;
        this.yd *= this.slowDown;
        this.zd *= this.slowDown;
        this.xd += this.level.random.nextGaussian() * 0.002D;
        this.yd += this.level.random.nextGaussian() * 0.002D;
        this.zd += this.level.random.nextGaussian() * 0.002D;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return BETA26_ADDITIVE;
    }

    private void markRendered() {
        if (SMOKE_PROBE && !smokeRendered && smokeId > 0) {
            smokeRendered = true;
            System.out.println("TC_SMOKE_CRUCIBLE_BUBBLE_RENDERED=YES id=" + smokeId
                    + " frame=" + currentFrame());
        }
    }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public @Nullable Particle createParticle(SimpleParticleType type, ClientLevel level,
                                                 double x, double y, double z,
                                                 double xSpeed, double ySpeed, double zSpeed) {
            return new CrucibleBubbleParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, sprites);
        }
    }
}
