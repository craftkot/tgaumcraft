package dev.ehtid.thaumcraft.scanning;

import dev.ehtid.thaumcraft.network.ThaumcraftNetworking;
import dev.ehtid.thaumcraft.player.ThaumcraftPlayerAttachments;
import dev.ehtid.thaumcraft.port.core.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.core.Direction;

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
        if (target == null || scannedAspects == null || scannedAspects.size() == 0) return false;

        String genericKey = genericKey(player, target);
        if (genericKey == null) return false;
        PlayerKnowledgeData knowledge = player.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge();
        var result = SCANS.scanTheThing(knowledge,
                new CriticalScanHandlers.Target(genericKey, scannedAspects),
                (ignored, key) -> knowledge.addResearch(key));
        if (result.anySuccess()) ThaumcraftNetworking.sync(player);
        return result.anySuccess();
    }

    private static AspectListData aspectsFor(ServerPlayer player, Object target) {
        if (target instanceof ItemEntity itemEntity) return objectAspects(itemEntity.getItem());
        if (target instanceof Entity entity) {
            String id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
            String legacy = LegacyEntityAspectTable.legacyName(id);
            if (legacy == null) return new AspectListData();
            boolean powered = entity instanceof Creeper creeper && creeper.isPowered();
            boolean elder = entity instanceof ElderGuardian;
            return LegacyEntityAspectTable.get(ASPECTS, legacy, powered, elder);
        }
        if (target instanceof BlockPos pos) {
            ItemStack stack = new ItemStack(player.level().getBlockState(pos).getBlock());
            return objectAspects(stack);
        }
        return new AspectListData();
    }

    private static AspectListData objectAspects(ItemStack stack) {
        if (stack.isEmpty()) return new AspectListData();
        return LegacyObjectAspectTable.get(ASPECTS, BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }

    static String genericKey(ServerPlayer player, Object target) {
        // BETA26 ScanGeneric treats EntityItem as an item, not as an entity.
        if (target instanceof ItemEntity itemEntity) return genericItemKey(itemEntity.getItem());
        if (target instanceof Entity entity) {
            return GenericScanKeyLogic.forEntity(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
        }
        if (target instanceof BlockPos pos) {
            // Legacy ScanningManager.getItemFromParms(BlockPos) builds an ItemStack from the block/state,
            // then ScanGeneric keys the ITEM registry id (not the block registry id).
            ItemStack stack = new ItemStack(player.level().getBlockState(pos).getBlock());
            return stack.isEmpty() ? null : GenericScanKeyLogic.forItem(
                    BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                    stack.isDamageableItem(), legacyBlockMetadata(player.level().getBlockState(pos)));
        }
        return null;
    }

    /**
     * Only project metadata where the legacy numeric encoding is known.
     * Unmapped modern states deliberately fall back to zero rather than inventing
     * a metadata value based on arbitrary property names.
     */
    private static int legacyBlockMetadata(BlockState state) {
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            Direction direction = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
            return switch (direction) {
                case NORTH -> 2;
                case SOUTH -> 3;
                case WEST -> 4;
                case EAST -> 5;
                default -> 0;
            };
        }
        return 0;
    }

    private static String genericItemKey(ItemStack stack) {
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        // 1.21 no longer exposes the 1.12 metadata integer. Damageable stacks still follow the exact
        // legacy no-metadata branch; non-damageable compatibility metadata remains 0 until state mapping lands.
        return GenericScanKeyLogic.forItem(id, stack.isDamageableItem(), 0);
    }
}

