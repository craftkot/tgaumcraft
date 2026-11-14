package dev.ehtid.thaumcraft.golem.test;

import com.mojang.logging.LogUtils;
import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.entity.*;
import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.port.core.golem.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.UUID;

/** One real deterministic dart crosses disk in flight. READ freezes it at load until owner inspection, then resumes physics. */
@EventBusSubscriber(modid=ThaumcraftPort.MOD_ID)
public final class GolemDartRestartProbe {
    private static final boolean ENABLED="dart".equals(System.getProperty("thaumcraft.golemRestartScenario",""));
    private static final String PHASE=System.getProperty("thaumcraft.golemRestartPhase","");
    private static final UUID OWNER=UUID.fromString("860f4b13-107e-4100-a70c-2524340103e1"), WORKER=UUID.fromString("481312fd-f39b-4937-9322-e38c66a0e3b5"), VICTIM=UUID.fromString("0bef3942-0e99-4de8-b8f5-1b581036947b"), DART=UUID.fromString("ef797052-8000-49f9-93dd-12438658316c");
    private static final String CHECKPOINT="GolemDartCheckpoint";
    private static final GolemDesign DESIGN=GolemDesign.fromIds(3,1,4,0,0,0);
    private static MinecraftServer active; private static int ticks,phaseTick; private static boolean prepared,checked,impact,newShot,enabledAgain,loadedFromDisk;
    private static GolemDart loadedDart; private static CompoundTag loadedState;
    private GolemDartRestartProbe() {}
    @SubscribeEvent public static void joined(EntityJoinLevelEvent event) {
        if(!ENABLED||!PHASE.equals("read")||!(event.getLevel() instanceof ServerLevel)||!(event.getEntity() instanceof GolemDart dart)) return;
        if(dart.getUUID().equals(DART)) {
            // Can happen before ServerStartedEvent; keep this exact pre-tick loaded state.
            loadedDart=dart; loadedState=dart.saveWithoutId(new CompoundTag()); loadedFromDisk=event.loadedFromDisk(); dart.setDeltaMovement(Vec3.ZERO); dart.setNoGravity(true);
        } else if(!event.loadedFromDisk()&&dart.getOwner()!=null&&dart.getOwner().getUUID().equals(WORKER)) newShot=true;
    }
    @SubscribeEvent public static void hit(LivingIncomingDamageEvent event) {
        if(!ENABLED||!PHASE.equals("read")||!event.getEntity().getUUID().equals(VICTIM)) return;
        var projectile=event.getSource().getDirectEntity(); var owner=event.getSource().getEntity();
        if(projectile instanceof GolemDart&&projectile.getUUID().equals(DART)&&owner!=null&&owner.getUUID().equals(WORKER)) { impact=true; LogUtils.getLogger().info("GOLEM_DART_RESTART_NATIVE_IMPACT_OBSERVED"); }
    }
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if(!ENABLED||!PHASE.equals("write")&&!PHASE.equals("read")) return;
        active=event.getServer(); ticks=phaseTick=0; prepared=checked=impact=newShot=enabledAgain=false; active.overworld().setChunkForced(0,0,true); active.overworld().getChunk(0,0);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { if(active==event.getServer()) active=null; }
    private static Vec3 vector(CompoundTag tag,String key) { var v=tag.getList(key,Tag.TAG_DOUBLE); return new Vec3(v.getDouble(0),v.getDouble(1),v.getDouble(2)); }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if(active!=event.getServer()||checked||++ticks<60) return;
        var level=active.overworld(); boolean ready=level.areEntitiesLoaded(ChunkPos.asLong(0,0))&&level.isPositionEntityTicking(new BlockPos(8,120,8)); if(!ready&&ticks<600) return;
        try {
            require(ready,"Actual ticking fixture chunk");
            if(PHASE.equals("write")&&!prepared) {
                require(level.getEntity(WORKER)==null&&level.getEntity(VICTIM)==null&&level.getEntity(DART)==null,"Fresh isolated dart world"); level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,active); level.setDayTime(6000);
                for(int x=1;x<=14;x++) for(int z=1;z<=14;z++) { level.setBlockAndUpdate(new BlockPos(x,119,z),Blocks.STONE.defaultBlockState()); for(int y=120;y<=125;y++) level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState()); }
                var victim=EntityType.HUSK.create(level); victim.setUUID(VICTIM); victim.moveTo(12.5,120,12.5); victim.setNoAi(true); victim.setPersistenceRequired(); victim.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100); victim.setHealth(100); require(level.addFreshEntity(victim),"One real persistent target");
                var g=GolemModule.GOLEM.get().create(level); g.setUUID(WORKER); g.moveTo(4.5,120,4.5); g.initialize(DESIGN,OWNER,new BlockPos(4,120,4),0); g.setNoAi(true); var cargo=new ItemStack(Items.GOLD_INGOT,7); cargo.set(DataComponents.CUSTOM_NAME,Component.literal("Dart actual cargo")); g.setItemSlot(EquipmentSlot.MAINHAND,cargo); require(level.addFreshEntity(g),"One real source ranged worker");
                var dart=new GolemDart(level,g); dart.setUUID(DART); float factor=GolemRangedRules.strength(GolemRangedRules.factor(g.distanceToSqr(victim))); dart.setBaseDamage(GolemRangedRules.damage(g.getAttributeValue(Attributes.ATTACK_DAMAGE),factor,0));
                // Zero spread only in this persistence fixture. Normal automatic shots retain source Gaussian spread3.
                dart.shootLegacy(victim.getX()-g.getX(),GolemRangedRules.verticalAim(victim.getBoundingBox().minY,victim.getEyeHeight(),factor,dart.getY()),victim.getZ()-g.getZ(),GolemRangedRules.VELOCITY,0); require(level.addFreshEntity(dart),"One actual source projectile"); prepared=true; phaseTick=ticks; return;
            }
            require(level.getEntity(WORKER) instanceof ThaumcraftGolem&&level.getEntity(VICTIM) instanceof LivingEntity,"Actual worker/target UUIDs restored"); var g=(ThaumcraftGolem)level.getEntity(WORKER); var victim=(LivingEntity)level.getEntity(VICTIM);
            require(g.design().equals(DESIGN)&&g.ownerId().equals(OWNER)&&g.ranged()&&g.rankXp()==0&&g.getMainHandItem().is(Items.GOLD_INGOT)&&g.getMainHandItem().getCount()==7&&g.getMainHandItem().getHoverName().getString().equals("Dart actual cargo"),"Exact ranged configuration/owner/namedcargo7/no fabricated rangedXP");
            if(PHASE.equals("write")) {
                if(ticks-phaseTick<1) return; require(level.getEntity(DART) instanceof GolemDart,"Actual flight entity still exists"); var dart=(GolemDart)level.getEntity(DART);
                require(dart.getOwner()==g&&dart.pickup==AbstractArrow.Pickup.DISALLOWED&&!dart.isNoGravity()&&dart.position().distanceToSqr(new Vec3(g.getX(),g.getEyeY()-(double).1F,g.getZ()))>.1&&victim.getHealth()==100,"One native flight tick before disk, correct owner/no pickup, no premature hit");
                g.getPersistentData().put(CHECKPOINT,dart.saveWithoutId(new CompoundTag()));
            } else {
                if(!prepared) {
                    var expected=g.getPersistentData().getCompound(CHECKPOINT); require(loadedDart!=null&&loadedState!=null&&loadedFromDisk&&!expected.isEmpty(),"Actual disk entity join and existing NeoForge persistent data snapshot");
                    require(loadedDart.getUUID().equals(DART)&&loadedDart.getOwner()==g&&loadedDart.getBaseDamage()==expected.getDouble("damage")&&loadedDart.pickup==AbstractArrow.Pickup.DISALLOWED&&vector(loadedState,"Pos").distanceToSqr(vector(expected,"Pos"))<1e-16&&vector(loadedState,"Motion").distanceToSqr(vector(expected,"Motion"))<1e-16&&!loadedState.getBoolean("NoGravity"),"Exact pre-freeze projectile UUID/position/motion/damage/owner/no pickup restored");
                    require(victim.getHealth()==100&&g.isNoAi(),"Target/worker did not run ahead of cold inspection"); loadedDart.setNoGravity(false); loadedDart.setDeltaMovement(vector(loadedState,"Motion")); prepared=true; phaseTick=ticks; return;
                }
                if(!impact||victim.getHealth()>=100||!loadedDart.isRemoved()) { require(ticks-phaseTick<180,"Resumed original projectile causes real native owner-attributed damage"); return; }
                if(!enabledAgain) { g.setTarget(victim); g.setNoAi(false); enabledAgain=true; phaseTick=ticks; return; }
                require(ticks-phaseTick<200||newShot,"Restored ranged goal resumes actual automatic fire"); if(!newShot||ticks-phaseTick<100) return;
                require(level.getEntity(DART)==null,"Original impact did not replay/recreate the old projectile UUID");
            }
            var box=new AABB(new BlockPos(8,120,8)).inflate(24);
            require(level.getEntitiesOfClass(ThaumcraftGolem.class,box).size()==1&&level.getEntitiesOfClass(ItemEntity.class,box,e->e.getItem().is(Items.GOLD_INGOT)).isEmpty(),"One actual worker and no duplicated cargo items");
            checked=true; LogUtils.getLogger().info("GOLEM_DART_RESTART_{}_ASSERTIONS_OK",PHASE.toUpperCase(java.util.Locale.ROOT));
        } catch(RuntimeException|AssertionError ex) { checked=true; LogUtils.getLogger().error("GOLEM_DART_RESTART_ASSERTIONS_FAILED",ex); }
        if(checked) active.halt(false);
    }
    private static void require(boolean value,String message) { if(!value) throw new AssertionError(message); }
}
