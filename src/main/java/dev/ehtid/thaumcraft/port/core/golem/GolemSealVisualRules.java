package dev.ehtid.thaumcraft.port.core.golem;

/** BETA26 RenderEventHandler.drawSeals/drawSealArea; geometry and artwork belong to the client adapter. */
public final class GolemSealVisualRules {
    public static final double RANGE_SQUARED = 256D;
    private GolemSealVisualRules() {}

    public static float opacity(double distanceSquared) {
        return distanceSquared >= 0 && distanceSquared <= RANGE_SQUARED
                ? 1F - (float) (distanceSquared / RANGE_SQUARED) : 0F;
    }

    public static float unpaintedChannel(float time, int coordinate, int period) {
        float angle=(time+coordinate)/period;
        int index=(int)(angle*10430.378F)&65535;
        return .7F+(float)Math.sin(index*Math.PI*2D/65536D)*.1F;
    }
}
