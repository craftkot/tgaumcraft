package dev.ehtid.thaumcraft.port.core.golem;

import dev.ehtid.thaumcraft.port.core.GenericScanKeyLogic;
import java.util.Objects;

/** Reuses the project's audited pre-flattening aliases; does not change research keys. */
public record GolemLegacyItem(String id,int metadata) {
    public static final int WILDCARD=32767;
    public GolemLegacyItem { Objects.requireNonNull(id); }
    public static GolemLegacyItem modern(String id,boolean damageable,int damage) {
        if(damageable) return new GolemLegacyItem(id,damage);
        String extra=switch(id) {
            case "minecraft:oak_fence"->"minecraft:fence";
            case "minecraft:oak_fence_gate"->"minecraft:fence_gate";
            case "minecraft:oak_door"->"minecraft:wooden_door";
            case "minecraft:nether_brick"->"minecraft:netherbrick";
            case "minecraft:sugar_cane"->"minecraft:reeds";
            case "thaumcraft:mind_biothaumic"->"thaumcraft:mind|1";
            case "thaumcraft:module_vision"->"thaumcraft:modules|0";
            case "thaumcraft:module_aggression"->"thaumcraft:modules|1";
            default->null;
        };
        if(extra!=null) { int split=extra.indexOf('|'); return split<0?new GolemLegacyItem(extra,0):new GolemLegacyItem(extra.substring(0,split),Integer.parseInt(extra.substring(split+1))); }
        String[] woods={"oak","spruce","birch","jungle","acacia","dark_oak"};
        for(int i=0;i<woods.length;i++) if(id.equals("minecraft:"+woods[i]+"_slab")) return new GolemLegacyItem("minecraft:wooden_slab",i);
        if(id.startsWith("minecraft:music_disc_")) {
            String disc=id.substring("minecraft:music_disc_".length());
            if(java.util.Set.of("13","cat","blocks","chirp","far","mall","mellohi","stal","strad","ward","11","wait").contains(disc)) return new GolemLegacyItem("minecraft:record_"+disc,0);
        }
        // Sentinel distinguishes a proven constant alias from the ordinary forItem fallback.
        String key=GenericScanKeyLogic.forModernItem(id,false,-1).substring(1);
        if(key.equals(id+"-1")) return new GolemLegacyItem(id,damage);
        for(String numericFamily:new String[]{"minecraft:log2","minecraft:leaves2"})
            if(key.startsWith(numericFamily)) return new GolemLegacyItem(numericFamily,Integer.parseInt(key.substring(numericFamily.length())));
        int split=key.length(); while(split>0 && Character.isDigit(key.charAt(split-1))) split--;
        if(split==key.length()) throw new IllegalStateException("Audited alias lacks metadata: "+key);
        return new GolemLegacyItem(key.substring(0,split),Integer.parseInt(key.substring(split)));
    }
}
