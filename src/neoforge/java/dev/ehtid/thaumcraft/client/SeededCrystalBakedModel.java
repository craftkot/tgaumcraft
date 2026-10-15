package dev.ehtid.thaumcraft.client;

import dev.ehtid.thaumcraft.block.CrystalClusterBlock;
import dev.ehtid.thaumcraft.port.core.LegacyCrystalMeshSelection;
import dev.ehtid.thaumcraft.port.core.LegacyCrystalMeshSelection.Face;
import dev.ehtid.thaumcraft.port.core.LegacyCrystalMeshSelection.Point;
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
 * Source-era CrystalModel# getQuads replacement:
 * - all six independent support faces (not just the floor);
 * - source-specific Java Random(positionSeed + 5*faceIndex);
 * - size+1 of the exact eight source OBJ groups for each supported side;
 * - identical original MeshModel.rotate axis/translation geometry.
 *
 * No guesses on the world data: the rendering support test is exactly the
 * same predicate used by CrystalClusterBlock.canSurvive.
 */
public final class SeededCrystalBakedModel extends BakedModelWrapper<BakedModel> {
    private static final ModelProperty<Long> POSITION_SEED = new ModelProperty<>();
    private static final ModelProperty<List<Direction>> SUPPORT_FACES = new ModelProperty<>();
    private final Map<String, BakedModel> shards;

    public SeededCrystalBakedModel(BakedModel fallback, Map<String, BakedModel> shards) {
        super(fallback);
        this.shards = Map.copyOf(shards);
        if (!this.shards.keySet().containsAll(LegacyCrystalMeshSelection.originalGroups()))
            throw new IllegalArgumentException("Missing original BETA26 OBJ shard");
    }

    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos pos,
                                  BlockState state, ModelData existing) {
        ModelData delegated = originalModel.getModelData(level, pos, state, existing);
        ArrayList<Direction> attachments = new ArrayList<>();
        for (Direction direction : Direction.values())
            if (CrystalClusterBlock.hasCrystalSupport(level, pos, direction))
                attachments.add(direction);
        return delegated.derive()
                .with(POSITION_SEED, state.getSeed(pos))
                .with(SUPPORT_FACES, List.copyOf(attachments))
                .build();
    }

    private static Face originalFace(Direction dir) {
        return switch (dir) {
            case UP -> Face.UP;
            case DOWN -> Face.DOWN;
            case EAST -> Face.EAST;
            case WEST -> Face.WEST;
            case NORTH -> Face.NORTH;
            case SOUTH -> Face.SOUTH;
        };
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side,
                                    RandomSource random, ModelData data,
                                    @Nullable RenderType renderType) {
        Long seed = data.get(POSITION_SEED);
        List<Direction> supports = data.get(SUPPORT_FACES);
        if (state == null || !state.hasProperty(CrystalClusterBlock.SIZE)
                || seed == null || supports == null)
            return originalModel.getQuads(state, side, random, data, renderType);
        // The original BETA26 CrystalModel emitted only unculled general quads.
        if (side != null || supports.isEmpty()) return List.of();

        int size = state.getValue(CrystalClusterBlock.SIZE);
        ArrayList<BakedQuad> quads = new ArrayList<>();
        for (Direction direction : supports) {
            Face face = originalFace(direction);
            for (String group : LegacyCrystalMeshSelection.selectForFace(size, seed, face)) {
                BakedModel shard = shards.get(group);
                for (BakedQuad quad : shard.getQuads(state, null, random, data, renderType))
                    quads.add(rotateQuad(quad, face));
            }
        }
        return quads;
    }

    /**
     * Vanilla's baked quad vertex array is four interleaved vertices.
     * Position float bits occupy the first 3 int slots; the packed normal
     * is the final slot for the standard BLOCK format (stride 8).
     * Preserve the original UVs, vertex colors, sprite and tint index.
     */
    private static BakedQuad rotateQuad(BakedQuad original, Face face) {
        int[] pixels = original.getVertices().clone();
        int stride = pixels.length / 4;
        if (pixels.length % 4 != 0 || stride < 3) return original;
        for (int vertex = 0; vertex < 4; vertex++) {
            int offset = vertex * stride;
            Point p = new Point(
                    Float.intBitsToFloat(pixels[offset]),
                    Float.intBitsToFloat(pixels[offset + 1]),
                    Float.intBitsToFloat(pixels[offset + 2]));
            Point rotated = LegacyCrystalMeshSelection.orient(face, p);
            pixels[offset] = Float.floatToRawIntBits((float) rotated.x());
            pixels[offset + 1] = Float.floatToRawIntBits((float) rotated.y());
            pixels[offset + 2] = Float.floatToRawIntBits((float) rotated.z());

            if (stride == 8) {
                int normalAt = offset + 7;
                int packed = pixels[normalAt];
                Point n = new Point((byte) packed / 127.0,
                        (byte) (packed >>> 8) / 127.0,
                        (byte) (packed >>> 16) / 127.0);
                Point result = LegacyCrystalMeshSelection.orientNormal(face, n);
                pixels[normalAt] = (packed & 0xFF000000)
                        | (packNormal(result.x()) & 0xFF)
                        | ((packNormal(result.y()) & 0xFF) << 8)
                        | ((packNormal(result.z()) & 0xFF) << 16);
            }
        }
        Direction normal = original.getDirection();
        if (normal != null) {
            Point rotated = LegacyCrystalMeshSelection.orientNormal(face,
                    new Point(normal.getStepX(), normal.getStepY(), normal.getStepZ()));
            normal = Direction.getNearest((float) rotated.x(),
                    (float) rotated.y(), (float) rotated.z());
        }
        return new BakedQuad(pixels, 0, // BETA26 CrystalModel gives every MeshPart tintIndex=0
                normal, original.getSprite(), original.isShade());
    }

    private static int packNormal(double value) {
        return Math.max(-127, Math.min(127, (int) Math.round(value * 127.0)));
    }
}
