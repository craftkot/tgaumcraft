package dev.ehtid.thaumcraft.crafting;

import dev.ehtid.thaumcraft.blockentity.EssentiaJarBlockEntity;
import dev.ehtid.thaumcraft.blockentity.EssentiaMirrorBlockEntity;
import dev.ehtid.thaumcraft.port.core.LegacyInfusionSourceVolume;
import dev.ehtid.thaumcraft.port.core.LegacyInfusionJarDebitEvidence;
import dev.ehtid.thaumcraft.util.ThaumcraftSmokeAudit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;

/**
 * Real-world jar and mirror subset of the original EssentiaHandler.
 *
 * Each successful call debits EXACTLY one unit from a loaded, unblocked,
 * source-era jar. It cannot invent essence, craft an item, trigger a ritual,
 * or consume items from pedestals.
 *
 * Original EssentiaHandler finds/sorts IAspectSource neighbors once, uses a
 * cached list until an entire pass fails, then waits 10 seconds to rescan.
 * A mirror delegates to its loaded partner's directional scan, with mirror
 * relaying forbidden. Actual jar balance movement remains the debit proof.
 * Pipes are not IAspectSource; original source FX packets are not yet ported.
 */
public final class InfusionJarSourceBridge {
    private static final long RETRY_MILLIS = 10_000L; // source exact wall-clock backoff
    private ServerLevel cachedWorld;
    private BlockPos cachedOrigin;
    private int cachedRange = -1;
    private Direction cachedFacing;
    private boolean cachedIgnoreMirrors;
    private List<BlockPos> knownSources;
    private long nextScanAtMillis;

    /**
     * The caller MUST be the server-side matrix tick / verified recipe
     * cycle. `fxDelay` in the BETA26 drainer controls packet visuals; no
     * visual packet is emitted until original FX networking is ported.
     */
    public boolean drainOne(ServerLevel level, BlockPos origin, String aspect,
                            int range, int fxDelay) {
        return drainOne(level, origin, aspect, range, fxDelay, null, false);
    }

    /** Original mirror remote request: its partner's face, range8, ignoreMirror=true. */
    public boolean drainOne(ServerLevel level, BlockPos origin, String aspect,
                            int range, int fxDelay, Direction facing, boolean ignoreMirrors) {
        if (level == null || origin == null || aspect == null || aspect.isBlank()
                || range <= 0 || range > 32 || fxDelay < 0) return false;
        if (cachedWorld != level || !origin.equals(cachedOrigin) || cachedRange != range
                || cachedFacing != facing || cachedIgnoreMirrors != ignoreMirrors) {
            cachedWorld = level;
            cachedOrigin = origin.immutable();
            cachedRange = range;
            cachedFacing = facing;
            cachedIgnoreMirrors = ignoreMirrors;
            knownSources = null;
            nextScanAtMillis = 0L;
        }

        if (knownSources == null) {
            if (System.currentTimeMillis() < nextScanAtMillis) return false;
            List<BlockPos> found = new ArrayList<>();
            var scanFacing = facing == null ? null : LegacyInfusionSourceVolume.Facing.valueOf(facing.name());
            for (var offset : LegacyInfusionSourceVolume.offsets(range, scanFacing)) {
                BlockPos candidate = origin.offset(offset.x(), offset.y(), offset.z());
                // Do not synchronously load new chunks from a tile's tick.
                if (!level.hasChunkAt(candidate)) continue;
                var tile = level.getBlockEntity(candidate);
                if (tile instanceof EssentiaJarBlockEntity
                        || (!ignoreMirrors && tile instanceof EssentiaMirrorBlockEntity))
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
            var tile = level.getBlockEntity(pos);
            if (!ignoreMirrors && tile instanceof EssentiaMirrorBlockEntity mirror) {
                if (mirror.takeForInfusion(aspect, 1)) return true;
                continue;
            }
            if (!(tile instanceof EssentiaJarBlockEntity jar) || jar.blocked()) continue;
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

    /** Runtime cache only; reconstructed world/link state must not retain a stale scan delay. */
    public void reset() {
        cachedWorld = null;
        cachedOrigin = null;
        knownSources = null;
        nextScanAtMillis = 0L;
    }
}
