package dev.ehtid.thaumcraft.entity;

import dev.ehtid.thaumcraft.port.core.LegacyAlumentumCrucibleRecipe;
import dev.ehtid.thaumcraft.registry.ThaumcraftEntities;
import dev.ehtid.thaumcraft.registry.ThaumcraftItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

/** Original EntityAlumentum: genuine ThrowableEntity physics, trail, explosion on impact. */
public final class AlumentumProjectile extends ThrowableItemProjectile {
    public AlumentumProjectile(EntityType<? extends AlumentumProjectile> type, Level level) {
        super(type, level);
    }

    public AlumentumProjectile(Level level, LivingEntity thrower) {
        super(ThaumcraftEntities.ALUMENTUM.get(), thrower, level);
    }

    @Override
    public void shoot(double x, double y, double z, float velocity, float inaccuracy) {
        // BETA26 EntityAlumentum.func_70186_c ignores velocity (the Item
        // passes 0.4F) and calls EntityThrowable with 0.75F instead.
        super.shoot(x, y, z, LegacyAlumentumCrucibleRecipe.ACTUAL_PROJECTILE_SPEED, inaccuracy);
    }

    @Override
    protected Item getDefaultItem() { return ThaumcraftItems.ALUMENTUM.get(); }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) return;
        // Three interpolated flight FX per client tick in original EntityAlumentum.
        // Particle appearance is provisional until FXDispatcher drawAlumentum is restored.
        for (int i = 0; i < 3; i++) {
            double t = i / 3.0;
            double x = xo + (getX() - xo) * t;
            double y = yo + (getY() - yo) * t + getBbHeight() / 2.0;
            double z = zo + (getZ() - zo) * t;
            level().addParticle(ParticleTypes.FLAME, x, y, z,
                    (random.nextFloat() - 0.5) * 0.0125,
                    (random.nextFloat() - 0.5) * 0.0125,
                    (random.nextFloat() - 0.5) * 0.0125);
            level().addParticle(ParticleTypes.SMOKE, x, y, z, 0, 0, 0);
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!level().isClientSide) {
            // Original World.createExplosion(Entity, x,y,z,1.1F,true): break blocks.
            level().explode(this, getX(), getY(), getZ(),
                    LegacyAlumentumCrucibleRecipe.EXPLOSION_POWER,
                    Level.ExplosionInteraction.BLOCK);
            discard();
        }
    }
}
