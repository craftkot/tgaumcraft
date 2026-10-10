package dev.ehtid.thaumcraft.port.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Original BETA26 ContainerResearchTable card button state transitions.
 *
 * The loader adapter owns subclass cardNBT and inventory/XP validation. This
 * layer only models the original selected flags, previous lastDraw seed save,
 * and inspiration cost, preserving the input ordering of cardChoices.
 */
public final class LegacyTheorycraftChoiceFlow {
    private LegacyTheorycraftChoiceFlow() {}

    public record Choice(String key, long seed, boolean fromAid, boolean selected) {
        public Choice {
            Objects.requireNonNull(key);
        }
        public Choice selectedCopy() {
            return new Choice(key, seed, fromAid, true);
        }
    }

    /**
     * BETA26 button 4/5/6 after card.activate returns TRUE:
     * set ONLY the chosen selection flag, and apply -getInspirationCost.
     * It doesn't automatically drop other choices or alter lastDraw.
     * No cost or state mutation occurs if activation failed.
     */
    public static List<Choice> afterActivation(
            LegacyResearchTableData session, List<Choice> choices,
            int index, int inspirationCost, boolean activated) {
        Objects.requireNonNull(session);
        Objects.requireNonNull(choices);
        if (!activated || index < 0 || index >= choices.size())
            return List.copyOf(choices);
        ArrayList<Choice> updated = new ArrayList<>(choices);
        Choice previous = updated.get(index);
        if (previous == null) throw new IllegalArgumentException("null choice");
        updated.set(index, previous.selectedCopy());
        session.addInspiration(-inspirationCost);
        return List.copyOf(updated);
    }

    public record Kept(List<Choice> remainingChoices, Choice lastDraw,
                       List<Long> savedCardSeeds) {
        public Kept {
            remainingChoices = List.copyOf(remainingChoices);
            savedCardSeeds = List.copyOf(savedCardSeeds);
        }
    }

    /**
     * BETA26 button 1 first appends the PREVIOUS lastDraw.seed to savedCards,
     * next finds the FIRST currently selected card, then clears choices.
     * No ink, paper, inspiration or other state is consumed by this button.
     *
     * A button 1 call with no selected card still performs the original
     * transition; the menu may reject this on the modern server as a
     * safety rule against repeated duplicate savedCard seeding.
     */
    public static Kept keepSelection(
            List<Choice> choices, Choice previousLastDraw, List<Long> savedSeeds) {
        Objects.requireNonNull(choices);
        Objects.requireNonNull(savedSeeds);
        ArrayList<Long> seeds = new ArrayList<>(savedSeeds);
        if (previousLastDraw != null) seeds.add(previousLastDraw.seed());
        Choice last = previousLastDraw;
        for (Choice choice : choices) {
            if (choice != null && choice.selected()) {
                last = choice;
                break;
            }
        }
        return new Kept(List.of(), last, seeds);
    }

    /**
     * Apply original drawCards scalar/bookkeeping results after a server-side
     * successful paper debit, using the complete 33 original normal cards.
     * CardChoice subclass NBT must be serialized separately by the adapter.
     */
    public static void afterPaperDebitedForDraw(
            LegacyResearchTableData session, LegacyTheorycraftDrawEngine.Result result,
            String playerName) {
        Objects.requireNonNull(session);
        Objects.requireNonNull(result);
        Objects.requireNonNull(playerName);
        session.replaceAidCards(result.remainingAidCards());
        session.addBonusDraws(result.remainingBonusDraws() - session.bonusDraws());
        session.setPlayer(playerName);
    }
}
