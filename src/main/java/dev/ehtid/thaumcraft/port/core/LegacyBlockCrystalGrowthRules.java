package dev.ehtid.thaumcraft.port.core;

/**
 * Direct BETA26 BlockCrystal bytecode parity (source JAR SHA-256:
 * 9425f8643581b27ff8845b087c8bc6fc10425a32942f1a3f0e265ce6b38f7b5f).
 * Aura transactions are intentionally separated: none is silently simulated.
 */
public final class LegacyBlockCrystalGrowthRules {
    private LegacyBlockCrystalGrowthRules() {}
    public static final int MAX_SIZE=3, MIN_GEN=1, MAX_GEN=4, AURA_STEP=10;
    public record State(int size, int generation) {
        public State {
            if (size<0||size>MAX_SIZE||generation<MIN_GEN||generation>MAX_GEN)
                throw new IllegalArgumentException("BETA26 size/gen");
        }
    }
    public static State initial() { return new State(0,1); }
    public static int metadata(State s) { return s.size()|((s.generation()-1)<<2); }
    public static State fromMetadata(int meta) {
        return new State(meta&3,1+((meta>>2)&3));
    }
    public static int crystalDrops(State s) { return s.size()+1; }
    public static int tickDenominator(State s) { return 3+s.generation(); }
    /** Original random.nextInt(3+generation)==0. */
    public static boolean attemptTick(State s,int rolled) {
        if(rolled<0||rolled>=tickDenominator(s)) throw new IllegalArgumentException();
        return rolled==0;
    }
    /** Original initial getVis/getFlux uses <=10 to trigger shrinking. */
    public static boolean starved(float aura) { return aura<=AURA_STEP; }
    public static State shrink(State s) { return s.size()==0?null:new State(s.size()-1,s.generation()); }
    /** At size zero, source only breaks when touching ANOTHER BlockCrystal. */
    public static boolean removeStarved(State s,boolean touchingSameCrystal) {
        return s.size()==0&&touchingSameCrystal;
    }
    /** BETA26 growth requires > auraBase+10 and size < 5-gen+posLong%3. */
    public static boolean canGrow(State s,long packedPos,float aura,int auraBase) {
        return aura > auraBase+AURA_STEP
                && s.size()<MAX_SIZE
                && s.size()<5-s.generation()+packedPos%3;
    }
    public static State grow(State s) {
        if(s.size()==MAX_SIZE) throw new IllegalArgumentException("already full");
        return new State(s.size()+1,s.generation());
    }
    /** If cannot increase size and gen<4, attempt to spread to a neighbour. */
    public static boolean maySpread(State s,float aura,int auraBase) {
        return aura > auraBase+AURA_STEP&&s.generation()<MAX_GEN;
    }
    /** 1/6 chance nextInt(6)==0 reduces generation before new +1. */
    public static State child(State parent,boolean lucky) {
        if(parent.generation()>=MAX_GEN) throw new IllegalArgumentException("gen max");
        return new State(0,parent.generation()+(lucky?0:1));
    }
    /** BETA26 spreadCrystal: +/-1 on each axis; never same position. */
    public static boolean spreadOffset(int dx,int dy,int dz) {
        return dx>=-1&&dx<=1&&dy>=-1&&dy<=1&&dz>=-1&&dz<=1
                && (dx!=0||dy!=0||dz!=0);
    }
}
