package dev.ehtid.thaumcraft.port.core.golem;

import java.util.List;
import static dev.ehtid.thaumcraft.port.core.golem.GolemTrait.*;

/** BETA26 built-in registration order; numeric IDs are the original packed-data IDs. */
public enum GolemMaterial {
    WOOD(6, 2, 1, 5059370, "MATSTUDWOOD", LIGHT),
    IRON(20, 8, 3, 16777215, "MATSTUDIRON", HEAVY, FIREPROOF, BLASTPROOF),
    CLAY(10, 4, 2, 13071447, "MATSTUDCLAY", FIREPROOF),
    BRASS(16, 6, 3, 15638812, "MATSTUDBRASS", LIGHT),
    THAUMIUM(24, 10, 4, 5257074, "MATSTUDTHAUMIUM", HEAVY, FIREPROOF, BLASTPROOF),
    VOID(20, 6, 4, 1445161, "MATSTUDVOID", REPAIR);

    private final int healthMod, armor, damage, itemColor;
    private final String research;
    private final List<GolemTrait> traits;

    GolemMaterial(int healthMod, int armor, int damage, int itemColor, String research, GolemTrait... traits) {
        this.healthMod = healthMod;
        this.armor = armor;
        this.damage = damage;
        this.itemColor = itemColor;
        this.research = research;
        this.traits = List.of(traits);
    }

    public int id() { return ordinal(); }
    public int healthMod() { return healthMod; }
    public int armor() { return armor; }
    public int damage() { return damage; }
    public int itemColor() { return itemColor; }
    public String research() { return research; }
    public List<GolemTrait> traits() { return traits; }
    public static GolemMaterial byId(int id) {
        if (id < 0 || id >= values().length) throw new IllegalArgumentException("Unknown BETA26 material: " + id);
        return values()[id];
    }
}
