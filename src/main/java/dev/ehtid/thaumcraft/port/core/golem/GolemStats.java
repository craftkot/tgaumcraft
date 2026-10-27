package dev.ehtid.thaumcraft.port.core.golem;

/** Actual entity attributes and navigation multiplier are separate in BETA26. */
public record GolemStats(int maxHealth, int armor, double attackDamage, double movementSpeed,
                         float moveSpeedMultiplier, int carrySlots, int homeRadius,
                         double followRange, float stepHeight) {
    public static GolemStats derive(GolemDesign design) {
        var material = design.material();
        int health = 10 + material.healthMod();
        if (design.hasTrait(GolemTrait.FRAGILE)) health = (int) (health * 0.75D);
        health += design.rank();
        int armor = material.armor();
        if (design.hasTrait(GolemTrait.ARMORED)) armor = (int) Math.max(armor * 1.5D, armor + 1);
        if (design.hasTrait(GolemTrait.FRAGILE)) armor = (int) (armor * 0.75D);
        double damage = 0;
        if (design.hasTrait(GolemTrait.FIGHTER)) {
            damage = material.damage();
            if (design.hasTrait(GolemTrait.BRUTAL)) damage = Math.max(damage * 1.5D, damage + 1);
            damage += design.rank() * 0.25D;
        }
        float speed = 1F + design.rank() * 0.025F;
        if (design.hasTrait(GolemTrait.LIGHT)) speed += 0.2F;
        if (design.hasTrait(GolemTrait.HEAVY)) speed -= 0.175F;
        if (design.hasTrait(GolemTrait.FLYER)) speed -= 0.33F;
        if (design.hasTrait(GolemTrait.WHEELED)) speed += 0.25F;
        boolean scout = design.hasTrait(GolemTrait.SCOUT);
        return new GolemStats(health, armor, damage, 0.3D, speed,
            design.hasTrait(GolemTrait.HAULER) ? 2 : 1, scout ? 48 : 32,
            scout ? 56D : 40D, design.hasTrait(GolemTrait.WHEELED) ? 0.5F : 0.6F);
    }
}
