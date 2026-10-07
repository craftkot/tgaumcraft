package dev.ehtid.thaumcraft.player;

import dev.ehtid.thaumcraft.network.ThaumcraftNetworking;
import dev.ehtid.thaumcraft.port.core.FirstStepsBootstrapLogic;
import dev.ehtid.thaumcraft.port.core.FirstStepsServerProgression;
import dev.ehtid.thaumcraft.port.core.PlayerKnowledgeData;
import dev.ehtid.thaumcraft.port.core.ThaumometerRecipeLogic;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;

/**
 * Server-only FIRSTSTEPS bootstrap hooks.  These deliberately mutate the player attachment only
 * after the authoritative NeoForge event has happened, then push the full legacy-compatible state.
 */
public final class FirstStepsPlayerEvents {
    private FirstStepsPlayerEvents() {}

    public static void onItemPickup(ItemEntityPickupEvent.Post event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        String id = itemId(event.getOriginalStack());
        PlayerKnowledgeData knowledge = player.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge();
        boolean changed = switch (id) {
            case "thaumcraft:crystal_essence" -> FirstStepsBootstrapLogic.onCrystalPickup(knowledge);
            case "thaumcraft:thaumonomicon" -> FirstStepsBootstrapLogic.onThaumonomiconPickup(knowledge);
            default -> false;
        };
        if (changed) ThaumcraftNetworking.sync(player);
    }

    public static void onWake(PlayerWakeUpEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PlayerKnowledgeData knowledge = player.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge();
        if (FirstStepsBootstrapLogic.onWake(knowledge)) ThaumcraftNetworking.sync(player);
    }

    /** BETA26 records the arcane-workbench craft hash when the transformed result is crafted. */
    public static void onCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        String id = itemId(event.getCrafting());
        PlayerKnowledgeData knowledge = player.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge();
        String marker = switch (id) {
            case "thaumcraft:arcane_workbench" -> FirstStepsServerProgression.ARCANE_WORKBENCH_MARKER;
            case "thaumcraft:thaumometer" -> ThaumometerRecipeLogic.LEGACY_CRAFT_MARKER;
            default -> null;
        };
        if (marker != null && knowledge.addResearch(marker)) ThaumcraftNetworking.sync(player);
    }

    private static String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }
}
