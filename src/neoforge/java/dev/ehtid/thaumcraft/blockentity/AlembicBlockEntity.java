package dev.ehtid.thaumcraft.blockentity;

import dev.ehtid.thaumcraft.port.core.LegacyAlembicLogic;
import dev.ehtid.thaumcraft.port.core.AspectRegistry;
import dev.ehtid.thaumcraft.registry.ThaumcraftBlockEntities;
import dev.ehtid.thaumcraft.world.ThaumcraftAuraSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** BETA26 TileAlembic: filtered single-aspect 128-unit distillation vessel. */
public final class AlembicBlockEntity extends BlockEntity implements EssentiaTransportPort {
    private static final AspectRegistry ASPECTS = new AspectRegistry();
    private LegacyAlembicLogic.State state = LegacyAlembicLogic.State.empty();

    public AlembicBlockEntity(BlockPos pos, BlockState block) {
        super(ThaumcraftBlockEntities.ALEMBIC.get(), pos, block);
    }
    public LegacyAlembicLogic.State snapshot() { return state; }

    private void commit(LegacyAlembicLogic.State next) {
        if (state.equals(next)) return;
        state = next;
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);
            level.updateNeighbourForOutputSignal(worldPosition,getBlockState().getBlock());
        }
    }

    /** Called by the original TileSmelter column search, never a free tick. */
    public boolean acceptFromSmelter(String aspect) {
        if (level == null || level.isClientSide()) return false;
        var added=LegacyAlembicLogic.add(state,aspect,1);
        if (added.transferred()!=1) return false;
        commit(added.state());
        return true;
    }

    /** Source label behavior: learns contents, or accepts a pre-typed label. */
    public boolean applyLabel(String requested, Direction side) {
        if (level == null || level.isClientSide() || side == null
                || !side.getAxis().isHorizontal() || state.filter()!=null) return false;
        String selected=state.amount()>0?state.aspect():requested;
        if(selected==null || ASPECTS.get(selected)==null) return false;
        commit(new LegacyAlembicLogic.State(state.aspect(),state.amount(),
                selected,side.get3DDataValue()));
        return true;
    }
    public boolean removeLabel(Direction side) {
        if(level==null||level.isClientSide()||side==null||state.filter()==null
                ||state.labelSide()!=side.get3DDataValue())return false;
        commit(new LegacyAlembicLogic.State(state.aspect(),state.amount(),null,0));
        return true;
    }

    /** Original BlockAlembic shift/empty hand: contents become world Flux. */
    public boolean releaseToAura() {
        if(!(level instanceof ServerLevel server)||state.amount()<=0)return false;
        int released=state.amount();
        ThaumcraftAuraSavedData.get(server).polluteFlux(worldPosition,released);
        commit(new LegacyAlembicLogic.State(null,0,state.filter(),state.labelSide()));
        return true;
    }

    @Override public boolean transportConnects(Direction face) {
        return face!=null&&LegacyAlembicLogic.canOutput(state,face.get3DDataValue());
    }
    @Override public boolean canTransportOutput(Direction face) {return transportConnects(face);}
    @Override public boolean canTransportInput(Direction face) {return false;}
    @Override public int transportSuction(Direction face) {return 0;}
    @Override public String transportSuctionAspect(Direction face) {return null;}
    @Override public int transportMinimumSuction() {return 0;}
    @Override public int transportAmount(Direction face) {
        return transportConnects(face)?state.amount():0;
    }
    @Override public String transportAspect(Direction face) {
        return transportConnects(face)?state.aspect():null;
    }
    @Override public int takeTransport(String aspect,int amount,Direction face) {
        if(level==null||level.isClientSide()||!canTransportOutput(face)||amount<=0)return 0;
        var result=LegacyAlembicLogic.take(state,aspect,amount);
        if(result.transferred()!=amount)return 0;
        commit(result.state());
        return result.transferred();
    }
    @Override public int addTransport(String aspect,int amount,Direction face) {return 0;}

    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries) {
        super.saveAdditional(tag,registries);
        tag.putShort("amount",(short)state.amount());
        if(state.aspect()!=null)tag.putString("aspect",state.aspect());
        if(state.filter()!=null)tag.putString("AspectFilter",state.filter());
        tag.putByte("facing",(byte)state.labelSide());
    }
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries) {
        super.loadAdditional(tag,registries);
        String aspect=tag.contains("aspect",Tag.TAG_STRING)?tag.getString("aspect"):null;
        String filter=tag.contains("AspectFilter",Tag.TAG_STRING)?tag.getString("AspectFilter"):null;
        int amount=Math.clamp(tag.getInt("amount"),0,LegacyAlembicLogic.CAPACITY);
        if(aspect==null||aspect.isBlank())amount=0;
        int face=Math.clamp(tag.getInt("facing"),0,5);
        state=new LegacyAlembicLogic.State(aspect,amount,filter,face);
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag=new CompoundTag();saveAdditional(tag,registries);return tag;
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
