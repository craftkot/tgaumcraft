package dev.ehtid.thaumcraft.scanning;

import dev.ehtid.thaumcraft.port.core.AspectRegistry;
import dev.ehtid.thaumcraft.port.core.LegacyTheorycraftOptionPools;
import dev.ehtid.thaumcraft.port.core.LegacyTheorycraftSeedDataIntegrity;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/**
 * BETA26 CardInfuse, CardTinker and CardMindOverMatter derive their required
 * ItemStack from a Java Random initialized with the persisted card seed.
 * A syntactically valid legacy NBT tag is NOT sufficient to authorize
 * inventory payment or theory rewards: cross-check the seeded item choice
 * before inspecting items or consuming XP, paper, or ink.
 *
 * Unknown/unported source options remain unactivatable; NEVER reroll.
 */
public final class NeoForgeTheorycraftSeedIntegrity {
    private static final AspectRegistry ASPECTS = new AspectRegistry();

    private NeoForgeTheorycraftSeedIntegrity() {}

    public static boolean matches(LegacyTheorycraftChoiceNbt.Choice choice) {
        if (choice == null || !LegacyTheorycraftCardTagValidator
                .structurallyValid(choice.key(), choice.cardNBT()))
            return false;
        String key = choice.key();
        CompoundTag data = choice.cardNBT();
        long seed = data.getLong("seed");
        if (key.endsWith(".CardCelestial")) {
            // Only the notes are seed-derived. Category comes from historic
            // categoryTotals and is deliberately not recomputed here.
            return LegacyTheorycraftSeedDataIntegrity.matchesCelestialNotes(
                    seed, data.getInt("md1"), data.getInt("md2"));
        }
        if (key.endsWith(".CardConcentrate") || key.endsWith(".CardChannel")
                || key.endsWith(".CardReactions") || key.endsWith(".CardSynthesis")) {
            return LegacyTheorycraftSeedDataIntegrity.matchesAlchemy(
                    key.substring(key.lastIndexOf('.') + 1), seed,
                    Map.of("aspect", data.getString("aspect"),
                           "aspect1", data.getString("aspect1"),
                           "aspect2", data.getString("aspect2"),
                           "aspect3", data.getString("aspect3")), ASPECTS);
        }
        if (!(key.endsWith(".CardMindOverMatter")
                || key.endsWith(".CardTinker") || key.endsWith(".CardInfuse")))
            return true;
        if (!data.contains("seed", Tag.TAG_LONG)
                || !data.contains("stack", Tag.TAG_COMPOUND))
            return false;
        LegacyTheorycraftOptionPools.Option option;
        if (key.endsWith(".CardInfuse")) {
            if (!data.contains("aspect", Tag.TAG_STRING)) return false;
            var chosen = LegacyTheorycraftOptionPools.infusion(ASPECTS, seed);
            if (chosen == null || !data.getString("aspect")
                    .equals(chosen.compoundAspect().tag()))
                return false;
            option = chosen.requiredItem();
        } else {
            option = LegacyTheorycraftOptionPools.artifice(seed);
        }
        CompoundTag expected = NeoForgeTheorycraftLegacyOptionNbt.serialize(option);
        return expected != null && expected.equals(data.getCompound("stack"));
    }
}
