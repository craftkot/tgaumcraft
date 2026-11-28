package dev.ehtid.thaumcraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.Tesselator;
import dev.ehtid.thaumcraft.port.core.visual.LegacyCrucibleFrothRules;
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
 * BETA26 FXDispatcher.crucibleFroth/FrothDown: cells 64/73, final cells 65/66,
 * original colors, lifetime/scale, gravity, drift and separate blend layers.
 */
public final class CrucibleFrothParticle extends TextureSheetParticle {
    private static final int GRID_SIZE = 64;
    private static final boolean SMOKE_PROBE = "1".equals(System.getenv("THAUMCRAFT_SMOKE"));
    private static final AtomicInteger SURFACE_CREATED = new AtomicInteger();
    private static final AtomicInteger OVERFLOW_CREATED = new AtomicInteger();

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

    private final boolean overflow;
    private final TextureAtlasSprite atlas;
    private final double slowDown = LegacyCrucibleFrothRules.DEFAULT_SLOW_DOWN;
    private final int smokeId;
    private boolean smokeRendered;

    private CrucibleFrothParticle(ClientLevel level, double x, double y, double z,
                                  boolean overflow, SpriteSet sprites) {
        super(level, x, y, z, 0.0D, 0.0D, 0.0D);
        this.overflow = overflow;
        this.xd = 0.0D;
        this.yd = 0.0D;
        this.zd = 0.0D;
        this.lifetime = LegacyCrucibleFrothRules.lifetime(
                overflow, level.random.nextInt(overflow ? 12 : 3));
        this.quadSize = 0.1F * LegacyCrucibleFrothRules.scale(overflow, level.random.nextFloat());
        this.gravity = overflow
                ? LegacyCrucibleFrothRules.OVERFLOW_GRAVITY
                : LegacyCrucibleFrothRules.SURFACE_GRAVITY;
        this.setColor(
                overflow ? LegacyCrucibleFrothRules.OVERFLOW_RED : LegacyCrucibleFrothRules.SURFACE_RED,
                overflow ? LegacyCrucibleFrothRules.OVERFLOW_GREEN : LegacyCrucibleFrothRules.SURFACE_GREEN,
                overflow ? LegacyCrucibleFrothRules.OVERFLOW_BLUE : LegacyCrucibleFrothRules.SURFACE_BLUE);
        this.alpha = overflow
                ? LegacyCrucibleFrothRules.OVERFLOW_ALPHA
                : LegacyCrucibleFrothRules.SURFACE_ALPHA;
        this.atlas = sprites.get(0, 1);
        this.setSprite(this.atlas);
        this.hasPhysics = true;
        this.smokeId = SMOKE_PROBE
                ? (overflow ? OVERFLOW_CREATED : SURFACE_CREATED).incrementAndGet()
                : -1;
        if (SMOKE_PROBE && smokeId <= 6) {
            System.out.println((overflow
                    ? "TC_SMOKE_CRUCIBLE_FROTH_DOWN_CREATED=YES id="
                    : "TC_SMOKE_CRUCIBLE_FROTH_CREATED=YES id=") + smokeId
                    + " frame=" + currentFrame() + " lifetime=" + lifetime
                    + " scale=" + quadSize + " alpha=" + alpha);
        }
    }

    private int currentFrame() {
        return LegacyCrucibleFrothRules.frame(this.overflow, this.age, this.lifetime);
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

        // FXGeneric.onUpdate: positive gravity falls, then the 0.98 slowdown
        // and 0.001 Gaussian drift are applied on all three axes.
        this.yd -= 0.04D * this.gravity;
        this.move(this.xd, this.yd, this.zd);
        this.xd *= this.slowDown;
        this.yd *= this.slowDown;
        this.zd *= this.slowDown;
        this.xd += this.level.random.nextGaussian() * LegacyCrucibleFrothRules.RANDOM_MOVEMENT_SCALE;
        this.yd += this.level.random.nextGaussian() * LegacyCrucibleFrothRules.RANDOM_MOVEMENT_SCALE;
        this.zd += this.level.random.nextGaussian() * LegacyCrucibleFrothRules.RANDOM_MOVEMENT_SCALE;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return overflow ? ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT : BETA26_ADDITIVE;
    }

    private void markRendered() {
        if (SMOKE_PROBE && !smokeRendered && smokeId > 0) {
            smokeRendered = true;
            System.out.println((overflow
                    ? "TC_SMOKE_CRUCIBLE_FROTH_DOWN_RENDERED=YES id="
                    : "TC_SMOKE_CRUCIBLE_FROTH_RENDERED=YES id=") + smokeId
                    + " frame=" + currentFrame());
        }
    }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        private final boolean overflow;

        public Provider(SpriteSet sprites, boolean overflow) {
            this.sprites = sprites;
            this.overflow = overflow;
        }

        @Override
        public @Nullable Particle createParticle(SimpleParticleType type, ClientLevel level,
                                                 double x, double y, double z,
                                                 double xSpeed, double ySpeed, double zSpeed) {
            return new CrucibleFrothParticle(level, x, y, z, overflow, sprites);
        }
    }
}
