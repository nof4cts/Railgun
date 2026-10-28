package com.abomination.horror.client.render;

import com.abomination.horror.AbominationMod;
import com.abomination.horror.entity.AbominationEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class AbominationRenderer extends MobRenderer<AbominationEntity, AbominationModel> {
    private static final ResourceLocation TEXTURE = AbominationMod.id("textures/entity/abomination.png");

    public AbominationRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new AbominationModel(ctx.bakeLayer(AbominationModel.LAYER)), 1.1F);
        this.addLayer(new AbominationGlowLayer(this));
    }

    @Override
    public ResourceLocation getTextureLocation(AbominationEntity entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(AbominationEntity entity, PoseStack pose, float partialTick) {
        float s = entity.isAwakened() ? 1.18F : 1.0F;
        pose.scale(s, s, s);
    }

    @Override
    protected void setupRotations(AbominationEntity e, PoseStack pose, float ageInTicks, float rotationYaw, float partialTick) {
        // no vanilla death flip: the model collapses and dissolves instead
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - rotationYaw));
        float age = e.tickCount - e.animStartTick + partialTick;
        if (e.getAnim() == AbominationEntity.ANIM_EMERGE) {
            float p = Mth.clamp(age / 60.0F, 0.0F, 1.0F);
            float rise = 1.0F - (1.0F - p) * (1.0F - p);
            pose.translate(Mth.sin(ageInTicks * 1.7F) * 0.04F * (1.0F - p), -(1.0F - rise) * 3.8F, 0.0F);
        } else if (e.getAnim() == AbominationEntity.ANIM_JUMPSCARE && age < 26.0F) {
            int step = (int) (ageInTicks * 2.0F);
            pose.translate(((step * 7919) % 13 - 6) * 0.012F, ((step * 104729) % 11 - 5) * 0.01F, ((step * 31) % 9 - 4) * 0.012F);
        } else if (e.getAnim() == AbominationEntity.ANIM_ROAR) {
            pose.translate(Mth.sin(ageInTicks * 3.3F) * 0.025F, 0.0F, Mth.cos(ageInTicks * 2.9F) * 0.025F);
        }
    }
}
