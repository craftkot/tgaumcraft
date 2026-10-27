package dev.ehtid.thaumcraft.client;

import com.mojang.logging.LogUtils;
import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.golem.menu.GolemSealMenu;
import dev.ehtid.thaumcraft.golem.network.GolemSealView;
import dev.ehtid.thaumcraft.golem.network.GolemSealViewCache;
import dev.ehtid.thaumcraft.golem.test.GolemOwnerClientFixture;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.UUID;

/** Two separate TCP clients exercise ordinary entity, block and menu packets. No ServerLevel access. */
@EventBusSubscriber(modid=ThaumcraftPort.MOD_ID,value=Dist.CLIENT)
public final class GolemOwnerClientAssertions {
    private static final boolean ENABLED=Boolean.getBoolean("thaumcraft.golemOwnerClientProbe");
    private static final UUID[] instances=new UUID[2];
    private static int role=-1,ticks,stable;
    private static boolean initial,foreignDye,ownDye,foreignSeal,ownSeal,edited,closed,finished,frame,failed;
    private GolemOwnerClientAssertions() {}

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc=Minecraft.getInstance();
        if(ENABLED&&finished&&frame&&!failed&&mc.level==null) {
            LogUtils.getLogger().info("GOLEM_OWNER_CLIENT_STOP_AFTER_REMOTE_PROOF role={}",role);mc.stop();return;
        }
        if(!ENABLED||failed||finished||mc.level==null||mc.player==null)return;
        try {
            require(++ticks<6000,"Bounded two-player packet and automation proof");
            require(mc.getSingleplayerServer()==null&&mc.getConnection()!=null&&mc.gameMode!=null
                    &&mc.getConnection().getConnection().getRemoteAddress() instanceof java.net.InetSocketAddress,
                    "Actual TCP connection with no integrated server");
            if(role<0) {
                String name=mc.player.getGameProfile().getName();
                for(int i=0;i<2;i++)if(name.equals(GolemOwnerClientFixture.playerName(i)))role=i;
                require(role>=0,"Expected distinct named TCP test account");
            }
            ThaumcraftGolem own=worker(role),other=worker(1-role);
            GolemSealView ownView=view(role),otherView=view(1-role);
            if(own==null||other==null||ownView==null||otherView==null)return;
            require(mc.player.getUUID().equals(own.ownerId())&&other.ownerId()!=null
                    &&!mc.player.getUUID().equals(other.ownerId())&&ownView.owner().equals(own.ownerId())
                    &&otherView.owner().equals(other.ownerId()),"Both distinct owners arrive through real entity/seal metadata");
            require(ownView.locked()&&otherView.locked(),"Both owner-locked seal snapshots remain locked");
            if(!initial) {
                if(!mc.level.getBlockState(GolemOwnerClientFixture.MARKER).is(Blocks.RED_WOOL)
                        ||!mc.player.getMainHandItem().is(Items.RED_DYE)||mc.player.getMainHandItem().getCount()!=6)return;
                require(own.isNoAi()&&other.isNoAi()&&own.golemColor()==0&&other.golemColor()==0,
                        "Genuine initial worker metadata");
                instances[role]=ownView.instance();instances[1-role]=otherView.instance();initial=true;
                LogUtils.getLogger().info("GOLEM_OWNER_CLIENT_REMOTE_INITIAL_ASSERTIONS_OK role={} own={} other={}",role,own.getUUID(),other.getUUID());
            }
            require(instances[role].equals(ownView.instance())&&instances[1-role].equals(otherView.instance()),
                    "Concurrent edits preserve both seal instances");
            var marker=mc.level.getBlockState(GolemOwnerClientFixture.MARKER);
            if(marker.is(Blocks.RED_WOOL)&&!foreignDye) {
                mc.gameMode.interact(mc.player,other,InteractionHand.MAIN_HAND);foreignDye=true;
                LogUtils.getLogger().info("GOLEM_OWNER_CLIENT_FOREIGN_DYE_PACKET_SENT role={}",role);
            }
            if(marker.is(Blocks.GOLD_BLOCK)&&!ownDye) {
                if(!mc.player.getMainHandItem().is(Items.RED_DYE)||mc.player.getMainHandItem().getCount()!=6)return;
                // The other client may already have legitimately dyed its own worker in this phase.
                require(foreignDye&&own.golemColor()==0,"Foreign dye retained actual inventory and the still-unedited own worker");
                LogUtils.getLogger().info("GOLEM_OWNER_CLIENT_FOREIGN_DYE_REJECTED_OBSERVED role={}",role);
                mc.gameMode.interact(mc.player,own,InteractionHand.MAIN_HAND);ownDye=true;
                LogUtils.getLogger().info("GOLEM_OWNER_CLIENT_OWN_DYE_PACKET_SENT role={}",role);
            }
            if(marker.is(Blocks.IRON_BLOCK)&&!foreignSeal) {
                if(!mc.player.getMainHandItem().is(GolemModule.GOLEM_BELL.get())
                        ||own.golemColor()!=GolemOwnerClientFixture.dyeColor()||other.golemColor()!=GolemOwnerClientFixture.dyeColor())return;
                require(ownDye,"Own dye packet preceded real synchronized color change");
                useSeal(1-role);foreignSeal=true;
                LogUtils.getLogger().info("GOLEM_OWNER_CLIENT_FOREIGN_SEAL_PACKET_SENT role={}",role);
            }
            if(marker.is(Blocks.DIAMOND_BLOCK)&&!ownSeal) {
                require(foreignSeal&&mc.player.containerMenu==mc.player.inventoryMenu,"Foreign bell packet opened no menu");
                useSeal(role);ownSeal=true;
                LogUtils.getLogger().info("GOLEM_OWNER_CLIENT_OWN_SEAL_PACKET_SENT role={}",role);
            }
            if(marker.is(Blocks.DIAMOND_BLOCK)&&ownSeal&&!edited&&mc.player.containerMenu instanceof GolemSealMenu menu) {
                if(!menu.locked())return; // Wait for actual DataSlot delivery after open-screen packet.
                require(menu.priority()==0,"Actual owner menu initially carries source priority zero");
                mc.gameMode.handleInventoryButtonClick(menu.containerId,81);edited=true;
                LogUtils.getLogger().info("GOLEM_OWNER_CLIENT_OWN_MENU_BUTTON_PACKET_SENT role={} container={}",role,menu.containerId);
            }
            if(marker.is(Blocks.EMERALD_BLOCK)&&!closed) {
                if(ownView.priority()!=1||otherView.priority()!=1)return;
                require(edited&&mc.player.containerMenu instanceof GolemSealMenu,"Both independent owner menus edited through real C2S button packets");
                if(((GolemSealMenu)mc.player.containerMenu).priority()!=1)return;
                LogUtils.getLogger().info("GOLEM_OWNER_CLIENT_CONCURRENT_MENU_ASSERTIONS_OK role={} ownPriority=1 otherPriority=1",role);
                mc.player.closeContainer();closed=true;
            }
            if(marker.is(Blocks.LAPIS_BLOCK)||marker.is(Blocks.NETHERITE_BLOCK)) {
                require(closed&&ownView.priority()==1&&otherView.priority()==1,"Menu edits remain delivered after both real closes");
                boolean actual=ItemStack.matches(own.getMainHandItem(),GolemOwnerClientFixture.cargo(role))
                        &&ItemStack.matches(other.getMainHandItem(),GolemOwnerClientFixture.cargo(1-role))
                        &&own.getOffhandItem().isEmpty()&&other.getOffhandItem().isEmpty()&&!own.isNoAi()&&!other.isNoAi();
                if(!actual){stable=0;return;}
                if(++stable<10||!marker.is(Blocks.NETHERITE_BLOCK))return;
                finished=true;
                LogUtils.getLogger().info("GOLEM_OWNER_CLIENT_FINAL_ASSERTIONS_OK role={} iron=13 gold=17 distinctOwners=2 sameInstances=2 stableClientTicks={}",role,stable);
            }
        } catch(RuntimeException|AssertionError ex) {
            failed=true;LogUtils.getLogger().error("GOLEM_OWNER_CLIENT_ASSERTIONS_FAILED role="+role,ex);mc.stop();
        }
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if(!ENABLED||failed||frame||!finished||event.getStage()!=RenderLevelStageEvent.Stage.AFTER_LEVEL)return;
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||mc.screen!=null)return;
        var own=worker(role);var other=worker(1-role);
        if(own==null||other==null||!event.getFrustum().isVisible(own.getBoundingBox())
                ||!event.getFrustum().isVisible(other.getBoundingBox()))return;
        frame=true;LogUtils.getLogger().info("GOLEM_OWNER_CLIENT_FINAL_FRAME_OBSERVED role={} workersVisible=2",role);
    }
    private static void useSeal(int index) {
        var mc=Minecraft.getInstance();var key=GolemOwnerClientFixture.key(index);
        Vec3 hit=Vec3.atCenterOf(key.pos()).add(0,.5,0);
        mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(hit,Direction.UP,key.pos(),false));
    }
    private static ThaumcraftGolem worker(int index) {
        var mc=Minecraft.getInstance();if(mc.level==null)return null;
        return mc.level.getEntitiesOfClass(ThaumcraftGolem.class,new AABB(GolemOwnerClientFixture.MARKER).inflate(16))
                .stream().filter(g->g.getUUID().equals(GolemOwnerClientFixture.worker(index))).findFirst().orElse(null);
    }
    private static GolemSealView view(int index) {
        var mc=Minecraft.getInstance();
        return GolemSealViewCache.CLIENT.snapshot(mc.level.dimension().location()).stream()
                .filter(v->v.key().equals(GolemOwnerClientFixture.key(index))).findFirst().orElse(null);
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
