package dev.ehtid.thaumcraft.golem.test;

import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.golem.seal.GolemSeals;
import dev.ehtid.thaumcraft.port.core.golem.GolemDesign;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

@GameTestHolder("thaumcraft")
@PrefixGameTestTemplate(false)
public final class GolemTaskRadiusGameTests {
    private GolemTaskRadiusGameTests() {}

    @GameTest(template="golem_test", batch="task_radius", timeoutTicks=80)
    public static void areaTaskInsideHomeCanWorkWhenItsSealAnchorIsOutside(GameTestHelper h) {
        for(int x=0;x<12;x++) for(int z=0;z<12;z++) h.setBlock(new BlockPos(x,0,z),Blocks.STONE);
        var level=h.getLevel(); var data=GolemSeals.get(level); var owner=UUID.randomUUID();
        var target=h.absolutePos(new BlockPos(5,1,6));
        var key=new GolemSeals.SealKey(target.east(),Direction.WEST);
        h.setBlock(new BlockPos(5,1,6),Blocks.STONE); h.setBlock(new BlockPos(6,1,6),Blocks.STONE);
        h.assertTrue(data.place(key.pos(),key.face(),owner,GolemSeals.Type.BREAKER),"Place area Breaker");
        h.assertTrue(data.configureArea(data.get(key),owner,new BlockPos(1,1,1)),"Its work block is west of the anchor");
        ThaumcraftGolem golem=GolemModule.GOLEM.get().create(level);
        golem.initialize(GolemDesign.fromIds(0,1,3,0,0,0),owner,target.west(31),0);
        golem.moveTo(Vec3.atBottomCenterOf(target.north())); golem.setOnGround(true);
        h.assertTrue(golem.home().distSqr(target)==31*31 && golem.home().distSqr(key.pos())==32*32,
                "Actual task is inside radius32 while the seal anchor is on the excluded boundary");
        h.assertTrue(level.addFreshEntity(golem),"Actual native AI Breaker");
        h.succeedWhen(()->{
            h.assertTrue(level.isEmptyBlock(target),"Source AIGotoBlock checks the task position against home, permitting this actual mining");
            int drops=level.getEntitiesOfClass(ItemEntity.class,h.getBounds()).stream()
                    .filter(e->e.getItem().is(Items.COBBLESTONE)).mapToInt(e->e.getItem().getCount()).sum();
            h.assertTrue(drops==1 && golem.rankXp()==1 && level.getBlockState(key.pos()).is(Blocks.STONE),
                    "Only the in-range target becomes one real cobblestone and one task XP");
            data.remove(key.pos(),key.face(),owner); golem.discard();
        });
    }
}
