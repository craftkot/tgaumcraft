package dev.ehtid.thaumcraft.golem.crafting;

import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.port.core.golem.GolemBrainDropRules;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

/** Restores source zombie loot independently of the optional infusion runtime. */
public final class GolemBrainDrops {
    private GolemBrainDrops() {}
    private static final TagKey<EntityType<?>> BRAINY = TagKey.create(Registries.ENTITY_TYPE,
            ResourceLocation.parse("thaumcraft:brainy_zombies"));

    public static void livingDrops(LivingDropsEvent event) {
        if (event.isCanceled() || event.getEntity().level().isClientSide()
                || !(event.getEntity() instanceof Zombie zombie) || zombie.getType().is(BRAINY) || !event.isRecentlyHit()) return;
        int looting = 0;
        if (event.getSource().getEntity() instanceof LivingEntity attacker) {
            var enchantment = zombie.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.LOOTING);
            looting = EnchantmentHelper.getEnchantmentLevel(enchantment, attacker);
        }
        if (GolemBrainDropRules.shouldDrop(true, false, true, looting, zombie.level().getRandom().nextInt(10)))
            event.getDrops().add(new ItemEntity(zombie.level(), zombie.getX(), zombie.getY() + zombie.getEyeHeight(), zombie.getZ(),
                    new ItemStack(GolemModule.ZOMBIE_BRAIN.get())));
    }
}
