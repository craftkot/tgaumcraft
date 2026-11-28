package dev.ehtid.thaumcraft.block;

import com.mojang.serialization.MapCodec;
import dev.ehtid.thaumcraft.blockentity.ArcaneWorkbenchBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import dev.ehtid.thaumcraft.menu.ArcaneWorkbenchMenu;
import net.minecraft.world.Containers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** FIRSTSTEPS Arcane Workbench block: its fifteen slots belong to the server BlockEntity. */
public final class ArcaneWorkbenchBlock extends BaseEntityBlock {
    public static final MapCodec<ArcaneWorkbenchBlock> CODEC = simpleCodec(ArcaneWorkbenchBlock::new);

    public ArcaneWorkbenchBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ArcaneWorkbenchBlockEntity(pos, state);
    }

    private static void smokeMarker(String message) {
        if ("1".equals(System.getenv("THAUMCRAFT_SMOKE"))) {
            System.out.println(message);
        }
    }

    private void openWorkbench(Level level, BlockPos pos, Player player, String interactionPath) {
        if (level.isClientSide) return;
        if (!(level.getBlockEntity(pos) instanceof ArcaneWorkbenchBlockEntity bench)) {
            smokeMarker("TC_SMOKE_WORKBENCH_SERVER_BLOCK_ENTITY=NO path=" + interactionPath + " pos=" + pos);
            return;
        }
        smokeMarker("TC_SMOKE_WORKBENCH_SERVER_BLOCK_ENTITY=YES path=" + interactionPath + " pos=" + pos);
        var menuId = player.openMenu(new SimpleMenuProvider(
                (id, inventory, viewer) -> new ArcaneWorkbenchMenu(id, inventory, bench),
                Component.translatable("container.thaumcraft.arcane_workbench")));
        smokeMarker("TC_SMOKE_WORKBENCH_SERVER_OPEN_MENU_RESULT="
                + (menuId.isPresent() ? "YES" : "NO")
                + (menuId.isPresent() ? " container_id=" + menuId.getAsInt() : "")
                + " path=" + interactionPath);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        openWorkbench(level, pos, player, "empty_hand");
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * BETA26 onBlockActivated opened the GUI for either hand, even when the
     * player held an item. Preserve that behavior on the 1.21.1 item-on-block path.
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                              BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        openWorkbench(level, pos, player, "item_on");
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState oldState, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!oldState.is(newState.getBlock())) {
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof ArcaneWorkbenchBlockEntity bench) {
                Containers.dropContents(level, pos, bench);
                level.updateNeighbourForOutputSignal(pos, this);
            }
        }
        super.onRemove(oldState, level, pos, newState, moving);
    }
}
