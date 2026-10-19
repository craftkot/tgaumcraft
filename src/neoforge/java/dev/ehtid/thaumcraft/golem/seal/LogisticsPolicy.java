package dev.ehtid.thaumcraft.golem.seal;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;

/** Nine defensive ghost slots. Stock is a whitelist with a separate desired total per slot. */
public final class LogisticsPolicy {
    private final List<SealFilter> slots;
    private final boolean single,cycle;
    public LogisticsPolicy(List<SealFilter> slots,boolean single) { this(slots,single,false); }
    private LogisticsPolicy(List<SealFilter> slots, boolean single,boolean cycle) {
        if (slots.size()!=9) throw new IllegalArgumentException("Nine logistics slots required");
        this.slots=List.copyOf(slots); this.single=single; this.cycle=cycle;
    }
    public static LogisticsPolicy defaults(boolean stock) {
        var slots=new ArrayList<SealFilter>();
        for(int i=0;i<9;i++) slots.add(new SealFilter(ItemStack.EMPTY,!stock,0));
        return new LogisticsPolicy(slots,false);
    }
    public List<SealFilter> slots() { return slots; }
    public SealFilter slot(int i) { return slots.get(i); }
    public boolean blacklist() { return slot(0).blacklist(); }
    public boolean matchNbt() { return slot(0).matchNbt(); }
    public boolean single() { return single; }
    public boolean cycle() { return cycle; }
    public LogisticsPolicy withCycle(boolean enabled) { return new LogisticsPolicy(slots,single,enabled); }
    public boolean extractionAccepts(ItemStack stack,int completed) {
        if(!cycle || blacklist()) return accepts(stack);
        var populated=slots.stream().filter(s->!s.item().isEmpty()).toList();
        if(populated.isEmpty()) return false;
        var selected=populated.get(dev.ehtid.thaumcraft.port.core.golem.SealEmptyRules.cycleIndex(completed,populated.size()));
        return selected.matches(selected.item(),stack);
    }
    public LogisticsPolicy withSlot(int i, SealFilter next) {
        var copy=new ArrayList<>(slots); copy.set(i,next.withBlacklist(blacklist()).withMatchNbt(matchNbt()));
        return new LogisticsPolicy(copy,single,cycle);
    }
    public LogisticsPolicy withMode(boolean blacklist, boolean nbt, boolean single) {
        return new LogisticsPolicy(slots.stream().map(s->s.withBlacklist(blacklist).withMatchNbt(nbt)).toList(),single,cycle);
    }
    public boolean accepts(ItemStack stack) {
        if(stack.isEmpty()) return false;
        boolean match=slots.stream().anyMatch(s->s.matches(s.item(),stack));
        return blacklist()?!match:match;
    }
    /** Fresh recipient budget; multiple matching slots share the largest target, never sum duplicated ghosts. */
    public SealFilter recipientFilter(ItemStack actual) {
        return slots.stream().filter(s->s.matches(s.item(),actual) && s.limit()>0)
                .max(java.util.Comparator.comparingInt(SealFilter::limit)).orElse(null);
    }
    public void save(CompoundTag row, HolderLookup.Provider registries) {
        var filters=new ListTag();
        for(int i=0;i<9;i++) { var entry=new CompoundTag(); entry.putInt("index",i); slots.get(i).save(entry,registries); filters.add(entry); }
        row.put("logistics",filters); row.putBoolean("psing",single); row.putBoolean("pcycle",cycle);
    }
    public static LogisticsPolicy load(CompoundTag row, HolderLookup.Provider registries, boolean stock) {
        var defaults=defaults(stock); var filters=new ArrayList<>(defaults.slots);
        var list=row.getList("logistics",Tag.TAG_COMPOUND);
        for(int i=0;i<list.size();i++) {
            var entry=list.getCompound(i); int index=entry.getInt("index");
            if(index>=0&&index<9) filters.set(index,SealFilter.load(entry,registries));
        }
        var first=filters.getFirst();
        return new LogisticsPolicy(filters,row.getBoolean("psing"),!stock&&row.getBoolean("pcycle"))
                .withMode(!stock&&first.blacklist(),first.matchNbt(),!stock&&row.getBoolean("psing"));
    }
}
