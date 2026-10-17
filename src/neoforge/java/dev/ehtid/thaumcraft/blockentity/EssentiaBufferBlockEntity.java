package dev.ehtid.thaumcraft.blockentity;

import dev.ehtid.thaumcraft.block.EssentiaTubeBlock;
import dev.ehtid.thaumcraft.block.BellowsBlock;
import dev.ehtid.thaumcraft.port.core.AspectRegistry;
import dev.ehtid.thaumcraft.port.core.LegacyTubeBufferLogic;
import dev.ehtid.thaumcraft.registry.ThaumcraftBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Source TileTubeBuffer: 10-unit MIXED essentia, 0/1/2 choke on six faces. */
public final class EssentiaBufferBlockEntity extends BlockEntity implements EssentiaTransportPort {
    private static final AspectRegistry REGISTRY=new AspectRegistry();
    private LegacyTubeBufferLogic.State state=LegacyTubeBufferLogic.State.empty();
    private int openMask=63;
    private final byte[] choke=new byte[6];
    private int bellows;
    private int ticks;

    public EssentiaBufferBlockEntity(BlockPos pos,BlockState block){
        super(ThaumcraftBlockEntities.TUBE_BUFFER.get(),pos,block);
    }
    public LegacyTubeBufferLogic.State snapshot(){return state;}
    private void sync(){
        setChanged();
        if(level!=null&&!level.isClientSide())
            level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);
    }
    private boolean open(Direction direction){
        return direction!=null&&(openMask&(1<<direction.get3DDataValue()))!=0;
    }
    public boolean toggleSide(Direction face){
        if(face==null||level==null||level.isClientSide())return false;
        openMask^=1<<face.get3DDataValue();
        sync();
        EssentiaTubeBlock.updateConnections(level,worldPosition);
        EssentiaTubeBlock.updateConnections(level,worldPosition.relative(face));
        return true;
    }
    public boolean cycleChoke(Direction face){
        if(face==null||level==null||level.isClientSide())return false;
        int i=face.get3DDataValue();
        choke[i]=(byte)((choke[i]+1)%3);
        sync();return true;
    }
    public static void serverTick(ServerLevel server,BlockPos pos,BlockState block,
                                  EssentiaBufferBlockEntity buffer){
        buffer.ticks++;
        if(buffer.ticks%20==0){
            int attached=BellowsBlock.countActive(server,pos,Direction.values());
            if(attached!=buffer.bellows){buffer.bellows=attached;buffer.sync();}
        }
        if(buffer.ticks%5!=0||buffer.state.amount()>=LegacyTubeBufferLogic.CAPACITY)return;
        // Original buffer is passive unless it can actually debit an adjacent
        // physical transport. One unit maximum per 5 ticks.
        for(Direction face:Direction.values()){
            if(!buffer.canTransportInput(face))continue;
            if(!(server.getBlockEntity(pos.relative(face)) instanceof EssentiaTransportPort source))
                continue;
            Direction opposite=face.getOpposite();
            if(!source.transportConnects(opposite)||!source.canTransportOutput(opposite))
                continue;
            int own=buffer.transportSuction(face);
            if(own<=source.transportSuction(opposite)
                    ||own<source.transportMinimumSuction()
                    ||source.transportAmount(opposite)<=0)continue;
            String aspect=source.transportAspect(opposite);
            if(aspect==null||REGISTRY.get(aspect)==null)continue;
            var plan=LegacyTubeBufferLogic.add(buffer.state,aspect,1);
            if(plan.accepted()!=1)continue;
            if(source.takeTransport(aspect,1,opposite)==1){
                buffer.state=plan.state();
                buffer.sync();break;
            }
        }
    }

    /** A neighboring machine is higher priority when its compatible suction
     * beats the actual requester on another open side (original BETA26).
     */
    private boolean highestPriority(String aspect,Direction recipient){
        if(level==null)return true;
        int requested=0;
        var requester=level.getBlockEntity(worldPosition.relative(recipient));
        if(requester instanceof EssentiaTransportPort port)
            requested=port.transportSuction(recipient.getOpposite());
        Map<Integer,Integer> competitors=new HashMap<>();
        Map<Integer,String> types=new HashMap<>();
        for(Direction face:Direction.values()){
            if(face==recipient||!canTransportOutput(face))continue;
            if(!(level.getBlockEntity(worldPosition.relative(face)) instanceof EssentiaTransportPort next)
                    ||!next.transportConnects(face.getOpposite()))continue;
            competitors.put(face.get3DDataValue(),next.transportSuction(face.getOpposite()));
            types.put(face.get3DDataValue(),next.transportSuctionAspect(face.getOpposite()));
        }
        return LegacyTubeBufferLogic.mayOutputTo(requested,transportSuction(recipient),
                aspect,competitors,types,recipient.get3DDataValue());
    }

    @Override public boolean transportConnects(Direction face){return open(face);}
    @Override public boolean canTransportInput(Direction face){return open(face);}
    @Override public boolean canTransportOutput(Direction face){return open(face);}
    @Override public int transportSuction(Direction face){
        return !open(face)?0:LegacyTubeBufferLogic.suction(choke[face.get3DDataValue()],bellows);
    }
    @Override public String transportSuctionAspect(Direction face){return null;}
    @Override public int transportMinimumSuction(){return 0;}
    @Override public int transportAmount(Direction face){return open(face)?state.amount():0;}
    @Override public String transportAspect(Direction face){
        if(!open(face)||state.amount()==0)return null;
        int choice=level==null?0:level.random.nextInt(state.stored().size());
        return state.stored().keySet().stream().skip(choice).findFirst().orElse(null);
    }
    @Override public int takeTransport(String aspect,int amount,Direction face){
        if(level==null||level.isClientSide()||!canTransportOutput(face)
                ||amount<=0||!highestPriority(aspect,face))return 0;
        var plan=LegacyTubeBufferLogic.take(state,aspect,amount);
        if(plan.accepted()!=amount)return 0;
        state=plan.state();sync();return amount;
    }
    @Override public int addTransport(String aspect,int amount,Direction face){
        if(level==null||level.isClientSide()||!canTransportInput(face))return 0;
        var plan=LegacyTubeBufferLogic.add(state,aspect,amount);
        if(plan.accepted()!=1)return 0;
        state=plan.state();sync();return 1;
    }

    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.saveAdditional(tag,registries);
        tag.putByteArray("choke",choke);
        byte[] sides=new byte[6];
        for(int i=0;i<6;i++)sides[i]=(byte)((openMask&(1<<i))!=0?1:0);
        tag.putByteArray("open",sides);
        ListTag list=new ListTag();
        for(var entry:state.stored().entrySet()){
            CompoundTag item=new CompoundTag();
            item.putString("key",entry.getKey());
            item.putInt("amount",entry.getValue());
            list.add(item);
        }
        tag.put("Aspects",list);
    }
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.loadAdditional(tag,registries);
        byte[] open=tag.getByteArray("open");
        openMask=63;
        if(open.length==6){
            openMask=0;for(int i=0;i<6;i++)if(open[i]!=0)openMask|=1<<i;
        }
        byte[] sourceChoke=tag.getByteArray("choke");
        java.util.Arrays.fill(choke,(byte)0);
        if(sourceChoke.length>=6)
            for(int i=0;i<6;i++)choke[i]=(byte)Math.clamp(sourceChoke[i],0,2);
        Map<String,Integer> loaded=new LinkedHashMap<>();
        ListTag list=tag.getList("Aspects",Tag.TAG_COMPOUND);
        int total=0;
        for(int i=0;i<list.size();i++){
            CompoundTag item=list.getCompound(i);
            String aspect=item.getString("key");
            int amount=item.getInt("amount");
            if(REGISTRY.get(aspect)==null||amount<=0||total+amount>10)continue;
            loaded.merge(aspect,amount,Integer::sum);
            total+=amount;
        }
        state=new LegacyTubeBufferLogic.State(loaded);
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){
        CompoundTag tag=new CompoundTag();saveAdditional(tag,registries);return tag;
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
