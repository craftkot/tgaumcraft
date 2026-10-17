package dev.ehtid.thaumcraft.menu;

import dev.ehtid.thaumcraft.blockentity.ArcaneWorkbenchBlockEntity;
import dev.ehtid.thaumcraft.network.ThaumcraftNetworking;
import dev.ehtid.thaumcraft.player.ThaumcraftPlayerAttachments;
import dev.ehtid.thaumcraft.port.core.ArcaneWorkbenchRuntime;
import dev.ehtid.thaumcraft.port.core.LegacyBellowsArcaneRecipe;
import dev.ehtid.thaumcraft.port.core.LegacyTubeBufferArcaneRecipe;
import dev.ehtid.thaumcraft.port.core.LegacyAlembicArcaneRecipe;
import dev.ehtid.thaumcraft.port.core.LegacyAlchemyFilterArcaneRecipe;
import dev.ehtid.thaumcraft.port.core.LegacyTubeFilterArcaneRecipe;
import dev.ehtid.thaumcraft.port.core.LegacyAlchemyConstructArcaneRecipe;
import dev.ehtid.thaumcraft.port.core.LegacyUpgradedSmelterArcaneRecipe;
import dev.ehtid.thaumcraft.port.core.LegacyAdvancedAlchemyConstructArcaneRecipe;
import dev.ehtid.thaumcraft.port.core.LegacyUpgradedSmelterArcaneRecipe.Kind;
import dev.ehtid.thaumcraft.port.core.LegacyEssentiaTubeUpgradeRecipes;
import dev.ehtid.thaumcraft.port.core.LegacyEssentiaTubeUpgradeRecipes.Upgrade;
import dev.ehtid.thaumcraft.registry.ThaumcraftItems;
import net.minecraft.server.level.ServerPlayer;
import dev.ehtid.thaumcraft.registry.ThaumcraftMenus;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Server-authoritative 15-slot Arcane Workbench inventory bridge. */
public final class ArcaneWorkbenchMenu extends AbstractContainerMenu {
    private final Container bench;
    private final SimpleContainer result = new SimpleContainer(1);
    private final Player owner;
    private final DataSlot availableVis;
    private final DataSlot displayedVisCost;
    private final DataSlot requiredCrystalMask;

    public ArcaneWorkbenchMenu(int id, Inventory inventory) {
        this(id, inventory, new SimpleContainer(ArcaneWorkbenchRuntime.SLOT_COUNT),
                DataSlot.standalone(), DataSlot.standalone());
    }

    public ArcaneWorkbenchMenu(int id, Inventory inventory, Container bench) {
        this(id, inventory, bench, visSlot(bench), recipeCostSlot(bench));
    }

    private ArcaneWorkbenchMenu(int id, Inventory inventory, Container bench,
                                DataSlot availableVis, DataSlot displayedVisCost) {
        super(ThaumcraftMenus.ARCANE_WORKBENCH.get(), id);
        checkContainerSize(bench, ArcaneWorkbenchRuntime.SLOT_COUNT);
        this.bench = bench;
        this.owner = inventory.player;
        this.availableVis = availableVis;
        this.displayedVisCost = displayedVisCost;
        addDataSlot(availableVis);
        addDataSlot(displayedVisCost);
        requiredCrystalMask = crystalMaskSlot(bench);
        addDataSlot(requiredCrystalMask);
        bench.startOpen(inventory.player);
        addSlot(new Slot(result, 0, 160, 64) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
            @Override public boolean mayPickup(Player player) {
                if (player instanceof ServerPlayer sp) return canCraftCurrentRecipe(sp, 0.0F);
                return !result.getItem(0).isEmpty();
            }
            @Override public void onTake(Player player, ItemStack stack) {
                if (player instanceof ServerPlayer sp)
                    craftSelectedRecipe(sp, stack, 0.0F);
                super.onTake(player, stack);
            }
        });
        for (int i = 0; i < 9; i++) {
            addSlot(new Slot(bench, i, 40 + (i % 3) * 24, 40 + (i / 3) * 24));
        }
        final int[] crystalX = {64, 17, 112, 17, 112, 64};
        final int[] crystalY = {13, 35, 35, 93, 93, 115};
        for (int i = 9; i < 15; i++) {
            final int slot = i;
            addSlot(new Slot(bench, slot, crystalX[slot - 9], crystalY[slot - 9]) {
                @Override public boolean mayPlace(ItemStack stack) {
                    return bench.canPlaceItem(slot, stack);
                }
            });
        }
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(inventory, col + row * 9 + 9, 16 + col * 18, 151 + row * 18));
        for (int col = 0; col < 9; col++)
            addSlot(new Slot(inventory, col, 16 + col * 18, 209));
    }

    private static DataSlot visSlot(Container bench) {
        return new DataSlot() {
            @Override
            public int get() {
                return bench instanceof ArcaneWorkbenchBlockEntity workbench ? (int) workbench.getVis() : 0;
            }

            @Override
            public void set(int value) {
                // Server slot is read-only. Client menus use DataSlot.standalone().
            }
        };
    }

    private static DataSlot recipeCostSlot(Container bench) {
        return new DataSlot() {
            @Override
            public int get() {
                if (!(bench instanceof ArcaneWorkbenchBlockEntity workbench)) return 0;
                if (workbench.matchesThaumometerPattern())
                    return dev.ehtid.thaumcraft.port.core.ThaumometerRecipeLogic.BASE_VIS;
                if (workbench.matchesVisResonatorPattern())
                    return dev.ehtid.thaumcraft.port.core.VisResonatorRecipeLogic.BASE_VIS;
                if (workbench.matchesWardedJarPattern())
                    return dev.ehtid.thaumcraft.port.core.LegacyWardedJarRecipes.NORMAL_VIS;
                if (workbench.matchesVoidJarPattern())
                    return dev.ehtid.thaumcraft.port.core.LegacyWardedJarRecipes.VOID_VIS;
                if (workbench.matchesEssentiaTubePattern())
                    return dev.ehtid.thaumcraft.port.core.LegacyEssentiaTubeRecipe.BASE_VIS;
                if (workbench.selectedTubeUpgrade() != null)
                    return LegacyEssentiaTubeUpgradeRecipes.VIS;
                if (workbench.matchesBellowsPattern()) return LegacyBellowsArcaneRecipe.VIS;
                if (workbench.matchesTubeBufferPattern()) return LegacyTubeBufferArcaneRecipe.BASE_VIS;
                if (workbench.matchesAlembicPattern()) return LegacyAlembicArcaneRecipe.VIS;
                if (workbench.matchesAlchemyFilterPattern()) return LegacyAlchemyFilterArcaneRecipe.VIS;
                if (workbench.matchesFilteredTubePattern()) return LegacyTubeFilterArcaneRecipe.VIS;
                if (workbench.matchesAlchemyConstructPattern()) return LegacyAlchemyConstructArcaneRecipe.VIS;
                if (workbench.matchesAdvancedAlchemyConstructPattern())
                    return LegacyAdvancedAlchemyConstructArcaneRecipe.VIS;
                if (workbench.matchesUpgradedSmelterPattern(Kind.THAUMIUM)) return Kind.THAUMIUM.vis;
                if (workbench.matchesUpgradedSmelterPattern(Kind.VOID)) return Kind.VOID.vis;
                if (workbench.matchesBasicSmelterPattern())
                    return dev.ehtid.thaumcraft.port.core.LegacySmelterArcaneRecipe.BASE_VIS;
                return 0;
            }

            @Override
            public void set(int value) {
                // Server slot is read-only. Client menus use DataSlot.standalone().
            }
        };
    }

    private static DataSlot crystalMaskSlot(Container bench) {
        return new DataSlot() {
            @Override public int get() {
                if (!(bench instanceof ArcaneWorkbenchBlockEntity workbench)) return 0;
                if (workbench.matchesThaumometerPattern()) return 0b111111;
                if (workbench.matchesVisResonatorPattern())
                    return dev.ehtid.thaumcraft.port.core.VisResonatorRecipeLogic.REQUIRED_CRYSTAL_MASK;
                if (workbench.matchesVoidJarPattern())
                    return dev.ehtid.thaumcraft.port.core.LegacyWardedJarRecipes.VOID_CRYSTAL_MASK;
                Upgrade upgrade = workbench.selectedTubeUpgrade();
                if (upgrade != null) return upgrade.crystalMask;
                if (workbench.matchesBellowsPattern())
                    return LegacyBellowsArcaneRecipe.CRYSTAL_MASK;
                if (workbench.matchesAlembicPattern())
                    return LegacyAlembicArcaneRecipe.CRYSTAL_MASK;
                if (workbench.matchesAlchemyConstructPattern())
                    return LegacyAlchemyConstructArcaneRecipe.CRYSTAL_MASK;
                if (workbench.matchesAdvancedAlchemyConstructPattern())
                    return LegacyAdvancedAlchemyConstructArcaneRecipe.CRYSTAL_MASK;
                if (workbench.matchesUpgradedSmelterPattern(Kind.THAUMIUM)
                        || workbench.matchesUpgradedSmelterPattern(Kind.VOID))
                    return LegacyUpgradedSmelterArcaneRecipe.CRYSTAL_MASK;
                if (workbench.matchesAlchemyFilterPattern())
                    return LegacyAlchemyFilterArcaneRecipe.CRYSTAL_MASK;
                if (workbench.matchesBasicSmelterPattern())
                    return dev.ehtid.thaumcraft.port.core.LegacySmelterArcaneRecipe.CRYSTAL_MASK;
                return 0;
            }
            @Override public void set(int ignored) { }
        };
    }

    /** Synced aspect requirement bits: aer, ignis, aqua, terra, ordo, perditio. */
    public int requiredCrystalMask() { return requiredCrystalMask.get(); }

    /** BETA26 synced auraVisClient equivalent (property id 0). */
    public int availableVis() {
        return availableVis.get();
    }

    /** Recipe vis preview is synced independently from result-slot craftability. */
    public int displayedVisCost() {
        return displayedVisCost.get();
    }

    private boolean canCraftThaumometer(ServerPlayer player, float totalDiscount) {
        if (!(bench instanceof ArcaneWorkbenchBlockEntity workbench) || !stillValid(player)) return false;
        return workbench.canCraftThaumometer(
                player.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge(), totalDiscount);
    }

    private boolean canCraftVisResonator(ServerPlayer player, float totalDiscount) {
        if (!(bench instanceof ArcaneWorkbenchBlockEntity workbench) || !stillValid(player))
            return false;
        return workbench.canCraftVisResonator(
                player.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge(), totalDiscount);
    }

    private boolean canCraftWardedJar(ServerPlayer player, float discount) {
        if (!(bench instanceof ArcaneWorkbenchBlockEntity wb) || !stillValid(player)) return false;
        return wb.canCraftWardedJar(
                player.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge(), discount);
    }

    private boolean canCraftVoidJar(ServerPlayer player, float discount) {
        if (!(bench instanceof ArcaneWorkbenchBlockEntity wb) || !stillValid(player)) return false;
        return wb.canCraftVoidJar(
                player.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge(), discount);
    }

    private boolean canCraftEssentiaTubes(ServerPlayer player,float discount) {
        if(!(bench instanceof ArcaneWorkbenchBlockEntity wb)||!stillValid(player))
            return false;
        return wb.canCraftEssentiaTubes(
                player.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge(),discount);
    }

    private boolean craftEssentiaTubes(ServerPlayer player,float discount) {
        if(!(bench instanceof ArcaneWorkbenchBlockEntity wb)||!stillValid(player))
            return false;
        if(!wb.craftEssentiaTubes(
                player.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge(),discount))
            return false;
        refreshResult();
        broadcastChanges();
        return true;
    }

    private boolean canCraftBasicSmelter(ServerPlayer player,float discount) {
        if(!(bench instanceof ArcaneWorkbenchBlockEntity wb)||!stillValid(player))return false;
        return wb.canCraftBasicSmelter(
                player.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge(),discount);
    }
    private boolean craftBasicSmelter(ServerPlayer player,float discount) {
        if(!(bench instanceof ArcaneWorkbenchBlockEntity wb)||!stillValid(player))return false;
        if(!wb.craftBasicSmelter(
                player.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge(),discount))
            return false;
        refreshResult();
        broadcastChanges();
        return true;
    }


    private boolean canCraftTubeUpgrade(ServerPlayer player,float discount) {
        if(!(bench instanceof ArcaneWorkbenchBlockEntity wb)||!stillValid(player))return false;
        Upgrade upgrade=wb.selectedTubeUpgrade();
        return upgrade!=null&&wb.canCraftTubeUpgrade(upgrade,
                player.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge(),discount);
    }

    private static ItemStack tubeUpgradeOutput(Upgrade type) {
        return switch(type) {
            case VALVE -> new ItemStack(ThaumcraftItems.TUBE_VALVE.get());
            case RESTRICT -> new ItemStack(ThaumcraftItems.TUBE_RESTRICT.get());
            case ONEWAY -> new ItemStack(ThaumcraftItems.TUBE_ONEWAY.get());
        };
    }

    private ItemStack craftTubeUpgrade(ServerPlayer player,ItemStack expected,float discount) {
        if(!(bench instanceof ArcaneWorkbenchBlockEntity wb)||!stillValid(player))
            return ItemStack.EMPTY;
        Upgrade type=wb.selectedTubeUpgrade();
        if(type==null)return ItemStack.EMPTY;
        ItemStack output=tubeUpgradeOutput(type);
        if(!ItemStack.isSameItemSameComponents(expected,output)
                ||!wb.craftTubeUpgrade(type,
                player.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge(),discount))
            return ItemStack.EMPTY;
        refreshResult();
        broadcastChanges();
        return output;
    }


    private boolean canCraftBellows(ServerPlayer player,float discount) {
        if(!(bench instanceof ArcaneWorkbenchBlockEntity wb)||!stillValid(player))return false;
        return wb.canCraftBellows(
                player.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge(),discount);
    }
    private boolean craftBellows(ServerPlayer player,float discount) {
        if(!(bench instanceof ArcaneWorkbenchBlockEntity wb)||!stillValid(player))return false;
        if(!wb.craftBellows(
                player.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge(),discount))
            return false;
        refreshResult();
        broadcastChanges();
        return true;
    }


    private boolean canCraftTubeBuffer(ServerPlayer player, float discount) {
        if (!(bench instanceof ArcaneWorkbenchBlockEntity wb) || !stillValid(player)) return false;
        return wb.canCraftTubeBuffer(
                player.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge(), discount);
    }

    private boolean craftTubeBuffer(ServerPlayer player, float discount) {
        if (!(bench instanceof ArcaneWorkbenchBlockEntity wb) || !stillValid(player)) return false;
        if (!wb.craftTubeBuffer(
                player.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge(), discount))
            return false;
        refreshResult();
        broadcastChanges();
        return true;
    }


    private boolean canCraftAlembic(ServerPlayer player,float discount) {
        if(!(bench instanceof ArcaneWorkbenchBlockEntity wb)||!stillValid(player))return false;
        return wb.canCraftAlembic(
                player.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge(),discount);
    }

    private boolean craftAlembic(ServerPlayer player,float discount) {
        if(!(bench instanceof ArcaneWorkbenchBlockEntity wb)||!stillValid(player))return false;
        if(!wb.craftAlembic(
                player.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge(),discount))
            return false;
        refreshResult();
        broadcastChanges();
        return true;
    }


    private boolean canCraftAlchemyFilter(ServerPlayer player,float discount) {
        if(!(bench instanceof ArcaneWorkbenchBlockEntity wb)||!stillValid(player))return false;
        return wb.canCraftAlchemyFilter(
                player.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge(),discount);
    }

    private boolean craftAlchemyFilter(ServerPlayer player,float discount) {
        if(!(bench instanceof ArcaneWorkbenchBlockEntity wb)||!stillValid(player))return false;
        if(!wb.craftAlchemyFilter(
                player.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge(),discount))
            return false;
        refreshResult();
        broadcastChanges();
        return true;
    }


    private boolean canCraftFilteredTube(ServerPlayer player,float discount) {
        if(!(bench instanceof ArcaneWorkbenchBlockEntity wb)||!stillValid(player))return false;
        return wb.canCraftFilteredTube(
                player.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge(),discount);
    }

    private boolean craftFilteredTube(ServerPlayer player,float discount) {
        if(!(bench instanceof ArcaneWorkbenchBlockEntity wb)||!stillValid(player))return false;
        if(!wb.craftFilteredTube(
                player.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge(),discount))
            return false;
        refreshResult();
        broadcastChanges();
        return true;
    }

    private boolean canCraftAlchemyConstruct(ServerPlayer player,float discount) {
        if(!(bench instanceof ArcaneWorkbenchBlockEntity wb)||!stillValid(player))return false;
        return wb.canCraftAlchemyConstruct(
                player.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge(),discount);
    }

    private boolean craftAlchemyConstruct(ServerPlayer player,float discount) {
        if(!(bench instanceof ArcaneWorkbenchBlockEntity wb)||!stillValid(player))return false;
        if(!wb.craftAlchemyConstruct(
                player.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge(),discount))return false;
        refreshResult();
        broadcastChanges();
        return true;
    }

    private boolean canCraftUpgradedSmelter(ServerPlayer player,Kind kind,float discount) {
        if(!(bench instanceof ArcaneWorkbenchBlockEntity wb)||!stillValid(player))return false;
        return wb.canCraftUpgradedSmelter(kind,
                player.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge(),discount);
    }

    private boolean craftUpgradedSmelter(ServerPlayer player,Kind kind,float discount) {
        if(!(bench instanceof ArcaneWorkbenchBlockEntity wb)||!stillValid(player))return false;
        if(!wb.craftUpgradedSmelter(kind,
                player.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge(),discount))return false;
        refreshResult();
        broadcastChanges();
        return true;
    }


    private boolean canCraftAdvancedAlchemyConstruct(ServerPlayer player,float discount) {
        if(!(bench instanceof ArcaneWorkbenchBlockEntity wb)||!stillValid(player))return false;
        return wb.canCraftAdvancedAlchemyConstruct(
                player.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge(),discount);
    }

    private boolean craftAdvancedAlchemyConstruct(ServerPlayer player,float discount) {
        if(!(bench instanceof ArcaneWorkbenchBlockEntity wb)||!stillValid(player))return false;
        if(!wb.craftAdvancedAlchemyConstruct(
                player.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge(),discount))
            return false;
        refreshResult();
        broadcastChanges();
        return true;
    }

    private boolean canCraftCurrentRecipe(ServerPlayer player, float totalDiscount) {
        return canCraftThaumometer(player, totalDiscount)
                || canCraftVisResonator(player, totalDiscount)
                || canCraftWardedJar(player, totalDiscount)
                || canCraftVoidJar(player, totalDiscount)
                || canCraftEssentiaTubes(player,totalDiscount)
                || canCraftBasicSmelter(player,totalDiscount)
                || canCraftTubeUpgrade(player,totalDiscount)
                || canCraftBellows(player,totalDiscount)
                || canCraftTubeBuffer(player,totalDiscount)
                || canCraftAlembic(player,totalDiscount)
                || canCraftAlchemyFilter(player,totalDiscount)
                || canCraftFilteredTube(player,totalDiscount)
                || canCraftAlchemyConstruct(player,totalDiscount)
                || canCraftAdvancedAlchemyConstruct(player,totalDiscount)
                || canCraftUpgradedSmelter(player,Kind.THAUMIUM,totalDiscount)
                || canCraftUpgradedSmelter(player,Kind.VOID,totalDiscount);
    }

    /** Server-authoritative FIRSTSTEPS craft endpoint; output is granted only after the atomic commit. */
    public boolean craftThaumometer(ServerPlayer player, float totalDiscount) {
        if (!(bench instanceof ArcaneWorkbenchBlockEntity workbench) || !stillValid(player)) return false;
        var knowledge = player.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge();
        if (!workbench.craftThaumometer(knowledge, totalDiscount)) return false;
        ThaumcraftNetworking.sync(player);
        refreshResult();
        broadcastChanges();
        return true;
    }

    /** Server-authoritative BETA26 vis-resonator arcane recipe endpoint. */
    public boolean craftVisResonator(ServerPlayer player, float totalDiscount) {
        if (!(bench instanceof ArcaneWorkbenchBlockEntity workbench) || !stillValid(player))
            return false;
        var knowledge = player.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge();
        if (!workbench.craftVisResonator(knowledge, totalDiscount)) return false;
        refreshResult();
        broadcastChanges();
        return true;
    }

    private boolean craftWardedJar(ServerPlayer player, float discount) {
        if (!(bench instanceof ArcaneWorkbenchBlockEntity wb) || !stillValid(player)) return false;
        if (!wb.craftWardedJar(
                player.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge(), discount))
            return false;
        refreshResult();
        broadcastChanges();
        return true;
    }

    private ItemStack craftVoidJar(ServerPlayer player, float discount) {
        if (!(bench instanceof ArcaneWorkbenchBlockEntity wb) || !stillValid(player))
            return ItemStack.EMPTY;
        ItemStack output = wb.craftVoidJar(
                player.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge(), discount);
        if (!output.isEmpty()) { refreshResult(); broadcastChanges(); }
        return output;
    }

    private ItemStack craftSelectedRecipe(ServerPlayer player, ItemStack expected,
                                          float totalDiscount) {
        if (expected.is(ThaumcraftItems.THAUMOMETER.get())
                && craftThaumometer(player, totalDiscount))
            return new ItemStack(ThaumcraftItems.THAUMOMETER.get());
        if (expected.is(ThaumcraftItems.VIS_RESONATOR.get())
                && craftVisResonator(player, totalDiscount))
            return new ItemStack(ThaumcraftItems.VIS_RESONATOR.get());
        if (expected.is(ThaumcraftItems.JAR_NORMAL.get())
                && craftWardedJar(player, totalDiscount))
            return new ItemStack(ThaumcraftItems.JAR_NORMAL.get());
        if (expected.is(ThaumcraftItems.JAR_VOID.get()))
            return craftVoidJar(player, totalDiscount);
        if (expected.is(ThaumcraftItems.TUBE.get())
                && craftEssentiaTubes(player,totalDiscount))
            return new ItemStack(ThaumcraftItems.TUBE.get(),
                    dev.ehtid.thaumcraft.port.core.LegacyEssentiaTubeRecipe.OUTPUT);
        ItemStack upgrade = craftTubeUpgrade(player,expected,totalDiscount);
        if(!upgrade.isEmpty())return upgrade;
        if(expected.is(ThaumcraftItems.TUBE_FILTER.get())
                &&craftFilteredTube(player,totalDiscount))
            return new ItemStack(ThaumcraftItems.TUBE_FILTER.get());
        if(expected.is(ThaumcraftItems.SMELTER_THAUMIUM.get())
                &&craftUpgradedSmelter(player,Kind.THAUMIUM,totalDiscount))
            return new ItemStack(ThaumcraftItems.SMELTER_THAUMIUM.get());
        if(expected.is(ThaumcraftItems.SMELTER_VOID.get())
                &&craftUpgradedSmelter(player,Kind.VOID,totalDiscount))
            return new ItemStack(ThaumcraftItems.SMELTER_VOID.get());
        if(expected.is(ThaumcraftItems.METAL_ALCHEMICAL_ADVANCED.get())
                &&craftAdvancedAlchemyConstruct(player,totalDiscount))
            return new ItemStack(ThaumcraftItems.METAL_ALCHEMICAL_ADVANCED.get());
        if(expected.is(ThaumcraftItems.METAL_ALCHEMICAL.get())
                &&craftAlchemyConstruct(player,totalDiscount))
            return new ItemStack(ThaumcraftItems.METAL_ALCHEMICAL.get(),
                    LegacyAlchemyConstructArcaneRecipe.OUTPUT);
        if(expected.is(ThaumcraftItems.FILTER.get())
                &&craftAlchemyFilter(player,totalDiscount))
            return new ItemStack(ThaumcraftItems.FILTER.get(),LegacyAlchemyFilterArcaneRecipe.OUTPUT);
        if(expected.is(ThaumcraftItems.ALEMBIC.get())
                &&craftAlembic(player,totalDiscount))
            return new ItemStack(ThaumcraftItems.ALEMBIC.get());
        if (expected.is(ThaumcraftItems.TUBE_BUFFER.get())
                && craftTubeBuffer(player, totalDiscount))
            return new ItemStack(ThaumcraftItems.TUBE_BUFFER.get());
        if(expected.is(ThaumcraftItems.BELLOWS.get())
                &&craftBellows(player,totalDiscount))
            return new ItemStack(ThaumcraftItems.BELLOWS.get());
        if(expected.is(ThaumcraftItems.SMELTER_BASIC.get())
                &&craftBasicSmelter(player,totalDiscount))
            return new ItemStack(ThaumcraftItems.SMELTER_BASIC.get());
        return ItemStack.EMPTY;
    }

    private void refreshResult() {
        if (owner instanceof ServerPlayer sp && canCraftThaumometer(sp, 0.0F))
            result.setItem(0, new ItemStack(ThaumcraftItems.THAUMOMETER.get()));
        else if (owner instanceof ServerPlayer sp && canCraftVisResonator(sp, 0.0F))
            result.setItem(0, new ItemStack(ThaumcraftItems.VIS_RESONATOR.get()));
        else if (owner instanceof ServerPlayer sp && canCraftWardedJar(sp, 0.0F))
            result.setItem(0, new ItemStack(ThaumcraftItems.JAR_NORMAL.get()));
        else if (owner instanceof ServerPlayer sp && canCraftVoidJar(sp, 0.0F)
                && bench instanceof ArcaneWorkbenchBlockEntity wb)
            result.setItem(0, wb.previewVoidJar());
        else if (owner instanceof ServerPlayer sp && canCraftEssentiaTubes(sp,0.0F))
            result.setItem(0,new ItemStack(ThaumcraftItems.TUBE.get(),
                    dev.ehtid.thaumcraft.port.core.LegacyEssentiaTubeRecipe.OUTPUT));
        else if(owner instanceof ServerPlayer sp&&canCraftTubeUpgrade(sp,0.0F)
                &&bench instanceof ArcaneWorkbenchBlockEntity wb)
            result.setItem(0,tubeUpgradeOutput(wb.selectedTubeUpgrade()));
        else if(owner instanceof ServerPlayer sp&&canCraftFilteredTube(sp,0.0F))
            result.setItem(0,new ItemStack(ThaumcraftItems.TUBE_FILTER.get()));
        else if(owner instanceof ServerPlayer sp&&canCraftUpgradedSmelter(sp,Kind.THAUMIUM,0.0F))
            result.setItem(0,new ItemStack(ThaumcraftItems.SMELTER_THAUMIUM.get()));
        else if(owner instanceof ServerPlayer sp&&canCraftUpgradedSmelter(sp,Kind.VOID,0.0F))
            result.setItem(0,new ItemStack(ThaumcraftItems.SMELTER_VOID.get()));
        else if(owner instanceof ServerPlayer sp&&canCraftAdvancedAlchemyConstruct(sp,0.0F))
            result.setItem(0,new ItemStack(ThaumcraftItems.METAL_ALCHEMICAL_ADVANCED.get()));
        else if(owner instanceof ServerPlayer sp&&canCraftAlchemyConstruct(sp,0.0F))
            result.setItem(0,new ItemStack(ThaumcraftItems.METAL_ALCHEMICAL.get(),
                    LegacyAlchemyConstructArcaneRecipe.OUTPUT));
        else if(owner instanceof ServerPlayer sp&&canCraftAlchemyFilter(sp,0.0F))
            result.setItem(0,new ItemStack(ThaumcraftItems.FILTER.get(),LegacyAlchemyFilterArcaneRecipe.OUTPUT));
        else if (owner instanceof ServerPlayer sp && canCraftAlembic(sp,0.0F))
            result.setItem(0,new ItemStack(ThaumcraftItems.ALEMBIC.get()));
        else if (owner instanceof ServerPlayer sp && canCraftTubeBuffer(sp, 0.0F))
            result.setItem(0, new ItemStack(ThaumcraftItems.TUBE_BUFFER.get()));
        else if(owner instanceof ServerPlayer sp&&canCraftBellows(sp,0.0F))
            result.setItem(0,new ItemStack(ThaumcraftItems.BELLOWS.get()));
        else if(owner instanceof ServerPlayer sp&&canCraftBasicSmelter(sp,0.0F))
            result.setItem(0,new ItemStack(ThaumcraftItems.SMELTER_BASIC.get()));
        else result.setItem(0, ItemStack.EMPTY);
    }

    @Override public void broadcastChanges() {
        refreshResult();
        super.broadcastChanges();
    }

    @Override public boolean stillValid(Player player) {
        return bench.stillValid(player);
    }

    private boolean hasInventorySpace(ItemStack stack) {
        for (int i = 16; i < 52; i++) {
            Slot slot = slots.get(i);
            ItemStack existing = slot.getItem();
            if (existing.isEmpty() && slot.mayPlace(stack) && slot.isActive()) return true;
            if (!existing.isEmpty() && slot.isActive() && slot.mayPlace(stack)
                    && ItemStack.isSameItemSameComponents(existing, stack)
                    && existing.getCount() < Math.min(slot.getMaxStackSize(existing), existing.getMaxStackSize()))
                return true;
        }
        return false;
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot source = slots.get(index);
        if (!source.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = source.getItem();
        ItemStack before = stack.copy();
        if (index == 0) {
            if (!(player instanceof ServerPlayer sp) || !canCraftCurrentRecipe(sp, 0.0F))
                return ItemStack.EMPTY;
            if (!hasInventorySpace(stack)) return ItemStack.EMPTY;
            // The output stack is virtual: commit whichever BETA26 recipe
            // matches the exact preview, then insert only the earned item.
            ItemStack crafted = craftSelectedRecipe(sp, before, 0.0F);
            if (crafted.isEmpty()) return ItemStack.EMPTY;
            if (!moveItemStackTo(crafted, 16, 52, true)) {
                // Defensive fallback: a concurrently filled inventory must not delete the item.
                player.drop(crafted, false);
            }
            broadcastChanges();
            return before;
        } else if (index < 16) {
            if (!moveItemStackTo(stack, 16, 52, true)) return ItemStack.EMPTY;
        } else {
            boolean moved = false;
            for (int i = 10; i < 16; i++) {
                if (slots.get(i).mayPlace(stack)
                        && moveItemStackTo(stack, i, i + 1, false)) {
                    moved = true;
                    break;
                }
            }
            if (!moved && !moveItemStackTo(stack, 1, 10, false)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) source.set(ItemStack.EMPTY);
        else source.setChanged();
        source.onTake(player, stack);
        broadcastChanges();
        return before;
    }

    @Override public void removed(Player player) {
        super.removed(player);
        bench.stopOpen(player);
    }
}
