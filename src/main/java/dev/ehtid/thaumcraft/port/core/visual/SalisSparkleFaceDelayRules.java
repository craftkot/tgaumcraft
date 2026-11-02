package dev.ehtid.thaumcraft.port.core.visual;

/** BETA26 FXDispatcher.drawBlockSparkles delay: (int)(rand.nextInt(5) + distance * 16). */
public final class SalisSparkleFaceDelayRules {
    private SalisSparkleFaceDelayRules() {}

    public static int delayTicks(int randomOffset, double distance) {
        if (randomOffset < 0 || randomOffset > 4) {
            throw new IllegalArgumentException("BETA26 face random offset must be 0..4");
        }
        if (!Double.isFinite(distance) || distance < 0 || distance > (Integer.MAX_VALUE - 4.0D) / 16.0D) {
            throw new IllegalArgumentException("invalid face distance");
        }
        return (int) (randomOffset + distance * 16.0D);
    }

    public static long dueTick(long now, int delayTicks) {
        if (delayTicks < 0) throw new IllegalArgumentException("negative face delay");
        return Math.addExact(now, delayTicks);
    }
}
