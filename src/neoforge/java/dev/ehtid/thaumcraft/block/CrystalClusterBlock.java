package dev.ehtid.thaumcraft.block;


import dev.ehtid.thaumcraft.port.core.LegacyBlockCrystalGrowthRules;
import dev.ehtid.thaumcraft.port.core.LegacyCrystalOutlineBounds;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.sounds.SoundEvent;
import dev.ehtid.thaumcraft.registry.ThaumcraftSounds;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Real seven-variant BETA26 BlockCrystal world behavior.
 * Uses the persisted (not imagined) per-chunk Vis/Flux in AuraSavedData.
 * BETA26 Material.ROCK + side-solid support is represented by an explicit,
 * data-driven vanilla compatibility tag that datapacks can extend. Advanced multi-face
 * mesh/outline and original crystal SoundType still require client parity.
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
    private static final TagKey<Block> CRYSTAL_SUPPORTS = TagKey.create(
            Registries.BLOCK, ResourceLocation.parse("thaumcraft:crystal_supports"));

    private final String aspectTag;
    public CrystalClusterBlock(BlockBehaviour.Properties props,String aspectTag) {
        // getShape depends on adjacent support blocks, not only BlockState properties.
        // Mark the block dynamic so another position's outline is never reused from a cache.
        super(props.dynamicShape().sound(ORIGINAL_CRYSTAL_SOUND));
        this.aspectTag=aspectTag;
        registerDefaultState(stateDefinition.any().setValue(SIZE,0).setValue(GENERATION,1));
    }
    public String aspectTag() { return aspectTag; }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b) {
        b.add(SIZE,GENERATION);
    }
    @Override public VoxelShape getCollisionShape(BlockState state,BlockGetter level,
        BlockPos pos, CollisionContext context) { return Shapes.empty(); }
    @Override public VoxelShape getShape(BlockState state,BlockGetter level,
        BlockPos pos,CollisionContext context) {
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
        BlockState state=defaultBlockState();
        return state.canSurvive(ctx.getLevel(),ctx.getClickedPos())?state:null;
    }
    /** World and renderer MUST use identical attached-face criteria. */
    public static boolean hasCrystalSupport(BlockGetter level, BlockPos pos, Direction d) {
        BlockPos side=pos.relative(d);
        BlockState support=level.getBlockState(side);
        return support.is(CRYSTAL_SUPPORTS)
                && support.isFaceSturdy(level,side,d.getOpposite());
    }

    private static boolean touchesStone(LevelReader level,BlockPos pos) {
        for(Direction d:Direction.values())
            if(hasCrystalSupport(level,pos,d)) return true;
        return false;
    }
    @Override public boolean canSurvive(BlockState state,LevelReader world,BlockPos pos) {
        return touchesStone(world,pos);
    }
    @Override protected void neighborChanged(BlockState state,
          net.minecraft.world.level.Level world,BlockPos pos,Block block,
          BlockPos fromPos,boolean movedByPiston) {
        super.neighborChanged(state,world,pos,block,fromPos,movedByPiston);
        if(!world.isClientSide&&!state.canSurvive(world,pos))
            world.scheduleTick(pos,this,1);
    }
    @Override public void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource rand) {
        if(level.getBlockState(pos).is(this) && !state.canSurvive(level,pos)) {
            Block.dropResources(state,level,pos);
            level.removeBlock(pos,false);
        }
    }

    @Override public void randomTick(BlockState state,ServerLevel level,BlockPos pos,
                                      RandomSource random) {
        var s=new LegacyBlockCrystalGrowthRules.State(
                state.getValue(SIZE),state.getValue(GENERATION));
        if(!LegacyBlockCrystalGrowthRules.attemptTick(s,
                random.nextInt(LegacyBlockCrystalGrowthRules.tickDenominator(s)))) return;
        ThaumcraftAuraSavedData aura=ThaumcraftAuraSavedData.get(level);
        // Missing chunk != zero aura. The original expects AuraHandler to
        // generate a chunk first; do not destroy crystals on unloaded data.
        if(!aura.hasChunk(pos)) return;
        boolean flux="vitium".equals(aspectTag);
        float available=flux?aura.getFlux(pos):aura.getVis(pos);
        if(LegacyBlockCrystalGrowthRules.starved(available)) {
            var smaller=LegacyBlockCrystalGrowthRules.shrink(s);
            if(smaller!=null) {
                level.setBlock(pos,state.setValue(SIZE,smaller.size()),3);
                if(flux) aura.polluteFlux(pos,10); else aura.addVis(pos,10);
            } else {
                boolean touchingSame=false;
                for(Direction d:Direction.values())
                    if(level.getBlockState(pos.relative(d)).is(this)) {
                        touchingSame=true;break;
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
        BlockState destination=level.getBlockState(other);
        // BETA26 checks the block Material, not whether its FluidState is non-empty.
        // BlockState.liquid() retains the legacy liquid-material flag in 1.21.1;
        // waterlogged replaceable solids therefore remain eligible for spreading.
        if(!LegacyBlockCrystalGrowthRules.canSpreadInto(
                destination.liquid(),
                destination.isAir()||destination.canBeReplaced())) return;
        if(random.nextInt(16)!=0||!touchesStone(level,other)) return;
        if(!aura.trySpendCrystalAura(pos,flux,10)) return;
        var child=LegacyBlockCrystalGrowthRules.child(s,random.nextInt(6)==0);
        level.setBlock(other,defaultBlockState()
                .setValue(SIZE,child.size())
                .setValue(GENERATION,child.generation()),3);
    }
}
