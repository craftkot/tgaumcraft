package dev.ehtid.thaumcraft.port.core.golem;

/** BETA26 FlyingMoveControl: normalized anisotropic acceleration and arrival braking. */
public final class GolemFlightRules {
    public static final double HORIZONTAL_ACCELERATION=.033D, VERTICAL_ACCELERATION=.0125D, BRAKE=.5D;
    public static final int LOOK_AHEAD=6;
    public static final double SMOOTH_DISTANCE_SQUARED=36D;
    public record Vector(double x,double y,double z) {}
    public record Step(Vector velocity,boolean arrived) {}
    private GolemFlightRules() {}
    public static Step move(Vector delta,Vector velocity,double speed,double arrivalDistance) {
        if(!Double.isFinite(speed)||speed<0||!Double.isFinite(arrivalDistance)||arrivalDistance<=0) throw new IllegalArgumentException("Invalid flight control parameters");
        double distance=Math.sqrt(delta.x*delta.x+delta.y*delta.y+delta.z*delta.z);
        if(distance<arrivalDistance) return new Step(new Vector(velocity.x*BRAKE,velocity.y*BRAKE,velocity.z*BRAKE),true);
        return new Step(new Vector(velocity.x+delta.x/distance*HORIZONTAL_ACCELERATION*speed,
                velocity.y+delta.y/distance*VERTICAL_ACCELERATION*speed,
                velocity.z+delta.z/distance*HORIZONTAL_ACCELERATION*speed),false);
    }
}
