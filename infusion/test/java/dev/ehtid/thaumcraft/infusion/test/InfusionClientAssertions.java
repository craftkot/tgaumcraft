package dev.ehtid.thaumcraft.infusion.test;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.blockentity.InfusionMatrixBlockEntity;
import dev.ehtid.thaumcraft.blockentity.PedestalBlockEntity;
import dev.ehtid.thaumcraft.client.InfusionMatrixRenderer;
import dev.ehtid.thaumcraft.client.PedestalItemRenderer;
import dev.ehtid.thaumcraft.registry.ThaumcraftBlockEntities;
import dev.ehtid.thaumcraft.registry.ThaumcraftBlocks;
import dev.ehtid.thaumcraft.registry.ThaumcraftItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Actual ClientLevel/network clicks and completed production-renderer calls. */
@EventBusSubscriber(modid=ThaumcraftPort.MOD_ID,value=Dist.CLIENT)
public final class InfusionClientAssertions {
    private static final boolean ENABLED=Boolean.getBoolean("thaumcraft.infusionClientProbe");
    private static int ticks,phase,matrixDraws,outputDraws;
    private static boolean craftingFrame,outputFrame,finished,failed;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();
        if(!ENABLED)return;
        if(mc.level==null){if(failed||finished)mc.stop();return;}
        if(failed||mc.player==null||mc.gameMode==null)return;
        try{
            require(++ticks<6000,"Bounded real graphical test");
            require(mc.getSingleplayerServer()==null&&mc.getConnection()!=null
                    &&mc.getConnection().getConnection().getRemoteAddress() instanceof java.net.InetSocketAddress,"Actual TCP connection");
            if(!(mc.level.getBlockEntity(InfusionClientProbe.MATRIX) instanceof InfusionMatrixBlockEntity matrix))return;
            if(!mc.player.getMainHandItem().is(ThaumcraftItems.CASTER_BASIC.get()))return;
            if(mc.player.distanceToSqr(Vec3.atCenterOf(InfusionClientProbe.MATRIX))>25)return;
            if(phase==0){
                var spriteId=net.minecraft.resources.ResourceLocation.parse("thaumcraft:entity/armor/caster_basic");
                require(mc.getTextureAtlas(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS).apply(spriteId).contents().name().equals(spriteId),
                        "Actual baked gauntlet sprite is present in the block/item atlas");
                click(mc);phase=1;return;
            }
            if(phase==1&&matrix.active()&&!matrix.crafting()){click(mc);phase=2;return;}
            if(phase==2&&matrix.crafting()){phase=3;return;}
            if(phase==3&&!matrix.crafting()&&isOutput()){
                require(craftingFrame,"Completed crafting renderer observed before result");
                click(mc);phase=4;return; // Same actual caster cannot replay the consumed recipe.
            }
            if(phase==4&&outputFrame&&mc.level.getBlockState(InfusionClientProbe.MARKER).is(Blocks.EMERALD_BLOCK)&&!finished){
                require(!matrix.crafting()&&isOutput(),"Server confirmed paid output/no replay, client received unchanged result");
                finished=true;LogUtils.getLogger().info("INFUSION_CLIENT_ASSERTIONS_OK actualTCP=true clicks=3 paidOutput=1 matrixDraws={} outputDraws={}",matrixDraws,outputDraws);
            }
        }catch(RuntimeException|AssertionError ex){failed=true;LogUtils.getLogger().error("INFUSION_CLIENT_ASSERTIONS_FAILED",ex);mc.stop();}
    }
    private static void click(Minecraft mc){
        var pos=InfusionClientProbe.MATRIX;
        require(mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos),Direction.SOUTH,pos,false)).consumesAction(),"Actual BasicCaster client click");
    }
    private static boolean isOutput(){
        var level=Minecraft.getInstance().level;
        return level!=null && level.getBlockEntity(InfusionClientProbe.MATRIX.below(2)) instanceof PedestalBlockEntity p
                && p.displayedItem().is(ThaumcraftBlocks.CRYSTAL_AER.get().asItem())&&p.displayedItem().getCount()==1;
    }
    @SubscribeEvent public static void frame(RenderLevelStageEvent event){
        if(!ENABLED||failed||event.getStage()!=RenderLevelStageEvent.Stage.AFTER_LEVEL)return;
        var mc=Minecraft.getInstance();if(mc.screen!=null||mc.level==null)return;
        if(phase>=3&&!craftingFrame&&matrixDraws>=70){craftingFrame=true;LogUtils.getLogger().info("INFUSION_CLIENT_CRAFTING_FRAME_OBSERVED actualProductionDraws={}",matrixDraws);}
        if(phase>=4&&!outputFrame&&outputDraws>=30&&isOutput()){outputFrame=true;LogUtils.getLogger().info("INFUSION_CLIENT_OUTPUT_FRAME_OBSERVED actualProductionDraws={}",outputDraws);}
    }
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}

    @EventBusSubscriber(modid=ThaumcraftPort.MOD_ID,bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
    public static final class DrawObservers {
        @SubscribeEvent(priority=EventPriority.LOWEST) public static void renderers(EntityRenderersEvent.RegisterRenderers e){
            if(!ENABLED)return;
            e.registerBlockEntityRenderer(ThaumcraftBlockEntities.INFUSION_MATRIX.get(),context->{
                var actual=new InfusionMatrixRenderer(context);
                return new BlockEntityRenderer<InfusionMatrixBlockEntity>(){
                    public void render(InfusionMatrixBlockEntity tile,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
                        actual.render(tile,partial,pose,buffers,light,overlay);
                        if(tile.getBlockPos().equals(InfusionClientProbe.MATRIX)&&tile.crafting())matrixDraws++;
                    }
                };
            });
            e.registerBlockEntityRenderer(ThaumcraftBlockEntities.PEDESTAL.get(),context->{
                var actual=new PedestalItemRenderer(context);
                return new BlockEntityRenderer<PedestalBlockEntity>(){
                    public void render(PedestalBlockEntity tile,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
                        actual.render(tile,partial,pose,buffers,light,overlay);
                        if(tile.getBlockPos().equals(InfusionClientProbe.MATRIX.below(2))&&isOutput())outputDraws++;
                    }
                };
            });
        }
    }
}
