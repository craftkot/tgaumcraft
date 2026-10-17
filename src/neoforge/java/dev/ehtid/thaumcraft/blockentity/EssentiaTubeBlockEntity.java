package dev.ehtid.thaumcraft.blockentity;

import dev.ehtid.thaumcraft.port.core.LegacyEssentiaTubeLogic;
import dev.ehtid.thaumcraft.registry.ThaumcraftBlockEntities;
import dev.ehtid.thaumcraft.registry.ThaumcraftBlocks;
import dev.ehtid.thaumcraft.port.core.AspectRegistry;
import dev.ehtid.thaumcraft.block.EssentiaTubeBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import java.util.ArrayList;
import java.util.List;

/**
 * First real BETA26 TileTube: one-unit persistent buffer, six open ports,
 * highest adjacent suction minus one, and 5-tick physical essence transfer.
 * No arbitrary fabrication; only removes an actual unit from a loaded
 * IEssentiaTransportPort neighbor after an exact read-only preflight.
 * Filter/valve/buffer/one-way tube subtypes are separate unfinished systems.
 */
public final class EssentiaTubeBlockEntity extends BlockEntity implements EssentiaTransportPort {
    private LegacyEssentiaTubeLogic.Tube stored = LegacyEssentiaTubeLogic.Tube.empty();
    private int count;
    private int facing = Direction.NORTH.get3DDataValue();
    private String aspectFilter;
    private boolean allowFlow = true;
    private boolean wasPoweredLastTick;

    private LegacyEssentiaTubeLogic.Variant variant(){
        BlockState block=getBlockState();
        if(block.is(ThaumcraftBlocks.TUBE_VALVE.get()))return LegacyEssentiaTubeLogic.Variant.VALVE;
        if(block.is(ThaumcraftBlocks.TUBE_FILTER.get()))return LegacyEssentiaTubeLogic.Variant.FILTER;
        if(block.is(ThaumcraftBlocks.TUBE_RESTRICT.get()))return LegacyEssentiaTubeLogic.Variant.RESTRICT;
        if(block.is(ThaumcraftBlocks.TUBE_ONEWAY.get()))return LegacyEssentiaTubeLogic.Variant.ONEWAY;
        return LegacyEssentiaTubeLogic.Variant.NORMAL;
    }
    public boolean hasFilter(){return aspectFilter!=null;}
    public boolean applyFilter(String aspect) {
        if(level==null||level.isClientSide()||variant()!=LegacyEssentiaTubeLogic.Variant.FILTER
                ||aspectFilter!=null||aspect==null||new AspectRegistry().get(aspect)==null)
            return false;
        aspectFilter=aspect;setChanged();level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);
        return true;
    }
    public boolean clearFilter(){
        if(level==null||level.isClientSide()||variant()!=LegacyEssentiaTubeLogic.Variant.FILTER
                ||aspectFilter==null)return false;
        aspectFilter=null;setChanged();level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);
        return true;
    }
    public boolean flipValve(){
        if(level==null||level.isClientSide()||variant()!=LegacyEssentiaTubeLogic.Variant.VALVE)
            return false;
        allowFlow=!allowFlow;setChanged();level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);
        return true;
    }
    public boolean rotateFacing(){
        if(level==null||level.isClientSide())return false;
        facing=(facing+1)%6;setChanged();level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);
        return true;
    }
    private static final int MAX_STORED_SUCTION = 32767;

    public EssentiaTubeBlockEntity(BlockPos pos, BlockState state) {
        super(ThaumcraftBlockEntities.TUBE.get(), pos, state);
    }

    public LegacyEssentiaTubeLogic.Tube snapshot() { return stored; }

    private void commit(LegacyEssentiaTubeLogic.Tube next) {
        if (stored.equals(next)) return;
        stored = next;
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public boolean toggleSide(Direction face) {
        if (level == null || level.isClientSide() || face == null) return false;
        commit(stored.toggle(face.get3DDataValue()));
        if (level.getBlockEntity(worldPosition.relative(face)) instanceof EssentiaTubeBlockEntity tube)
            tube.setSide(face.getOpposite(), stored.open(face.get3DDataValue()));
        EssentiaTubeBlock.updateConnections(level, worldPosition);
        EssentiaTubeBlock.updateConnections(level, worldPosition.relative(face));
        return true;
    }

    private void setSide(Direction face, boolean open) {
        if (stored.open(face.get3DDataValue()) != open) {
            commit(stored.toggle(face.get3DDataValue()));
            if (level != null) EssentiaTubeBlock.updateConnections(level, worldPosition);
        }
    }

    private List<LegacyEssentiaTubeLogic.Neighbour> neighbours() {
        List<LegacyEssentiaTubeLogic.Neighbour> neighbours = new ArrayList<>();
        if (level == null) return neighbours;
        for (Direction dir : Direction.values()) {
            if (!transportConnects(dir)) continue;
            if (!(level.getBlockEntity(worldPosition.relative(dir)) instanceof EssentiaTransportPort port)
                    || !port.transportConnects(dir.getOpposite())) continue;
            Direction face = dir.getOpposite();
            neighbours.add(new LegacyEssentiaTubeLogic.Neighbour(
                    dir.get3DDataValue(), port.transportSuction(face),
                    port.transportSuctionAspect(face), port.transportAspect(face),
                    port.transportAmount(face), port.transportMinimumSuction(),
                    port.canTransportOutput(face), true));
        }
        return neighbours;
    }

    public static void serverTick(ServerLevel level, BlockPos pos, BlockState state,
                                  EssentiaTubeBlockEntity tile) {
        if(tile.variant()==LegacyEssentiaTubeLogic.Variant.VALVE && tile.count%5==0){
            boolean powered=level.hasNeighborSignal(pos);
            if(powered!=tile.wasPoweredLastTick){
                tile.wasPoweredLastTick=powered;
                tile.allowFlow=!powered;
                tile.setChanged();
                level.sendBlockUpdated(pos,state,state,3);
            }
        }
        if (++tile.count % 2 == 0 || tile.count % 5 == 0) {
            if (tile.count % 2 == 0 && tile.stored.venting() == 0) {
                var nearby = tile.neighbours();
                if(tile.variant()==LegacyEssentiaTubeLogic.Variant.VALVE && !tile.allowFlow)
                    tile.commit(new LegacyEssentiaTubeLogic.Tube(
                            tile.stored.aspect(),tile.stored.amount(),null,0,
                            tile.stored.openMask(),tile.stored.venting()));
                else tile.commit(LegacyEssentiaTubeLogic.checkVenting(
                        LegacyEssentiaTubeLogic.calculateVariant(tile.stored,nearby,
                                tile.variant(),tile.aspectFilter,tile.facing),nearby));
            }
            if (tile.count % 5 == 0 && tile.stored.venting() == 0
                    && tile.stored.amount() == 0 && tile.stored.suction() > 0) {
                var adjacent = tile.neighbours();
                for (var next : adjacent) {
                    if (tile.variant()==LegacyEssentiaTubeLogic.Variant.VALVE&&!tile.allowFlow)
                        continue;
                    if (!LegacyEssentiaTubeLogic.canPullVariant(tile.stored,next,
                            tile.variant(),tile.aspectFilter,tile.facing))continue;
                    Direction face = Direction.from3DDataValue(next.side());
                    if (!(level.getBlockEntity(pos.relative(face)) instanceof EssentiaTransportPort source))
                        continue;
                    if (!source.transportConnects(face.getOpposite())) continue;
                    String aspect = next.essence();
                    // A single server tick is serialized. Verify ability to
                    // accept BEFORE debiting the real source.
                    if (source.takeTransport(aspect, 1, face.getOpposite()) == 1) {
                        tile.commit(LegacyEssentiaTubeLogic.acceptOne(tile.stored, aspect,
                                face.get3DDataValue()));
                        if (source instanceof AlembicBlockEntity)
                            dev.ehtid.thaumcraft.util.ThaumcraftSmokeAudit.mark("ALEMBIC_TO_TUBE");
                        break;
                    }
                }
            }
        }
        if (tile.stored.venting() > 0)
            tile.commit(LegacyEssentiaTubeLogic.tickVenting(tile.stored));
    }

    @Override public boolean transportConnects(Direction face) {
        return face != null && stored.open(face.get3DDataValue())
                && !(variant()==LegacyEssentiaTubeLogic.Variant.VALVE
                        && face.get3DDataValue()==facing);
    }
    @Override public boolean canTransportInput(Direction face) {
        return transportConnects(face) &&
                (variant()!=LegacyEssentiaTubeLogic.Variant.VALVE||allowFlow);
    }
    @Override public boolean canTransportOutput(Direction face) {
        return transportConnects(face) &&
                (variant()!=LegacyEssentiaTubeLogic.Variant.VALVE||allowFlow);
    }
    @Override public int transportSuction(Direction face) {
        return canTransportInput(face) ? stored.suction() : 0;
    }
    @Override public String transportSuctionAspect(Direction face) {
        return transportConnects(face) ? stored.suctionAspect() : null;
    }
    @Override public int transportMinimumSuction() { return 0; }
    @Override public int transportAmount(Direction face) {
        return transportConnects(face) ? stored.amount() : 0;
    }
    @Override public String transportAspect(Direction face) {
        return transportConnects(face) ? stored.aspect() : null;
    }
    @Override public int takeTransport(String aspect, int amount, Direction face) {
        if (level == null || level.isClientSide() || amount <= 0 ||
                !canTransportOutput(face)) return 0;
        var result = LegacyEssentiaTubeLogic.takeOne(stored, aspect, face.get3DDataValue());
        if (result.equals(stored)) return 0;
        commit(result);
        return 1;
    }
    @Override public int addTransport(String aspect, int amount, Direction face) {
        if (level == null || level.isClientSide() || amount <= 0 ||
                !canTransportInput(face)) return 0;
        var result = LegacyEssentiaTubeLogic.acceptOne(stored, aspect, face.get3DDataValue());
        if (result.equals(stored)) return 0;
        commit(result);
        return 1;
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (stored.aspect() != null) tag.putString("type", stored.aspect());
        tag.putInt("amount", stored.amount());
        if (stored.suctionAspect() != null) tag.putString("stype", stored.suctionAspect());
        if (aspectFilter != null) tag.putString("AspectFilter",aspectFilter);
        tag.putBoolean("flow",allowFlow);
        tag.putBoolean("hadpower",wasPoweredLastTick);
        tag.putInt("samount", stored.suction());
        tag.putInt("side", facing);
        byte[] sides = new byte[6];
        for (int i=0;i<6;i++) sides[i]=(byte)(stored.open(i)?1:0);
        tag.putByteArray("open", sides);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        String aspect = tag.contains("type",Tag.TAG_STRING) ? tag.getString("type") : null;
        String suctionAspect = tag.contains("stype",Tag.TAG_STRING) ? tag.getString("stype") : null;
        int amount = Math.clamp(tag.getInt("amount"), 0, 1);
        int suction = Math.clamp(tag.getInt("samount"), 0, MAX_STORED_SUCTION);
        byte[] sides = tag.getByteArray("open");
        int openMask = 63;
        if (sides.length == 6) {
            openMask=0;
            for(int i=0;i<6;i++) if(sides[i]!=0) openMask|=1<<i;
        }
        facing = Math.clamp(tag.getInt("side"),0,5);
        aspectFilter=tag.contains("AspectFilter",Tag.TAG_STRING)
                ?tag.getString("AspectFilter"):null;
        allowFlow=!tag.contains("flow")||tag.getBoolean("flow");
        wasPoweredLastTick=tag.getBoolean("hadpower");
        if (aspect == null || aspect.isBlank()) amount = 0;
        stored = new LegacyEssentiaTubeLogic.Tube(aspect, amount,
                suctionAspect, suction, openMask, 0);
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag(); saveAdditional(tag, registries); return tag;
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
