package dev.ehtid.thaumcraft.port.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exact built-in BETA26 aspect table and the legacy registry/cache behavior that gameplay code depends on.
 * Definitions were extracted from Aspect.<clinit> bytecode in the supplied JAR.
 */
public final class AspectRegistry {
    private final LinkedHashMap<String, AspectData> aspects = new LinkedHashMap<>();
    private final Map<Integer, AspectData> mixList = new HashMap<>();
    private final ArrayList<AspectData> primalCache = new ArrayList<>();
    private final ArrayList<AspectData> compoundCache = new ArrayList<>();

    public final AspectData AIR;
    public final AspectData EARTH;
    public final AspectData FIRE;
    public final AspectData WATER;
    public final AspectData ORDER;
    public final AspectData ENTROPY;
    public final AspectData VOID;
    public final AspectData LIGHT;
    public final AspectData MOTION;
    public final AspectData COLD;
    public final AspectData CRYSTAL;
    public final AspectData METAL;
    public final AspectData LIFE;
    public final AspectData DEATH;
    public final AspectData ENERGY;
    public final AspectData EXCHANGE;
    public final AspectData MAGIC;
    public final AspectData AURA;
    public final AspectData ALCHEMY;
    public final AspectData FLUX;
    public final AspectData DARKNESS;
    public final AspectData ELDRITCH;
    public final AspectData FLIGHT;
    public final AspectData PLANT;
    public final AspectData TOOL;
    public final AspectData CRAFT;
    public final AspectData MECHANISM;
    public final AspectData TRAP;
    public final AspectData SOUL;
    public final AspectData MIND;
    public final AspectData SENSES;
    public final AspectData AVERSION;
    public final AspectData PROTECT;
    public final AspectData DESIRE;
    public final AspectData UNDEAD;
    public final AspectData BEAST;
    public final AspectData MAN;

    public AspectRegistry() {
        AIR = primal("aer", 16777086, "e", 1);
        EARTH = primal("terra", 5685248, "2", 1);
        FIRE = primal("ignis", 16734721, "c", 1);
        WATER = primal("aqua", 3986684, "3", 1);
        ORDER = primal("ordo", 14013676, "7", 1);
        ENTROPY = primal("perditio", 4210752, "8", 771);
        VOID = compound("vacuos", 8947848, AIR, ENTROPY, 771);
        LIGHT = compound("lux", 16777152, AIR, FIRE, 1);
        MOTION = compound("motus", 13487348, AIR, ORDER, 1);
        COLD = compound("gelum", 14811135, FIRE, ENTROPY, 1);
        CRYSTAL = compound("vitreus", 8454143, EARTH, AIR, 1);
        METAL = compound("metallum", 11908557, EARTH, ORDER, 1);
        LIFE = compound("victus", 14548997, EARTH, WATER, 1);
        DEATH = compound("mortuus", 6946821, WATER, ENTROPY, 1);
        ENERGY = compound("potentia", 12648447, ORDER, FIRE, 1);
        EXCHANGE = compound("permutatio", 5735255, ENTROPY, ORDER, 1);
        MAGIC = compound("praecantatio", 13566207, ENERGY, AIR, 1);
        AURA = compound("auram", 16761087, MAGIC, AIR, 1);
        ALCHEMY = compound("alkimia", 2337949, MAGIC, WATER, 1);
        FLUX = compound("vitium", 8388736, ENTROPY, MAGIC, 1);
        DARKNESS = compound("tenebrae", 2236962, VOID, LIGHT, 1);
        ELDRITCH = compound("alienis", 8409216, VOID, DARKNESS, 1);
        FLIGHT = compound("volatus", 15198167, AIR, MOTION, 1);
        PLANT = compound("herba", 109568, LIFE, EARTH, 1);
        TOOL = compound("instrumentum", 4210926, METAL, ENERGY, 1);
        CRAFT = compound("fabrico", 8428928, EXCHANGE, TOOL, 1);
        MECHANISM = compound("machina", 8421536, MOTION, TOOL, 1);
        TRAP = compound("vinculum", 10125440, MOTION, ENTROPY, 1);
        SOUL = compound("spiritus", 15461371, LIFE, DEATH, 1);
        MIND = compound("cognitio", 16356991, FIRE, SOUL, 1);
        SENSES = compound("sensus", 12648384, AIR, SOUL, 1);
        AVERSION = compound("aversio", 12603472, SOUL, ENTROPY, 1);
        PROTECT = compound("praemunio", 49344, SOUL, EARTH, 1);
        DESIRE = compound("desiderium", 15121988, SOUL, VOID, 1);
        UNDEAD = compound("exanimis", 3817472, MOTION, DEATH, 1);
        BEAST = compound("bestia", 10445833, MOTION, LIFE, 1);
        MAN = compound("humanus", 16766912, SOUL, LIFE, 1);
    }

    public AspectData get(String tag) { return aspects.get(tag); }
    public Collection<AspectData> values() { return aspects.values(); }
    public int size() { return aspects.size(); }

    public AspectData register(String tag, int color, List<AspectData> components, String chatColor, int blend) {
        if (aspects.containsKey(tag)) throw new IllegalArgumentException(tag + " already registered!");
        AspectData aspect = new AspectData(tag, color, components, chatColor, blend);
        aspects.put(tag, aspect);
        if (components != null) {
            // TC6 hashes the concatenated component tags in registration order.
            mixList.put((components.get(0).tag() + components.get(1).tag()).hashCode(), aspect);
        }
        // Intentionally do not invalidate primal/compound caches: BETA26 doesn't either.
        return aspect;
    }

    /** Same order-independent semantic result as ResearchManager.getCombinationResult. */
    public AspectData combinationResult(AspectData a, AspectData b) {
        for (AspectData candidate : aspects.values()) {
            List<AspectData> components = candidate.components();
            if (components == null || components.size() < 2) continue;
            if ((components.get(0) == a && components.get(1) == b)
                    || (components.get(0) == b && components.get(1) == a)) {
                return candidate;
            }
        }
        return null;
    }

    public ArrayList<AspectData> primalAspects() {
        if (primalCache.isEmpty()) {
            for (AspectData aspect : aspects.values()) if (aspect.isPrimal()) primalCache.add(aspect);
        }
        return primalCache;
    }

    public ArrayList<AspectData> compoundAspects() {
        if (compoundCache.isEmpty()) {
            for (AspectData aspect : aspects.values()) if (!aspect.isPrimal()) compoundCache.add(aspect);
        }
        return compoundCache;
    }

    private AspectData primal(String tag, int color, String chatColor, int blend) {
        return register(tag, color, null, chatColor, blend);
    }

    private AspectData compound(String tag, int color, AspectData a, AspectData b, int blend) {
        return register(tag, color, List.of(a, b), null, blend);
    }
}
