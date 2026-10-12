package dev.ehtid.thaumcraft.port.core;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Original BETA26 TileInfusionMatrix layout and surroundings search offsets. */
public final class LegacyInfusionMatrixLayout {
    private LegacyInfusionMatrixLayout() {}
    public enum Part { OTHER, PEDESTAL, PILLAR }
    public record Offset(int x, int y, int z) {}

    public static final Offset CENTER = new Offset(0, -2, 0);
    public static List<Offset> requiredPillarOffsets() {
        return List.of(new Offset(1,-2,1),new Offset(-1,-2,1),
                new Offset(1,-2,-1),new Offset(-1,-2,-1));
    }

    /** Original only checks BlockPedestal below and BlockPillar at four corners. */
    public static boolean validLocation(Function<Offset, Part> world) {
        if (world == null || world.apply(CENTER) != Part.PEDESTAL) return false;
        for (Offset corner : requiredPillarOffsets())
            if (world.apply(corner) != Part.PILLAR) return false;
        return true;
    }

    /**
     * Matrix surrounding scan: x/z -8..8, original inner var -3..7
     * corresponding to relative world Y +3..-7. Entire central column
     * skipped, iteration order preserved for future instability parity.
     */
    public static List<Offset> scanPedestals(Function<Offset, Part> world) {
        if (world == null) return List.of();
        List<Offset> found = new ArrayList<>();
        for (int x=-8; x<=8; x++)
            for (int z=-8; z<=8; z++) {
                if (x==0 && z==0) continue;
                for (int v=-3; v<=7; v++) {
                    Offset at = new Offset(x,-v,z);
                    if (world.apply(at)==Part.PEDESTAL) found.add(at);
                }
            }
        return List.copyOf(found);
    }

    public static boolean withinScanVolume(Offset pos) {
        return pos!=null && pos.x()>=-8 && pos.x()<=8
                && pos.z()>=-8 && pos.z()<=8
                && pos.y()>=-7 && pos.y()<=3
                && (pos.x()!=0 || pos.z()!=0);
    }
}
