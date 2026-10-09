package dev.ehtid.thaumcraft.port.core;

import java.util.Objects;
import java.util.Set;

/**
 * BETA26 ScanSky.checkThing + onSuccess, projected into a side-effect-free
 * proposal. The NeoForge adapter performs inventory and research mutations.
 *
 * This deliberately preserves actual moon phase and day-limited observation.
 * Optional randomized celestial notes are a separate, FINAL polish request.
 */
public final class LegacyCelestialScanService {
    private LegacyCelestialScanService() {}

    public enum Kind { SUN, MOON, STAR }
    public enum Status { NOT_SCANNABLE, ALREADY_SEEN, NEEDS_MATERIALS, READY }

    public record Observation(Kind kind, String researchKey, String dayPrefix,
                              int itemMetadata, boolean aligned, boolean night) {
        public Observation {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(researchKey, "researchKey");
            Objects.requireNonNull(dayPrefix, "dayPrefix");
            if (itemMetadata < 0 || itemMetadata > 12) throw new IllegalArgumentException("itemMetadata");
        }
    }

    public record Result(Status status, Observation observation) {
        public Result {
            Objects.requireNonNull(status, "status");
        }
    }

    /**
     * Simulates the original ScanSky day/phase/heading decisions.
     *
     * @param facing3dIndex 1.12 EnumFacing.getIndex(): north=2, south=3,
     *                      west=4, east=5. For stars this yields notes 1..4.
     * @param hasScribingTools original wildcard metadata InventoryUtils check
     * @param hasPaper paper may be consumed, unlike the nonconsumable scribing tools
     */
    public static Result examine(SkyScanLogic.Geometry geometry,
                                 long totalWorldTime, int moonPhase, int facing3dIndex,
                                 Set<String> playerResearch,
                                 boolean hasScribingTools, boolean hasPaper) {
        Objects.requireNonNull(geometry, "geometry");
        Objects.requireNonNull(playerResearch, "playerResearch");
        if (!geometry.eligible()) return new Result(Status.NOT_SCANNABLE, null);

        String prefix = SkyScanLogic.dayPrefix(totalWorldTime);
        Observation observation;
        if (geometry.celestialAligned()) {
            if (geometry.night()) {
                if (moonPhase < 0 || moonPhase > 7) throw new IllegalArgumentException("moonPhase");
                observation = new Observation(
                        Kind.MOON, SkyScanLogic.sunOrMoonKey(prefix, true, moonPhase),
                        prefix, 5 + moonPhase, true, true);
            } else {
                observation = new Observation(
                        Kind.SUN, SkyScanLogic.sunOrMoonKey(prefix, false, moonPhase),
                        prefix, 0, true, false);
            }
        } else if (geometry.night() && facing3dIndex >= 2 && facing3dIndex <= 5) {
            observation = new Observation(
                    Kind.STAR, SkyScanLogic.starKey(prefix, facing3dIndex),
                    prefix, 1 + facing3dIndex - 2, false, true);
        } else {
            return new Result(Status.NOT_SCANNABLE, null);
        }

        if (playerResearch.contains(observation.researchKey()))
            return new Result(Status.ALREADY_SEEN, observation);
        if (!hasScribingTools || !hasPaper)
            return new Result(Status.NEEDS_MATERIALS, observation);
        return new Result(Status.READY, observation);
    }

    /**
     * Mirrors ScanSky.cleanResearch, called after an attempted eligible note
     * except when the current-day research key is already known.
     */
    public static void removeOldDayResearch(PlayerKnowledgeData knowledge,
                                            String currentDayPrefix) {
        Objects.requireNonNull(knowledge, "knowledge");
        Objects.requireNonNull(currentDayPrefix, "currentDayPrefix");
        for (String key : Set.copyOf(knowledge.getResearchList())) {
            if (SkyScanLogic.shouldRemoveOldCelestialResearch(key, currentDayPrefix)) {
                knowledge.removeResearch(key);
            }
        }
    }
}
