package dev.ehtid.thaumcraft.client;

import com.mojang.logging.LogUtils;
import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.golem.network.GolemSealViewCache;
import dev.ehtid.thaumcraft.golem.seal.GolemSeals;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.util.UUID;

/** Checks only metadata delivered to the real client, with no integrated ServerLevel access. */
@EventBusSubscriber(modid=ThaumcraftPort.MOD_ID,value=Dist.CLIENT)
public final class GolemSealClientAssertions {
    private static final boolean ENABLED=Boolean.getBoolean("thaumcraft.golemClientProbe")&&"seals".equals(System.getProperty("thaumcraft.golemClientScenario",""));
    private static final GolemSeals.SealKey PICK=new GolemSeals.SealKey(new BlockPos(7,119,5),Direction.UP);
    private static UUID instance;private static int phase;
    private GolemSealClientAssertions() {}
    @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
        var mc=Minecraft.getInstance();if(!ENABLED||mc.player==null||mc.level==null||phase>=3) return;
        var views=GolemSealViewCache.CLIENT.snapshot(mc.level.dimension().location());var pick=views.stream().filter(v->v.key().equals(PICK)).findFirst();
        if(phase==0&&views.size()==3&&pick.isPresent()&&pick.get().area().equals(new BlockPos(3,1,2))&&pick.get().color()==4) {
            if(!views.stream().allMatch(v->v.owner().equals(mc.player.getUUID()))) throw new AssertionError("Actual snapshot owner UUID mismatch");
            instance=pick.get().instance();phase=1;LogUtils.getLogger().info("GOLEM_CLIENT_SEAL_SNAPSHOT_ASSERTIONS_OK");
        } else if(phase==1&&pick.isPresent()&&pick.get().area().equals(new BlockPos(4,1,2))&&pick.get().color()==5) {
            if(!pick.get().instance().equals(instance)) throw new AssertionError("A real metadata update must preserve source seal instance");
            phase=2;LogUtils.getLogger().info("GOLEM_CLIENT_SEAL_UPDATE_ASSERTIONS_OK");
        } else if(phase==2&&pick.isEmpty()&&views.size()==2) {
            phase=3;LogUtils.getLogger().info("GOLEM_CLIENT_SEAL_REMOVE_ASSERTIONS_OK");
        }
    }
}
