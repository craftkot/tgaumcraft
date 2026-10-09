package dev.ehtid.thaumcraft.scanning;

import dev.ehtid.thaumcraft.port.core.LegacyResearchTableData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * BETA26 ResearchTableData scalar and categoryTotals NBT compatibility.
 *
 * The original savedCards, aidCards and categoriesBlocked lists have their
 * exact legacy NBT shape. CardChoice and lastDraw contain subclass-specific
 * cardNBT; these are passed through as untouched raw compounds until every
 * card serializer is ported. No surviving choices may be silently dropped.
 */
public final class LegacyResearchTableNbt {
    private LegacyResearchTableNbt() {}

    public static CompoundTag write(LegacyResearchTableData data) {
        return write(data, new CompoundTag());
    }

    /**
     * Preserve unknown original CardChoice.cardNBT and lastDraw compounds on
     * every save/load. BETA26's subclasses persist additional, distinct fields.
     */
    public static CompoundTag write(LegacyResearchTableData data,
                                    CompoundTag originalSession) {
        LegacyResearchTableData.Snapshot snapshot = data.snapshot();
        CompoundTag tag = originalSession == null ? new CompoundTag() : originalSession.copy();
        tag.putString("player", snapshot.player());
        tag.putInt("inspiration", snapshot.inspiration());
        tag.putInt("inspirationStart", snapshot.inspirationStart());
        tag.putInt("placedCards", snapshot.placedCards());
        tag.putInt("bonusDraws", snapshot.bonusDraws());
        tag.putInt("aidsChosen", snapshot.aidsChosen());
        tag.putInt("penaltyStart", snapshot.penaltyStart());

        ListTag categories = new ListTag();
        for (var entry : snapshot.categoryTotals().entrySet()) {
            CompoundTag row = new CompoundTag();
            row.putString("category", entry.getKey());
            row.putInt("total", entry.getValue());
            categories.add(row);
        }
        tag.put("categoryTotals", categories);

        ListTag savedCards = new ListTag();
        for (long cardSeed : snapshot.savedCards()) {
            CompoundTag row = new CompoundTag();
            row.putLong("card", cardSeed);
            savedCards.add(row);
        }
        tag.put("savedCards", savedCards);

        ListTag blocked = new ListTag();
        for (String key : snapshot.categoriesBlocked()) {
            CompoundTag row = new CompoundTag();
            row.putString("category", key);
            blocked.add(row);
        }
        tag.put("categoriesBlocked", blocked);

        ListTag aidCards = new ListTag();
        for (String key : snapshot.aidCards()) {
            CompoundTag row = new CompoundTag();
            row.putString("aidCard", key);
            aidCards.add(row);
        }
        tag.put("aidCards", aidCards);
        // cardChoices and lastDraw are IN originalSession and are left intact.
        return tag;
    }

    public static LegacyResearchTableData read(CompoundTag tag) {
        ListTag categories = tag.getList("categoryTotals", Tag.TAG_COMPOUND);
        java.util.Map<String, Integer> totals = new java.util.TreeMap<>();
        for (int index = 0; index < categories.size(); index++) {
            CompoundTag row = categories.getCompound(index);
            totals.put(row.getString("category"), row.getInt("total"));
        }
        java.util.List<Long> saved = new java.util.ArrayList<>();
        ListTag savedCards = tag.getList("savedCards", Tag.TAG_COMPOUND);
        for (int i = 0; i < savedCards.size(); i++) {
            saved.add(savedCards.getCompound(i).getLong("card"));
        }

        java.util.List<String> aidCards = new java.util.ArrayList<>();
        ListTag aids = tag.getList("aidCards", Tag.TAG_COMPOUND);
        for (int i = 0; i < aids.size(); i++) {
            aidCards.add(aids.getCompound(i).getString("aidCard"));
        }

        java.util.List<String> blocked = new java.util.ArrayList<>();
        ListTag blockedRows = tag.getList("categoriesBlocked", Tag.TAG_COMPOUND);
        for (int i = 0; i < blockedRows.size(); i++) {
            blocked.add(blockedRows.getCompound(i).getString("category"));
        }

        return LegacyResearchTableData.fromSnapshot(
                new LegacyResearchTableData.Snapshot(
                        tag.getInt("inspiration"), tag.getInt("inspirationStart"),
                        tag.getInt("placedCards"), tag.getInt("bonusDraws"),
                        tag.getInt("aidsChosen"), tag.getInt("penaltyStart"), totals,
                        tag.getString("player"), saved, aidCards, blocked));
    }
}
