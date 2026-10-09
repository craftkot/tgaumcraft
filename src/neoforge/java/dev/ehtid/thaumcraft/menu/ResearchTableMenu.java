package dev.ehtid.thaumcraft.menu;

import dev.ehtid.thaumcraft.blockentity.ResearchTableBlockEntity;
import dev.ehtid.thaumcraft.registry.ThaumcraftMenus;
import dev.ehtid.thaumcraft.registry.ThaumcraftItems;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
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

    public boolean hasSession() { return hasSession.get() != 0; }
    public int inspiration() { return inspiration.get(); }
    public int inspirationStart() { return inspirationStart.get(); }

    /**
     * BETA26 ContainerResearchTable.enchantItem button IDs:
     * 1 = keep selected card, 2/3 = draw choices (consume paper),
     * 4..6 = activate one of three choices, 7 = finish, 9 = abandon.
     *
     * The full draw/activate buttons remain disabled until the complete
     * original card registry and choice serialization are live. Only the
     * two already ported terminal actions are server-authoritative here.
     */
    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (!(player instanceof ServerPlayer serverPlayer)
                || !stillValid(serverPlayer)
                || !(table instanceof ResearchTableBlockEntity researchTable)) return false;
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
