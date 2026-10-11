package dev.ehtid.thaumcraft.scanning;

import dev.ehtid.thaumcraft.item.CurioItem;
import dev.ehtid.thaumcraft.port.core.LegacyMaterialTheoryCards;
import dev.ehtid.thaumcraft.registry.ThaumcraftItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Real server-side BETA26 CardCurio.initialize() bridge.
 *
 * BETA26 searches ONLY player.inventory.mainInventory, copies each Curio
 * source ItemStack to a one-item option, chooses one with
 * new java.util.Random(cardSeed).nextInt(optionCount), then persists
 * CardChoice.cardNBT.stack as the original 1.12 ItemStack compound.
 *
 * Modern UI/model components do not become invented 1.12 NBT fields.
 * The selected Curio source item must still exist in the player's inventory
 * at activation, when the atomic original-item transaction is performed.
 * This initializer never consumes anything.
 */
public final class NeoForgeTheorycraftCurioInitializer {
    private NeoForgeTheorycraftCurioInitializer() {}

    public static LegacyTheorycraftChoiceNbt.Choice initialize(
            ServerPlayer player, long cardSeed) {
        Objects.requireNonNull(player);
        if (player.level().isClientSide) return null;
        List<Integer> variants = new ArrayList<>(player.getInventory().items.size());
        for (ItemStack source : player.getInventory().items) {
            int variant = !source.isEmpty() && source.is(ThaumcraftItems.CURIO.get())
                    ? CurioItem.legacyMetadata(source) : -1;
            variants.add(variant);
        }
        var selected = LegacyMaterialTheoryCards.initializeCurio(variants, cardSeed);
        if (selected == null) return null;

        CompoundTag legacyItem = new CompoundTag();
        legacyItem.putString("id", "thaumcraft:curio");
        legacyItem.putByte("Count", (byte) 1);
        legacyItem.putShort("Damage", (short) selected.variantMetadata());

        CompoundTag card = new CompoundTag();
        card.putLong("seed", cardSeed);
        card.put("stack", legacyItem);
        if (!LegacyTheorycraftCardTagValidator.structurallyValid(
                LegacyMaterialTheoryCards.CURIO, card)) return null;

        return new LegacyTheorycraftChoiceNbt.Choice(
                LegacyMaterialTheoryCards.CURIO, false, false, card, new CompoundTag());
    }
}
