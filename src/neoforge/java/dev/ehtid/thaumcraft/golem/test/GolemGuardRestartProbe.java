package dev.ehtid.thaumcraft.golem.test;

import com.mojang.logging.LogUtils;
import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.golem.seal.GolemSeals;
import dev.ehtid.thaumcraft.port.core.golem.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.UUID;

/** Actual living UUID and fighter cargo cross disk; transient attack/task is reconstructed, not serialized. */
@EventBusSubscriber(modid=ThaumcraftPort.MOD_ID)
public final class GolemGuardRestartProbe {
    private static final String SCENARIO=System.getProperty("thaumcraft.golemRestartScenario","");
    private static final boolean CLIMBER="climber".equals(SCENARIO), FLYER="flyer".equals(SCENARIO), ENABLED=CLIMBER||FLYER||"guard".equals(SCENARIO);
    private static final String PHASE=System.getProperty("thaumcraft.golemRestartPhase","");
    private static final UUID OWNER=UUID.fromString("c352cad3-9aa7-4f6b-bb70-55abc1ca568c"), WORKER=UUID.fromString("c863cdd2-6ec0-42be-8280-2ee339ca98f2"), VICTIM=UUID.fromString("3094f521-dc9c-46e9-a0c4-2a5083f142b7");
    private static final GolemSeals.SealKey KEY=new GolemSeals.SealKey(new BlockPos(8,CLIMBER||FLYER?124:119,8),Direction.UP);
    private static final GolemCombatRules.Settings SETTINGS=new GolemCombatRules.Settings(false,true,true);
    private static final GolemDesign DESIGN=GolemDesign.fromIds(3,1,2,FLYER?3:CLIMBER?2:1,0,0);
    private static MinecraftServer active; private static int ticks,phaseTick,beforeKill; private static boolean prepared,checked,observedKill,climbed;
    private GolemGuardRestartProbe() {}
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if(!ENABLED||!PHASE.equals("write")&&!PHASE.equals("read")) return;
        active=event.getServer(); ticks=phaseTick=0; prepared=checked=observedKill=climbed=false; beforeKill=-1; active.overworld().setChunkForced(0,0,true); active.overworld().getChunk(0,0);
    }
    @SubscribeEvent public static void death(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        if(active==null||!PHASE.equals("read")||!event.getEntity().getUUID().equals(VICTIM)) return;
        var attacker=event.getSource().getEntity();
        if(attacker instanceof ThaumcraftGolem&&attacker.getUUID().equals(WORKER)) { observedKill=true; beforeKill=((ThaumcraftGolem)attacker).rankXp(); LogUtils.getLogger().info("GOLEM_{}_RESTART_NATIVE_DEATH_OBSERVED",SCENARIO.toUpperCase(java.util.Locale.ROOT)); }
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { if(active==event.getServer()) active=null; }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if(active!=event.getServer()||checked||++ticks<60) return;
        var level=active.overworld(); boolean ready=level.areEntitiesLoaded(ChunkPos.asLong(0,0))&&level.isPositionEntityTicking(KEY.pos()); if(!ready&&ticks<600) return;
        try {
            require(ready,"Actual ticking fixture chunk"); var data=GolemSeals.get(level);
            if(PHASE.equals("write")&&!prepared) {
                require(level.getEntity(WORKER)==null&&level.getEntity(VICTIM)==null&&!data.contains(KEY.pos(),KEY.face()),"Fresh isolated guard world");
                level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,active); level.setDayTime(6000);
                for(int x=1;x<=14;x++) for(int z=1;z<=14;z++) { level.setBlockAndUpdate(new BlockPos(x,119,z),Blocks.STONE.defaultBlockState()); for(int y=120;y<125;y++) level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState()); }
                if(CLIMBER) for(int x=8;x<=14;x++) for(int z=1;z<=14;z++) for(int y=120;y<=124;y++) level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.STONE.defaultBlockState());
                if(FLYER) level.setBlockAndUpdate(KEY.pos(),Blocks.STONE.defaultBlockState());
                data.place(KEY.pos(),KEY.face(),OWNER,GolemSeals.Type.GUARD_ADVANCED); data.configureControls(data.get(KEY),OWNER,3,4,true,false); data.configureArea(data.get(KEY),OWNER,new BlockPos(4,1,3)); data.configureCombat(data.get(KEY),OWNER,SETTINGS);
                var victim=EntityType.HUSK.create(level); victim.setUUID(VICTIM); victim.moveTo(CLIMBER||FLYER?8.5:10.5,CLIMBER||FLYER?125:120,8.5); victim.setNoAi(true); victim.setPersistenceRequired(); victim.setHealth(4); require(level.addFreshEntity(victim),"One actual persistent hostile");
                var g=GolemModule.GOLEM.get().create(level); g.setUUID(WORKER); g.moveTo(4.5,120,8.5); g.initialize(DESIGN,OWNER,KEY.pos(),0); g.setGolemColor((byte)4); g.setNoAi(true); var cargo=new ItemStack(Items.GOLD_INGOT,7); cargo.set(DataComponents.CUSTOM_NAME,Component.literal("Guard actual cargo")); g.setItemSlot(EquipmentSlot.MAINHAND,cargo); require(level.addFreshEntity(g),"One actual paid fixture cargo worker"); prepared=true; phaseTick=ticks; return;
            }
            require(level.getEntity(WORKER) instanceof ThaumcraftGolem,"Exact worker UUID present"); var g=(ThaumcraftGolem)level.getEntity(WORKER); var seal=data.get(KEY);
            require(g.design().equals(DESIGN)&&g.ownerId().equals(OWNER)&&g.golemColor()==4&&g.getMainHandItem().is(Items.GOLD_INGOT)&&g.getMainHandItem().getCount()==7&&g.getMainHandItem().getHoverName().getString().equals("Guard actual cargo"),"Exact fighter/owner/color/named actual cargo7 restored");
            require(seal!=null&&seal.type()==GolemSeals.Type.GUARD_ADVANCED&&seal.locked()&&seal.priority()==3&&seal.color()==4&&seal.area().equals(new BlockPos(4,1,3))&&data.combatSettings(seal).equals(SETTINGS),"Actual typed guard flags/area/control survive disk");
            if(CLIMBER) require(g.getNavigation() instanceof net.minecraft.world.entity.ai.navigation.WallClimberNavigation,"Actual cold NBT rebuilds the source climber navigator");
            if(FLYER) require(g.getNavigation() instanceof dev.ehtid.thaumcraft.golem.ai.GolemFlightNavigation&&g.getMoveControl() instanceof dev.ehtid.thaumcraft.golem.ai.GolemFlightMoveControl&&g.isNoGravity(),"Cold packed NBT rebuilds actual flight controller/navigation/gravity");
            if(g.onClimbable()||FLYER&&g.getY()>123) climbed=true;
            if(PHASE.equals("write")) {
                if(ticks-phaseTick<22) return;
                var assignment=data.claimEntity(g,e->true).orElseThrow(); require(data.startCombat(level,g,assignment)&&g.rankXp()==1&&g.getTarget()!=null&&g.getTarget().getUUID().equals(VICTIM)&&g.getTarget().getHealth()==4,"One source assignmentXP1 before disk, no actual damage yet");
            } else {
                if(!prepared) { require(g.rankXp()==1&&g.getTarget()==null&&level.getEntity(VICTIM) instanceof LivingEntity living&&living.getHealth()==4,"Actual hostile survived while transient attack target did not serialize"); prepared=true; phaseTick=ticks; g.setNoAi(false); return; }
                if(!observedKill) { require(ticks-phaseTick<350,"Native restarted guard actually assigns, navigates and kills original hostile"); return; }
                require(!(level.getEntity(VICTIM) instanceof LivingEntity victim)||!victim.isAlive(),"Actual original victim remains dead after observed native damage event");
                require(beforeKill>=2&&g.rankXp()==beforeKill+8&&(!(CLIMBER||FLYER)||climbed),"Saved plus actual fresh assignments, exact native kill8, actual climb where selected, no stale replay"); if(ticks-phaseTick<130) return;
            }
            require(level.getEntitiesOfClass(ThaumcraftGolem.class,new AABB(KEY.pos()).inflate(20)).size()==1&&level.getEntitiesOfClass(ItemEntity.class,new AABB(KEY.pos()).inflate(20),e->e.getItem().is(Items.GOLD_INGOT)).isEmpty(),"One worker/no duplicated actual cargo in world");
            checked=true; LogUtils.getLogger().info("GOLEM_{}_RESTART_{}_ASSERTIONS_OK",SCENARIO.toUpperCase(java.util.Locale.ROOT),PHASE.toUpperCase(java.util.Locale.ROOT));
        } catch(RuntimeException|AssertionError ex) { checked=true; LogUtils.getLogger().error("GOLEM_"+SCENARIO.toUpperCase(java.util.Locale.ROOT)+"_RESTART_ASSERTIONS_FAILED",ex); }
        if(checked) active.halt(false);
    }
    private static void require(boolean value,String message) { if(!value) throw new AssertionError(message); }
}
