package dev.ehtid.thaumcraft.port.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Pure Java port of the state-transition part of BETA26 ResearchManager.progressResearch.
 *
 * It intentionally preserves several non-obvious legacy rules:
 * - stage completion is represented as stageCount + 1;
 * - one requirement-free stage can be skipped in addition to the normal +1 advancement;
 * - completion warp can count the prior/final stage twice exactly as the BETA26 bytecode does;
 * - sibling completion is attempted after every successful progress call, not only on completion;
 * - addenda of already-complete research are checked only when a research becomes complete.
 *
 * Minecraft-only side effects (Forge event cancellation, XP, inventory rewards, chat, packet sync) are
 * returned as a Result for the NeoForge integration layer to perform later.
 */
public final class ResearchProgressionService {
    private final ResearchCatalog catalog;

    public ResearchProgressionService(ResearchCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    public record WarpGain(int permanent, int normal) {
        public static final WarpGain NONE = new WarpGain(0, 0);
    }

    public record Result(
            boolean progressed,
            boolean completed,
            int resultingStage,
            WarpGain warpGain,
            List<String> addendaUnlocked,
            List<String> siblingCompletionAttempts,
            boolean requestSync,
            int xpAward,
            List<KnowledgeReward> knowledgeRewards) {

        static Result rejected() {
            return new Result(false, false, -1, WarpGain.NONE, List.of(), List.of(), false, 0, List.of());
        }
    }

    /** Convenience entry point wired to the exact built-in ResearchManager.doesPlayerHaveRequisites port. */
    public Result progress(
            PlayerKnowledgeData knowledge,
            PlayerWarpData warp,
            String key,
            boolean eventCancelled,
            boolean showFlags,
            boolean noFlags,
            boolean wussMode,
            boolean serverSide) {
        return progress(knowledge, warp, key,
                candidate -> ResearchRequisites.hasRequisites(knowledge, catalog, candidate),
                eventCancelled, showFlags, noFlags, wussMode, serverSide);
    }

    /**
     * @param requisites mirrors ResearchManager.doesPlayerHaveRequisites; unknown entries normally return true.
     * @param eventCancelled mirrors a cancelled ResearchEvent.Research.
     * @param showFlags mirrors the BETA26 boolean argument.
     * @param noFlags mirrors ResearchManager.noFlags for this completion; caller owns resetting that global.
     * @param wussMode disables warp grants.
     * @param serverSide warp is only actually granted server-side in BETA26.
     */
    public Result progress(
            PlayerKnowledgeData knowledge,
            PlayerWarpData warp,
            String key,
            Predicate<String> requisites,
            boolean eventCancelled,
            boolean showFlags,
            boolean noFlags,
            boolean wussMode,
            boolean serverSide) {
        return progressInternal(knowledge, warp, key, requisites, eventCancelled, showFlags, noFlags,
                wussMode, serverSide, new HashSet<>());
    }

    private Result progressInternal(
            PlayerKnowledgeData knowledge,
            PlayerWarpData warp,
            String key,
            Predicate<String> requisites,
            boolean eventCancelled,
            boolean showFlags,
            boolean noFlags,
            boolean wussMode,
            boolean serverSide,
            Set<String> siblingRecursionGuard) {

        Objects.requireNonNull(knowledge, "knowledge");
        Objects.requireNonNull(warp, "warp");
        Objects.requireNonNull(requisites, "requisites");

        if (knowledge.isResearchComplete(key, catalog) || !requisites.test(key) || eventCancelled) {
            return Result.rejected();
        }

        if (!knowledge.isResearchKnown(key)) knowledge.addResearch(key);

        ResearchEntryData entry = catalog.get(key);
        boolean complete = true;
        int resultingStage = knowledge.getResearchStage(key);
        WarpGain gain = WarpGain.NONE;
        List<String> unlockedAddenda = new ArrayList<>();
        List<KnowledgeReward> grantedKnowledge = new ArrayList<>();

        if (entry != null && entry.stages() != null) {
            List<ResearchStageData> stages = entry.stages();
            int stage = knowledge.getResearchStage(key);
            ResearchStageData previousStage = null;

            if (stage > 0) {
                stage = Math.min(stage, stages.size());
                previousStage = stages.get(stage - 1);
            }

            if (stages.size() == 1 && stage == 0 && !stages.get(0).hasGateRequirement()) {
                stage++;
            } else if (stages.size() > 1
                    && stages.size() > stage + 1
                    && stage < stages.size()
                    && !stages.get(stage).hasGateRequirement()) {
                stage++;
            }

            int storedStage = Math.min(stages.size() + 1, stage + 1);
            knowledge.setResearchStage(key, storedStage);
            resultingStage = storedStage;
            complete = stage >= stages.size();

            int legacyWarp = previousStage == null ? 0 : previousStage.warp();
            if (complete) {
                stage = Math.min(stage, stages.size());
                // This is safe for all real BETA26 entries: an empty stages array is not used.
                if (!stages.isEmpty()) previousStage = stages.get(stage - 1);
            }
            if (previousStage != null) {
                legacyWarp += previousStage.warp();
            }

            if (legacyWarp > 0 && !wussMode && serverSide) {
                int permanent;
                int normal;
                if (legacyWarp > 1) {
                    int half = legacyWarp / 2;
                    permanent = legacyWarp - half;
                    normal = half;
                } else {
                    permanent = legacyWarp;
                    normal = 0;
                }
                if (permanent > 0) warp.add(WarpType.PERMANENT, permanent);
                if (normal > 0) warp.add(WarpType.NORMAL, normal);
                gain = new WarpGain(permanent, normal);
            }
        }

        if (entry != null && complete) {
            if (showFlags) {
                knowledge.setResearchFlag(key, ResearchFlag.POPUP);
                if (!noFlags) knowledge.setResearchFlag(key, ResearchFlag.RESEARCH);
            }

            for (KnowledgeReward reward : entry.knowledgeRewards()) {
                knowledge.addKnowledge(reward.type(), reward.categoryKey(), reward.rawAmount());
                grantedKnowledge.add(reward);
            }

            // Exact BETA26 behavior: scan every complete entry and stop at the first matching addendum per entry.
            for (ResearchEntryData other : catalog.entries()) {
                if (other.addenda().isEmpty() || !knowledge.isResearchComplete(other.key(), catalog)) continue;
                for (ResearchAddendumData addendum : other.addenda()) {
                    if (addendum.requiredResearch().contains(key)) {
                        knowledge.setResearchFlag(other.key(), ResearchFlag.PAGE);
                        unlockedAddenda.add(other.key());
                        break;
                    }
                }
            }
        }

        List<String> siblingAttempts = new ArrayList<>();
        if (entry != null && !entry.siblings().isEmpty()) {
            for (String sibling : entry.siblings()) {
                if (!knowledge.isResearchComplete(sibling, catalog) && requisites.test(sibling)) {
                    siblingAttempts.add(sibling);
                    // completeResearch() repeatedly calls progressResearch() until it returns false.
                    // Guard only protects malformed cycles; normal BETA26 siblings terminate via completion.
                    if (siblingRecursionGuard.add(sibling)) {
                        while (true) {
                            Result r = progressInternal(knowledge, warp, sibling, requisites, false, showFlags,
                                    noFlags, wussMode, serverSide, siblingRecursionGuard);
                            if (!r.progressed()) break;
                        }
                        siblingRecursionGuard.remove(sibling);
                    }
                }
            }
        }

        return new Result(
                true,
                complete,
                resultingStage,
                gain,
                List.copyOf(unlockedAddenda),
                List.copyOf(siblingAttempts),
                showFlags,
                showFlags && entry != null ? 5 : 0,
                List.copyOf(grantedKnowledge));
    }
}
