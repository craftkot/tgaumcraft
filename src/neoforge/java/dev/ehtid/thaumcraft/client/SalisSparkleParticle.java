package dev.ehtid.thaumcraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.Tesselator;
import dev.ehtid.thaumcraft.port.core.visual.SalisSparkleScaleRules;
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

import java.util.concurrent.atomic.AtomicInteger;

/**
 * BETA26 FXGeneric.drawSimpleSparkle projection for the Salis hand trail and exposed-face burst.
 *
 * The original particles.png is a 64x64 grid of 16px sprites. BETA26 selects
 * the 16-frame sequences beginning at indices 320 (20%) or 512 (80%).
 * The complete original atlas is stitched as one particle sprite; these UV
 * accessors select the exact legacy cell without copying/repainting the art.
 */
public final class SalisSparkleParticle extends TextureSheetParticle {
    // BETA26 FXGeneric uses ParticleEngine layer 0, whose blend function is
    // SRC_ALPHA, ONE (additive). Reuse the vanilla particle-sheet setup/atlas,
    // then replace only its standard alpha blend state with the source blend.
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
    private static final int GRID_SIZE = 64;
    private static final int FRAME_COUNT = 16;
    private static final double FLOATY_SENTINEL = 1000.0D;
    private static final double FACE_SENTINEL = 2000.0D;
    private static final boolean SMOKE_PROBE = "1".equals(System.getenv("THAUMCRAFT_SMOKE"));
    private static final AtomicInteger SMOKE_CREATED = new AtomicInteger();
    private static final AtomicInteger SMOKE_RENDERED = new AtomicInteger();
    private static final AtomicInteger SMOKE_EXPIRED = new AtomicInteger();
    private static final AtomicInteger SMOKE_FACE_CREATED = new AtomicInteger();
    private static final AtomicInteger SMOKE_FACE_RENDERED = new AtomicInteger();
    private static final AtomicInteger SMOKE_FACE_EXPIRED = new AtomicInteger();

    private final TextureAtlasSprite atlas;
    private final int firstFrame;
    private final float[] alphaFrames;
    private final float[] scaleFrames;
    private final double slowDown;
    private final double windX;
    private final double windZ;
    private final int smokeId;
    private final int faceSmokeId;
    private final boolean face;
    private boolean smokeRendered;

    private SalisSparkleParticle(ClientLevel level, double x, double y, double z,
                                 double xSpeed, double ySpeed, double encodedZSpeed,
                                 SpriteSet sprites) {
        super(level, x, y, z, xSpeed, ySpeed, encodedZSpeed);

        // The server queue carries the hand-trail floaty flag in Z and face mode in X.
        // Both sentinels are removed before the particle receives any physical motion.
        this.face = xSpeed >= FACE_SENTINEL * 0.5D;
        boolean floaty = !this.face && encodedZSpeed >= FLOATY_SENTINEL * 0.5D;
        this.xd = this.face ? 0.0D : xSpeed;
        this.yd = ySpeed;
        this.zd = this.face ? 0.0D
                : encodedZSpeed - (floaty ? FLOATY_SENTINEL : 0.0D);

        // BETA26 ItemMagicDust/FXDispatcher sample warm RGB before drawSimpleSparkle.
        this.setColor(1.0F,
                (189 + level.random.nextInt(67)) / 255.0F,
                (64 + level.random.nextInt(192)) / 255.0F);
        float scaleStart = this.face
                ? SalisSparkleScaleRules.blockFace(level.random.nextGaussian()) : 0.5F;

        // drawBlockSparkles uses gravity .01, no slowdown and scale -> 2x;
        // the hand trail retains its 16/34 floaty/non-floaty source split.
        this.gravity = this.face ? 0.01F : (floaty ? 0.2F : 0.5F);
        this.slowDown = this.face ? 1.0D
                : (floaty ? 0.3D + level.random.nextFloat() * 0.5D : 0.85D);
        this.firstFrame = level.random.nextFloat() < 0.2F ? 320 : 512;
        this.lifetime = 64 + level.random.nextInt(16);

        this.alphaFrames = buildAlphaFrames(this.lifetime, level.random);
        this.scaleFrames = buildScaleFrames(this.lifetime, scaleStart,
                this.face ? scaleStart * 2.0F : 1.0F);
        this.quadSize = 0.1F * scaleStart;
        this.alpha = 0.0F;

        this.atlas = sprites.get(0, 1);
        this.setSprite(this.atlas);
        this.hasPhysics = true;

        // FXGeneric#setWind(0.0005): rotate (0.1, 0, 0) by moon phase.
        double angle = level.getMoonPhase() * (40 + level.random.nextInt(10))
                / 180.0D * Math.PI;
        this.windX = Math.cos(angle) * 0.00005D;
        this.windZ = -Math.sin(angle) * 0.00005D;

        this.smokeId = SMOKE_PROBE && !this.face ? SMOKE_CREATED.incrementAndGet() : -1;
        this.faceSmokeId = SMOKE_PROBE && this.face ? SMOKE_FACE_CREATED.incrementAndGet() : -1;
        if (SMOKE_PROBE && this.face) {
            System.out.println("TC_SMOKE_SALIS_FACE_SPARKLE_CREATED=YES id=" + faceSmokeId
                    + " frameStart=" + firstFrame + " lifetime=" + lifetime
                    + " scaleStart=" + scaleStart);
        } else if (SMOKE_PROBE) {
            System.out.println("TC_SMOKE_SALIS_SPARKLE_CREATED=YES id=" + smokeId
                    + " frameStart=" + firstFrame + " lifetime=" + lifetime
                    + " floaty=" + floaty);
        }
    }

    private static float[] buildAlphaFrames(int lifetime, RandomSource random) {
        // FXGeneric.drawSimpleSparkle: 6 + rand.nextInt(age / 3) alpha keys,
        // with implicit zero endpoints and independently randomized interior.
        int keyCount = 6 + random.nextInt(lifetime / 3);
        float[] keys = new float[keyCount];
        for (int i = 1; i < keyCount - 1; i++) keys[i] = random.nextFloat();

        float[] frames = new float[lifetime + 1];
        float increment = (keyCount - 1) / (float) lifetime;
        for (int age = 0; age <= lifetime; age++) {
            float sample = age * increment;
            int index = Math.min((int) sample, keyCount - 1);
            float fraction = sample - index;
            float delta = index < keyCount - 1 ? keys[index + 1] - keys[index] : 0.0F;
            frames[age] = keys[index] + delta * fraction;
        }
        return frames;
    }

    private static float[] buildScaleFrames(int lifetime, float start, float end) {
        // FXGeneric.setScale(start, end), linearly interpolated over age.
        float[] frames = new float[lifetime + 1];
        for (int age = 0; age <= lifetime; age++) {
            frames[age] = start + (end - start) * age / lifetime;
        }
        return frames;
    }

    private int currentFrame() {
        return firstFrame + this.age % FRAME_COUNT;
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
            if (SMOKE_PROBE && this.face) {
                int expired = SMOKE_FACE_EXPIRED.incrementAndGet();
                System.out.println("TC_SMOKE_SALIS_FACE_SPARKLE_EXPIRED=YES id=" + faceSmokeId
                        + " age=" + (this.age - 1) + " lifetime=" + lifetime
                        + " count=" + expired);
            } else if (SMOKE_PROBE) {
                int expired = SMOKE_EXPIRED.incrementAndGet();
                System.out.println("TC_SMOKE_SALIS_SPARKLE_EXPIRED=YES id=" + smokeId
                        + " age=" + (this.age - 1) + " lifetime=" + lifetime
                        + " count=" + expired);
            }
            this.remove();
            return;
        }

        int frameAge = Math.min(this.age, this.lifetime);
        this.alpha = this.alphaFrames[frameAge];
        this.quadSize = 0.1F * this.scaleFrames[frameAge];

        if (SMOKE_PROBE && !this.face && smokeId == 1
                && (this.age == 1 || this.age == 16 || this.age == 17
                    || this.age == 31 || this.age == 32)) {
            System.out.println("TC_SMOKE_SALIS_SPARKLE_FRAME=YES id=1 age=" + this.age
                    + " frame=" + currentFrame());
        }
        if (SMOKE_PROBE && this.face && faceSmokeId == 1
                && (this.age == 1 || this.age == 16 || this.age == 17
                    || this.age == 31 || this.age == 32)) {
            System.out.println("TC_SMOKE_SALIS_FACE_SPARKLE_FRAME=YES id=1 age=" + this.age
                    + " frame=" + currentFrame());
        }

        // FXGeneric.onUpdate: gravity, move, slowdown, Gaussian drift, wind,
        // then the original on-ground horizontal damping.
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

    @Override
    public ParticleRenderType getRenderType() {
        return BETA26_ADDITIVE;
    }

    private void markRendered() {
        if (SMOKE_PROBE && !smokeRendered) {
            smokeRendered = true;
            if (this.face) {
                int rendered = SMOKE_FACE_RENDERED.incrementAndGet();
                System.out.println("TC_SMOKE_SALIS_FACE_SPARKLE_RENDERED=YES id=" + faceSmokeId
                        + " frame=" + currentFrame() + " count=" + rendered);
            } else {
                int rendered = SMOKE_RENDERED.incrementAndGet();
                System.out.println("TC_SMOKE_SALIS_SPARKLE_RENDERED=YES id=" + smokeId
                        + " frame=" + currentFrame() + " count=" + rendered);
            }
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
            return new SalisSparkleParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, sprites);
        }
    }
}
