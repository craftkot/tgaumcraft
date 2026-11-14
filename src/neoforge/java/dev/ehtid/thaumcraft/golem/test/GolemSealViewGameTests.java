package dev.ehtid.thaumcraft.golem.test;

import dev.ehtid.thaumcraft.golem.*;
import dev.ehtid.thaumcraft.golem.network.*;
import dev.ehtid.thaumcraft.golem.seal.GolemSeals;
import io.netty.buffer.Unpooled;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.gametest.*;
import java.util.*;
import static dev.ehtid.thaumcraft.golem.network.GolemSealViewPayload.Action.*;

@GameTestHolder("thaumcraft") @PrefixGameTestTemplate(false)
public final class GolemSealViewGameTests {
    private static final ResourceLocation OVERWORLD=ResourceLocation.withDefaultNamespace("overworld"), NETHER=ResourceLocation.withDefaultNamespace("the_nether");
    private GolemSealViewGameTests() {}
    private static GolemSealView view(BlockPos p,Direction face,GolemSeals.Type type,int color) {
        return new GolemSealView(new GolemSeals.SealKey(p,face),type,new BlockPos(2,3,4),color,3,UUID.randomUUID(),UUID.randomUUID(),true,true);
    }
    private static GolemSealViewPayload up(GolemSealView v) { return new GolemSealViewPayload(OVERWORLD,UPSERT,v.key(),v); }
    private static GolemSealViewPayload roundtrip(GameTestHelper h,GolemSealViewPayload p) {
        var b=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());
        try {
            GolemSealViewPayload.STREAM_CODEC.encode(b,p); h.assertTrue(b.readableBytes()<128,"Single bounded metadata packet, independent of number of seals/inventory contents");
            var decoded=GolemSealViewPayload.STREAM_CODEC.decode(b); h.assertTrue(b.readableBytes()==0&&decoded.equals(p),"Actual native codec preserves exact metadata and consumes entire packet"); return decoded;
        } finally { b.release(); }
    }
    @GameTest(template="golem_test",batch="seal_view_codec",timeoutTicks=40)
    public static void actualCodecPreservesAllSixteenTypesAndSixFacesWithoutInventories(GameTestHelper h) {
        for(var type:GolemSeals.Type.values()) for(var face:Direction.values()) {
            var v=view(new BlockPos(-19,121,35),face,type,16); var p=roundtrip(h,up(v));
            h.assertTrue(p.view().bounds().equals(v.bounds()),"Decoded face-relative bounds remain original Core geometry");
        }
        var k=new GolemSeals.SealKey(BlockPos.ZERO,Direction.UP);
        roundtrip(h,new GolemSealViewPayload(OVERWORLD,REMOVE,k,null)); roundtrip(h,new GolemSealViewPayload(OVERWORLD,CLEAR_CHUNK,k,null));
        h.succeed();
    }
    @GameTest(template="golem_test",batch="seal_view_cache",timeoutTicks=40)
    public static void orderedDeltasClearOnlyTheirChunkAndRejectPacketsForAnotherDimension(GameTestHelper h) {
        var cache=new GolemSealViewCache(); var a=view(new BlockPos(15,120,15),Direction.UP,GolemSeals.Type.PICKUP,0);
        var b=view(new BlockPos(16,120,15),Direction.WEST,GolemSeals.Type.BREAKER,4);
        cache.apply(roundtrip(h,up(a)),OVERWORLD); cache.apply(roundtrip(h,up(b)),OVERWORLD);
        var changed=new GolemSealView(a.key(),a.type(),new BlockPos(8,2,6),12,-2,a.owner(),UUID.randomUUID(),false,false);
        cache.apply(roundtrip(h,up(changed)),OVERWORLD);
        h.assertTrue(cache.snapshot(OVERWORLD).size()==2&&cache.snapshot(OVERWORLD).contains(changed)&&!cache.snapshot(OVERWORLD).contains(a),"Replacement has exact area/color/new instance, no duplicate old entry");
        cache.apply(new GolemSealViewPayload(NETHER,CLEAR_CHUNK,a.key(),null),OVERWORLD);
        h.assertTrue(cache.snapshot(OVERWORLD).size()==2,"Wrong-dimension late packet cannot erase current dimension");
        cache.apply(new GolemSealViewPayload(OVERWORLD,CLEAR_CHUNK,a.key(),null),OVERWORLD);
        h.assertTrue(cache.snapshot(OVERWORLD).equals(List.of(b)),"Chunk boundary reset preserves adjacent chunk");
        cache.apply(new GolemSealViewPayload(OVERWORLD,REMOVE,b.key(),null),OVERWORLD);
        h.assertTrue(cache.snapshot(OVERWORLD).isEmpty(),"Actual remove delta clears original face");
        cache.apply(up(a),OVERWORLD); cache.unload(OVERWORLD,new ChunkPos(a.key().pos()));
        h.assertTrue(cache.snapshot(OVERWORLD).isEmpty(),"Client chunk unload cannot leave stale markers");
        cache.apply(up(a),OVERWORLD); cache.apply(new GolemSealViewPayload(NETHER,UPSERT,b.key(),b),NETHER);
        h.assertTrue(cache.snapshot(OVERWORLD).isEmpty()&&cache.snapshot(NETHER).equals(List.of(b)),"Dimension switch replaces old cache");
        cache.clear(); h.assertTrue(cache.snapshot(NETHER).isEmpty(),"Disconnect clears remaining dimension"); h.succeed();
    }
    @GameTest(template="golem_test",batch="seal_view_snapshot",timeoutTicks=80)
    public static void lateChunkSnapshotUsesSavedSealRecordsWithoutLoadingRemoteChunks(GameTestHelper h) {
        var level=h.getLevel(); var current=new ChunkPos(h.absolutePos(BlockPos.ZERO)); var chunk=new ChunkPos(current.x+2048,current.z+2048);
        var origin=new BlockPos(chunk.getMinBlockX(),120,chunk.getMinBlockZ()); var owner=UUID.randomUUID(); var data=GolemSeals.get(level); var keys=new ArrayList<GolemSeals.SealKey>();
        h.assertTrue(level.getChunkSource().getChunkNow(chunk.x,chunk.z)==null,"Remote fixture chunk is initially absent");
        for(var type:GolemSeals.Type.values()) {
            int n=keys.size(); var key=new GolemSeals.SealKey(origin.offset(n%4,0,n/4),Direction.UP); keys.add(key);
            h.assertTrue(data.place(key.pos(),key.face(),owner,type),"Actual saved source seal "+type);
        }
        h.runAfterDelay(79,()->keys.forEach(k->data.remove(k.pos(),k.face(),owner)));
        h.runAfterDelay(30,()->{
            try {
                var packets=GolemSealSync.snapshot(level,chunk); var cache=new GolemSealViewCache();
                h.assertTrue(packets.size()==17&&packets.getFirst().action()==CLEAR_CHUNK,"Initial reset plus every saved seal, no clipping or stale append");
                for(var packet:packets) cache.apply(roundtrip(h,packet),level.dimension().location());
                h.assertTrue(cache.snapshot(level.dimension().location()).size()==16&&cache.snapshot(level.dimension().location()).stream().allMatch(v->v.owner().equals(owner)),"Late client sees all exact owner/type/face data without a server-world reference");
                h.assertTrue(level.getChunkSource().getChunkNow(chunk.x,chunk.z)==null&&!level.areEntitiesLoaded(chunk.toLong()),"Mutation publication, snapshot and codecs never create chunk tickets"); h.succeed();
            } finally { keys.forEach(k->data.remove(k.pos(),k.face(),owner)); }
        });
    }
    @GameTest(template="golem_test",batch="seal_view_items",timeoutTicks=40)
    public static void registeredBellBlankAndEverySealCarryOriginalDisplayerMarker(GameTestHelper h) {
        h.assertTrue(GolemModule.GOLEM_BELL.get() instanceof GolemSealDisplayer&&GolemModule.BLANK_SEAL.get() instanceof GolemSealDisplayer,"Source bell and blank seal are display tools");
        for(var type:GolemSeals.Type.values()) h.assertTrue(GolemModule.sealItem(type) instanceof GolemSealDisplayer,"Source placeable seal displays areas: "+type);
        Item placer=GolemModule.GOLEM_PLACER.get();
        h.assertTrue(!(Items.STICK instanceof GolemSealDisplayer)&&!(placer instanceof GolemSealDisplayer),"Ordinary items and source golem placer do not reveal seals"); h.succeed();
    }
}
