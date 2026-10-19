package dev.ehtid.thaumcraft.port.core.golem;

import java.util.Optional;

/** BETA26 SealBaseContainer actions90..95: Y-/Y+/X-/X+/Z-/Z+, each axis1..8. */
public final class SealAreaControls {
    private SealAreaControls() {}

    public static boolean valid(SealArea.BlockPos size) {
        return size != null && size.x() >= 1 && size.x() <= 8
                && size.y() >= 1 && size.y() <= 8 && size.z() >= 1 && size.z() <= 8;
    }

    public static Optional<SealArea.BlockPos> adjust(SealArea.BlockPos size, int action) {
        if (!valid(size)) return Optional.empty();
        SealArea.BlockPos next = switch (action) {
            case 90 -> size.offset(0, -1, 0);
            case 91 -> size.offset(0, 1, 0);
            case 92 -> size.offset(-1, 0, 0);
            case 93 -> size.offset(1, 0, 0);
            case 94 -> size.offset(0, 0, -1);
            case 95 -> size.offset(0, 0, 1);
            default -> null;
        };
        return valid(next) ? Optional.of(next) : Optional.empty();
    }
}
