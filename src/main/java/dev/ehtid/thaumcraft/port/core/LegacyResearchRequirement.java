package dev.ehtid.thaumcraft.port.core;

/** Result of BETA26 ResearchManager.parseJsonOreList semantics. */
public sealed interface LegacyResearchRequirement permits LegacyResearchRequirement.Item, LegacyResearchRequirement.OreDictionary {
    record Item(LegacyItemStackSpec stack) implements LegacyResearchRequirement {}
    record OreDictionary(String name) implements LegacyResearchRequirement {}
}
