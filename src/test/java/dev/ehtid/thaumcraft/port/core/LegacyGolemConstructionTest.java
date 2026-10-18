package dev.ehtid.thaumcraft.port.core;

import dev.ehtid.thaumcraft.port.core.golem.*;
import java.util.Set;
import static dev.ehtid.thaumcraft.port.core.golem.GolemTrait.*;

/** Standalone golden values recovered from BETA26 entity/properties bytecode. */
public final class LegacyGolemConstructionTest {
    private static int assertions;
    private static void same(Object expected, Object actual) {
        assertions++;
        if (!java.util.Objects.equals(expected, actual))
            throw new AssertionError("Expected " + expected + ", got " + actual);
    }
    private static void near(double expected, double actual) {
        assertions++;
        if (Math.abs(expected - actual) > 0.000001D)
            throw new AssertionError("Expected " + expected + ", got " + actual);
    }
    private static void invalid(Runnable action) {
        assertions++;
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Invalid design was accepted");
    }
    public static void main(String[] args) {
        same(6, GolemMaterial.values().length);
        same(5, GolemCatalog.HEADS.size());
        same(5, GolemCatalog.ARMS.size());
        same(4, GolemCatalog.LEGS.size());
        same(4, GolemCatalog.ADDONS.size());
        var wood = GolemDesign.defaultWood();
        var components = GolemAssembly.components(wood);
        same(java.util.List.of(
                new GolemAssembly.Component("thaumcraft:plank_greatwood", 0, 3),
                new GolemAssembly.Component("thaumcraft:mechanism_simple", 0, 2),
                new GolemAssembly.Component("thaumcraft:mind", 0, 1)), components);
        same(components, GolemAssembly.components(wood.withRank(10)));
        assertions++;
        try {
            GolemAssembly.components(GolemDesign.fromIds(1, 0, 0, 0, 0, 0));
            throw new AssertionError("Unported costs must not be silently guessed");
        } catch (UnsupportedOperationException expected) { /* explicit implementation gap */ }
        same(0L, wood.toLong());
        same(Set.of(LIGHT), wood.traits());
        same(16, wood.stats().maxHealth());
        same(2, wood.stats().armor());
        near(0D, wood.stats().attackDamage());
        near(.3D, wood.stats().movementSpeed());
        near(1.2D, wood.stats().moveSpeedMultiplier());
        same(1, wood.stats().carrySlots());
        same(32, wood.stats().homeRadius());
        near(40D, wood.stats().followRange());
        near(.6D, wood.stats().stepHeight());
        int[] health = {16, 30, 20, 26, 34, 30};
        int[] armor = {2, 8, 4, 6, 10, 6};
        for (int material = 0; material < 6; material++) {
            var basic = GolemDesign.fromIds(material, 0, 0, 0, 0, 0);
            same(health[material], basic.stats().maxHealth());
            same(armor[material], basic.stats().armor());
        }
        var fragileWood = GolemDesign.fromIds(0, 1, 1, 0, 0, 3);
        same(Set.of(LIGHT, SMART, DEFT, FRAGILE), fragileWood.traits());
        same(15, fragileWood.stats().maxHealth()); // floor(16*.75)+3
        same(1, fragileWood.stats().armor());
        near(1.275D, fragileWood.stats().moveSpeedMultiplier());
        var cancelled = GolemDesign.fromIds(0, 1, 1, 0, 1, 0);
        same(Set.of(SMART, DEFT), cancelled.traits()); // opposite traits remove both
        same(16, cancelled.stats().maxHealth());
        same(2, cancelled.stats().armor());
        near(1D, cancelled.stats().moveSpeedMultiplier());
        var heavyArmor = GolemDesign.fromIds(1, 0, 0, 0, 1, 0);
        same(12, heavyArmor.stats().armor());
        near(.825D, heavyArmor.stats().moveSpeedMultiplier());
        var claws = GolemDesign.fromIds(1, 2, 2, 0, 0, 4);
        same(Set.of(HEAVY, FIREPROOF, BLASTPROOF, SMART, FIGHTER, CLUMSY, BRUTAL), claws.traits());
        near(5.5D, claws.stats().attackDamage());
        near(0D, GolemDesign.fromIds(1, 0, 3, 0, 0, 4).stats().attackDamage());
        near(2D, GolemDesign.fromIds(0, 0, 2, 0, 0, 0).stats().attackDamage());
        var scoutHauler = GolemDesign.fromIds(5, 4, 0, 1, 3, 10);
        same(48, scoutHauler.stats().homeRadius());
        near(56D, scoutHauler.stats().followRange());
        same(2, scoutHauler.stats().carrySlots());
        near(.5D, scoutHauler.stats().stepHeight());
        near(1.5D, scoutHauler.stats().moveSpeedMultiplier());
        var flyer = GolemDesign.fromIds(3, 0, 0, 3, 0, 0);
        same(19, flyer.stats().maxHealth());
        same(4, flyer.stats().armor());
        near(.87D, flyer.stats().moveSpeedMultiplier());
        same(DEFT, CLUMSY.opposite());
        same(CLUMSY, DEFT.opposite());
        same(null, SMART.opposite());
        invalid(() -> GolemDesign.fromLong(6L));
        invalid(() -> GolemDesign.fromIds(0, 5, 0, 0, 0, 0));
        invalid(() -> GolemDesign.fromIds(0, 0, 5, 0, 0, 0));
        invalid(() -> GolemDesign.fromIds(0, 0, 0, 4, 0, 0));
        invalid(() -> GolemDesign.fromIds(0, 0, 0, 0, 4, 0));
        invalid(() -> GolemDesign.fromIds(0, 0, 0, 0, 0, 11));
        invalid(() -> wood.withRank(-1));
        invalid(() -> GolemDesign.fromIds(-1, 0, 0, 0, 0, 0));
        for (int m = 0; m < 6; m++) for (int h = 0; h < 5; h++)
            for (int a = 0; a < 5; a++) for (int l = 0; l < 4; l++)
                for (int addon = 0; addon < 4; addon++) {
                    var design = GolemDesign.fromIds(m, h, a, l, addon, 7);
                    long expected = m | ((long) h << 8) | ((long) a << 16) | ((long) l << 24)
                        | ((long) addon << 32) | (7L << 40) | 0xABCD000000000000L;
                    var restored = GolemDesign.fromLong(design.toLong() | 0xABCD000000000000L);
                    same(expected, restored.toLong());
                    same(m, restored.material().id());
                    same(h, restored.head().id());
                    same(a, restored.arms().id());
                    same(l, restored.legs().id());
                    same(addon, restored.addon().id());
                    same(7, restored.rank());
                    same(expected & ~(0xffL << 40), restored.withRank(0).toLong());
                    for (var trait : restored.traits())
                        same(false, trait.opposite() != null && restored.hasTrait(trait.opposite()));
                }
        System.out.println("BETA26 golem construction: OK (" + assertions + " assertions)");
    }
}
