package dev.ehtid.thaumcraft.scanning;

import dev.ehtid.thaumcraft.blockentity.ResearchTableBlockEntity;
import dev.ehtid.thaumcraft.port.core.LegacyTheorycraftActivationPreflight;
import dev.ehtid.thaumcraft.port.core.LegacyTheorycraftInventoryPlanner;
import dev.ehtid.thaumcraft.registry.ThaumcraftItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Objects;

/**
 * Server-authoritative nonmutating validation before BETA26 card activation.
 *
 * Real source saved CardChoice.cardNBT is validated, the player's 36 main
 * inventory slots are checked with the original material planner, XP and
 * inspiration are read from the live server, and one Scripting activation
 * budgets BOTH the inner ink+paper AND outer ink. Nothing is ever debited
 * from a UI hover or a failed activation preview.
 *
 * This is a staging bridge: do not expose buttons 4..6 before 33 original
 * activation effects and a commit-time revalidation are implemented.
 */
public final class NeoForgeTheorycraftActivationPreflight {
    private NeoForgeTheorycraftActivationPreflight() {}

    public record Check(LegacyTheorycraftActivationPreflight.Plan costs,
                        LegacyTheorycraftChoiceNbt.Choice originalCard,
                        LegacyTheorycraftInventoryPlanner.Plan inventoryPlan) {
        public boolean canActivate() { return costs.accepted(); }
    }

    public static Check check(ServerPlayer player, ResearchTableBlockEntity table,
                              int index) {
        Objects.requireNonNull(player);
        Objects.requireNonNull(table);
        var session = table.session();
        // readChoices skips malformed legacy rows. A truncated view must
        // never claim an activation can succeed or disclose a fake preview.
        if (!table.hasStructurallyValidPendingChoices()) return null;
        var choices = table.pendingChoices();
        if (index < 0 || index >= choices.size() || choices.size() > 3) return null;
        var selected = choices.get(index);
        // A legacy ItemStack with the right NBT *shape* is not evidence
        // that it was drawn by the original seed. Reject tampered saves
        // before reading the player's inventory or promising any reward.
        if (!NeoForgeTheorycraftSeedIntegrity.matches(selected)) return null;
        // These four cards additionally store an initialization-time category
        // or value derived from the theory's current totals. Until a card is
        // activated those totals cannot legitimately change via the table.
        // Reject stale/forged state before inventory, XP or supplies are read.
        String name = selected.key().substring(selected.key().lastIndexOf('.') + 1);
        if (session != null && (name.equals("CardInspired")
                || name.equals("CardNotation") || name.equals("CardReject")
                || name.equals("CardCelestial"))) {
            var data = selected.cardNBT();
            if (!dev.ehtid.thaumcraft.port.core.LegacyTheorycraftSessionFieldIntegrity.matches(
                    name, data.getLong("seed"),
                    java.util.Map.of("cat", data.getString("cat"),
                            "cat1", data.getString("cat1"),
                            "cat2", data.getString("cat2")),
                    java.util.Map.of("amt", data.getInt("amt")), session)) return null;
        }

        boolean valid = LegacyTheorycraftCardTagValidator.structurallyValid(
                selected.key(), selected.cardNBT());
        // Original CardChoice selections may be loaded from older worlds:
        // don't strip unknown or malformed NBT; just reject activation.
        boolean othersSelected = false;
        for (int i = 0; i < choices.size(); i++) {
            if (i != index && choices.get(i).selected()) othersSelected = true;
        }

        ItemStack ink = table.getItem(ResearchTableBlockEntity.SCRIBING_SLOT);
        ItemStack paper = table.getItem(ResearchTableBlockEntity.PAPER_SLOT);
        int inkAvailable = ink.is(ThaumcraftItems.SCRIBING_TOOLS.get())
                && ink.isDamageableItem() ? Math.max(0,
                        ink.getMaxDamage() - ink.getDamageValue()) : 0;
        int paperAvailable = paper.is(Items.PAPER) ? paper.getCount() : 0;

        // Invalid original subclass state must never be inspected as
        // a material recipe (could contain non-1.21 ItemStack metadata).
        LegacyTheorycraftInventoryPlanner.Plan items = valid && session != null
                ? NeoForgeTheorycraftItemTransaction.prepareOriginalChoice(
                        player, selected) : null;
        var inputs = new LegacyTheorycraftActivationPreflight.Inputs(
                selected.key(), valid,
                session != null && !session.isComplete()
                        && table.stillValid(player) && !player.level().isClientSide,
                selected.selected(), othersSelected,
                session == null ? 0 : session.inspiration(),
                inkAvailable, paperAvailable,
                player.experienceLevel, items != null);

        var costs = LegacyTheorycraftActivationPreflight.check(inputs);
        return new Check(costs, selected, items);
    }
}
