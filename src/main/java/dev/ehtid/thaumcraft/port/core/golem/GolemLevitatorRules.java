package dev.ehtid.thaumcraft.port.core.golem;

/** BETA26 TileLevitator: range scan,1200credits perVis, paid per-entity directional force. */
public final class GolemLevitatorRules {
    private GolemLevitatorRules() {}
    private static final int[] RANGES = {4, 8, 16, 32};
    public static final int DEFAULT_RANGE_INDEX = 1;
    public enum Direction {
        DOWN(0,-1,0), UP(0,1,0), NORTH(0,0,-1), SOUTH(0,0,1), WEST(-1,0,0), EAST(1,0,0);
        public final int x,y,z;
        Direction(int x,int y,int z) { this.x=x; this.y=y; this.z=z; }
    }
    public record Scan(int actualRange,int counter) {}
    public record Motion(double x,double y,double z) {}

    public static int range(int index) {
        if(index<0 || index>=RANGES.length) throw new IllegalArgumentException("Invalid levitator range index");
        return RANGES[index];
    }
    public static int cost(int index) { return range(index)*2; }
    public static int nextProbe(int index,int counter) { return 1+Math.floorMod(counter,range(index)); }
    public static Scan scan(int index,int actualRange,int counter,boolean opaque) {
        if(actualRange>range(index)) actualRange=0;
        int distance=nextProbe(index,counter);
        if(opaque) {
            if(distance<actualRange) actualRange=distance;
            return new Scan(actualRange,0);
        }
        return new Scan(Math.max(actualRange,distance),counter+1);
    }
    /** Preserve source fractional conversion and negative per-entity remainder. */
    public static int refill(int credits,float drainedVis) {
        if(!Float.isFinite(drainedVis) || drainedVis<0 || drainedVis>1) throw new IllegalArgumentException("Invalid aura drain");
        return credits<10 ? (int)(credits+drainedVis*1200F) : credits;
    }
    public static int afterEntity(int credits,int index) { return credits-cost(index); }
    public static boolean eligible(boolean item,boolean pushable,boolean horse) { return item || pushable || horse; }
    public static Motion motion(double x,double y,double z,Direction facing,boolean sneaking,boolean onGround) {
        if(sneaking && facing==Direction.UP) return new Motion(x,y<0?y*(double).9F:y,z);
        x+=(double)(.1F*facing.x); y+=(double)(.1F*facing.y); z+=(double)(.1F*facing.z);
        if(facing.y==0 && !onGround) { if(y<0) y*=(double).9F; y+=(double).08F; }
        return new Motion(clamp(x),clamp(y),clamp(z));
    }
    private static double clamp(double speed) { return Math.max(-(double).35F,Math.min((double).35F,speed)); }
}
