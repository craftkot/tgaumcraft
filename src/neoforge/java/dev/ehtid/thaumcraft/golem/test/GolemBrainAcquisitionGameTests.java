package dev.ehtid.thaumcraft.golem.test;

import com.mojang.authlib.GameProfile;
import dev.ehtid.thaumcraft.golem.GolemModule;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.ArrayList;
import java.util.UUID;

@GameTestHolder("thaumcraft") @PrefixGameTestTemplate(false)
public final class GolemBrainAcquisitionGameTests {
    private GolemBrainAcquisitionGameTests() {}

    @GameTest(template="golem_test", batch="acquisition_brain_gate", timeoutTicks=40)
    public static void originalBrainDropsRequireRecentHitAndZombieWithoutReplacingOtherDrops(GameTestHelper h) {
        var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "BrainDropGate"));
        player.getInventory().clearContent();
        var zombie = EntityType.ZOMBIE.create(h.getLevel());
        var skeleton = EntityType.SKELETON.create(h.getLevel());
        var drops = new ArrayList<ItemEntity>();
        var original = new ItemEntity(h.getLevel(), 0, 0, 0, new ItemStack(Items.ROTTEN_FLESH, 3)); drops.add(original);
        NeoForge.EVENT_BUS.post(new LivingDropsEvent(zombie, h.getLevel().damageSources().generic(), drops, false));
        NeoForge.EVENT_BUS.post(new LivingDropsEvent(skeleton, h.getLevel().damageSources().playerAttack(player), drops, true));
        h.assertTrue(drops.size() == 1 && drops.getFirst() == original && original.getItem().getCount() == 3,
                "Environmental zombie death and recently hit skeleton cannot grant brain or alter existing drops");
        h.getLevel().getRandom().setSeed(0); // First source nextInt10=0; no tick or other RNG caller before synchronous event.
        NeoForge.EVENT_BUS.post(new LivingDropsEvent(zombie, h.getLevel().damageSources().playerAttack(player), drops, true));
        h.assertTrue(drops.size() == 2 && drops.getFirst() == original && drops.getLast().getItem().is(GolemModule.ZOMBIE_BRAIN.get())
                && drops.getLast().getItem().getCount() == 1, "Original successful Looting0 roll adds one brain to event collection");
        zombie.discard(); skeleton.discard(); h.succeed();
    }

    @GameTest(template="golem_test", batch="acquisition_brain_death", timeoutTicks=40)
    public static void actualPlayerAttributedZombieDeathDropsOneRealRegisteredBrain(GameTestHelper h) {
        var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "BrainDropDeath"));
        player.setGameMode(GameType.SURVIVAL); player.getInventory().clearContent();
        var sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.LOOTING), 9);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, sword);
        var zombie = EntityType.ZOMBIE.create(h.getLevel()); zombie.setNoAi(true);
        zombie.moveTo(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(5, 2, 5)))); h.getLevel().addFreshEntity(zombie);
        zombie.hurt(h.getLevel().damageSources().playerAttack(player), 1000);
        var brains = h.getLevel().getEntitiesOfClass(ItemEntity.class, h.getBounds(), e -> e.getItem().is(GolemModule.ZOMBIE_BRAIN.get()));
        h.assertTrue(!zombie.isAlive() && brains.stream().mapToInt(e -> e.getItem().getCount()).sum() == 1,
                "Source Looting9 guarantees exactly one actual brain through ordinary death/drop engine, without fabricated infusion inputs");
        brains.forEach(ItemEntity::discard); zombie.discard(); player.getInventory().clearContent(); h.succeed();
    }
}
