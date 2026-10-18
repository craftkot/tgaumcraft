package dev.ehtid.thaumcraft.golem.inventory;

import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import dev.ehtid.thaumcraft.golem.seal.GolemSeals.SealKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/** BETA26 SealFill default empty blacklist, projected onto sided NeoForge item capabilities. */
public final class GolemContainers {
    private GolemContainers() {}

    public static boolean loaded(ServerLevel level, SealKey key) {
        if (!level.hasChunkAt(key.pos()) || !level.hasChunkAt(key.pos().relative(key.face()))) return false;
        var state = level.getBlockState(key.pos());
        if (state.isAir()) return false;
        // Vanilla double-chest capability joins both halves. Never request its unloaded partner.
        return !(state.getBlock() instanceof ChestBlock) || state.getValue(ChestBlock.TYPE) == ChestType.SINGLE
                || level.hasChunkAt(key.pos().relative(ChestBlock.getConnectedDirection(state)));
    }

    public static IItemHandler handler(ServerLevel level, SealKey key) {
        return loaded(level, key) ? level.getCapability(Capabilities.ItemHandler.BLOCK, key.pos(), key.face()) : null;
    }

    private static EquipmentSlot firstSlot(ThaumcraftGolem golem) {
        if (!golem.getMainHandItem().isEmpty()) return EquipmentSlot.MAINHAND;
        return golem.design().stats().carrySlots() > 1 && !golem.getOffhandItem().isEmpty()
                ? EquipmentSlot.OFFHAND : null;
    }

    public static boolean canDeposit(ServerLevel level, ThaumcraftGolem golem, SealKey key) {
        if (!loaded(level, key)) return false;
        EquipmentSlot slot = firstSlot(golem);
        if (slot == null) return false;
        ItemStack stack = golem.getItemBySlot(slot);
        IItemHandler target = handler(level, key);
        return target == null || ItemHandlerHelper.insertItemStacked(target, stack.copy(), true).getCount() < stack.getCount();
    }

    /** Actual remainder, not simulate's estimate, determines how much leaves the golem. */
    public static int insertCarried(ThaumcraftGolem golem, IItemHandler target) {
        if (golem.level().isClientSide) return 0;
        EquipmentSlot slot = firstSlot(golem);
        if (slot == null) return 0;
        ItemStack extracted = golem.getItemBySlot(slot).copy();
        golem.setItemSlot(slot, ItemStack.EMPTY);
        ItemStack remainder = ItemHandlerHelper.insertItemStacked(target, extracted.copy(), false);
        golem.setItemSlot(slot, remainder.copy());
        return extracted.getCount() - remainder.getCount();
    }

    public static int deposit(ServerLevel level, ThaumcraftGolem golem, SealKey key) {
        if (!loaded(level, key)) return 0;
        IItemHandler target = handler(level, key); // Re-resolve at completion; do not cache a removed container.
        if (target != null) return insertCarried(golem, target);
        EquipmentSlot slot = firstSlot(golem);
        if (slot == null) return 0;
        ItemStack stack = golem.getItemBySlot(slot).copy();
        ItemEntity drop = new ItemEntity(level, key.pos().getX() + .5 + key.face().getStepX(),
                key.pos().getY() + .5 + key.face().getStepY(),
                key.pos().getZ() + .5 + key.face().getStepZ(), stack);
        drop.setDeltaMovement(drop.getDeltaMovement().multiply(.2, .5, .2));
        if (!level.addFreshEntity(drop)) return 0;
        golem.setItemSlot(slot, ItemStack.EMPTY);
        return stack.getCount();
    }
}
