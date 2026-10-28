package com.abomination.horror.client.render;

import com.abomination.horror.AbominationMod;
import com.abomination.horror.entity.AbominationEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Emissive eyes, veins and growths. They pulse like a heartbeat and flare when it is enraged. */
public class AbominationGlowLayer extends RenderLayer<AbominationEntity, AbominationModel> {
    private static final ResourceLocation GLOW = AbominationMod.id("textures/entity/abomination_glow.png");

    public AbominationGlowLayer(RenderLayerParent<AbominationEntity, AbominationModel> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbominationEntity e, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        float beat = Mth.sin(ageInTicks * 0.15F);
        float pulse = 0.55F + 0.45F * beat * beat;
        if (e.getPhase() == AbominationEntity.PHASE_NIGHTMARE || e.getAnim() == AbominationEntity.ANIM_ROAR) pulse = 0.8F + 0.2F * beat;
        if (e.isFrozenByGaze()) pulse *= 0.4F; // eyes dim when it plays dead
        float a = this.getParentModel().alpha;
        float c = pulse * a;
        VertexConsumer vc = buffers.getBuffer(RenderType.eyes(GLOW));
        AbominationModel model = this.getParentModel();
        model.glowPass = true;
        model.renderToBuffer(pose, vc, 0xF000F0, OverlayTexture.NO_OVERLAY, c, c, c, 1.0F);
        model.glowPass = false;
    }
}
