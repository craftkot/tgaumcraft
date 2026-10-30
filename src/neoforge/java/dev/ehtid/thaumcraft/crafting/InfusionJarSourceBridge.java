package dev.ehtid.thaumcraft.crafting;

import dev.ehtid.thaumcraft.blockentity.EssentiaJarBlockEntity;
import dev.ehtid.thaumcraft.port.core.LegacyEssentiaSourceOrder;
import dev.ehtid.thaumcraft.port.core.LegacyInfusionJarDebitEvidence;
import dev.ehtid.thaumcraft.util.ThaumcraftSmokeAudit;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;

/**
 * Real-world jar subset of the original EssentiaHandler.
 *
 * Each successful call debits EXACTLY one unit from a loaded, unblocked,
 * source-era jar. It cannot invent essence, craft an item, trigger a ritual,
 * or consume items from pedestals.
 *
 * Original EssentiaHandler finds/sorts IAspectSource neighbors once, uses a
 * cached list until an entire pass fails, then waits 10 seconds to rescan.
 * Mirror sources, pipes, source FX packets and caster rituals are NOT
 * implemented here. This adapter intentionally only supports actual jars.
 */
public final class InfusionJarSourceBridge {
    private static final long RETRY_MILLIS = 10_000L; // source exact wall-clock backoff
    private ServerLevel cachedWorld;
    private BlockPos cachedOrigin;
    private int cachedRange = -1;
    private List<BlockPos> knownSources;
    private long nextScanAtMillis;

    /**
     * The caller MUST be the server-side matrix tick / verified recipe
     * cycle. `fxDelay` in the BETA26 drainer controls packet visuals; no
     * visual packet is emitted until original FX networking is ported.
     */
    public boolean drainOne(ServerLevel level, BlockPos origin, String aspect,
                            int range, int fxDelay) {
        if (level == null || origin == null || aspect == null || aspect.isBlank()
                || range <= 0 || range > 32 || fxDelay < 0) return false;
        if (cachedWorld != level || !origin.equals(cachedOrigin) || cachedRange != range) {
            cachedWorld = level;
            cachedOrigin = origin.immutable();
            cachedRange = range;
            knownSources = null;
            nextScanAtMillis = 0L;
        }

        if (knownSources == null) {
            if (System.currentTimeMillis() < nextScanAtMillis) return false;
            List<BlockPos> found = new ArrayList<>();
            for (var offset : LegacyEssentiaSourceOrder.nullFacing(range)) {
                BlockPos candidate = origin.offset(offset.x(), offset.y(), offset.z());
                // Do not synchronously load new chunks from a tile's tick.
                if (!level.hasChunkAt(candidate)) continue;
                if (level.getBlockEntity(candidate) instanceof EssentiaJarBlockEntity)
                    found.add(candidate.immutable());
            }
            if (found.isEmpty()) {
                nextScanAtMillis = System.currentTimeMillis() + RETRY_MILLIS;
                return false;
            }
            knownSources = List.copyOf(found);
        }

        for (BlockPos pos : knownSources) {
            if (!level.hasChunkAt(pos)) continue;
            if (!(level.getBlockEntity(pos) instanceof EssentiaJarBlockEntity jar)
                    || jar.blocked()) continue;
            // This comparison is against the LIVE jar BlockEntity, not the
            // matrix's own requested-cost ledger or a fabricated snapshot.
            // In particular, the final unit must clear the jar's aspect.
            var before = jar.snapshot();
            if (!jar.takeForInfusion(aspect, 1)) continue;
            var after = jar.snapshot();
            if (!LegacyInfusionJarDebitEvidence.exactlyOne(
                    aspect, before, after, true)) {
                // A reported debit without exact physical balance movement
                // is not permission to reduce ledger cost or mint an output.
                ThaumcraftSmokeAudit.mark("INFUSION_CRYSTAL_JAR_DEBIT_INCONSISTENT");
                return false;
            }
            ThaumcraftSmokeAudit.mark("INFUSION_CRYSTAL_JAR_ONE_UNIT_DEBIT_PROVEN");
            return true;
        }

        // Same 10-second backoff as original EssentiaHandler after no source
        // could actually satisfy the requested aspect.
        knownSources = null;
        nextScanAtMillis = System.currentTimeMillis() + RETRY_MILLIS;
        return false;
    }
}
