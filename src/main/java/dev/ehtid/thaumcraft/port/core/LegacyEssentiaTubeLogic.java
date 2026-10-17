package dev.ehtid.thaumcraft.port.core;

import java.util.List;
import java.util.Objects;

/**
 * Core BETA26 TileTube pressure and single-unit buffer mechanics.
 * Direction indices preserve 1.12 EnumFacing order: DOWN, UP, NORTH,
 * SOUTH, WEST, EAST. Every connected face must be independently validated
 * by the live-world adapter (including IEssentiaTransport.isBlocked).
 *
 * This intentionally excludes filter, buffer, valve, restrict and one-way
 * variants until their respective source behavior is integrated.
 */
public final class LegacyEssentiaTubeLogic {
    private LegacyEssentiaTubeLogic() {}
    public static final int OPEN_ALL = 0b111111;
    public static final int CAPACITY = 1;
    public static final int MAX_VENT_TICKS = 40;

    public record Tube(String aspect, int amount, String suctionAspect,
                       int suction, int openMask, int venting) {
        public Tube {
            if (amount < 0 || amount > 1 || suction < 0 || suction > 32767
                    || openMask < 0 || openMask > OPEN_ALL || venting < 0)
                throw new IllegalArgumentException("invalid tube state");
            if (amount > 0 && (aspect == null || aspect.isBlank()))
                throw new IllegalArgumentException("stored essentia requires aspect");
            if (amount == 0) aspect = null;
        }
        public static Tube empty() { return new Tube(null, 0, null, 0, OPEN_ALL, 0); }
        public boolean open(int side) {
            return side >= 0 && side < 6 && (openMask & (1 << side)) != 0;
        }
        public Tube toggle(int side) {
            if (side < 0 || side > 5) throw new IllegalArgumentException("side");
            return new Tube(aspect, amount, suctionAspect, suction,
                    openMask ^ (1 << side), venting);
        }
    }

    /** One adjacent IEssentiaTransport; pressure direction is FROM its face. */
    public record Neighbour(int side, int suction, String suctionAspect,
                            String essence, int essenceAmount, int minimumSuction,
                            boolean canOutput, boolean connectable) {
        public Neighbour {
            if (side < 0 || side > 5 || suction < 0 || essenceAmount < 0
                    || minimumSuction < 0) throw new IllegalArgumentException("neighbour");
        }
    }
    public static int opposite(int side) {
        if (side < 0 || side > 5) throw new IllegalArgumentException("side");
        return side ^ 1;
    }

    /** TileTube.calculateSuction: largest eligible neighbor suction minus one. */
    public static Tube calculateSuction(Tube tube, List<Neighbour> neighbours) {
        Objects.requireNonNull(tube);
        Objects.requireNonNull(neighbours);
        int suction = 0;
        String type = null;
        for (int side = 0; side < 6; side++) {
            if (!tube.open(side)) continue;
            for (Neighbour n : neighbours) {
                if (n.side() != side || !n.connectable()) continue;
                if (n.suctionAspect() != null && tube.amount() > 0
                        && !n.suctionAspect().equals(tube.aspect())) continue;
                int s = n.suction();
                if (s > suction + 1) {
                    suction = s - 1;
                    type = n.suctionAspect();
                }
                break;
            }
        }
        return new Tube(tube.aspect(), tube.amount(), type, suction,
                tube.openMask(), tube.venting());
    }


    public enum Variant { NORMAL, VALVE, FILTER, RESTRICT, ONEWAY }

    /**
     * BETA26 TileTubeFilter, TileTubeRestrict, TileTubeOneway override
     * calculateSuction rather than changing core one-unit storage.
     * Facing points toward input for directional suction; only the
     * opposite face supplies suction to a one-way tube.
     */
    public static Tube calculateVariant(Tube tube, List<Neighbour> neighbours,
                                        Variant variant, String filter, int facing) {
        Objects.requireNonNull(variant);
        int suction=0;
        String suctionType=null;
        for (Neighbour n : neighbours) {
            if (!n.connectable() || !tube.open(n.side())) continue;
            if (variant==Variant.ONEWAY && n.side()!=opposite(facing)) continue;
            if (filter!=null && n.suctionAspect()!=null &&
                    !filter.equals(n.suctionAspect())) continue;
            if (filter==null && tube.amount()>0 && n.suctionAspect()!=null &&
                    !n.suctionAspect().equals(tube.aspect())) continue;
            if (n.suction()>suction+1) {
                suction=variant==Variant.RESTRICT?n.suction()/2:n.suction()-1;
                suctionType=n.suctionAspect()==null?filter:n.suctionAspect();
            }
        }
        return new Tube(tube.aspect(),tube.amount(),suctionType,suction,
                tube.openMask(),tube.venting());
    }

    /** Directional source path and non-consuming filter preflight. */
    public static boolean canPullVariant(Tube tube, Neighbour source,
                                          Variant variant, String filter, int facing) {
        if (variant==Variant.ONEWAY && source.side()==opposite(facing))return false;
        if (filter!=null && !filter.equals(source.essence()))return false;
        return canPull(tube,source);
    }

    /** TileTube.checkVenting: conflicting typed suction on adjacent port. */
    public static Tube checkVenting(Tube tube, List<Neighbour> neighbours) {
        if (tube.suction() == 0 || tube.venting() > 0) return tube;
        for (Neighbour n : neighbours) {
            if (!tube.open(n.side()) || !n.connectable()) continue;
            int adjacent = n.suction();
            if ((adjacent == tube.suction() || adjacent == tube.suction() - 1)
                    && !Objects.equals(n.suctionAspect(), tube.suctionAspect())) {
                return new Tube(tube.aspect(), tube.amount(), tube.suctionAspect(),
                        tube.suction(), tube.openMask(), MAX_VENT_TICKS);
            }
        }
        return tube;
    }

    /** Tube may pull one unit only with higher suction and compatible type. */
    public static boolean canPull(Tube tube, Neighbour source) {
        return tube.amount() == 0 && tube.venting() == 0
                && tube.open(source.side()) && source.connectable()
                && source.canOutput() && source.essenceAmount() > 0
                && source.essence() != null
                && (tube.suctionAspect() == null ||
                    tube.suctionAspect().equals(source.essence()))
                && tube.suction() > source.suction()
                && tube.suction() >= source.minimumSuction();
    }

    /** One transported unit, exactly as TileTube.addEssentia/takeEssentia. */
    public static Tube acceptOne(Tube tube, String essence, int side) {
        if (!tube.open(side) || tube.amount() != 0 || essence == null)
            return tube;
        return new Tube(essence, 1, tube.suctionAspect(),
                tube.suction(), tube.openMask(), tube.venting());
    }
    public static Tube takeOne(Tube tube, String essence, int side) {
        if (!tube.open(side) || tube.amount() != 1 ||
                !Objects.equals(tube.aspect(), essence)) return tube;
        return new Tube(null, 0, tube.suctionAspect(),
                tube.suction(), tube.openMask(), tube.venting());
    }
    public static Tube tickVenting(Tube tube) {
        return tube.venting() == 0 ? tube
                : new Tube(tube.aspect(), tube.amount(), tube.suctionAspect(),
                tube.suction(), tube.openMask(), tube.venting() - 1);
    }
}
