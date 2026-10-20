package dev.ehtid.thaumcraft.port.core.golem;

/** BETA26 AIArrowAttack/GolemArmDart plus official MC1.12.2 EntityArrow Gaussian launch. */
public final class GolemRangedRules {
    public static final int MIN_DELAY=20,MAX_DELAY=25,VISIBLE_TICKS=20;
    public static final float RANGE=16,VELOCITY=1.6F,INACCURACY=3;
    public static final double SPREAD=.007499999832361937D;
    public record Clock(int remaining,int seen) { public static Clock initial() { return new Clock(-1,0); } }
    public record Decision(Clock clock,boolean stopMoving,boolean shoot,float strength) {}
    public record Velocity(double x,double y,double z) {}
    private GolemRangedRules() {}
    public static float factor(double distanceSquared) { return (float)Math.sqrt(distanceSquared)/RANGE; }
    public static float strength(float factor) { return Math.max(.1F,Math.min(1F,factor)); }
    public static int delay(float factor) { return (int)Math.floor(factor*(MAX_DELAY-MIN_DELAY)+MIN_DELAY); }
    public static Decision tick(Clock old,double distanceSquared,boolean visible) {
        int seen=visible?old.seen+1:0,remaining=old.remaining-1;
        boolean within=distanceSquared<=RANGE*RANGE,stop=within&&seen>=VISIBLE_TICKS;
        if(remaining==0) {
            if(!within||!visible) return new Decision(new Clock(0,seen),stop,false,0);
            float factor=factor(distanceSquared); return new Decision(new Clock(delay(factor),seen),stop,true,strength(factor));
        }
        if(remaining<0) remaining=delay(factor(distanceSquared));
        return new Decision(new Clock(remaining,seen),stop,false,0);
    }
    public static double damage(double attackDamage,float strength,double gaussian) {
        return (double)((float)attackDamage/3F+strength)+gaussian*.25D;
    }
    public static double verticalAim(double targetMinY,float targetEyeHeight,float strength,double dartY) {
        return targetMinY+(double)targetEyeHeight+(double)(strength*strength)-dartY;
    }
    /** Source sqrt rounds to float before normalization; do not substitute modern triangular randomness. */
    public static Velocity launch(double x,double y,double z,float speed,float inaccuracy,double gx,double gy,double gz) {
        float length=(float)Math.sqrt(x*x+y*y+z*z);
        if(length==0||!Float.isFinite(length)) throw new IllegalArgumentException("Degenerate dart direction");
        return new Velocity((x/(double)length+gx*SPREAD*(double)inaccuracy)*(double)speed,
                (y/(double)length+gy*SPREAD*(double)inaccuracy)*(double)speed,
                (z/(double)length+gz*SPREAD*(double)inaccuracy)*(double)speed);
    }
}
