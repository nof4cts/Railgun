package com.firestar.sage.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import org.joml.Matrix4f;

/**
 * The Nullstaff in the sage's right hand: a black shaft ringed in gold at the grip, the middle and the head, a gold
 * crescent cradling a sphere of night at the top. Built from plain coloured geometry; the gold glows.
 */
public final class StaffLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public StaffLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack ps, MultiBufferSource buf, int light, AbstractClientPlayer p, float limb, float limbAmt, float partial, float age,
                       float yaw, float pitch) {
        float m = SageForms.mantle(p, partial);
        if (m < 0.05F || !p.getMainHandItem().isEmpty() || p.isInvisible()) {
            return;
        }
        ps.pushPose();
        getParentModel().rightArm.translateAndRotate(ps);
        // the staff crosses the fist like any held item, along the hand's forward: with the arm raised forward it stands
        // upright, lowered it levels into a thrust, raised overhead it lies back over the shoulder
        ps.translate(-0.0625F, 0.62F, -0.06F);
        draw(ps.last().pose(), buf, m);
        ps.popPose();
    }

    /** The staff along -z (head at -2.0, butt at +0.55), in whatever frame {@code m} gives. */
    public static void draw(Matrix4f m, MultiBufferSource buf, float k) {
        VertexConsumer dark = buf.getBuffer(RenderType.debugQuads());
        VertexConsumer glow = buf.getBuffer(RenderType.lightning());
        float head = -2.0F, butt = 0.55F;
        float top = butt + (head - butt) * k;
        rod(dark, m, 0.066F, butt, top, 0xFF0A0710);
        if (k > 0.9F) {
            for (float z : new float[]{0.0F, -0.9F, -1.7F}) {
                rod(glow, m, 0.085F, z + 0.06F, z - 0.06F, 0xF0F2C14E);
            }
            rod(glow, m, 0.08F, butt + 0.02F, butt - 0.1F, 0xF0F2C14E);
            // the head: a gold crescent around a black orb
            sphere(dark, m, 0, 0, head - 0.18F, 0.13F, 0xFF050308);
            for (int i = 0; i < 18; i++) {
                double a0 = Math.PI * (0.15 + 1.7 * i / 18.0), a1 = Math.PI * (0.15 + 1.7 * (i + 1) / 18.0);
                float r = 0.24F, w = 0.025F;
                float y0 = (float) Math.cos(a0) * r, z0 = head - 0.18F + (float) Math.sin(a0) * r;
                float y1 = (float) Math.cos(a1) * r, z1 = head - 0.18F + (float) Math.sin(a1) * r;
                quad(glow, m, -w, y0, z0, w, y0, z0, w, y1, z1, -w, y1, z1, 0xF0F2C14E);
                quad(glow, m, 0, y0 - w, z0, 0, y0 + w, z0, 0, y1 + w, z1, 0, y1 - w, z1, 0xF0F2C14E);
            }
        }
    }

    private static void rod(VertexConsumer vc, Matrix4f m, float r, float z0, float z1, int col) {
        int n = 8;
        for (int i = 0; i < n; i++) {
            double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
            float x0 = (float) Math.cos(a0) * r, y0 = (float) Math.sin(a0) * r, x1 = (float) Math.cos(a1) * r, y1 = (float) Math.sin(a1) * r;
            quad(vc, m, x0, y0, z0, x1, y1, z0, x1, y1, z1, x0, y0, z1, col);
        }
    }

    private static void sphere(VertexConsumer vc, Matrix4f m, float cx, float cy, float cz, float r, int col) {
        int lat = 6, lon = 10;
        for (int i = 0; i < lat; i++) {
            double t0 = Math.PI * i / lat, t1 = Math.PI * (i + 1) / lat;
            for (int j = 0; j < lon; j++) {
                double p0 = Math.PI * 2 * j / lon, p1 = Math.PI * 2 * (j + 1) / lon;
                quad(vc, m, pt(cx, r, t0, p0, 0), pt(cy, r, t0, p0, 1), pt(cz, r, t0, p0, 2), pt(cx, r, t1, p0, 0), pt(cy, r, t1, p0, 1),
                        pt(cz, r, t1, p0, 2), pt(cx, r, t1, p1, 0), pt(cy, r, t1, p1, 1), pt(cz, r, t1, p1, 2), pt(cx, r, t0, p1, 0),
                        pt(cy, r, t0, p1, 1), pt(cz, r, t0, p1, 2), col);
            }
        }
    }

    private static float pt(float c, float r, double th, double ph, int axis) {
        return c + (float) (r * switch (axis) {
            case 0 -> Math.sin(th) * Math.cos(ph);
            case 1 -> Math.cos(th);
            default -> Math.sin(th) * Math.sin(ph);
        });
    }

    private static void quad(VertexConsumer vc, Matrix4f m, float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz,
                             float dx, float dy, float dz, int col) {
        int a = col >>> 24, r = (col >> 16) & 255, g = (col >> 8) & 255, b = col & 255;
        vc.vertex(m, ax, ay, az).color(r, g, b, a).endVertex();
        vc.vertex(m, bx, by, bz).color(r, g, b, a).endVertex();
        vc.vertex(m, cx, cy, cz).color(r, g, b, a).endVertex();
        vc.vertex(m, dx, dy, dz).color(r, g, b, a).endVertex();
        // and the back face, so it shows from every side whatever the render type culls
        vc.vertex(m, ax, ay, az).color(r, g, b, a).endVertex();
        vc.vertex(m, dx, dy, dz).color(r, g, b, a).endVertex();
        vc.vertex(m, cx, cy, cz).color(r, g, b, a).endVertex();
        vc.vertex(m, bx, by, bz).color(r, g, b, a).endVertex();
    }
}
