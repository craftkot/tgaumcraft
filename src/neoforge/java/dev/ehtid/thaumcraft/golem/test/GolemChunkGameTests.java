package dev.ehtid.thaumcraft.golem.test;

import dev.ehtid.thaumcraft.golem.inventory.GolemContainers;
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
}
