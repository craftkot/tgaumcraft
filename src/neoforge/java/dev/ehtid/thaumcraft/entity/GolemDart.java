package dev.ehtid.thaumcraft.entity;

import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.port.core.golem.GolemRangedRules;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

/** Real server projectile; original NPC dart has no ammo debit and DISALLOWED arrow pickup. */
public final class GolemDart extends AbstractArrow {
    public GolemDart(EntityType<? extends GolemDart> type,Level level) { super(type,level); }
    public GolemDart(Level level,LivingEntity owner) {
        this(GolemModule.GOLEM_DART.get(),level); setOwner(owner);
        setPos(owner.getX(),owner.getEyeY()-(double).1F,owner.getZ()); pickup=Pickup.DISALLOWED;
    }
    @Override protected ItemStack getDefaultPickupItem() { return new ItemStack(Items.ARROW); }
    @Override protected double getDefaultGravity() { return (double).05F; }
    public void shootLegacy(double x,double y,double z,float speed,float inaccuracy) {
        var motion=GolemRangedRules.launch(x,y,z,speed,inaccuracy,random.nextGaussian(),random.nextGaussian(),random.nextGaussian());
        setDeltaMovement(motion.x(),motion.y(),motion.z());
        setYRot((float)(Mth.atan2(motion.x(),motion.z())*180D/Math.PI));
        setXRot((float)(Mth.atan2(motion.y(),(float)Math.sqrt(motion.x()*motion.x()+motion.z()*motion.z()))*180D/Math.PI));
        yRotO=getYRot(); xRotO=getXRot(); hasImpulse=true;
    }
}
