package dev.ehtid.thaumcraft.port.core;

/**
 * BETA26 FXDispatcher.scanHighlight -> drawSimpleSparkle parameter rules.
 * Keeps the particle's random payload, frame selection, lifetime, alpha/scale
 * interpolation, wind and SimpleParticleType transport explicit.
 */
public final class LegacyThaumometerSparkleRules {
    public static final int FRAME_COUNT = 16;
    public static final int FIRST_SEQUENCE = 320;
    public static final int SECOND_SEQUENCE = 512;
    public static final int BASE_AGE = 4;
    public static final int MIN_LIFETIME = BASE_AGE * 4;
    public static final int MAX_LIFETIME = MIN_LIFETIME + BASE_AGE - 1;
    public static final int MAX_DELAY = 9;

    private static final double COLOR_PAYLOAD_BASE = 1000.0D;
    private static final double COLOR_PAYLOAD_DENOMINATOR = 33554432.0D;
    private static final double SCALE_PAYLOAD_BASE = 2000.0D;
    private static final double DELAY_PAYLOAD_BASE = 3000.0D;

    private LegacyThaumometerSparkleRules() {}

    public record ColorVariant(int red, int green, int blue, boolean firstSequence) {}
    public record Wind(double x, double z) {}

    public static int channel(int minimum, int maximum, int roll) {
        if (minimum < 0 || maximum > 255 || minimum > maximum || roll < 0 || roll > maximum - minimum) {
            throw new IllegalArgumentException("Invalid inclusive color channel roll");
        }
        return minimum + roll;
    }

    /** BETA26 scale = 0.4 + Gaussian * 0.1; deliberately not clamped. */
    public static float initialScale(double gaussian) {
        if (!Double.isFinite(gaussian)) throw new IllegalArgumentException("Non-finite Gaussian");
        return 0.4F + (float) gaussian * 0.1F;
    }

    public static int delay(int roll) {
        if (roll < 0 || roll > MAX_DELAY) throw new IllegalArgumentException("delay roll");
        return roll;
    }

    /** drawSimpleSparkle: baseAge=4, then nextInt(4), yielding 16..19 ticks. */
    public static int lifetime(int roll) {
        if (roll < 0 || roll >= BASE_AGE) throw new IllegalArgumentException("age roll");
        return MIN_LIFETIME + roll;
    }

    /** drawSimpleSparkle: alpha key count = 6 + nextInt(lifetime / 3). */
    public static int alphaKeyCount(int lifetime, int roll) {
        if (lifetime < MIN_LIFETIME || lifetime > MAX_LIFETIME || roll < 0 || roll >= lifetime / 3) {
            throw new IllegalArgumentException("alpha key roll");
        }
        return 6 + roll;
    }

    public static int firstFrame(boolean firstSequence) {
        return firstSequence ? FIRST_SEQUENCE : SECOND_SEQUENCE;
    }

    public static int frame(int firstFrame, int age) {
        if ((firstFrame != FIRST_SEQUENCE && firstFrame != SECOND_SEQUENCE) || age < 0) {
            throw new IllegalArgumentException("Invalid FXGeneric frame");
        }
        return firstFrame + age % FRAME_COUNT;
    }

    /** setScale(scale, scale*2), evaluated in FXGeneric's normalized age space. */
    public static float scaleAt(float initialScale, float progress) {
        if (!Float.isFinite(initialScale) || !Float.isFinite(progress) || progress < 0.0F) {
            throw new IllegalArgumentException("Invalid scale interpolation");
        }
        return initialScale + initialScale * Math.min(progress, 1.0F);
    }

    /** One interpolation step from FXGeneric.calculateFrames. */
    public static float alphaAt(float[] keys, float sample) {
        if (keys == null || keys.length < 2 || !Float.isFinite(sample) || sample < 0.0F) {
            throw new IllegalArgumentException("Invalid alpha sample");
        }
        float bounded = Math.min(sample, keys.length - 1.0F);
        int index = (int) Math.floor(bounded);
        float fraction = bounded - index;
        float delta = index < keys.length - 1 ? keys[index + 1] - keys[index] : 0.0F;
        return keys[index] + delta * fraction;
    }

    /** FXGeneric.setWind(0.0005), including its float-angle calculation. */
    public static Wind wind(int moonPhase, int roll) {
        if (roll < 0 || roll > MAX_DELAY) throw new IllegalArgumentException("wind roll");
        float angle = moonPhase * (40 + roll) / 180.0F * (float) Math.PI;
        return new Wind(Math.cos(angle) * 0.00005D, -Math.sin(angle) * 0.00005D);
    }

    /**
     * SimpleParticleType transports caller-sampled RGB and the 20% frame choice
     * through xSpeed. This finite payload is decoded and zeroed by the provider.
     */
    public static double encodeColorVariant(int red, int green, int blue, boolean firstSequence) {
        if (red < 0 || red > 255 || green < 0 || green > 255 || blue < 0 || blue > 255) {
            throw new IllegalArgumentException("RGB channel");
        }
        int packed = (firstSequence ? 1 << 24 : 0) | (red << 16) | (green << 8) | blue;
        return COLOR_PAYLOAD_BASE + packed / COLOR_PAYLOAD_DENOMINATOR;
    }

    public static ColorVariant decodeColorVariant(double payload) {
        double packedValue = (payload - COLOR_PAYLOAD_BASE) * COLOR_PAYLOAD_DENOMINATOR;
        if (!Double.isFinite(packedValue) || packedValue < 0.0D || packedValue >= COLOR_PAYLOAD_DENOMINATOR) {
            throw new IllegalArgumentException("RGB/variant payload");
        }
        int packed = (int) Math.round(packedValue);
        return new ColorVariant((packed >>> 16) & 255, (packed >>> 8) & 255,
                packed & 255, (packed & (1 << 24)) != 0);
    }

    public static double encodeScale(float scale) {
        if (!Float.isFinite(scale)) throw new IllegalArgumentException("Non-finite scale");
        return SCALE_PAYLOAD_BASE + scale;
    }

    public static float decodeScale(double payload) {
        float scale = (float) (payload - SCALE_PAYLOAD_BASE);
        if (!Float.isFinite(scale)) throw new IllegalArgumentException("Scale payload");
        return scale;
    }

    public static double encodeDelay(int delay) {
        return DELAY_PAYLOAD_BASE + delay(delay);
    }

    public static int decodeDelay(double payload) {
        double decoded = payload - DELAY_PAYLOAD_BASE;
        if (!Double.isFinite(decoded)) throw new IllegalArgumentException("Delay payload");
        return delay((int) Math.round(decoded));
    }
}
