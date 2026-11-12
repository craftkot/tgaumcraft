package dev.ehtid.thaumcraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.block.NitorBlock;
import dev.ehtid.thaumcraft.registry.NitorParticleRegistration;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Client providers for the original BETA26 Nitor FXGeneric atlas frames. */
@EventBusSubscriber(modid = ThaumcraftPort.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class NitorParticleClientEvents {
    private NitorParticleClientEvents() {}

    @SubscribeEvent
    public static void registerParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(NitorParticleRegistration.FLAME_TYPE,
                sprites -> new NitorParticle.Provider(sprites, true));
        event.registerSpriteSet(NitorParticleRegistration.CORE_TYPE,
                sprites -> new NitorParticle.Provider(sprites, false));
    }
}

/**
 * BETA26 FXDispatcher.drawNitorFlames/drawNitorCore projected onto the existing
 * byte-identical 64x64 particles.png atlas.
 *
 * Flames use layer 0 (SRC_ALPHA, ONE) and frames 264..271. The central glow
 * uses layer 1 (SRC_ALPHA, ONE_MINUS_SRC_ALPHA) and frame 457.
 */
final class NitorParticle extends TextureSheetParticle {
    private static final int GRID_SIZE = 64;
    private static final int FLAME_FIRST_FRAME = 264;
    private static final int FLAME_FRAME_COUNT = 8;
    private static final int CORE_FRAME = 457;
    private static final double SLOW_DOWN = 0.9800000190734863D;
    private static final boolean SMOKE_PROBE =
            Boolean.getBoolean("thaumcraft.nitorSmoke");

    private static final AtomicInteger FLAME_CREATED = new AtomicInteger();
    private static final AtomicInteger CORE_CREATED = new AtomicInteger();
    private static final AtomicInteger FLAME_FRAME_MASK = new AtomicInteger();
    private static final AtomicBoolean BLEND_REPORTED = new AtomicBoolean();

    private static final ParticleRenderType BETA26_ADDITIVE = new ParticleRenderType() {
        @Override
        public BufferBuilder begin(Tesselator tesselator, TextureManager textureManager) {
            BufferBuilder builder = ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT.begin(
                    tesselator, textureManager);
            RenderSystem.blendFunc(770, 1);
            if (SMOKE_PROBE && BLEND_REPORTED.compareAndSet(false, true)) {
                System.out.println("TC_SMOKE_NITOR_ADDITIVE_BLEND_BEGIN=YES src=770 dst=1");
            }
            return builder;
        }

        @Override
        public boolean isTranslucent() {
            return true;
        }
    };

    private final boolean flame;
    private final TextureAtlasSprite atlas;
    private final int smokeId;
    private final float startScale;
    private final float middleScale;
    private final double randomX;
    private final double randomY;
    private final double randomZ;
    private boolean firstVisibleRenderDone;
    private double firstVisibleXo;
    private double firstVisibleYo;
    private double firstVisibleZo;
    private boolean smokeRendered;
    private boolean smokeExpired;

    private NitorParticle(ClientLevel level, double x, double y, double z,
                          double xSpeed, double ySpeed, double zSpeed,
                          SpriteSet sprites, boolean flame, int particleRgb) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed);
        this.flame = flame;
        this.xd = xSpeed;
        this.yd = ySpeed;
        this.zd = zSpeed;

        // BETA26 drawNitorFlames: age 10..14, alpha .66, scale 3..4 -> .05.
        // BETA26 drawNitorCore: age 10, white, scale 1 -> randomized middle -> 1.
        this.lifetime = flame ? 10 + level.random.nextInt(5) : 10;
        // BETA26 FXGeneric starts at particleAge=0 and increments before its first
        // update/render. Start at 0 so the first modern tick is legacy age 1.
        // ParticleEngine may tick a queued particle again before its first render;
        // tick() holds the age-one state and its interpolation origin until rendered.
        this.age = 0;
        this.startScale = flame ? 3.0F + level.random.nextFloat() : 1.0F;
        this.middleScale = flame ? 1.0F
                : 1.0F + (float) level.random.nextGaussian() * 0.1F;
        this.alpha = flame ? 0.66F : 1.0F;
        this.gravity = 0.0F;
        this.hasPhysics = true;
        this.quadSize = 0.1F * scaleAt(this.age);

        int rgb = flame ? particleRgb : 0xFFFFFF;
        this.setColor(((rgb >> 16) & 0xFF) / 255.0F,
                ((rgb >> 8) & 0xFF) / 255.0F,
                (rgb & 0xFF) / 255.0F);

        // FXGeneric defaults slowDown=.980000019; only random movement differs.
        this.randomX = flame ? 0.0025D : 0.0002D;
        this.randomY = flame ? 0.0D : 0.0002D;
        this.randomZ = flame ? 0.0025D : 0.0002D;

        this.atlas = sprites.get(0, 1);
        this.setSprite(this.atlas);

        this.smokeId = SMOKE_PROBE
                ? (flame ? FLAME_CREATED.incrementAndGet() : CORE_CREATED.incrementAndGet())
                : -1;
        if (SMOKE_PROBE && this.smokeId == 1 && flame) {
            System.out.println("TC_SMOKE_NITOR_FLAME_CREATED=YES id=1 frameStart="
                    + FLAME_FIRST_FRAME + " frameCount=" + FLAME_FRAME_COUNT
                    + " lifetime=" + this.lifetime + " startScale=" + this.startScale
                    + " rgb=" + rgb);
        } else if (SMOKE_PROBE && this.smokeId == 1) {
            System.out.println("TC_SMOKE_NITOR_CORE_CREATED=YES id=1 frame="
                    + CORE_FRAME + " lifetime=" + this.lifetime
                    + " middleScale=" + this.middleScale);
        }
    }

    private int currentFrame() {
        return flame ? FLAME_FIRST_FRAME + this.age % FLAME_FRAME_COUNT : CORE_FRAME;
    }

    private float scaleAt(int frameAge) {
        int boundedAge = Math.min(Math.max(frameAge, 0), this.lifetime);
        if (flame) {
            return startScale + (0.05F - startScale) * boundedAge / (float) lifetime;
        }
        float progress = boundedAge / (float) lifetime;
        return progress <= 0.5F
                ? 1.0F + (middleScale - 1.0F) * (progress * 2.0F)
                : middleScale + (1.0F - middleScale) * ((progress - 0.5F) * 2.0F);
    }

    private float u(int cell) {
        return atlas.getU0() + (atlas.getU1() - atlas.getU0()) * cell / GRID_SIZE;
    }

    private float v(int cell) {
        return atlas.getV0() + (atlas.getV1() - atlas.getV0()) * cell / GRID_SIZE;
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTick) {
        // BETA26 FXGeneric first renders after its first onUpdate at age 1.
        // Do not draw the constructor state; tick() freezes the first age-one
        // update if the modern queue advances it again before this first render.
        if (this.age < 1) return;
        this.firstVisibleRenderDone = true;
        super.render(buffer, camera, partialTick);
    }

    @Override
    protected float getU0() {
        markRendered();
        int frame = currentFrame();
        if (SMOKE_PROBE && flame) {
            int bit = 1 << (frame - FLAME_FIRST_FRAME);
            int seen = FLAME_FRAME_MASK.getAndUpdate(mask -> mask | bit);
            if ((seen & bit) == 0) {
                System.out.println("TC_SMOKE_NITOR_FLAME_FRAME=YES frame=" + frame);
            }
            if (smokeId == 1) {
                System.out.println("TC_SMOKE_NITOR_FLAME_FRAME_DETAIL=YES id=1 age="
                        + age + " frame=" + frame);
            }
        }
        return u(frame % GRID_SIZE);
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
            if (SMOKE_PROBE && smokeId == 1 && !smokeExpired) {
                smokeExpired = true;
                String kind = flame ? "FLAME" : "CORE";
                System.out.println("TC_SMOKE_NITOR_" + kind + "_EXPIRED=YES id=1 age="
                        + (age - 1) + " lifetime=" + lifetime);
            }
            this.remove();
            return;
        }

        // A queued NeoForge particle can be ticked again before its first render.
        // FXGeneric has already reached its first visible update at age 1; preserve
        // that exact state instead of consuming extra animation/motion/RNG updates.
        if (!this.firstVisibleRenderDone && this.age > 1) {
            this.age = 1;
            this.xo = this.firstVisibleXo;
            this.yo = this.firstVisibleYo;
            this.zo = this.firstVisibleZo;
            return;
        }

        int frameAge = Math.min(this.age, this.lifetime);
        float scale = scaleAt(frameAge);
        this.quadSize = 0.1F * scale;

        if (SMOKE_PROBE && smokeId == 1 && frameAge == lifetime) {
            String kind = flame ? "FLAME" : "CORE";
            System.out.println("TC_SMOKE_NITOR_" + kind + "_SCALE=YES id=1 age="
                    + frameAge + " scale=" + scale);
        }

        // FXGeneric.onUpdate: gravity, move, slowdown, world-RNG drift.
        this.yd -= 0.04D * this.gravity;
        this.move(this.xd, this.yd, this.zd);
        this.xd *= SLOW_DOWN;
        this.yd *= SLOW_DOWN;
        this.zd *= SLOW_DOWN;
        this.xd += this.level.random.nextGaussian() * randomX;
        this.yd += this.level.random.nextGaussian() * randomY;
        this.zd += this.level.random.nextGaussian() * randomZ;
        if (this.onGround && SLOW_DOWN != 1.0D) {
            this.xd *= 0.699999988079071D;
            this.zd *= 0.699999988079071D;
        }

        // Preserve the previous-position side of FXGeneric's first rendered
        // interpolation. If another queue tick arrives before render(), tick()
        // restores these coordinates while holding age 1.
        if (!this.firstVisibleRenderDone && this.age == 1) {
            this.firstVisibleXo = this.xo;
            this.firstVisibleYo = this.yo;
            this.firstVisibleZo = this.zo;
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return flame ? BETA26_ADDITIVE : ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    private void markRendered() {
        if (!SMOKE_PROBE || smokeId != 1 || smokeRendered) return;
        smokeRendered = true;
        if (flame) {
            System.out.println("TC_SMOKE_NITOR_FLAME_RENDERED=YES id=1 frame="
                    + currentFrame() + " age=" + age);
        } else {
            System.out.println("TC_SMOKE_NITOR_CORE_RENDERED=YES id=1 frame="
                    + currentFrame() + " age=" + age);
        }
        String kind = flame ? "FLAME" : "CORE";
        System.out.println("TC_SMOKE_NITOR_" + kind + "_SCALE=YES id=1 age="
                + age + " scale=" + scaleAt(age));
    }

    static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        private final boolean flame;

        Provider(SpriteSet sprites, boolean flame) {
            this.sprites = sprites;
            this.flame = flame;
        }

        @Override
        public @Nullable Particle createParticle(SimpleParticleType type, ClientLevel level,
                                                 double x, double y, double z,
                                                 double xSpeed, double ySpeed, double zSpeed) {
            int rgb = 0xFFFFFF;
            if (flame) {
                BlockState state = level.getBlockState(BlockPos.containing(x, y, z));
                if (state.getBlock() instanceof NitorBlock nitor) {
                    rgb = nitor.particleRgb();
                    nitor.reportParticleColorIfNeeded();
                } else if (SMOKE_PROBE) {
                    System.out.println("TC_SMOKE_NITOR_PARTICLE_COLOR_SOURCE=FAIL");
                }
            }
            return new NitorParticle(level, x, y, z, xSpeed, ySpeed, zSpeed,
                    sprites, flame, rgb);
        }
    }
}
