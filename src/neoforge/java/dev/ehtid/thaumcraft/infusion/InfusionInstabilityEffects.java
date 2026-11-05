package dev.ehtid.thaumcraft.infusion;

import dev.ehtid.thaumcraft.blockentity.PedestalBlockEntity;
import dev.ehtid.thaumcraft.crafting.InfusionStabilizerAdapter;
import dev.ehtid.thaumcraft.network.ThaumcraftNetworking;
import dev.ehtid.thaumcraft.player.ThaumcraftPlayerAttachments;
import dev.ehtid.thaumcraft.port.core.BuiltInResearchCatalog;
import dev.ehtid.thaumcraft.port.core.LegacyInfusionInstability;
import dev.ehtid.thaumcraft.port.core.ResearchProgressionService;
import dev.ehtid.thaumcraft.port.core.WarpType;
import dev.ehtid.thaumcraft.world.ThaumcraftAuraSavedData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.List;
import java.util.Objects;

/** Actual server-world consequences of the BETA26 24-way instability selector. */
public final class InfusionInstabilityEffects {
    private InfusionInstabilityEffects() {}

    public record Result(int affectedEntities, BlockPos pedestal, boolean mitigated,
                         int droppedItems, float pollutionAdded, int researchGranted) {}

    /** Production bridge; no seed substitution or ritual-local deterministic RNG. */
    public static LegacyInfusionInstability.RandomSource bridge(ServerLevel level) {
        var source = level.getRandom();
        return new LegacyInfusionInstability.RandomSource() {
            @Override public int nextInt(int bound) { return source.nextInt(bound); }
            @Override public float nextFloat() { return source.nextFloat(); }
            @Override public boolean nextBoolean() { return source.nextBoolean(); }
        };
    }

    /**
     * Called between beginCycle and recoverAfterEvent with the same world RNG.
     * Research has no RNG; it is included even when the selected hazard has no
     * available target. The matrix performs recovery immediately after return.
     */
    public static Result perform(ServerLevel level, BlockPos matrix,
                                  LegacyInfusionInstability.Event event,
                                  List<BlockPos> allPedestals,
                                  LegacyInfusionInstability.RandomSource random) {
        Objects.requireNonNull(level);
        Objects.requireNonNull(matrix);
        Objects.requireNonNull(event);
        Objects.requireNonNull(allPedestals);
        Objects.requireNonNull(random);
        if (event == LegacyInfusionInstability.Event.NONE)
            return new Result(0, null, false, 0, 0, 0);
        Result result;
        if (event.ejectionMode() >= 0) {
            result = eject(level, matrix, event.ejectionMode(), allPedestals, random);
        } else {
            result = switch (event) {
                case WARP -> warp(level, matrix, random);
                case ZAP_ONE, ZAP_ALL -> zap(level, matrix, event.allTargets(), random);
                case HARM_ONE, HARM_ALL -> harm(level, matrix, event.allTargets(), random);
                case MATRIX_EXPLOSION -> {
                    explode(level, matrix, LegacyInfusionInstability.rollMatrixExplosionStrength(random));
                    yield new Result(0, null, false, 0, 0, 0);
                }
                default -> throw new IllegalArgumentException("Unmapped instability event " + event);
            };
        }
        int awarded = grantInstabilityResearch(level, matrix);
        return new Result(result.affectedEntities(), result.pedestal(), result.mitigated(),
                result.droppedItems(), result.pollutionAdded(), awarded);
    }

    private static Result eject(ServerLevel level, BlockPos matrix, int mode,
                                 List<BlockPos> allPedestals,
                                 LegacyInfusionInstability.RandomSource random) {
        BlockPos target = LegacyInfusionInstability.findEjectionPedestal(allPedestals,
                pos -> level.getBlockEntity(pos) instanceof PedestalBlockEntity p && p.isOccupied(), random);
        if (target == null) return new Result(0, null, false, 0, 0, 0);
        var pedestal = (PedestalBlockEntity) level.getBlockEntity(target);
        var mitigator = InfusionStabilizerAdapter.findMitigator(level, target);
        if (mitigator != null && mitigator.mitigate(LegacyInfusionInstability.rollMitigationCost(random))) {
            level.blockEvent(target, level.getBlockState(target).getBlock(),
                    LegacyInfusionInstability.MITIGATED_BLOCK_EVENT, 0);
            arc(level, matrix, Vec3.atCenterOf(target.above()), random);
            arc(level, target.above(), Vec3.atCenterOf(mitigator.getBlockPos()), random);
            return new Result(0, target, true, 0, 0, 0);
        }
        var effect = LegacyInfusionInstability.ejectionEffect(mode);
        // Clear the real slot AND external-write receipts before making an item
        // entity. Never copy the saved recipe snapshot back into the world.
        var removed = pedestal.extractToPlayer();
        int dropped = 0;
        if (effect.dropItem() && !removed.isEmpty()) {
            dropped = removed.getCount();
            // Source InventoryUtils.dropItems delegates to InventoryHelper;
            // modern Containers uses its own drop RNG, not the world RNG.
            Containers.dropItemStack(level, target.getX(), target.getY(), target.getZ(), removed);
        }
        float pollution = 0;
        if (effect.placeFluxGoo()) {
            level.setBlockAndUpdate(target.above(), InfusionHazardContent.FLUX_GOO.get().defaultBlockState());
            level.playSound(null, target, SoundEvents.BOTTLE_FILL,
                    SoundSource.BLOCKS, 0.3F, 1.0F);
        } else if (effect.polluteAura()) {
            int amount = LegacyInfusionInstability.rollFluxPollution(random);
            var aura = ThaumcraftAuraSavedData.get(level);
            float before = aura.getFlux(target);
            aura.polluteFlux(target, amount);
            pollution = aura.getFlux(target) - before;
            // AuraHelper.polluteAura(effect=true) produces visible flux feedback.
            level.sendParticles(ParticleTypes.WITCH, target.getX() + 0.5, target.getY() + 1,
                    target.getZ() + 0.5, 5, 0.2, 0.2, 0.2, 0);
        } else if (effect.explosionStrength() > 0) {
            explode(level, target, effect.explosionStrength());
        }
        level.blockEvent(target, level.getBlockState(target).getBlock(),
                LegacyInfusionInstability.EJECTED_BLOCK_EVENT, 0);
        arc(level, matrix, Vec3.atCenterOf(target.above()), random);
        return new Result(0, target, false, dropped, pollution, 0);
    }

    private static Result zap(ServerLevel level, BlockPos matrix, boolean all,
                              LegacyInfusionInstability.RandomSource random) {
        int affected = 0;
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, targetBox(matrix))) {
            arc(level, matrix, entity.position().add(0, entity.getBbHeight() / 2.0, 0), random);
            entity.hurt(level.damageSources().magic(), LegacyInfusionInstability.rollZapDamage(random));
            affected++;
            if (!all) break;
        }
        return new Result(affected, null, false, 0, 0, 0);
    }

    private static Result harm(ServerLevel level, BlockPos matrix, boolean all,
                               LegacyInfusionInstability.RandomSource random) {
        int affected = 0;
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, targetBox(matrix))) {
            var harm = LegacyInfusionInstability.rollHarm(random);
            MobEffectInstance effect = harm == LegacyInfusionInstability.Harm.FLUX_TAINT
                    ? new MobEffectInstance(InfusionHazardContent.FLUX_TAINT, harm.duration(),
                            harm.amplifier(), harm.ambient(), harm.visible())
                    : InfusionHazardContent.visExhaustion(harm.duration(), harm.amplifier(), harm.ambient());
            entity.addEffect(effect);
            affected++;
            if (!all) break;
        }
        return new Result(affected, null, false, 0, 0, 0);
    }

    private static Result warp(ServerLevel level, BlockPos matrix,
                               LegacyInfusionInstability.RandomSource random) {
        var players = level.getEntitiesOfClass(ServerPlayer.class, targetBox(matrix));
        if (players.isEmpty()) return new Result(0, null, false, 0, 0, 0);
        ServerPlayer player = players.get(random.nextInt(players.size()));
        var gain = LegacyInfusionInstability.rollWarp(random);
        var data = player.getData(ThaumcraftPlayerAttachments.PLAYER_DATA);
        WarpType type = gain.type() == LegacyInfusionInstability.WarpType.NORMAL
                ? WarpType.NORMAL : WarpType.TEMPORARY;
        data.warp().add(type, gain.amount());
        // InternalMethodHandler.addWarpToPlayer resets the counter on positive gain.
        data.warp().setCounter(data.warp().get(WarpType.NORMAL)
                + data.warp().get(WarpType.PERMANENT) + data.warp().get(WarpType.TEMPORARY));
        var catalog = BuiltInResearchCatalog.create();
        if (type != WarpType.TEMPORARY && data.knowledge().isResearchComplete("FIRSTSTEPS", catalog)
                && !data.knowledge().isResearchComplete("WARP", catalog)) {
            var service = new ResearchProgressionService(catalog);
            while (service.progress(data.knowledge(), data.warp(), "WARP", false, true,
                    false, false, true).progressed()) {
                if (data.knowledge().isResearchComplete("WARP", catalog)) break;
            }
            player.displayClientMessage(Component.translatable("research.WARP.warn"), true);
        }
        ThaumcraftNetworking.sync(player);
        return new Result(1, null, false, 0, 0, 0);
    }

    public static int grantInstabilityResearch(ServerLevel level, BlockPos matrix) {
        int granted = 0;
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, targetBox(matrix))) {
            var knowledge = player.getData(ThaumcraftPlayerAttachments.PLAYER_DATA).knowledge();
            if (!knowledge.addResearch(LegacyInfusionInstability.RESEARCH_KEY)) continue;
            ThaumcraftNetworking.sync(player);
            player.displayClientMessage(Component.translatable("got.instability")
                    .withStyle(ChatFormatting.DARK_PURPLE), true);
            granted++;
        }
        return granted;
    }

    private static AABB targetBox(BlockPos matrix) {
        return new AABB(matrix).inflate(LegacyInfusionInstability.TARGET_RANGE);
    }

    private static void explode(ServerLevel level, BlockPos center, float strength) {
        level.explode(null, center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5,
                strength, false, Level.ExplosionInteraction.NONE);
    }

    /** Native visible server particles; original custom shader arc is not ported. */
    private static void arc(ServerLevel level, BlockPos origin, Vec3 target,
                             LegacyInfusionInstability.RandomSource random) {
        var color = LegacyInfusionInstability.rollArcColor(random);
        var particle = new DustParticleOptions(new Vector3f(color.red(), color.green(), color.blue()), 1);
        Vec3 start = Vec3.atCenterOf(origin);
        int steps = Math.max(1, (int) Math.ceil(start.distanceTo(target) * 5));
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(origin.getX(), origin.getY(), origin.getZ())
                    > LegacyInfusionInstability.FX_RANGE * LegacyInfusionInstability.FX_RANGE) continue;
            for (int step = 0; step <= steps; step++) {
                Vec3 point = start.lerp(target, (double) step / steps);
                level.sendParticles(player, particle, false, point.x, point.y, point.z,
                        1, 0, 0, 0, 0);
            }
        }
    }
}
