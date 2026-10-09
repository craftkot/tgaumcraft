package dev.ehtid.thaumcraft.port.core;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Loader-neutral bridge for BETA26 ConfigAspects.registerItemAspects' OreDictionary
 * registrations. Each aspect amount below comes from the original BETA26 JAR's
 * registerObjectTag(String, AspectList) bytecode, in original registration order.
 *
 * BETA26's ore names are translated into matching 1.21 common item-tag families.
 * Unknown ore names and modern raw materials are deliberately not synthesized.
 * Direct vanilla item overrides should be checked before calling this helper.
 */
public final class LegacyOreAspectTable {
    private LegacyOreAspectTable() {}

    private record Entry(String modernTag, String amounts) {}

    private static final List<Entry> ENTRIES = List.of(
            new Entry("c:ores/lapis", "terra:5,sensus:15"),
            new Entry("c:ores/diamond", "terra:5,desiderium:15,vitreus:15"),
            new Entry("c:gems/diamond", "vitreus:15,desiderium:15"),
            new Entry("c:ores/redstone", "terra:5,potentia:15"),
            new Entry("c:ores/emerald", "terra:5,desiderium:10,vitreus:15"),
            new Entry("c:gems/emerald", "vitreus:15,desiderium:10"),
            new Entry("c:ores/quartz", "terra:5,vitreus:10"),
            new Entry("c:gems/quartz", "vitreus:5"),
            new Entry("c:ores/iron", "terra:5,metallum:15"),
            new Entry("c:dusts/iron", "metallum:15,perditio:1"),
            new Entry("c:ingots/iron", "metallum:15"),
            new Entry("c:ores/gold", "terra:5,metallum:10,desiderium:10"),
            new Entry("c:dusts/gold", "metallum:10,desiderium:10,perditio:1"),
            new Entry("c:ingots/gold", "metallum:10,desiderium:10"),
            new Entry("c:dusts/redstone", "potentia:10"),
            new Entry("c:dusts/glowstone", "sensus:5,lux:10"),
            new Entry("c:ingots/copper", "metallum:10,permutatio:5"),
            new Entry("c:dusts/copper", "metallum:10,perditio:1,permutatio:5"),
            new Entry("c:ores/copper", "metallum:10,terra:5,permutatio:5"),
            new Entry("c:ingots/tin", "metallum:10,vitreus:5"),
            new Entry("c:dusts/tin", "metallum:10,perditio:1,vitreus:5"),
            new Entry("c:ores/tin", "metallum:10,terra:5,vitreus:5"),
            new Entry("c:ingots/silver", "metallum:10,desiderium:5"),
            new Entry("c:dusts/silver", "metallum:10,perditio:1,desiderium:5"),
            new Entry("c:ores/silver", "metallum:10,terra:5,desiderium:5"),
            new Entry("c:ingots/lead", "metallum:10,ordo:5"),
            new Entry("c:dusts/lead", "metallum:10,perditio:1,ordo:5"),
            new Entry("c:ores/lead", "metallum:10,terra:5,ordo:5"),
            new Entry("c:ingots/brass", "metallum:10,instrumentum:5"),
            new Entry("c:dusts/brass", "metallum:10,perditio:1,instrumentum:5"),
            new Entry("c:ingots/bronze", "metallum:10,instrumentum:5"),
            new Entry("c:dusts/bronze", "metallum:10,perditio:1,instrumentum:5"),
            new Entry("c:ores/uranium", "metallum:10,mortuus:5,potentia:10"),
            new Entry("c:ingots/uranium", "metallum:10,mortuus:5,potentia:10"),
            new Entry("c:gems/ruby", "vitreus:10,desiderium:10"),
            new Entry("c:gems/green_sapphire", "vitreus:10,desiderium:10"),
            new Entry("c:gems/sapphire", "vitreus:10,desiderium:10"),
            new Entry("c:ingots/steel", "metallum:15,ordo:5")
    );

    public static List<String> modernTagNames() {
        return ENTRIES.stream().map(Entry::modernTag).toList();
    }

    /**
     * Return the registered AspectList for the first matching legacy ore class.
     * A target having several modern tags must not sum duplicate registrations.
     */
    public static AspectListData fromCommonTags(AspectRegistry aspects, Set<String> itemTags) {
        Objects.requireNonNull(aspects, "aspects");
        Objects.requireNonNull(itemTags, "itemTags");
        for (Entry entry : ENTRIES) {
            if (!itemTags.contains(entry.modernTag())) continue;
            AspectListData out = new AspectListData();
            for (String descriptor : entry.amounts().split(",")) {
                String[] pair = descriptor.split(":", 2);
                AspectData aspect = aspects.get(pair[0]);
                if (aspect == null) throw new IllegalStateException("unknown BETA26 aspect " + pair[0]);
                out.add(aspect, Integer.parseInt(pair[1]));
            }
            return out;
        }
        return new AspectListData();
    }
}
