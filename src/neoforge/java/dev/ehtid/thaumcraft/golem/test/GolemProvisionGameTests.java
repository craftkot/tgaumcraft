package dev.ehtid.thaumcraft.golem.test;

import dev.ehtid.thaumcraft.golem.seal.GolemProvisioning;
import dev.ehtid.thaumcraft.golem.seal.GolemSeals;
import dev.ehtid.thaumcraft.golem.seal.SealFilter;
import dev.ehtid.thaumcraft.port.core.golem.ProvisionRequest;
import dev.ehtid.thaumcraft.port.core.golem.ProvisionRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class GolemProvisionGameTests {
    private GolemProvisionGameTests() {}

    private static List<SealFilter> filters(ItemStack item, int desired) {
        var filters = new ArrayList<SealFilter>();
        filters.add(new SealFilter(item, false, desired));
        while (filters.size() < ProvisionRules.FILTER_SLOTS)
            filters.add(new SealFilter(ItemStack.EMPTY, false, 0));
        return List.copyOf(filters);
    }

    private static ItemStack named(int count) {
        var stack = new ItemStack(Items.IRON_INGOT, count);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Provision cargo"));
        return stack;
    }

    @GameTest(template="golem_test", batch="provision_stock", timeoutTicks=40)
    public static void stockDeficitUsesExactComponentsRoomAndDoesNotMutateInventory(GameTestHelper h) {
        var level = h.getLevel();
        h.setBlock(new BlockPos(3,1,3), Blocks.BARREL);
        var pos = h.absolutePos(new BlockPos(3,1,3));
        var barrel = (BarrelBlockEntity)level.getBlockEntity(pos);
        barrel.setItem(0, named(5)); barrel.setItem(1, new ItemStack(Items.IRON_INGOT, 40));
        var recipient = new GolemSeals.SealKey(pos, Direction.UP);
        var policy = filters(named(1), 12);
        var first = GolemProvisioning.stock(level, recipient, policy);
        h.assertTrue(first.size()==1 && first.getFirst().count()==7, "Strict named deficit is12-5, not12-45");
        var request = first.getFirst();
        h.assertTrue(ItemStack.matches(request.item().stack(7),named(7)), "Actual name is captured defensively");
        h.assertTrue(GolemProvisioning.stock(level,recipient,policy).getFirst()==request, "Repeated request is deduplicated");
        var copy = request.item().stack(1); copy.remove(DataComponents.CUSTOM_NAME); copy.setCount(64);
        h.assertTrue(ItemStack.matches(request.item().stack(7),named(7)), "Returned template cannot alter request identity");
        h.assertTrue(ItemStack.matches(barrel.getItem(0),named(5)) && barrel.getItem(1).getCount()==40,
                "Demand production never consumes or inserts items");
        // Partial insertion simulation must request only one available slot, not the whole deficit.
        for (int slot=1; slot<barrel.getContainerSize(); slot++) barrel.setItem(slot,new ItemStack(Items.STONE,64));
        barrel.setItem(0,named(63));
        var partial = GolemProvisioning.stock(level,recipient,filters(named(1),100));
        h.assertTrue(partial.size()==1 && partial.getFirst().count()==1 && barrel.getItem(0).getCount()==63,
                "Partial room yields one request item without mutation");
        barrel.setItem(0,named(64));
        h.assertTrue(GolemProvisioning.stock(level,recipient,filters(named(1),100)).isEmpty(),"Full inventory produces no demand");
        h.assertTrue(GolemProvisioning.stock(level,recipient,filters(named(1),0)).isEmpty(),"Zero desired stock is not Fill unlimited mode");
        h.succeed();
    }

    @GameTest(template="golem_test", batch="provision_sides", timeoutTicks=40)
    public static void stockUsesRealFurnaceSidesAndNeverLoadsRemoteChunks(GameTestHelper h) {
        var level=h.getLevel(); h.setBlock(new BlockPos(3,1,3),Blocks.FURNACE);
        var pos=h.absolutePos(new BlockPos(3,1,3));
        var furnace=(FurnaceBlockEntity)level.getBlockEntity(pos);
        furnace.setItem(0,new ItemStack(Items.RAW_IRON,3));
        var policy=filters(new ItemStack(Items.RAW_IRON),10);
        var top=GolemProvisioning.stock(level,new GolemSeals.SealKey(pos,Direction.UP),policy);
        var bottom=GolemProvisioning.stock(level,new GolemSeals.SealKey(pos,Direction.DOWN),policy);
        h.assertTrue(top.size()==1 && top.getFirst().count()==7 && bottom.isEmpty(),"Actual sided insertion separates top input and bottom output");
        h.assertTrue(furnace.getItem(0).getCount()==3 && furnace.getItem(2).isEmpty(),"Simulation leaves furnace contents intact");
        var remote=new GolemSeals.SealKey(new BlockPos(25_000_000,80,25_000_000),Direction.UP);
        h.assertTrue(!level.hasChunkAt(remote.pos()),"Remote chunk initially absent");
        h.assertTrue(GolemProvisioning.stock(level,remote,policy).isEmpty() && !level.hasChunkAt(remote.pos()),"No chunk ticket or remote lookup");
        h.succeed();
    }

    @GameTest(template="golem_test", batch="provision_links", timeoutTicks=40)
    public static void nativeRequestsCopyCallerPayloadAndRejectLatePickupUnlink(GameTestHelper h) {
        var level=h.getLevel(); var board=GolemProvisioning.get(level);
        var provider=GolemProvisioning.location(new GolemSeals.SealKey(h.absolutePos(new BlockPos(2,1,2)),Direction.NORTH));
        var target=new ProvisionRequest.EntityTarget(UUID.randomUUID());
        var caller=named(11);
        var request=GolemProvisioning.request(level,target,caller,4).orElseThrow();
        caller.remove(DataComponents.CUSTOM_NAME); caller.setCount(1);
        h.assertTrue(ItemStack.matches(request.item().stack(request.count()),named(11)),"Request never shares mutable caller stack");
        h.assertTrue(GolemProvisioning.request(level,target,named(11),4).orElseThrow()==request,"Count/components/UI deduplicate exactly");
        h.assertTrue(GolemProvisioning.request(level,target,named(11),5).orElseThrow()!=request,"UI discriminator separates otherwise identical requests");
        var pickup=ProvisionRules.pickup(100_001,provider,request,(byte)0);
        h.assertTrue(board.link(request,null,pickup),"Request links pickup");
        var delivery=ProvisionRules.delivery(100_002,pickup,request);
        h.assertTrue(board.link(request,pickup,delivery) && delivery.getData()==1 && delivery.getEntityId().equals(target.entityId()),"Entity delivery keeps UUID and request identity");
        pickup.setSuspended(true);
        h.assertTrue(!board.unlink(request,pickup) && request.linkedTask()==delivery && delivery.getLinkedProvision()==request,"Stale pickup callback cannot release successor");
        board.invalidate(request); board.sweep();
        h.assertTrue(delivery.isSuspended() && delivery.getLinkedProvision()==null && request.invalid(),"Cleanup invalidates both sides without creating items");
        h.succeed();
    }
}
