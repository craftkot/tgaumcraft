package dev.ehtid.thaumcraft.scanning;

import dev.ehtid.thaumcraft.player.ThaumcraftPlayerAttachments;
import dev.ehtid.thaumcraft.port.core.BuiltInResearchCatalog;
import dev.ehtid.thaumcraft.port.core.BuiltInResearchCategories;
import dev.ehtid.thaumcraft.port.core.AspectRegistry;
import dev.ehtid.thaumcraft.port.core.LegacyResearchTableData;
import dev.ehtid.thaumcraft.port.core.LegacyTheorycraftDrawEngine;
import dev.ehtid.thaumcraft.port.core.LegacyTheorycraftManifest;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;

/**
 * Full original 33-class ResearchTableData.drawCards wiring in an
 * ISOLATED candidate preview, with true subclass NBT produced by the
 * NeoForge server player and the actual registered mod item mappings.
 *
 * Called by live menu buttons 2/3 through ResearchTableBlockEntity. The
 * block entity charges paper for accepted draws; activation and other payments
 * occur on menu buttons 4..6. Missing 1.21 source items FAIL closed.
 *
 * The passed RNG is consumed in original call order, but no inventory,
 * knowledge, warp, inspiration, aidCards or pending choices are mutated.
 * Invoke with the actual player's random ONLY when actually committing a
 * successful authoritative draw, not when rendering a client preview.
 */
public final class NeoForgeTheorycraftDrawBridge {
    private static final dev.ehtid.thaumcraft.port.core.ResearchCatalog CATALOG =
            BuiltInResearchCatalog.create();
    private static final dev.ehtid.thaumcraft.port.core.ResearchCategoryRegistry CATEGORIES =
            BuiltInResearchCategories.create(new AspectRegistry());

    private NeoForgeTheorycraftDrawBridge() {}

    public record PreparedDraw(List<LegacyTheorycraftChoiceNbt.Choice> choices,
                               List<String> remainingAidCards, int remainingBonusDraws,
                               int attempts) {
        public PreparedDraw {
            choices = List.copyOf(choices);
            remainingAidCards = List.copyOf(remainingAidCards);
        }
    }

    public static PreparedDraw prepare(
            ServerPlayer player, LegacyResearchTableData theory, int requestedCards,
            LegacyTheorycraftDrawEngine.RandomSource random) {
        Objects.requireNonNull(player);
        Objects.requireNonNull(theory);
        Objects.requireNonNull(random);
        if (requestedCards != 2 && requestedCards != 3)
            throw new IllegalArgumentException("BETA26 draw button requests 2 or 3 choices");

        var knowledge = player.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge();
        List<String> availableCategories = LegacyResearchTableData.availableCategories(
                knowledge, CATALOG, CATEGORIES, new HashSet<>(theory.blockedCategories()));
        IdentityHashMap<LegacyTheorycraftDrawEngine.Candidate,
                LegacyTheorycraftChoiceNbt.Choice> generated = new IdentityHashMap<>();

        var result = LegacyTheorycraftDrawEngine.draw(
                requestedCards, theory.bonusDraws(), theory.inspiration(),
                theory.aidCards(), LegacyTheorycraftManifest.cardsInHashMapIterationOrder(),
                new HashSet<>(availableCategories), theory.blockedCategories(), random,
                (cardKey, rng) -> {
                    // Original generateCard() obtains the player's nextLong()
                    // BEFORE initialize(). Even an initialization rejection
                    // must burn that seed, or later cards draw differently.
                    if (!LegacyTheorycraftManifest.allCardKeys().contains(cardKey)) return null;
                    long seed = rng.nextLong();
                    var initialized = NeoForgeTheorycraftCardInitializer.initialize(
                            player, theory, cardKey, seed, false);
                    if (initialized == null) return null;
                    var meta = NeoForgeTheorycraftCardInitializer.metadata(cardKey);
                    var candidate = new LegacyTheorycraftDrawEngine.Candidate(
                            cardKey, seed, meta.cost(),
                            NeoForgeTheorycraftCardInitializer.initializedCategory(initialized),
                            meta.aidOnly());
                    generated.put(candidate, initialized);
                    return candidate;
                });

        ArrayList<LegacyTheorycraftChoiceNbt.Choice> choices =
                new ArrayList<>(result.choices().size());
        for (var choice : result.choices()) {
            var original = generated.get(choice.card());
            if (original == null)
                throw new IllegalStateException("original BETA26 candidate lost its NBT");
            choices.add(new LegacyTheorycraftChoiceNbt.Choice(
                    original.key(), choice.fromAid(), false,
                    original.cardNBT(), original.originalRow()));
        }
        return new PreparedDraw(choices, result.remainingAidCards(),
                result.remainingBonusDraws(), result.attempts());
    }
}
