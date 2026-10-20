package dev.ehtid.thaumcraft.client;

import com.mojang.logging.LogUtils;
import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.golem.GolemSealDisplayer;
import dev.ehtid.thaumcraft.golem.network.*;
import dev.ehtid.thaumcraft.port.core.golem.GolemSealVisualRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.event.level.*;

/** Original visibility/area rules with provisional wire geometry, never a read of server SavedData. */
@EventBusSubscriber(modid=ThaumcraftPort.MOD_ID,value=Dist.CLIENT)
public final class GolemSealOverlay {
    private static boolean renderChecked,offhandChecked,throughChecked;
    private GolemSealOverlay() {}
    public static boolean displays(ItemStack stack) { return stack.getItem() instanceof GolemSealDisplayer; }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e) { GolemSealViewCache.CLIENT.clear(); }
    @SubscribeEvent public static void unload(LevelEvent.Unload e) {
        if(e.getLevel() instanceof ClientLevel level) {
            boolean hadEntries=!GolemSealViewCache.CLIENT.snapshot(level.dimension().location()).isEmpty();
            GolemSealViewCache.CLIENT.clear();
            if(hadEntries&&Boolean.getBoolean("thaumcraft.golemClientProbe")) {
                if(!GolemSealViewCache.CLIENT.snapshot(level.dimension().location()).isEmpty()) throw new AssertionError("World unload left stale seal metadata");
                LogUtils.getLogger().info("GOLEM_CLIENT_SEAL_CACHE_CLEARED_ON_WORLD_UNLOAD");
            }
        }
    }
    @SubscribeEvent public static void chunk(ChunkEvent.Unload e) {
        if(e.getLevel() instanceof ClientLevel level) GolemSealViewCache.CLIENT.unload(level.dimension().location(),e.getChunk().getPos());
    }
    public static AABB marker(GolemSealView view) {
        var p=view.key().pos(); var d=view.key().face();
        double cx=p.getX()+.5+d.getStepX()*.505,cy=p.getY()+.5+d.getStepY()*.505,cz=p.getZ()+.5+d.getStepZ()*.505;
        double x=d.getStepX()==0?.35:.001,y=d.getStepY()==0?.35:.001,z=d.getStepZ()==0?.35:.001;
        return new AABB(cx-x,cy-y,cz-z,cx+x,cy+y,cz+z);
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent e) {
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        var mc=Minecraft.getInstance(); var player=mc.player; var level=mc.level;
        if(player==null||level==null||!displays(player.getMainHandItem())&&!displays(player.getOffhandItem())) return;
        var pose=e.getPoseStack(); var camera=e.getCamera().getPosition(); var buffers=mc.renderBuffers().bufferSource();
        var type=player.isShiftKeyDown()?GolemSealRenderTypes.THROUGH:GolemSealRenderTypes.NORMAL;
        var lines=buffers.getBuffer(type); pose.pushPose(); pose.translate(-camera.x,-camera.y,-camera.z);
        int rendered=0,areas=0;
        try {
            float time=player.tickCount+e.getPartialTick().getGameTimeDeltaPartialTick(false);
            for(var view:GolemSealViewCache.CLIENT.snapshot(level.dimension().location())) {
                var p=view.key().pos(); if(!level.hasChunkAt(p)) continue;
                float alpha=GolemSealVisualRules.opacity(player.distanceToSqr(Vec3.atCenterOf(p))); if(alpha<=0) continue;
                float r,g,b;
                if(view.color()>0) { int rgb=DyeColor.byId(view.color()-1).getTextureDiffuseColor(); r=((rgb>>16)&255)/255F;g=((rgb>>8)&255)/255F;b=(rgb&255)/255F; }
                else { r=GolemSealVisualRules.unpaintedChannel(time,p.getX(),4);g=GolemSealVisualRules.unpaintedChannel(time,p.getY(),5);b=GolemSealVisualRules.unpaintedChannel(time,p.getZ(),6); }
                LevelRenderer.renderLineBox(pose,lines,marker(view),r,g,b,alpha*.8F); rendered++;
                if(view.type().areaControl()) {
                    var a=view.bounds(); LevelRenderer.renderLineBox(pose,lines,new AABB(a.minX(),a.minY(),a.minZ(),a.maxX(),a.maxY(),a.maxZ()),r,g,b,alpha*.7F); areas++;
                }
            }
        } finally { pose.popPose(); buffers.endBatch(type); }
        if(!renderChecked&&rendered>0&&areas>0&&Boolean.getBoolean("thaumcraft.golemClientProbe")) {
            renderChecked=true; LogUtils.getLogger().info("GOLEM_CLIENT_SEAL_OVERLAY_RENDER_OBSERVED entries={} areas={}",rendered,areas);
        }
        if(rendered>0&&areas>0&&Boolean.getBoolean("thaumcraft.golemClientProbe")) {
            if(!offhandChecked&&!displays(player.getMainHandItem())&&displays(player.getOffhandItem())) {
                offhandChecked=true; LogUtils.getLogger().info("GOLEM_CLIENT_SEAL_OFFHAND_RENDER_OBSERVED");
            }
            if(!throughChecked&&player.isShiftKeyDown()&&type==GolemSealRenderTypes.THROUGH) {
                throughChecked=true; LogUtils.getLogger().info("GOLEM_CLIENT_SEAL_THROUGH_RENDER_OBSERVED");
            }
        }
    }
}
