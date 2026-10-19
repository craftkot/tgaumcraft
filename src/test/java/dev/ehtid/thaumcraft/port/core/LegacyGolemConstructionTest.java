package dev.ehtid.thaumcraft.port.core;

import dev.ehtid.thaumcraft.port.core.golem.*;
import java.util.Set;
import static dev.ehtid.thaumcraft.port.core.golem.GolemTrait.*;

/** Standalone golden values recovered from BETA26 entity/properties bytecode. */
public final class LegacyGolemConstructionTest {
    private static int assertions;
    private static void same(Object expected, Object actual) {
        assertions++;
        if (!java.util.Objects.equals(expected, actual))
            throw new AssertionError("Expected " + expected + ", got " + actual);
    }
    private static void near(double expected, double actual) {
        assertions++;
        if (Math.abs(expected - actual) > 0.000001D)
            throw new AssertionError("Expected " + expected + ", got " + actual);
    }
    private static void invalid(Runnable action) {
        assertions++;
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Invalid design was accepted");
    }
    public static void main(String[] args) {
        componentRecipes();
        same("MINDCLOCKWORK", GolemPressBlueprint.RESEARCH); same(50, GolemPressBlueprint.DELAY);
        int[][] controllers = {{1,0},{0,0},{0,1},{1,1}};
        for (int rotation = 0; rotation < 4; rotation++) {
            var cells = GolemPressBlueprint.cells(rotation);
            same(5, cells.size()); same(5L, cells.stream().map(GolemPressBlueprint.Cell::part).distinct().count());
            same(controllers[rotation][0], cells.get(2).x()); same(controllers[rotation][1], cells.get(2).z());
            same(0, cells.get(2).y()); same(1, cells.get(4).y());
            same(cells.get(2).x(), cells.get(4).x()); same(cells.get(2).z(), cells.get(4).z());
            same(4L, cells.stream().filter(c -> c.y() == 0).count());
            same(5L, cells.stream().map(c -> c.x()+":"+c.y()+":"+c.z()).distinct().count());
        }
        invalid(() -> GolemPressBlueprint.cells(-1)); invalid(() -> GolemPressBlueprint.cells(4));
        same(6, GolemMaterial.values().length);
        same(5, GolemCatalog.HEADS.size());
        same(5, GolemCatalog.ARMS.size());
        same(4, GolemCatalog.LEGS.size());
        same(4, GolemCatalog.ADDONS.size());
        var wood = GolemDesign.defaultWood();
        var components = GolemAssembly.components(wood);
        same(java.util.List.of(
                new GolemAssembly.Component("thaumcraft:plank_greatwood", 0, 3),
                new GolemAssembly.Component("thaumcraft:mechanism_simple", 0, 2),
                new GolemAssembly.Component("thaumcraft:mind", 0, 1)), components);
        same(components, GolemAssembly.components(wood.withRank(10)));
        same(java.util.List.of(new GolemAssembly.Component("thaumcraft:plate",1,3),
                new GolemAssembly.Component("thaumcraft:mechanism_simple",0,2),new GolemAssembly.Component("thaumcraft:mind",0,1)),
                GolemAssembly.components(GolemDesign.fromIds(1,0,0,0,0,0)));
        assemblyCatalog();
        same(0L, wood.toLong());
        same(8, GolemPress.cost(wood));
        var press = GolemPress.State.idle();
        same(false, press.accepts("machina", 1));
        same(false, press.ready());
        press = press.startPaid(wood);
        same(8, press.remaining());
        same(8, press.maximum());
        same(false, press.accepts("aer", 1));
        same(false, press.accepts("machina", 0));
        for (int unit = 0; unit < 8; unit++) {
            same(press, press.consumeBuffered()); // No phantom progress without actual payment.
            press = press.receive("machina", 64); // One-unit buffer, never bulk-credit.
            same(false, press.accepts("machina", 1));
            same(press, new GolemPress.State(press.props(), press.remaining(), press.maximum(), true));
            press = press.consumeBuffered();
            same(7 - unit, press.remaining());
        }
        same(true, press.ready());
        same(false, press.accepts("machina", 1));
        same(GolemPress.State.idle(), press.finish());
        invalid(() -> new GolemPress.State(0, -1, 8, false));
        invalid(() -> new GolemPress.State(0, 9, 8, false));
        invalid(() -> new GolemPress.State(0, 0, 8, true));
        invalid(() -> new GolemPress.State(0, 8, 7, false));
        invalid(() -> new GolemPress.State(-1, 0, 0, true));
        invalid(() -> GolemPress.State.idle().startPaid(wood.withRank(1)));
        same(Set.of(LIGHT), wood.traits());
        same(16, wood.stats().maxHealth());
        same(2, wood.stats().armor());
        near(0D, wood.stats().attackDamage());
        near(.3D, wood.stats().movementSpeed());
        near(1.2D, wood.stats().moveSpeedMultiplier());
        same(1, wood.stats().carrySlots());
        same(32, wood.stats().homeRadius());
        near(40D, wood.stats().followRange());
        near(.6D, wood.stats().stepHeight());
        int[] health = {16, 30, 20, 26, 34, 30};
        int[] armor = {2, 8, 4, 6, 10, 6};
        for (int material = 0; material < 6; material++) {
            var basic = GolemDesign.fromIds(material, 0, 0, 0, 0, 0);
            same(health[material], basic.stats().maxHealth());
            same(armor[material], basic.stats().armor());
        }
        var fragileWood = GolemDesign.fromIds(0, 1, 1, 0, 0, 3);
        same(Set.of(LIGHT, SMART, DEFT, FRAGILE), fragileWood.traits());
        same(15, fragileWood.stats().maxHealth()); // floor(16*.75)+3
        same(1, fragileWood.stats().armor());
        near(1.275D, fragileWood.stats().moveSpeedMultiplier());
        var cancelled = GolemDesign.fromIds(0, 1, 1, 0, 1, 0);
        same(Set.of(SMART, DEFT), cancelled.traits()); // opposite traits remove both
        same(16, cancelled.stats().maxHealth());
        same(2, cancelled.stats().armor());
        near(1D, cancelled.stats().moveSpeedMultiplier());
        var heavyArmor = GolemDesign.fromIds(1, 0, 0, 0, 1, 0);
        same(12, heavyArmor.stats().armor());
        near(.825D, heavyArmor.stats().moveSpeedMultiplier());
        var claws = GolemDesign.fromIds(1, 2, 2, 0, 0, 4);
        same(Set.of(HEAVY, FIREPROOF, BLASTPROOF, SMART, FIGHTER, CLUMSY, BRUTAL), claws.traits());
        near(5.5D, claws.stats().attackDamage());
        near(0D, GolemDesign.fromIds(1, 0, 3, 0, 0, 4).stats().attackDamage());
        near(2D, GolemDesign.fromIds(0, 0, 2, 0, 0, 0).stats().attackDamage());
        var scoutHauler = GolemDesign.fromIds(5, 4, 0, 1, 3, 10);
        same(48, scoutHauler.stats().homeRadius());
        near(56D, scoutHauler.stats().followRange());
        same(2, scoutHauler.stats().carrySlots());
        near(.5D, scoutHauler.stats().stepHeight());
        near(1.5D, scoutHauler.stats().moveSpeedMultiplier());
        var flyer = GolemDesign.fromIds(3, 0, 0, 3, 0, 0);
        same(19, flyer.stats().maxHealth());
        same(4, flyer.stats().armor());
        near(.87D, flyer.stats().moveSpeedMultiplier());
        same(DEFT, CLUMSY.opposite());
        same(CLUMSY, DEFT.opposite());
        same(null, SMART.opposite());
        invalid(() -> GolemDesign.fromLong(6L));
        invalid(() -> GolemDesign.fromIds(0, 5, 0, 0, 0, 0));
        invalid(() -> GolemDesign.fromIds(0, 0, 5, 0, 0, 0));
        invalid(() -> GolemDesign.fromIds(0, 0, 0, 4, 0, 0));
        invalid(() -> GolemDesign.fromIds(0, 0, 0, 0, 4, 0));
        invalid(() -> GolemDesign.fromIds(0, 0, 0, 0, 0, 11));
        invalid(() -> wood.withRank(-1));
        invalid(() -> GolemDesign.fromIds(-1, 0, 0, 0, 0, 0));
        for (int m = 0; m < 6; m++) for (int h = 0; h < 5; h++)
            for (int a = 0; a < 5; a++) for (int l = 0; l < 4; l++)
                for (int addon = 0; addon < 4; addon++) {
                    var design = GolemDesign.fromIds(m, h, a, l, addon, 7);
                    long expected = m | ((long) h << 8) | ((long) a << 16) | ((long) l << 24)
                        | ((long) addon << 32) | (7L << 40) | 0xABCD000000000000L;
                    var restored = GolemDesign.fromLong(design.toLong() | 0xABCD000000000000L);
                    same(expected, restored.toLong());
                    same(m, restored.material().id());
                    same(h, restored.head().id());
                    same(a, restored.arms().id());
                    same(l, restored.legs().id());
                    same(addon, restored.addon().id());
                    same(7, restored.rank());
                    same(expected & ~(0xffL << 40), restored.withRank(0).toLong());
                    for (var trait : restored.traits())
                        same(false, trait.opposite() != null && restored.hasTrait(trait.opposite()));
                }
        System.out.println("BETA26 golem construction: OK (" + assertions + " assertions)");
    }

    private static void componentRecipes() {
        var mechanism = GolemComponentRecipes.Recipe.SIMPLE_MECHANISM;
        var mind = GolemComponentRecipes.Recipe.CLOCKWORK_MIND;
        same("BASEARTIFICE", mechanism.research); same(10, mechanism.vis); same(6, mechanism.crystalMask);
        same("MINDCLOCKWORK@2", mind.research); same(25, mind.vis); same(18, mind.crystalMask);
        same(7, mechanism.discountedVis(.25F)); same(18, mind.discountedVis(.25F));
        same(0, mind.discountedVis(1));
        invalid(() -> mind.discountedVis(Float.NaN)); invalid(() -> mind.discountedVis(Float.POSITIVE_INFINITY));
        invalid(() -> mind.discountedVis(-.01F)); invalid(() -> mind.discountedVis(1.01F));
        // Independent golden matrices, including mandatory empty corners and comparator (not clock).
        String[] golden = {" B ISI B ", " P PGPBCB"};
        int recipeIndex = 0;
        for (var recipe : GolemComponentRecipes.Recipe.values()) {
            int[] grid = new int[9];
            String pattern = golden[recipeIndex++];
            for (int slot = 0; slot < 9; slot++) grid[slot] = GolemComponentRecipes.bit(switch (pattern.charAt(slot)) {
                case 'B' -> GolemComponentRecipes.Ingredient.BRASS_PLATE;
                case 'I' -> GolemComponentRecipes.Ingredient.IRON_PLATE;
                case 'S' -> GolemComponentRecipes.Ingredient.WOODEN_ROD;
                case 'P' -> GolemComponentRecipes.Ingredient.GLASS_PANE;
                case 'G' -> GolemComponentRecipes.Ingredient.SIMPLE_MECHANISM;
                case 'C' -> GolemComponentRecipes.Ingredient.COMPARATOR;
                default -> GolemComponentRecipes.Ingredient.EMPTY;
            });
            same(true, recipe.matches(grid)); same(false, recipe.matches(null)); same(false, recipe.matches(new int[8]));
            for (int slot = 0; slot < 9; slot++) for (var replacement : GolemComponentRecipes.Ingredient.values()) {
                int original = grid[slot]; grid[slot] = GolemComponentRecipes.bit(replacement);
                same(grid[slot] == original, recipe.matches(grid)); grid[slot] = original;
            }
        }
        var knowledge = new PlayerKnowledgeData();
        same(false, knowledge.isResearchKnown(mind.research));
        knowledge.addResearch("MINDCLOCKWORK"); knowledge.setResearchStage("MINDCLOCKWORK", 1);
        same(false, knowledge.isResearchKnown(mind.research));
        knowledge.setResearchStage("MINDCLOCKWORK", 2); same(true, knowledge.isResearchKnown(mind.research));
    }

    private static int component(GolemDesign design,String id,int meta) {
        return GolemAssembly.components(design).stream().filter(c->c.legacyItemId().equals(id)&&c.metadata()==meta).mapToInt(GolemAssembly.Component::count).sum();
    }
    private static void assemblyCatalog() {
        // Golden totals from the BETA26 registration arrays, including each ItemStack's own count.
        int[] heads={1,1,4,2,2},arms={0,2,4,5,36},legs={2,4,7,7},addons={0,4,2,2};
        int supported=0;
        for(int m=0;m<6;m++) for(int h=0;h<5;h++) for(int a=0;a<5;a++) for(int l=0;l<4;l++) for(int x=0;x<4;x++) {
            var d=GolemDesign.fromIds(m,h,a,l,x,0); var rows=GolemAssembly.components(d);
            same(3+heads[h]+arms[a]+legs[l]+addons[x],rows.stream().mapToInt(GolemAssembly.Component::count).sum());
            same((long)rows.size(),rows.stream().map(c->c.legacyItemId()+":"+c.metadata()).distinct().count());
            if(GolemRuntimeRules.groundWorker(d)) supported++;
        }
        same(180,supported);
        var advanced=GolemDesign.fromIds(3,2,1,1,1,0);
        same(9,component(advanced,"thaumcraft:plate",0));
        same(3,component(advanced,"thaumcraft:mechanism_simple",0));
        same(1,component(advanced,"minecraft:leather",0));
        same(2,component(advanced,"minecraft:bowl",0));
        same(1,component(advanced,"minecraft:wool",0));
        same(1,component(advanced,"thaumcraft:mind",1));
        var breakers=GolemDesign.fromIds(0,0,3,0,0,0);
        same(2,component(breakers,"minecraft:diamond",0)); same(2,component(breakers,"minecraft:piston",0));
        same(4,component(GolemDesign.fromIds(0,0,0,2,0,0),"minecraft:flint",0));
        var flyer=GolemDesign.fromIds(0,0,0,3,0,0);
        same(4,component(flyer,"thaumcraft:plate",0)); same(1,component(flyer,"minecraft:slime_ball",0));
        same(1,component(flyer,"thaumcraft:levitator",0)); same(false,GolemRuntimeRules.groundWorker(flyer));
        var hauler=GolemDesign.fromIds(0,1,1,0,3,10);
        same(true,GolemRuntimeRules.groundWorker(hauler));
        same(1,component(hauler,"minecraft:leather",0)); same(1,component(hauler,"minecraft:chest",0));
        same(Set.of("MATSTUDWOOD","MINDBIOTHAUMIC","MATSTUDBRASS","MINDCLOCKWORK"),GolemRuntimeRules.research(hauler));
        var iron=GolemDesign.fromIds(1,0,0,0,0,0); var clay=GolemDesign.fromIds(2,0,0,0,0,0);
        same(0F,GolemDamageRules.amount(iron,10,30,true,false,false));
        same(3F,GolemDamageRules.amount(iron,10,30,false,true,false));
        same(15F,GolemDamageRules.amount(iron,100,30,false,true,false));
        same(10F,GolemDamageRules.amount(clay,10,20,false,true,false));
        same(0F,GolemDamageRules.amount(GolemDesign.defaultWood(),10,16,false,false,true));
        same(10F,GolemDamageRules.amount(GolemDesign.defaultWood(),10,16,true,false,false));
    }
}
