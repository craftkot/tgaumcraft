package dev.ehtid.thaumcraft.port.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Source-boundary and random-call-order checks for BETA26 craftCycle events. */
public final class InfusionInstabilityParityTest {
    private static int checks;
    private static void eq(Object expected, Object actual) {
        checks++;
        if (!java.util.Objects.equals(expected, actual))
            throw new AssertionError("expected " + expected + ", actual " + actual);
    }
    private static void near(float expected, float actual) {
        checks++;
        if (Math.abs(expected - actual) > 0.00001F)
            throw new AssertionError("expected " + expected + ", actual " + actual);
    }
    private static void rejects(Runnable action) {
        checks++;
        try { action.run(); }
        catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Expected IllegalArgumentException");
    }

    /** Each expected RNG call also checks its method and bound. */
    private static final class Script implements LegacyInfusionInstability.RandomSource {
        private final ArrayDeque<Object> values = new ArrayDeque<>();
        private final List<String> calls = new ArrayList<>();
        Script(Object... script) { values.addAll(List.of(script)); }
        public int nextInt(int bound) {
            calls.add("int:" + bound);
            Object value = values.remove();
            if (!(value instanceof Integer n) || n < 0 || n >= bound)
                throw new AssertionError("Unexpected nextInt(" + bound + "): " + value);
            return n;
        }
        public float nextFloat() {
            calls.add("float");
            Object value = values.remove();
            if (!(value instanceof Float n)) throw new AssertionError("Unexpected nextFloat: " + value);
            return n;
        }
        public boolean nextBoolean() {
            calls.add("boolean");
            Object value = values.remove();
            if (!(value instanceof Boolean n)) throw new AssertionError("Unexpected nextBoolean: " + value);
            return n;
        }
        void done(String... expected) {
            eq(List.of(expected), calls);
            eq(0, values.size());
        }
    }

    public static void main(String[] args) {
        eventGate();
        allTwentyFourCases();
        cycleOrder();
        ejection();
        hazardParameters();
        inputValidation();
        System.out.println("BETA26 infusion instability: OK (" + checks + " assertions)");
    }

    private static void eventGate() {
        var r = new Script();
        eq(LegacyInfusionInstability.Event.NONE,
                LegacyInfusionInstability.selectEvent(0, true, r));
        eq(LegacyInfusionInstability.Event.NONE,
                LegacyInfusionInstability.selectEvent(25, true, r));
        r.done();

        // The comparison is inclusive, even at an absolute stability below 1.
        r = new Script(0, 4);
        eq(LegacyInfusionInstability.Event.WARP,
                LegacyInfusionInstability.selectEvent(-0.01F, true, r));
        r.done("int:1500", "int:24");
        r = new Script(1);
        eq(LegacyInfusionInstability.Event.NONE,
                LegacyInfusionInstability.selectEvent(-0.99F, true, r));
        r.done("int:1500");
        r = new Script(25, 23);
        eq(LegacyInfusionInstability.Event.MATRIX_EXPLOSION,
                LegacyInfusionInstability.selectEvent(-25, true, r));
        r.done("int:1500", "int:24");
        r = new Script(26);
        eq(LegacyInfusionInstability.Event.NONE,
                LegacyInfusionInstability.selectEvent(-25, true, r));
        r.done("int:1500");

        // Missing central input fires even at positive stability and bypasses chance RNG.
        r = new Script(22);
        eq(LegacyInfusionInstability.Event.HARM_ALL,
                LegacyInfusionInstability.selectEvent(25, false, r));
        r.done("int:24");
    }

    private static void allTwentyFourCases() {
        var e = LegacyInfusionInstability.Event.values();
        var expected = new LegacyInfusionInstability.Event[] {
                e[1], e[1], e[1], e[1], e[2], e[2], e[2], e[3], e[3], e[3],
                e[4], e[4], e[5], e[5], e[6], e[6], e[7], e[8], e[9], e[9],
                e[10], e[10], e[11], e[12]
        };
        // Spell out every source bucket so enum ordinal changes cannot hide a remapping.
        eq(List.of("EJECT_ITEM", "WARP", "ZAP_ONE", "ZAP_ALL", "EJECT_ITEM_GOO",
                "EJECT_ITEM_FLUX", "EJECT_ITEM_GOO_ALT", "DESTROY_ITEM_FLUX", "HARM_ONE",
                "EJECT_ITEM_EXPLOSION", "HARM_ALL", "MATRIX_EXPLOSION"),
                java.util.Arrays.stream(e).skip(1).map(Enum::name).toList());
        for (int i = 0; i < 24; i++) {
            var r = new Script(i);
            eq(expected[i], LegacyInfusionInstability.selectEvent(0, false, r));
            r.done("int:24");
        }
        eq(false, LegacyInfusionInstability.Event.ZAP_ONE.allTargets());
        eq(true, LegacyInfusionInstability.Event.ZAP_ALL.allTargets());
        eq(true, LegacyInfusionInstability.Event.HARM_ALL.allTargets());
        eq(false, LegacyInfusionInstability.Event.HARM_ONE.allTargets());
        eq(3, LegacyInfusionInstability.Event.EJECT_ITEM_GOO_ALT.ejectionMode());
        eq(4, LegacyInfusionInstability.Event.DESTROY_ITEM_FLUX.ejectionMode());
        eq(-1, LegacyInfusionInstability.Event.WARP.ejectionMode());
    }

    private static void cycleOrder() {
        // Loss takes a stable matrix negative; chance must use this UPDATED stability.
        var r = new Script(0.9F, 0, 7, 0.25F, 0.5F, 3, 0.75F);
        var c = LegacyInfusionInstability.beginCycle(0.1F, 12, 0, 25, true, r);
        near(-1.7F, c.stability());
        eq(LegacyInfusionInstability.Event.ZAP_ONE, c.event());
        eq(false, c.continueRecipe());
        eq(false, c.abortRecipe());
        // Real event execution consumes its randomness BEFORE stability recovery.
        var color = LegacyInfusionInstability.rollArcColor(r);
        near(0.275F, color.red());
        near(0, color.green());
        near(0.25F, color.blue());
        eq(7, LegacyInfusionInstability.rollZapDamage(r));
        near(7.05F, LegacyInfusionInstability.recoverAfterEvent(c.stability(), r));
        r.done("float", "int:1500", "int:24", "float", "float", "int:4", "float");

        r = new Script(0.5F);
        c = LegacyInfusionInstability.beginCycle(10, 0, 0, 25, true, r);
        near(10, c.stability());
        eq(true, c.continueRecipe());
        eq(false, c.abortRecipe());
        r.done("float");

        r = new Script(0F, 0);
        c = LegacyInfusionInstability.beginCycle(25, 0, 0, 25, false, r);
        eq(false, c.continueRecipe());
        eq(true, c.abortRecipe());
        r.done("float", "int:24");
        // Recovery is NOT clamped to the stability cap (missing central input path).
        r = new Script(0.5F);
        near(32.5F, LegacyInfusionInstability.recoverAfterEvent(c.stability(), r));
        r.done("float");

        r = new Script(0.9F, 101);
        c = LegacyInfusionInstability.beginCycle(-99, 100, 0, 25, true, r);
        near(-100, c.stability());
        eq(true, c.continueRecipe());
        r.done("float", "int:1500");
    }

    private static void ejection() {
        var r = new Script(0, 0, 2, 1);
        eq("occupied", LegacyInfusionInstability.findEjectionPedestal(
                List.of("empty", "occupied", "missing"), "occupied"::equals, r));
        r.done("int:3", "int:3", "int:3", "int:3");
        r = new Script();
        eq(null, LegacyInfusionInstability.findEjectionPedestal(List.<String>of(), x -> true, r));
        r.done();
        var repeated = new Object[25];
        java.util.Arrays.fill(repeated, 0);
        r = new Script(repeated);
        eq(null, LegacyInfusionInstability.findEjectionPedestal(List.of("empty"), x -> false, r));
        eq(25, r.calls.size());
        eq(0, r.values.size());
        for (int mode = 0; mode < 6; mode++) {
            var effect = LegacyInfusionInstability.ejectionEffect(mode);
            eq(mode != 4, effect.dropItem());
            eq(mode == 1 || mode == 3, effect.placeFluxGoo());
            eq(mode == 2 || mode == 4, effect.polluteAura());
            near(mode == 5 ? 1 : 0, effect.explosionStrength());
        }
        eq(25, LegacyInfusionInstability.EJECTION_ATTEMPTS);
        eq(5, LegacyInfusionInstability.MITIGATED_BLOCK_EVENT);
        eq(11, LegacyInfusionInstability.EJECTED_BLOCK_EVENT);
        eq(false, LegacyInfusionInstability.EXPLOSION_DAMAGES_BLOCKS);
    }

    private static void hazardParameters() {
        var r = new Script(0.249F);
        var warp = LegacyInfusionInstability.rollWarp(r);
        eq(LegacyInfusionInstability.WarpType.NORMAL, warp.type());
        eq(1, warp.amount());
        r.done("float");
        r = new Script(0.25F, 3);
        warp = LegacyInfusionInstability.rollWarp(r);
        eq(LegacyInfusionInstability.WarpType.TEMPORARY, warp.type());
        eq(5, warp.amount());
        r.done("float", "int:4");
        r = new Script(true, false);
        var harm = LegacyInfusionInstability.rollHarm(r);
        eq(LegacyInfusionInstability.Harm.FLUX_TAINT, harm);
        eq(120, harm.duration());
        eq(0, harm.amplifier());
        eq(false, harm.ambient());
        eq(true, harm.visible());
        eq(false, harm.clearCuratives());
        harm = LegacyInfusionInstability.rollHarm(r);
        eq(LegacyInfusionInstability.Harm.VIS_EXHAUST, harm);
        eq(2400, harm.duration());
        eq(true, harm.ambient());
        eq(true, harm.clearCuratives());
        r.done("boolean", "boolean");
        r = new Script(0, 5, 0, 4, 0F, 0.999F);
        eq(5, LegacyInfusionInstability.rollMitigationCost(r));
        eq(10, LegacyInfusionInstability.rollMitigationCost(r));
        eq(5, LegacyInfusionInstability.rollFluxPollution(r));
        eq(9, LegacyInfusionInstability.rollFluxPollution(r));
        near(1.5F, LegacyInfusionInstability.rollMatrixExplosionStrength(r));
        near(2.499F, LegacyInfusionInstability.rollMatrixExplosionStrength(r));
        r.done("int:6", "int:6", "int:5", "int:5", "float", "float");
        eq(10, LegacyInfusionInstability.TARGET_RANGE);
        eq(32, LegacyInfusionInstability.FX_RANGE);
        eq("!INSTABILITY", LegacyInfusionInstability.RESEARCH_KEY);
    }

    private static void inputValidation() {
        rejects(() -> LegacyInfusionInstability.selectEvent(Float.NaN, true, new Script()));
        rejects(() -> LegacyInfusionInstability.ejectionEffect(-1));
        rejects(() -> LegacyInfusionInstability.ejectionEffect(6));
        rejects(() -> LegacyInfusionInstability.recoverAfterEvent(0, new Script(1F)));
    }
}
