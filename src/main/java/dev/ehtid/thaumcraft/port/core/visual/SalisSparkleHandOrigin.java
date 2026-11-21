package dev.ehtid.thaumcraft.port.core.visual;

/** Original BETA26 EntityUtils.posToHand anchor, independent of Minecraft classes. */
public final class SalisSparkleHandOrigin {
    private static final double HAND_SIDE = 0.3D;
    private static final double LOOK_FORWARD = 0.3D;
    private static final double CHEST_OFFSET = 0.25D;

    private SalisSparkleHandOrigin() {}

    public static double[] position(double x, double boundingBoxMinY, double height, double z,
                                    double yawDegrees, double lookX, double lookY, double lookZ,
                                    boolean offHand) {
        // BETA26 uses EnumHand, not the player's preferred left/right main arm.
        double yaw = Math.toRadians(yawDegrees + (offHand ? 180.0D : 0.0D));
        return new double[] {
                x - Math.cos(yaw) * HAND_SIDE + lookX * LOOK_FORWARD,
                boundingBoxMinY + height / 2.0D + CHEST_OFFSET + lookY * LOOK_FORWARD,
                z - Math.sin(yaw) * HAND_SIDE + lookZ * LOOK_FORWARD
        };
    }
}
