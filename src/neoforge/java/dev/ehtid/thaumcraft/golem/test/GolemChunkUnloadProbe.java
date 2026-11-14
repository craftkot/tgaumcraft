package dev.ehtid.thaumcraft.golem.test;

import com.mojang.logging.LogUtils;
import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.golem.inventory.GolemContainers;
import dev.ehtid.thaumcraft.golem.seal.GolemSeals;
import dev.ehtid.thaumcraft.port.core.golem.GolemDesign;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.*;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.UUID;

/** Opt-in dedicated fixture: release real chunk tickets, observe unload, then reload and resume actual work. */
@EventBusSubscriber(modid=ThaumcraftPort.MOD_ID)
public final class GolemChunkUnloadProbe {
    private static final boolean ENABLED="chunk".equals(System.getProperty("thaumcraft.golemRestartScenario",""));
    private static final int C=512,BASE=C*16;
    private static final UUID OWNER=UUID.fromString("939f6a38-64ab-4c90-8bac-6a167aa41f78"),WORKER=UUID.fromString("a84f939c-408c-4006-86cf-ff249ec943b2");
    private static final GolemDesign DESIGN=GolemDesign.fromIds(0,1,0,1,0,0);
    private static final GolemSeals.SealKey PICKUP=new GolemSeals.SealKey(pos(3,119,5),Direction.UP),FILL=new GolemSeals.SealKey(pos(11,120,5),Direction.UP);
    private static MinecraftServer active; private static int ticks,phase,phaseTick,oldXp,completedTick;
    private static boolean checked,chunkUnloaded,trackingEnded,loadedFromDisk;
    private static ThaumcraftGolem original; private static ItemEntity paid;
    private GolemChunkUnloadProbe() {}
    private static BlockPos pos(int x,int y,int z) { return new BlockPos(BASE+x,y,BASE+z); }
    @SubscribeEvent public static void unloaded(ChunkEvent.Unload e) {
        if(ENABLED&&active!=null&&e.getLevel()==active.overworld()&&e.getChunk().getPos().equals(new ChunkPos(C,C))) chunkUnloaded=true;
    }
    @SubscribeEvent public static void left(EntityLeaveLevelEvent e) {
        // Tracking ends at HIDDEN before PersistentEntitySectionManager assigns the final removal reason.
        if(ENABLED&&e.getEntity()==original) trackingEnded=true;
    }
    @SubscribeEvent public static void joined(EntityJoinLevelEvent e) {
        if(ENABLED&&phase>=3&&e.getEntity() instanceof ThaumcraftGolem&&e.getEntity().getUUID().equals(WORKER)&&e.loadedFromDisk()) loadedFromDisk=true;
    }
    @SubscribeEvent public static void started(ServerStartedEvent e) {
        if(!ENABLED||!e.getServer().isDedicatedServer()) return;
        active=e.getServer(); ticks=phase=phaseTick=completedTick=0; checked=chunkUnloaded=trackingEnded=loadedFromDisk=false;
        active.overworld().setChunkForced(C,C,true); active.overworld().getChunk(C,C);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e) { if(active==e.getServer()) active=null; }
    private static int stock(ServerLevel level) {
        var handler=GolemContainers.handler(level,FILL); require(handler!=null,"Existing real barrel capability"); int count=0;
        for(int i=0;i<handler.getSlots();i++) { var stack=handler.getStackInSlot(i); if(!stack.isEmpty()) { require(stack.is(Items.IRON_INGOT)&&stack.getHoverName().getString().equals("Chunk actual input"),"No substituted or invented cargo"); count+=stack.getCount(); } }
        return count;
    }
    private static int ground(ServerLevel level) { return level.getEntitiesOfClass(ItemEntity.class,new AABB(pos(8,120,8)).inflate(16),e->e.getItem().is(Items.IRON_INGOT)).stream().mapToInt(e->e.getItem().getCount()).sum(); }
    @SubscribeEvent public static void tick(ServerTickEvent.Post e) {
        if(active!=e.getServer()||checked||++ticks<60) return;
        var level=active.overworld();
        try {
            require(ticks<2200,"Bounded actual unload/reload scenario");
            if(phase==0) {
                if(!level.isPositionEntityTicking(pos(8,120,8))) return;
                var data=GolemSeals.get(level); require(!data.contains(PICKUP.pos(),PICKUP.face())&&!data.contains(FILL.pos(),FILL.face())&&level.getEntity(WORKER)==null,"Fresh isolated fixture world");
                level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,active); level.setDayTime(6000);
                for(int x=1;x<=14;x++) for(int z=1;z<=14;z++) { level.setBlockAndUpdate(pos(x,119,z),Blocks.STONE.defaultBlockState()); for(int y=120;y<=124;y++) level.setBlockAndUpdate(pos(x,y,z),Blocks.AIR.defaultBlockState()); }
                level.setBlockAndUpdate(FILL.pos(),Blocks.BARREL.defaultBlockState()); level.setBlockAndUpdate(FILL.pos().east(),Blocks.REDSTONE_BLOCK.defaultBlockState());
                require(data.place(PICKUP.pos(),PICKUP.face(),OWNER,GolemSeals.Type.PICKUP)&&data.place(FILL.pos(),FILL.face(),OWNER,GolemSeals.Type.FILL),"Actual Pickup/Fill seals");
                require(data.configureControls(data.get(FILL),OWNER,0,0,true,true)&&GolemSeals.stopped(level,data.get(FILL)),"Powered Fill is stopped through normal source redstone control");
                original=GolemModule.GOLEM.get().create(level); original.setUUID(WORKER); original.moveTo(Vec3.atBottomCenterOf(pos(6,120,6))); original.initialize(DESIGN,OWNER,pos(6,120,6),0); require(level.addFreshEntity(original),"One real enabled wheeled worker");
                var input=new ItemStack(Items.IRON_INGOT,13); input.set(DataComponents.CUSTOM_NAME,Component.literal("Chunk actual input")); paid=new ItemEntity(level,BASE+3.5,120,BASE+5.5,input); paid.setDeltaMovement(Vec3.ZERO); paid.setNoPickUpDelay(); require(level.addFreshEntity(paid),"Actual finite input13"); phase=1; phaseTick=ticks; return;
            }
            if(phase==1) {
                require(ground(level)+original.getMainHandItem().getCount()+stock(level)==13,"Each-tick conservation before real unload");
                if(!paid.isRemoved()||original.getMainHandItem().getCount()!=13) { require(ticks-phaseTick<500,"Actual source pickup completes"); return; }
                require(stock(level)==0&&!original.isNoAi(),"Worker really carries13 with AI enabled before unload"); oldXp=original.rankXp();
                require(level.setChunkForced(C,C,false),"Fixture removes its only forced ticket"); phase=2; phaseTick=ticks; LogUtils.getLogger().info("GOLEM_CHUNK_TICKETS_RELEASED"); return;
            }
            if(phase==2) {
                // These are non-loading APIs. Never query the barrel or chunk through getChunk while waiting.
                if(!chunkUnloaded||!trackingEnded||original.getRemovalReason()!=Entity.RemovalReason.UNLOADED_TO_CHUNK||level.getChunkSource().getChunkNow(C,C)!=null||level.areEntitiesLoaded(ChunkPos.asLong(C,C))) { require(ticks-phaseTick<900,"Native chunk/entity unload actually occur: chunkEvent="+chunkUnloaded+", trackingEnd="+trackingEnded+", reason="+original.getRemovalReason()+", blockLoaded="+(level.getChunkSource().getChunkNow(C,C)!=null)+", entitiesLoaded="+level.areEntitiesLoaded(ChunkPos.asLong(C,C))); return; }
                require(level.getEntity(WORKER)==null,"Original UUID absent from live entity manager after native unload"); phase=3; phaseTick=ticks; LogUtils.getLogger().info("GOLEM_CHUNK_NATIVE_UNLOAD_OBSERVED"); return;
            }
            if(phase==3) {
                require(level.getChunkSource().getChunkNow(C,C)==null&&!level.areEntitiesLoaded(ChunkPos.asLong(C,C))&&level.getEntity(WORKER)==null,"Production seals do not force-load absent fixture for40ticks");
                if(ticks-phaseTick<40) return; level.setChunkForced(C,C,true); level.getChunk(C,C); phase=4; phaseTick=ticks; return;
            }
            if(!level.isPositionEntityTicking(pos(8,120,8))||!(level.getEntity(WORKER) instanceof ThaumcraftGolem)) { require(ticks-phaseTick<300,"Native entity reload completes"); return; }
            var worker=(ThaumcraftGolem)level.getEntity(WORKER);
            if(phase==4) {
                require(loadedFromDisk&&worker!=original&&worker.design().equals(DESIGN)&&worker.ownerId().equals(OWNER)&&worker.rankXp()==oldXp&&!worker.isNoAi()&&worker.getMainHandItem().getCount()==13&&worker.getMainHandItem().getHoverName().getString().equals("Chunk actual input")&&stock(level)==0,"Different Java entity restored from disk with same UUID/design/owner/rank/namedcargo13 and enabled AI");
                var data=GolemSeals.get(level); require(data.contains(PICKUP.pos(),PICKUP.face())&&data.get(FILL).redstone()&&data.get(FILL).locked(),"Original dimension seals/control state retained");
                level.setBlockAndUpdate(FILL.pos().east(),Blocks.AIR.defaultBlockState()); require(!GolemSeals.stopped(level,data.get(FILL)),"Actual redstone removal releases native Fill"); phase=5; phaseTick=ticks; return;
            }
            int stored=stock(level); require(ground(level)+worker.getMainHandItem().getCount()+stored==13,"Each-tick conservation after real reload");
            require(level.getEntitiesOfClass(ThaumcraftGolem.class,new AABB(pos(8,120,8)).inflate(16)).size()==1,"No duplicate golem after native chunk reload");
            if(stored!=13||!worker.getMainHandItem().isEmpty()) { require(ticks-phaseTick<500,"Actual reloaded AI completes native Fill"); return; }
            if(completedTick==0) completedTick=ticks; if(ticks-completedTick<100) return;
            checked=true; LogUtils.getLogger().info("GOLEM_CHUNK_UNLOAD_ASSERTIONS_OK");
        } catch(RuntimeException|AssertionError ex) { checked=true; LogUtils.getLogger().error("GOLEM_CHUNK_UNLOAD_ASSERTIONS_FAILED",ex); }
        if(checked) active.halt(false);
    }
    private static void require(boolean value,String message) { if(!value) throw new AssertionError(message); }
}
