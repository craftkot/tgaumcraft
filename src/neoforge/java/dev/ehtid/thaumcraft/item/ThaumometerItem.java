package dev.ehtid.thaumcraft.item;

import dev.ehtid.thaumcraft.client.ThaumometerRuneFx;
import dev.ehtid.thaumcraft.network.ThaumcraftNetworking;
import dev.ehtid.thaumcraft.registry.ThaumcraftSounds;
import dev.ehtid.thaumcraft.scanning.ThaumometerIdleHighlight;
import dev.ehtid.thaumcraft.scanning.ThaumometerScanDispatcher;
import dev.ehtid.thaumcraft.world.ThaumcraftAuraSavedData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Server-authoritative FIRSTSTEPS Thaumometer interaction.
 * Target selection and scan rewards remain in the BETA26 parity dispatcher.
 */
public final class ThaumometerItem extends Item {
    public ThaumometerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // BETA26 always played the scan sound client-side at 0.5 volume / 1.0 pitch,
        // independently from whether ScanningManager actually discovered something.
        if (level.isClientSide()) {
            ThaumometerRuneFx.draw(level, player);
            level.playLocalSound(player.getX(), player.getY(), player.getZ(),
                    ThaumcraftSounds.SCAN.get(), SoundSource.PLAYERS, 0.5F, 1.0F, false);
        } else if (player instanceof ServerPlayer serverPlayer) {
            ThaumometerScanDispatcher.scan(serverPlayer);
        }
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);

        // ItemThaumometer#onUpdate in BETA26 used (isSelected || itemSlot == 0).
        boolean active = isSelected || slotId == 0;
        if (!active) return;

        if (level.isClientSide()) {
            if (entity instanceof Player player && entity.tickCount % 5 == 0)
                ThaumometerIdleHighlight.tick(level, player);
            return;
        }

        // Every 20 ticks on the server BETA26 sent PacketAuraToClient(base, vis, flux).
        if (entity.tickCount % 20 != 0) return;
        if (!(entity instanceof ServerPlayer player) || !(level instanceof ServerLevel serverLevel)) return;

        var aura = ThaumcraftAuraSavedData.get(serverLevel).getChunk(player.blockPosition());
        if (aura != null) ThaumcraftNetworking.sendAura(player, aura);
    }
}
