package dev.ehtid.thaumcraft.client;

import com.mojang.logging.LogUtils;
import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import dev.ehtid.thaumcraft.golem.test.GolemCropClientFixture;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/** ClientLevel and vanilla entity/equipment packets only; never reads an integrated or dedicated ServerLevel. */
@EventBusSubscriber(modid=ThaumcraftPort.MOD_ID,value=Dist.CLIENT)
public final class GolemCropClientAssertions {
    private static final boolean ENABLED=Boolean.getBoolean("thaumcraft.golemCropClientProbe");
    private static int phase,ticks,stable;
    private static boolean failed,initialFrame,finalFrame;
    private GolemCropClientAssertions() {}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();
        if(ENABLED&&!failed&&phase==2&&finalFrame&&mc.level==null){
            LogUtils.getLogger().info("GOLEM_CROP_CLIENT_STOP_AFTER_REMOTE_PROOF");mc.stop();return;
        }
        if(!ENABLED||failed||phase==2||mc.level==null||mc.player==null)return;
        try{
            require(++ticks<2000,"Bounded client-only crop/equipment synchronization");
            require(mc.getSingleplayerServer()==null&&mc.getConnection()!=null
                &&mc.getConnection().getConnection().getRemoteAddress() instanceof java.net.InetSocketAddress,"Actual TCP client requires no integrated server");
            var worker=worker();if(worker==null)return;
            var state=mc.level.getBlockState(GolemCropClientFixture.CROP);
            if(phase==0){
                if(!state.is(Blocks.WHEAT)||state.getValue(CropBlock.AGE)!=7||!ItemStack.matches(worker.getMainHandItem(),GolemCropClientFixture.seed(2)))return;
                require(worker.ownerId()!=null&&worker.ownerId().equals(mc.player.getUUID())&&worker.isNoAi(),"Actual initial owner/noAI metadata delivered through normal entity packets");
                phase=1;ticks=0;
                LogUtils.getLogger().info("GOLEM_CROP_CLIENT_REMOTE_CONNECTION_OBSERVED");
                LogUtils.getLogger().info("GOLEM_CROP_CLIENT_INITIAL_ASSERTIONS_OK uuid={} seed=2 age=7",worker.getUUID());
                return;
            }
            // Equipment and block changes are separate packets. Require a stable delivered final state.
            if(state.is(Blocks.WHEAT)&&state.getValue(CropBlock.AGE)==0&&!worker.isNoAi()
                &&ItemStack.matches(worker.getMainHandItem(),GolemCropClientFixture.seed(1))){
                require(worker.ownerId()!=null&&worker.ownerId().equals(mc.player.getUUID()),"Same original owner metadata after real autonomous action");
                if(++stable<10)return;
                phase=2;
                LogUtils.getLogger().info("GOLEM_CROP_CLIENT_FINAL_ASSERTIONS_OK uuid={} seed=1 age=0 stableClientTicks={}",worker.getUUID(),stable);
            }else stable=0;
        }catch(RuntimeException|AssertionError ex){failed=true;LogUtils.getLogger().error("GOLEM_CROP_CLIENT_ASSERTIONS_FAILED",ex);}
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(!ENABLED||failed||event.getStage()!=RenderLevelStageEvent.Stage.AFTER_LEVEL)return;
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||mc.screen!=null)return;
        var worker=worker();if(worker==null||!event.getFrustum().isVisible(worker.getBoundingBox())
            ||!event.getFrustum().isVisible(new AABB(GolemCropClientFixture.CROP)))return;
        var state=mc.level.getBlockState(GolemCropClientFixture.CROP);
        if(phase==1&&!initialFrame&&state.is(Blocks.WHEAT)&&state.getValue(CropBlock.AGE)==7
            &&ItemStack.matches(worker.getMainHandItem(),GolemCropClientFixture.seed(2))){
            initialFrame=true;LogUtils.getLogger().info("GOLEM_CROP_CLIENT_INITIAL_FRAME_OBSERVED uuid={}",worker.getUUID());
        }
        if(phase==2&&!finalFrame){
            finalFrame=true;LogUtils.getLogger().info("GOLEM_CROP_CLIENT_FINAL_FRAME_OBSERVED uuid={}",worker.getUUID());
        }
    }
    private static ThaumcraftGolem worker(){
        var level=Minecraft.getInstance().level;if(level==null)return null;
        return level.getEntitiesOfClass(ThaumcraftGolem.class,new AABB(GolemCropClientFixture.CROP).inflate(16))
            .stream().filter(e->e.getUUID().equals(GolemCropClientFixture.WORKER)).findFirst().orElse(null);
    }
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
