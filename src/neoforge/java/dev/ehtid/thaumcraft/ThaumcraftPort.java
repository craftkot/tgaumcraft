package dev.ehtid.thaumcraft;

import dev.ehtid.thaumcraft.network.ThaumcraftNetworking;
import dev.ehtid.thaumcraft.player.ThaumcraftPlayerAttachments;
import dev.ehtid.thaumcraft.player.FirstStepsPlayerEvents;
import dev.ehtid.thaumcraft.player.ThaumcraftPlayerEvents;
import dev.ehtid.thaumcraft.registry.ThaumcraftBlockEntities;
import dev.ehtid.thaumcraft.registry.ThaumcraftBlocks;
import dev.ehtid.thaumcraft.registry.ThaumcraftItems;
import dev.ehtid.thaumcraft.registry.ThaumcraftEntities;
import dev.ehtid.thaumcraft.registry.ThaumcraftCreativeTabs;
import dev.ehtid.thaumcraft.registry.ThaumcraftMenus;
import dev.ehtid.thaumcraft.registry.ThaumcraftParticles;
import dev.ehtid.thaumcraft.registry.ThaumcraftRecipes;
import dev.ehtid.thaumcraft.registry.ThaumcraftSounds;
import dev.ehtid.thaumcraft.world.SalisMundusTransformQueue;
import dev.ehtid.thaumcraft.world.ThaumcraftAuraWorldEvents;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(ThaumcraftPort.MOD_ID)
public final class ThaumcraftPort {
    public static final String MOD_ID = "thaumcraft";

    public ThaumcraftPort(IEventBus modBus) {
        ThaumcraftBlocks.register(modBus);
        ThaumcraftBlockEntities.register(modBus);
        ThaumcraftMenus.register(modBus);
        ThaumcraftItems.register(modBus);
        ThaumcraftEntities.register(modBus);
        dev.ehtid.thaumcraft.golem.GolemModule.register(modBus);
        ThaumcraftCreativeTabs.register(modBus);
        ThaumcraftParticles.register(modBus);
        ThaumcraftRecipes.register(modBus);
        ThaumcraftSounds.register(modBus);
        ThaumcraftPlayerAttachments.register(modBus);
        modBus.addListener(ThaumcraftNetworking::registerPayloads);

        NeoForge.EVENT_BUS.addListener(ThaumcraftPlayerEvents::onClone);
        NeoForge.EVENT_BUS.addListener(ThaumcraftPlayerEvents::onLogin);
        NeoForge.EVENT_BUS.addListener(ThaumcraftPlayerEvents::onRespawn);
        NeoForge.EVENT_BUS.addListener(ThaumcraftPlayerEvents::onDimensionChange);
        NeoForge.EVENT_BUS.addListener(FirstStepsPlayerEvents::onItemPickup);
        NeoForge.EVENT_BUS.addListener(FirstStepsPlayerEvents::onWake);
        NeoForge.EVENT_BUS.addListener(FirstStepsPlayerEvents::onCrafted);
        NeoForge.EVENT_BUS.addListener(SalisMundusTransformQueue::onServerTick);
        NeoForge.EVENT_BUS.addListener(SalisMundusTransformQueue::onServerStopped);
        NeoForge.EVENT_BUS.addListener(ThaumcraftAuraWorldEvents::onServerTick);
    }
}
