package dev.ehtid.thaumcraft.port.core.golem;

import java.util.Set;

/** BETA26 SealGuard/SealGuardAdvanced/SealButcher. Classification is supplied by the platform adapter. */
public final class GolemCombatRules {
    public static final int GUARD_INTERVAL=20, BUTCHER_INTERVAL=200;
    public static final short TASK_LIFESPAN=10;
    public record Settings(boolean mobs,boolean animals,boolean players) {
        public static Settings defaults() { return new Settings(true,false,false); }
        public Settings withToggle(int index,boolean value) {
            return switch(index) {
                case 0->new Settings(value,animals,players);
                case 1->new Settings(mobs,value,players);
                case 2->new Settings(mobs,animals,value);
                default->throw new IllegalArgumentException("Unknown source guard toggle");
            };
        }
    }
    /** Legacy IAnimals also includes monsters, villagers, bats/water mobs and golems. It is not EntityAnimal. */
    public record Target(boolean monster,boolean legacyAnimal,boolean player,boolean animalChild,
                         boolean tameableTamed,boolean golem) {}
    private GolemCombatRules() {}
    public static boolean eligible(Set<GolemTrait> traits,boolean smartRequired) {
        return traits.contains(GolemTrait.FIGHTER)&&(!smartRequired||traits.contains(GolemTrait.SMART));
    }
    public static boolean guard(Settings settings,Target target,boolean pvp) {
        return settings.mobs&&target.monster || settings.animals&&target.legacyAnimal
                || settings.players&&pvp&&target.player;
    }
    public static boolean butcher(Target target) {
        return target.legacyAnimal&&!target.monster&&!target.tameableTamed&&!target.golem&&!target.animalChild;
    }
    /** Source counts assignable classes, not registry type IDs. Adapter performs that asymmetric class query. */
    public static boolean excess(int eligibleClassPopulation) { return eligibleClassPopulation>2; }
}
