package dev.ehtid.thaumcraft.gametest;

import dev.ehtid.thaumcraft.port.core.LegacyEssentiaMirrorLogic;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;

/**
 * Native GameTest registration canary for the isolated Alchemy candidate.
 * This tests only pure mirror invariants while running in a real GameTest
 * server. It is NOT an essentia transport, persistence, or anti-dupe test.
 */
@GameTestHolder("thaumcraft")
public final class AlchemyRegistrationGameTests {
    private AlchemyRegistrationGameTests() {}

    @GameTest(template = "empty", templateNamespace = "thaumcraft")
    public static void mirrorCoreCanary(GameTestHelper helper) {
        var left = new LegacyEssentiaMirrorLogic.Endpoint("minecraft:overworld", 0, 64, 0);
        var right = new LegacyEssentiaMirrorLogic.Endpoint("minecraft:overworld", 10, 64, 0);
        boolean linked = LegacyEssentiaMirrorLogic.reciprocal(
                left, new LegacyEssentiaMirrorLogic.Link(right, true),
                right, new LegacyEssentiaMirrorLogic.Link(left, true), true);
        if (!linked || LegacyEssentiaMirrorLogic.canTransfer(2, true, true, true)
                || !LegacyEssentiaMirrorLogic.conservedOneUnit(2, 1, 0, 1)
                || LegacyEssentiaMirrorLogic.conservedOneUnit(2, 2, 0, 1)) {
            throw new AssertionError("Alchemy mirror core invariants failed");
        }
        helper.succeed();
    }
}
