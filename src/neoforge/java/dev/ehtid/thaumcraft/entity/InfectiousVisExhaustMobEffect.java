package dev.ehtid.thaumcraft.entity;

import dev.ehtid.thaumcraft.registry.FluxInfectionEffectRegistration;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

/**
 * BETA26 PotionInfectiousVisExhaust (Flux Phage): every 40 ticks, spread
 * to living entities in the carrier's AABB grown by four blocks. Each hop
 * loses one amplifier; amplifier zero converts the infection to Flux Flu.
 */
public final class InfectiousVisExhaustMobEffect extends MobEffect {
    public static final int BETA26_COLOR = 6706551;

    public InfectiousVisExhaustMobEffect() {
        super(MobEffectCategory.HARMFUL, BETA26_COLOR);
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (!(entity.level() instanceof ServerLevel serverLevel)) {
            return true;
        }

        AABB spreadBounds = entity.getBoundingBox().inflate(4.0D);
        var infectious = FluxInfectionEffectRegistration.infectious();
        for (LivingEntity target : serverLevel.getEntitiesOfClass(LivingEntity.class, spreadBounds)) {
            // BETA26 skips every entity already carrying this effect; it does not
            // refresh duration or upgrade/downgrade an existing infection.
            if (target.hasEffect(infectious)) {
                continue;
            }

            if (amplifier > 0) {
                target.addEffect(new MobEffectInstance(
                        infectious, 6000, amplifier - 1));
            } else {
                target.addEffect(new MobEffectInstance(
                        FluxInfectionEffectRegistration.visExhaust(), 6000, 0));
            }
        }
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % 40 == 0;
    }
}
