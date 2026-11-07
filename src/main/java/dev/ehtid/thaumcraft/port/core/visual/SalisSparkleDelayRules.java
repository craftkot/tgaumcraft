package dev.ehtid.thaumcraft.port.core.visual;

/** BETA26 FXDispatcher.drawSimpleSparkle 0..4 tick trail start delay. */
public final class SalisSparkleDelayRules {
    private SalisSparkleDelayRules() {}

    public static long dueTick(long now, int delayTicks) {
        if (delayTicks < 0 || delayTicks > 4) {
            throw new IllegalArgumentException("BETA26 trail delay must be 0..4 ticks");
        }
        return Math.addExact(now, delayTicks);
    }
}
