package dev.ehtid.thaumcraft.item;

import dev.ehtid.thaumcraft.network.ThaumcraftNetworking;
import dev.ehtid.thaumcraft.player.ThaumcraftPlayerAttachments;
import dev.ehtid.thaumcraft.port.core.AspectRegistry;
import dev.ehtid.thaumcraft.port.core.BuiltInResearchCatalog;
import dev.ehtid.thaumcraft.port.core.BuiltInResearchCategories;
import dev.ehtid.thaumcraft.port.core.LegacyCurioUse;
import dev.ehtid.thaumcraft.port.core.ResearchCategoryData;
import dev.ehtid.thaumcraft.port.core.ResearchProgressionService;
import dev.ehtid.thaumcraft.port.core.WarpType;
import dev.ehtid.thaumcraft.registry.ThaumcraftItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.Level;
import java.util.List;

/**
 * Seven original BETA26 ItemCurio metadata variants. User interaction
 * reproduces original knowledge and warp rolls, including CrimsonRites
 * actual-warp gate (>20 NORMAL+PERMANENT, not temporary) and advancement.
 *
 * Curio metadata is persisted in 1.21 DataComponents rather than damage.
 * The CardCurio ingredient/loot bridge reads THIS EXACT metadata, and
 * must never silently reroll another variant when inventory lacks one.
 */
public final class CurioItem extends Item {
    private static final String VARIANT_TAG = "LegacyCurioMeta";
    private static final List<String> CATEGORIES =
            BuiltInResearchCategories.create(new AspectRegistry()).values().stream()
                    .map(ResearchCategoryData::key).toList();

    public CurioItem(Properties properties) {
        super(properties);
    }

    public static ItemStack create(int variant) {
        if (variant < 0 || variant >= LegacyCurioUse.VARIANTS.size())
            throw new IllegalArgumentException("original curio variant " + variant);
        ItemStack stack = new ItemStack(ThaumcraftItems.CURIO.get());
        CompoundTag data = new CompoundTag();
        data.putInt(VARIANT_TAG, variant);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(variant));
        return stack;
    }

    /** -1 means an invalid or unported metadata value; never reward it. */
    public static int legacyMetadata(ItemStack stack) {
        if (stack.isEmpty() || !stack.is(ThaumcraftItems.CURIO.get())) return -1;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return 0; // Original bare 1.12 ItemStack uses metadata 0.
        CompoundTag nbt = data.copyTag();
        if (!nbt.contains(VARIANT_TAG, Tag.TAG_INT)) return -1;
        int index = nbt.getInt(VARIANT_TAG);
        return index >= 0 && index < LegacyCurioUse.VARIANTS.size() ? index : -1;
    }

    @Override
    public Component getName(ItemStack stack) {
        int index = legacyMetadata(stack);
        String suffix = index < 0 ? "arcane" : LegacyCurioUse.VARIANTS.get(index);
        return Component.translatable("item.curio." + suffix + ".name");
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("item.curio.text"));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player user, InteractionHand hand) {
        ItemStack held = user.getItemInHand(hand);
        int index = legacyMetadata(held);
        if (index < 0) return InteractionResultHolder.fail(held);
        if (level.isClientSide) return InteractionResultHolder.success(held);
        if (!(user instanceof ServerPlayer player)) return InteractionResultHolder.pass(held);

        var state = player.getData(ThaumcraftPlayerAttachments.PLAYER_DATA);
        // The original getActualWarp includes only NORMAL and PERMANENT.
        int actualWarp = state.warp().get(WarpType.NORMAL) + state.warp().get(WarpType.PERMANENT);
        if (index == 6 && actualWarp <= 20) {
            player.displayClientMessage(Component.translatable("fail.crimsonrites")
                    .withStyle(ChatFormatting.DARK_PURPLE), false);
            return InteractionResultHolder.pass(held);
        }

        if (index == 6 && !state.knowledge().isResearchKnown("CrimsonRites")) {
            // BETA26 completeResearch loops ResearchManager.progressResearch
            // while its prerequisites allow progress. The original item does
            // NOT withhold knowledge/warp if the research is still gated.
            var service = new ResearchProgressionService(BuiltInResearchCatalog.create());
            for (int stage = 0; stage < 5; stage++) {
                var outcome = service.progress(state.knowledge(), state.warp(),
                        "CrimsonRites", false, true, false, false, true);
                if (!outcome.progressed()) break;
                if (outcome.xpAward() > 0) player.giveExperiencePoints(outcome.xpAward());
            }
        }

        var rng = player.getRandom();
        var result = LegacyCurioUse.activate(index, state.knowledge(), state.warp(), CATEGORIES,
                new LegacyCurioUse.Source() {
                    @Override public int nextInt(int bound) { return rng.nextInt(bound); }
                    @Override public boolean nextBoolean() { return rng.nextBoolean(); }
                });
        if (!result.consumed()) return InteractionResultHolder.pass(held);
        if (!player.getAbilities().instabuild) held.shrink(1);
        player.displayClientMessage(Component.translatable("tc.knowledge.gained")
                .withStyle(ChatFormatting.DARK_PURPLE), false);
        ThaumcraftNetworking.sync(player);
        return InteractionResultHolder.success(held);
    }
}
