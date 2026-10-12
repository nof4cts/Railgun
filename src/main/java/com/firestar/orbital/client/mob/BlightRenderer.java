package com.firestar.orbital.client.mob;

import com.firestar.orbital.OrbitalMod;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;

/** Draws a blight creature: its skin, then its glow (eyes, veins, the light inside) at full brightness on top. */
public class BlightRenderer<T extends Mob, M extends EntityModel<T>> extends MobRenderer<T, M> {
    private final ResourceLocation skin;
    private final float scale;

    public BlightRenderer(EntityRendererProvider.Context ctx, M model, String name, float shadow, float scale) {
        super(ctx, model, shadow);
        this.skin = OrbitalMod.id("textures/entity/" + name + ".png");
        this.scale = scale;
        RenderType glow = RenderType.eyes(OrbitalMod.id("textures/entity/" + name + "_glow.png"));
        addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return glow;
            }
        });
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return skin;
    }

    @Override
    protected void scale(T entity, PoseStack ps, float partial) {
        ps.scale(scale, scale, scale);
    }
}
