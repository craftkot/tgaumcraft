package dev.ehtid.thaumcraft.golem.test;

import com.mojang.authlib.GameProfile;
import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.golem.seal.GolemSeals;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Native cancellation and synchronous join reentry, beyond ordinary owner/menu validation. */
@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class GolemSealRefundGameTests {
    private GolemSealRefundGameTests() {}

    @GameTest(template="golem_test", batch="seal_refund", timeoutTicks=40)
    public static void cancelledRefundSpawnLeavesFullInventoryAndOriginalSealUntouched(GameTestHelper h) {
        var f=fixture(h,true);var seen=new AtomicBoolean();
        Consumer<EntityJoinLevelEvent> deny=e->{
            if(e.getLevel()==h.getLevel()&&e.getEntity() instanceof ItemEntity item&&item.getItem().is(GolemModule.PICKUP_SEAL.get())) {
                seen.set(true);e.setCanceled(true);
            }
        };
        NeoForge.EVENT_BUS.addListener(deny);
        try {
            h.assertTrue(!use(f).consumesAction()&&seen.get(),"Actual native refund spawn is cancelled");
            h.assertTrue(f.data.get(f.key).instanceId().equals(f.original.instanceId())&&f.player.getMainHandItem().getCount()==64
                    &&ground(h)==0,"Cancelled spawn retains original paid seal and all actual held items");
            for(int slot=1;slot<36;slot++) h.assertTrue(f.player.getInventory().getItem(slot).is(Items.STONE)
                    &&f.player.getInventory().getItem(slot).getCount()==64,"Full unrelated inventory remains intact");
        } finally { NeoForge.EVENT_BUS.unregister(deny);cleanup(f); }
        h.succeed();
    }

    @GameTest(template="golem_test", batch="seal_refund", timeoutTicks=40)
    public static void fullInventoryLeavesOneAcceptedRefundAndCannotRepeatIt(GameTestHelper h) {
        var f=fixture(h,true);
        h.assertTrue(use(f).consumesAction()&&f.data.get(f.key)==null&&f.player.getMainHandItem().getCount()==64
                &&ground(h)==1,"One accepted actual refund survives beside full unchanged inventory");
        h.assertTrue(!use(f).consumesAction()&&ground(h)==1&&f.player.getMainHandItem().getCount()==64,"Second click cannot mint another refund");
        cleanup(f);h.succeed();
    }

    @GameTest(template="golem_test", batch="seal_refund", timeoutTicks=40)
    public static void acceptedRefundMovesItsActualStackIntoAvailableInventoryOnce(GameTestHelper h) {
        var f=fixture(h,false);var actual=new AtomicReference<ItemEntity>();
        Consumer<EntityJoinLevelEvent> observe=e->{
            if(e.getLevel()==h.getLevel()&&e.getEntity() instanceof ItemEntity item&&item.getItem().is(GolemModule.PICKUP_SEAL.get())) actual.set(item);
        };
        NeoForge.EVENT_BUS.addListener(observe);
        try {
            h.assertTrue(use(f).consumesAction()&&actual.get()!=null,"Real refund entity accepted before original seal retires");
            h.assertTrue(f.data.get(f.key)==null&&f.player.getMainHandItem().getCount()==3&&ground(h)==0
                    &&actual.get().isRemoved()&&actual.get().getItem().isEmpty(),"Existing two plus one actual refund, empty world remainder discarded");
            h.assertTrue(!use(f).consumesAction()&&f.player.getMainHandItem().getCount()==3,"Removed seal cannot pay inventory twice");
        } finally { NeoForge.EVENT_BUS.unregister(observe);cleanup(f); }
        h.succeed();
    }

    @GameTest(template="golem_test", batch="seal_refund", timeoutTicks=40)
    public static void synchronousSpawnCallbackReplacementCannotBeRemovedByOldClick(GameTestHelper h) {
        var f=fixture(h,true);var replacement=new AtomicReference<GolemSeals.Seal>();var actual=new AtomicReference<ItemEntity>();
        Consumer<EntityJoinLevelEvent> replace=e->{
            if(e.getLevel()==h.getLevel()&&e.getEntity() instanceof ItemEntity item&&item.getItem().is(GolemModule.PICKUP_SEAL.get())) {
                actual.set(item);
                h.assertTrue(f.data.remove(f.original,f.player.getUUID()),"Join callback retires original fixture instance");
                h.assertTrue(f.data.place(f.key.pos(),f.key.face(),f.player.getUUID(),GolemSeals.Type.PICKUP),"Join callback places different instance at same face");
                replacement.set(f.data.get(f.key));
            }
        };
        NeoForge.EVENT_BUS.addListener(replace);
        try {
            h.assertTrue(!use(f).consumesAction()&&replacement.get()!=null&&actual.get()!=null,"Old operation refuses real synchronous replacement");
            h.assertTrue(!replacement.get().instanceId().equals(f.original.instanceId())&&f.data.get(f.key).instanceId().equals(replacement.get().instanceId())
                    &&f.player.getMainHandItem().getCount()==64&&actual.get().isRemoved()&&ground(h)==0,"Replacement retained; uncommitted refund discarded without inventory mutation");
        } finally { NeoForge.EVENT_BUS.unregister(replace);cleanup(f); }
        h.succeed();
    }

    private record Fixture(FakePlayer player,GolemSeals data,GolemSeals.SealKey key,GolemSeals.Seal original) {}
    private static Fixture fixture(GameTestHelper h,boolean full) {
        for(int x=0;x<12;x++)for(int z=0;z<12;z++)h.setBlock(new BlockPos(x,0,z),Blocks.STONE);
        var player=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"SealRefundOwner"));
        player.setGameMode(GameType.SURVIVAL);player.getInventory().clearContent();player.getInventory().selected=0;
        if(full)for(int slot=0;slot<36;slot++)player.getInventory().setItem(slot,new ItemStack(Items.STONE,64));
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(GolemModule.PICKUP_SEAL.get(),full?64:2));
        var pos=h.absolutePos(new BlockPos(6,0,6));player.moveTo(Vec3.atBottomCenterOf(pos.above()));player.setShiftKeyDown(true);
        var data=GolemSeals.get(h.getLevel());var key=new GolemSeals.SealKey(pos,Direction.UP);
        h.assertTrue(data.place(pos,Direction.UP,player.getUUID(),GolemSeals.Type.PICKUP),"One original paid typed fixture seal");
        return new Fixture(player,data,key,data.get(key));
    }
    private static net.minecraft.world.InteractionResult use(Fixture f) {
        return GolemModule.PICKUP_SEAL.get().useOn(new UseOnContext(f.player,InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(f.key.pos()).add(0,.5,0),Direction.UP,f.key.pos(),false)));
    }
    private static int ground(GameTestHelper h) {
        return h.getLevel().getEntitiesOfClass(ItemEntity.class,h.getBounds(),e->e.getItem().is(GolemModule.PICKUP_SEAL.get()))
                .stream().mapToInt(e->e.getItem().getCount()).sum();
    }
    private static void cleanup(Fixture f) {
        f.data.remove(f.key.pos(),f.key.face(),f.player.getUUID());f.player.setShiftKeyDown(false);f.player.getInventory().clearContent();
    }
}
