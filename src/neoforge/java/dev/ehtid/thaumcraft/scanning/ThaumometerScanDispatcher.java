package dev.ehtid.thaumcraft.scanning;

import dev.ehtid.thaumcraft.network.ThaumcraftNetworking;
import dev.ehtid.thaumcraft.player.ThaumcraftPlayerAttachments;
import dev.ehtid.thaumcraft.port.core.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
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
        if (target == null) return false;

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

    private static java.util.Set<String> specialKeys(Player player, Object target) {
        if (target instanceof ItemStack stack) {
            return stack.isEmpty() ? java.util.Set.of()
                    : LegacyVanillaSpecialScans.forItem(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        }
        if (target instanceof ItemEntity itemEntity) {
            ItemStack stack = itemEntity.getItem();
            return stack.isEmpty() ? java.util.Set.of()
                    : LegacyVanillaSpecialScans.forItem(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        }
        if (target instanceof Entity entity) {
            return LegacyVanillaSpecialScans.forEntity(
                    BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
        }
        if (target instanceof BlockPos pos) {
            return LegacyVanillaSpecialScans.forBlock(
                    BuiltInRegistries.BLOCK.getKey(player.level().getBlockState(pos).getBlock()).toString());
        }
        return java.util.Set.of();
    }

    private static AspectListData aspectsFor(Player player, Object target) {
        if (target instanceof ItemStack stack) return objectAspects(stack);
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
            return objectAspects(scanningStackFor(player, pos));
        }
        return new AspectListData();
    }

    private static AspectListData objectAspects(ItemStack stack) {
        if (stack.isEmpty()) return new AspectListData();
        return LegacyObjectAspectTable.get(ASPECTS, BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }

    static String genericKey(Player player, Object target) {
        if (target instanceof ItemStack stack) return genericItemKey(stack);
        // BETA26 ScanGeneric treats EntityItem as an item, not as an entity.
        if (target instanceof ItemEntity itemEntity) return genericItemKey(itemEntity.getItem());
        if (target instanceof Entity entity) {
            return GenericScanKeyLogic.forEntity(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
        }
        if (target instanceof BlockPos pos) {
            // BETA26 ScanningManager.getItemFromParms first asks the block for its
            // pick/clone stack, then falls back to water/lava buckets. ScanGeneric
            // keys the resulting ITEM registry id rather than the block id.
            ItemStack stack = scanningStackFor(player, pos);
            return stack.isEmpty() ? null : GenericScanKeyLogic.forItem(
                    BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                    stack.isDamageableItem(), legacyBlockMetadata(player.level().getBlockState(pos)));
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
        return GenericScanKeyLogic.forModernItem(id, stack.isDamageableItem(), 0);
    }
}

