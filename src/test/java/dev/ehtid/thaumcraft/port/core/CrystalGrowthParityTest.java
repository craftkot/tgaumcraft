package dev.ehtid.thaumcraft.port.core;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
    // BETA26 checks the block Material: a replaceable waterlogged solid is not liquid.
    yes(LegacyBlockCrystalGrowthRules.canSpreadInto(false,true)); // non-liquid block, fluid state may be non-empty
    no(LegacyBlockCrystalGrowthRules.canSpreadInto(true,true)); // water/lava liquid block
    no(LegacyBlockCrystalGrowthRules.canSpreadInto(false,false));
    no(LegacyBlockCrystalGrowthRules.canSpreadInto(true,false));
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
    eq(50, AuraGenerationLogic.SOURCE_BIOME_SAMPLE_Y); // pinned BETA26 AuraHandler.generateAura
    verifyCrystalWorldGenLogic();
    verifyNeoForgeSpreadLiquidAdapter();
    verifyCrystalWorldGenAdapter();
    System.out.println("Crystal growth BETA26 parity: OK ("+total+" assertions)");
  }

  private static void verifyCrystalWorldGenLogic() {
    eq(8, LegacyCrystalWorldGenLogic.attemptCount(100));
    eq(64, LegacyCrystalWorldGenLogic.maximumCrystalAmount(100));
    eq(12, LegacyCrystalWorldGenLogic.attemptCount(150));
    eq(0, LegacyCrystalWorldGenLogic.maximumCrystalAmount(0));
    eq(95, LegacyCrystalWorldGenLogic.yRange(0, 100));
    eq(123, LegacyCrystalWorldGenLogic.yRange(-64, 64));
    eq(5, LegacyCrystalWorldGenLogic.yRange(-64, -64));
    eq(1, LegacyCrystalWorldGenLogic.clusterSize(0));
    eq(2, LegacyCrystalWorldGenLogic.clusterSize(1));
    eq(3, LegacyCrystalWorldGenLogic.clusterSize(2));
    yes(LegacyCrystalWorldGenLogic.canPlace(false, true, true));
    no(LegacyCrystalWorldGenLogic.canPlace(true, true, true));
    no(LegacyCrystalWorldGenLogic.canPlace(false, false, true));
    no(LegacyCrystalWorldGenLogic.canPlace(false, true, false));
    eq(Integer.valueOf(1), LegacyCrystalWorldGenLogic.aspectMetadata("MESA"));
    eq(Integer.valueOf(5), LegacyCrystalWorldGenLogic.aspectMetadata("DRY"));
    eq(null, LegacyCrystalWorldGenLogic.aspectMetadata("RARE"));

    // BETA26 order: random metadata, one-in-three override, random biome type.
    eq(0, scriptedShardMetadata("minecraft:plains",
        new int[]{6, 3, 1}, new int[]{5, 0, 0}));
    // MESA's final type list is [MESA, SANDY, DRY]; DRY was overwritten to ENTROPY.
    eq(5, scriptedShardMetadata("minecraft:badlands",
        new int[]{6, 3, 3}, new int[]{0, 0, 2}));
    // A newly added biome has no BETA26 type list: failed lookup falls back to a fresh random meta.
    eq(4, scriptedShardMetadata("modded:forest",
        new int[]{6, 3, 6}, new int[]{2, 0, 4}));
    // BETA26's RARE type is not registered in biomeInfo and therefore also falls back.
    eq(4, scriptedShardMetadata("minecraft:sunflower_plains",
        new int[]{6, 3, 2, 6}, new int[]{2, 0, 1, 4}));
    // If the one-in-three override does not fire, the first metadata is retained.
    eq(5, scriptedShardMetadata("minecraft:plains",
        new int[]{6, 3}, new int[]{5, 1}));
  }

  private static int scriptedShardMetadata(String biomeId, int[] bounds, int[] values) {
    int[] index = {0};
    int metadata = LegacyCrystalWorldGenLogic.chooseShardMetadata(biomeId, bound -> {
      if (index[0] >= bounds.length) throw new AssertionError("Unexpected nextInt(" + bound + ")");
      eq(bounds[index[0]], bound);
      return values[index[0]++];
    });
    eq(values.length, index[0]);
    return metadata;
  }

  private static void verifyCrystalWorldGenAdapter() {
    try {
      String source = Files.readString(Path.of(
          "src/neoforge/java/dev/ehtid/thaumcraft/world/CrystalClusterWorldGen.java"));
      yes(source.contains("event.isNewChunk()"));
      yes(source.contains("getChunkSource().getChunkNow"));
      yes(source.contains("level.hasChunkAt"));
      no(source.contains("level.getChunk("));
      yes(source.contains("RandomSource.create(chunkSeed"));
    } catch (IOException e) {
      throw new AssertionError("Unable to inspect NeoForge crystal worldgen adapter", e);
    }
  }

  private static void verifyNeoForgeSpreadLiquidAdapter() {
    try {
      String source = Files.readString(Path.of(
          "src/neoforge/java/dev/ehtid/thaumcraft/block/CrystalClusterBlock.java"));
      yes(source.contains("destination.liquid()"));
      no(source.contains("destination.getFluidState().isEmpty()"));
    } catch (IOException e) {
      throw new AssertionError("Unable to inspect NeoForge crystal spread adapter", e);
    }
  }
  private static void eq(Object a,Object b){total++;if(!Objects.equals(a,b))throw new AssertionError(a+" != "+b);}
  private static void yes(boolean b){total++;if(!b)throw new AssertionError("expected true");}
  private static void no(boolean b){total++;if(b)throw new AssertionError("expected false");}
}
