package dev.ehtid.thaumcraft.blockentity;

import dev.ehtid.thaumcraft.port.core.LegacyEssentiaMirrorLogic;
import dev.ehtid.thaumcraft.port.core.LegacyEssentiaSourceOrder;
import dev.ehtid.thaumcraft.block.EssentiaMirrorBlock;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import dev.ehtid.thaumcraft.registry.ThaumcraftItems;
import net.minecraft.world.level.block.entity.BlockEntity;
import dev.ehtid.thaumcraft.registry.ThaumcraftBlockEntities;
import dev.ehtid.thaumcraft.world.ThaumcraftAuraSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * BETA26 TileMirrorEssentia world-side link and instability persistence.
 * Transport is implemented as an exact remote debit on the server thread:
 * no fabricated buffer and no forced chunk loads. Mirror input is not yet
 * implemented; rejecting it is safer than a partial remote credit.
 */
public final class EssentiaMirrorBlockEntity extends BlockEntity implements EssentiaTransportPort {
    private String linkDimension;
    private BlockPos linkPosition;
    private boolean linked;
    private int instability;
    private long ticks;
    private int nextCheck=LegacyEssentiaMirrorLogic.LINK_RETRY_START;
    /** A one-tick memo for remote source discovery, never authoritative for debit. */
    private long sourceScanTick=Long.MIN_VALUE;
    private RemoteSource cachedSource;

    private record RemoteSource(ServerLevel world, BlockPos pos,
                                EssentiaTransportPort port, Direction face) {}

    /** Only the mirror's exposed face connects to the local pipe network. */
    @Override public boolean transportConnects(Direction face) {
        return face != null && getBlockState().getValue(EssentiaMirrorBlock.FACING) == face;
    }

    @Override public boolean canTransportOutput(Direction face) {
        return transportConnects(face) && hasValidLink();
    }

    /** No external push until two-phase remote sink crediting is implemented. */
    @Override public boolean canTransportInput(Direction face) { return false; }
    @Override public int transportSuction(Direction face) { return 0; }
    @Override public String transportSuctionAspect(Direction face) { return null; }
    @Override public int transportMinimumSuction() { return 0; }

    private boolean stillValidSource(RemoteSource candidate, String aspect) {
        if(candidate == null || !candidate.world().hasChunkAt(candidate.pos())
                || candidate.world().getBlockEntity(candidate.pos()) != candidate.port())
            return false;
        EssentiaTransportPort port=candidate.port();
        Direction side=candidate.face();
        String current=port.transportAspect(side);
        return port.transportConnects(side) && port.canTransportOutput(side)
                && LegacyEssentiaMirrorLogic.mayRelayDebit(1,true,true,
                        !(port instanceof EssentiaMirrorBlockEntity),
                        current,port.transportAmount(side),aspect);
    }

    /**
     * Original EssentiaHandler range 8 ordered by squared distance.
     * Only loaded actual BlockEntities qualify; mirror-to-mirror relay is
     * deliberately forbidden to eliminate recursive loops and double debit.
     */
    private RemoteSource remoteSource(String requestedAspect) {
        if(!(level instanceof ServerLevel local) || !hasValidLink())return null;
        EssentiaMirrorBlockEntity remoteMirror=partnerLoaded(local);
        if(remoteMirror==null || !(remoteMirror.level instanceof ServerLevel remote))return null;
        long tick=local.getGameTime();
        if(sourceScanTick==tick) {
            if(stillValidSource(cachedSource,requestedAspect))return cachedSource;
            if(cachedSource==null && requestedAspect==null)return null;
        }
        sourceScanTick=tick;
        cachedSource=null;
        for(var offset:LegacyEssentiaSourceOrder.nullFacing(LegacyEssentiaMirrorLogic.SEARCH_RANGE)) {
            BlockPos target=remoteMirror.worldPosition.offset(offset.x(),offset.y(),offset.z());
            if(!remote.hasChunkAt(target))continue;
            BlockEntity tile=remote.getBlockEntity(target);
            if(!(tile instanceof EssentiaTransportPort port)
                    || tile instanceof EssentiaMirrorBlockEntity)continue;
            for(Direction side:Direction.values()) {
                var candidate=new RemoteSource(remote,target,port,side);
                if(stillValidSource(candidate,requestedAspect)) {
                    cachedSource=candidate;
                    return candidate;
                }
            }
        }
        return null;
    }

    @Override public int transportAmount(Direction face) {
        return canTransportOutput(face) && remoteSource(null)!=null ? 1 : 0;
    }

    @Override public String transportAspect(Direction face) {
        if(!canTransportOutput(face))return null;
        RemoteSource source=remoteSource(null);
        return source==null?null:source.port().transportAspect(source.face());
    }

    /**
     * Called by a real local tube/jar which will credit its buffer only if
     * this method returns 1. The remote source is debited exactly once.
     */
    @Override public int takeTransport(String aspect,int amount,Direction face) {
        if(!(level instanceof ServerLevel local)
                ||!canTransportOutput(face)||amount!=1||aspect==null)return 0;
        RemoteSource source=remoteSource(aspect);
        if(!stillValidSource(source,aspect))return 0;
        // No local or remote state is changed until the real source returns 1.
        if(source.port().takeTransport(aspect,1,source.face())!=1)return 0;
        // This marker is emitted only after an actual remote BlockEntity debit.
        dev.ehtid.thaumcraft.util.ThaumcraftSmokeAudit.mark("MIRROR_REMOTE_DEBIT");
        EssentiaMirrorBlockEntity partner=partnerLoaded(local);
        if(partner!=null && hasValidLink()) {
            instability=LegacyEssentiaMirrorLogic.afterTransfer(instability,1,true);
            partner.instability=LegacyEssentiaMirrorLogic.afterTransfer(partner.instability,1,true);
            setChanged();
            partner.setChanged();
        }
        cachedSource=null;
        sourceScanTick=Long.MIN_VALUE;
        return 1;
    }

    @Override public int addTransport(String aspect,int amount,Direction face) {return 0;}


    public EssentiaMirrorBlockEntity(BlockPos pos,BlockState state) {
        super(ThaumcraftBlockEntities.MIRROR_ESSENTIA.get(),pos,state);
    }

    public boolean hasValidLink() {
        if(!(level instanceof ServerLevel server)||!linked)return false;
        EssentiaMirrorBlockEntity other=partnerLoaded(server);
        if(other==null)return false;
        return LegacyEssentiaMirrorLogic.reciprocal(endpoint(server,worldPosition),
                link(),endpoint((ServerLevel)other.level,other.worldPosition),other.link(),true);
    }

    /** Preserve the original BETA26 BlockMirrorItem coordinates when harvested. */
    public boolean writeLinkedItem(ItemStack drop) {
        if(drop.isEmpty()||!drop.is(ThaumcraftItems.MIRROR_ESSENTIA.get())
                ||!linked||linkDimension==null||linkPosition==null)return false;
        CompoundTag tag=new CompoundTag();
        tag.putString("linkDim",linkDimension);
        tag.putInt("linkX",linkPosition.getX());
        tag.putInt("linkY",linkPosition.getY());
        tag.putInt("linkZ",linkPosition.getZ());
        drop.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
        return true;
    }

    public LegacyEssentiaMirrorLogic.Link link() {
        return new LegacyEssentiaMirrorLogic.Link(
                linkPosition==null||linkDimension==null?null:
                new LegacyEssentiaMirrorLogic.Endpoint(
                        linkDimension,linkPosition.getX(),linkPosition.getY(),linkPosition.getZ()),linked);
    }

    private static LegacyEssentiaMirrorLogic.Endpoint endpoint(ServerLevel level,BlockPos pos) {
        return new LegacyEssentiaMirrorLogic.Endpoint(level.dimension().location().toString(),
                pos.getX(),pos.getY(),pos.getZ());
    }

    private EssentiaMirrorBlockEntity partnerLoaded(ServerLevel origin) {
        if(linkDimension==null||linkPosition==null)return null;
        ResourceLocation id=ResourceLocation.tryParse(linkDimension);
        if(id==null)return null;
        ServerLevel dest=origin.getServer().getLevel(ResourceKey.create(Registries.DIMENSION,id));
        if(dest==null||!dest.hasChunkAt(linkPosition))return null;
        return dest.getBlockEntity(linkPosition) instanceof EssentiaMirrorBlockEntity target?target:null;
    }

    /** Original held MirrorItem obtains a reference from an existing unpaired mirror. */
    public boolean canSupplyLink() {
        return level instanceof ServerLevel && !hasValidLink();
    }

    /** Existing mirror must be loaded and not validly paired with someone else. */
    public boolean linkTo(String dimension,BlockPos position) {
        if(!(level instanceof ServerLevel server)||dimension==null||position==null)return false;
        ResourceLocation id=ResourceLocation.tryParse(dimension);
        if(id==null)return false;
        ServerLevel dest=server.getServer().getLevel(ResourceKey.create(Registries.DIMENSION,id));
        if(dest==null||!dest.hasChunkAt(position))return false;
        if(!(dest.getBlockEntity(position) instanceof EssentiaMirrorBlockEntity target))return false;
        var self=endpoint(server,worldPosition);
        var other=endpoint(dest,position);
        if(!LegacyEssentiaMirrorLogic.canRestore(self,other,true,true,target.hasValidLink()))
            return false;
        // The source mirror must not already belong to a valid different pair.
        if(hasValidLink())return false;
        setLink(other);
        target.setLink(self);
        return true;
    }

    private void setLink(LegacyEssentiaMirrorLogic.Endpoint target) {
        linkDimension=target.dimension();
        linkPosition=new BlockPos(target.x(),target.y(),target.z());
        linked=true;
        setChanged();
        if(level!=null&&!level.isClientSide())
            level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);
    }

    public static void serverTick(ServerLevel server,BlockPos pos,EssentiaMirrorBlockEntity tile) {
        tile.ticks++;
        var result=LegacyEssentiaMirrorLogic.checkInstability(tile.instability,tile.ticks);
        if(result.emittedFlux()>0)ThaumcraftAuraSavedData.get(server)
                .polluteFlux(pos,result.emittedFlux());
        if(tile.instability!=result.value()) {
            tile.instability=result.value();
            tile.setChanged();
        }
        if(tile.ticks%tile.nextCheck==0)
            tile.nextCheck=LegacyEssentiaMirrorLogic.nextLinkCheckInterval(
                    tile.nextCheck,tile.hasValidLink());
    }

    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries) {
        super.saveAdditional(tag,registries);
        tag.putBoolean("linked",linked);
        if(linkDimension!=null&&linkPosition!=null) {
            tag.putString("linkDim",linkDimension);
            tag.putInt("linkX",linkPosition.getX());
            tag.putInt("linkY",linkPosition.getY());
            tag.putInt("linkZ",linkPosition.getZ());
        }
        tag.putInt("instability",instability);
    }

    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries) {
        super.loadAdditional(tag,registries);
        linked=tag.getBoolean("linked");
        linkDimension=tag.contains("linkDim",Tag.TAG_STRING)?tag.getString("linkDim"):null;
        linkPosition=tag.contains("linkX",Tag.TAG_INT)&&tag.contains("linkY",Tag.TAG_INT)
                &&tag.contains("linkZ",Tag.TAG_INT)
                ?new BlockPos(tag.getInt("linkX"),tag.getInt("linkY"),tag.getInt("linkZ")):null;
        if(linkPosition==null||linkDimension==null)linked=false;
        instability=Math.max(0,tag.getInt("instability"));
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag=new CompoundTag();
        saveAdditional(tag,registries);
        return tag;
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
