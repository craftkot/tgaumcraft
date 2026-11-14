package dev.ehtid.thaumcraft.client;

import com.mojang.logging.LogUtils;
import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import dev.ehtid.thaumcraft.golem.test.GolemPartsClientFixture;
import dev.ehtid.thaumcraft.item.PlateItem;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import java.util.UUID;

/** ClientLevel only. Recipes, menus, equipment and movement must arrive through the real TCP connection. */
@EventBusSubscriber(modid=ThaumcraftPort.MOD_ID,value=Dist.CLIENT)
public final class GolemPartsClientAssertions {
    private static final boolean ENABLED=Boolean.getBoolean("thaumcraft.golemPartsClientProbe");
    private static int ticks,recipe,clickPhase,drawnMask;
    private static boolean recipes,initial,initialFrame,cargo,cargoFrame,wheelTurned,barrelSent,barrel,finished,finalFrame,failed;
    private static float lastWheel=Float.NaN;
    private GolemPartsClientAssertions() {}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();
        if(ENABLED&&mc.level==null&&(failed||finished&&finalFrame)){LogUtils.getLogger().info("GOLEM_PARTS_CLIENT_STOP_AFTER_REMOTE_PROOF");mc.stop();return;}
        if(!ENABLED||failed||mc.level==null||mc.player==null)return;
        try{
            require(++ticks<4000,"Hard bounded client recipe/entity/menu proof");
            require(mc.getSingleplayerServer()==null&&mc.getConnection()!=null&&mc.gameMode!=null&&mc.getConnection().getConnection().getRemoteAddress() instanceof java.net.InetSocketAddress,"Actual TCP client with no integrated server");
            if(!recipes){
                for(int i=0;i<3;i++){
                    var holder=mc.getConnection().getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath("thaumcraft",GolemPartsClientFixture.RECIPES[i]));
                    if(holder.isEmpty())return;
                    var ingredient=holder.get().value().getIngredients().getFirst();
                    require(!ingredient.isSimple(),"Actual server-synchronized metadata ingredient remains component aware");
                    for(int meta=0;meta<3;meta++){
                        var ingot=GolemPartsClientFixture.ingot(meta);ingot.set(DataComponents.CUSTOM_NAME,Component.literal("Network predicate brass/void/thaumium"));
                        require(ingredient.test(ingot)==(meta==GolemPartsClientFixture.METALS[i]),"Received recipe retains exact metal distinction and ignores unrelated name");
                    }
                }
                recipes=true;LogUtils.getLogger().info("GOLEM_PARTS_CLIENT_RECIPE_SYNC_ASSERTIONS_OK recipes=3 testedNativeMetadata=9 actualTCP=true");
            }
            var worker=worker(GolemPartsClientFixture.worker());if(worker==null)return;
            if(!initial){
                for(int i=0;i<6;i++){
                    var g=worker(GolemPartsClientFixture.gallery(i));if(g==null)return;
                    if(!g.design().equals(GolemPartsClientFixture.design(i))||!mc.player.getUUID().equals(g.ownerId())||!g.isNoAi()||g.getMainHandItem().getCount()!=i+1)return;
                }
                if(!worker.design().equals(GolemPartsClientFixture.workerDesign())||!worker.isNoAi())return;
                initial=true;LogUtils.getLogger().info("GOLEM_PARTS_CLIENT_GALLERY_ASSERTIONS_OK materials=6 heads=5 arms=5 legs=4 addons=4");
            }
            if(recipe<3&&mc.player.containerMenu instanceof CraftingMenu menu){
                int expected=GolemPartsClientFixture.PLATES[recipe];
                if(clickPhase==0){
                    var result=menu.getSlot(0).getItem();if(PlateItem.legacyMetadata(result)!=expected||result.getCount()!=3)return;
                    for(int slot=1;slot<=3;slot++)if(!ItemStack.matches(menu.getSlot(slot).getItem(),GolemPartsClientFixture.ingot(GolemPartsClientFixture.METALS[recipe])))return;
                    mc.gameMode.handleInventoryMouseClick(menu.containerId,0,0,ClickType.PICKUP,mc.player);clickPhase=1;return;
                }
                if(clickPhase==1){
                    if(PlateItem.legacyMetadata(menu.getCarried())!=expected||menu.getCarried().getCount()!=3)return;
                    for(int slot=1;slot<=3;slot++)require(menu.getSlot(slot).getItem().isEmpty(),"Actual client output take consumes three synchronized ingots");
                    int target=-1;for(int slot=10;slot<46;slot++)if(menu.getSlot(slot).getItem().isEmpty()){target=slot;break;}
                    require(target>=10,"Ordinary empty inventory slot for paid result");
                    mc.gameMode.handleInventoryMouseClick(menu.containerId,target,0,ClickType.PICKUP,mc.player);clickPhase=2;return;
                }
                if(!menu.getCarried().isEmpty())return;
                LogUtils.getLogger().info("GOLEM_PARTS_CLIENT_PLATE_CLICK_ASSERTIONS_OK recipe={} paid=3 received=3",GolemPartsClientFixture.RECIPES[recipe]);
                mc.player.closeContainer();recipe++;clickPhase=0;return;
            }
            if(!worker.isNoAi()&&worker.getMainHandItem().is(Items.IRON_INGOT)&&worker.getMainHandItem().getCount()==11&&worker.position().distanceToSqr(new Vec3(6.5,120,9.5))>1&&!cargo){
                cargo=true;LogUtils.getLogger().info("GOLEM_PARTS_CLIENT_MOVING_CARGO_ASSERTIONS_OK worker={} actualIron=11 position={}",worker.getUUID(),worker.position());
            }
            if(mc.level.getBlockState(GolemPartsClientFixture.MARKER).is(Blocks.EMERALD_BLOCK)&&!barrelSent){
                require(recipe==3&&cargo&&cargoFrame,"All paid recipes and actual rendered moving cargo observed before final deposit");
                var pos=GolemPartsClientFixture.SINK;
                if(mc.player.distanceToSqr(Vec3.atCenterOf(pos))>20)return; // Wait for the separate normal teleport packet before interacting.
                mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos).add(0,.5,0),Direction.UP,pos,false));barrelSent=true;return;
            }
            if(barrelSent&&!barrel&&mc.player.containerMenu instanceof ChestMenu menu){
                int amount=0;for(int i=0;i<27;i++){var s=menu.getSlot(i).getItem();require(s.isEmpty()||s.is(Items.IRON_INGOT),"Only actual funded iron delivered in network barrel menu");amount+=s.getCount();}
                if(amount!=11)return;
                require(worker.getMainHandItem().isEmpty()&&!worker.isNoAi(),"Same autonomous worker no longer holds deposited cargo");
                barrel=true;mc.player.closeContainer();finished=true;
                LogUtils.getLogger().info("GOLEM_PARTS_CLIENT_FINAL_ASSERTIONS_OK materials=6 recipes=3 paidIngots=9 paidPlates=9 actualBarrelIron=11");
            }
        }catch(RuntimeException|AssertionError ex){failed=true;LogUtils.getLogger().error("GOLEM_PARTS_CLIENT_ASSERTIONS_FAILED",ex);mc.stop();}
    }
    @SubscribeEvent public static void drawn(RenderLivingEvent.Post<?,?> event){
        if(!ENABLED||failed||!(event.getEntity() instanceof ThaumcraftGolem g)||!(event.getRenderer() instanceof GolemRenderer renderer))return;
        try{
            var design=g.design();var model=renderer.getModel();
            require(model.selectedMaterial()==design.material().id()&&model.selectedHead()==design.head().id()
                &&model.selectedArms()==design.arms().id()&&model.selectedLegs()==design.legs().id()&&model.selectedAddon()==design.addon().id(),
                "Actual post-draw model selection matches this entity's delivered packed design");
            var chassis=model.root().getChild("chassis");
            String[] parts={"head","left_arm","right_arm","left_leg","right_leg"};
            int[] selected={design.head().id(),design.arms().id(),design.arms().id(),design.legs().id(),design.legs().id()};
            int[] counts={5,5,5,4,4};
            for(int p=0;p<parts.length;p++)for(int variant=0;variant<counts[p];variant++)
                require(chassis.getChild(parts[p]).getChild("variant_"+variant).visible==(variant==selected[p]),"Exactly the matching actual rendered part mesh is visible");
            for(int variant=0;variant<4;variant++)require(chassis.getChild("addon_"+variant).visible==(variant==design.addon().id()),"Actual addon visibility matches this entity");
            String texture=switch(design.material().id()){
                case 0->"oak_planks";case 1,4->"iron_block";case 2->"terracotta";case 3->"gold_block";case 5->"obsidian";default->throw new AssertionError("Unknown material");
            };
            require(renderer.getTextureLocation(g).equals(ResourceLocation.withDefaultNamespace("textures/block/"+texture+".png")),"Actual material-specific texture chosen for the rendered entity");
            require(model.materialTint()==(design.material().id()==4?0xff8975bd:0xffffffff),"Actual source-independent thaumium material tint");
            if(g.getUUID().equals(GolemPartsClientFixture.worker())&&g.getMainHandItem().is(Items.IRON_INGOT)&&g.getMainHandItem().getCount()==11){
                require(chassis.getChild("left_arm").xRot < -1&&chassis.getChild("right_arm").xRot < -1,"Actual moving cargo uses both raised carry arms");
                float rotation=chassis.getChild("left_leg").getChild("variant_1").getChild("wheel").xRot;
                require(Float.isFinite(rotation),"Actual wheel pose is finite");
                if(Float.isFinite(lastWheel)&&Math.abs(rotation-lastWheel)>.005f)wheelTurned=true;
                lastWheel=rotation;
            }
            for(int i=0;i<6;i++)if(g.getUUID().equals(GolemPartsClientFixture.gallery(i))&&design.equals(GolemPartsClientFixture.design(i)))drawnMask|=1<<i;
        }catch(RuntimeException|AssertionError ex){failed=true;LogUtils.getLogger().error("GOLEM_PARTS_CLIENT_ASSERTIONS_FAILED actualPostDraw=true",ex);Minecraft.getInstance().stop();}
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(!ENABLED||failed||event.getStage()!=RenderLevelStageEvent.Stage.AFTER_LEVEL)return;
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||mc.screen!=null)return;
        if(initial&&!initialFrame&&drawnMask==63){initialFrame=true;LogUtils.getLogger().info("GOLEM_PARTS_CLIENT_GALLERY_FRAME_OBSERVED actualRenderedMaterials=6 heads=5 arms=5 legs=4 addons=4");}
        var worker=worker(GolemPartsClientFixture.worker());
        if(cargo&&wheelTurned&&!cargoFrame&&worker!=null&&worker.getMainHandItem().is(Items.IRON_INGOT)&&worker.getMainHandItem().getCount()==11&&event.getFrustum().isVisible(worker.getBoundingBox())){
            cargoFrame=true;LogUtils.getLogger().info("GOLEM_PARTS_CLIENT_CARGO_FRAME_OBSERVED actualIron=11 workerVisible=true raisedCarryArms=true wheelRotationChanged=true");
        }
        if(finished&&!finalFrame&&worker!=null&&event.getFrustum().isVisible(worker.getBoundingBox())){finalFrame=true;LogUtils.getLogger().info("GOLEM_PARTS_CLIENT_FINAL_FRAME_OBSERVED actualBarrelIron=11");}
    }
    private static ThaumcraftGolem worker(UUID id){var level=Minecraft.getInstance().level;return level==null?null:level.getEntitiesOfClass(ThaumcraftGolem.class,new AABB(0,118,0,20,128,18)).stream().filter(g->g.getUUID().equals(id)).findFirst().orElse(null);}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
