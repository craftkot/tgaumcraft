package dev.ehtid.thaumcraft.port.core;

/** Exact unambiguous BETA26 object-tag slice extracted from ConfigAspects.registerItemAspects bytecode. */
public final class LegacyObjectAspectTable {
    private LegacyObjectAspectTable() {}
    public static AspectListData get(AspectRegistry a, String id) {
        return switch (id) {
            case "minecraft:diamond" -> list(a.CRYSTAL,15,a.DESIRE,15);
            case "minecraft:emerald" -> list(a.CRYSTAL,15,a.DESIRE,10);
            case "minecraft:quartz" -> list(a.CRYSTAL,5);
            case "minecraft:iron_ingot" -> list(a.METAL,15);
            case "minecraft:gold_ingot" -> list(a.METAL,10,a.DESIRE,10);
            case "minecraft:redstone" -> list(a.ENERGY,10);
            case "minecraft:glowstone_dust" -> list(a.SENSES,5,a.LIGHT,10);
            case "minecraft:diamond_ore" -> list(a.EARTH,5,a.DESIRE,15,a.CRYSTAL,15);
            case "minecraft:emerald_ore" -> list(a.EARTH,5,a.DESIRE,10,a.CRYSTAL,15);
            case "minecraft:iron_ore" -> list(a.EARTH,5,a.METAL,15);
            case "minecraft:gold_ore" -> list(a.EARTH,5,a.METAL,10,a.DESIRE,10);
            case "minecraft:redstone_ore" -> list(a.EARTH,5,a.ENERGY,15);
            default -> new AspectListData();
        };
    }
    private static AspectListData list(Object... p){ AspectListData o=new AspectListData(); for(int i=0;i<p.length;i+=2)o.add((AspectData)p[i],(Integer)p[i+1]); return o; }
}
