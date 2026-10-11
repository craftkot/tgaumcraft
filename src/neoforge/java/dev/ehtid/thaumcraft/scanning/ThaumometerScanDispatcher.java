package dev.ehtid.thaumcraft.scanning;

import dev.ehtid.thaumcraft.network.ThaumcraftNetworking;
import dev.ehtid.thaumcraft.player.ThaumcraftPlayerAttachments;
import dev.ehtid.thaumcraft.item.CrystalEssenceItem;
import dev.ehtid.thaumcraft.item.CelestialNotesItem;
import dev.ehtid.thaumcraft.registry.ThaumcraftItems;
import dev.ehtid.thaumcraft.port.core.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Server-authoritative bridge from the real Thaumometer target to the loader-neutral
 * BETA26 ScanAspect/ScanGeneric pipeline.
 *
 * Aspect extraction is intentionally supplied by the caller: the full 1.12 object-tag
 * compatibility table is a separate porting layer and must not be guessed here.
 */
public final class ThaumometerScanDispatcher {
    private static final AspectRegistry ASPECTS = new AspectRegistry();
    private static final ResearchCategoryRegistry CATEGORIES = BuiltInResearchCategories.create(ASPECTS);
    private static final ScanningService<PlayerKnowledgeData, CriticalScanHandlers.Target> SCANS =
            CriticalScanHandlers.createCriticalRegistry(ASPECTS, CATEGORIES);
    // Compiled once, not recreated for each Thaumometer ray/highlight tick.
    private static final java.util.Map<String, TagKey<net.minecraft.world.item.Item>> COMMON_ITEM_TAG_KEYS =
            buildCommonItemTagKeys();

    private static java.util.Map<String, TagKey<net.minecraft.world.item.Item>> buildCommonItemTagKeys() {
        java.util.Map<String, TagKey<net.minecraft.world.item.Item>> tags = new java.util.LinkedHashMap<>();
        for (String id : LegacyOreDictionaryScans.modernTagNames()) {
            tags.put(id, TagKey.create(Registries.ITEM, ResourceLocation.parse(id)));
        }
        for (String id : LegacyOreAspectTable.modernTagNames()) {
            tags.putIfAbsent(id, TagKey.create(Registries.ITEM, ResourceLocation.parse(id)));
        }
        for (String id : LegacyDyeAspectBonuses.modernTagNames()) {
            tags.putIfAbsent(id, TagKey.create(Registries.ITEM, ResourceLocation.parse(id)));
        }
        return java.util.Map.copyOf(tags);
    }


    private ThaumometerScanDispatcher() {}

    public static boolean scan(ServerPlayer player) {
        Object target = ThaumometerTargeting.findScanTarget(player.level(), player);
        return scan(player, target, aspectsFor(player, target));
    }

    public static boolean scan(ServerPlayer player, AspectListData scannedAspects) {
        Object target = ThaumometerTargeting.findScanTarget(player.level(), player);
        return scan(player, target, scannedAspects);
    }

    private static boolean scan(ServerPlayer player, Object target, AspectListData scannedAspects) {
        // BETA26 ScanSky is registered last and uniquely accepts null targets:
        // an unobstructed Thaumometer ray towards the actual sky.
        if (target == null) return scanSky(player);

        boolean anySuccess = false;
        String genericKey = genericKey(player, target);
        java.util.Set<String> specialKeys = specialKeys(player, target);
        boolean hasGeneric = genericKey != null && scannedAspects != null && scannedAspects.size() > 0;
        if (hasGeneric || !specialKeys.isEmpty()) {
            PlayerKnowledgeData knowledge = player.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge();
            var result = SCANS.scanTheThing(knowledge,
                    new CriticalScanHandlers.Target(genericKey,
                            scannedAspects == null ? new AspectListData() : scannedAspects,
                            specialKeys),
                    (ignored, key) -> knowledge.addResearch(key));

            // BETA26 ScanningManager feedback is an action-bar TextComponentString:
            // §5§o tc.unknownobject or §a§o tc.knownobject. Any unkeyed success
            // suppresses both messages.
            if (result.feedback() == ScanningService.Feedback.UNKNOWN_OBJECT) {
                player.displayClientMessage(
                        Component.translatable("tc.unknownobject")
                                .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC),
                        true);
            } else if (result.feedback() == ScanningService.Feedback.KNOWN_OBJECT) {
                player.displayClientMessage(
                        Component.translatable("tc.knownobject")
                                .withStyle(ChatFormatting.GREEN, ChatFormatting.ITALIC),
                        true);
            }

            if (result.anySuccess()) {
                anySuccess = true;
                ThaumcraftNetworking.sync(player);
            }
        }

        // BETA26 ScanningManager always probes an UP-side item handler after a
        // BlockPos scan, even when the block itself yielded no successful scan.
        if (target instanceof BlockPos pos) anySuccess |= scanContainer(player, pos);
        return anySuccess;
    }

    /**
     * Original BETA26 ScanSky: no sky research before CELESTIALSCANNING,
     * day-aligned sun / moon-phase observations or night-facing star notes.
     * A scribing tool must be in the main inventory; only paper is consumed.
     *
     * Deliberately does NOT implement the requested final-polish randomization
     * of uncollected moon notes. Original phase dependence remains intact.
     */
    private static boolean scanSky(ServerPlayer player) {
        PlayerKnowledgeData knowledge =
                player.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge();
        SkyScanLogic.Geometry geometry = SkyScanLogic.check(
                true, player.getXRot(),
                player.level().canSeeSky(player.blockPosition().above()),
                player.level().dimension().equals(Level.OVERWORLD),
                knowledge.isResearchKnown("CELESTIALSCANNING"),
                player.getYRot(), player.level().getTimeOfDay(0.0F));

        boolean hasTools = hasMainInventoryItem(player, ThaumcraftItems.SCRIBING_TOOLS.get());
        boolean hasPaper = hasMainInventoryItem(player, Items.PAPER);
        var result = LegacyCelestialScanService.examine(
                geometry, player.level().getGameTime(), player.level().getMoonPhase(),
                player.getDirection().get3DDataValue(), knowledge.getResearchList(),
                hasTools, hasPaper);
        if (result.status() == LegacyCelestialScanService.Status.NOT_SCANNABLE) {
            return false;
        }
        if (result.status() == LegacyCelestialScanService.Status.ALREADY_SEEN) {
            player.displayClientMessage(
                    Component.translatable("tc.celestial.fail.1"), true);
            return true;
        }
        if (result.status() == LegacyCelestialScanService.Status.NEEDS_MATERIALS) {
            player.displayClientMessage(
                    Component.translatable("tc.celestial.fail.2"), true);
        } else {
            // InventoryUtils.consumePlayerItem checks the main 36 slots in
            // inventory order, consuming exactly one vanilla paper.
            if (consumeMainInventoryPaper(player)) {
                ItemStack note = CelestialNotesItem.create(
                        result.observation().itemMetadata());
                if (!player.getInventory().add(note)) player.drop(note, false);
                knowledge.addResearch(result.observation().researchKey());
                ThaumcraftNetworking.sync(player);
            } else {
                player.displayClientMessage(
                        Component.translatable("tc.celestial.fail.2"), true);
            }
        }
        // BETA26 ScanSky.onSuccess calls cleanResearch after eligible attempts
        // even when paper/tools are missing, but not for already-seen notes.
        LegacyCelestialScanService.removeOldDayResearch(
                knowledge, result.observation().dayPrefix());
        ThaumcraftNetworking.sync(player);
        return true;
    }

    private static boolean hasMainInventoryItem(ServerPlayer player,
                                                net.minecraft.world.item.Item item) {
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty() && stack.is(item)) return true;
        }
        return false;
    }

    private static boolean consumeMainInventoryPaper(ServerPlayer player) {
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty() && stack.is(Items.PAPER)) {
                stack.shrink(1);
                return true;
            }
        }
        return false;
    }

    public static boolean isStillScannable(Player player, Object target) {
        if (target == null) return false;
        AspectListData scannedAspects = aspectsFor(player, target);
        String genericKey = genericKey(player, target);
        java.util.Set<String> specialKeys = specialKeys(player, target);
        boolean hasGeneric = genericKey != null && scannedAspects != null && scannedAspects.size() > 0;
        if (!hasGeneric && specialKeys.isEmpty()) return false;

        PlayerKnowledgeData knowledge = player.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge();
        return SCANS.isThingStillScannable(
                knowledge,
                new CriticalScanHandlers.Target(genericKey,
                        scannedAspects == null ? new AspectListData() : scannedAspects,
                        specialKeys),
                PlayerKnowledgeData::isResearchKnown);
    }

    /**
     * Merge the exact BETA26 built-in item scan registrations with the four
     * BETA26 OreDictionary material scanners translated to NeoForge item tags.
     * Multiple matching ore forms grant the same research key only once.
     */
    private static java.util.Set<String> matchingCommonItemTags(ItemStack stack,
                                                             java.util.List<String> tagIds) {
        if (stack == null || stack.isEmpty()) return java.util.Set.of();
        java.util.Set<String> matches = new java.util.HashSet<>();
        for (String tagId : tagIds) {
            TagKey<net.minecraft.world.item.Item> key = COMMON_ITEM_TAG_KEYS.get(tagId);
            if (key != null && stack.is(key)) matches.add(tagId);
        }
        return matches;
    }

    private static java.util.Set<String> itemSpecialKeys(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return java.util.Set.of();
        java.util.Set<String> keys = new java.util.LinkedHashSet<>(
                LegacyVanillaSpecialScans.forItem(
                        BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()));
        keys.addAll(LegacyOreDictionaryScans.fromCommonTags(
                matchingCommonItemTags(stack, LegacyOreDictionaryScans.modernTagNames())));

        // ConfigResearch creates ScanEnchantment handlers for 1.12 enchantment
        // registry entries. Old EnchantmentHelper.getEnchantments includes
        // enchanted-book stored enchantments as well as regular item enchants.
        java.util.Set<String> enchanted = new java.util.HashSet<>();
        // Since 1.21 enchantments live in data components, not a stack
        // EnchantmentHelper.getEnchantments method.
        ItemEnchantments applied = stack.get(DataComponents.ENCHANTMENTS);
        if (applied != null) collectEnchantmentKeys(enchanted, applied);
        ItemEnchantments stored = stack.get(DataComponents.STORED_ENCHANTMENTS);
        if (stored != null) collectEnchantmentKeys(enchanted, stored);
        keys.addAll(LegacyEnchantmentEffectScans.forItemEnchantments(enchanted));

        // ScanPotion checks PotionUtils.getEffectsFromStack for ItemStack
        // and the active MobEffect instances for living entities.
        PotionContents potion = stack.get(DataComponents.POTION_CONTENTS);
        if (potion != null) {
            java.util.Set<String> effects = new java.util.HashSet<>();
            for (MobEffectInstance effect : potion.getAllEffects()) {
                effect.getEffect().unwrapKey().ifPresent(key ->
                        effects.add(key.location().toString()));
            }
            keys.addAll(LegacyEnchantmentEffectScans.forPotionEffects(effects));
        }
        return java.util.Set.copyOf(keys);
    }

    private static void collectEnchantmentKeys(java.util.Set<String> into,
                                               ItemEnchantments enchantments) {
        for (var enchantment : enchantments.keySet()) {
            enchantment.unwrapKey().ifPresent(key -> into.add(key.location().toString()));
        }
    }

    /** Modern data-component equivalent of BETA26's ench NBT iteration. */
    private static java.util.List<LegacyEnchantmentAspectBonuses.Enchantment>
    itemEnchantmentBonuses(ItemStack stack) {
        // Enchanted books use StoredEnchantments in original 1.12; ordinary
        // equipment uses its live enchantment NBT. Do not count both twice.
        ItemEnchantments applied = stack.get(DataComponents.ENCHANTMENTS);
        ItemEnchantments stored = stack.get(DataComponents.STORED_ENCHANTMENTS);
        ItemEnchantments enchants = stack.is(Items.ENCHANTED_BOOK) && stored != null
                ? stored : applied;
        if (enchants == null || enchants.isEmpty()) return java.util.List.of();

        java.util.List<LegacyEnchantmentAspectBonuses.Enchantment> out =
                new java.util.ArrayList<>();
        for (var holder : enchants.keySet()) {
            var key = holder.unwrapKey();
            if (key.isEmpty()) continue;
            String id = key.get().location().toString();
            var rarity = LegacyEnchantmentAspectBonuses.oldVanillaRarity(id);
            if (rarity == null) {
                // Preserve modded-enchantment fallback using modern rarity
                // when no canonical 1.12 vanilla rarity exists.
                rarity = LegacyEnchantmentAspectBonuses.Rarity.fromWeight(
                        holder.value().definition().weight());
            }
            out.add(new LegacyEnchantmentAspectBonuses.Enchantment(
                    id, enchants.getLevel(holder), rarity));
        }
        return java.util.List.copyOf(out);
    }

    private static java.util.Set<String> specialKeys(Player player, Object target) {
        if (target instanceof ItemStack stack) return itemSpecialKeys(stack);
        if (target instanceof ItemEntity itemEntity) return itemSpecialKeys(itemEntity.getItem());
        if (target instanceof Entity entity) {
            java.util.Set<String> keys = new java.util.LinkedHashSet<>(
                    LegacyVanillaSpecialScans.forEntity(
                            BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString()));
            // BETA26 ScanPotion checks active potion effects on EntityLivingBase.
            if (entity instanceof LivingEntity living) {
                java.util.Set<String> effects = new java.util.HashSet<>();
                for (MobEffectInstance effect : living.getActiveEffects()) {
                    effect.getEffect().unwrapKey().ifPresent(key ->
                            effects.add(key.location().toString()));
                }
                keys.addAll(LegacyEnchantmentEffectScans.forPotionEffects(effects));
            }
            return java.util.Set.copyOf(keys);
        }
        if (target instanceof BlockPos pos) {
            java.util.Set<String> keys = new java.util.LinkedHashSet<>(
                    LegacyVanillaSpecialScans.forBlock(
                            BuiltInRegistries.BLOCK.getKey(
                                    player.level().getBlockState(pos).getBlock()).toString()));
            // Original ScanOreDictionary.getItemFromParms resolves a block pick
            // stack before querying its OreDictionary ids; preserve that route.
            keys.addAll(itemSpecialKeys(scanningStackFor(player, pos)));
            return java.util.Set.copyOf(keys);
        }
        return java.util.Set.of();
    }

    private static AspectListData aspectsFor(Player player, Object target) {
        if (target instanceof ItemStack stack) return objectAspects(stack);
        if (target instanceof ItemEntity itemEntity) return objectAspects(itemEntity.getItem());
        // BETA26 AspectHelper special-cases EntityPlayer before looking up any
        // registered EntityTags: the name hash deterministically picks 3 aspects.
        if (target instanceof Player scannedPlayer) {
            return LegacyPlayerEntityAspectTable.get(
                    ASPECTS, scannedPlayer.getGameProfile().getName());
        }
        if (target instanceof Entity entity) {
            String id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
            String legacy = LegacyEntityAspectTable.legacyName(id);
            if (legacy == null) return new AspectListData();
            boolean powered = entity instanceof Creeper creeper && creeper.isPowered();
            boolean elder = entity instanceof ElderGuardian;
            return LegacyEntityAspectTable.get(ASPECTS, legacy, powered, elder);
        }
        if (target instanceof BlockPos pos) {
            return objectAspects(scanningStackFor(player, pos));
        }
        return new AspectListData();
    }

    /**
     * The original crucible and thaumometer BOTH use
     * ThaumcraftCraftingManager.getObjectTags, including contained crystal
     * aspects and enchantment bonuses. Reuse one exact resolver.
     */
    public static AspectListData aspectsForCrucibleItem(ItemStack stack) {
        return objectAspects(stack);
    }

    private static AspectListData objectAspects(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return new AspectListData();
        // BETA26 ThaumcraftCraftingManager.getBonusTags overrides the registered
        // item tags with IEssentiaContainerItem.getAspects for crystal essence.
        // This is what lets the Thaumometer discover its NBT-contained aspect.
        if (stack.is(ThaumcraftItems.CRYSTAL_ESSENCE.get())) {
            return LegacyObjectAspectFinalizer.finish(
                    CrystalEssenceItem.legacyContainedAspects(stack, ASPECTS));
        }
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        AspectListData direct = LegacyObjectAspectTable.get(ASPECTS, itemId);
        if (direct.size() == 0) {
            // Original ore-name registrations also apply to tagged modded items.
            direct = LegacyOreAspectTable.fromCommonTags(
                    ASPECTS, matchingCommonItemTags(stack, LegacyOreAspectTable.modernTagNames()));
        }
        // TC6 also merges Sensus for the sixteen historical dye ore names,
        // then equipment-derived bonuses using AspectList.merge (max).
        AspectListData withDye = LegacyDyeAspectBonuses.apply(ASPECTS, direct, itemId,
                !matchingCommonItemTags(stack, LegacyDyeAspectBonuses.modernTagNames()).isEmpty());
        AspectListData equipped = LegacyEquipmentAspectBonuses.apply(ASPECTS, withDye, itemId);
        // BETA26 getBonusTags processes the vanilla/mapped enchant aspect
        // bonuses last, including rarity-dependent Praecantatio.
        AspectListData enchanted = LegacyEnchantmentAspectBonuses.apply(
                ASPECTS, equipped, itemEnchantmentBonuses(stack));
        // BETA26 getBonusTags culls to seven aspect TYPES before
        // getObjectTags caps each aspect QUANTITY at 500.
        return LegacyObjectAspectFinalizer.finish(enchanted);
    }

    static String genericKey(Player player, Object target) {
        if (target instanceof ItemStack stack) return genericItemKey(stack);
        // BETA26 ScanGeneric treats EntityItem as an item, not as an entity.
        if (target instanceof ItemEntity itemEntity) return genericItemKey(itemEntity.getItem());
        // Vanilla 1.12 EntityList did not register EntityPlayer. Consequently
        // ScanGeneric concatenated the null ResourceLocation as literal "!null"
        // rather than a modern synthetic minecraft:player registry key.
        if (target instanceof Player) return GenericScanKeyLogic.forEntity(null);
        if (target instanceof Entity entity) {
            return GenericScanKeyLogic.forEntity(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
        }
        if (target instanceof BlockPos pos) {
            // BETA26 ScanningManager.getItemFromParms first asks the block for its
            // pick/clone stack, then falls back to water/lava buckets. ScanGeneric
            // keys the resulting ITEM registry id rather than the block id.
            ItemStack stack = scanningStackFor(player, pos);
            return stack.isEmpty() ? null : GenericScanKeyLogic.forModernItem(
                    BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                    stack.isDamageableItem(), 0);
        }
        return null;
    }

    private static boolean scanContainer(ServerPlayer player, BlockPos pos) {
        IItemHandler handler = player.level().getCapability(Capabilities.ItemHandler.BLOCK, pos, Direction.UP);
        if (handler == null) return false;

        boolean anySuccess = false;
        int nonEmpty = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack == null || stack.isEmpty()) continue;

            anySuccess |= scan(player, stack, objectAspects(stack));
            nonEmpty++;
            if (nonEmpty >= 100) {
                player.displayClientMessage(
                        Component.translatable("tc.invtoolarge")
                                .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC),
                        true);
                break;
            }
        }
        return anySuccess;
    }

    /**
     * BETA26 ScanningManager.rayTrace uses a fixed four-block ray and asks blocks
     * for their pick stack with that hit result.
     */
    private static BlockHitResult scanPickHit(Player player) {
        Vec3 start = player.getEyePosition(0.0F);
        Vec3 end = start.add(player.getViewVector(0.0F).scale(4.0D));
        return player.level().clip(new ClipContext(
                start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));
    }

    private static ItemStack scanningStackFor(Player player, BlockPos pos) {
        BlockState state = player.level().getBlockState(pos);
        ItemStack stack;
        try {
            stack = state.getCloneItemStack(scanPickHit(player), player.level(), pos, player);
        } catch (Exception ignored) {
            stack = ItemStack.EMPTY;
        }
        if (!stack.isEmpty()) return stack;

        // Exact BETA26 ScanningManager fallback for non-pickable water/lava blocks.
        if (state.getFluidState().is(FluidTags.WATER)) return new ItemStack(Items.WATER_BUCKET);
        if (state.getFluidState().is(FluidTags.LAVA)) return new ItemStack(Items.LAVA_BUCKET);
        return ItemStack.EMPTY;
    }

    // ScanGeneric builds the key from the picked ItemStack's damage metadata,
    // NOT from the scanned block's orientation. Modern pick stacks expose no
    // numeric variant metadata; forModernItem reconstructs verified 1.12 variants.

    private static String genericItemKey(ItemStack stack) {
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        // 1.21 no longer exposes the 1.12 metadata integer. Damageable stacks still follow the exact
        // legacy no-metadata branch; non-damageable compatibility metadata remains 0 until state mapping lands.
        return GenericScanKeyLogic.forModernItem(id, stack.isDamageableItem(), 0);
    }
}

