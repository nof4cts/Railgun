package com.aegis.command.client.render;

import com.aegis.command.AegisCommand;
import com.aegis.command.entity.StrikeJet;
import com.aegis.command.mission.AircraftType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public class StrikeJetRenderer extends EntityRenderer<StrikeJet> {
    public static final ResourceLocation HULL = AegisCommand.id("textures/entity/aircraft.png");
    public static final ResourceLocation GLOW = AegisCommand.id("textures/entity/engine_glow.png");

    private final ModelPart[] hull = new ModelPart[3];
    private final ModelPart[] glow = new ModelPart[3];

    public StrikeJetRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        ModelPart k = ctx.bakeLayer(AircraftModels.KESTREL);
        ModelPart g = ctx.bakeLayer(AircraftModels.GOLIATH);
        ModelPart w = ctx.bakeLayer(AircraftModels.WRAITH);
        hull[0] = k.getChild("hull"); glow[0] = k.getChild("glow");
        hull[1] = g.getChild("hull"); glow[1] = g.getChild("glow");
        hull[2] = w.getChild("hull"); glow[2] = w.getChild("glow");
        shadowRadius = 0f;
    }

    @Override
    public void render(StrikeJet jet, float entityYaw, float partial, PoseStack ps, MultiBufferSource buffers, int light) {
        AircraftType ac = jet.aircraft();
        int i = ac.ordinal();
        double t = jet.flightTime(partial);
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(180f - jet.getYRot()));
        // gentle banking and pitch oscillation, a hard bank as it breaks away after the drop
        float life = jet.lifeTicks();
        float breakAway = (float) Math.max(0, Math.min(1, (t - life * 0.55) / 25.0));
        float bank = (float) Math.sin(t * 0.06) * 4f + breakAway * 38f;
        ps.mulPose(Axis.ZP.rotationDegrees(bank));
        ps.mulPose(Axis.XP.rotationDegrees((float) Math.sin(t * 0.045) * 1.5f - breakAway * 8f));
        ps.scale(-ac.scale, -ac.scale, ac.scale);

        int tint = ac.tint;
        float r = ((tint >> 16) & 255) / 255f, g = ((tint >> 8) & 255) / 255f, b = (tint & 255) / 255f;
        int lit = Math.max(light, 0x00A000A0);
        hull[i].render(ps, buffers.getBuffer(RenderType.entityCutoutNoCull(HULL)), lit, OverlayTexture.NO_OVERLAY, r, g, b, 1f);

        float pulse = 0.75f + 0.25f * (float) Math.sin(t * 1.3);
        float gr = ac == AircraftType.WRAITH ? 0.4f : 1f, gg = ac == AircraftType.WRAITH ? 0.7f : 0.55f, gb = ac == AircraftType.WRAITH ? 1f : 0.2f;
        glow[i].render(ps, buffers.getBuffer(RenderType.eyes(GLOW)), 0xF000F0, OverlayTexture.NO_OVERLAY, gr * pulse, gg * pulse, gb * pulse, 1f);
        ps.popPose();
        super.render(jet, entityYaw, partial, ps, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(StrikeJet jet) {
        return HULL;
    }
}
