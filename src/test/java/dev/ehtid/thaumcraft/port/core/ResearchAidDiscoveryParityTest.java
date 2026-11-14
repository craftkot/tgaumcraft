package dev.ehtid.thaumcraft.port.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Research-lane regression: BETA26 nearby-aid detection and its real NeoForge
 * block mapping. The source checks are wiring assertions, not a gameplay test.
 * Run separately with tools/run_research_aid_parity.sh.
 */
public final class ResearchAidDiscoveryParityTest {
    private static int checks;
    private static final String API = "thaumcraft.api.research.theorycraft.";
    private static final String COMMON = "thaumcraft.common.lib.research.theorycraft.";
    private static final String ALCHEMY = COMMON + "AidBasicAlchemy";
    private static final String INFUSION = COMMON + "AidBasicInfusion";

    private static void check(boolean ok, String reason) {
        checks++;
        if (!ok) throw new AssertionError(reason);
    }

    public static void main(String[] args) throws Exception {
        check(LegacyTheorycraftManifest.AID_REGISTRATION_ORDER.size() == 14,
                "expected 14 BETA26 helpers");
        check(LegacyTheorycraftAidScanner.kind(ALCHEMY)
                        == LegacyTheorycraftAidScanner.Kind.BLOCK,
                "crucible helper must be block identity");
        check(LegacyTheorycraftAidScanner.kind(INFUSION)
                        == LegacyTheorycraftAidScanner.Kind.BLOCK,
                "matrix helper must be block identity");
        check(LegacyTheorycraftAidCards.forAid(ALCHEMY).equals(List.of(
                        COMMON + "CardConcentrate",
                        COMMON + "CardReactions",
                        COMMON + "CardSynthesis")),
                "crucible aid-only cards");
        check(LegacyTheorycraftAidCards.forAid(INFUSION).equals(List.of(
                        COMMON + "CardMeasure",
                        COMMON + "CardChannel",
                        COMMON + "CardInfuse")),
                "infusion matrix aid-only cards");

        // Source geometry: x/z in [-4,4], y in [-1,1]. Two
        // duplicate bookshelves must still yield exactly one aid.
        Set<String> found = LegacyTheorycraftAidScanner.scan(
                new LegacyTheorycraftAidScanner.Probe() {
                    @Override
                    public boolean matches(String aid, LegacyTheorycraftAidScanner.Kind kind,
                                           int x, int y, int z) {
                        if (aid.equals(ALCHEMY))
                            return kind == LegacyTheorycraftAidScanner.Kind.BLOCK
                                    && x == 4 && y == 1 && z == -4;
                        if (aid.equals(INFUSION))
                            return kind == LegacyTheorycraftAidScanner.Kind.BLOCK
                                    && x == -4 && y == -1 && z == 4;
                        if (aid.equals(API + "AidBookshelf"))
                            return kind == LegacyTheorycraftAidScanner.Kind.BLOCK
                                    && y == 0 && z == 0 && (x == 0 || x == 1);
                        if (aid.equals(COMMON + "AidBeacon"))
                            return kind == LegacyTheorycraftAidScanner.Kind.PICKED_STACK
                                    && x == 0 && y == 0 && z == -1;
                        return false;
                    }
                    @Override
                    public boolean hasEntity(String aid, double radius) {
                        check(radius == 5.0, "original entity scan radius");
                        return false;
                    }
                });
        check(found.size() == 4, "one result per aid class despite duplicate blocks");
        check(found.containsAll(Set.of(ALCHEMY, INFUSION,
                        API + "AidBookshelf", COMMON + "AidBeacon")),
                "all four genuine block/picked-stack aids");

        // Test the actual research-only NeoForge adapter references without
        // requiring Minecraft libraries in this low-cost parity suite.
        String bridge = Files.readString(Path.of(
                "src/neoforge/java/dev/ehtid/thaumcraft/scanning/NeoForgeTheorycraftAidBridge.java"));
        check(bridge.contains("case COMMON + \"AidBasicAlchemy\" -> ThaumcraftBlocks.CRUCIBLE.get();"),
                "crucible block must be connected to its original aid");
        check(bridge.contains("case COMMON + \"AidBasicInfusion\" -> ThaumcraftBlocks.INFUSION_MATRIX.get();"),
                "matrix block must be connected to its original aid");
        check(bridge.contains("if (!world.hasChunkAt(at)) return false;"),
                "do not load distant chunks to find aids");
        check(bridge.contains("Collections.unmodifiableSet(new LinkedHashSet<>("),
                "preserve original scanner iteration when freezing discovered aids");
        check(!bridge.contains("Set.copyOf(LegacyTheorycraftAidScanner.scan("),
                "unordered Set.copyOf must not scramble discovered aid iteration");
        String blocks = Files.readString(Path.of(
                "src/neoforge/java/dev/ehtid/thaumcraft/registry/ThaumcraftBlocks.java"));
        check(blocks.contains("DeferredBlock<CrucibleBlock> CRUCIBLE"),
                "crucible must actually be registered");
        check(blocks.contains("DeferredBlock<InfusionMatrixBlock> INFUSION_MATRIX"),
                "infusion matrix must actually be registered");

        System.out.println("Research aid discovery parity: OK (" + checks + " assertions)");
    }
}
