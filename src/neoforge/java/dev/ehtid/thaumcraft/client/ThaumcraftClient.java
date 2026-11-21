package dev.ehtid.thaumcraft.client;

import dev.ehtid.thaumcraft.ThaumcraftPort;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

/** Client-only entrypoint for graphics configuration; kept separate from shared registry initialization. */
@Mod(value = ThaumcraftPort.MOD_ID, dist = Dist.CLIENT)
public final class ThaumcraftClient {
    public ThaumcraftClient(ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, ThaumcraftClientConfig.SPEC);
    }
}
