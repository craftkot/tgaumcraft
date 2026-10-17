package dev.ehtid.thaumcraft.port.core;

/**
 * Original TC6 6.1.BETA26 SmelterAux and SmelterVent arcane recipes.
 * Aux:  WTW / RGR / IBI; W greatwood, T filtered tube, R brass plate,
 *       G alchemical construct, I iron plate, B bellows.
 * Vent: IBI / MGF / IBI; I iron plate, B brass plate, M/F alchemy filter,
 *       G alchemical construct.
 */
public final class LegacySmelterAttachmentArcaneRecipe {
    private LegacySmelterAttachmentArcaneRecipe() {}

    public static final int AER_SLOT = 9;
    public static final int TERRA_SLOT = 12;

    public enum Kind {
        AUX("IMPROVEDSMELTING", 100, (1 << 0) | (1 << 3),
                new String[]{"WTW", "RGR", "IBI"}),
        VENT("IMPROVEDSMELTING2", 150, 1 << 0,
                new String[]{"IBI", "MGF", "IBI"});

        public final String research;
        public final int vis;
        public final int crystalMask;
        private final String[] pattern;

        Kind(String research, int vis, int crystalMask, String[] pattern) {
            this.research = research;
            this.vis = vis;
            this.crystalMask = crystalMask;
            this.pattern = pattern;
        }
    }

    public static boolean matches(Kind kind, boolean[] greatwood,
                                  boolean[] filteredTube, boolean[] brass,
                                  boolean[] iron, boolean[] construct,
                                  boolean[] bellows, boolean[] filter) {
        if (kind == null || greatwood == null || filteredTube == null
                || brass == null || iron == null || construct == null
                || bellows == null || filter == null
                || greatwood.length != 9 || filteredTube.length != 9
                || brass.length != 9 || iron.length != 9
                || construct.length != 9 || bellows.length != 9 || filter.length != 9)
            return false;
        for (int slot = 0; slot < 9; slot++) {
            char symbol = kind.pattern[slot / 3].charAt(slot % 3);
            boolean correct = switch (symbol) {
                case 'W' -> greatwood[slot];
                case 'T' -> filteredTube[slot];
                case 'R' -> brass[slot];
                case 'B' -> kind == Kind.AUX ? bellows[slot] : brass[slot];
                case 'I' -> iron[slot];
                case 'G' -> construct[slot];
                case 'M', 'F' -> filter[slot];
                default -> false;
            };
            if (!correct) return false;
        }
        return true;
    }

    public static int discountedVis(Kind kind, float discount) {
        return (int) (kind.vis * (1.0F - discount));
    }
}
