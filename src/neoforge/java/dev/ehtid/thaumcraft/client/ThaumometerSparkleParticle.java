package dev.ehtid.thaumcraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.Tesselator;
import dev.ehtid.thaumcraft.port.core.LegacyThaumometerSparkleRules;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** BETA26 FXDispatcher.scanHighlight -> drawSimpleSparkle projection. */
public final class ThaumometerSparkleParticle extends TextureSheetParticle {
    private static final int GRID_SIZE = 64;
    private static final int FRAME_COUNT = LegacyThaumometerSparkleRules.FRAME_COUNT;
    private static final boolean SMOKE_PROBE = "1".equals(System.getenv("THAUMCRAFT_SMOKE"));
    private static final int SMOKE_LOG_LIMIT = 12;
    private static final AtomicInteger SMOKE_CREATED = new AtomicInteger();
    private static final AtomicInteger SMOKE_RENDERED = new AtomicInteger();
    private static final AtomicInteger SMOKE_EXPIRED = new AtomicInteger();
    private static final AtomicBoolean SMOKE_BLEND_BEGIN = new AtomicBoolean();

    // BETA26 ParticleEngine layer 0 is SRC_ALPHA, ONE additive blending.
    private static final ParticleRenderType BETA26_ADDITIVE = new ParticleRenderType() {
        @Override
        public BufferBuilder begin(Tesselator tesselator, TextureManager textureManager) {
            BufferBuilder builder = ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT.begin(
                    tesselator, textureManager);
            RenderSystem.blendFunc(770, 1);
            if (SMOKE_PROBE && SMOKE_BLEND_BEGIN.compareAndSet(false, true)) {
                System.out.println("TC_SMOKE_THAUMOMETER_SPARKLE_ADDITIVE_BLEND_BEGIN=YES src=770 dst=1");
            }
            return builder;
        }

        @Override
        public boolean isTranslucent() {
            return true;
        }
    };

    private final TextureAtlasSprite atlas;
    private final int firstFrame;
    private final float[] alphaFrames;
    private final float[] scaleFrames;
    private final double windX;
    private final double windZ;
    private final long spawnGameTime;
    private final int smokeId;
    private int delayRemaining;
    private boolean smokeRendered;

    private ThaumometerSparkleParticle(ClientLevel level, double x, double y, double z,
                                       double encodedColorVariant, double encodedScale,
                                       double encodedDelay, SpriteSet sprites) {
        // BETA26 samples RGB/scale/delay/frame choice before FXGeneric's
        // Particle superclass. The finite payloads preserve that order.
        super(level, x, y, z, encodedColorVariant, encodedScale, encodedDelay);
        this.xd = this.yd = this.zd = 0.0D;

        LegacyThaumometerSparkleRules.ColorVariant color =
                LegacyThaumometerSparkleRules.decodeColorVariant(encodedColorVariant);
        float scaleStart = LegacyThaumometerSparkleRules.decodeScale(encodedScale);
        int delay = LegacyThaumometerSparkleRules.decodeDelay(encodedDelay);
        this.firstFrame = LegacyThaumometerSparkleRules.firstFrame(color.firstSequence());

        RandomSource random = level.random;
        this.lifetime = LegacyThaumometerSparkleRules.lifetime(random.nextInt(4));
        int alphaKeyCount = LegacyThaumometerSparkleRules.alphaKeyCount(
                this.lifetime, random.nextInt(this.lifetime / 3));
        float[] alphaKeys = new float[alphaKeyCount];
        for (int i = 1; i < alphaKeys.length - 1; i++) alphaKeys[i] = random.nextFloat();

        this.alphaFrames = buildAlphaFrames(alphaKeys, this.lifetime);
        this.scaleFrames = buildScaleFrames(scaleStart, this.lifetime);
        int windRoll = random.nextInt(10);
        LegacyThaumometerSparkleRules.Wind wind =
                LegacyThaumometerSparkleRules.wind(level.getMoonPhase(), windRoll);
        this.windX = wind.x();
        this.windZ = wind.z();
        this.delayRemaining = delay;
        this.spawnGameTime = level.getGameTime();

        this.gravity = 0.0F;
        this.slowDown = 1.0D;
        this.setSize(0.1F, 0.1F);
        this.setColor(color.red() / 255.0F, color.green() / 255.0F, color.blue() / 255.0F);
        this.quadSize = 0.1F * scaleStart;
        this.alpha = 0.0F;
        this.atlas = sprites.get(0, 1);
        this.setSprite(this.atlas);
        this.hasPhysics = true;
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;

        this.smokeId = SMOKE_PROBE ? SMOKE_CREATED.incrementAndGet() : -1;
        if (SMOKE_PROBE && smokeId <= SMOKE_LOG_LIMIT) {
            System.out.println("TC_SMOKE_THAUMOMETER_SPARKLE_CREATED=YES id=" + smokeId
                    + " firstFrame=" + firstFrame + " lifetime=" + lifetime
                    + " delay=" + delay + " alphaKeys=" + alphaKeyCount
                    + " red=" + color.red() + " green=" + color.green() + " blue=" + color.blue()
                    + " scale=" + scaleStart + " moonPhase=" + level.getMoonPhase()
                    + " windRoll=" + windRoll + " windX=" + windX + " windZ=" + windZ);
        }
    }

    private static float[] buildAlphaFrames(float[] keys, int lifetime) {
        // FXGeneric.calculateFrames uses repeated float addition.
        float[] frames = new float[lifetime + 1];
        float increment = (keys.length - 1) / (float) lifetime;
        float sample = 0.0F;
        for (int age = 0; age <= lifetime; age++) {
            frames[age] = LegacyThaumometerSparkleRules.alphaAt(keys, sample);
            sample += increment;
        }
        return frames;
    }

    private static float[] buildScaleFrames(float scaleStart, int lifetime) {
        float[] frames = new float[lifetime + 1];
        float increment = 1.0F / lifetime;
        float progress = 0.0F;
        for (int age = 0; age <= lifetime; age++) {
            frames[age] = LegacyThaumometerSparkleRules.scaleAt(scaleStart, progress);
            progress += increment;
        }
        return frames;
    }

    private int currentFrame() {
        return LegacyThaumometerSparkleRules.frame(firstFrame, this.age);
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

        // BETA26 addEffectWithDelay admits the already-created particle on
        // the next client tick, after decrementing its 0..9 tick delay.
        if (this.level.getGameTime() <= this.spawnGameTime) return;
        if (this.delayRemaining > 0) {
            this.delayRemaining--;
            if (this.delayRemaining > 0) return;
        }

        if (this.age++ >= this.lifetime) {
            if (SMOKE_PROBE && smokeId <= SMOKE_LOG_LIMIT) {
                int expired = SMOKE_EXPIRED.incrementAndGet();
                System.out.println("TC_SMOKE_THAUMOMETER_SPARKLE_EXPIRED=YES id=" + smokeId
                        + " age=" + (this.age - 1) + " lifetime=" + lifetime
                        + " count=" + expired);
            }
            this.remove();
            return;
        }

        int frameAge = Math.min(this.age, this.lifetime);
        this.alpha = this.alphaFrames[frameAge];
        this.quadSize = 0.1F * this.scaleFrames[frameAge];

        // FXGeneric.onUpdate: gravity, move, slowdown, Gaussian drift, wind.
        this.yd -= 0.04D * this.gravity;
        this.move(this.xd, this.yd, this.zd);
        this.xd *= this.slowDown;
        this.yd *= this.slowDown;
        this.zd *= this.slowDown;
        this.xd += this.level.random.nextGaussian() * 5.0E-4D;
        this.yd += this.level.random.nextGaussian() * 1.0E-3D;
        this.zd += this.level.random.nextGaussian() * 5.0E-4D;
        this.xd += this.windX;
        this.zd += this.windZ;
        if (this.onGround && this.slowDown != 1.0D) {
            this.xd *= 0.699999988079071D;
            this.zd *= 0.699999988079071D;
        }
    }

    private void markRendered() {
        if (SMOKE_PROBE && !smokeRendered && this.alpha >= 0.05F) {
            smokeRendered = true;
            int rendered = SMOKE_RENDERED.incrementAndGet();
            if (smokeId <= SMOKE_LOG_LIMIT) {
                System.out.println("TC_SMOKE_THAUMOMETER_SPARKLE_RENDERED=YES id=" + smokeId
                        + " age=" + age + " frame=" + currentFrame()
                        + " alpha=" + alpha + " scale=" + (quadSize / 0.1F)
                        + " count=" + rendered);
            }
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return BETA26_ADDITIVE;
    }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public @Nullable Particle createParticle(SimpleParticleType type, ClientLevel level,
                                                 double x, double y, double z,
                                                 double encodedColorVariant, double encodedScale,
                                                 double encodedDelay) {
            return new ThaumometerSparkleParticle(level, x, y, z,
                    encodedColorVariant, encodedScale, encodedDelay, sprites);
        }
    }
}
