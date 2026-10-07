package dev.ehtid.thaumcraft.port.core;

/** Loader-neutral equivalent of TC6 IScanThing. */
public interface ScanThing<P, T> {
    boolean checkThing(P player, T thing);
    String getResearchKey(P player, T thing);
    default void onSuccess(P player, T thing) {}
}
