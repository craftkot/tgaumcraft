package dev.ehtid.thaumcraft.scanning;

import dev.ehtid.thaumcraft.network.ThaumcraftNetworking;
import dev.ehtid.thaumcraft.player.ThaumcraftPlayerAttachments;
import dev.ehtid.thaumcraft.port.core.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

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

    public static boolean scan(ServerPlayer player, AspectListData scannedAspects) {
        Object target = ThaumometerTargeting.findScanTarget(player.level(), player);
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

    static String genericKey(ServerPlayer player, Object target) {
        if (target instanceof Entity entity) {
            return GenericScanKeyLogic.forEntity(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
        }
        if (target instanceof BlockPos pos) {
            var state = player.level().getBlockState(pos);
            return GenericScanKeyLogic.forItem(
                    BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(), false, 0);
        }
        return null;
    }
}
