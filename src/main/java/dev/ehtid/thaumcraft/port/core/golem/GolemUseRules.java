package dev.ehtid.thaumcraft.port.core.golem;

import java.util.Set;

/** Confirmed BETA26 SealUse/InteractionHelper gates and source toggle indices4..8. */
public final class GolemUseRules {
    private GolemUseRules() {}
    public static final int SCAN_TICKS=5;
    public record Settings(boolean left,boolean empty,boolean emptyHand,boolean sneak,boolean provision) {
        public static Settings defaults() { return new Settings(false,false,false,false,false); }
        public boolean toggle(int index) { return switch(index) { case 4->left; case 5->empty; case 6->emptyHand; case 7->sneak; case 8->provision; default->throw new IllegalArgumentException("Use toggle4..8"); }; }
        public Settings withToggle(int index,boolean enabled) {
            return switch(index) {
                case 4->new Settings(enabled,empty,emptyHand,sneak,provision);
                case 5->new Settings(left,enabled,emptyHand,sneak,provision);
                case 6->new Settings(left,empty,enabled,sneak,provision);
                case 7->new Settings(left,empty,emptyHand,enabled,provision);
                case 8->new Settings(left,empty,emptyHand,sneak,enabled);
                default->throw new IllegalArgumentException("Use toggle4..8");
            };
        }
    }
    /** Source completion reads MAIN directly when ghost0 is empty; eligibility still checks both carried slots. */
    public static int completionHand(boolean ghostEmpty,boolean mainMatches,boolean offMatches) {
        return ghostEmpty?0:mainMatches?0:offMatches?1:-1;
    }
    public static boolean eligible(Set<GolemTrait> traits) { return traits.contains(GolemTrait.SMART)&&traits.contains(GolemTrait.DEFT); }
    public static boolean target(Settings settings,boolean air) { return settings.empty==air; }
    public static boolean carrying(Settings settings,boolean matching) { return settings.emptyHand||matching; }
    public static boolean request(Settings settings,boolean matching,boolean blacklist,boolean ghostEmpty) {
        return !settings.emptyHand&&!matching&&settings.provision&&!blacklist&&!ghostEmpty;
    }
}
