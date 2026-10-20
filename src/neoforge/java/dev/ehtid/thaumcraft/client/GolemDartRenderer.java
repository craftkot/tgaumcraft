package dev.ehtid.thaumcraft.client;

import dev.ehtid.thaumcraft.entity.GolemDart;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/** Provisional vanilla arrow visual; original Thaumcraft dart artwork is not redistributed. */
public final class GolemDartRenderer extends ArrowRenderer<GolemDart> {
    public GolemDartRenderer(EntityRendererProvider.Context context) { super(context); }
    @Override public ResourceLocation getTextureLocation(GolemDart dart) { return ResourceLocation.withDefaultNamespace("textures/entity/projectiles/arrow.png"); }
}
