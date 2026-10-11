package dev.ehtid.thaumcraft.scanning;

import dev.ehtid.thaumcraft.player.ThaumcraftPlayerAttachments;
import dev.ehtid.thaumcraft.port.core.AspectRegistry;
import dev.ehtid.thaumcraft.port.core.BuiltInResearchCatalog;
import dev.ehtid.thaumcraft.port.core.BuiltInResearchCategories;
import dev.ehtid.thaumcraft.port.core.LegacyAdditionalTheoryCards;
import dev.ehtid.thaumcraft.port.core.LegacyAlchemyTheoryCards;
import dev.ehtid.thaumcraft.port.core.LegacyCardAnalyze;
import dev.ehtid.thaumcraft.port.core.LegacyCelestialTheoryCard;
import dev.ehtid.thaumcraft.port.core.LegacyMaterialTheoryCards;
import dev.ehtid.thaumcraft.port.core.LegacyResearchTableData;
import dev.ehtid.thaumcraft.port.core.LegacyTheorycraftCardMetadata;
import dev.ehtid.thaumcraft.port.core.LegacyTheorycraftFieldlessInit;
import dev.ehtid.thaumcraft.port.core.LegacyTheorycraftManifest;
import dev.ehtid.thaumcraft.port.core.LegacyTheorycraftMiscCards;
import dev.ehtid.thaumcraft.port.core.LegacyTheorycraftOptionPools;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;
import java.util.Set;

/**
 * Original BETA26 TheorycraftCard.initialize adapter for every one of the
 * 33 registered card subclasses, including all original class-specific NBT.
 *
 * No draw UI is enabled yet: ResearchTableData.drawCards may only be wired
 * when all original effects, transactional material payments, and real
 * client choices work. This creates real, seed-stable cardNBT, not fake
 * simplified "placeholder" cards. Original item options NOT yet mapped to
 * 1.21 fail closed without re-rolling or consuming materials.
 *
 * The original CardAnalyze had a confirmed implementation bug. As explicitly
 * permitted for the port, this uses the existing narrowly scoped bugfix to
 * check observation of the CURRENT category, not the unset card.cat field.
 */
public final class NeoForgeTheorycraftCardInitializer {
    private static final AspectRegistry ASPECTS = new AspectRegistry();

    private NeoForgeTheorycraftCardInitializer() {}

    /**
     * Return null when an original initializer fails or the exact selected
     * seeded source ItemStack does not have a safe 1.21 equivalent.
     * All calls MUST be on the server thread with an existing true session.
     */
    public static LegacyTheorycraftChoiceNbt.Choice initialize(
            ServerPlayer player, LegacyResearchTableData table,
            String key, long seed, boolean fromAid) {
        Objects.requireNonNull(player, "server player");
        Objects.requireNonNull(table, "BETA26 research session");
        if (key == null || !LegacyTheorycraftManifest.allCardKeys().contains(key))
            return null;

        CompoundTag card = new CompoundTag();
        card.putLong("seed", seed);
        if (LegacyTheorycraftFieldlessInit.isFieldless(key)) {
            if (!LegacyTheorycraftFieldlessInit.initialize(
                    key, table, player.experienceLevel)) return null;
        } else {
            String name = key.substring(key.lastIndexOf('.') + 1);
            var knowledge = player.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge();
            var catalog = BuiltInResearchCatalog.create();
            var categories = BuiltInResearchCategories.create(ASPECTS);
            switch (name) {
                case "CardStudy" -> {
                    var available = LegacyResearchTableData.availableCategories(
                            knowledge, catalog, categories, Set.copyOf(table.blockedCategories()));
                    String selected = LegacyAdditionalTheoryCards.initializeStudy(seed, available);
                    if (selected == null) return null;
                    card.putString("cat", selected);
                }
                case "CardAnalyze" -> {
                    String selected = LegacyCardAnalyze.initialize(seed, knowledge, categories);
                    if (selected == null) return null;
                    card.putString("cat", selected);
                }
                case "CardReject" -> {
                    String category = LegacyTheorycraftMiscCards.initializeReject(table, seed);
                    if (category == null) return null;
                    card.putString("cat", category);
                }
                case "CardInspired" -> {
                    var inspired = LegacyTheorycraftMiscCards.initializeInspired(table);
                    if (inspired == null) return null;
                    card.putString("cat", inspired.category());
                    card.putInt("amt", inspired.amount());
                }
                case "CardNotation" -> {
                    var note = LegacyAdditionalTheoryCards.initializeNotation(table);
                    if (note == null) return null;
                    card.putString("cat1", note.weakest());
                    card.putString("cat2", note.strongest());
                }
                case "CardCurio" -> {
                    var initialized = NeoForgeTheorycraftCurioInitializer.initialize(player, seed);
                    if (initialized == null) return null;
                    card = initialized.cardNBT();
                }
                case "CardCelestial" -> {
                    var note = LegacyCelestialTheoryCard.initialize(
                            knowledge.isResearchComplete("CELESTIALSCANNING", catalog),
                            table.categoryTotals(), seed);
                    if (note == null) return null;
                    card.putInt("md1", note.firstMetadata());
                    card.putInt("md2", note.secondMetadata());
                    card.putString("cat", note.category());
                }
                case "CardConcentrate", "CardChannel" -> {
                    var aspect = LegacyAlchemyTheoryCards.selectCompound(ASPECTS, seed);
                    if (aspect == null) return null;
                    card.putString("aspect", aspect.tag());
                }
                case "CardReactions" -> {
                    var reaction = LegacyAlchemyTheoryCards.initializeReactions(ASPECTS, seed);
                    if (reaction == null) return null;
                    card.putString("aspect1", reaction.first().tag());
                    card.putString("aspect2", reaction.second().tag());
                }
                case "CardSynthesis" -> {
                    var synthesis = LegacyAlchemyTheoryCards.initializeSynthesis(ASPECTS, seed);
                    if (synthesis == null) return null;
                    card.putString("aspect1", synthesis.component1().tag());
                    card.putString("aspect2", synthesis.component2().tag());
                    card.putString("aspect3", synthesis.result().tag());
                }
                case "CardMindOverMatter", "CardTinker" -> {
                    var selected = LegacyTheorycraftOptionPools.artifice(seed);
                    var original = NeoForgeTheorycraftLegacyOptionNbt.serialize(selected);
                    if (original == null) return null;
                    card.put("stack", original);
                }
                case "CardInfuse" -> {
                    var selected = LegacyTheorycraftOptionPools.infusion(ASPECTS, seed);
                    if (selected == null) return null;
                    var original = NeoForgeTheorycraftLegacyOptionNbt.serialize(
                            selected.requiredItem());
                    if (original == null) return null;
                    card.putString("aspect", selected.compoundAspect().tag());
                    card.put("stack", original);
                }
                default -> throw new AssertionError(
                        "registered BETA26 subclass not handled: " + name);
            }
        }
        if (!LegacyTheorycraftCardTagValidator.structurallyValid(key, card))
            return null;
        // This flag is identity from the draw engine, not a license to skip
        // the original aidOnly / available-category / cost filtering.
        return new LegacyTheorycraftChoiceNbt.Choice(
                key, fromAid, false, card, new CompoundTag());
    }

    /**
     * Metadata for original ResearchTableData.drawCards candidate filtering.
     * A null category means the original getResearchCategory() returned null;
     * it is intentionally NOT replaced by BASICS or an unlocked category.
     */
    public static LegacyTheorycraftCardMetadata.Metadata metadata(String key) {
        return LegacyTheorycraftCardMetadata.get(key);
    }

    public static String initializedCategory(LegacyTheorycraftChoiceNbt.Choice choice) {
        Objects.requireNonNull(choice);
        var meta = metadata(choice.key());
        return meta.categoryAfterInitialization(
                meta.dynamicCategory() ? choice.cardNBT().getString("cat") : null);
    }
}
