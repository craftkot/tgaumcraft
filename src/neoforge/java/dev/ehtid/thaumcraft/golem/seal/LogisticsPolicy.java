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
    private final boolean single,cycle,existingOnly,silk;
    public LogisticsPolicy(List<SealFilter> slots,boolean single) { this(slots,single,false,false,false); }
    private LogisticsPolicy(List<SealFilter> slots, boolean single,boolean cycle,boolean existingOnly,boolean silk) {
        if (slots.size()!=9) throw new IllegalArgumentException("Nine logistics slots required");
        this.slots=List.copyOf(slots); this.single=single; this.cycle=cycle; this.existingOnly=existingOnly; this.silk=silk;
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
    public dev.ehtid.thaumcraft.port.core.golem.SealFilterRules.Options options() { return slot(0).options(); }
    public LogisticsPolicy withOptions(dev.ehtid.thaumcraft.port.core.golem.SealFilterRules.Options value) {
        return new LogisticsPolicy(slots.stream().map(s->s.withOptions(value)).toList(),single,cycle,existingOnly,silk);
    }
    public boolean single() { return single; }
    public boolean silk() { return silk; }
    public LogisticsPolicy withSilk(boolean value) { return new LogisticsPolicy(slots,single,cycle,existingOnly,value); }
    public boolean existingOnly() { return existingOnly; }
    public LogisticsPolicy withExistingOnly(boolean value) { return new LogisticsPolicy(slots,single,cycle,value,silk); }
    /** Source first matching ghost controls Fill size; zero remains unlimited, unlike Stock. */
    public SealFilter fillFilter(ItemStack actual) {
        if(!accepts(actual)) return null;
        if(blacklist()) return new SealFilter(actual,false,0,options());
        for(var slot:slots) if(slot.matches(slot.item(),actual)) return slot;
        return null;
    }
    public boolean cycle() { return cycle; }
    public LogisticsPolicy withCycle(boolean enabled) { return new LogisticsPolicy(slots,single,enabled,existingOnly,silk); }
    public boolean extractionAccepts(ItemStack stack,int completed) {
        if(!cycle || blacklist()) return accepts(stack);
        var populated=slots.stream().filter(s->!s.item().isEmpty()).toList();
        if(populated.isEmpty()) return false;
        var selected=populated.get(dev.ehtid.thaumcraft.port.core.golem.SealEmptyRules.cycleIndex(completed,populated.size()));
        return selected.matches(selected.item(),stack);
    }
    public LogisticsPolicy withSlot(int i, SealFilter next) {
        var copy=new ArrayList<>(slots); copy.set(i,next.withBlacklist(blacklist()).withOptions(options()));
        return new LogisticsPolicy(copy,single,cycle,existingOnly,silk);
    }
    public LogisticsPolicy withMode(boolean blacklist, boolean nbt, boolean single) {
        return new LogisticsPolicy(slots.stream().map(s->s.withBlacklist(blacklist).withOptions(options().withNbt(nbt))).toList(),single,cycle,existingOnly,silk);
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
        row.put("logistics",filters); row.putBoolean("psilk",silk); row.putBoolean("psing",single); row.putBoolean("pcycle",cycle); row.putBoolean("pexist",existingOnly);
        row.putBoolean("pmeta",options().metadata()); row.putBoolean("pnbt",options().nbt()); row.putBoolean("pore",options().ore()); row.putBoolean("pmod",options().mod());
    }
    public static LogisticsPolicy load(CompoundTag row, HolderLookup.Provider registries, boolean stock) {
        var defaults=defaults(stock); var filters=new ArrayList<>(defaults.slots);
        var list=row.getList("logistics",Tag.TAG_COMPOUND);
        for(int i=0;i<list.size();i++) {
            var entry=list.getCompound(i); int index=entry.getInt("index");
            if(index>=0&&index<9) filters.set(index,SealFilter.load(entry,registries));
        }
        var first=filters.getFirst();
        return new LogisticsPolicy(filters,row.getBoolean("psing"),!stock&&row.getBoolean("pcycle"),!stock&&row.getBoolean("pexist"),row.getBoolean("psilk"))
                .withMode(!stock&&first.blacklist(),first.matchNbt(),!stock&&row.getBoolean("psing"));
    }
}
