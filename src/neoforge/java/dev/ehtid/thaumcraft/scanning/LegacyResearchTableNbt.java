package dev.ehtid.thaumcraft.scanning;

import dev.ehtid.thaumcraft.port.core.LegacyResearchTableData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * BETA26 ResearchTableData scalar and categoryTotals NBT compatibility.
 *
 * This covers the state used by the first ported theorycraft cards. The
 * original savedCards, aidCards and CardChoice object graphs will be handled
 * by the full ResearchTableBlockEntity implementation, not discarded by it.
 */
public final class LegacyResearchTableNbt {
    private LegacyResearchTableNbt() {}

    public static CompoundTag write(LegacyResearchTableData data) {
        LegacyResearchTableData.Snapshot snapshot = data.snapshot();
        CompoundTag tag = new CompoundTag();
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
        return tag;
    }

    public static LegacyResearchTableData read(CompoundTag tag) {
        ListTag categories = tag.getList("categoryTotals", Tag.TAG_COMPOUND);
        java.util.Map<String, Integer> totals = new java.util.TreeMap<>();
        for (int index = 0; index < categories.size(); index++) {
            CompoundTag row = categories.getCompound(index);
            totals.put(row.getString("category"), row.getInt("total"));
        }
        return LegacyResearchTableData.fromSnapshot(
                new LegacyResearchTableData.Snapshot(
                        tag.getInt("inspiration"), tag.getInt("inspirationStart"),
                        tag.getInt("placedCards"), tag.getInt("bonusDraws"),
                        tag.getInt("aidsChosen"), tag.getInt("penaltyStart"), totals));
    }
}
