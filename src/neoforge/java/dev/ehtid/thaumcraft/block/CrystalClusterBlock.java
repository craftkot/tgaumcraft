package dev.ehtid.thaumcraft.block;


import dev.ehtid.thaumcraft.port.core.LegacyBlockCrystalGrowthRules;
import dev.ehtid.thaumcraft.port.core.LegacyCrystalOutlineBounds;
import dev.ehtid.thaumcraft.port.core.LegacyCrystalSupportLogic;
import dev.ehtid.thaumcraft.world.ThaumcraftAuraSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.sounds.SoundEvent;
import dev.ehtid.thaumcraft.registry.ThaumcraftSounds;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Real seven-variant BETA26 BlockCrystal world behavior.
 * Uses the persisted (not imagined) per-chunk Vis/Flux in AuraSavedData.
 * BETA26 Material.ROCK + side-solid support is represented by an explicit,
 * data-driven vanilla compatibility tag that datapacks can extend. Six face-specific
 * mesh orientations are selected by multipart models; per-face randomized shard choice
 * remains a separate visual-parity detail.
 */
public final class CrystalClusterBlock extends Block {
    // BETA26 SoundsTC.CRYSTAL: 0.5 volume, 1.0 pitch, crystal event for
    // break, step, place, hit and fall. Resolve the DeferredRegister lazily:
    // block constructors can run before the sound registry is populated.
    private static final SoundType ORIGINAL_CRYSTAL_SOUND = new SoundType(
            0.5F, 1.0F,
            SoundType.GLASS.getBreakSound(), SoundType.GLASS.getStepSound(),
            SoundType.GLASS.getPlaceSound(), SoundType.GLASS.getHitSound(),
            SoundType.GLASS.getFallSound()) {
        @Override public SoundEvent getBreakSound() { return ThaumcraftSounds.CRYSTAL.get(); }
        @Override public SoundEvent getStepSound() { return ThaumcraftSounds.CRYSTAL.get(); }
        @Override public SoundEvent getPlaceSound() { return ThaumcraftSounds.CRYSTAL.get(); }
        @Override public SoundEvent getHitSound() { return ThaumcraftSounds.CRYSTAL.get(); }
        @Override public SoundEvent getFallSound() { return ThaumcraftSounds.CRYSTAL.get(); }
    };

    public static final IntegerProperty SIZE=IntegerProperty.create("size",0,3);
    public static final IntegerProperty GENERATION=IntegerProperty.create("gen",1,4);
    public static final BooleanProperty UP=BooleanProperty.create("up");
    public static final BooleanProperty DOWN=BooleanProperty.create("down");
    public static final BooleanProperty EAST=BooleanProperty.create("east");
    public static final BooleanProperty WEST=BooleanProperty.create("west");
    public static final BooleanProperty NORTH=BooleanProperty.create("north");
    public static final BooleanProperty SOUTH=BooleanProperty.create("south");
    private static final TagKey<Block> CRYSTAL_SUPPORTS = TagKey.create(
            Registries.BLOCK, ResourceLocation.parse("thaumcraft:crystal_supports"));

    private final String aspectTag;
    public CrystalClusterBlock(BlockBehaviour.Properties props,String aspectTag) {
        // getShape depends on adjacent support blocks, not only BlockState properties.
        // Mark the block dynamic so another position's outline is never reused from a cache.
        super(props.dynamicShape().sound(ORIGINAL_CRYSTAL_SOUND));
        this.aspectTag=aspectTag;
        registerDefaultState(stateDefinition.any().setValue(SIZE,0).setValue(GENERATION,1)
                .setValue(UP,false).setValue(DOWN,false).setValue(EAST,false)
                .setValue(WEST,false).setValue(NORTH,false).setValue(SOUTH,false));
    }
    public String aspectTag() { return aspectTag; }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b) {
        b.add(SIZE,GENERATION,UP,DOWN,EAST,WEST,NORTH,SOUTH);
    }
    @Override public VoxelShape getCollisionShape(BlockState state,BlockGetter level,
        BlockPos pos, CollisionContext context) { return Shapes.empty(); }
    @Override public VoxelShape getShape(BlockState state,BlockGetter level,
        BlockPos pos,CollisionContext context) {
        // NeoForge's threaded light engine also asks for shapes off the server
        // thread. This block is noOcclusion with light level 1, so an empty
        // worker-thread shape preserves light transmission without touching
        // Level/ServerChunkCache from the lighting worker. Interactive outline
        // queries on the server thread and client render thread remain exact.
        if (level instanceof ServerLevel serverLevel && !serverLevel.getServer().isSameThread()) {
            return Shapes.empty();
        }
        // BETA26 BlockCrystal#getBoundingBox: a single support face selects
        // its corresponding half-cube; two or more use the full outline.
        boolean up = hasCrystalSupport(level,pos,Direction.UP);
        boolean down = hasCrystalSupport(level,pos,Direction.DOWN);
        boolean east = hasCrystalSupport(level,pos,Direction.EAST);
        boolean west = hasCrystalSupport(level,pos,Direction.WEST);
        boolean south = hasCrystalSupport(level,pos,Direction.SOUTH);
        boolean north = hasCrystalSupport(level,pos,Direction.NORTH);
        var bounds = LegacyCrystalOutlineBounds.fromSupportFaces(
                up, down, east, west, south, north);
        return box(bounds.minX()*16.0D,bounds.minY()*16.0D,bounds.minZ()*16.0D,
                bounds.maxX()*16.0D,bounds.maxY()*16.0D,bounds.maxZ()*16.0D);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState state=withSupportFaces(defaultBlockState(),ctx.getLevel(),ctx.getClickedPos());
        return state.canSurvive(ctx.getLevel(),ctx.getClickedPos())?state:null;
    }

    /** Recompute the six source-era extended-state flags without loading neighbours. */
    private static BlockState withSupportFaces(BlockState state,BlockGetter level,BlockPos pos) {
        return state.setValue(UP,hasCrystalSupport(level,pos,Direction.UP))
                .setValue(DOWN,hasCrystalSupport(level,pos,Direction.DOWN))
                .setValue(EAST,hasCrystalSupport(level,pos,Direction.EAST))
                .setValue(WEST,hasCrystalSupport(level,pos,Direction.WEST))
                .setValue(NORTH,hasCrystalSupport(level,pos,Direction.NORTH))
                .setValue(SOUTH,hasCrystalSupport(level,pos,Direction.SOUTH));
    }

    @Override protected BlockState updateShape(BlockState state,LevelReader level,
            ScheduledTickAccess scheduledTickAccess,BlockPos pos,Direction direction,
            BlockPos neighborPos,BlockState neighborState,RandomSource random) {
        BlockState updated=super.updateShape(state,level,scheduledTickAccess,pos,
                direction,neighborPos,neighborState,random);
        return withSupportFaces(updated,level,pos);
    }

    @Override protected void onPlace(BlockState state,Level level,BlockPos pos,
            BlockState oldState,boolean movedByPiston) {
        super.onPlace(state,level,pos,oldState,movedByPiston);
        if(level.isClientSide) return;
        BlockState updated=withSupportFaces(state,level,pos);
        if(!updated.equals(state)) level.setBlock(pos,updated,2);
    }
    /** World and renderer MUST use identical attached-face criteria. */
    public static boolean hasCrystalSupport(BlockGetter level, BlockPos pos, Direction d) {
        BlockPos side=pos.relative(d);
        if (level instanceof ServerLevel serverLevel) {
            // ServerLevel.hasChunkAt can be true while a neighbour is still in
            // the chunk-loading pipeline. Level.getBlockState then joins that
            // chunk future; the light worker can do this while the server thread
            // is itself waiting for a neighbour during crystal placement. Read
            // only a fully available chunk and never join its load future here.
            LevelChunk chunk=serverLevel.getChunkSource().getChunkNow(
                    side.getX() >> 4, side.getZ() >> 4);
            if (chunk == null) return false;
            BlockState support=chunk.getBlockState(side);
            return support.is(CRYSTAL_SUPPORTS)
                    && support.isFaceSturdy(chunk,side,d.getOpposite());
        }
        // Client outline queries stay on the render thread. A world-backed
        // client getter must still avoid requesting an absent neighbouring chunk;
        // chunk-only render getters are already local to their own view.
        if (level instanceof LevelReader reader && !reader.hasChunkAt(side)) return false;
        BlockState support=level.getBlockState(side);
        return support.is(CRYSTAL_SUPPORTS)
                && support.isFaceSturdy(level,side,d.getOpposite());
    }

    private static boolean touchesStone(LevelReader level,BlockPos pos) {
        for(Direction d:Direction.values())
            if(hasCrystalSupport(level,pos,d)) return true;
        return false;
    }

    private static boolean touchesLoadedStone(ServerLevel level,BlockPos pos) {
        for(Direction d:Direction.values()) {
            BlockPos supportPos=pos.relative(d);
            // A support face in an unloaded neighbour is simply unavailable
            // for this tick; never load that neighbour to answer the query.
            if(!level.hasChunkAt(supportPos)) continue;
            if(hasCrystalSupport(level,pos,d)) return true;
        }
        return false;
    }

    /**
     * A missing adjacent chunk is not proof that a crystal has lost its support.
     * Use only non-joining lookups so a scheduled support check never loads a
     * neighbouring chunk or deletes a crystal while that neighbour is unavailable.
     */
    private static boolean allSupportChunksLoaded(ServerLevel level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos supportPos = pos.relative(direction);
            if (level.getChunkSource().getChunkNow(
                    supportPos.getX() >> 4, supportPos.getZ() >> 4) == null) {
                return false;
            }
        }
        return true;
    }
    /**
     * A chunk may be present in the loading pipeline while hasChunkAt already
     * returns true. Level#getBlockState can join that unfinished future from a
     * random tick at a chunk edge. Read only a fully available chunk and never
     * wait for a neighboring chunk from this path.
     */
    private static BlockState loadedDestination(ServerLevel level, BlockPos pos) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(
                pos.getX() >> 4, pos.getZ() >> 4);
        return chunk == null ? null : chunk.getBlockState(pos);
    }

    @Override public boolean canSurvive(BlockState state,LevelReader world,BlockPos pos) {
        // Server-side placement at a chunk edge must not load a neighbour just
        // to test its support face. Client shape/placement queries stay read-only.
        if (world instanceof ServerLevel serverLevel) return touchesLoadedStone(serverLevel,pos);
        return touchesStone(world,pos);
    }
    @Override protected void neighborChanged(BlockState state,
          net.minecraft.world.level.Level world,BlockPos pos,Block block,
          BlockPos fromPos,boolean movedByPiston) {
        super.neighborChanged(state,world,pos,block,fromPos,movedByPiston);
        if(world.isClientSide) return;
        BlockState current=world.getBlockState(pos);
        if(!current.is(this)) return;
        BlockState updated=withSupportFaces(current,world,pos);
        if(!updated.equals(current)) world.setBlock(pos,updated,2);
        if(!updated.canSurvive(world,pos)) world.scheduleTick(pos,this,1);
    }
    @Override public void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource rand) {
        BlockState current = level.getBlockState(pos);
        if (!current.is(this)) return;

        boolean hasLoadedSupport = touchesLoadedStone(level, pos);
        boolean allSupportChunksLoaded = allSupportChunksLoaded(level, pos);
        if (!LegacyCrystalSupportLogic.shouldRemoveUnsupportedCrystal(
                hasLoadedSupport, allSupportChunksLoaded)) {
            // Keep checking while a possible support face is in an unavailable
            // or in-flight neighbour chunk. Never interpret "unknown" as "air".
            if (!hasLoadedSupport) level.scheduleTick(pos, this, 20);
            return;
        }

        Block.dropResources(current, level, pos);
        level.removeBlock(pos, false);
    }

    @Override public void randomTick(BlockState state,ServerLevel level,BlockPos pos,
                                      RandomSource random) {
        var s=new LegacyBlockCrystalGrowthRules.State(
                state.getValue(SIZE),state.getValue(GENERATION));
        if(!LegacyBlockCrystalGrowthRules.attemptTick(s,
                random.nextInt(LegacyBlockCrystalGrowthRules.tickDenominator(s)))) return;
        ThaumcraftAuraSavedData aura=ThaumcraftAuraSavedData.get(level);
        // BETA26 AuraHandler.getVis/getFlux return zero when the aura-world
        // cache has no entry for this chunk. That is starvation, not a reason
        // to skip the tick: size reduction still occurs, while addVis/polluteFlux
        // remain no-ops for the absent record exactly as in the legacy helper path.
        boolean flux="vitium".equals(aspectTag);
        float available=flux?aura.getFlux(pos):aura.getVis(pos);
        if(LegacyBlockCrystalGrowthRules.starved(available)) {
            var smaller=LegacyBlockCrystalGrowthRules.shrink(s);
            if(smaller!=null) {
                level.setBlock(pos,state.setValue(SIZE,smaller.size()),3);
                if(flux) aura.polluteFlux(pos,10); else aura.addVis(pos,10);
            } else {
                boolean touchingSame=false;
                for(Direction d:Direction.values()) {
                    BlockPos neighbor=pos.relative(d);
                    // A size-zero crystal may be on a chunk edge. Never load an
                    // adjacent chunk just to answer this same-variant adjacency check.
                    if(!level.hasChunkAt(neighbor)) continue;
                    if(level.getBlockState(neighbor).is(this)) {
                        touchingSame=true;break;
                    }
                }
                if(LegacyBlockCrystalGrowthRules.removeStarved(s,touchingSame)) {
                    // Source removes block and returns aura without manual
                    // crystal item drops in this starvation-only branch.
                    level.removeBlock(pos,false);
                    if(flux) aura.polluteFlux(pos,10); else aura.addVis(pos,10);
                }
            }
            return;
        }
        if(LegacyBlockCrystalGrowthRules.canGrow(s,pos.asLong(),available,aura.getBase(pos))) {
            if(aura.trySpendCrystalAura(pos,flux,10)) level.setBlock(pos,
                    state.setValue(SIZE,LegacyBlockCrystalGrowthRules.grow(s).size()),3);
            return;
        }
        if(!LegacyBlockCrystalGrowthRules.maySpread(s,available,aura.getBase(pos))) return;
        int dx=random.nextInt(3)-1,dy=random.nextInt(3)-1,dz=random.nextInt(3)-1;
        if(!LegacyBlockCrystalGrowthRules.spreadOffset(dx,dy,dz)) return;
        BlockPos other=pos.offset(dx,dy,dz);
        // Random ticks can occur at a chunk edge. ServerLevel#getBlockState may
        // request an absent chunk, so do not inspect a spread destination until
        // its chunk is already loaded.
        BlockState destination = loadedDestination(level, other);
        if (destination == null) return;
        // BETA26 checks the block Material, not whether its FluidState is non-empty.
        // BlockState.liquid() retains the legacy liquid-material flag in 1.21.1;
        // waterlogged replaceable solids therefore remain eligible for spreading.
        if(!LegacyBlockCrystalGrowthRules.canSpreadInto(
                destination.liquid(),
                destination.isAir()||destination.canBeReplaced())) return;
        if(random.nextInt(16)!=0||!touchesLoadedStone(level,other)) return;
        if(!aura.trySpendCrystalAura(pos,flux,10)) return;
        var child=LegacyBlockCrystalGrowthRules.child(s,random.nextInt(6)==0);
        BlockState childState=withSupportFaces(defaultBlockState()
                .setValue(SIZE,child.size())
                .setValue(GENERATION,child.generation()),level,other);
        level.setBlock(other,childState,3);
    }
}
