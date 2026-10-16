package dev.ehtid.thaumcraft.item;

import dev.ehtid.thaumcraft.blockentity.InfusionMatrixBlockEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

/**
 * Original BETA26 ItemCaster#onItemUseFirst -> IInteractWithCaster.
 *
 * This is the BASIC gauntlet's source-era interaction with the matrix,
 * not an implementation of foci or other caster effects. The first use
 * activates a valid multiblock, the second attempts research-gated
 * real infusion craftingStart using the current pedestal inventories.
 * The original TileInfusionMatrix.onCasterRightClick does not debit Vis.
 */
public final class BasicCasterItem extends Item {
    public BasicCasterItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        if (!player.mayUseItemAt(context.getClickedPos(), context.getClickedFace(), stack))
            return InteractionResult.FAIL;
        if (!(context.getLevel().getBlockEntity(context.getClickedPos())
                instanceof InfusionMatrixBlockEntity matrix))
            return InteractionResult.PASS;
        if (context.getLevel().isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.FAIL;
        // A caster click is harmless on invalid/incomplete structures.
        // Never run both activation and recipe start in one interaction.
        if (!matrix.active()) {
            matrix.tryActivateFromCaster();
        } else if (!matrix.crafting()) {
            matrix.beginVerifiedCrystalFarmerFromFutureCaster(serverPlayer);
        }
        return InteractionResult.SUCCESS;
    }
}
