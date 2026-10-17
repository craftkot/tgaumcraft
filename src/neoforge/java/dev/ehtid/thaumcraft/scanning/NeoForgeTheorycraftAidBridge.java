package dev.ehtid.thaumcraft.scanning;

import dev.ehtid.thaumcraft.port.core.LegacyTheorycraftAidScanner;
import dev.ehtid.thaumcraft.registry.ThaumcraftBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Reads real loaded Minecraft blocks for original BETA26 theorycraft aids.
 * Only registered original block identities are recognized (including the
 * real crucible and infusion matrix). Unported aids such as the brain jar
 * are NOT guessed or substituted.
 *
 * Vanilla picked-stack aids (enchanting table, beacon) have canonical vanilla
 * pick items and no variant metadata; their source selectors are ItemStacks.
 */
public final class NeoForgeTheorycraftAidBridge {
    private NeoForgeTheorycraftAidBridge() {}

    private static final String API = "thaumcraft.api.research.theorycraft.";
    private static final String COMMON = "thaumcraft.common.lib.research.theorycraft.";

    public static Set<String> discover(Level world, BlockPos origin) {
        Objects.requireNonNull(world);
        Objects.requireNonNull(origin);
        return Collections.unmodifiableSet(new LinkedHashSet<>(
                LegacyTheorycraftAidScanner.scan(new LegacyTheorycraftAidScanner.Probe() {
            @Override
            public boolean matches(String aid, LegacyTheorycraftAidScanner.Kind kind,
                                   int dx, int dy, int dz) {
                // Avoid forcing chunks to load when checking the nearby aids.
                BlockPos at = origin.offset(dx, dy, dz);
                if (!world.hasChunkAt(at)) return false;
                BlockState state = world.getBlockState(at);
                if (kind == LegacyTheorycraftAidScanner.Kind.BLOCK) {
                    Block original = switch (aid) {
                        case API + "AidBookshelf" -> Blocks.BOOKSHELF;
                        case COMMON + "AidPortal$AidPortalEnd" -> Blocks.END_PORTAL;
                        case COMMON + "AidPortal$AidPortalNether" -> Blocks.NETHER_PORTAL;
                        case COMMON + "AidBasicAlchemy" -> ThaumcraftBlocks.CRUCIBLE.get();
                        case COMMON + "AidBasicArtifice" -> ThaumcraftBlocks.ARCANE_WORKBENCH.get();
                        case COMMON + "AidBasicInfusion" -> ThaumcraftBlocks.INFUSION_MATRIX.get();
                        default -> null; // Unported blocks have no identity in this version.
                    };
                    return original != null && state.is(original);
                }
                if (kind == LegacyTheorycraftAidScanner.Kind.PICKED_STACK) {
                    // BETA26 only vanilla enchantment/beacon aids are supported
                    // here. For modded blocks use real getCloneItemStack
                    // metadata/components, not a different block's item.
                    return switch (aid) {
                        case COMMON + "AidEnchantmentTable" -> state.is(Blocks.ENCHANTING_TABLE);
                        case COMMON + "AidBeacon" -> state.is(Blocks.BEACON);
                        default -> false;
                    };
                }
                return false;
            }

            @Override
            public boolean hasEntity(String aid, double radius) {
                // Crimson portal entity is not ported; never award that aid
                // for arbitrary portal particles/entities.
                return false;
            }
        })));
    }
}
