package dev.ehtid.thaumcraft.port.core;
import java.util.Objects;
public final class CrystalGrowthParityTest {
  private static int total;
  public static void main(String[] args) {
    var original = LegacyBlockCrystalGrowthRules.initial();
    eq(new LegacyBlockCrystalGrowthRules.State(0,1),original);
    for(int gen=1;gen<=4;gen++) for(int size=0;size<=3;size++) {
      var s=new LegacyBlockCrystalGrowthRules.State(size,gen);
      eq(s,LegacyBlockCrystalGrowthRules.fromMetadata(LegacyBlockCrystalGrowthRules.metadata(s)));
      eq(size+1,LegacyBlockCrystalGrowthRules.crystalDrops(s));
      eq(3+gen,LegacyBlockCrystalGrowthRules.tickDenominator(s));
      yes(LegacyBlockCrystalGrowthRules.attemptTick(s,0));
      no(LegacyBlockCrystalGrowthRules.attemptTick(s,1));
    }
    yes(LegacyBlockCrystalGrowthRules.starved(10f));
    yes(LegacyBlockCrystalGrowthRules.starved(9.99f));
    no(LegacyBlockCrystalGrowthRules.starved(10.01f));
    eq(null,LegacyBlockCrystalGrowthRules.shrink(original));
    no(LegacyBlockCrystalGrowthRules.removeStarved(original,false));
    yes(LegacyBlockCrystalGrowthRules.removeStarved(original,true));
    var third=new LegacyBlockCrystalGrowthRules.State(3,4);
    eq(new LegacyBlockCrystalGrowthRules.State(2,4),LegacyBlockCrystalGrowthRules.shrink(third));
    no(LegacyBlockCrystalGrowthRules.canGrow(third,0,100,0));
    no(LegacyBlockCrystalGrowthRules.canGrow(original,0,10,0));
    yes(LegacyBlockCrystalGrowthRules.canGrow(original,0,11,0));
    no(LegacyBlockCrystalGrowthRules.canGrow(new LegacyBlockCrystalGrowthRules.State(2,4),0,20,0));
    yes(LegacyBlockCrystalGrowthRules.canGrow(new LegacyBlockCrystalGrowthRules.State(2,4),2,20,0));
    eq(new LegacyBlockCrystalGrowthRules.State(2,1),
       LegacyBlockCrystalGrowthRules.grow(new LegacyBlockCrystalGrowthRules.State(1,1)));
    yes(LegacyBlockCrystalGrowthRules.maySpread(original,50,0));
    no(LegacyBlockCrystalGrowthRules.maySpread(third,50,0));
    eq(new LegacyBlockCrystalGrowthRules.State(0,1),
       LegacyBlockCrystalGrowthRules.child(original,true));
    eq(new LegacyBlockCrystalGrowthRules.State(0,2),
       LegacyBlockCrystalGrowthRules.child(original,false));
    yes(LegacyBlockCrystalGrowthRules.spreadOffset(-1,1,1));
    no(LegacyBlockCrystalGrowthRules.spreadOffset(0,0,0));
    no(LegacyBlockCrystalGrowthRules.spreadOffset(0,2,0));
    eq("PLAINS", LegacyModernBiomeCompatibility.originalConstant("minecraft:plains"));
    eq("ICE_PLAINS", LegacyModernBiomeCompatibility.originalConstant("minecraft:snowy_plains"));
    eq("HELL", LegacyModernBiomeCompatibility.originalConstant("minecraft:nether_wastes"));
    eq(null, LegacyModernBiomeCompatibility.originalConstant("minecraft:cherry_grove"));
    eq(null, LegacyModernBiomeCompatibility.originalConstant("modded:forest"));
    eq(0.5F, LegacyModernBiomeCompatibility.auraModifier("modded:forest"));
    eq(LegacyVanillaBiomeTypes.auraModifier("PLAINS"),
        LegacyModernBiomeCompatibility.auraModifier("minecraft:plains"));
    eq(LegacyVanillaBiomeTypes.auraModifier("OCEAN"),
        LegacyModernBiomeCompatibility.auraModifier("minecraft:ocean"));
    eq(150, AuraGenerationLogic.generatedBase(.3F,.3F,.3F,.3F,.3F,0.0D));
    System.out.println("Crystal growth BETA26 parity: OK ("+total+" assertions)");
  }
  private static void eq(Object a,Object b){total++;if(!Objects.equals(a,b))throw new AssertionError(a+" != "+b);}
  private static void yes(boolean b){total++;if(!b)throw new AssertionError("expected true");}
  private static void no(boolean b){total++;if(b)throw new AssertionError("expected false");}
}
