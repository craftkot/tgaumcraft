package dev.ehtid.thaumcraft.port.core;

import java.util.Arrays;

/** BETA26 15-slot workbench inventory: 0..8 crafting, 9..14 primal crystals. */
public final class ArcaneWorkbenchRuntime {
    public static final int SLOT_COUNT=15, CRAFT_SLOTS=9, CRYSTAL_SLOTS=6;
    public static final String[] PRIMAL_ORDER={"aer","ignis","aqua","terra","ordo","perditio"};
    public record Stack(String itemId, int count, String aspect) {
        public Stack { if (count < 0) throw new IllegalArgumentException("count"); }
        public boolean empty(){ return count==0 || itemId==null; }
        public Stack shrink(int n){ return new Stack(itemId, Math.max(0,count-n), aspect); }
    }
    private final Stack[] slots=new Stack[SLOT_COUNT];
    public ArcaneWorkbenchRuntime(){ Arrays.fill(slots,new Stack(null,0,null)); }
    public Stack get(int slot){ return slots[slot]; }
    public void set(int slot, Stack stack){ slots[slot]=stack==null?new Stack(null,0,null):stack; }
    public boolean crystalSlotAccepts(int slot, Stack stack){
        int i=slot-9; return i>=0&&i<6&&stack!=null&&"thaumcraft:crystal_essence".equals(stack.itemId())&&PRIMAL_ORDER[i].equals(stack.aspect());
    }
    public ArcaneWorkbenchRuntime copy(){ ArcaneWorkbenchRuntime c=new ArcaneWorkbenchRuntime(); System.arraycopy(slots,0,c.slots,0,SLOT_COUNT); return c; }
    void consume(int slot,int n){ slots[slot]=slots[slot].shrink(n); }
    void replaceFrom(ArcaneWorkbenchRuntime other){ System.arraycopy(other.slots,0,slots,0,SLOT_COUNT); }
}
