package dev.ehtid.thaumcraft.client;

import dev.ehtid.thaumcraft.block.CrystalClusterBlock;
import dev.ehtid.thaumcraft.port.core.LegacyCrystalMeshSelection;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The BETA26 CrystalModel shuffles all eight original OBJ shard groups for
 * every position seed, then displays exactly (size+1) groups. This wrapper
 * reuses eight independently baked original OBJ groups, not rewritten cubes.
 *
 * Geometry is currently in the source model's UP orientation; six-face
 * support/rotation parity is a separate follow-up. Missing shard models or
 * absence of world ModelData retain the preceding visible static model.
 */
public final class SeededCrystalBakedModel extends BakedModelWrapper<BakedModel> {
    private static final ModelProperty<Long> POSITION_SEED = new ModelProperty<>();
    private final Map<String, BakedModel> shards;

    public SeededCrystalBakedModel(BakedModel fallback, Map<String, BakedModel> shards) {
        super(fallback);
        this.shards = Map.copyOf(shards);
        if (!this.shards.keySet().containsAll(LegacyCrystalMeshSelection.originalGroups()))
            throw new IllegalArgumentException("Missing a source BETA26 OBJ shard");
    }

    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos pos,
                                  BlockState state, ModelData existing) {
        ModelData delegated = originalModel.getModelData(level, pos, state, existing);
        // The same vanilla world position seed used by the chunk model renderer.
        // Immutable property is safe when chunk geometry is baked off-thread.
        return delegated.derive().with(POSITION_SEED, state.getSeed(pos)).build();
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side,
                                    RandomSource rand, ModelData data,
                                    @Nullable RenderType renderType) {
        Long seed = data.get(POSITION_SEED);
        if (state == null || !state.hasProperty(CrystalClusterBlock.SIZE) || seed == null)
            return originalModel.getQuads(state, side, rand, data, renderType);

        int size = state.getValue(CrystalClusterBlock.SIZE);
        List<BakedQuad> quads = new ArrayList<>();
        for (String group : LegacyCrystalMeshSelection.select(size, seed)) {
            BakedModel shard = shards.get(group);
            quads.addAll(shard.getQuads(state, side, rand, data, renderType));
        }
        return quads;
    }
}
