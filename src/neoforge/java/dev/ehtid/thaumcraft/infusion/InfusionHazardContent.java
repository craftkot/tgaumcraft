package dev.ehtid.thaumcraft.infusion;

import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.world.ThaumcraftAuraSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Actual native effects needed by BETA26 matrix accidents. Flux Taint ticks
 * damage. Vis Exhaustion stores the original duration/cure state and exposes
 * the source CasterManager discount penalty; wiring to actual focus casting
 * awaits M10. The original potion tick itself is empty.
 * Goo contact/evaporation is supported. Legacy finite-fluid equalization,
 * thaumic-slime ecology and taint-fibre conversion remain M11 work.
 */
public final class InfusionHazardContent {
    private InfusionHazardContent() {}
    private static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, ThaumcraftPort.MOD_ID);
    private static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, ThaumcraftPort.MOD_ID);
    public static final DeferredHolder<MobEffect, MobEffect> FLUX_TAINT =
            EFFECTS.register("flux_taint", FluxTaintEffect::new);
    public static final DeferredHolder<MobEffect, MobEffect> VIS_EXHAUST =
            EFFECTS.register("vis_exhaust", () -> new MobEffect(MobEffectCategory.HARMFUL, 0x6B5B86) {});
    public static final DeferredHolder<Block, FluxGooBlock> FLUX_GOO =
            BLOCKS.register("flux_goo", FluxGooBlock::new);
    public static final ResourceKey<DamageType> TAINT_DAMAGE = ResourceKey.create(
            Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(ThaumcraftPort.MOD_ID, "taint"));

    public static void register(IEventBus bus) {
        EFFECTS.register(bus);
        BLOCKS.register(bus);
    }

    /** Future caster discount hook; subtract this (amp0 => -10 percentage points). */
    public static float visExhaustDiscountPenalty(LivingEntity entity) {
        MobEffectInstance effect = entity.getEffect(VIS_EXHAUST);
        return effect == null ? 0 : (effect.getAmplifier() + 1) * 0.1F;
    }

    /** Source instances explicitly clear their curative-item list. */
    public static MobEffectInstance visExhaustion(int duration, int amplifier, boolean ambient) {
        var effect = new MobEffectInstance(VIS_EXHAUST, duration, amplifier, ambient, true);
        effect.getCures().clear();
        return effect;
    }

    public static final class FluxTaintEffect extends MobEffect {
        private FluxTaintEffect() { super(MobEffectCategory.HARMFUL, 0x800080); }
        @Override public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
            int period = 40 >> amplifier;
            return period <= 0 || duration % period == 0;
        }
        @Override public boolean applyEffectTick(LivingEntity entity, int amplifier) {
            if (entity.level() instanceof ServerLevel level
                    && !entity.isInvertedHealAndHarm()) {
                entity.hurt(new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                        .getHolderOrThrow(TAINT_DAMAGE)), 1.0F);
            }
            return true;
        }
    }

    /** Bounded source Goo behavior, not a vanilla liquid or potion substitute. */
    public static final class FluxGooBlock extends Block {
        public static final IntegerProperty LEVEL = IntegerProperty.create("level", 0, 7);
        private FluxGooBlock() {
            super(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).noCollission()
                    .noOcclusion().noLootTable().strength(100).sound(SoundType.SLIME_BLOCK).randomTicks());
            registerDefaultState(stateDefinition.any().setValue(LEVEL, 7));
        }
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(LEVEL);
        }
        @Override protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos,
                                                 CollisionContext context) {
            return box(0, 0, 0, 16, (state.getValue(LEVEL) + 1) * 2, 16);
        }
        @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos,
                                                          CollisionContext context) { return Shapes.empty(); }
        @Override protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
            int quanta = state.getValue(LEVEL) + 1;
            double drag = 1.0F - quanta / 8.0F;
            var velocity = entity.getDeltaMovement();
            entity.setDeltaMovement(velocity.x * drag, velocity.y, velocity.z * drag);
            if (!level.isClientSide && entity instanceof LivingEntity living)
                living.addEffect(visExhaustion(600, state.getValue(LEVEL) / 3, true));
        }
        @Override protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource rng) {
            if (rng.nextInt(4) != 0) return;
            int value = state.getValue(LEVEL);
            if (value > 0) {
                level.setBlock(pos, state.setValue(LEVEL, value - 1), 2);
                ThaumcraftAuraSavedData.get(level).polluteFlux(pos, 1);
            } else if (rng.nextBoolean()) {
                ThaumcraftAuraSavedData.get(level).polluteFlux(pos, 1);
                level.removeBlock(pos, false);
            }
            // The other level0 branch is original taintFibre conversion, which
            // must wait for that actual block; retaining goo avoids a fake block.
        }
    }
}
