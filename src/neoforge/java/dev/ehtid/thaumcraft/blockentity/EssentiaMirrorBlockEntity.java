package dev.ehtid.thaumcraft.blockentity;

import dev.ehtid.thaumcraft.port.core.LegacyEssentiaMirrorLogic;
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
 * No transport endpoints are exposed until a real remote transaction bridge
 * exists: core link state alone must not fabricate or delete essentia.
 */
public final class EssentiaMirrorBlockEntity extends BlockEntity {
    private String linkDimension;
    private BlockPos linkPosition;
    private boolean linked;
    private int instability;
    private long ticks;
    private int nextCheck=LegacyEssentiaMirrorLogic.LINK_RETRY_START;

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
