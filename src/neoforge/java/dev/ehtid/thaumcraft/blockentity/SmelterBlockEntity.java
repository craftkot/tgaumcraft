package dev.ehtid.thaumcraft.blockentity;

import dev.ehtid.thaumcraft.port.core.AspectData;
import dev.ehtid.thaumcraft.port.core.AspectListData;
import dev.ehtid.thaumcraft.port.core.AspectRegistry;
import dev.ehtid.thaumcraft.port.core.LegacyAlembicLogic;
import dev.ehtid.thaumcraft.port.core.LegacySmelterLogic;
import dev.ehtid.thaumcraft.registry.ThaumcraftBlockEntities;
import dev.ehtid.thaumcraft.registry.ThaumcraftItems;
import dev.ehtid.thaumcraft.block.SmelterBlock;
import dev.ehtid.thaumcraft.scanning.ThaumometerScanDispatcher;
import dev.ehtid.thaumcraft.world.ThaumcraftAuraSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Live BETA26 basic TileSmelter: stored fuel, two persisted inventory slots,
 * real scanned item aspects, server-tick cooking, losses polluting world aura,
 * and direct transfer into a contiguous vertical TileAlembic column.
 *
 * Original gui/container automation and auxiliary blocks are NOT implemented;
 * this block is registered for runtime validation and later GUI integration.
 */
public final class SmelterBlockEntity extends BlockEntity implements Container {
    private static final AspectRegistry REGISTRY=new AspectRegistry();
    private static final int MAX_VIS=LegacySmelterLogic.ESSENTIA_CAPACITY;
    private final NonNullList<ItemStack> items=NonNullList.withSize(2,ItemStack.EMPTY);
    private AspectListData aspects=new AspectListData();
    private int burnTime;
    private int currentBurnTime;
    private int cookTime;
    private int cookTarget=100;
    private int ticks;
    private boolean speedBoost;
    private LegacySmelterLogic.Tier tier=LegacySmelterLogic.Tier.BASIC;

    public SmelterBlockEntity(BlockPos pos,BlockState state) {
        super(ThaumcraftBlockEntities.SMELTER_BASIC.get(),pos,state);
    }

    public int storedVis() {return aspects.visSize();}
    public int burnTime() {return burnTime;}
    public int currentFuelBurnTime() {return currentBurnTime;}
    public int cookTime() {return cookTime;}
    public int cookTarget() {return cookTarget;}
    public AspectListData storedAspects() {return aspects.copy();}
    private void sync() {
        setChanged();
        if(level!=null&&!level.isClientSide())
            level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);
    }

    private AspectListData currentInputTags() {
        ItemStack input=items.get(0);
        return input.isEmpty()?new AspectListData()
                :ThaumometerScanDispatcher.aspectsForCrucibleItem(input);
    }
    private boolean canSmelt(AspectListData input) {
        return input.size()>0 && LegacySmelterLogic.canHold(storedVis(),input.visSize());
    }

    /** Source TileAlembic.processAlembics scans one contiguous column upward. */
    private boolean transferOneToAlembic() {
        if(level==null || aspects.visSize()==0)return false;
        List<AlembicBlockEntity> column=new ArrayList<>();
        List<LegacyAlembicLogic.State> states=new ArrayList<>();
        for(int depth=1;depth<=32;depth++) {
            if(!(level.getBlockEntity(worldPosition.above(depth))
                    instanceof AlembicBlockEntity vessel))break;
            column.add(vessel);
            states.add(vessel.snapshot());
        }
        if(column.isEmpty())return false;
        for(AspectData aspect:aspects.getAspects()) {
            if(aspects.getAmount(aspect)<=0)continue;
            var plan=LegacyAlembicLogic.depositOne(states,aspect.tag());
            if(!plan.accepted())continue;
            if(column.get(plan.acceptedAt()).acceptFromSmelter(aspect.tag())) {
                aspects.remove(aspect,1);
                sync();
                return true;
            }
        }
        return false;
    }

    private void finishItem(ServerLevel server,AspectListData original) {
        // Preserve the original source random trial per aspect unit; never
        // create the distillation output without consuming one real item.
        if(!canSmelt(original)||items.get(0).isEmpty())return;
        int pollution=0;
        for(AspectData aspect:original.getAspects()) {
            int amount=original.getAmount(aspect);
            if(amount<=0)continue;
            float[] draws=new float[amount];
            for(int i=0;i<amount;i++)draws[i]=server.random.nextFloat();
            var result=LegacySmelterLogic.processUnits(tier,
                    "vitium".equals(aspect.tag()),draws);
            if(result.stored()>0)aspects.add(aspect,result.stored());
            pollution+=result.polluted();
        }
        items.get(0).shrink(1);
        if(pollution>0)ThaumcraftAuraSavedData.get(server)
                .polluteFlux(worldPosition,pollution);
        sync();
    }

    public static void serverTick(ServerLevel server,BlockPos pos,BlockState state,
                                  SmelterBlockEntity tile) {
        tile.ticks++;
        if(tile.burnTime>0)tile.burnTime--;
        // BETA26 basic transfers once every 15 ticks. The original
        // speed-boost source had int-cast 0.8 -> zero (division by zero).
        // Deliberately fix that crash while retaining the 20% speedup.
        int interval=LegacySmelterLogic.outputInterval(tile.tier);
        if(tile.speedBoost)interval=Math.max(1,Math.round(interval*0.8f));
        if(tile.ticks%interval==0 && tile.storedVis()>0)
            tile.transferOneToAlembic();

        AspectListData input=tile.currentInputTags();
        boolean canCook=tile.canSmelt(input);
        if(tile.burnTime==0 && canCook && !tile.items.get(1).isEmpty()) {
            ItemStack fuel=tile.items.get(1);
            int available=fuel.getBurnTime(RecipeType.SMELTING);
            if(available>0) {
                tile.burnTime=available;
                tile.currentBurnTime=available;
                tile.speedBoost=fuel.is(ThaumcraftItems.ALUMENTUM.get());
                ItemStack leftover=fuel.getCraftingRemainingItem();
                fuel.shrink(1);
                if(fuel.isEmpty())tile.items.set(1,leftover);
                tile.sync();
            }
        }
        boolean active=tile.burnTime>0;
        if(state.getValue(SmelterBlock.LIT)!=active)
            server.setBlock(pos,state.setValue(SmelterBlock.LIT,active),3);
        if(active && canCook) {
            tile.cookTarget=Math.max(1,LegacySmelterLogic.smeltTime(input.visSize(),0));
            tile.cookTime++;
            if(tile.cookTime>=tile.cookTarget) {
                tile.cookTime=0;
                tile.finishItem(server,input);
            }
        } else tile.cookTime=0;
        if(tile.ticks%20==0)tile.sync();
    }

    public void spillOnBreak(ServerLevel server) {
        if(storedVis()>0)
            ThaumcraftAuraSavedData.get(server).polluteFlux(worldPosition,storedVis());
        aspects=new AspectListData();
    }

    @Override public int getContainerSize(){return 2;}
    @Override public boolean isEmpty(){return items.get(0).isEmpty()&&items.get(1).isEmpty();}
    @Override public ItemStack getItem(int slot){
        return slot>=0&&slot<2?items.get(slot):ItemStack.EMPTY;
    }
    @Override public ItemStack removeItem(int slot,int count){
        if(slot<0||slot>=2||count<=0)return ItemStack.EMPTY;
        ItemStack removed=ContainerHelper.removeItem(items,slot,count);
        if(!removed.isEmpty())sync();
        return removed;
    }
    @Override public ItemStack removeItemNoUpdate(int slot){
        if(slot<0||slot>=2)return ItemStack.EMPTY;
        ItemStack removed=ContainerHelper.takeItem(items,slot);
        if(!removed.isEmpty())sync();
        return removed;
    }
    @Override public void setItem(int slot,ItemStack stack){
        if(slot<0||slot>=2||(!stack.isEmpty()&&!canPlaceItem(slot,stack)))return;
        items.set(slot,stack.copyWithCount(Math.min(stack.getCount(),64)));
        sync();
    }
    @Override public boolean canPlaceItem(int slot,ItemStack stack){
        return slot==0?ThaumometerScanDispatcher.aspectsForCrucibleItem(stack).size()>0
                :slot==1&&stack.getBurnTime(RecipeType.SMELTING)>0;
    }
    @Override public boolean stillValid(Player player){
        return level!=null&&level.getBlockEntity(worldPosition)==this
                &&player.distanceToSqr(worldPosition.getX()+0.5,
                worldPosition.getY()+0.5,worldPosition.getZ()+0.5)<=64.0;
    }
    @Override public void clearContent(){items.set(0,ItemStack.EMPTY);items.set(1,ItemStack.EMPTY);sync();}

    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.saveAdditional(tag,registries);
        ContainerHelper.saveAllItems(tag,items,registries);
        tag.putShort("BurnTime",(short)burnTime);
        tag.putShort("CookTime",(short)cookTime);
        tag.putBoolean("speedBoost",speedBoost);
        ListTag list=new ListTag();
        for(AspectData aspect:aspects.getAspects()){
            CompoundTag entry=new CompoundTag();
            entry.putString("key",aspect.tag());
            entry.putInt("amount",aspects.getAmount(aspect));
            list.add(entry);
        }
        tag.put("Aspects",list);
    }
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.loadAdditional(tag,registries);
        ContainerHelper.loadAllItems(tag,items,registries);
        burnTime=Math.max(0,tag.getShort("BurnTime"));
        cookTime=Math.max(0,tag.getShort("CookTime"));
        speedBoost=tag.getBoolean("speedBoost");
        aspects=new AspectListData();
        ListTag list=tag.getList("Aspects",Tag.TAG_COMPOUND);
        for(int i=0;i<list.size();i++){
            CompoundTag a=list.getCompound(i);
            AspectData aspect=REGISTRY.get(a.getString("key"));
            int count=a.getInt("amount");
            if(aspect!=null&&count>0&&count<=MAX_VIS&&aspects.visSize()+count<=MAX_VIS)
                aspects.add(aspect,count);
        }
    }
}
