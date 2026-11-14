package dev.ehtid.thaumcraft.port.core.golem;

/** BETA26 SealFiltered / InventoryUtils tuple matching and SealFill stock limit. */
public final class SealFilterRules {
    private SealFilterRules() {}

    public record Options(boolean metadata,boolean nbt,boolean ore,boolean mod) {
        public static Options strict() { return new Options(true,true,false,false); }
        public Options withMetadata(boolean v) { return new Options(v,nbt,ore,mod); }
        public Options withNbt(boolean v) { return new Options(metadata,v,ore,mod); }
        public Options withOre(boolean v) { return new Options(metadata,nbt,v,mod); }
        public Options withMod(boolean v) { return new Options(metadata,nbt,ore,v); }
    }
    /** Nonempty stacks. Ore evidence includes right-entry metadata/NBT, not just shared group names. */
    public record Comparison(boolean sameItem,boolean sameMetadata,boolean wildcard,boolean sameTags,boolean sameMod,boolean oreEntryMatches) {}
    public static boolean matches(Comparison c,Options o) {
        if(o.mod()) return c.sameMod();
        if(o.ore() && c.oreEntryMatches()) return true;
        return c.sameItem() && (!o.metadata() || c.wildcard() || c.sameMetadata()) && (!o.nbt() || c.sameTags());
    }

    /** BETA26 igNBT does not disable the separate igDmg/pmeta comparison. */
    public static boolean matches(boolean sameItem, boolean sameDamage, boolean sameComponents, boolean matchNbt) {
        return sameItem && sameDamage && (!matchNbt || sameComponents);
    }

    public static boolean accepts(boolean blacklist, boolean hasFilter, boolean matches) {
        return blacklist ? !hasFilter || !matches : hasFilter && matches;
    }

    /** Zero is unlimited; a positive whitelist size bounds target stock, not each delivery. */
    public static int transferable(int carried, boolean blacklist, int limit, long existing) {
        if (carried <= 0) return 0;
        if (blacklist || limit <= 0) return carried;
        return (int) Math.min(carried, Math.max(0L, (long) limit - Math.max(0L, existing)));
    }
}
