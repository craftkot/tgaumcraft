package dev.ehtid.thaumcraft.port.core;

import java.util.Map;
import java.util.Objects;

/**
 * ORIGINAL obfuscated 1.12.2 Blocks/Items field mappings to their unchanged
 * corresponding Minecraft 1.21.1 item registry IDs. Resolved from the 1.12
 * Paper2Srg net.minecraft.init.Blocks / Items field initialization (NOT
 * guessed from card names):
 * https://github.com/Akarin-project/Paper2Srg/tree/master/src/main/java/net/minecraft/init
 *
 * In CardInfuse the old wool block ItemStack has metadata 0 by default;
 * its 1.21 equivalent is white_wool, NOT removed "minecraft:wool".
 *
 * Thaumcraft mod items and the yellow Nitor colored block are deliberately
 * absent. Their items must exist in the NeoForge registry before mapping.
 */
public final class LegacyTheorycraftVanillaOptionIds {
    private LegacyTheorycraftVanillaOptionIds() {}

    private static final Map<String, String> IDS = Map.ofEntries(
            Map.entry("Blocks.field_150467_bQ", "minecraft:anvil"),
            Map.entry("Blocks.field_150408_cc", "minecraft:activator_rail"),
            Map.entry("Blocks.field_150367_z", "minecraft:dispenser"),
            Map.entry("Blocks.field_150409_cd", "minecraft:dropper"),
            Map.entry("Blocks.field_150381_bn", "minecraft:enchanting_table"),
            Map.entry("Blocks.field_150477_bB", "minecraft:ender_chest"),
            Map.entry("Blocks.field_150421_aI", "minecraft:jukebox"),
            Map.entry("Blocks.field_150453_bW", "minecraft:daylight_detector"),
            Map.entry("Blocks.field_150331_J", "minecraft:piston"),
            Map.entry("Blocks.field_150438_bZ", "minecraft:hopper"),
            Map.entry("Blocks.field_150320_F", "minecraft:sticky_piston"),
            Map.entry("Blocks.field_150325_L", "minecraft:white_wool"),
            Map.entry("Items.field_151148_bJ", "minecraft:map"),
            Map.entry("Items.field_151111_aL", "minecraft:compass"),
            Map.entry("Items.field_151142_bV", "minecraft:tnt_minecart"),
            Map.entry("Items.field_151132_bS", "minecraft:comparator"),
            Map.entry("Items.field_151113_aN", "minecraft:clock"),
            Map.entry("Items.field_151043_k", "minecraft:gold_ingot"),
            Map.entry("Items.field_151042_j", "minecraft:iron_ingot"),
            Map.entry("Items.field_151045_i", "minecraft:diamond"),
            Map.entry("Items.field_151166_bC", "minecraft:emerald"),
            Map.entry("Items.field_151072_bj", "minecraft:blaze_rod"),
            Map.entry("Items.field_151116_aA", "minecraft:leather"),
            Map.entry("Items.field_151118_aC", "minecraft:brick"),
            Map.entry("Items.field_151032_g", "minecraft:arrow"),
            Map.entry("Items.field_151110_aK", "minecraft:egg"),
            Map.entry("Items.field_151008_G", "minecraft:feather"),
            Map.entry("Items.field_151114_aO", "minecraft:glowstone_dust"),
            Map.entry("Items.field_151137_ax", "minecraft:redstone"),
            Map.entry("Items.field_151073_bk", "minecraft:ghast_tear"),
            Map.entry("Items.field_151016_H", "minecraft:gunpowder"),
            Map.entry("Items.field_151031_f", "minecraft:bow"),
            Map.entry("Items.field_151010_B", "minecraft:golden_sword"),
            Map.entry("Items.field_151040_l", "minecraft:iron_sword"),
            Map.entry("Items.field_151035_b", "minecraft:iron_pickaxe"),
            Map.entry("Items.field_151005_D", "minecraft:golden_pickaxe"),
            Map.entry("Items.field_151128_bU", "minecraft:quartz"),
            Map.entry("Items.field_151034_e", "minecraft:apple")
    );

    public static String modernId(LegacyTheorycraftOptionPools.Option option) {
        Objects.requireNonNull(option);
        if (option.kind() != LegacyTheorycraftOptionPools.SourceKind.VANILLA_BLOCK
                && option.kind() != LegacyTheorycraftOptionPools.SourceKind.VANILLA_ITEM)
            return null;
        return IDS.get(option.historicalField());
    }

    public static Map<String, String> all() { return IDS; }
}
