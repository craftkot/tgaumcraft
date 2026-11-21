package dev.ehtid.thaumcraft.entity;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;

/**
 * BETA26 PotionFluxTaint: one point of magic damage every (40 >> amplifier)
 * ticks, with the original tainted-target healing and undead immunity rules.
 */
public final class FluxTaintMobEffect extends MobEffect {
    public static final int BETA26_COLOR = 6697847;

    public FluxTaintMobEffect() {
        super(MobEffectCategory.HARMFUL, BETA26_COLOR);
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity instanceof FluxTaintHealable) {
            entity.heal(1.0F);
        } else if (entity.getMobType() != MobType.UNDEAD) {
            // BETA26 DamageSourceThaumcraft.taint is magic and bypasses armor.
            entity.hurt(entity.level().damageSources().magic(), 1.0F);
        }
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        int interval = 40 >> amplifier;
        return interval > 0 ? duration % interval == 0 : true;
    }
}
