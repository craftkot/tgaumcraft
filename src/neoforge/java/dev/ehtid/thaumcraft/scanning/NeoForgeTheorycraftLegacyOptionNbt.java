package dev.ehtid.thaumcraft.scanning;

import dev.ehtid.thaumcraft.port.core.LegacyTheorycraftOptionPools;
import dev.ehtid.thaumcraft.port.core.LegacyTheorycraftVanillaOptionIds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * Writes the exact simple BETA26 ItemStack.serializeNBT() shape for a seeded
 * card option AFTER confirming that its equivalent has been ported.
 *
 * Original CardMindOverMatter, CardTinker, CardInfuse choose their option
 * BEFORE serialization. Any missing original mod item is a failed init,
 * not permission to roll a different item or replace it by a random one.
 */
public final class NeoForgeTheorycraftLegacyOptionNbt {
    private NeoForgeTheorycraftLegacyOptionNbt() {}

    public static CompoundTag serialize(LegacyTheorycraftOptionPools.Option option) {
        if (option == null || !safeMetadata(option)) return null;
        ItemStack live = NeoForgeTheorycraftOriginalOptionResolver.resolve(option);
        if (live.isEmpty()) return null;

        String legacyId = switch (option.kind()) {
            case VANILLA_BLOCK, VANILLA_ITEM ->
                    LegacyTheorycraftVanillaOptionIds.modernId(option);
            case TC_ITEM -> switch (option.historicalField()) {
                case "ItemsTC.thaumometer" -> "thaumcraft:thaumometer";
                case "ItemsTC.salisMundus" -> "thaumcraft:salis_mundus";
                default -> null;
            };
            case TC_COLORED_BLOCK -> null; // yellow Nitor is not yet ported.
        };
        if (legacyId == null) return null;
        if (legacyId.equals("minecraft:white_wool")) legacyId = "minecraft:wool";

        CompoundTag original = new CompoundTag();
        original.putString("id", legacyId);
        original.putByte("Count", (byte) 1);
        original.putShort("Damage", (short) 0);
        // Round-trip through our strict loader instead of silently writing
        // a modern stack that cannot later be activated/paid from inventory.
        ItemStack decoded = NeoForgeLegacyItemStackDecoder.safeDecode(original);
        if (decoded.isEmpty() || !ItemStack.isSameItemSameComponents(decoded, live))
            return null;
        return original;
    }

    private static boolean safeMetadata(LegacyTheorycraftOptionPools.Option option) {
        return option.metadata() == -1 || option.metadata() == 0;
    }
}
