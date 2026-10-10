package dev.ehtid.thaumcraft.scanning;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Exact BETA26 ResearchTableData.serializeCardChoice NBT shape.
 * Unknown subclass-specific fields and unknown enclosing row fields survive.
 */
public final class LegacyTheorycraftChoiceNbt {
    private LegacyTheorycraftChoiceNbt() {}

    public record Choice(String key, boolean fromAid, boolean selected,
                         CompoundTag cardNBT, CompoundTag originalRow) {
        public Choice {
            if (key == null || key.isBlank()) throw new IllegalArgumentException("cardChoice");
            Objects.requireNonNull(cardNBT);
            Objects.requireNonNull(originalRow);
            cardNBT = cardNBT.copy();
            originalRow = originalRow.copy();
        }
        @Override public CompoundTag cardNBT() { return cardNBT.copy(); }
        @Override public CompoundTag originalRow() { return originalRow.copy(); }
        public long seed() { return cardNBT.getLong("seed"); }
    }

    public static Optional<Choice> read(CompoundTag row) {
        if (row == null || !row.contains("cardChoice", Tag.TAG_STRING)
                || row.getString("cardChoice").isBlank()) return Optional.empty();
        CompoundTag cardNBT = row.contains("cardNBT", Tag.TAG_COMPOUND)
                ? row.getCompound("cardNBT") : new CompoundTag();
        return Optional.of(new Choice(row.getString("cardChoice"), row.getBoolean("aid"),
                row.getBoolean("select"), cardNBT, row));
    }

    public static CompoundTag write(Choice choice) {
        Objects.requireNonNull(choice);
        CompoundTag row = choice.originalRow();
        row.putString("cardChoice", choice.key());
        row.putBoolean("aid", choice.fromAid());
        row.putBoolean("select", choice.selected());
        if (row.contains("cardNBT", Tag.TAG_COMPOUND) || !choice.cardNBT().isEmpty())
            row.put("cardNBT", choice.cardNBT());
        return row;
    }

    public static List<Choice> readChoices(CompoundTag session) {
        Objects.requireNonNull(session);
        ListTag rows = session.getList("cardChoices", Tag.TAG_COMPOUND);
        ArrayList<Choice> result = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++)
            read(rows.getCompound(i)).ifPresent(result::add);
        return List.copyOf(result);
    }

    public static void writeChoices(CompoundTag session, List<Choice> choices) {
        Objects.requireNonNull(session);
        Objects.requireNonNull(choices);
        ListTag rows = new ListTag();
        for (Choice choice : choices) rows.add(write(choice));
        session.put("cardChoices", rows);
    }

    public static Optional<Choice> readLastDraw(CompoundTag session) {
        Objects.requireNonNull(session);
        if (!session.contains("lastDraw", Tag.TAG_COMPOUND)) return Optional.empty();
        return read(session.getCompound("lastDraw"));
    }

    public static void writeLastDraw(CompoundTag session, Choice choice) {
        Objects.requireNonNull(session);
        if (choice == null) session.remove("lastDraw");
        else session.put("lastDraw", write(choice));
    }
}
