package dev.ehtid.thaumcraft.port.core;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * BETA26 ConfigResearch.initScannables ScanOreDictionary registrations.
 *
 * The original ScanOreDictionary compares the stack's OreDictionary names to
 * each requested name using String.equals. This 1.21 adapter translates ONLY
 * the established ore/ingot/storage-block/plate categories to common item tags.
 * It intentionally does not match raw metals, nuggets, gems or arbitrary
 * strings containing a material name.
 */
public final class LegacyOreDictionaryScans {
    private LegacyOreDictionaryScans() {}

    public static final String IRON = "f_MATIRON";
    public static final String BRASS = "f_MATBRASS";
    public static final String THAUMIUM = "f_MATTHAUMIUM";
    public static final String VOID = "f_MATVOID";

    private static final Map<String, String> TAG_TO_RESEARCH;

    static {
        Map<String, String> tags = new LinkedHashMap<>();
        // Original BETA26: oreIron, ingotIron, blockIron, plateIron.
        tags.put("c:ores/iron", IRON);
        tags.put("c:ingots/iron", IRON);
        tags.put("c:storage_blocks/iron", IRON);
        tags.put("c:plates/iron", IRON);
        // Original BETA26: ingotBrass, blockBrass, plateBrass.
        tags.put("c:ingots/brass", BRASS);
        tags.put("c:storage_blocks/brass", BRASS);
        tags.put("c:plates/brass", BRASS);
        // Original BETA26: ingotThaumium, blockThaumium, plateThaumium.
        tags.put("c:ingots/thaumium", THAUMIUM);
        tags.put("c:storage_blocks/thaumium", THAUMIUM);
        tags.put("c:plates/thaumium", THAUMIUM);
        // Original BETA26: ingotVoid, blockVoid, plateVoid.
        // This mapping is only active for real items advertising these tags.
        tags.put("c:ingots/void", VOID);
        tags.put("c:storage_blocks/void", VOID);
        tags.put("c:plates/void", VOID);
        TAG_TO_RESEARCH = Map.copyOf(tags);
    }

    /** Complete list of common tags the NeoForge bridge must query. */
    public static List<String> modernTagNames() {
        return List.of(
                "c:ores/iron", "c:ingots/iron", "c:storage_blocks/iron", "c:plates/iron",
                "c:ingots/brass", "c:storage_blocks/brass", "c:plates/brass",
                "c:ingots/thaumium", "c:storage_blocks/thaumium", "c:plates/thaumium",
                "c:ingots/void", "c:storage_blocks/void", "c:plates/void"
        );
    }

    /** Return original TC6 scan research keys corresponding to matching item tag ids. */
    public static Set<String> fromCommonTags(Set<String> itemTags) {
        Objects.requireNonNull(itemTags, "itemTags");
        Set<String> keys = new LinkedHashSet<>();
        for (String tag : modernTagNames()) {
            if (itemTags.contains(tag)) keys.add(TAG_TO_RESEARCH.get(tag));
        }
        return Set.copyOf(keys);
    }
}
