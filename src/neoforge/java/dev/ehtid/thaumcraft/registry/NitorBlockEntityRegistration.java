package dev.ehtid.thaumcraft.registry;

import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.blockentity.NitorBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterEvent;

/**
 * Isolated registration for the original TileNitor client ticker.
 * Kept separate from the shared ThaumcraftBlockEntities DeferredRegister.
 */
@EventBusSubscriber(modid = ThaumcraftPort.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class NitorBlockEntityRegistration {
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(ThaumcraftPort.MOD_ID, "nitor");
    public static BlockEntityType<NitorBlockEntity> TYPE;

    private NitorBlockEntityRegistration() {}

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.BLOCK_ENTITY_TYPE, helper -> {
            Block[] validBlocks = ThaumcraftBlocks.NITOR.values().stream()
                    .map(block -> (Block) block.get())
                    .toArray(Block[]::new);
            if (validBlocks.length != 16) {
                throw new IllegalStateException("BETA26 Nitor block entity expected 16 color variants");
            }
            TYPE = BlockEntityType.Builder.of(NitorBlockEntity::new, validBlocks).build(null);
            helper.register(ID, TYPE);
        });
    }
}
