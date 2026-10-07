package dev.ehtid.thaumcraft.port.core;

import java.util.Arrays;

/**
 * Loader-independent port of TC6 PlayerWarp.DefaultImpl.
 * Values are clamped to 0..500 exactly like BETA26.
 */
public final class PlayerWarpData {
    public static final int MAX_WARP_PER_TYPE = 500;
    private final int[] warp = new int[WarpType.values().length];
    private int counter;

    public void clear() {
        Arrays.fill(warp, 0);
        counter = 0;
    }

    public int get(WarpType type) {
        return warp[type.ordinal()];
    }

    public void set(WarpType type, int value) {
        warp[type.ordinal()] = clamp(value);
    }

    public int add(WarpType type, int amount) {
        int value = clamp(get(type) + amount);
        warp[type.ordinal()] = value;
        return value;
    }

    public int reduce(WarpType type, int amount) {
        int value = clamp(get(type) - amount);
        warp[type.ordinal()] = value;
        return value;
    }

    public int getCounter() {
        return counter;
    }

    public void setCounter(int counter) {
        // TC6 does not clamp the event counter.
        this.counter = counter;
    }

    public int[] copyLegacyWarpArray() {
        return warp.clone();
    }

    public void loadLegacyWarpArray(int[] old) {
        clear();
        if (old == null) return;
        System.arraycopy(old, 0, warp, 0, Math.min(old.length, warp.length));
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(MAX_WARP_PER_TYPE, value));
    }
}
