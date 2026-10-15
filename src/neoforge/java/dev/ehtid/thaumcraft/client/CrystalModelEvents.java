package dev.ehtid.thaumcraft.client;

import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.block.CrystalClusterBlock;
import dev.ehtid.thaumcraft.port.core.LegacyCrystalMeshSelection;
import dev.ehtid.thaumcraft.registry.ThaumcraftBlocks;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bake-time, reload-safe integration of the ORIGINAL eight Crystal OBJ groups. */
@EventBusSubscriber(modid = ThaumcraftPort.MOD_ID, bus = EventBusSubscriber.Bus.MOD,
                    value = Dist.CLIENT)
public final class CrystalModelEvents {
    private CrystalModelEvents() {}

    private static ModelResourceLocation shardLocation(String group) {
        return ModelResourceLocation.standalone(
                ResourceLocation.fromNamespaceAndPath(ThaumcraftPort.MOD_ID,
                        "block/crystal_shard_" + group));
    }

    @SubscribeEvent
    public static void registerShardModels(ModelEvent.RegisterAdditional event) {
        for (String group : LegacyCrystalMeshSelection.originalGroups())
            event.register(shardLocation(group));
    }

    @SubscribeEvent
    public static void installSeededModels(ModelEvent.ModifyBakingResult event) {
        var baked = event.getModels();
        Map<String, BakedModel> parts = new LinkedHashMap<>();
        for (String group : LegacyCrystalMeshSelection.originalGroups()) {
            BakedModel model = baked.get(shardLocation(group));
            if (model == null) return; // never replace valid geometry with missing quads
            parts.put(group, model);
        }
        List<Block> crystals = List.of(
                ThaumcraftBlocks.CRYSTAL_AER.get(),
                ThaumcraftBlocks.CRYSTAL_IGNIS.get(),
                ThaumcraftBlocks.CRYSTAL_AQUA.get(),
                ThaumcraftBlocks.CRYSTAL_TERRA.get(),
                ThaumcraftBlocks.CRYSTAL_ORDO.get(),
                ThaumcraftBlocks.CRYSTAL_PERDITIO.get(),
                ThaumcraftBlocks.CRYSTAL_VITIUM.get());
        // NeoForge stores a separate state->baked-model entry for every
        // combination of source BETA26 size and generation. Changing only the
        // sixteenth default model would leave 15 states using fixed shards.
        for (Block crystal : crystals)
            for (int size = 0; size <= 3; size++)
                for (int generation = 1; generation <= 4; generation++) {
                    var state = crystal.defaultBlockState()
                            .setValue(CrystalClusterBlock.SIZE, size)
                            .setValue(CrystalClusterBlock.GENERATION, generation);
                    var location = BlockModelShaper.stateToModelLocation(state);
                    baked.computeIfPresent(location, (key, prior) ->
                            new SeededCrystalBakedModel(prior, parts));
                }
    }
}
