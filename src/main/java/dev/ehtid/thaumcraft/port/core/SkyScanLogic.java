package dev.ehtid.thaumcraft.port.core;

/** Bytecode-level geometry/key helpers for BETA26 ScanSky. */
public final class SkyScanLogic {
    private SkyScanLogic() {}

    public record Geometry(boolean eligible, boolean night, boolean celestialAligned,
                           int transformedCelestialAngle, int yawInt, int pitchAbs) {}

    /**
     * @param celestialAngle vanilla World#getCelestialAngle(0) value before the legacy +0.25 rotation.
     */
    public static Geometry check(boolean targetIsNull, float pitch, boolean canSeeSky, boolean overworld,
                                 boolean knowsCelestialScanning, float yaw, float celestialAngle) {
        if (!targetIsNull || pitch > 0.0F || !canSeeSky || !overworld || !knowsCelestialScanning) {
            return new Geometry(false, false, false, 0, 0, (int)Math.abs(pitch));
        }
        int yawInt = ((int)(yaw + 90.0F)) % 360;
        int pitchAbs = (int)Math.abs(pitch);
        int angle = ((int)((celestialAngle + 0.25D) * 360.0D)) % 360;
        boolean night = angle > 180;
        if (night) angle -= 180;

        boolean yawMatch;
        boolean pitchMatch;
        if (angle > 90) {
            yawMatch = Math.abs(Math.abs(yawInt) - 180) < 10;
            pitchMatch = Math.abs(180 - angle - pitchAbs) < 7;
        } else {
            yawMatch = Math.abs(yawInt) < 10;
            pitchMatch = Math.abs(angle - pitchAbs) < 7;
        }
        boolean aligned = yawMatch && pitchMatch;
        // Intentional BETA26 quirk: at night any otherwise-valid upward sky scan passes checkThing.
        return new Geometry(aligned || night, night, aligned, angle, yawInt, pitchAbs);
    }

    public static String dayPrefix(long totalWorldTime) {
        return "CEL_" + (int)(totalWorldTime / 24000L) + "_";
    }

    public static String sunOrMoonKey(String prefix, boolean night, int moonPhase) {
        return prefix + (night ? "Moon" + moonPhase : "Sun");
    }

    public static String starKey(String prefix, int legacyHorizontalIndex) {
        return prefix + "Star" + (legacyHorizontalIndex - 2);
    }

    public static boolean shouldRemoveOldCelestialResearch(String researchKey, String currentDayPrefix) {
        return researchKey != null && researchKey.startsWith("CEL_") && !researchKey.startsWith(currentDayPrefix);
    }
}
