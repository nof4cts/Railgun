package com.aegis.command.client.render;

import com.aegis.command.AegisCommand;
import com.aegis.command.client.render.mesh.WMesh;
import com.aegis.command.entity.StrikeJet;
import com.aegis.command.mission.AircraftType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

import static com.aegis.command.client.render.mesh.WMesh.*;

public class StrikeJetRenderer extends EntityRenderer<StrikeJet> {
    public static final ResourceLocation HULL = AegisCommand.id("textures/entity/aircraft_skin.png");

    public StrikeJetRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        shadowRadius = 0f;
    }

    @Override
    public void render(StrikeJet jet, float entityYaw, float partial, PoseStack ps, MultiBufferSource buffers, int light) {
        AircraftType ac = jet.aircraft();
        double t = jet.flightTime(partial);
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(180f - jet.getYRot()));
        // gentle banking and pitch oscillation, a hard bank as it breaks away after the drop
        float life = jet.lifeTicks();
        float breakAway = (float) Math.max(0, Math.min(1, (t - life * 0.55) / 25.0));
        float bank = (float) Math.sin(t * 0.06) * 4f + breakAway * 38f;
        ps.mulPose(Axis.ZP.rotationDegrees(bank));
        ps.mulPose(Axis.XP.rotationDegrees((float) Math.sin(t * 0.045) * 1.5f - breakAway * 8f));
        float s = ac.scale / 16f;
        ps.scale(s, s, s);
        renderAircraft(ac, t, ps, buffers, Math.max(light, 0x00A000A0), 1f);
        ps.popPose();
        super.render(jet, entityYaw, partial, ps, buffers, light);
    }

    private static float[] rgb(int c, float mul) {
        return new float[]{Math.min(1, ((c >> 16) & 255) / 255f * mul), Math.min(1, ((c >> 8) & 255) / 255f * mul), Math.min(1, (c & 255) / 255f * mul)};
    }

    /** Draw an aircraft in model units (already scaled); {@code throttle} scales the afterburner. */
    public static void renderAircraft(AircraftType ac, double t, PoseStack ps, MultiBufferSource buffers, int light, float throttle) {
        WMesh m = AircraftModels.get(ac);
        int ov = OverlayTexture.NO_OVERLAY;
        int tint = ac.tint & 0xFFFFFF;
        int engine = ac == AircraftType.WRAITH ? 0x4FA8FF : 0xFF8A2A;
        int stripe = ac == AircraftType.WRAITH ? 0x7A1E26 : ac == AircraftType.GOLIATH ? 0xB89A3A : 0xD0A23C;
        VertexConsumer body = buffers.getBuffer(RenderType.entityCutoutNoCull(HULL));
        for (int g = 0; g < GROUPS; g++) {
            float[] p = rgb(tint, 1.0f), a = rgb(tint, 0.72f);
            m.emit(ps, body, g, PAINT, 1, p[0], p[1], p[2], 1, 0.35f, 16, 0xBFD8FF, 0.25f, light, ov);
            m.emit(ps, body, g, ACCENT, 1, a[0], a[1], a[2], 1, 0.3f, 16, 0xBFD8FF, 0.2f, light, ov);
            m.emit(ps, body, g, DARK, 1, 0.2f, 0.22f, 0.25f, 1, 0.2f, 12, 0, 0, light, ov);
            m.emit(ps, body, g, BLACK, 1, 0.08f, 0.09f, 0.11f, 1, 1.0f, 30, 0xE0B060, 0.5f, light, ov);   // canopy: hard glint + gold film
            m.emit(ps, body, g, CHROME, 1, 0.55f, 0.57f, 0.6f, 1, 0.8f, 9, 0, 0, light, ov);
            float[] st = rgb(stripe, 1f);
            m.emit(ps, body, g, TRIM, 1, st[0], st[1], st[2], 1, 0.3f, 14, 0, 0, light, ov);
        }
        float pulse = 0.8f + 0.2f * (float) Math.sin(t * 1.9);
        float[] e = rgb(engine, 1f);
        VertexConsumer glow = buffers.getBuffer(RenderType.entityTranslucentEmissive(WeaponRenderer.EMISSIVE));
        boolean blink = (((long) (t * 50)) / 600) % 2 == 0 || (((long) (t * 50)) % 600) < 80;
        for (int g = 0; g < GROUPS; g++) {
            m.emit(ps, glow, g, GLOW, 1, e[0] * pulse, e[1] * pulse, e[2] * pulse, 1, 0, 1, 0, 0, WeaponRenderer.FULL_BRIGHT, ov);
            m.emit(ps, glow, g, HOT, 1, 1f, 0.95f, 0.85f, 1, 0, 1, 0, 0, WeaponRenderer.FULL_BRIGHT, ov);
            boolean green = g == AircraftModels.NAV_GREEN;
            float nl = blink ? 1f : 0.35f;
            m.emit(ps, glow, g, LENS, 1, green ? 0.15f * nl : nl, green ? nl : 0.1f * nl, green ? 0.3f * nl : 0.1f * nl, 1, 0, 1, 0, 0, WeaponRenderer.FULL_BRIGHT, ov);
        }
        // afterburner plumes: crossed additive streaks + a heat bloom at each nozzle
        float[][] nozzles = switch (ac) {
            case KESTREL -> new float[][]{{-1.8f, 0.2f, 33.5f, 1.3f}, {1.8f, 0.2f, 33.5f, 1.3f}};
            case GOLIATH -> new float[][]{{-14, -1.2f, 2.4f, 1.1f}, {14, -1.2f, 2.4f, 1.1f}, {-27, 4.9f, 7.4f, 1.1f}, {27, 4.9f, 7.4f, 1.1f}};
            case WRAITH -> new float[][]{{-8.5f, 1.1f, 13.2f, 1.0f}, {8.5f, 1.1f, 13.2f, 1.0f}};
        };
        // Goliath pod y values are computed in the model; recompute here to match
        if (ac == AircraftType.GOLIATH) {
            for (float[] n : nozzles) {
                float y = 1.4f - Math.abs(n[0]) * 0.02f - 2.4f;
                n[1] = y;
                n[2] = (Math.abs(n[0]) < 20 ? -9f : -4f) + 11.4f;
            }
        }
        VertexConsumer plume = buffers.getBuffer(RenderType.eyes(WeaponRenderer.STREAK));
        VertexConsumer bloom = buffers.getBuffer(RenderType.eyes(WeaponRenderer.GLOW_TEX));
        float flick = 0.85f + 0.15f * (float) Math.sin(t * 9.7) * (float) Math.sin(t * 3.3);
        for (float[] n : nozzles) {
            float len = (ac == AircraftType.WRAITH ? 9f : 16f) * throttle * flick;
            float w = n[3] * (ac == AircraftType.WRAITH ? 0.6f : 1f);
            ps.pushPose();
            ps.translate(n[0], n[1], n[2] + len * 0.5f);
            for (int i = 0; i < 3; i++) {
                ps.pushPose();
                ps.mulPose(Axis.ZP.rotationDegrees(i * 60));
                ps.mulPose(Axis.YP.rotationDegrees(90));
                WeaponRenderer.quad(ps, plume, len * 0.5f, w, e[0] * 0.9f, e[1] * 0.9f, e[2] * 0.9f);
                WeaponRenderer.quad(ps, plume, len * 0.3f, w * 0.4f, 1, 1, 1);
                ps.popPose();
            }
            ps.popPose();
            ps.pushPose();
            ps.translate(n[0], n[1], n[2] + 0.4f);
            WeaponRenderer.quad(ps, bloom, w * 2.6f, w * 2.6f, e[0] * 0.8f, e[1] * 0.8f, e[2] * 0.8f);
            ps.mulPose(Axis.YP.rotationDegrees(90));
            WeaponRenderer.quad(ps, bloom, w * 2.2f, w * 2.2f, e[0] * 0.5f, e[1] * 0.5f, e[2] * 0.5f);
            ps.popPose();
        }
    }

    @Override
    public ResourceLocation getTextureLocation(StrikeJet jet) {
        return HULL;
    }
}
