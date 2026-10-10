package dev.ehtid.thaumcraft.menu;

import dev.ehtid.thaumcraft.blockentity.ResearchTableBlockEntity;
import dev.ehtid.thaumcraft.registry.ThaumcraftMenus;
import dev.ehtid.thaumcraft.registry.ThaumcraftItems;
import dev.ehtid.thaumcraft.port.core.LegacyTheorycraftManifest;
import dev.ehtid.thaumcraft.port.core.LegacyTheorycraftChoicePreview;
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
    private final Container table;
    private final DataSlot hasSession;
    private final DataSlot inspiration;
    private final DataSlot inspirationStart;
    private final DataSlot nearbyAidBits;
    private final DataSlot selectedAidBits;
    private final DataSlot hasChosenTheoryCard;
    private final DataSlot[] previewCards = new DataSlot[3];
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
                        return live.pendingChoices().stream().anyMatch(c -> c.selected())
                                ? 1 : 0;
                    }
                    @Override public void set(int ignored) { }
                } : DataSlot.standalone();
        addDataSlot(hasChosenTheoryCard);
        // Original BETA26 draws at most three pending card choices. Their
        // full subclass NBT never leaves the server through these DataSlots:
        // the client receives only read-only class IDs and visual flags.
        for (int i = 0; i < previewCards.length; i++) {
            final int choiceIndex = i;
            previewCards[i] = table instanceof ResearchTableBlockEntity live
                    ? new DataSlot() {
                        @Override public int get() {
                            var choices = live.pendingChoices();
                            if (choiceIndex >= choices.size()) return 0;
                            var choice = choices.get(choiceIndex);
                            return LegacyTheorycraftChoicePreview.encode(
                                    choice.key(), choice.fromAid(), choice.selected());
                        }
                        @Override public void set(int ignored) { }
                    } : DataSlot.standalone();
            addDataSlot(previewCards[i]);
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

    public boolean hasChosenTheoryCard() { return hasChosenTheoryCard.get() != 0; }
    public boolean hasSession() { return hasSession.get() != 0; }
    public int inspiration() { return inspiration.get(); }
    public int inspirationStart() { return inspirationStart.get(); }

    /**
     * BETA26 ContainerResearchTable.enchantItem button IDs:
     * 1 = keep selected card, 2/3 = draw choices (consume paper),
     * 4..6 = activate one of three choices, 7 = finish, 9 = abandon.
     *
     * Start/finish/abandon are implemented with true table inventory checks.
     * Draw/activate choices remain disabled until original CardChoice class
     * dispatch and nearby aid selection are fully ported.
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
            if (!researchTable.startNewTheory(serverPlayer,
                    new java.util.HashSet<>(selectedAidKeys))) return false;
            selectedAidKeys.clear();
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
        // Don't let malformed/early client requests consume resources or
        // fabricate theory results in place of the unported original cards.
        return false;
    }

    @Override public boolean stillValid(Player player) { return table.stillValid(player); }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
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
