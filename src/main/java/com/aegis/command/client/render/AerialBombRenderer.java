package com.aegis.command.client.render;

import com.aegis.command.client.render.mesh.WMesh;
import com.aegis.command.entity.AerialBomb;
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

import static com.aegis.command.client.render.mesh.WMesh.*;

public class AerialBombRenderer extends EntityRenderer<AerialBomb> {
    public AerialBombRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
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
        float s = 1.6f / 16f;
        ps.scale(s, s, s);
        WMesh m = AircraftModels.bomb();
        int ov = OverlayTexture.NO_OVERLAY;
        VertexConsumer body = buffers.getBuffer(RenderType.entityCutoutNoCull(StrikeJetRenderer.HULL));
        m.emit(ps, body, MAIN, PAINT, 1, 0.36f, 0.4f, 0.31f, 1, 0.3f, 14, 0, 0, light, ov);
        m.emit(ps, body, MAIN, ACCENT, 1, 0.25f, 0.27f, 0.22f, 1, 0.3f, 14, 0, 0, light, ov);
        m.emit(ps, body, MAIN, DARK, 1, 0.18f, 0.19f, 0.2f, 1, 0.2f, 12, 0, 0, light, ov);
        m.emit(ps, body, MAIN, TRIM, 1, 0.85f, 0.7f, 0.15f, 1, 0.2f, 12, 0, 0, light, ov);
        VertexConsumer glow = buffers.getBuffer(RenderType.entityTranslucentEmissive(WeaponRenderer.EMISSIVE));
        float blink = (bomb.tickCount / 3) % 2 == 0 ? 1f : 0.4f;
        m.emit(ps, glow, MAIN, GLOW, 1, 1f, 0.55f * blink, 0.1f, 1, 0, 1, 0, 0, WeaponRenderer.FULL_BRIGHT, ov);
        m.emit(ps, glow, MAIN, HOT, 1, 1f, 0.3f, 0.2f, 1, 0, 1, 0, 0, WeaponRenderer.FULL_BRIGHT, ov);
        ps.popPose();
        super.render(bomb, yaw, partial, ps, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(AerialBomb bomb) {
        return StrikeJetRenderer.HULL;
    }
}
