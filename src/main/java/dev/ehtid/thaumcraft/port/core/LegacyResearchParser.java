package dev.ehtid.thaumcraft.port.core;

import java.util.ArrayList;
import java.util.List;

/** Pure parser for the string encodings used by BETA26 research JSON. */
public final class LegacyResearchParser {
    private LegacyResearchParser() {}

    /**
     * Mirrors ResearchManager.parseJSONtoItemStack up to the point where 1.12 resolves Item and SNBT.
     * Invalid integer tokens are ignored; first nonnegative int is count, second is metadata.
     */
    public static LegacyItemStackSpec parseItemStack(String input) {
        if (input == null) return null;
        String[] split = input.split(";");
        if (split.length == 0 || split[0].isEmpty()) return null;
        String item = split[0];
        int count = -1;
        int meta = -1;
        String snbt = null;
        for (int i = 1; i < split.length; i++) {
            String part = split[i];
            if (part.startsWith("{")) {
                snbt = part;
                break; // exact BETA26 behavior
            }
            try {
                int value = Integer.parseInt(part);
                if (value >= 0 && count < 0) count = value;
                else if (value >= 0 && meta < 0) meta = value;
            } catch (NumberFormatException ignored) {
                // BETA26 ignores malformed numeric fields.
            }
        }
        if (count < 0) count = 1;
        if (meta < 0) meta = 0;
        return new LegacyItemStackSpec(item, count, meta, snbt);
    }

    /** Mirrors parseJsonOreList's quote replacement and oredict prefix handling. */
    public static List<LegacyResearchRequirement> parseRequirementList(List<String> entries) {
        if (entries == null || entries.isEmpty()) return List.of();
        ArrayList<LegacyResearchRequirement> out = new ArrayList<>();
        for (String raw : entries) {
            if (raw == null) continue;
            String value = raw.replace("'", "\"");
            if (value.startsWith("oredict:")) {
                String[] parts = value.split(":");
                if (parts.length > 1) out.add(new LegacyResearchRequirement.OreDictionary(parts[1]));
            } else {
                LegacyItemStackSpec stack = parseItemStack(value);
                if (stack != null) out.add(new LegacyResearchRequirement.Item(stack));
            }
        }
        return List.copyOf(out);
    }

    /** Mirrors ResearchStage.Knowledge.parse for the two TC6 knowledge types. */
    public static KnowledgeReward parseKnowledge(String encoded, java.util.Set<String> knownCategories) {
        if (encoded == null) return null;
        String[] split = encoded.split(";");
        if (split.length == 2) {
            // TC6 only accepts this form for knowledge types with hasFields=false. Both BETA26
            // types have hasFields=true, so no real BETA26 THEORY/OBSERVATION string is valid here.
            return null;
        }
        if (split.length == 3) {
            int amount;
            try { amount = Integer.parseInt(split[2]); } catch (Exception ignored) { amount = 0; }
            // Enum.valueOf is intentionally allowed to throw, matching BETA26's Knowledge.parse.
            KnowledgeType type = KnowledgeType.valueOf(split[0].toUpperCase());
            String category = split[1].toUpperCase();
            if (!knownCategories.contains(category) || amount <= 0) return null;
            return new KnowledgeReward(type, category, amount);
        }
        return null;
    }
}
