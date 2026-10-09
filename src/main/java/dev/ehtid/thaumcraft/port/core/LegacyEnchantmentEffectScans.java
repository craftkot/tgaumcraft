package dev.ehtid.thaumcraft.port.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Vanilla portion of BETA26 ConfigResearch.initScannables registry loops.
 *
 * BETA26 creates one ScanEnchantment per Enchantment.REGISTRY entry and one
 * ScanPotion per Potion.REGISTRY entry. Their research keys are NOT modern
 * registry names: ScanEnchantment returns "!" + Enchantment.getName() and
 * ScanPotion returns "!" + Potion.getName(). Those strings used the 1.12
 * enchantment.* and effect.* localization keys.
 *
 * ScanEnchantment checks enchantments on an ItemStack from
 * ScanningManager.getItemFromParms. ScanPotion checks the active effects on
 * a living entity OR PotionUtils.getEffectsFromStack for an item.
 *
 * Only verified vanilla 1.12 registry entries are mapped here. Unknown
 * modern enchantments/effects must not masquerade as BETA26 research markers.
 */
public final class LegacyEnchantmentEffectScans {
    private LegacyEnchantmentEffectScans() {}

    private static final Map<String, String> ENCHANTMENTS = buildEnchantments();
    private static final Map<String, String> EFFECTS = buildEffects();

    private static Map<String, String> buildEnchantments() {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        map.put("minecraft:protection", "enchantment.protect.all");
        map.put("minecraft:fire_protection", "enchantment.protect.fire");
        map.put("minecraft:feather_falling", "enchantment.protect.fall");
        map.put("minecraft:blast_protection", "enchantment.protect.explosion");
        map.put("minecraft:projectile_protection", "enchantment.protect.projectile");
        map.put("minecraft:respiration", "enchantment.oxygen");
        map.put("minecraft:aqua_affinity", "enchantment.waterWorker");
        map.put("minecraft:thorns", "enchantment.thorns");
        map.put("minecraft:depth_strider", "enchantment.waterWalker");
        map.put("minecraft:frost_walker", "enchantment.frostWalker");
        map.put("minecraft:binding_curse", "enchantment.binding_curse");
        map.put("minecraft:sharpness", "enchantment.damage.all");
        map.put("minecraft:smite", "enchantment.damage.undead");
        map.put("minecraft:bane_of_arthropods", "enchantment.damage.arthropods");
        map.put("minecraft:knockback", "enchantment.knockback");
        map.put("minecraft:fire_aspect", "enchantment.fire");
        map.put("minecraft:looting", "enchantment.lootBonus");
        map.put("minecraft:sweeping_edge", "enchantment.sweeping");
        map.put("minecraft:efficiency", "enchantment.digging");
        map.put("minecraft:silk_touch", "enchantment.untouching");
        map.put("minecraft:unbreaking", "enchantment.durability");
        map.put("minecraft:fortune", "enchantment.lootBonusDigger");
        map.put("minecraft:power", "enchantment.arrowDamage");
        map.put("minecraft:punch", "enchantment.arrowKnockback");
        map.put("minecraft:flame", "enchantment.arrowFire");
        map.put("minecraft:infinity", "enchantment.arrowInfinite");
        map.put("minecraft:luck_of_the_sea", "enchantment.lootBonusFishing");
        map.put("minecraft:lure", "enchantment.fishingSpeed");
        map.put("minecraft:mending", "enchantment.mending");
        map.put("minecraft:vanishing_curse", "enchantment.vanishing_curse");
        return Collections.unmodifiableMap(map);
    }

    private static Map<String, String> buildEffects() {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        map.put("minecraft:speed", "effect.moveSpeed");
        map.put("minecraft:slowness", "effect.moveSlowdown");
        map.put("minecraft:haste", "effect.digSpeed");
        map.put("minecraft:mining_fatigue", "effect.digSlowDown");
        map.put("minecraft:strength", "effect.damageBoost");
        map.put("minecraft:instant_health", "effect.heal");
        map.put("minecraft:instant_damage", "effect.harm");
        map.put("minecraft:jump_boost", "effect.jump");
        map.put("minecraft:nausea", "effect.confusion");
        map.put("minecraft:regeneration", "effect.regeneration");
        map.put("minecraft:resistance", "effect.resistance");
        map.put("minecraft:fire_resistance", "effect.fireResistance");
        map.put("minecraft:water_breathing", "effect.waterBreathing");
        map.put("minecraft:invisibility", "effect.invisibility");
        map.put("minecraft:blindness", "effect.blindness");
        map.put("minecraft:night_vision", "effect.nightVision");
        map.put("minecraft:hunger", "effect.hunger");
        map.put("minecraft:weakness", "effect.weakness");
        map.put("minecraft:poison", "effect.poison");
        map.put("minecraft:wither", "effect.wither");
        map.put("minecraft:health_boost", "effect.healthBoost");
        map.put("minecraft:absorption", "effect.absorption");
        map.put("minecraft:saturation", "effect.saturation");
        map.put("minecraft:glowing", "effect.glowing");
        map.put("minecraft:levitation", "effect.levitation");
        map.put("minecraft:luck", "effect.luck");
        map.put("minecraft:unluck", "effect.unluck");
        return Collections.unmodifiableMap(map);
    }

    /** Vanilla 1.12 enchantment scan research markers, in stable registry order. */
    public static List<String> enchantmentResearchKeys() {
        return ENCHANTMENTS.values().stream().map(key -> "!" + key).toList();
    }

    /** Vanilla 1.12 effect scan research markers, in stable registry order. */
    public static List<String> effectResearchKeys() {
        return EFFECTS.values().stream().map(key -> "!" + key).toList();
    }

    public static Set<String> forItemEnchantments(Set<String> modernEnchantmentIds) {
        return lookup(modernEnchantmentIds, ENCHANTMENTS);
    }

    public static Set<String> forPotionEffects(Set<String> modernEffectIds) {
        return lookup(modernEffectIds, EFFECTS);
    }

    private static Set<String> lookup(Set<String> ids, Map<String, String> aliases) {
        Objects.requireNonNull(ids, "ids");
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        for (String id : ids) {
            String legacyName = aliases.get(id);
            if (legacyName != null) keys.add("!" + legacyName);
        }
        return Set.copyOf(keys);
    }

    public static List<String> allResearchKeys() {
        ArrayList<String> keys = new ArrayList<>(ENCHANTMENTS.size() + EFFECTS.size());
        keys.addAll(enchantmentResearchKeys());
        keys.addAll(effectResearchKeys());
        return List.copyOf(keys);
    }
}
