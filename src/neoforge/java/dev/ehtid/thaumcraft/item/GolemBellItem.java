package dev.ehtid.thaumcraft.item;

import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.golem.menu.GolemSealMenu;
import dev.ehtid.thaumcraft.golem.seal.GolemSeals;
import dev.ehtid.thaumcraft.port.core.golem.GolemBellRules;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** BETA26 bell block use: open a face's seal, or sneak-remove and drop its exact item. */
public final class GolemBellItem extends Item {
    public GolemBellItem(Properties properties) { super(properties); }

    @Override public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) { return useOn(context); }

    @Override public InteractionResult useOn(UseOnContext context) {
        var player = context.getPlayer();
        if (player == null || !(context.getLevel() instanceof ServerLevel level)) return InteractionResult.PASS;
        var key = new GolemSeals.SealKey(context.getClickedPos(), context.getClickedFace());
        Vec3 hit = context.getClickLocation();
        if (!level.hasChunkAt(key.pos()) || !level.hasChunkAt(key.pos().relative(key.face()))
                || !new AABB(key.pos()).inflate(.001).contains(hit)
                || player.getEyePosition().distanceToSqr(hit) > GolemBellRules.SEAL_REACH_SQUARED
                || player.isSpectator() || !player.isAlive() || !player.mayInteract(level, key.pos())
                || !player.mayUseItemAt(key.pos(), key.face(), context.getItemInHand())) return InteractionResult.FAIL;
        var data = GolemSeals.get(level);
        var seal = data.get(key);
        if (seal == null) return InteractionResult.PASS;
        if (!seal.owner().equals(player.getUUID())) return InteractionResult.FAIL;
        if (player.isShiftKeyDown()) {
            ItemStack returned = new ItemStack(switch (seal.type()) {
                case PICKUP -> GolemModule.PICKUP_SEAL.get(); case FILL -> GolemModule.FILL_SEAL.get();
                case EMPTY -> GolemModule.EMPTY_SEAL.get();
            });
            Vec3 pos = Vec3.atCenterOf(key.pos()).add(key.face().getStepX()/1.7, key.face().getStepY()/1.7, key.face().getStepZ()/1.7);
            ItemEntity drop = new ItemEntity(level, pos.x, pos.y, pos.z, returned);
            if (!level.addFreshEntity(drop)) return InteractionResult.FAIL;
            if (!data.remove(seal, player.getUUID())) { drop.discard(); return InteractionResult.FAIL; }
            level.playSound(null, key.pos(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, .5F, 1F);
        } else {
            var menu = new GolemSealMenu(0, player.getInventory(), level, seal);
            if (!menu.stillValid(player)) return InteractionResult.FAIL;
            player.openMenu(new SimpleMenuProvider((id, inventory, viewer) -> new GolemSealMenu(id, inventory, level, seal),
                    Component.translatable("menu.thaumcraft.golem_seal.title")));
        }
        player.swing(context.getHand(), true);
        return InteractionResult.CONSUME;
    }
}
