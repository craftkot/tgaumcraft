package dev.ehtid.thaumcraft.port.core;

/**
 * BETA26 TileSmelter material limits, processing intervals and probabilistic
 * essentia retention. Actual inventory/fuel, source item-tag lookup and
 * pollution are live-world work, never faked by this pure core.
 */
public final class LegacySmelterLogic {
    private LegacySmelterLogic() {}
    public static final int ESSENTIA_CAPACITY = 256;
    public enum Tier { BASIC, THAUMIUM, VOID }
    public static float efficiency(Tier tier) {
        return switch (tier) {
            case BASIC -> 0.8f;
            case THAUMIUM -> 0.9f;
            case VOID -> 0.95f;
        };
    }
    public static int outputInterval(Tier tier) {
        return switch (tier) {
            case BASIC -> 15;
            case THAUMIUM -> 10;
            case VOID -> 15;
        };
    }
    /** Source TileSmelter.canSmelt computes this before each cook. */
    public static int smeltTime(int totalAspects, int bellows) {
        if (totalAspects < 0 || bellows < 0) throw new IllegalArgumentException();
        return (int)(totalAspects * 2.0f * (1.0f - 0.125f * bellows));
    }
    public static boolean canHold(int stored, int incoming) {
        return stored >= 0 && incoming > 0 && stored <= ESSENTIA_CAPACITY
                && incoming <= ESSENTIA_CAPACITY - stored;
    }

    public record Output(int stored, int polluted) {
        public Output {
            if (stored < 0 || polluted < 0) throw new IllegalArgumentException();
        }
    }
    /**
     * For every source aspect unit, original smelter loses it when
     * nextFloat() > (aspect=Vitium ? efficiency*0.66 : efficiency).
     * The supplied draws must represent real fresh RNG calls (one per unit).
     */
    public static Output processUnits(Tier tier, boolean fluxAspect, float[] randomDraws) {
        if (tier == null || randomDraws == null) throw new IllegalArgumentException();
        int saved = 0, lost = 0;
        float keepChance = efficiency(tier) * (fluxAspect ? 0.66f : 1.0f);
        for (float draw : randomDraws) {
            if (!Float.isFinite(draw) || draw < 0.0f || draw >= 1.0f)
                throw new IllegalArgumentException("random draw");
            if (draw > keepChance) lost++; else saved++;
        }
        return new Output(saved, lost);
    }
}
