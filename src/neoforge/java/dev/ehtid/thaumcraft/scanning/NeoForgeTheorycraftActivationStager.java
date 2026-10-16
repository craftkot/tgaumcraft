package dev.ehtid.thaumcraft.scanning;

import dev.ehtid.thaumcraft.blockentity.ResearchTableBlockEntity;
import dev.ehtid.thaumcraft.item.CurioItem;
import dev.ehtid.thaumcraft.player.ThaumcraftPlayerAttachments;
import dev.ehtid.thaumcraft.port.core.AspectRegistry;
import dev.ehtid.thaumcraft.port.core.BuiltInResearchCategories;
import dev.ehtid.thaumcraft.port.core.LegacyObjectAspectTable;
import dev.ehtid.thaumcraft.port.core.LegacyTheorycraftSourceVis;
import dev.ehtid.thaumcraft.item.ThaumcraftIngotItem;
import dev.ehtid.thaumcraft.registry.ThaumcraftItems;
import dev.ehtid.thaumcraft.port.core.LegacyTheorycraftActivationEffects;
import dev.ehtid.thaumcraft.port.core.LegacyTheorycraftCardFields;
import dev.ehtid.thaumcraft.port.core.LegacyTheorycraftRequiredItems;
import dev.ehtid.thaumcraft.port.core.ResearchCategoryData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Adapter from REAL serialized BETA26 CardChoice.cardNBT to the pure
 * 33-class activation effect staging implementation.
 *
 * Do not call from a passive UI preview: staging consumes the real player's
 * RNG. This method MUST remain unreachable from menu activation until the
 * full atomic table/player inventory, ink/paper and XP commit is installed.
 * It never changes table/knowledge/warp itself, and it refuses to guess
 * an item's visSize from partial or unported aspect tables.
 */
public final class NeoForgeTheorycraftActivationStager {
    private static final AspectRegistry ASPECTS = new AspectRegistry();
    private static final List<String> CATEGORIES =
            BuiltInResearchCategories.create(ASPECTS).values().stream()
                    .map(ResearchCategoryData::key).toList();

    private NeoForgeTheorycraftActivationStager() {}

    public static LegacyTheorycraftActivationEffects.Outcome stageAfterValidatedCosts(
            ServerPlayer player, ResearchTableBlockEntity table, int index,
            NeoForgeTheorycraftActivationPreflight.Check preflight) {
        if (preflight == null || !preflight.canActivate() || table.session() == null
                || index < 0 || index >= table.pendingChoices().size()) return null;
        // A preflight result must not be reusable after inventory/XP/table
        // changes. Always verify the authoritative current state before
        // consuming one byte of the real player's activation RNG.
        var current = NeoForgeTheorycraftActivationPreflight.check(player, table, index);
        if (current == null || !current.canActivate()) return null;
        var choice = table.pendingChoices().get(index);
        if (!choice.equals(preflight.originalCard())
                || !choice.equals(current.originalCard())) return null;
        if (!LegacyTheorycraftCardTagValidator.structurallyValid(
                choice.key(), choice.cardNBT())) return null;

        CompoundTag tag = choice.cardNBT();
        Map<String, String> texts = new HashMap<>();
        Map<String, Integer> numbers = new HashMap<>();
        for (var field : LegacyTheorycraftCardFields.additionalFields(choice.key())) {
            switch (field.type()) {
                case STRING -> texts.put(field.key(), tag.getString(field.key()));
                case INT -> numbers.put(field.key(), tag.getInt(field.key()));
                case LONG, ITEM_STACK -> { /* seed and legacy stack stay in NBT */ }
            }
        }

        int curioVariant = -1;
        if (choice.key().endsWith(".CardCurio")) {
            ItemStack original = NeoForgeLegacyItemStackDecoder.safeDecode(
                    tag.getCompound("stack"));
            curioVariant = CurioItem.legacyMetadata(original);
            if (curioVariant < 0) return null;
        }

        int visSize = -1;
        if (choice.key().endsWith(".CardMindOverMatter")
                || choice.key().endsWith(".CardTinker")
                || choice.key().endsWith(".CardInfuse")) {
            ItemStack original = NeoForgeLegacyItemStackDecoder.safeDecode(
                    tag.getCompound("stack"));
            if (original.isEmpty()) return null;
            String id = BuiltInRegistries.ITEM.getKey(original.getItem()).toString();
            int originalMetadata = original.is(ThaumcraftItems.INGOT.get())
                    ? ThaumcraftIngotItem.legacyMetadata(original) : 0;
            // Exact source OreDictionary tag totals are independent of
            // modern 1.21 item IDs: do NOT conflate brass and thaumium.
            int sourceVis = LegacyTheorycraftSourceVis
                    .explicitlyTaggedVisSize(id, originalMetadata);
            if (sourceVis >= 0) {
                visSize = sourceVis;
            } else {
                var knownAspects = LegacyObjectAspectTable.get(ASPECTS, id);
                if (knownAspects.size() == 0) {
                    // Source getObjectTags also generates unknown aspect
                    // results from recipes. Until that graph is ported,
                    // reject rather than awarding an invented Vis score.
                    return null;
                }
                visSize = knownAspects.visSize();
            }
        }

        // 1.21 RandomSource differs from java.util.Random, but these original
        // card.activate() models only invoke nextInt, nextBoolean, nextFloat.
        var source = player.getRandom();
        Random rng = new Random(0L) {
            @Override public int nextInt(int bound) { return source.nextInt(bound); }
            @Override public boolean nextBoolean() { return source.nextBoolean(); }
            @Override public float nextFloat() { return source.nextFloat(); }
            @Override public long nextLong() { return source.nextLong(); }
        };
        var data = player.getData(ThaumcraftPlayerAttachments.PLAYER_DATA);
        return LegacyTheorycraftActivationEffects.stage(
                new LegacyTheorycraftActivationEffects.Card(
                        choice.key(), texts, numbers, curioVariant),
                table.session(), data.knowledge(), data.warp(),
                CATEGORIES, ASPECTS, rng, player.experienceLevel, visSize);
    }
}
