package dev.ehtid.thaumcraft.golem.network;

import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.golem.seal.GolemSeals;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import java.util.*;
import static dev.ehtid.thaumcraft.golem.network.GolemSealViewPayload.Action.*;

/** Modern chunk subscription replaces source dimension-wide broadcast; packets never request chunks. */
@EventBusSubscriber(modid=ThaumcraftPort.MOD_ID)
public final class GolemSealSync {
    private GolemSealSync() {}
    public static void register(RegisterPayloadHandlersEvent e) {
        e.registrar("1").playToClient(GolemSealViewPayload.TYPE,GolemSealViewPayload.STREAM_CODEC,
                (p,c)->c.enqueueWork(()->GolemSealViewCache.CLIENT.apply(p,c.player().level().dimension().location())));
    }
    public static List<GolemSealViewPayload> snapshot(ServerLevel level,ChunkPos chunk) {
        var dim=level.dimension().location(); var packets=new ArrayList<GolemSealViewPayload>();
        packets.add(new GolemSealViewPayload(dim,CLEAR_CHUNK,new GolemSeals.SealKey(new BlockPos(chunk.getMinBlockX(),0,chunk.getMinBlockZ()),Direction.DOWN),null));
        for (var seal:GolemSeals.get(level).all()) if (new ChunkPos(seal.key().pos()).equals(chunk))
            packets.add(new GolemSealViewPayload(dim,UPSERT,seal.key(),GolemSealView.of(seal)));
        return List.copyOf(packets);
    }
    @SubscribeEvent public static void sent(ChunkWatchEvent.Sent event) {
        for (var packet:snapshot(event.getLevel(),event.getPos())) PacketDistributor.sendToPlayer(event.getPlayer(),packet);
    }
    public static void changed(ServerLevel level,GolemSeals.Seal seal) {
        PacketDistributor.sendToPlayersTrackingChunk(level,new ChunkPos(seal.key().pos()),
                new GolemSealViewPayload(level.dimension().location(),UPSERT,seal.key(),GolemSealView.of(seal)));
    }
    public static void removed(ServerLevel level,GolemSeals.SealKey key) {
        PacketDistributor.sendToPlayersTrackingChunk(level,new ChunkPos(key.pos()),new GolemSealViewPayload(level.dimension().location(),REMOVE,key,null));
    }
}
