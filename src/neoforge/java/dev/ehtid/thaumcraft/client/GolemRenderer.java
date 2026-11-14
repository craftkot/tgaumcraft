package dev.ehtid.thaumcraft.client;

import dev.ehtid.thaumcraft.ThaumcraftPort;
import dev.ehtid.thaumcraft.client.golem.GolemDetailLayer;
import dev.ehtid.thaumcraft.client.golem.GolemItemLayer;
import dev.ehtid.thaumcraft.client.golem.GolemModel;
import dev.ehtid.thaumcraft.golem.GolemModule;
import dev.ehtid.thaumcraft.entity.ThaumcraftGolem;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Newly authored construction-aware geometry, using only vanilla textures.
 * BETA26 supplies the construction IDs and attachment/animation semantics,
 * not the meshes or artwork. These material substitutes are not visual parity.
 */
@EventBusSubscriber(modid = ThaumcraftPort.MOD_ID,
        bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class GolemRenderer extends MobRenderer<ThaumcraftGolem, GolemModel> {
    private static final ResourceLocation WOOD = blockTexture("oak_planks");
    private static final ResourceLocation IRON = blockTexture("iron_block");
    private static final ResourceLocation CLAY = blockTexture("terracotta");
    private static final ResourceLocation BRASS = blockTexture("gold_block");
    private static final ResourceLocation VOID = blockTexture("obsidian");

    public GolemRenderer(EntityRendererProvider.Context context) {
        super(context, new GolemModel(), 0.2f);
        addLayer(new GolemDetailLayer(this));
        addLayer(new GolemItemLayer(this, context.getItemInHandRenderer()));
    }

    @SubscribeEvent
    public static void registerRenderer(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(GolemModule.GOLEM.get(), GolemRenderer::new);
        event.registerEntityRenderer(GolemModule.GOLEM_DART.get(), GolemDartRenderer::new);
    }

    @Override
    public ResourceLocation getTextureLocation(ThaumcraftGolem golem) {
        return switch (golem.design().material()) {
            case WOOD -> WOOD;
            case IRON, THAUMIUM -> IRON;
            case CLAY -> CLAY;
            case BRASS -> BRASS;
            case VOID -> VOID;
        };
    }

    private static ResourceLocation blockTexture(String name) {
        return ResourceLocation.withDefaultNamespace("textures/block/" + name + ".png");
    }
}
