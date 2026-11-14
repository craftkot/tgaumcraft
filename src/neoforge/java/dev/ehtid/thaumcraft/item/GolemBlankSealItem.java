package dev.ehtid.thaumcraft.item;

import dev.ehtid.thaumcraft.golem.GolemSealDisplayer;
import net.minecraft.world.item.Item;

/** The source blank seal shares the displayer marker, without placement behavior. */
public final class GolemBlankSealItem extends Item implements GolemSealDisplayer {
    public GolemBlankSealItem(Properties properties) { super(properties); }
}
