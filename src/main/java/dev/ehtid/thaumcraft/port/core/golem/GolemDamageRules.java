package dev.ehtid.thaumcraft.port.core.golem;

/** EntityThaumcraftGolem.damageEntity: fire immunity, cactus immunity, capped explosion reduction. */
public final class GolemDamageRules {
    private GolemDamageRules() {}
    public static float amount(GolemDesign design,float damage,float maxHealth,boolean fire,boolean explosion,boolean cactus) {
        if(cactus || fire && design.hasTrait(GolemTrait.FIREPROOF)) return 0;
        return explosion && design.hasTrait(GolemTrait.BLASTPROOF)?Math.min(maxHealth/2,damage*.3F):damage;
    }
}
