package com.aegis.command.client.render;

import com.aegis.command.AegisCommand;
import com.aegis.command.client.render.mesh.WMesh;
import com.aegis.command.item.AegisWeaponItem;
import com.aegis.command.item.WeaponType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import static com.aegis.command.client.render.mesh.WMesh.*;

/**
 * Renders the arsenal weapons from their code-built {@link WeaponModels}. Opaque materials get a
 * panel-line texture, vanilla diffuse light plus a view-space specular and an energy-tinted rim;
 * energy parts are drawn full-bright with an additive bloom pass; glass is translucent emissive.
 * Animated groups (cell, spinning coils) are posed from {@link WeaponAnimator.Pose}.
 */
public class WeaponRenderer extends BlockEntityWithoutLevelRenderer {
    private static WeaponRenderer instance;
    public static final ResourceLocation FLARE = AegisCommand.id("textures/entity/energy_bolt.png");
    public static final ResourceLocation GLOW_TEX = AegisCommand.id("textures/entity/fx_glow.png");
    public static final ResourceLocation STAR = AegisCommand.id("textures/entity/fx_star.png");
    public static final ResourceLocation RING = AegisCommand.id("textures/entity/fx_ring.png");
    public static final ResourceLocation STREAK = AegisCommand.id("textures/entity/fx_streak.png");
    public static final ResourceLocation PANEL = AegisCommand.id("textures/item/weapon_panel.png");
    public static final ResourceLocation EMISSIVE = AegisCommand.id("textures/item/weapon_emissive.png");
    public static final int FULL_BRIGHT = 0xF000F0;

    private static final int DARK_RGB = 0x2C3037, BLACK_RGB = 0x15171B, CHROME_RGB = 0xC9D0D9, GRIP_RGB = 0x232427;

    public static WeaponRenderer get() {
        if (instance == null) {
            Minecraft mc = Minecraft.getInstance();
            instance = new WeaponRenderer(mc);
        }
        return instance;
    }

    private WeaponRenderer(Minecraft mc) {
        super(mc.getBlockEntityRenderDispatcher(), mc.getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        WeaponType type = AegisWeaponItem.typeOf(stack);
        if (type == null) return;
        boolean firstPerson = ctx.firstPerson();
        WeaponAnimator.Pose pose = (firstPerson ? WeaponAnimator.HAND : WeaponAnimator.STATIC).pose(type, firstPerson);
        render(type, AegisWeaponItem.finish(stack), AegisWeaponItem.energy(stack), pose, ps, buffers, light, overlay);
    }

    private static float[] rgb(int c, float mul) {
        return new float[]{Math.min(1, ((c >> 16) & 255) / 255f * mul), Math.min(1, ((c >> 8) & 255) / 255f * mul), Math.min(1, (c & 255) / 255f * mul)};
    }

    public void render(WeaponType type, int finish, int energy, WeaponAnimator.Pose pose, PoseStack ps,
                       MultiBufferSource buffers, int light, int overlay) {
        ps.pushPose();
        ps.translate(0.5, 0.5, 0.5);
        ps.translate(pose.tx, pose.ty, pose.tz);
        ps.mulPose(Axis.YP.rotationDegrees(pose.ry));
        ps.mulPose(Axis.XP.rotationDegrees(pose.rx));
        ps.mulPose(Axis.ZP.rotationDegrees(pose.rz));
        ps.translate(-0.5, -0.5, -0.5);
        ps.scale(1 / 16f, 1 / 16f, 1 / 16f);

        int fc = WeaponType.FINISH_COLORS[Math.floorMod(finish, WeaponType.FINISH_COLORS.length)];
        int ec = WeaponType.ENERGY_COLORS[Math.floorMod(energy, WeaponType.ENERGY_COLORS.length)];
        float[] e = rgb(ec, 1f);
        float g = Mth.clamp(pose.glow + pose.coreFlash * 0.8f, 0.05f, 2.2f);
        float lvl = Math.min(1f, 0.4f + 0.6f * Math.min(1f, g));

        WMesh mesh = WeaponModels.get(type);
        VertexConsumer body = buffers.getBuffer(RenderType.entityCutoutNoCull(PANEL));
        for (int grp = 0; grp < GROUPS; grp++) {
            ps.pushPose();
            groupTransform(type, grp, pose, ps);
            // opaque materials: colour, specular strength, shininess, rim
            mat(mesh, ps, body, grp, PAINT, rgb(fc, 1.05f), 0.42f, 18, ec, 0.32f, light, overlay);
            mat(mesh, ps, body, grp, ACCENT, rgb(type.accentColor, 1f), 0.45f, 18, ec, 0.32f, light, overlay);
            mat(mesh, ps, body, grp, DARK, rgb(DARK_RGB, 1f), 0.26f, 14, ec, 0.25f, light, overlay);
            mat(mesh, ps, body, grp, BLACK, rgb(BLACK_RGB, 1f), 0.16f, 10, ec, 0.18f, light, overlay);
            mat(mesh, ps, body, grp, CHROME, rgb(CHROME_RGB, 1f), 0.95f, 9, ec, 0.45f, light, overlay);
            mat(mesh, ps, body, grp, GRIP, rgb(GRIP_RGB, 1f), 0.05f, 4, ec, 0.08f, light, overlay);
            mat(mesh, ps, body, grp, TRIM, rgb(ec, 0.55f), 0.3f, 12, ec, 0.2f, light, overlay);
            ps.popPose();
        }
        // energy layer: full-bright tubes + additive bloom, white-hot cores, holographic glass
        VertexConsumer glow = buffers.getBuffer(RenderType.entityTranslucentEmissive(EMISSIVE));
        float hot = Math.min(1, 0.55f + 0.45f * g);
        for (int grp = 0; grp < GROUPS; grp++) {
            ps.pushPose();
            groupTransform(type, grp, pose, ps);
            mesh.emit(ps, glow, grp, GLOW, 1, Math.min(1, e[0] * lvl + 0.12f * g), Math.min(1, e[1] * lvl + 0.12f * g), Math.min(1, e[2] * lvl + 0.12f * g), 1f,
                    0, 1, 0, 0, FULL_BRIGHT, overlay);
            mesh.emit(ps, glow, grp, HOT, 1, Math.min(1, e[0] * 0.4f + 0.6f * hot), Math.min(1, e[1] * 0.4f + 0.6f * hot), Math.min(1, e[2] * 0.4f + 0.6f * hot), 1f,
                    0, 1, 0, 0, FULL_BRIGHT, overlay);
            ps.popPose();
        }
        VertexConsumer lens = buffers.getBuffer(RenderType.entityTranslucentEmissive(EMISSIVE));
        for (int grp = 0; grp < GROUPS; grp++) {
            ps.pushPose();
            groupTransform(type, grp, pose, ps);
            mesh.emit(ps, lens, grp, LENS, 1, e[0] * 0.55f + 0.1f, e[1] * 0.55f + 0.1f, e[2] * 0.55f + 0.1f, 0.3f,
                    0, 1, 0, 0, FULL_BRIGHT, overlay);
            ps.popPose();
        }
        float bloom = Mth.clamp((g - 0.35f) * 0.75f, 0, 1);
        if (bloom > 0.01f) {
            VertexConsumer add = buffers.getBuffer(RenderType.eyes(EMISSIVE));
            for (int grp = 0; grp < GROUPS; grp++) {
                ps.pushPose();
                groupTransform(type, grp, pose, ps);
                mesh.emit(ps, add, grp, GLOW, 1, e[0] * bloom, e[1] * bloom, e[2] * bloom, 1f, 0, 1, 0, 0, FULL_BRIGHT, overlay);
                mesh.emit(ps, add, grp, HOT, 1, bloom * 0.6f, bloom * 0.6f, bloom * 0.6f, 1f, 0, 1, 0, 0, FULL_BRIGHT, overlay);
                ps.popPose();
            }
        }

        // muzzle flash and core flare (pixel space)
        if (pose.flash > 0.01f) muzzleFlash(type, ps, buffers, e, pose.flash);
        if (pose.coreFlash > 0.01f || pose.charge > 0.05f) {
            float[] c = type.core;
            float k = Math.max(pose.coreFlash, pose.charge);
            sprite(ps, buffers, GLOW_TEX, c[0], c[1], c[2], 4f + 5f * k, e, k * 0.9f, 3, 0);
        }
        ps.popPose();
    }

    private static void mat(WMesh mesh, PoseStack ps, VertexConsumer vc, int grp, int m, float[] c, float spec, float shin,
                            int rim, float rimAmt, int light, int overlay) {
        mesh.emit(ps, vc, grp, m, 1, c[0], c[1], c[2], 1f, spec, shin, rim, rimAmt, light, overlay);
    }

    /** Pose a part group in pixel space. */
    private static void groupTransform(WeaponType t, int grp, WeaponAnimator.Pose p, PoseStack ps) {
        switch (grp) {
            case MAG -> {
                float k = p.magOut;
                if (k <= 0.001f) return;
                if (t == WeaponType.HELIX_4) {
                    ps.translate(0, 0, 3.2f * k);                       // pump racks back
                } else if (t == WeaponType.TITAN_X) {
                    ps.translate(0, -1.0f * k, 5.0f * k);              // rear cell slides out
                    ps.mulPose(Axis.XP.rotationDegrees(-12 * k));
                } else {
                    ps.translate(0, -6.5f * k, 1.2f * k);              // cell drops out
                    ps.translate(8, 6, 3);
                    ps.mulPose(Axis.XP.rotationDegrees(-22 * k));
                    ps.mulPose(Axis.ZP.rotationDegrees(10 * k));
                    ps.translate(-8, -6, -3);
                }
            }
            case SPIN -> {
                ps.translate(8, t.spinY, 0);
                ps.mulPose(Axis.ZP.rotationDegrees(p.spin));
                ps.translate(-8, -t.spinY, 0);
            }
            case AUX -> {
                ps.translate(8, t.muzzle[1], 0);
                ps.mulPose(Axis.ZP.rotationDegrees(p.auxSpin));
                ps.translate(-8, -t.muzzle[1], 0);
            }
            default -> {}
        }
    }

    /** Layered muzzle flash: star burst, forward plume, expanding ring and a white-hot core. */
    private static void muzzleFlash(WeaponType t, PoseStack ps, MultiBufferSource buffers, float[] e, float f) {
        float[] m = t.muzzle;
        float big = t.heavy ? 2.1f : t == WeaponType.HELIX_4 ? 1.5f : t == WeaponType.SPECTRE_7 ? 0.8f : 1f;
        float spin = (System.currentTimeMillis() % 100000) / 7f;
        // star burst (three crossed planes)
        sprite(ps, buffers, STAR, m[0], m[1], m[2] - 0.6f, (5f + 4f * f) * big, e, f, 3, spin);
        sprite(ps, buffers, GLOW_TEX, m[0], m[1], m[2] - 0.3f, (6f + 3f * f) * big, e, f * 0.9f, 3, 0);
        // forward plume
        ps.pushPose();
        ps.translate(m[0], m[1], m[2] - 4.5f * big);
        VertexConsumer vc = buffers.getBuffer(RenderType.eyes(STREAK));
        for (int i = 0; i < 2; i++) {
            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees(90));
            ps.mulPose(Axis.XP.rotationDegrees(i * 90));
            quad(ps, vc, 5.5f * big * (0.6f + 0.4f * f), 1.3f * big, e[0] * f, e[1] * f, e[2] * f);
            ps.popPose();
        }
        ps.popPose();
        // expanding ring perpendicular to the bore
        float grow = 1 - f;
        ps.pushPose();
        ps.translate(m[0], m[1], m[2] - 1.2f - grow * 2.5f);
        VertexConsumer rv = buffers.getBuffer(RenderType.eyes(RING));
        float rs = (1.5f + grow * 6f) * big;
        quad(ps, rv, rs, rs, e[0] * f, e[1] * f, e[2] * f);
        ps.popPose();
        // white-hot centre
        sprite(ps, buffers, GLOW_TEX, m[0], m[1], m[2] - 0.2f, 2.2f * big, new float[]{1, 1, 1}, f, 2, 0);
    }

    /** Crossed additive sprite planes centred on a point (pixel space). */
    private static void sprite(PoseStack ps, MultiBufferSource buffers, ResourceLocation tex, float x, float y, float z, float s,
                               float[] c, float a, int planes, float spin) {
        VertexConsumer vc = buffers.getBuffer(RenderType.eyes(tex));
        ps.pushPose();
        ps.translate(x, y, z);
        for (int i = 0; i < planes; i++) {
            ps.pushPose();
            if (i == 1) ps.mulPose(Axis.YP.rotationDegrees(90));
            if (i == 2) ps.mulPose(Axis.XP.rotationDegrees(90));
            ps.mulPose(Axis.ZP.rotationDegrees(spin + i * 30));
            quad(ps, vc, s, s, c[0] * a, c[1] * a, c[2] * a);
            ps.popPose();
        }
        ps.popPose();
    }

    public static void quad(PoseStack ps, VertexConsumer vc, float w, float h, float r, float g, float b) {
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        vertex(vc, m, n, -w, -h, 0, 0, r, g, b);
        vertex(vc, m, n, w, -h, 1, 0, r, g, b);
        vertex(vc, m, n, w, h, 1, 1, r, g, b);
        vertex(vc, m, n, -w, h, 0, 1, r, g, b);
        // back face, so additive sprites stay visible from every angle under back-face culling
        vertex(vc, m, n, -w, h, 0, 1, r, g, b);
        vertex(vc, m, n, w, h, 1, 1, r, g, b);
        vertex(vc, m, n, w, -h, 1, 0, r, g, b);
        vertex(vc, m, n, -w, -h, 0, 0, r, g, b);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float u, float v, float r, float g, float b) {
        vc.vertex(m, x, y, 0).color(Mth.clamp(r, 0, 1), Mth.clamp(g, 0, 1), Mth.clamp(b, 0, 1), 1f)
                .uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(FULL_BRIGHT).normal(n, 0, 0, 1).endVertex();
    }
}
