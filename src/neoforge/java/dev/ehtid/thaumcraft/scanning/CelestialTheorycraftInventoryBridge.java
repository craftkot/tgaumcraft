package dev.ehtid.thaumcraft.scanning;

import dev.ehtid.thaumcraft.item.CelestialNotesItem;
import dev.ehtid.thaumcraft.network.ThaumcraftNetworking;
import dev.ehtid.thaumcraft.player.ThaumcraftPlayerAttachments;
import dev.ehtid.thaumcraft.port.core.LegacyCelestialTheoryCard;
import dev.ehtid.thaumcraft.port.core.LegacyResearchTableData;
import dev.ehtid.thaumcraft.registry.ThaumcraftItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Server-side bridge for BETA26 CardCelestial.getRequiredItems / activate.
 *
 * This is an inventory transaction helper for the upcoming full research-table
 * menu. It must NOT be invoked on right-click alone: BETA26 only consumes the
 * notes after the player chooses the card and has enough inspiration.
 */
public final class CelestialTheorycraftInventoryBridge {
    private CelestialTheorycraftInventoryBridge() {}

    public static boolean activateSelectedCard(ServerPlayer player, LegacyResearchTableData table,
                                               LegacyCelestialTheoryCard.Selection selection) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(table, "table");
        Objects.requireNonNull(selection, "selection");
        if (player.level().isClientSide || table.isComplete() || table.inspiration() < 1) return false;

        // ItemCelestialNotes stores the original thirteen metadata variants in
        // 1.21 CUSTOM_DATA. All unrelated items are null match candidates.
        List<Integer> variants = new ArrayList<>();
        for (ItemStack stack : player.getInventory().items) {
            variants.add(stack.isEmpty() || !stack.is(ThaumcraftItems.CELESTIAL_NOTES.get())
                    ? null : CelestialNotesItem.legacyMetadata(stack));
        }
        int[] slots = LegacyCelestialTheoryCard.requiredNoteSlots(selection, variants);
        if (slots == null) return false;

        ItemStack first = player.getInventory().items.get(slots[0]);
        ItemStack second = player.getInventory().items.get(slots[1]);
        if (first.isEmpty() || second.isEmpty()
                || !first.is(ThaumcraftItems.CELESTIAL_NOTES.get())
                || !second.is(ThaumcraftItems.CELESTIAL_NOTES.get())
                || CelestialNotesItem.legacyMetadata(first) != selection.firstMetadata()
                || CelestialNotesItem.legacyMetadata(second) != selection.secondMetadata()) {
            return false;
        }

        // The original CardCelestial consumes two DIFFERENT metadata notes.
        // Evaluate the action before mutating, then commit both consumes and
        // theory/warp changes in the same server-thread call.
        var effects = LegacyCelestialTheoryCard.activate(
                selection, (LegacyCelestialTheoryCard.BoundedRandom)
                        bound -> player.getRandom().nextInt(bound), slots);

        first.shrink(1);
        second.shrink(1);
        table.applyCelestialEffects(
                effects, player.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).warp());
        table.consumeInspirationAndPlaceCard(1);

        player.getInventory().setChanged();
        ThaumcraftNetworking.sync(player);
        return true;
    }
}
