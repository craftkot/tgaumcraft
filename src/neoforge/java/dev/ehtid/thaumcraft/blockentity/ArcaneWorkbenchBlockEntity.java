package dev.ehtid.thaumcraft.blockentity;

import dev.ehtid.thaumcraft.item.CrystalEssenceItem;
import dev.ehtid.thaumcraft.port.core.ArcaneWorkbenchRuntime;
import dev.ehtid.thaumcraft.port.core.ThaumometerRecipeLogic;
import dev.ehtid.thaumcraft.port.core.VisResonatorRecipeLogic;
import dev.ehtid.thaumcraft.port.core.LegacyWardedJarRecipes;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import dev.ehtid.thaumcraft.item.PlateItem;
import dev.ehtid.thaumcraft.item.ThaumcraftNuggetItem;
import dev.ehtid.thaumcraft.port.core.LegacyEssentiaTubeRecipe;
import dev.ehtid.thaumcraft.port.core.LegacyEssentiaTubeUpgradeRecipes;
import dev.ehtid.thaumcraft.port.core.LegacyBellowsArcaneRecipe;
import dev.ehtid.thaumcraft.port.core.LegacyTubeBufferArcaneRecipe;
import dev.ehtid.thaumcraft.port.core.LegacyAlembicArcaneRecipe;
import dev.ehtid.thaumcraft.port.core.LegacyAlchemyFilterArcaneRecipe;
import dev.ehtid.thaumcraft.port.core.LegacyTubeFilterArcaneRecipe;
import dev.ehtid.thaumcraft.port.core.LegacyAlchemyConstructArcaneRecipe;
import dev.ehtid.thaumcraft.port.core.LegacyUpgradedSmelterArcaneRecipe;
import net.minecraft.tags.ItemTags;
import dev.ehtid.thaumcraft.port.core.LegacySmelterArcaneRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import dev.ehtid.thaumcraft.port.core.PlayerKnowledgeData;
import dev.ehtid.thaumcraft.registry.ThaumcraftBlockEntities;
import dev.ehtid.thaumcraft.registry.ThaumcraftItems;
import dev.ehtid.thaumcraft.world.ThaumcraftAuraSavedData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Server-owned, persisted BETA26 workbench inventory:
 * crafting 0..8; six ordered primal Crystal Essence slots 9..14.
 * Server-authoritative vis transactions are exposed through the menu bridge.
 */
public final class ArcaneWorkbenchBlockEntity extends BlockEntity implements Container {
    private static final String TAG_VIS = "vis";

    private final NonNullList<ItemStack> items =
            NonNullList.withSize(ArcaneWorkbenchRuntime.SLOT_COUNT, ItemStack.EMPTY);
    private float vis;

    public ArcaneWorkbenchBlockEntity(BlockPos pos, BlockState state) {
        super(ThaumcraftBlockEntities.ARCANE_WORKBENCH.get(), pos, state);
    }

    /** Server-owned chunk aura snapshot used by FIRSTSTEPS arcane crafting transactions. */
    public float getVis() {
        if (level instanceof ServerLevel serverLevel) {
            ThaumcraftAuraSavedData aura = ThaumcraftAuraSavedData.get(serverLevel);
            if (aura.hasChunk(worldPosition)) {
                vis = aura.getVis(worldPosition);
                return vis;
            }
            // One-time migration from the earlier block-entity-local prototype.
            if (vis > 0.0F) {
                aura.putChunk(worldPosition, (short) Math.min(500, (int) vis), vis, 0.0F);
            }
        }
        return vis;
    }

    public void setVis(float vis) {
        if (!Float.isFinite(vis)) throw new IllegalArgumentException("vis must be finite");
        float normalized = Math.max(0.0F, vis);
        if (level instanceof ServerLevel serverLevel)
            ThaumcraftAuraSavedData.get(serverLevel).setVis(worldPosition, normalized);
        if (Float.compare(this.vis, normalized) != 0) {
            this.vis = normalized;
            setChanged();
        }
    }

    /** Atomically checks and consumes vis; failed crafts leave the stored value untouched. */
    public boolean consumeVis(float amount) {
        if (!Float.isFinite(amount) || amount < 0.0F) throw new IllegalArgumentException("amount");
        float available = getVis();
        if (available < amount) return false;
        setVis(available - amount);
        return true;
    }

    /**
     * Commits the legacy Thaumometer inputs and vis as one server-side transaction.
     * Callers must validate research separately; this method never partially consumes.
     */
    /**
     * BETA26 findMatchingArcaneRecipe() exposes recipe cost before vis/crystal/research
     * gates are applied to the actual result slot.
     */
    // Source ConfigRecipes: both WARDEDJARS recipes are paid arcane crafts.
    private static final TagKey<Item> VANILLA_GLASS_PANES = TagKey.create(
            Registries.ITEM, ResourceLocation.fromNamespaceAndPath("minecraft", "glass_panes"));
    private static final TagKey<Item> COMMON_GLASS_PANES = TagKey.create(
            Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "glass_panes"));
    private static final TagKey<Item> WOODEN_SLABS = TagKey.create(
            Registries.ITEM, ResourceLocation.fromNamespaceAndPath("minecraft", "wooden_slabs"));
    private static final TagKey<Item> COMMON_WOODEN_SLABS = TagKey.create(
            Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "slabs/wooden"));

    /** Original GWG/G G/GGG, seven panes and one wooden slab. */
    public boolean matchesWardedJarPattern() {
        boolean[] glass = new boolean[9], slab = new boolean[9], empty = new boolean[9];
        for (int i = 0; i < 9; i++) {
            ItemStack stack = items.get(i);
            glass[i] = stack.is(Items.GLASS_PANE) || stack.is(VANILLA_GLASS_PANES)
                    || stack.is(COMMON_GLASS_PANES);
            slab[i] = stack.is(WOODEN_SLABS) || stack.is(COMMON_WOODEN_SLABS);
            empty[i] = stack.isEmpty();
        }
        return LegacyWardedJarRecipes.normalPattern(glass, slab, empty);
    }

    /** Original ShapedArcaneVoidJar: one ordinary jar anywhere in the grid. */
    private int voidJarInput() {
        boolean[] occupied = new boolean[9], jar = new boolean[9];
        for (int i = 0; i < 9; i++) {
            ItemStack stack = items.get(i);
            occupied[i] = !stack.isEmpty();
            jar[i] = stack.is(ThaumcraftItems.JAR_NORMAL.get());
        }
        return LegacyWardedJarRecipes.voidJarSlot(occupied, jar);
    }

    public boolean matchesVoidJarPattern() { return voidJarInput() >= 0; }

    public boolean canCraftWardedJar(PlayerKnowledgeData knowledge, float discount) {
        return matchesWardedJarPattern()
                && knowledge.isResearchKnown(LegacyWardedJarRecipes.RESEARCH)
                && getVis() >= Math.max(0, LegacyWardedJarRecipes.discountedVis(
                LegacyWardedJarRecipes.NORMAL_VIS, discount));
    }

    public boolean craftWardedJar(PlayerKnowledgeData knowledge, float discount) {
        if (!canCraftWardedJar(knowledge, discount)) return false;
        int cost = LegacyWardedJarRecipes.discountedVis(
                LegacyWardedJarRecipes.NORMAL_VIS, discount);
        for (int i = 0; i < 9; i++) if (i != 4) items.get(i).shrink(1);
        if (cost > 0) setVis(getVis() - cost);
        else setChanged();
        return true;
    }

    public boolean canCraftVoidJar(PlayerKnowledgeData knowledge, float discount) {
        int input = voidJarInput();
        return input >= 0
                && knowledge.isResearchKnown(LegacyWardedJarRecipes.RESEARCH)
                && !items.get(LegacyWardedJarRecipes.VOID_CRYSTAL_SLOT).isEmpty()
                && canPlaceItem(LegacyWardedJarRecipes.VOID_CRYSTAL_SLOT,
                        items.get(LegacyWardedJarRecipes.VOID_CRYSTAL_SLOT))
                && getVis() >= Math.max(0, LegacyWardedJarRecipes.discountedVis(
                LegacyWardedJarRecipes.VOID_VIS, discount));
    }

    /**
     * ShapedArcaneVoidJar.getCraftingResult retains the input jar's saved
     * contents/filter instead of creating an empty jar. The original 1.12 NBT
     * is represented by the modern minecraft:block_entity_data component.
     * This preview is never allowed to consume ingredients or vis.
     */
    public ItemStack previewVoidJar() {
        int input = voidJarInput();
        if (input < 0) return ItemStack.EMPTY;
        ItemStack crafted = new ItemStack(ThaumcraftItems.JAR_VOID.get());
        CustomData saved = items.get(input).get(DataComponents.BLOCK_ENTITY_DATA);
        if (saved != null && !saved.isEmpty())
            crafted.set(DataComponents.BLOCK_ENTITY_DATA, saved);
        return crafted;
    }

    /** Atomic server-side debit; caller uses the precomputed NBT-aware output. */
    public ItemStack craftVoidJar(PlayerKnowledgeData knowledge, float discount) {
        if (!canCraftVoidJar(knowledge, discount)) return ItemStack.EMPTY;
        int input = voidJarInput();
        if (input < 0) return ItemStack.EMPTY;
        ItemStack output = previewVoidJar();
        int cost = LegacyWardedJarRecipes.discountedVis(
                LegacyWardedJarRecipes.VOID_VIS, discount);
        items.get(input).shrink(1);
        items.get(LegacyWardedJarRecipes.VOID_CRYSTAL_SLOT).shrink(1);
        if (cost > 0) setVis(getVis() - cost);
        else setChanged();
        return output;
    }

    // BETA26 original eight-tube arcane recipe (not ordinary 3x3 crafting).
    private static final TagKey<Item> COMMON_GLASS_BLOCKS = TagKey.create(
            Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "glass_blocks"));
    private static final TagKey<Item> FORGE_GLASS_BLOCKS = TagKey.create(
            Registries.ITEM, ResourceLocation.fromNamespaceAndPath("forge", "glass"));
    private static final TagKey<Item> COMMON_IRON_PLATES = TagKey.create(
            Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "plates/iron"));

    public boolean matchesEssentiaTubePattern() {
        boolean[] quicksilver=new boolean[9], iron=new boolean[9],
                  glass=new boolean[9], brass=new boolean[9], empty=new boolean[9];
        for(int slot=0;slot<9;slot++) {
            ItemStack stack=items.get(slot);
            int nugget=ThaumcraftNuggetItem.legacyMetadata(stack);
            quicksilver[slot]=nugget==LegacyEssentiaTubeRecipe.QUICKSILVER_NUGGET_META;
            brass[slot]=nugget==LegacyEssentiaTubeRecipe.BRASS_NUGGET_META;
            iron[slot]=isIronPlate(stack) || stack.is(COMMON_IRON_PLATES);
            glass[slot]=stack.is(Items.GLASS) || stack.is(COMMON_GLASS_BLOCKS)
                    || stack.is(FORGE_GLASS_BLOCKS);
            empty[slot]=stack.isEmpty();
        }
        return LegacyEssentiaTubeRecipe.matches(quicksilver,iron,glass,brass,empty);
    }

    public boolean canCraftEssentiaTubes(PlayerKnowledgeData knowledge,float discount) {
        return matchesEssentiaTubePattern()
                && knowledge.isResearchKnown(LegacyEssentiaTubeRecipe.RESEARCH)
                && getVis() >= Math.max(0, LegacyEssentiaTubeRecipe.discountedVis(discount));
    }

    public boolean craftEssentiaTubes(PlayerKnowledgeData knowledge,float discount) {
        if(!canCraftEssentiaTubes(knowledge,discount))return false;
        int cost=LegacyEssentiaTubeRecipe.discountedVis(discount);
        for(int slot:new int[]{1,3,4,5,7})items.get(slot).shrink(1);
        if(cost>0)setVis(getVis()-cost);
        else setChanged();
        return true;
    }







    /** Original BETA26 Thaumium/Void EssentiaSmelter upgrades BFB / IGI / III. */
    public boolean matchesUpgradedSmelterPattern(LegacyUpgradedSmelterArcaneRecipe.Kind kind) {
        if(kind==null)return false;
        boolean[] brass=new boolean[9],basic=new boolean[9],
                  plates=new boolean[9],construct=new boolean[9];
        final int legacyPlate=kind==LegacyUpgradedSmelterArcaneRecipe.Kind.THAUMIUM?2:3;
        final var material=kind==LegacyUpgradedSmelterArcaneRecipe.Kind.THAUMIUM
                ?ThaumcraftItems.METAL_ALCHEMICAL.get()
                :ThaumcraftItems.METAL_ALCHEMICAL_ADVANCED.get();
        for(int slot=0;slot<9;slot++) {
            ItemStack stack=items.get(slot);
            brass[slot]=PlateItem.legacyMetadata(stack)==0||stack.is(COMMON_BRASS_PLATES);
            basic[slot]=stack.is(ThaumcraftItems.SMELTER_BASIC.get());
            plates[slot]=PlateItem.legacyMetadata(stack)==legacyPlate;
            construct[slot]=stack.is(material);
        }
        return LegacyUpgradedSmelterArcaneRecipe.matches(brass,basic,plates,construct);
    }

    public boolean canCraftUpgradedSmelter(LegacyUpgradedSmelterArcaneRecipe.Kind kind,
                                           PlayerKnowledgeData knowledge,float discount) {
        if(kind==null||!matchesUpgradedSmelterPattern(kind)
                ||!knowledge.isResearchKnown(kind.research))return false;
        ItemStack ignis=items.get(LegacyUpgradedSmelterArcaneRecipe.IGNIS_SLOT);
        return ignis.getCount()>=kind.ignisCrystals
                &&canPlaceItem(LegacyUpgradedSmelterArcaneRecipe.IGNIS_SLOT,ignis)
                &&getVis()>=Math.max(0,LegacyUpgradedSmelterArcaneRecipe.discountedVis(kind,discount));
    }

    public boolean craftUpgradedSmelter(LegacyUpgradedSmelterArcaneRecipe.Kind kind,
                                        PlayerKnowledgeData knowledge,float discount) {
        if(!canCraftUpgradedSmelter(kind,knowledge,discount))return false;
        int vis=LegacyUpgradedSmelterArcaneRecipe.discountedVis(kind,discount);
        for(int slot=0;slot<9;slot++)items.get(slot).shrink(1);
        items.get(LegacyUpgradedSmelterArcaneRecipe.IGNIS_SLOT).shrink(kind.ignisCrystals);
        if(vis>0)setVis(getVis()-vis);
        else setChanged();
        return true;
    }

    /** Original two-output TUBES alchemical construct recipe IVI / TWT / IVI. */
    public boolean matchesAlchemyConstructPattern() {
        boolean[] iron=new boolean[9],valve=new boolean[9],
                  tube=new boolean[9],wood=new boolean[9];
        for(int slot=0;slot<9;slot++) {
            ItemStack stack=items.get(slot);
            iron[slot]=isIronPlate(stack);
            valve[slot]=stack.is(ThaumcraftItems.TUBE_VALVE.get());
            tube[slot]=stack.is(ThaumcraftItems.TUBE.get());
            wood[slot]=stack.is(ThaumcraftItems.PLANK_GREATWOOD.get());
        }
        return LegacyAlchemyConstructArcaneRecipe.matches(iron,valve,tube,wood);
    }

    public boolean canCraftAlchemyConstruct(PlayerKnowledgeData knowledge,float discount) {
        if(!matchesAlchemyConstructPattern()
                ||!knowledge.isResearchKnown(LegacyAlchemyConstructArcaneRecipe.RESEARCH))return false;
        for(int crystal:new int[]{LegacyAlchemyConstructArcaneRecipe.AQUA_SLOT,
                    LegacyAlchemyConstructArcaneRecipe.ORDO_SLOT,
                    LegacyAlchemyConstructArcaneRecipe.PERDITIO_SLOT}) {
            ItemStack stack=items.get(crystal);
            if(stack.isEmpty()||!canPlaceItem(crystal,stack))return false;
        }
        return getVis()>=Math.max(0,LegacyAlchemyConstructArcaneRecipe.discountedVis(discount));
    }

    public boolean craftAlchemyConstruct(PlayerKnowledgeData knowledge,float discount) {
        if(!canCraftAlchemyConstruct(knowledge,discount))return false;
        int cost=LegacyAlchemyConstructArcaneRecipe.discountedVis(discount);
        for(int slot=0;slot<9;slot++)items.get(slot).shrink(1);
        for(int crystal:new int[]{LegacyAlchemyConstructArcaneRecipe.AQUA_SLOT,
                    LegacyAlchemyConstructArcaneRecipe.ORDO_SLOT,
                    LegacyAlchemyConstructArcaneRecipe.PERDITIO_SLOT})
            items.get(crystal).shrink(1);
        if(cost>0)setVis(getVis()-cost);
        else setChanged();
        return true;
    }

    /** Shapeless BETA26 TubeFilter: one tube and one manufactured filter. */
    private int[] filteredTubeInputs() {
        boolean[] tubes=new boolean[9],filters=new boolean[9],occupied=new boolean[9];
        for(int slot=0;slot<9;slot++) {
            ItemStack stack=items.get(slot);
            tubes[slot]=stack.is(ThaumcraftItems.TUBE.get());
            filters[slot]=stack.is(ThaumcraftItems.FILTER.get());
            occupied[slot]=!stack.isEmpty();
        }
        return LegacyTubeFilterArcaneRecipe.inputSlots(tubes,filters,occupied);
    }

    public boolean matchesFilteredTubePattern() { return filteredTubeInputs()!=null; }

    public boolean canCraftFilteredTube(PlayerKnowledgeData knowledge,float discount) {
        return matchesFilteredTubePattern()
                &&knowledge.isResearchKnown(LegacyTubeFilterArcaneRecipe.RESEARCH)
                &&getVis()>=Math.max(0,LegacyTubeFilterArcaneRecipe.discountedVis(discount));
    }

    public boolean craftFilteredTube(PlayerKnowledgeData knowledge,float discount) {
        if(!canCraftFilteredTube(knowledge,discount))return false;
        int[] inputs=filteredTubeInputs();
        if(inputs==null)return false;
        int cost=LegacyTubeFilterArcaneRecipe.discountedVis(discount);
        for(int slot:inputs)items.get(slot).shrink(1);
        if(cost>0)setVis(getVis()-cost);
        else setChanged();
        return true;
    }

    /** BETA26 Filter: a horizontal 3x1 arcane recipe in any crafting grid row. */
    private int[] filterInputSlots() {
        boolean[] gold=new boolean[9],silverwood=new boolean[9],occupied=new boolean[9];
        for(int slot=0;slot<9;slot++) {
            ItemStack stack=items.get(slot);
            gold[slot]=stack.is(Items.GOLD_INGOT);
            silverwood[slot]=stack.is(ThaumcraftItems.PLANK_SILVERWOOD.get());
            occupied[slot]=!stack.isEmpty();
        }
        return LegacyAlchemyFilterArcaneRecipe.inputSlots(gold,silverwood,occupied);
    }

    public boolean matchesAlchemyFilterPattern() {
        return filterInputSlots()!=null;
    }

    public boolean canCraftAlchemyFilter(PlayerKnowledgeData knowledge,float discount) {
        ItemStack aqua=items.get(LegacyAlchemyFilterArcaneRecipe.AQUA_SLOT);
        return filterInputSlots()!=null
                &&knowledge.isResearchKnown(LegacyAlchemyFilterArcaneRecipe.RESEARCH)
                &&!aqua.isEmpty()&&canPlaceItem(LegacyAlchemyFilterArcaneRecipe.AQUA_SLOT,aqua)
                &&getVis()>=Math.max(0,LegacyAlchemyFilterArcaneRecipe.discountedVis(discount));
    }

    /** Debit only after all research, crystal, vis and shaped-input checks. */
    public boolean craftAlchemyFilter(PlayerKnowledgeData knowledge,float discount) {
        if(!canCraftAlchemyFilter(knowledge,discount))return false;
        int[] slots=filterInputSlots();
        if(slots==null)return false;
        int cost=LegacyAlchemyFilterArcaneRecipe.discountedVis(discount);
        for(int slot:slots)items.get(slot).shrink(1);
        items.get(LegacyAlchemyFilterArcaneRecipe.AQUA_SLOT).shrink(1);
        if(cost>0)setVis(getVis()-cost);
        else setChanged();
        return true;
    }

    /** Original WFW / SBS / WFW alembic recipe, preserving thaumcraft items. */
    public boolean matchesAlembicPattern() {
        boolean[] wood=new boolean[9],filter=new boolean[9],
                  brass=new boolean[9],bucket=new boolean[9];
        for(int slot=0;slot<9;slot++) {
            ItemStack stack=items.get(slot);
            wood[slot]=stack.is(ThaumcraftItems.PLANK_GREATWOOD.get());
            filter[slot]=stack.is(ThaumcraftItems.FILTER.get());
            brass[slot]=PlateItem.legacyMetadata(stack)==0||stack.is(COMMON_BRASS_PLATES);
            bucket[slot]=stack.is(Items.BUCKET);
        }
        return LegacyAlembicArcaneRecipe.matches(wood,filter,brass,bucket);
    }

    public boolean canCraftAlembic(PlayerKnowledgeData knowledge,float discount) {
        ItemStack aqua=items.get(LegacyAlembicArcaneRecipe.AQUA_SLOT);
        return matchesAlembicPattern()
                &&knowledge.isResearchKnown(LegacyAlembicArcaneRecipe.RESEARCH)
                &&!aqua.isEmpty()&&canPlaceItem(LegacyAlembicArcaneRecipe.AQUA_SLOT,aqua)
                &&getVis()>=Math.max(0,LegacyAlembicArcaneRecipe.discountedVis(discount));
    }

    /** All conditions are checked before the inventory and aura debit. */
    public boolean craftAlembic(PlayerKnowledgeData knowledge,float discount) {
        if(!canCraftAlembic(knowledge,discount))return false;
        int cost=LegacyAlembicArcaneRecipe.discountedVis(discount);
        for(int slot=0;slot<9;slot++)items.get(slot).shrink(1);
        items.get(LegacyAlembicArcaneRecipe.AQUA_SLOT).shrink(1);
        if(cost>0)setVis(getVis()-cost);
        else setChanged();
        return true;
    }

    /** Source ConfigRecipes TubeBuffer: PVP / TWT / PRP on the paid arcane bench. */
    public boolean matchesTubeBufferPattern() {
        boolean[] phial = new boolean[9], valve = new boolean[9],
                  tube = new boolean[9], iron = new boolean[9], restrict = new boolean[9];
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = items.get(slot);
            // Empty original ItemPhial metadata=0 has no essentia NBT.
            // Filled or corrupt/custom phials must not be consumed as empty ones.
            var phialModel = stack.get(DataComponents.CUSTOM_MODEL_DATA);
            phial[slot] = stack.is(ThaumcraftItems.PHIAL.get())
                    && !stack.has(DataComponents.CUSTOM_DATA)
                    && (phialModel == null || phialModel.equals(new net.minecraft.world.item.component.CustomModelData(0)));
            valve[slot] = stack.is(ThaumcraftItems.TUBE_VALVE.get());
            tube[slot] = stack.is(ThaumcraftItems.TUBE.get());
            iron[slot] = isIronPlate(stack);
            restrict[slot] = stack.is(ThaumcraftItems.TUBE_RESTRICT.get());
        }
        return LegacyTubeBufferArcaneRecipe.matches(phial, valve, tube, iron, restrict);
    }

    public boolean canCraftTubeBuffer(PlayerKnowledgeData knowledge, float discount) {
        return matchesTubeBufferPattern()
                && knowledge.isResearchKnown(LegacyTubeBufferArcaneRecipe.RESEARCH)
                && getVis() >= Math.max(0, LegacyTubeBufferArcaneRecipe.discountedVis(discount));
    }

    /** Nine distinct inputs, no crystals; craft commits only after every gate. */
    public boolean craftTubeBuffer(PlayerKnowledgeData knowledge, float discount) {
        if (!canCraftTubeBuffer(knowledge, discount)) return false;
        int cost = LegacyTubeBufferArcaneRecipe.discountedVis(discount);
        for (int slot = 0; slot < 9; slot++) items.get(slot).shrink(1);
        if (cost > 0) setVis(getVis() - cost);
        else setChanged();
        return true;
    }

    /** Original BELLOWS shaped arcane craft, with mirrored pattern. */
    public boolean matchesBellowsPattern() {
        boolean[] plank=new boolean[9],leather=new boolean[9],
                  iron=new boolean[9],empty=new boolean[9];
        for(int slot=0;slot<9;slot++) {
            ItemStack stack=items.get(slot);
            plank[slot]=stack.is(ItemTags.PLANKS);
            leather[slot]=stack.is(Items.LEATHER);
            iron[slot]=stack.is(Items.IRON_INGOT);
            empty[slot]=stack.isEmpty();
        }
        return LegacyBellowsArcaneRecipe.matches(plank,leather,iron,empty);
    }

    public boolean canCraftBellows(PlayerKnowledgeData knowledge,float discount) {
        ItemStack aer=items.get(LegacyBellowsArcaneRecipe.CRYSTAL_SLOT);
        return matchesBellowsPattern()
                &&knowledge.isResearchKnown(LegacyBellowsArcaneRecipe.RESEARCH)
                &&!aer.isEmpty()&&canPlaceItem(LegacyBellowsArcaneRecipe.CRYSTAL_SLOT,aer)
                &&getVis()>=Math.max(0,LegacyBellowsArcaneRecipe.discountedVis(discount));
    }

    public boolean craftBellows(PlayerKnowledgeData knowledge,float discount) {
        if(!canCraftBellows(knowledge,discount))return false;
        for(int slot=0;slot<9;slot++)if(!items.get(slot).isEmpty())items.get(slot).shrink(1);
        items.get(LegacyBellowsArcaneRecipe.CRYSTAL_SLOT).shrink(1);
        int cost=LegacyBellowsArcaneRecipe.discountedVis(discount);
        if(cost>0)setVis(getVis()-cost);
        else setChanged();
        return true;
    }

    /** BETA26 TubeValve, TubeRestrict and TubeOneway remain true arcane recipes. */
    private int[] tubeUpgradeInputs(LegacyEssentiaTubeUpgradeRecipes.Upgrade kind) {
        boolean[] tube=new boolean[9],lever=new boolean[9],occupied=new boolean[9];
        for(int i=0;i<9;i++) {
            ItemStack stack=items.get(i);
            occupied[i]=!stack.isEmpty();
            tube[i]=stack.is(ThaumcraftItems.TUBE.get());
            lever[i]=stack.is(Items.LEVER);
        }
        return LegacyEssentiaTubeUpgradeRecipes.match(kind,tube,lever,occupied);
    }

    public boolean matchesTubeUpgradePattern(LegacyEssentiaTubeUpgradeRecipes.Upgrade kind) {
        return tubeUpgradeInputs(kind)!=null;
    }

    /** The plain tube has two possible crystal upgrades; active crystal selects output. */
    public LegacyEssentiaTubeUpgradeRecipes.Upgrade selectedTubeUpgrade() {
        var valve=LegacyEssentiaTubeUpgradeRecipes.Upgrade.VALVE;
        if(matchesTubeUpgradePattern(valve))return valve;
        var restrict=LegacyEssentiaTubeUpgradeRecipes.Upgrade.RESTRICT;
        if(!matchesTubeUpgradePattern(restrict))return null;
        var oneway=LegacyEssentiaTubeUpgradeRecipes.Upgrade.ONEWAY;
        ItemStack terra=items.get(restrict.crystalSlot),aqua=items.get(oneway.crystalSlot);
        if(!terra.isEmpty()&&canPlaceItem(restrict.crystalSlot,terra))return restrict;
        if(!aqua.isEmpty()&&canPlaceItem(oneway.crystalSlot,aqua))return oneway;
        return null;
    }

    public boolean canCraftTubeUpgrade(LegacyEssentiaTubeUpgradeRecipes.Upgrade kind,
                                       PlayerKnowledgeData knowledge,float discount) {
        if(kind==null||kind!=selectedTubeUpgrade()
                ||!knowledge.isResearchKnown(LegacyEssentiaTubeUpgradeRecipes.RESEARCH))return false;
        if(kind.crystalSlot>=0) {
            ItemStack stack=items.get(kind.crystalSlot);
            if(stack.isEmpty()||!canPlaceItem(kind.crystalSlot,stack))return false;
        }
        return getVis()>=Math.max(0,LegacyEssentiaTubeUpgradeRecipes.discountedVis(discount));
    }

    /** Validates all conditions before debiting vis or shrinking inventory. */
    public boolean craftTubeUpgrade(LegacyEssentiaTubeUpgradeRecipes.Upgrade kind,
                                    PlayerKnowledgeData knowledge,float discount) {
        if(!canCraftTubeUpgrade(kind,knowledge,discount))return false;
        int[] slots=tubeUpgradeInputs(kind);
        if(slots==null)return false;
        items.get(slots[0]).shrink(1);
        if(slots[1]>=0)items.get(slots[1]).shrink(1);
        if(kind.crystalSlot>=0)items.get(kind.crystalSlot).shrink(1);
        int cost=LegacyEssentiaTubeUpgradeRecipes.discountedVis(discount);
        if(cost>0)setVis(getVis()-cost);
        else setChanged();
        return true;
    }

    private static final TagKey<Item> COMMON_BRASS_PLATES=TagKey.create(
            Registries.ITEM,ResourceLocation.fromNamespaceAndPath("c","plates/brass"));
    private static final TagKey<Item> COMMON_COBBLESTONE=TagKey.create(
            Registries.ITEM,ResourceLocation.fromNamespaceAndPath("c","cobblestones"));

    /** BETA26 BCB / SFS / SSS smelter pattern; every slot is occupied. */
    public boolean matchesBasicSmelterPattern(){
        boolean[] brass=new boolean[9],crucible=new boolean[9],
                  furnace=new boolean[9],cobble=new boolean[9];
        for(int i=0;i<9;i++){
            ItemStack stack=items.get(i);
            brass[i]=PlateItem.legacyMetadata(stack)==0||stack.is(COMMON_BRASS_PLATES);
            crucible[i]=stack.is(ThaumcraftItems.CRUCIBLE.get());
            furnace[i]=stack.is(Items.FURNACE);
            cobble[i]=stack.is(Items.COBBLESTONE)||stack.is(COMMON_COBBLESTONE);
        }
        return LegacySmelterArcaneRecipe.matches(brass,crucible,furnace,cobble);
    }

    public boolean canCraftBasicSmelter(PlayerKnowledgeData knowledge,float discount){
        return matchesBasicSmelterPattern()
                &&knowledge.isResearchKnown(LegacySmelterArcaneRecipe.RESEARCH)
                &&!items.get(LegacySmelterArcaneRecipe.IGNIS_SLOT).isEmpty()
                &&canPlaceItem(LegacySmelterArcaneRecipe.IGNIS_SLOT,
                        items.get(LegacySmelterArcaneRecipe.IGNIS_SLOT))
                &&getVis()>=Math.max(0,LegacySmelterArcaneRecipe.discountedVis(discount));
    }
    public boolean craftBasicSmelter(PlayerKnowledgeData knowledge,float discount){
        if(!canCraftBasicSmelter(knowledge,discount))return false;
        int cost=LegacySmelterArcaneRecipe.discountedVis(discount);
        for(int i=0;i<9;i++)items.get(i).shrink(1);
        items.get(LegacySmelterArcaneRecipe.IGNIS_SLOT).shrink(1);
        if(cost>0)setVis(getVis()-cost);else setChanged();
        return true;
    }

    public boolean matchesThaumometerPattern() {
        for (int slot = 0; slot < ArcaneWorkbenchRuntime.CRAFT_SLOTS; slot++) {
            ItemStack stack = items.get(slot);
            if (slot == 4) {
                if (!stack.is(net.minecraft.world.item.Items.GLASS_PANE)) return false;
            } else if (slot == 1 || slot == 3 || slot == 5 || slot == 7) {
                if (!stack.is(net.minecraft.world.item.Items.GOLD_INGOT)) return false;
            } else if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    // 1.12 OreDictionary "plateIron" and "gemQuartz" have no single item
    // identities in 1.21. NeoForge's community tags preserve modded plate
    // ingredients; ordinary iron ingots MUST NOT satisfy plateIron.
    private static final TagKey<Item> C_IRON_PLATES = TagKey.create(
            Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "plates/iron"));
    private static final TagKey<Item> FORGE_IRON_PLATES = TagKey.create(
            Registries.ITEM, ResourceLocation.fromNamespaceAndPath("forge", "plates/iron"));
    private static final TagKey<Item> C_QUARTZ_GEMS = TagKey.create(
            Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "gems/quartz"));
    private static final TagKey<Item> FORGE_QUARTZ_GEMS = TagKey.create(
            Registries.ITEM, ResourceLocation.fromNamespaceAndPath("forge", "gems/quartz"));

    private static boolean isIronPlate(ItemStack stack) {
        // Do not mistake brass, thaumium or void plate for iron: a
        // shared item ID requires checking original metadata first.
        return PlateItem.legacyMetadata(stack) == 1
                || stack.is(C_IRON_PLATES) || stack.is(FORGE_IRON_PLATES);
    }

    private static boolean isQuartzGem(ItemStack stack) {
        return stack.is(Items.QUARTZ) || stack.is(C_QUARTZ_GEMS)
                || stack.is(FORGE_QUARTZ_GEMS);
    }

    /**
     * The original ShapelessOreRecipe accepts the two ingredients in ANY
     * 3x3 positions and requires all other grid slots empty. This also
     * resolves ambiguous modded items tagged both iron plate and quartz.
     */
    private int[] visResonatorInputSlots() {
        boolean[] occupied = new boolean[ArcaneWorkbenchRuntime.CRAFT_SLOTS];
        boolean[] plates = new boolean[ArcaneWorkbenchRuntime.CRAFT_SLOTS];
        boolean[] quartz = new boolean[ArcaneWorkbenchRuntime.CRAFT_SLOTS];
        for (int i = 0; i < ArcaneWorkbenchRuntime.CRAFT_SLOTS; i++) {
            ItemStack stack = items.get(i);
            if (stack.isEmpty()) continue;
            occupied[i] = true;
            plates[i] = isIronPlate(stack);
            quartz[i] = isQuartzGem(stack);
        }
        return VisResonatorRecipeLogic.matchGrid(occupied, plates, quartz);
    }

    public boolean matchesVisResonatorPattern() {
        return visResonatorInputSlots() != null;
    }

    /**
     * Authentic BETA26 vis_resonator arcane recipe:
     * UNLOCKAUROMANCY@2; 50 vis; one Aer + one Aqua crystal;
     * plateIron + gemQuartz as original ore-dictionary inputs.
     */
    public boolean canCraftVisResonator(PlayerKnowledgeData knowledge, float totalDiscount) {
        if (visResonatorInputSlots() == null
                || !knowledge.isResearchKnown(VisResonatorRecipeLogic.RESEARCH)) return false;
        ItemStack aer = items.get(9);
        ItemStack aqua = items.get(11);
        if (aer.isEmpty() || aqua.isEmpty()
                || !canPlaceItem(9, aer) || !canPlaceItem(11, aqua)) return false;
        int cost = VisResonatorRecipeLogic.discountedVis(totalDiscount);
        return getVis() >= Math.max(0, cost);
    }

    /** Spend all inputs and aura together on the server; failure changes nothing. */
    public boolean craftVisResonator(PlayerKnowledgeData knowledge, float totalDiscount) {
        if (!canCraftVisResonator(knowledge, totalDiscount)) return false;
        int[] inputs = visResonatorInputSlots();
        if (inputs == null) return false;
        int cost = VisResonatorRecipeLogic.discountedVis(totalDiscount);
        items.get(inputs[0]).shrink(1);
        items.get(inputs[1]).shrink(1);
        items.get(9).shrink(1);
        items.get(11).shrink(1);
        if (cost > 0) setVis(getVis() - cost);
        else setChanged();
        return true;
    }

    public boolean canCraftThaumometer(PlayerKnowledgeData knowledge, float totalDiscount) {
        if (!matchesThaumometerPattern()) return false;
        if (!knowledge.isResearchKnown(ThaumometerRecipeLogic.RESEARCH)) return false;
        int cost = ThaumometerRecipeLogic.discountedVis(totalDiscount);
        for (int slot = ArcaneWorkbenchRuntime.CRAFT_SLOTS; slot < ArcaneWorkbenchRuntime.SLOT_COUNT; slot++) {
            if (items.get(slot).isEmpty() || !canPlaceItem(slot, items.get(slot))) return false;
        }
        return getVis() >= cost;
    }

    public boolean craftThaumometer(PlayerKnowledgeData knowledge, float totalDiscount) {
        if (!canCraftThaumometer(knowledge, totalDiscount)) return false;
        int cost = ThaumometerRecipeLogic.discountedVis(totalDiscount);
        for (int slot : new int[]{1, 3, 4, 5, 7, 9, 10, 11, 12, 13, 14}) items.get(slot).shrink(1);
        // BETA26 SlotCraftingArcaneWorkbench only calls spendAura when the
        // discounted cost is strictly positive. A >100% discount therefore
        // permits the craft but must NOT add aura back to the workbench.
        if (cost > 0) setVis(getVis() - cost);
        else setChanged();
        knowledge.addResearch(ThaumometerRecipeLogic.LEGACY_CRAFT_MARKER);
        return true;
    }

    @Override
    public int getContainerSize() {
        return ArcaneWorkbenchRuntime.SLOT_COUNT;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot >= 0 && slot < items.size() ? items.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        if (slot < 0 || slot >= items.size() || count <= 0) return ItemStack.EMPTY;
        ItemStack removed = ContainerHelper.removeItem(items, slot, count);
        if (!removed.isEmpty()) setChanged();
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (slot < 0 || slot >= items.size()) return ItemStack.EMPTY;
        ItemStack removed = ContainerHelper.takeItem(items, slot);
        if (!removed.isEmpty()) setChanged();
        return removed;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= getContainerSize()) return;
        if (!stack.isEmpty() && !canPlaceItem(slot, stack)) return;
        items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) stack.setCount(getMaxStackSize());
        setChanged();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= getContainerSize()) return false;
        if (slot < ArcaneWorkbenchRuntime.CRAFT_SLOTS) return true;
        if (stack.isEmpty()) return true;
        if (!stack.is(ThaumcraftItems.CRYSTAL_ESSENCE.get())) return false;
        String aspect = CrystalEssenceItem.legacyAspect(stack);
        return ArcaneWorkbenchRuntime.PRIMAL_ORDER[slot - ArcaneWorkbenchRuntime.CRAFT_SLOTS].equals(aspect);
    }

    @Override
    public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX() + 0.5,
                worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= 64.0;
    }

    @Override
    public void clearContent() {
        for (int slot = 0; slot < items.size(); slot++) items.set(slot, ItemStack.EMPTY);
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putFloat(TAG_VIS, vis);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ContainerHelper.loadAllItems(tag, items, registries);
        float storedVis = tag.getFloat(TAG_VIS);
        vis = Float.isFinite(storedVis) ? Math.max(0.0F, storedVis) : 0.0F;
    }
}
