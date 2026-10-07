package dev.ehtid.thaumcraft.player;

import dev.ehtid.thaumcraft.network.ThaumcraftNetworking;
import dev.ehtid.thaumcraft.port.core.BuiltInResearchCatalog;
import dev.ehtid.thaumcraft.port.core.LegacyPlayerTransfer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Player lifecycle bridge. Mutation call sites should use ThaumcraftNetworking.sync after changing state. */
public final class ThaumcraftPlayerEvents {
    private ThaumcraftPlayerEvents() {}

    public static void onClone(PlayerEvent.Clone event) {
        if (!(event.getEntity() instanceof ServerPlayer target)) return;
        LegacyPlayerTransfer.cloneViaLegacyPersistence(
                event.getOriginal().getData(ThaumcraftPlayerAttachments.PLAYER_DATA),
                target.getData(ThaumcraftPlayerAttachments.PLAYER_DATA),
                BuiltInResearchCatalog.create());
    }

    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) ThaumcraftNetworking.sync(player);
    }

    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) ThaumcraftNetworking.sync(player);
    }

    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) ThaumcraftNetworking.sync(player);
    }
}
