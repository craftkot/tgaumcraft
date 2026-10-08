package dev.ehtid.thaumcraft.scanning;

import dev.ehtid.thaumcraft.port.core.LegacyThaumometerTargeting;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Minecraft/NeoForge projection of BETA26 ItemThaumometer + EntityUtils#getPointedEntity targeting. */
public final class ThaumometerTargeting {
    public static final double ENTITY_MIN_DISTANCE = 1.0;
    public static final double SCAN_ENTITY_RANGE = 9.0;
    public static final double SCAN_ENTITY_QUERY_GROW = 0.0;
    public static final double HIGHLIGHT_ENTITY_RANGE = 16.0;
    public static final double HIGHLIGHT_ENTITY_QUERY_GROW = 5.0;
    public static final double BLOCK_RANGE = 5.0; // vanilla 1.12 Item.rayTrace fixed distance
    public static final double HIGHLIGHT_BLOCK_RANGE = 16.0;

    private ThaumometerTargeting() {}

    /** Returns Entity, BlockPos, or null exactly in the priority expected by ScanningManager. */
    public static Object findScanTarget(Level level, Player player) {
        Entity entity = findPointedEntity(level, player);
        if (entity != null) return entity;
        BlockHitResult block = rayTraceBlock(level, player);
        return block.getType() == HitResult.Type.MISS ? null : block.getBlockPos();
    }

    public static Entity findPointedEntity(Level level, Player player) {
        return findPointedEntity(level, player, SCAN_ENTITY_RANGE, SCAN_ENTITY_QUERY_GROW);
    }

    /** BETA26 idle Thaumometer highlight used the much wider 1..16 range with a 5-block query grow. */
    public static Entity findHighlightEntity(Level level, Player player) {
        return findPointedEntity(level, player, HIGHLIGHT_ENTITY_RANGE, HIGHLIGHT_ENTITY_QUERY_GROW);
    }

    private static Entity findPointedEntity(Level level, Player player, double maxDistance, double queryGrow) {
        Vec3 startMc = player.getEyePosition(1.0F);
        Vec3 lookMc = player.getViewVector(1.0F);
        LegacyThaumometerTargeting.Vec start = vec(startMc);
        LegacyThaumometerTargeting.Vec look = vec(lookMc);

        LegacyThaumometerTargeting.Box query = LegacyThaumometerTargeting.queryBox(
                start, box(player.getBoundingBox()), look, maxDistance, queryGrow);
        AABB queryMc = new AABB(query.minX(), query.minY(), query.minZ(), query.maxX(), query.maxY(), query.maxZ());

        // Legacy code asks the world for all entities except the source and applies canBeCollidedWith itself.
        List<Entity> nearby = level.getEntities(player, queryMc, e -> true);
        List<LegacyThaumometerTargeting.Candidate<Entity>> candidates = new ArrayList<>(nearby.size());
        for (Entity candidate : nearby) {
            candidates.add(new LegacyThaumometerTargeting.Candidate<>(
                    candidate,
                    vec(candidate.position()),
                    vec(candidate.getEyePosition(1.0F)),
                    box(candidate.getBoundingBox()),
                    candidate.canBeCollidedWith(),
                    candidate.getPickRadius()));
        }

        // ItemThaumometer passes true to EntityUtils#getPointedEntity, allowing otherwise non-collidable targets.
        return LegacyThaumometerTargeting.selectEntity(start, look,
                ENTITY_MIN_DISTANCE, maxDistance, true, candidates,
                (candidate, from, to) -> hasLegacyLineOfSight(level, player, from, to));
    }

    /**
     * Exact math from ItemThaumometer#getRayTraceResultFromPlayerWild: current
     * pitch/yaw plus two independent [0,24] random terms, then a 16-block ray.
     */
    public static BlockHitResult rayTraceBlockWild(Level level, Player player) {
        float pitch = player.getXRot()
                + level.random.nextInt(25) - level.random.nextInt(25);
        float yaw = player.getYRot()
                + level.random.nextInt(25) - level.random.nextInt(25);

        Vec3 start = new Vec3(player.getX(), player.getY() + player.getEyeHeight(), player.getZ());
        float yawCos = Mth.cos(-yaw * Mth.DEG_TO_RAD - Mth.PI);
        float yawSin = Mth.sin(-yaw * Mth.DEG_TO_RAD - Mth.PI);
        float pitchCos = -Mth.cos(-pitch * Mth.DEG_TO_RAD);
        float pitchSin = Mth.sin(-pitch * Mth.DEG_TO_RAD);
        Vec3 end = start.add(
                yawSin * pitchCos * HIGHLIGHT_BLOCK_RANGE,
                pitchSin * HIGHLIGHT_BLOCK_RANGE,
                yawCos * pitchCos * HIGHLIGHT_BLOCK_RANGE);
        return level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));
    }

    private static boolean hasLegacyLineOfSight(Level level, Player player,
                                                 LegacyThaumometerTargeting.Vec from,
                                                 LegacyThaumometerTargeting.Vec to) {
        BlockHitResult obstruction = level.clip(new ClipContext(
                vec(from), vec(to), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return obstruction.getType() == HitResult.Type.MISS;
    }

    /**
     * Closest 1.21.1 projection of old Item.rayTrace(world, player, true): fixed 5-block ray,
     * block collision shapes, and fluids included.
     */
    public static BlockHitResult rayTraceBlock(Level level, Player player) {
        Vec3 start = player.getEyePosition(1.0F);
        Vec3 end = start.add(player.getViewVector(1.0F).scale(BLOCK_RANGE));
        return level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));
    }

    private static LegacyThaumometerTargeting.Vec vec(Vec3 v) {
        return new LegacyThaumometerTargeting.Vec(v.x, v.y, v.z);
    }

    private static Vec3 vec(LegacyThaumometerTargeting.Vec v) {
        return new Vec3(v.x(), v.y(), v.z());
    }

    private static LegacyThaumometerTargeting.Box box(AABB b) {
        return new LegacyThaumometerTargeting.Box(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ);
    }
}
