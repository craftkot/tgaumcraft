package dev.ehtid.thaumcraft.port.core.golem;

/** BETA26 TileGolemBuilder processing rules; item payment belongs to the server adapter. */
public final class GolemPress {
    public static final int INTERVAL = 5;
    public static final int SUCTION = 128;
    public static final String ASPECT = "machina";
    private GolemPress() {}

    public static int cost(GolemDesign design) {
        return 2 * design.traits().size() + GolemAssembly.components(design).stream()
                .mapToInt(GolemAssembly.Component::count).sum();
    }

    /** Includes a paid, ready-but-blocked job. Never restart an active job. */
    public record State(long props, int remaining, int maximum, boolean buffered) {
        public State {
            if (props == -1) {
                if (remaining != 0 || maximum != 0 || buffered) throw new IllegalArgumentException("Invalid idle press");
            } else {
                var design = GolemDesign.fromLong(props);
                // Construction always produces rank0; supported native functions are gated by its adapter.
                if (design.rank()!=0 || props>>>48!=0 || maximum != cost(design)
                        || remaining < 0 || remaining > maximum || buffered && remaining == 0)
                    throw new IllegalArgumentException("Invalid or unsupported press job");
            }
        }
        public static State idle() { return new State(-1, 0, 0, false); }
        public boolean active() { return props != -1; }
        public boolean ready() { return active() && remaining == 0; }
        public boolean accepts(String aspect, int amount) {
            return active() && remaining > 0 && !buffered && amount > 0 && ASPECT.equals(aspect);
        }
        /** Called only after the complete component debit succeeds. */
        public State startPaid(GolemDesign design) {
            if (active()) throw new IllegalStateException("Press already paid");
            int cost = cost(design);
            return new State(design.toLong(), cost, cost, false);
        }
        public State receive(String aspect, int amount) {
            return accepts(aspect, amount) ? new State(props, remaining, maximum, true) : this;
        }
        /** Adapter invokes once per five ticks; a buffered unit has already been debited. */
        public State consumeBuffered() {
            return buffered ? new State(props, remaining - 1, maximum, false) : this;
        }
        /** Called only after exactly one product is committed to the output slot. */
        public State finish() {
            if (!ready()) throw new IllegalStateException("Unfinished press");
            return idle();
        }
    }
}
