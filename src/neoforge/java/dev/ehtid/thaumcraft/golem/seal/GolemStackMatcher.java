package dev.ehtid.thaumcraft.golem.seal;

import dev.ehtid.thaumcraft.item.PlateItem;
import dev.ehtid.thaumcraft.port.core.golem.*;
import net.minecraft.core.component.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Source InventoryUtils ordering, separate legacy metadata and actual modern component patches. */
public final class GolemStackMatcher {
    private GolemStackMatcher() {}
    private record OreTag(String name,TagKey<Item> tag) {}
    private record OreStack(String name,GolemLegacyItem identity,DataComponentPatch tags) {}
    private static final Map<UUID,OreTag> EXTRA_TAGS=new ConcurrentHashMap<>();
    private static final Map<UUID,OreStack> EXTRA_STACKS=new ConcurrentHashMap<>();
    private static final Map<String,String> COMMON=GolemOreGroups.commonTags();

    /** Explicit compatibility registration; no arbitrary shared item tag is an ore group. */
    public static Runnable registerOreTag(String name,TagKey<Item> tag) {
        if(name.isBlank()) throw new IllegalArgumentException("Ore name required");
        var id=UUID.randomUUID(); var binding=new OreTag(name,Objects.requireNonNull(tag)); EXTRA_TAGS.put(id,binding);
        return ()->EXTRA_TAGS.remove(id,binding);
    }
    /** Stack registration retains original wildcard/metadata and canonical NBT, with defensive snapshots. */
    public static Runnable registerOre(String name,ItemStack entry) {
        if(name.isBlank() || entry.isEmpty()) throw new IllegalArgumentException("Nonempty ore registration required");
        var id=UUID.randomUUID(); var binding=new OreStack(name,identity(entry),tags(entry)); EXTRA_STACKS.put(id,binding);
        return ()->EXTRA_STACKS.remove(id,binding);
    }
    public static GolemLegacyItem identity(ItemStack stack) {
        var id=BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return id.equals("thaumcraft:plate")?new GolemLegacyItem(id,PlateItem.legacyMetadata(stack))
                :GolemLegacyItem.modern(id,stack.isDamageableItem(),stack.getDamageValue());
    }
    public static DataComponentPatch tags(ItemStack stack) {
        var copy=stack.copy();
        boolean plate=BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals("thaumcraft:plate");
        if(plate) {
            var custom=copy.get(DataComponents.CUSTOM_DATA);
            if(custom!=null) { var tag=custom.copyTag(); tag.remove("LegacyPlateMeta"); if(tag.isEmpty()) copy.remove(DataComponents.CUSTOM_DATA); else copy.set(DataComponents.CUSTOM_DATA,CustomData.of(tag)); }
        }
        var model=copy.get(DataComponents.CUSTOM_MODEL_DATA);
        boolean synthesizedModel=plate && model!=null && model.value()==PlateItem.legacyMetadata(stack);
        return copy.getComponentsPatch().forget(type->type==DataComponents.DAMAGE || synthesizedModel&&type==DataComponents.CUSTOM_MODEL_DATA);
    }
    private static boolean entryMatches(GolemLegacyItem entry,GolemLegacyItem actual) {
        return entry.id().equals(actual.id()) && (entry.metadata()==GolemLegacyItem.WILDCARD || entry.metadata()==actual.metadata());
    }
    private static Set<String> groups(ItemStack stack,GolemLegacyItem identity) {
        var result=new HashSet<>(GolemOreGroups.groups(identity));
        // Registry tags cannot distinguish the four metadata variants of the shared plate item.
        if(!identity.id().equals("thaumcraft:plate")) stack.getTags().forEach(tag->{ var name=COMMON.get(tag.location().toString()); if(name!=null) result.add(name); });
        for(var binding:EXTRA_TAGS.values()) if(stack.is(binding.tag)) result.add(binding.name);
        for(var binding:EXTRA_STACKS.values()) if(entryMatches(binding.identity,identity)) result.add(binding.name);
        return result;
    }
    private static boolean oreMatches(ItemStack left,ItemStack right,GolemLegacyItem a,GolemLegacyItem b) {
        var names=groups(left,a); if(names.isEmpty()) return false;
        var rightTags=tags(right);
        // Source containsMatch compares the RIGHT stack to a canonical registered stack's NBT.
        if(rightTags.isEmpty()) {
            var base=GolemOreGroups.groups(b); if(base.stream().anyMatch(names::contains)) return true;
            if(!b.id().equals("thaumcraft:plate") && right.getTags().anyMatch(tag->names.contains(COMMON.get(tag.location().toString())))) return true;
            for(var binding:EXTRA_TAGS.values()) if(names.contains(binding.name) && right.is(binding.tag)) return true;
        }
        for(var binding:EXTRA_STACKS.values()) if(names.contains(binding.name) && entryMatches(binding.identity,b) && binding.tags.equals(rightTags)) return true;
        return false;
    }
    public static boolean matches(ItemStack left,ItemStack right,SealFilterRules.Options options) {
        if(left.isEmpty() || right.isEmpty()) return false;
        var leftId=BuiltInRegistries.ITEM.getKey(left.getItem()); var rightId=BuiltInRegistries.ITEM.getKey(right.getItem());
        if(options.mod()) return leftId.getNamespace().equals(rightId.getNamespace());
        var a=identity(left); var b=identity(right);
        return SealFilterRules.matches(new SealFilterRules.Comparison(a.id().equals(b.id()),a.metadata()==b.metadata(),
                a.metadata()==GolemLegacyItem.WILDCARD || b.metadata()==GolemLegacyItem.WILDCARD,
                tags(left).equals(tags(right)),leftId.getNamespace().equals(rightId.getNamespace()),options.ore()&&oreMatches(left,right,a,b)),options);
    }
}
