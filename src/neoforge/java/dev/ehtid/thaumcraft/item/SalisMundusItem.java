package dev.ehtid.thaumcraft.item;

import dev.ehtid.thaumcraft.network.ThaumcraftNetworking;
import dev.ehtid.thaumcraft.player.ThaumcraftPlayerAttachments;
import dev.ehtid.thaumcraft.registry.ThaumcraftSounds;
import dev.ehtid.thaumcraft.port.core.FirstStepsBootstrapLogic;
import dev.ehtid.thaumcraft.port.core.FirstStepsServerProgression;
import dev.ehtid.thaumcraft.port.core.PlayerKnowledgeData;
import dev.ehtid.thaumcraft.world.SalisMundusTransformQueue;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

/** BETA26 ItemMagicDust: FIRSTSTEPS and original UNLOCKALCHEMY crucible dust triggers. */
public final class SalisMundusItem extends Item {
    public SalisMundusItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        if (!player.mayUseItemAt(context.getClickedPos(), context.getClickedFace(), stack)) return InteractionResult.FAIL;
        if (player.isShiftKeyDown()) return InteractionResult.PASS;

        PlayerKnowledgeData knowledge = player.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge();
        String blockId = BuiltInRegistries.BLOCK.getKey(context.getLevel().getBlockState(context.getClickedPos()).getBlock()).toString();
        FirstStepsBootstrapLogic.DustTarget target = FirstStepsBootstrapLogic.dustTarget(knowledge, blockId);
        if (target == FirstStepsBootstrapLogic.DustTarget.NONE) return InteractionResult.PASS;

        player.swing(context.getHand(), true);
        if (context.getLevel() instanceof ServerLevel level && player instanceof ServerPlayer serverPlayer) {
            // Client knowledge may lag behind the server after the wake trigger.
            // BETA26 plays dust only for a valid use, and only to the actor.
            // Send an actor-only vanilla sound packet AFTER the authoritative
            // DustTarget gate; a client-local gate could silently miss the sound.
            var pos = context.getClickedPos();
            float pitch = 1.0F + (float) level.getRandom().nextGaussian() * 0.05F;
            serverPlayer.connection.send(new ClientboundSoundPacket(
                    BuiltInRegistries.SOUND_EVENT.wrapAsHolder(ThaumcraftSounds.DUST.get()),
                    SoundSource.PLAYERS, pos.getX(), pos.getY(), pos.getZ(),
                    0.33F, pitch, level.getRandom().nextLong()));
            // BETA26 ItemMagicDust consumes the dust before DustTrigger.execute() fires the crafting event
            // and queues the 50-tick swap. Keep that order even though normal execution cannot fail here.
            if (!player.getAbilities().instabuild) stack.shrink(1);
            // DustTriggerSimple/Ore fires PlayerCraftingEvent immediately, before its 50-tick swap.
            if (target == FirstStepsBootstrapLogic.DustTarget.ARCANE_WORKBENCH_BLOCK
                    && knowledge.addResearch(FirstStepsServerProgression.ARCANE_WORKBENCH_MARKER)) {
                ThaumcraftNetworking.sync(serverPlayer);
            }
            SalisMundusTransformQueue.schedule(level, context.getClickedPos(), level.getBlockState(context.getClickedPos()),
                    serverPlayer, target);
            if (target == FirstStepsBootstrapLogic.DustTarget.THAUMONOMICON_ITEM) {
                dev.ehtid.thaumcraft.util.ThaumcraftSmokeAudit.mark("BOOKSHELF_SALIS_USED");
            }
        }
        return InteractionResult.SUCCESS;
    }
}
