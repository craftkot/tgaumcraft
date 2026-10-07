package dev.ehtid.thaumcraft.port.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Loader-neutral control-flow port of BETA26 ScanningManager.
 *
 * Minecraft-specific target normalization (ItemStack/EntityItem/BlockPos), inventory recursion, chat rendering,
 * and FX are deliberately left for the NeoForge adapter. The important ordered multi-handler scan behavior is
 * reproduced here exactly, including repeated getResearchKey() calls and exception handling in the
 * isThingStillScannable path.
 */
public final class ScanningService<P, T> {
    public enum Feedback {
        /** At least one handler succeeded and all successful handlers were keyed. BETA26 emits tc.knownobject. */
        KNOWN_OBJECT,
        /** No handler successfully scanned. BETA26 emits tc.unknownobject. */
        UNKNOWN_OBJECT,
        /** A successful handler had null/empty research key, suppressing both chat messages. */
        NONE
    }

    @FunctionalInterface
    public interface ResearchProgressor<P> {
        boolean progress(P player, String researchKey);
    }

    @FunctionalInterface
    public interface ResearchKnowledge<P> {
        boolean knows(P player, String researchKey);
    }

    public record ScanResult(
            boolean anySuccess,
            boolean anyUnkeyedSuccess,
            int matchingHandlers,
            int successfulHandlers,
            Feedback feedback) {}

    private final List<ScanThing<P, T>> things = new ArrayList<>();

    public void addScannableThing(ScanThing<P, T> thing) {
        things.add(Objects.requireNonNull(thing, "thing"));
    }

    public int size() {
        return things.size();
    }

    public List<ScanThing<P, T>> things() {
        return Collections.unmodifiableList(things);
    }

    /** Mirrors the first (non-container-recursive) half of ScanningManager.scanTheThing. */
    public ScanResult scanTheThing(P player, T thing, ResearchProgressor<P> progressor) {
        Objects.requireNonNull(progressor, "progressor");
        boolean anySuccess = false;
        boolean anyUnkeyedSuccess = false;
        int matching = 0;
        int successful = 0;

        for (ScanThing<P, T> scan : things) {
            if (!scan.checkThing(player, thing)) continue;
            matching++;

            // Preserve repeated getter calls rather than caching. A custom IScanThing is allowed to be stateful.
            String first = scan.getResearchKey(player, thing);
            if (first != null) {
                String second = scan.getResearchKey(player, thing);
                if (!second.isEmpty()) {
                    String third = scan.getResearchKey(player, thing);
                    if (!progressor.progress(player, third)) continue;
                }
            }

            String fourth = scan.getResearchKey(player, thing);
            if (fourth == null || scan.getResearchKey(player, thing).isEmpty()) {
                anyUnkeyedSuccess = true;
            }
            anySuccess = true;
            successful++;
            scan.onSuccess(player, thing);
        }

        Feedback feedback = anyUnkeyedSuccess
                ? Feedback.NONE
                : (anySuccess ? Feedback.KNOWN_OBJECT : Feedback.UNKNOWN_OBJECT);
        return new ScanResult(anySuccess, anyUnkeyedSuccess, matching, successful, feedback);
    }

    /**
     * Mirrors ScanningManager.isThingStillScannable. Exceptions from the knowledge check (notably a null key)
     * are swallowed and iteration continues.
     */
    public boolean isThingStillScannable(P player, T thing, ResearchKnowledge<P> knowledge) {
        Objects.requireNonNull(knowledge, "knowledge");
        for (ScanThing<P, T> scan : things) {
            if (!scan.checkThing(player, thing)) continue;
            try {
                if (!knowledge.knows(player, scan.getResearchKey(player, thing))) return true;
            } catch (Exception ignored) {
                // Exact BETA26 behavior: the try/catch starts after checkThing and only surrounds research lookup.
            }
        }
        return false;
    }
}
