package dev.ehtid.thaumcraft.golem.test;

import dev.ehtid.thaumcraft.golem.inventory.GolemContainers;
import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.golem.press.GolemPressStructures;
import dev.ehtid.thaumcraft.port.core.golem.GolemPressBlueprint;
import net.minecraft.nbt.*;
import dev.ehtid.thaumcraft.golem.seal.GolemSeals;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder("thaumcraft") @PrefixGameTestTemplate(false)
public final class GolemChunkGameTests {
    private GolemChunkGameTests() {}
    @GameTest(template="golem_test",batch="chunk_lifecycle",timeoutTicks=100)
    public static void allSixteenSealsIgnoreFullButNonTickingChunkAndAllowNativeUnload(GameTestHelper h) {
        var level=h.getLevel(); var current=new ChunkPos(h.absolutePos(BlockPos.ZERO)); var far=new ChunkPos(current.x+1024,current.z);
        // One explicit fixture request supplies a vanilla FULL/UNKNOWN ticket, never an entity-ticking forced ticket.
        level.getChunk(far.x,far.z);
        var origin=new BlockPos(far.getMinBlockX(),level.getMinBuildHeight()+8,far.getMinBlockZ());
        h.assertTrue(level.hasChunkAt(origin)&&!level.isPositionEntityTicking(origin),"Actual FULL chunk exists but has no entity-ticking ticket");
        var owner=UUID.randomUUID(); var data=GolemSeals.get(level); var keys=new ArrayList<GolemSeals.SealKey>();
        try {
            for(var type:GolemSeals.Type.values()) {
                int n=keys.size(); var key=new GolemSeals.SealKey(origin.offset(1+(n%4)*3,0,1+(n/4)*3),Direction.UP);
                h.assertTrue(data.place(key.pos(),key.face(),owner,type),"Fresh source seal type "+type); keys.add(key);
                h.assertTrue(!data.get(key).redstone()&&GolemSeals.stopped(level,data.get(key)),"Even non-redstone "+type+" pauses outside real ticking chunks");
                h.assertTrue(GolemContainers.handler(level,key)==null,"Inactive capability lookup does not read blocks or renew UNKNOWN tickets");
            }
            h.assertTrue(keys.size()==16,"Every original catalog seal participates");
        } catch(RuntimeException|AssertionError ex) { keys.forEach(k->data.remove(k.pos(),k.face(),owner)); throw ex; }
        h.runAfterDelay(99,()->keys.forEach(k->data.remove(k.pos(),k.face(),owner)));
        h.runAfterDelay(60,()->{
            try {
                h.assertTrue(level.getChunkSource().getChunkNow(far.x,far.z)==null&&!level.areEntitiesLoaded(far.toLong()),"Actual native chunk expires while all source producers tick; no hidden reload");
                h.assertTrue(keys.stream().allMatch(k->data.contains(k.pos(),k.face())),"Inactive seals remain saved, never mistaken for destroyed support"); h.succeed();
            } finally { keys.forEach(k->data.remove(k.pos(),k.face(),owner)); }
        });
    }
    @GameTest(template="golem_test",batch="press_inactive_lifecycle",timeoutTicks=100)
    public static void persistedPressPhasesWaitForTickingChunksWithoutRenewingUnknownTickets(GameTestHelper h) {
        var level=h.getLevel();var here=new ChunkPos(h.absolutePos(BlockPos.ZERO));var far=new ChunkPos(here.x+2048,here.z);
        level.getChunk(far.x,far.z); // Explicit FULL/UNKNOWN fixture, never entity-ticking.
        var base=new BlockPos(far.getMinBlockX(),120,far.getMinBlockZ());
        h.assertTrue(level.hasChunkAt(base)&&!level.isPositionEntityTicking(base),"Real FULL non-ticking fixture");
        var tag=GolemPressStructures.get(level).save(new CompoundTag(),level.registryAccess());
        var rows=tag.getList("Structures",Tag.TAG_COMPOUND);int previous=rows.size();
        var origins=List.of(base.offset(4,0,4),base.offset(9,0,4),base.offset(4,0,9));
        for(int i=0;i<3;i++) {
            var origin=origins.get(i);GolemFormationGameTests.placeSource(level,origin,1);var source=new ListTag();
            for(var cell:GolemPressBlueprint.cells(1)) {
                var pos=origin.offset(cell.x(),cell.y(),cell.z());source.add(NbtUtils.writeBlockState(level.getBlockState(pos)));
                if(i!=0)level.setBlock(pos,switch(cell.part()) {
                    case CONTROLLER->GolemModule.PRESS.get().defaultBlockState();case CAULDRON->GolemModule.PLACEHOLDER_CAULDRON.get().defaultBlockState();
                    case ANVIL->GolemModule.PLACEHOLDER_ANVIL.get().defaultBlockState();case TABLE->GolemModule.PLACEHOLDER_TABLE.get().defaultBlockState();case BARS->GolemModule.PLACEHOLDER_BARS.get().defaultBlockState();
                },18);
            }
            var row=new CompoundTag();row.putLong("Origin",origin.asLong());row.putInt("Rotation",1);row.putString("Phase",new String[]{"PENDING","ACTIVE","RESTORING"}[i]);
            row.putInt("Facing",0);row.putUUID("Owner",UUID.randomUUID());row.putLong("Due",level.getGameTime());row.putInt("RestoreMask",31);
            row.put("Source",i==0?source:new ListTag());rows.add(row);
        }
        // Explicit valid persisted lifecycle fixtures; no formation or paid-output claim is made here.
        tag.put("Structures",rows);var data=GolemPressStructures.load(tag,level.registryAccess());level.getDataStorage().set("thaumcraft_golem_press_structures",data);
        h.assertTrue(data.size()==previous+3,"All three valid persisted phases accepted");
        h.runAfterDelay(60,()->{
            try {
                h.assertTrue(level.getChunkSource().getChunkNow(far.x,far.z)==null&&!level.areEntitiesLoaded(far.toLong()),"Native FULL chunk expires despite all three structure scanners");
                h.assertTrue(data.size()==previous+3,"Inactive pending/active/restoring records retained without fake teardown");h.succeed();
            } finally {
                var cleanup=data.save(new CompoundTag(),level.registryAccess());var all=cleanup.getList("Structures",Tag.TAG_COMPOUND);
                all.removeIf(t->origins.stream().anyMatch(o->o.asLong()==((CompoundTag)t).getLong("Origin")));
                level.getDataStorage().set("thaumcraft_golem_press_structures",GolemPressStructures.load(cleanup,level.registryAccess()));
            }
        });
    }

    @GameTest(template="golem_test",batch="press_block_ticking",timeoutTicks=160)
    public static void paidPressContinuesInBlockTickingChunkWithoutEntityTicking(GameTestHelper h) {
        var level=h.getLevel();var here=new ChunkPos(h.absolutePos(BlockPos.ZERO));var far=new ChunkPos(here.x+4096,here.z);
        var cache=level.getChunkSource();var ticket=net.minecraft.server.level.TicketType.FORCED;
        cache.addRegionTicket(ticket,far,1,far); // FULL(33)-1=32: block ticking, not entity ticking(31).
        level.getChunk(far.x,far.z);var pos=new BlockPos(far.getMinBlockX()+8,120,far.getMinBlockZ()+8);
        h.runAfterDelay(159,()->cache.removeRegionTicket(ticket,far,1,far));
        h.startSequence().thenWaitUntil(()->h.assertTrue(cache.isPositionTicking(far.toLong()),"Native block-ticking ticket is active"))
            .thenExecute(()->{
                h.assertTrue(!level.isPositionEntityTicking(pos),"No entity-ticking ticket in this machine fixture");
                level.setBlockAndUpdate(pos,GolemModule.PRESS.get().defaultBlockState());
                level.setBlockAndUpdate(pos.below(),dev.ehtid.thaumcraft.registry.ThaumcraftBlocks.JAR_NORMAL.get().defaultBlockState());
                var jar=(dev.ehtid.thaumcraft.blockentity.EssentiaJarBlockEntity)level.getBlockEntity(pos.below());
                for(int n=0;n<8;n++)h.assertTrue(jar.addFromTransport("machina",1,Direction.UP)==1,"Physical eight-unit input");
                var owner=net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"BlockTickPress"));
                owner.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);owner.getInventory().clearContent();owner.moveTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(pos.east()));
                var knowledge=owner.getData(dev.ehtid.thaumcraft.player.ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge();knowledge.addResearch("MINDCLOCKWORK");knowledge.addResearch("MATSTUDWOOD");
                GolemPressGameTests.ingredients(owner.getInventory(),1);
                h.assertTrue(((dev.ehtid.thaumcraft.golem.press.GolemPressBlockEntity)level.getBlockEntity(pos)).start(owner)&&owner.getInventory().isEmpty(),"Actual paid start uses player components");
            }).thenIdle(45).thenExecute(()->{
                try {
                    var press=(dev.ehtid.thaumcraft.golem.press.GolemPressBlockEntity)level.getBlockEntity(pos);
                    var jar=(dev.ehtid.thaumcraft.blockentity.EssentiaJarBlockEntity)level.getBlockEntity(pos.below());
                    h.assertTrue(!level.isPositionEntityTicking(pos)&&!press.job().active()&&press.getItem(0).is(GolemModule.GOLEM_PLACER.get())&&press.getItem(0).getCount()==1&&jar.snapshot().amount()==0,"Native block entity ticks complete exactly one paid product without entity simulation");
                }finally{cache.removeRegionTicket(ticket,far,1,far);}
            }).thenSucceed();
    }

}
