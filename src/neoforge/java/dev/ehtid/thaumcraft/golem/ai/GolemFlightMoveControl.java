package dev.ehtid.thaumcraft.golem.ai;

import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import dev.ehtid.thaumcraft.port.core.golem.GolemFlightRules;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.control.MoveControl;

/** Source controller changes actual velocity, not a decorative flying animation or ground movement input. */
public final class GolemFlightMoveControl extends MoveControl {
    private final ThaumcraftGolem golem;
    public GolemFlightMoveControl(ThaumcraftGolem golem) { super(golem); this.golem=golem; }
    @Override public void tick() {
        if(operation!=Operation.MOVE_TO) return;
        var velocity=golem.getDeltaMovement();
        var step=GolemFlightRules.move(new GolemFlightRules.Vector(wantedX-golem.getX(),wantedY-golem.getY(),wantedZ-golem.getZ()),
                new GolemFlightRules.Vector(velocity.x,velocity.y,velocity.z),speedModifier,golem.getBoundingBox().getSize());
        var result=step.velocity(); golem.setDeltaMovement(result.x(),result.y(),result.z());
        if(step.arrived()) { operation=Operation.WAIT; return; }
        var target=golem.getTarget();
        double x=target==null?result.x():target.getX()-golem.getX();
        double z=target==null?result.z():target.getZ()-golem.getZ();
        golem.setYRot(-(float)Mth.atan2(x,z)*57.295776F); golem.yBodyRot=golem.getYRot();
    }
}
