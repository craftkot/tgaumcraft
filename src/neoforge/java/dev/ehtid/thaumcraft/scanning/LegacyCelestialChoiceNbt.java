package dev.ehtid.thaumcraft.scanning;

import dev.ehtid.thaumcraft.port.core.LegacyCelestialTheoryCard;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.Optional;

/**
 * Original BETA26 ResearchTableData.serializeCardChoice for CardCelestial:
 *
 * cardChoice: "thaumcraft.common.lib.research.theorycraft.CardCelestial"
 * aid: boolean, select: boolean,
 * cardNBT: {seed: long, md1: int, md2: int, cat: String}
 *
 * The generic theorycraft NBT pass-through preserves OTHER original cards
 * without inventing their subclass fields. This codec accepts only Celestial.
 */
public final class LegacyCelestialChoiceNbt {
    private LegacyCelestialChoiceNbt() {}

    public static final String CARD_KEY =
            "thaumcraft.common.lib.research.theorycraft.CardCelestial";

    public record Choice(LegacyCelestialTheoryCard.Selection selection,
                         long seed, boolean fromAid, boolean selected) {}

    public static CompoundTag write(Choice choice) {
        CompoundTag card = new CompoundTag();
        card.putLong("seed", choice.seed());
        card.putInt("md1", choice.selection().firstMetadata());
        card.putInt("md2", choice.selection().secondMetadata());
        card.putString("cat", choice.selection().category());

        CompoundTag row = new CompoundTag();
        row.putString("cardChoice", CARD_KEY);
        row.putBoolean("aid", choice.fromAid());
        row.putBoolean("select", choice.selected());
        row.put("cardNBT", card);
        return row;
    }

    public static Optional<Choice> read(CompoundTag row) {
        if (row == null || !CARD_KEY.equals(row.getString("cardChoice"))
                || !row.contains("cardNBT", Tag.TAG_COMPOUND)) return Optional.empty();
        CompoundTag card = row.getCompound("cardNBT");
        if (!card.contains("seed", Tag.TAG_LONG)
                || !card.contains("md1", Tag.TAG_INT)
                || !card.contains("md2", Tag.TAG_INT)
                || !card.contains("cat", Tag.TAG_STRING)) return Optional.empty();
        try {
            var selection = new LegacyCelestialTheoryCard.Selection(
                    card.getInt("md1"), card.getInt("md2"), card.getString("cat"));
            return Optional.of(new Choice(selection, card.getLong("seed"),
                    row.getBoolean("aid"), row.getBoolean("select")));
        } catch (IllegalArgumentException invalid) {
            return Optional.empty();
        }
    }
}
