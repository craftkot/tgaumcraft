package dev.ehtid.thaumcraft.entity;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * BETA26 PotionVisExhaust (Flux Flu) is a passive marker. Its Vis-cost
 * multiplier belongs to the later wand/focus economy integration (M09).
 */
public final class VisExhaustMobEffect extends MobEffect {
    public static final int BETA26_COLOR = 6702199;

    public VisExhaustMobEffect() {
        super(MobEffectCategory.HARMFUL, BETA26_COLOR);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return false;
    }
}
