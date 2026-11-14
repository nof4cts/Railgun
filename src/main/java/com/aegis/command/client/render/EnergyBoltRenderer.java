package com.aegis.command.client.render;

import com.aegis.command.entity.EnergyBolt;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public class EnergyBoltRenderer extends EntityRenderer<EnergyBolt> {
    public EnergyBoltRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        shadowRadius = 0f;
    }

    @Override
    public void render(EnergyBolt bolt, float yaw, float partial, PoseStack ps, MultiBufferSource buffers, int light) {
        int c = bolt.color();
        float r = ((c >> 16) & 255) / 255f, g = ((c >> 8) & 255) / 255f, b = (c & 255) / 255f;
        float size = bolt.size() * (bolt.kind() == EnergyBolt.TITAN ? 0.9f : 0.5f);
        float pulse = 0.85f + 0.15f * (float) Math.sin((bolt.tickCount + partial) * 2.1f);
        VertexConsumer vc = buffers.getBuffer(RenderType.eyes(WeaponRenderer.FLARE));

        ps.pushPose();
        ps.translate(0, bolt.getBbHeight() / 2, 0);
        // camera-facing core + halo
        ps.pushPose();
        ps.mulPose(entityRenderDispatcher.cameraOrientation());
        quad(ps, vc, size * pulse, size * pulse, r, g, b);
        quad(ps, vc, size * 0.45f, size * 0.45f, 1f, 1f, 1f);
        ps.popPose();
        // velocity streak
        Vec3 v = bolt.getDeltaMovement();
        if (v.lengthSqr() > 1e-4) {
            float yawR = (float) Math.atan2(-v.x, -v.z);
            float pitch = (float) Math.asin(Math.max(-1, Math.min(1, v.y / v.length())));
            ps.mulPose(Axis.YP.rotation(yawR));
            ps.mulPose(Axis.XP.rotation(pitch));
            ps.translate(0, 0, size * 1.6f);
            ps.mulPose(Axis.YP.rotationDegrees(90));
            quad(ps, vc, size * 2.2f, size * 0.35f, r * 0.8f, g * 0.8f, b * 0.8f);
            ps.mulPose(Axis.XP.rotationDegrees(90));
            quad(ps, vc, size * 2.2f, size * 0.35f, r * 0.8f, g * 0.8f, b * 0.8f);
        }
        ps.popPose();
        super.render(bolt, yaw, partial, ps, buffers, light);
    }

    private static void quad(PoseStack ps, VertexConsumer vc, float w, float h, float r, float g, float b) {
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        v(vc, m, n, -w, -h, 0, 0, r, g, b);
        v(vc, m, n, w, -h, 1, 0, r, g, b);
        v(vc, m, n, w, h, 1, 1, r, g, b);
        v(vc, m, n, -w, h, 0, 1, r, g, b);
    }

    private static void v(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float u, float vv, float r, float g, float b) {
        vc.vertex(m, x, y, 0).color(r, g, b, 1f).uv(u, vv).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(WeaponRenderer.FULL_BRIGHT).normal(n, 0, 0, 1).endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(EnergyBolt bolt) {
        return WeaponRenderer.FLARE;
    }
}
