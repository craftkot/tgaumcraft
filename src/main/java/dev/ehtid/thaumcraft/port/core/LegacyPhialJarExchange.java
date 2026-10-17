package dev.ehtid.thaumcraft.port.core;

/**
 * All-or-nothing material exchange between the original 10-unit glass phial
 * and the existing 250-unit essentia jar. Unlike pipe IO, a handheld bottle
 * is not required to touch the jar's UP face.
 *
 * These guards are extra safety around BETA26 transfer semantics: a partially
 * filled phial or an almost-full jar is never permitted to erase resources.
 * Inventory replacement is a separate live-world adapter step.
 */
public final class LegacyPhialJarExchange {
    private LegacyPhialJarExchange() {}
    public static final int PHIAL_CAPACITY = 10;

    public static LegacyEssentiaJarLogic.Transfer withdraw(
            LegacyEssentiaJarLogic.State jar, String aspect, int units) {
        if (units != PHIAL_CAPACITY || aspect == null || !aspect.equals(jar.aspect()))
            return new LegacyEssentiaJarLogic.Transfer(jar, 0);
        return LegacyEssentiaJarLogic.take(jar, aspect, units);
    }

    public static LegacyEssentiaJarLogic.Transfer deposit(
            LegacyEssentiaJarLogic.State jar, String aspect, int units) {
        if (units <= 0 || units > PHIAL_CAPACITY || aspect == null
                || !LegacyEssentiaJarLogic.accepts(jar, aspect)
                || jar.amount() > LegacyEssentiaJarLogic.CAPACITY - units)
            return new LegacyEssentiaJarLogic.Transfer(jar, 0);
        return LegacyEssentiaJarLogic.add(jar, aspect, units);
    }
}
