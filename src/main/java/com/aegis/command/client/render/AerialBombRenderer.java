package com.aegis.command.client.render;

import com.aegis.command.entity.AerialBomb;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public class AerialBombRenderer extends EntityRenderer<AerialBomb> {
    private final ModelPart hull;
    private final ModelPart glow;

    public AerialBombRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        ModelPart root = ctx.bakeLayer(AircraftModels.BOMB);
        hull = root.getChild("hull");
        glow = root.getChild("glow");
        shadowRadius = 0.3f;
    }

    @Override
    public void render(AerialBomb bomb, float yaw, float partial, PoseStack ps, MultiBufferSource buffers, int light) {
        Vec3 v = bomb.getDeltaMovement();
        double h = Math.sqrt(v.x * v.x + v.z * v.z);
        double len = v.length();
        float yawRad = (float) Math.atan2(-v.x, -v.z);
        float pitch = len < 1e-4 ? -1.2f : (float) Math.asin(Math.max(-1, Math.min(1, v.y / len)));
        if (h < 1e-4) yawRad = 0;
        ps.pushPose();
        ps.translate(0, 0.3, 0);
        ps.mulPose(Axis.YP.rotation(yawRad));
        ps.mulPose(Axis.XP.rotation(pitch));
        ps.mulPose(Axis.ZP.rotationDegrees((bomb.tickCount + partial) * 9f));
        ps.scale(-1.6f, -1.6f, 1.6f);
        hull.render(ps, buffers.getBuffer(RenderType.entityCutoutNoCull(StrikeJetRenderer.HULL)), light, OverlayTexture.NO_OVERLAY, 0.42f, 0.45f, 0.36f, 1f);
        glow.render(ps, buffers.getBuffer(RenderType.eyes(StrikeJetRenderer.GLOW)), 0xF000F0, OverlayTexture.NO_OVERLAY, 1f, 0.75f, 0.1f, 1f);
        ps.popPose();
        super.render(bomb, yaw, partial, ps, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(AerialBomb bomb) {
        return StrikeJetRenderer.HULL;
    }
}
