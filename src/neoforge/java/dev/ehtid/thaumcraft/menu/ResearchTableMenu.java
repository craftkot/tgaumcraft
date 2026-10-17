package dev.ehtid.thaumcraft.menu;

import dev.ehtid.thaumcraft.blockentity.ResearchTableBlockEntity;
import dev.ehtid.thaumcraft.registry.ThaumcraftMenus;
import dev.ehtid.thaumcraft.registry.ThaumcraftItems;
import dev.ehtid.thaumcraft.port.core.LegacyTheorycraftManifest;
import dev.ehtid.thaumcraft.port.core.LegacyTheorycraftChoicePreview;
import dev.ehtid.thaumcraft.port.core.LegacyTheorycraftDisplayArgument;
import dev.ehtid.thaumcraft.port.core.LegacyTheorycraftActivationPreflight;
import dev.ehtid.thaumcraft.port.core.LegacyTheorycraftCardMetadata;
import dev.ehtid.thaumcraft.port.core.LegacyTheorycraftRequiredItems;
import dev.ehtid.thaumcraft.scanning.NeoForgeTheorycraftActivationPreflight;
import dev.ehtid.thaumcraft.scanning.NeoForgeTheorycraftMaterialResolver;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Exact BETA26 ContainerResearchTable inventory layout:
 * scribing tools at (16,15), paper at (224,16), player's storage at
 * (77+18*column,190+18*row), and nine hotbar slots in a 3x3 at (20,190).
 *
 * Research card selection requires the full old ResearchTableData CardChoice
 * serialization and is intentionally not exposed through an unsafe button.
 */
public final class ResearchTableMenu extends AbstractContainerMenu {
    private static final int TABLE_SLOTS = 2;
    private static final int INVENTORY_END = 29; // table 2 + storage 27
    private static final int HOTBAR_END = 38;
    private static final int REQUIREMENTS_PER_CARD = 2;
    private static final int REQUIREMENT_PREVIEW_SLOTS = 3 * REQUIREMENTS_PER_CARD;
    public static final int XP_NONE = 0;
    public static final int XP_FIXED_FIVE = 1;
    public static final int XP_UP_TO_FIVE = 2;
    public static final int XP_ALL_PLUS_TEN = 3;
    private final Container table;
    private final DataSlot hasSession;
    private final DataSlot inspiration;
    private final DataSlot inspirationStart;
    private final DataSlot nearbyAidBits;
    private final DataSlot selectedAidBits;
    private final DataSlot hasChosenTheoryCard;
    private final DataSlot bonusDraws;
    private final DataSlot corruptTheoryChoices;
    private final DataSlot[] previewCards = new DataSlot[3];
    private final DataSlot[] previewArguments = new DataSlot[3];
    private final DataSlot[] previewInspirationCosts = new DataSlot[3];
    private final DataSlot[] previewInkCosts = new DataSlot[3];
    private final DataSlot[] previewPaperCosts = new DataSlot[3];
    private final DataSlot[] previewXpModes = new DataSlot[3];
    private final DataSlot[] previewRequirementStates = new DataSlot[3];
    private final DataSlot[] previewRequirementFlags = new DataSlot[3];
    private final DataSlot[] previewFailureCodes = new DataSlot[3];
    private final SimpleContainer requirementPreview =
            new SimpleContainer(REQUIREMENT_PREVIEW_SLOTS);
    private final java.util.Set<String> selectedAidKeys = new java.util.HashSet<>();

    public ResearchTableMenu(int id, Inventory inventory) {
        this(id, inventory, new SimpleContainer(TABLE_SLOTS),
                DataSlot.standalone(), DataSlot.standalone(), DataSlot.standalone());
    }

    public ResearchTableMenu(int id, Inventory inventory, Container table) {
        this(id, inventory, table,
                dataFor(table, 0), dataFor(table, 1), dataFor(table, 2));
    }

    private ResearchTableMenu(int id, Inventory inventory, Container table, DataSlot session,
                              DataSlot inspiration, DataSlot inspirationStart) {
        super(ThaumcraftMenus.RESEARCH_TABLE.get(), id);
        checkContainerSize(table, TABLE_SLOTS);
        this.table = table;
        this.hasSession = session;
        this.inspiration = inspiration;
        this.inspirationStart = inspirationStart;
        addDataSlot(hasSession);
        addDataSlot(inspiration);
        addDataSlot(inspirationStart);
        // Fourteen aid IDs fit inside one 16-bit vanilla DataSlot. Both masks
        // are derived on the SERVER; clients only request index toggles.
        nearbyAidBits = table instanceof ResearchTableBlockEntity live
                ? new DataSlot() {
                    @Override public int get() { return encodeAids(live.nearbyAids()); }
                    @Override public void set(int ignored) { }
                } : DataSlot.standalone();
        selectedAidBits = table instanceof ResearchTableBlockEntity
                ? new DataSlot() {
                    @Override public int get() { return encodeAids(selectedAidKeys); }
                    @Override public void set(int ignored) { }
                } : DataSlot.standalone();
        addDataSlot(nearbyAidBits);
        addDataSlot(selectedAidBits);
        hasChosenTheoryCard = table instanceof ResearchTableBlockEntity live
                ? new DataSlot() {
                    @Override public int get() {
                        return live.hasStructurallyValidPendingChoices()
                                && live.pendingChoices().stream().anyMatch(c -> c.selected())
                                ? 1 : 0;
                    }
                    @Override public void set(int ignored) { }
                } : DataSlot.standalone();
        addDataSlot(hasChosenTheoryCard);
        // The BETA26 GUI requests three cards only when one bonus draw is
        // available; otherwise button 2 gives two. Never trust the client
        // to invent bonus resources.
        bonusDraws = table instanceof ResearchTableBlockEntity liveBonus
                ? new DataSlot() {
                    @Override public int get() {
                        return liveBonus.session() == null ? 0
                                : liveBonus.session().bonusDraws();
                    }
                    @Override public void set(int ignored) { }
                } : DataSlot.standalone();
        addDataSlot(bonusDraws);
        // A saved malformed cardChoices tag may decode to zero preview
        // cards. Sync its presence independently so the UI doesn't offer
        // a fake draw button that the server will always reject.
        corruptTheoryChoices = table instanceof ResearchTableBlockEntity liveCorrupt
                ? new DataSlot() {
                    @Override public int get() {
                        return liveCorrupt.session() != null
                                && liveCorrupt.hasSavedCardChoicesTag()
                                && !liveCorrupt.hasStructurallyValidPendingChoices() ? 1 : 0;
                    }
                    @Override public void set(int ignored) { }
                } : DataSlot.standalone();
        addDataSlot(corruptTheoryChoices);
        // Original BETA26 draws at most three pending card choices. Their
        // full subclass NBT never leaves the server through these DataSlots:
        // the client receives only read-only class IDs and visual flags.
        for (int i = 0; i < previewCards.length; i++) {
            final int choiceIndex = i;
            previewCards[i] = table instanceof ResearchTableBlockEntity live
                    ? new DataSlot() {
                        @Override public int get() {
                            if (!live.hasStructurallyValidPendingChoices()) return 0;
                            var choices = live.pendingChoices();
                            if (choiceIndex >= choices.size()) return 0;
                            var choice = choices.get(choiceIndex);
                            return LegacyTheorycraftChoicePreview.encode(
                                    choice.key(), choice.fromAid(), choice.selected());
                        }
                        @Override public void set(int ignored) { }
                    } : DataSlot.standalone();
            addDataSlot(previewCards[i]);
            // The original four card titles with dynamic placeholders need
            // the genuine saved category/aspect. Sync only its bounded index,
            // not a fabricated value or untrusted full NBT compound.
            previewArguments[i] = table instanceof ResearchTableBlockEntity liveArgs
                    ? new DataSlot() {
                        @Override public int get() {
                            if (!liveArgs.hasStructurallyValidPendingChoices()) return 0;
                            var choices = liveArgs.pendingChoices();
                            if (choiceIndex >= choices.size()) return 0;
                            var choice = choices.get(choiceIndex);
                            String field = LegacyTheorycraftDisplayArgument.fieldKey(choice.key());
                            return field == null ? 0 : LegacyTheorycraftDisplayArgument.encode(
                                    choice.key(), choice.cardNBT().getString(field));
                        }
                        @Override public void set(int ignored) { }
                    } : DataSlot.standalone();
            addDataSlot(previewArguments[i]);

            // Safe activation metadata is derived from the authoritative
            // server CardChoice. The client never receives cardNBT.
            previewInspirationCosts[i] =
                    cardDataSlot(table, inventory.player, choiceIndex, 0);
            previewInkCosts[i] =
                    cardDataSlot(table, inventory.player, choiceIndex, 1);
            previewPaperCosts[i] =
                    cardDataSlot(table, inventory.player, choiceIndex, 2);
            previewXpModes[i] =
                    cardDataSlot(table, inventory.player, choiceIndex, 3);
            previewRequirementStates[i] =
                    cardDataSlot(table, inventory.player, choiceIndex, 4);
            previewRequirementFlags[i] =
                    cardDataSlot(table, inventory.player, choiceIndex, 5);
            previewFailureCodes[i] =
                    cardDataSlot(table, inventory.player, choiceIndex, 6);
            addDataSlot(previewInspirationCosts[i]);
            addDataSlot(previewInkCosts[i]);
            addDataSlot(previewPaperCosts[i]);
            addDataSlot(previewXpModes[i]);
            addDataSlot(previewRequirementStates[i]);
            addDataSlot(previewRequirementFlags[i]);
            addDataSlot(previewFailureCodes[i]);
        }
        table.startOpen(inventory.player);

        addSlot(new Slot(table, ResearchTableBlockEntity.SCRIBING_SLOT, 16, 15) {
            @Override public boolean mayPlace(ItemStack stack) {
                return table.canPlaceItem(ResearchTableBlockEntity.SCRIBING_SLOT, stack);
            }
        });
        addSlot(new Slot(table, ResearchTableBlockEntity.PAPER_SLOT, 224, 16) {
            @Override public boolean mayPlace(ItemStack stack) {
                return table.canPlaceItem(ResearchTableBlockEntity.PAPER_SLOT, stack);
            }
        });

        // Original 1.12 GUI puts storage on the 3x9 right and hotbar
        // on the 3x3 left, rather than the modern vanilla horizontal hotbar.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, 9 + row * 9 + col,
                        77 + col * 18, 190 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col,
                    20 + (col % 3) * 18, 190 + (col / 3) * 18));
        }

        // Six hidden, read-only ghost slots use vanilla menu ItemStack sync
        // for the at-most-two exact BETA26 requirements of each card. This
        // preserves item components/names without sending the original cardNBT.
        for (int i = 0; i < REQUIREMENT_PREVIEW_SLOTS; i++) {
            addSlot(new Slot(requirementPreview, i, -1000, -1000) {
                @Override public boolean mayPlace(ItemStack stack) { return false; }
                @Override public boolean mayPickup(Player player) { return false; }
            });
        }
        refreshRequirementPreview();
    }

    private static DataSlot cardDataSlot(Container table, Player player,
                                         int choiceIndex, int property) {
        return table instanceof ResearchTableBlockEntity live
                ? new DataSlot() {
                    @Override public int get() {
                        return cardDataValue(live, player, choiceIndex, property);
                    }
                    @Override public void set(int ignored) { }
                } : DataSlot.standalone();
    }

    private static int cardDataValue(ResearchTableBlockEntity table, Player player,
                                     int choiceIndex, int property) {
        if (!table.hasStructurallyValidPendingChoices()) return 0;
        var choices = table.pendingChoices();
        if (choiceIndex < 0 || choiceIndex >= choices.size()) return 0;
        var choice = choices.get(choiceIndex);
        String key = choice.key();
        if (!LegacyTheorycraftManifest.allCardKeys().contains(key)) {
            return property == 6
                    ? LegacyTheorycraftActivationPreflight.Failure.UNKNOWN_CARD.ordinal() + 1
                    : 0;
        }

        return switch (property) {
            case 0 -> LegacyTheorycraftCardMetadata.get(key).cost();
            case 1 -> key.endsWith(".CardScripting") ? 2 : 1;
            case 2 -> key.endsWith(".CardScripting") ? 1 : 0;
            case 3 -> xpMode(key);
            case 4 -> requirementState(key, choice);
            case 5 -> requirementFlags(key, choice);
            case 6 -> {
                if (!(player instanceof ServerPlayer serverPlayer)) yield 0;
                var check = NeoForgeTheorycraftActivationPreflight.check(
                        serverPlayer, table, choiceIndex);
                var failure = check == null
                        ? LegacyTheorycraftActivationPreflight.Failure.INVALID_CARD_NBT
                        : check.costs().failure();
                yield failure.ordinal() + 1;
            }
            default -> 0;
        };
    }

    private static int xpMode(String key) {
        if (key.endsWith(".CardEnchantment")) return XP_FIXED_FIVE;
        if (key.endsWith(".CardSpellbinding")) return XP_UP_TO_FIVE;
        if (key.endsWith(".CardDarkWhispers")) return XP_ALL_PLUS_TEN;
        return XP_NONE;
    }

    private static NeoForgeTheorycraftMaterialResolver.Resolved resolvedRequirements(
            String key, dev.ehtid.thaumcraft.scanning.LegacyTheorycraftChoiceNbt.Choice choice) {
        // Show no actionable ingredient ghost slots for a corrupt, forged
        // seed-derived CardChoice. Activation already rejects these NBT
        // records; preview must not suggest the forged cost is legitimate.
        if (!dev.ehtid.thaumcraft.scanning.NeoForgeTheorycraftSeedIntegrity
                .matches(choice)) return null;
        int expected = LegacyTheorycraftRequiredItems.requiredItemCount(key);
        if (expected == 0) return new NeoForgeTheorycraftMaterialResolver.Resolved(
                java.util.List.of(), new boolean[0]);
        var resolved = NeoForgeTheorycraftMaterialResolver.resolve(key, choice.cardNBT());
        if (resolved == null || resolved.required().size() != expected
                || resolved.required().size() > REQUIREMENTS_PER_CARD) return null;
        return resolved;
    }

    private static int requirementState(
            String key, dev.ehtid.thaumcraft.scanning.LegacyTheorycraftChoiceNbt.Choice choice) {
        if (LegacyTheorycraftRequiredItems.requiredItemCount(key) == 0) return 0;
        return resolvedRequirements(key, choice) == null ? 2 : 1;
    }

    private static int requirementFlags(
            String key, dev.ehtid.thaumcraft.scanning.LegacyTheorycraftChoiceNbt.Choice choice) {
        var resolved = resolvedRequirements(key, choice);
        if (resolved == null) return 0;
        boolean[] consumed = resolved.consumed();
        int flags = 0;
        for (int i = 0; i < consumed.length && i < REQUIREMENTS_PER_CARD; i++)
            if (consumed[i]) flags |= 1 << i;
        return flags;
    }

    private void refreshRequirementPreview() {
        if (!(table instanceof ResearchTableBlockEntity live)) return;
        for (int slot = 0; slot < REQUIREMENT_PREVIEW_SLOTS; slot++)
            requirementPreview.setItem(slot, ItemStack.EMPTY);

        // A damaged legacy list must not leak partial phantom card previews.
        if (!live.hasStructurallyValidPendingChoices()) return;
        var choices = live.pendingChoices();
        for (int card = 0; card < choices.size() && card < 3; card++) {
            var choice = choices.get(card);
            if (!LegacyTheorycraftManifest.allCardKeys().contains(choice.key())) continue;
            var resolved = resolvedRequirements(choice.key(), choice);
            if (resolved == null) continue;
            for (int item = 0; item < resolved.required().size()
                    && item < REQUIREMENTS_PER_CARD; item++) {
                requirementPreview.setItem(card * REQUIREMENTS_PER_CARD + item,
                        resolved.required().get(item).copy());
            }
        }
    }

    private static DataSlot dataFor(Container table, int property) {
        return new DataSlot() {
            @Override public int get() {
                if (!(table instanceof ResearchTableBlockEntity blockEntity)
                        || blockEntity.session() == null) return 0;
                return switch (property) {
                    case 0 -> 1;
                    case 1 -> blockEntity.session().inspiration();
                    case 2 -> blockEntity.session().inspirationStart();
                    default -> 0;
                };
            }
            @Override public void set(int ignored) {
                // The server is authoritative. Client gets DataSlot.standalone().
            }
        };
    }

    private static int encodeAids(java.util.Set<String> keys) {
        int bits = 0;
        var order = LegacyTheorycraftManifest.AID_REGISTRATION_ORDER;
        for (int i = 0; i < order.size(); i++) {
            if (keys.contains(order.get(i))) bits |= 1 << i;
        }
        return bits;
    }

    public boolean aidAvailable(int registrationIndex) {
        return registrationIndex >= 0
                && registrationIndex < LegacyTheorycraftManifest.AID_REGISTRATION_ORDER.size()
                && (nearbyAidBits.get() & (1 << registrationIndex)) != 0;
    }

    public boolean aidSelected(int registrationIndex) {
        return registrationIndex >= 0
                && registrationIndex < LegacyTheorycraftManifest.AID_REGISTRATION_ORDER.size()
                && (selectedAidBits.get() & (1 << registrationIndex)) != 0;
    }

    public int cardPreview(int index) {
        return index >= 0 && index < previewCards.length ? previewCards[index].get() : 0;
    }

    public int cardPreviewArgument(int index) {
        return index >= 0 && index < previewArguments.length
                ? previewArguments[index].get() : 0;
    }

    public int cardInspirationCost(int index) {
        return index >= 0 && index < previewInspirationCosts.length
                ? previewInspirationCosts[index].get() : 0;
    }

    public int cardInkCost(int index) {
        return index >= 0 && index < previewInkCosts.length
                ? previewInkCosts[index].get() : 0;
    }

    public int cardPaperCost(int index) {
        return index >= 0 && index < previewPaperCosts.length
                ? previewPaperCosts[index].get() : 0;
    }

    public int cardXpMode(int index) {
        return index >= 0 && index < previewXpModes.length
                ? previewXpModes[index].get() : XP_NONE;
    }

    /** 0=no required items, 1=exact server ItemStacks synced, 2=fail-closed/unresolved. */
    public int cardRequirementState(int index) {
        return index >= 0 && index < previewRequirementStates.length
                ? previewRequirementStates[index].get() : 0;
    }

    public ItemStack cardRequirement(int cardIndex, int requirementIndex) {
        if (cardIndex < 0 || cardIndex >= 3 || requirementIndex < 0
                || requirementIndex >= REQUIREMENTS_PER_CARD) return ItemStack.EMPTY;
        return requirementPreview.getItem(
                cardIndex * REQUIREMENTS_PER_CARD + requirementIndex);
    }

    public boolean cardRequirementConsumed(int cardIndex, int requirementIndex) {
        if (cardIndex < 0 || cardIndex >= previewRequirementFlags.length
                || requirementIndex < 0 || requirementIndex >= REQUIREMENTS_PER_CARD)
            return false;
        return (previewRequirementFlags[cardIndex].get()
                & (1 << requirementIndex)) != 0;
    }

    /** Enum ordinal + 1; zero means no synchronized card/failure state. */
    public int cardFailureCode(int index) {
        return index >= 0 && index < previewFailureCodes.length
                ? previewFailureCodes[index].get() : 0;
    }

    public boolean hasChosenTheoryCard() { return hasChosenTheoryCard.get() != 0; }
    public boolean hasCorruptTheoryChoices() { return corruptTheoryChoices.get() != 0; }
    public int bonusDraws() { return bonusDraws.get(); }
    public boolean hasPendingCardChoices() {
        for (int i = 0; i < previewCards.length; i++)
            if (previewCards[i].get() != 0) return true;
        return false;
    }
    public boolean hasSession() { return hasSession.get() != 0; }
    public int inspiration() { return inspiration.get(); }
    public int inspirationStart() { return inspirationStart.get(); }

    /**
     * BETA26 ContainerResearchTable.enchantItem button IDs:
     * 1 = keep selected card, 2/3 = draw choices (consume paper),
     * 4..6 = activate one of three choices, 7 = finish, 9 = abandon.
     *
     * Start/finish/abandon are implemented with true table inventory checks.
     * Draw and activate are now server-authoritative: the complete 33-card
     * factory, staged effects, atomic item/ink/paper/XP commit and original
     * selected-row serialization handle buttons 2..6.
     */
    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (!(player instanceof ServerPlayer serverPlayer)
                || !stillValid(serverPlayer)
                || !(table instanceof ResearchTableBlockEntity researchTable)) return false;
        if (buttonId >= 10 && buttonId < 10 + LegacyTheorycraftManifest.AID_REGISTRATION_ORDER.size()) {
            // A malicious client cannot select unsupported or absent helpers.
            if (researchTable.session() != null) return false;
            String key = LegacyTheorycraftManifest.AID_REGISTRATION_ORDER.get(buttonId - 10);
            // Existing selections can always be removed, even if their
            // surrounding block was broken after the aid was chosen.
            if (selectedAidKeys.remove(key)) {
                broadcastChanges();
                return true;
            }
            if (!researchTable.nearbyAids().contains(key)) return false;
            selectedAidKeys.add(key);
            broadcastChanges();
            return true;
        }
        if (buttonId == 1) {
            // BETA26 reuses ID 1: before theory it starts a session;
            // after selecting a drawn card it keeps lastDraw and clears
            // the choices. Never silently restart an active theory.
            if (researchTable.session() != null) {
                if (!researchTable.keepSelectedTheoryCard(serverPlayer)) return false;
                broadcastChanges();
                return true;
            }
            dev.ehtid.thaumcraft.util.ThaumcraftSmokeAudit.mark("TABLE_CREATE_MENU_RECEIVED");
            if (!researchTable.startNewTheory(serverPlayer,
                    new java.util.HashSet<>(selectedAidKeys))) {
                dev.ehtid.thaumcraft.util.ThaumcraftSmokeAudit.mark("TABLE_CREATE_MENU_REJECTED");
                return false;
            }
            dev.ehtid.thaumcraft.util.ThaumcraftSmokeAudit.mark("TABLE_CREATE_MENU_ACCEPTED");
            selectedAidKeys.clear();
            broadcastChanges();
            return true;
        }
        if (buttonId == 2 || buttonId == 3) {
            // One paper per full draw, no paper charged for a failed
            // initialization. Three cards require a real bonusDraws token.
            if (researchTable.session() == null || researchTable.session().isComplete()
                    || researchTable.session().bonusDraws() <= 0 && buttonId == 3)
                return false;
            dev.ehtid.thaumcraft.util.ThaumcraftSmokeAudit.mark("TABLE_DRAW_MENU_RECEIVED");
            if (!researchTable.drawOriginalTheoryCards(serverPlayer, buttonId)) {
                dev.ehtid.thaumcraft.util.ThaumcraftSmokeAudit.mark("TABLE_DRAW_MENU_REJECTED");
                return false;
            }
            dev.ehtid.thaumcraft.util.ThaumcraftSmokeAudit.mark("TABLE_DRAW_MENU_ACCEPTED");
            broadcastChanges();
            return true;
        }
        if (buttonId >= 4 && buttonId <= 6) {
            dev.ehtid.thaumcraft.util.ThaumcraftSmokeAudit.mark("TABLE_CARD_MENU_RECEIVED");
            if (!researchTable.activateOriginalTheoryCard(serverPlayer, buttonId - 4)) {
                dev.ehtid.thaumcraft.util.ThaumcraftSmokeAudit.mark("TABLE_CARD_MENU_REJECTED");
                return false;
            }
            dev.ehtid.thaumcraft.util.ThaumcraftSmokeAudit.mark("TABLE_CARD_MENU_ACCEPTED");
            broadcastChanges();
            return true;
        }
        if (buttonId == 7) {
            if (!researchTable.finishTheory(serverPlayer)) return false;
            broadcastChanges();
            return true;
        }
        if (buttonId == 9) {
            if (!researchTable.abandonTheory(serverPlayer)) return false;
            broadcastChanges();
            return true;
        }
        // Reject every other client-supplied button ID.
        return false;
    }

    @Override
    public void broadcastChanges() {
        refreshRequirementPreview();
        super.broadcastChanges();
    }

    @Override public boolean stillValid(Player player) { return table.stillValid(player); }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        // Hidden requirement slots are server-synchronized previews only.
        // Never allow shift-click to materialize a ghost ingredient.
        if (index < 0 || index >= HOTBAR_END) return ItemStack.EMPTY;
        Slot source = slots.get(index);
        if (!source.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = source.getItem();
        ItemStack before = stack.copy();
        if (index < TABLE_SLOTS) {
            if (!moveItemStackTo(stack, TABLE_SLOTS, HOTBAR_END, true))
                return ItemStack.EMPTY;
        } else {
            boolean inserted = false;
            for (int slot = 0; slot < TABLE_SLOTS; slot++) {
                Slot target = slots.get(slot);
                if (target.mayPlace(stack)
                        && moveItemStackTo(stack, slot, slot + 1, false)) {
                    inserted = true;
                    break;
                }
            }
            if (!inserted) {
                if (index < INVENTORY_END) {
                    if (!moveItemStackTo(stack, INVENTORY_END, HOTBAR_END, false))
                        return ItemStack.EMPTY;
                } else if (!moveItemStackTo(stack, TABLE_SLOTS, INVENTORY_END, false))
                    return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) source.set(ItemStack.EMPTY);
        else source.setChanged();
        source.onTake(player, stack);
        return before;
    }

    @Override public void removed(Player player) {
        super.removed(player);
        table.stopOpen(player);
    }
}
