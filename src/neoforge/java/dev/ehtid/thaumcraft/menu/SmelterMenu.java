package dev.ehtid.thaumcraft.menu;

import dev.ehtid.thaumcraft.blockentity.SmelterBlockEntity;
import dev.ehtid.thaumcraft.registry.ThaumcraftMenus;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * BETA26 ContainerSmelter layout, input/fuel 0..1, player slots 2..37.
 * All inventory mutations execute in the server-owned BlockEntity.
 */
public final class SmelterMenu extends AbstractContainerMenu {
    private final Container smelter;
    private final DataSlot[] bars=new DataSlot[5];

    public SmelterMenu(int id,Inventory inventory) {
        this(id,inventory,new SimpleContainer(2),false);
    }
    public SmelterMenu(int id,Inventory inventory,Container smelter) {
        this(id,inventory,smelter,true);
    }
    private SmelterMenu(int id,Inventory inventory,Container smelter,boolean server) {
        super(ThaumcraftMenus.SMELTER_BASIC.get(),id);
        checkContainerSize(smelter,2);
        this.smelter=smelter;
        smelter.startOpen(inventory.player);
        for(int i=0;i<5;i++){
            final int property=i;
            DataSlot data=server?new DataSlot(){
                @Override public int get(){
                    if(!(smelter instanceof SmelterBlockEntity tile))return 0;
                    return switch(property) {
                        case 0 -> tile.cookTime();
                        case 1 -> tile.burnTime();
                        case 2 -> tile.currentFuelBurnTime();
                        case 3 -> tile.storedVis();
                        case 4 -> tile.cookTarget();
                        default -> 0;
                    };
                }
                @Override public void set(int ignored){}
            }:DataSlot.standalone();
            bars[i]=data;
            addDataSlot(data);
        }
        addSlot(new Slot(smelter,0,80,8){
            @Override public boolean mayPlace(ItemStack stack){
                return smelter.canPlaceItem(0,stack);
            }
        });
        addSlot(new Slot(smelter,1,80,48){
            @Override public boolean mayPlace(ItemStack stack){
                return smelter.canPlaceItem(1,stack);
            }
        });
        for(int row=0;row<3;row++)
            for(int col=0;col<9;col++)
                addSlot(new Slot(inventory,9+row*9+col,8+col*18,84+row*18));
        for(int col=0;col<9;col++)
            addSlot(new Slot(inventory,col,8+col*18,142));
    }
    public int cookProgress(){return bars[0].get();}
    public int burnRemaining(){return bars[1].get();}
    public int burnTotal(){return bars[2].get();}
    public int storedVis(){return bars[3].get();}
    public int smeltDuration(){return bars[4].get();}

    @Override public boolean stillValid(Player player){return smelter.stillValid(player);}
    @Override public void removed(Player player) {
        super.removed(player);
        smelter.stopOpen(player);
    }
    @Override public ItemStack quickMoveStack(Player player,int slotIndex) {
        if(slotIndex<0||slotIndex>=slots.size())return ItemStack.EMPTY;
        Slot slot=slots.get(slotIndex);
        if(!slot.hasItem())return ItemStack.EMPTY;
        ItemStack stack=slot.getItem();
        ItemStack original=stack.copy();
        if(slotIndex<2){
            if(!moveItemStackTo(stack,2,38,true))return ItemStack.EMPTY;
        }else{
            boolean moved=false;
            if(smelter.canPlaceItem(1,stack))
                moved=moveItemStackTo(stack,1,2,false);
            if(!moved&&smelter.canPlaceItem(0,stack))
                moved=moveItemStackTo(stack,0,1,false);
            if(!moved) {
                if(slotIndex<29)moved=moveItemStackTo(stack,29,38,false);
                else moved=moveItemStackTo(stack,2,29,false);
            }
            if(!moved)return ItemStack.EMPTY;
        }
        if(stack.isEmpty())slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        slot.onTake(player,stack);
        return original;
    }
}
