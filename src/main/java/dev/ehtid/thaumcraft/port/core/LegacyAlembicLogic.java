package dev.ehtid.thaumcraft.port.core;

import java.util.List;
import java.util.Objects;

/**
 * BETA26 TileAlembic storage and TileAlembic.processAlembics source behavior.
 * The original first fills matching NONEMPTY alembics in a contiguous column,
 * then attempts the first compatible EMPTY (or same-aspect) alembic.
 * An unavailable/full column must not consume any furnace essentia.
 */
public final class LegacyAlembicLogic {
    private LegacyAlembicLogic() {}
    public static final int CAPACITY = 128;

    public record State(String aspect, int amount, String filter, int labelSide) {
        public State {
            if (amount < 0 || amount > CAPACITY) throw new IllegalArgumentException("amount");
            if (amount > 0 && (aspect == null || aspect.isBlank()))
                throw new IllegalArgumentException("aspect");
            if (amount == 0) aspect = null;
            if (filter != null && filter.isBlank()) filter = null;
            if (labelSide < 0 || labelSide > 5) throw new IllegalArgumentException("side");
        }
        public static State empty() { return new State(null, 0, null, 0); }
    }

    public record Transfer(State state, int transferred) {
        public Transfer {
            Objects.requireNonNull(state);
            if (transferred < 0) throw new IllegalArgumentException("transferred");
        }
    }
    public record ColumnTransfer(List<State> column, int acceptedAt) {
        public ColumnTransfer { column = List.copyOf(column); }
        public boolean accepted() { return acceptedAt >= 0; }
    }

    /** 1.12 addToContainer returns remaining; this returns accepted units. */
    public static Transfer add(State original, String aspect, int count) {
        Objects.requireNonNull(original);
        if (count <= 0 || aspect == null || aspect.isBlank()
                || (original.filter() != null && !original.filter().equals(aspect))
                || (original.amount() > 0 && !aspect.equals(original.aspect())))
            return new Transfer(original, 0);
        int added = Math.min(count, CAPACITY - original.amount());
        if (added == 0) return new Transfer(original, 0);
        return new Transfer(new State(aspect, original.amount() + added,
                original.filter(), original.labelSide()), added);
    }

    public static Transfer take(State original, String aspect, int count) {
        Objects.requireNonNull(original);
        if (count <= 0 || !Objects.equals(original.aspect(), aspect)
                || original.amount() < count) return new Transfer(original, 0);
        return new Transfer(new State(original.aspect(), original.amount() - count,
                original.filter(), original.labelSide()), count);
    }

    /** Source TileAlembic.isConnectable/canOutputTo: excludes DOWN and label side. */
    public static boolean canOutput(State original, int face) {
        return face >= 0 && face < 6 && face != 0 && face != original.labelSide();
    }

    /** Source comparator: floor(14 * amount/128) + 1 when nonempty. */
    public static int comparator(State state) {
        return state.amount() == 0 ? 0 : 1 + (state.amount() * 14) / CAPACITY;
    }

    /**
     * Exact two-pass BETA26 processAlembics, with contiguous vertical
     * column represented bottom-to-top (does not search through gaps).
     */
    public static ColumnTransfer depositOne(List<State> column, String aspect) {
        Objects.requireNonNull(column);
        if (aspect == null || aspect.isBlank()) return new ColumnTransfer(column, -1);
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < column.size(); i++) {
                State jar = column.get(i);
                if (pass == 0 && !(jar.amount() > 0 && aspect.equals(jar.aspect())))
                    continue;
                if (pass == 1 && jar.filter() != null && !jar.filter().equals(aspect))
                    continue;
                Transfer added = add(jar, aspect, 1);
                if (added.transferred() == 1) {
                    var result = new java.util.ArrayList<>(column);
                    result.set(i, added.state());
                    return new ColumnTransfer(result, i);
                }
            }
        }
        return new ColumnTransfer(column, -1);
    }
}
