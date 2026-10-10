package dev.ehtid.thaumcraft.scanning;

import dev.ehtid.thaumcraft.item.CelestialNotesItem;
import dev.ehtid.thaumcraft.port.core.AspectRegistry;
import dev.ehtid.thaumcraft.port.core.LegacyTheorycraftRequiredItems;
import dev.ehtid.thaumcraft.registry.ThaumcraftItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Creates REAL NeoForge stacks ONLY when the original BETA26 required
 * object has an already-ported 1.21 equivalent with checked metadata.
 *
 * No guesses for unported phials or Curio metadata; old saved stacks
 * decode only when their exact unchanged registry identity is validated
 * by NeoForgeLegacyItemStackDecoder.
 *
 * Returns null for any unmapped original object or invalid subclass NBT.
 * The returned stacks are ingredient SPECIFICATIONS, not rewards and are
 * not directly consumed; call NeoForgeTheorycraftItemTransaction.prepare
 * and commit only after full original card activation validation.
 */
public final class NeoForgeTheorycraftMaterialResolver {
    private NeoForgeTheorycraftMaterialResolver() {}

    private static final AspectRegistry ASPECTS = new AspectRegistry();

    public record Resolved(List<ItemStack> required, boolean[] consumed) {
        public Resolved {
            required = List.copyOf(required);
            consumed = consumed.clone();
            if (required.size() != consumed.length)
                throw new IllegalArgumentException("requirements/consume-mask mismatch");
        }
        @Override public boolean[] consumed() { return consumed.clone(); }
    }

    public static Resolved resolve(String cardKey, CompoundTag originalCardNbt) {
        if (!LegacyTheorycraftCardTagValidator.structurallyValid(cardKey, originalCardNbt))
            return null;

        // BETA26 CardCelestial.initialize always chooses two distinct
        // metadata variants. A forged saved card with duplicate md values
        // must not be allowed to debit twice and grant celestial rewards.
        if (cardKey.endsWith(".CardCelestial")
                && originalCardNbt.getInt("md1") == originalCardNbt.getInt("md2"))
            return null;

        List<LegacyTheorycraftRequiredItems.Requirement> descriptors =
                LegacyTheorycraftRequiredItems.get(cardKey);
        ArrayList<ItemStack> items = new ArrayList<>(descriptors.size());
        for (LegacyTheorycraftRequiredItems.Requirement descriptor : descriptors) {
            String field = descriptor.cardField();
            ItemStack next = switch (descriptor.source()) {
                case CELESTIAL_NOTE -> celestial(originalCardNbt.getInt(field));
                case ASPECT_CRYSTAL -> crystal(originalCardNbt.getString(field));
                case VANILLA_CLAY_BALL -> new ItemStack(Items.CLAY_BALL);
                case SAVED_ITEM_STACK -> NeoForgeLegacyItemStackDecoder.safeDecode(
                        originalCardNbt.getCompound(field));
                case FILLED_PHIAL -> ItemStack.EMPTY;
            };
            if (next.isEmpty()) return null;
            items.add(next);
        }
        return new Resolved(items, LegacyTheorycraftRequiredItems.consumedFlags(cardKey));
    }

    private static ItemStack celestial(int metadata) {
        return metadata >= 0 && metadata <= 12
                ? CelestialNotesItem.create(metadata) : ItemStack.EMPTY;
    }

    /** BETA26 ThaumcraftApiHelper.makeCrystal(aspect) always holds one aspect. */
    private static ItemStack crystal(String aspectTag) {
        if (aspectTag == null || ASPECTS.get(aspectTag) == null)
            return ItemStack.EMPTY;
        ItemStack crystal = new ItemStack(ThaumcraftItems.CRYSTAL_ESSENCE.get());
        CompoundTag aspect = new CompoundTag();
        aspect.putString("key", aspectTag);
        aspect.putInt("amount", 1);
        ListTag entries = new ListTag();
        entries.add(aspect);
        CompoundTag custom = new CompoundTag();
        custom.put("Aspects", entries);
        crystal.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
        return crystal;
    }
}
