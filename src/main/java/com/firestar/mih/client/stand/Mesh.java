package com.firestar.mih.client.stand;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Arrays;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Draws shaded boxes, discs and tubes for the Stand's hand-built model into an entity buffer, and collects a second,
 * additive "glow" layer (inflated shells, lit dials) to be flushed afterwards in one batch.
 */
public final class Mesh {
    private final VertexConsumer vc;
    private final int light;
    /** Multiplies every alpha: the Stand fading in and out. */
    public float alpha = 1.0F;
    /** Multiplies every glow alpha. */
    public float glow = 1.0F;
    /** Thickness of the anime ink outline (inverted hull) round every smooth form; 0 for none. */
    public float ink = 0.0F;
    private float[] o = new float[8192];
    private int on;
    private float[] g = new float[4096];
    private int gn;

    public Mesh(VertexConsumer vc, int light) {
        this.vc = vc;
        this.light = light;
    }

    private static float r(int c) {
        return (c >> 16 & 255) / 255.0F;
    }

    private static float gr(int c) {
        return (c >> 8 & 255) / 255.0F;
    }

    private static float b(int c) {
        return (c & 255) / 255.0F;
    }

    private void v(Matrix4f m, Matrix3f n, float x, float y, float z, float cr, float cg, float cb, float a, float nx, float ny, float nz, float u, float w) {
        vc.vertex(m, x, y, z).color(cr, cg, cb, a).uv(u, w).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, nx, ny, nz).endVertex();
    }

    /** A box with per-face shading; {@code glowA} > 0 also lays a soft additive shell round it. */
    public void box(PoseStack ps, float x0, float y0, float z0, float x1, float y1, float z1, int rgb, float glowA) {
        float a = alpha;
        if (a > 0.004F) {
            Matrix4f m = ps.last().pose();
            Matrix3f n = ps.last().normal();
            float cr = r(rgb), cg = gr(rgb), cb = b(rgb);
            // +y
            v(m, n, x0, y1, z0, cr, cg, cb, a, 0, 1, 0, 0, 0);
            v(m, n, x0, y1, z1, cr, cg, cb, a, 0, 1, 0, 0, 1);
            v(m, n, x1, y1, z1, cr, cg, cb, a, 0, 1, 0, 1, 1);
            v(m, n, x1, y1, z0, cr, cg, cb, a, 0, 1, 0, 1, 0);
            // -y
            v(m, n, x0, y0, z0, cr, cg, cb, a, 0, -1, 0, 0, 0);
            v(m, n, x1, y0, z0, cr, cg, cb, a, 0, -1, 0, 1, 0);
            v(m, n, x1, y0, z1, cr, cg, cb, a, 0, -1, 0, 1, 1);
            v(m, n, x0, y0, z1, cr, cg, cb, a, 0, -1, 0, 0, 1);
            // +z
            v(m, n, x0, y0, z1, cr, cg, cb, a, 0, 0, 1, 0, 0);
            v(m, n, x1, y0, z1, cr, cg, cb, a, 0, 0, 1, 1, 0);
            v(m, n, x1, y1, z1, cr, cg, cb, a, 0, 0, 1, 1, 1);
            v(m, n, x0, y1, z1, cr, cg, cb, a, 0, 0, 1, 0, 1);
            // -z
            v(m, n, x0, y0, z0, cr, cg, cb, a, 0, 0, -1, 0, 0);
            v(m, n, x0, y1, z0, cr, cg, cb, a, 0, 0, -1, 0, 1);
            v(m, n, x1, y1, z0, cr, cg, cb, a, 0, 0, -1, 1, 1);
            v(m, n, x1, y0, z0, cr, cg, cb, a, 0, 0, -1, 1, 0);
            // +x
            v(m, n, x1, y0, z0, cr, cg, cb, a, 1, 0, 0, 0, 0);
            v(m, n, x1, y1, z0, cr, cg, cb, a, 1, 0, 0, 0, 1);
            v(m, n, x1, y1, z1, cr, cg, cb, a, 1, 0, 0, 1, 1);
            v(m, n, x1, y0, z1, cr, cg, cb, a, 1, 0, 0, 1, 0);
            // -x
            v(m, n, x0, y0, z0, cr, cg, cb, a, -1, 0, 0, 0, 0);
            v(m, n, x0, y0, z1, cr, cg, cb, a, -1, 0, 0, 1, 0);
            v(m, n, x0, y1, z1, cr, cg, cb, a, -1, 0, 0, 1, 1);
            v(m, n, x0, y1, z0, cr, cg, cb, a, -1, 0, 0, 0, 1);
        }
        if (glowA > 0.0F) {
            float ix = (x1 - x0) * 0.09F + 0.02F, iy = (y1 - y0) * 0.09F + 0.02F, iz = (z1 - z0) * 0.09F + 0.02F;
            glowBox(ps, x0 - ix, y0 - iy, z0 - iz, x1 + ix, y1 + iy, z1 + iz, 0x7CFFD4, glowA);
        }
    }

    /** A disc of radius {@code rad} in the local XY plane at depth z, facing +z (both sides drawn). */
    public void disc(PoseStack ps, float cx, float cy, float z, float rad, int segs, int rgb) {
        if (alpha <= 0.004F) {
            return;
        }
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        float cr = r(rgb), cg = gr(rgb), cb = b(rgb), a = alpha;
        for (int i = 0; i < segs; i++) {
            double a0 = i * Math.PI * 2.0 / segs, a1 = (i + 1) * Math.PI * 2.0 / segs;
            float x0 = cx + (float) Math.cos(a0) * rad, y0 = cy + (float) Math.sin(a0) * rad;
            float x1 = cx + (float) Math.cos(a1) * rad, y1 = cy + (float) Math.sin(a1) * rad;
            v(m, n, cx, cy, z, cr, cg, cb, a, 0, 0, 1, 0.5F, 0.5F);
            v(m, n, x0, y0, z, cr, cg, cb, a, 0, 0, 1, 0, 0);
            v(m, n, x1, y1, z, cr, cg, cb, a, 0, 0, 1, 1, 0);
            v(m, n, x1, y1, z, cr, cg, cb, a, 0, 0, 1, 1, 0);
            v(m, n, cx, cy, z, cr, cg, cb, a, 0, 0, -1, 0.5F, 0.5F);
            v(m, n, x1, y1, z, cr, cg, cb, a, 0, 0, -1, 1, 0);
            v(m, n, x0, y0, z, cr, cg, cb, a, 0, 0, -1, 0, 0);
            v(m, n, x0, y0, z, cr, cg, cb, a, 0, 0, -1, 0, 0);
        }
    }

    /** A flat ring (annulus) in the local XY plane at depth z. */
    public void ring(PoseStack ps, float cx, float cy, float z, float r0, float r1, int segs, int rgb) {
        if (alpha <= 0.004F) {
            return;
        }
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        float cr = r(rgb), cg = gr(rgb), cb = b(rgb), a = alpha;
        for (int i = 0; i < segs; i++) {
            double a0 = i * Math.PI * 2.0 / segs, a1 = (i + 1) * Math.PI * 2.0 / segs;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            v(m, n, cx + c0 * r0, cy + s0 * r0, z, cr, cg, cb, a, 0, 0, 1, 0, 0);
            v(m, n, cx + c0 * r1, cy + s0 * r1, z, cr, cg, cb, a, 0, 0, 1, 0, 1);
            v(m, n, cx + c1 * r1, cy + s1 * r1, z, cr, cg, cb, a, 0, 0, 1, 1, 1);
            v(m, n, cx + c1 * r0, cy + s1 * r0, z, cr, cg, cb, a, 0, 0, 1, 1, 0);
            v(m, n, cx + c1 * r0, cy + s1 * r0, z, cr, cg, cb, a, 0, 0, -1, 1, 0);
            v(m, n, cx + c1 * r1, cy + s1 * r1, z, cr, cg, cb, a, 0, 0, -1, 1, 1);
            v(m, n, cx + c0 * r1, cy + s0 * r1, z, cr, cg, cb, a, 0, 0, -1, 0, 1);
            v(m, n, cx + c0 * r0, cy + s0 * r0, z, cr, cg, cb, a, 0, 0, -1, 0, 0);
        }
    }

    /**
     * A clock face in the local XY plane facing +z: gold rim, white face, twelve marks, and two hands at the given
     * angles (degrees, clockwise from 12). It glows faintly so it reads from a distance.
     */
    public void clock(PoseStack ps, float rad, float hourDeg, float minuteDeg, float glowA) {
        ring(ps, 0, 0, 0.0F, rad * 0.84F, rad, 24, 0xE8BE48);
        disc(ps, 0, 0, -0.004F, rad * 0.86F, 20, 0xFFFDF5);
        for (int i = 0; i < 12; i++) {
            ps.pushPose();
            ps.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-i * 30.0F));
            float w = i % 3 == 0 ? 0.012F : 0.007F;
            box(ps, -w * rad * 6, rad * 0.62F, 0.004F, w * rad * 6, rad * 0.78F, 0.010F, 0x1A1A1E, 0.0F);
            ps.popPose();
        }
        hand(ps, rad * 0.45F, rad * 0.05F, hourDeg, 0x15151A);
        hand(ps, rad * 0.7F, rad * 0.035F, minuteDeg, 0x15151A);
        disc(ps, 0, 0, 0.016F, rad * 0.07F, 8, 0xE8BE48);
        if (glowA > 0.0F) {
            glowDisc(ps, 0, 0, 0.02F, rad * 1.05F, 0xFFE9A8, glowA * 0.35F);
        }
    }

    private void hand(PoseStack ps, float len, float w, float deg, int rgb) {
        ps.pushPose();
        ps.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-deg));
        box(ps, -w, -len * 0.15F, 0.008F, w, len, 0.014F, rgb, 0.0F);
        ps.popPose();
    }

    /** A tube through camera-space points (already transformed), drawn with an identity pose. */
    public void tube(PoseStack identity, Vector3f[] pts, float rad, int rgb) {
        if (alpha <= 0.004F || pts.length < 2) {
            return;
        }
        Matrix4f m = identity.last().pose();
        Matrix3f n = identity.last().normal();
        float cr = r(rgb), cg = gr(rgb), cb = b(rgb), a = alpha;
        int sides = 6;
        Vector3f[] prevRing = null;
        Vector3f[] prevNorm = null;
        for (int i = 0; i < pts.length; i++) {
            Vector3f t = new Vector3f(pts[Math.min(pts.length - 1, i + 1)]).sub(pts[Math.max(0, i - 1)]).normalize();
            Vector3f u = Math.abs(t.y) > 0.9F ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0);
            Vector3f s1 = new Vector3f(t).cross(u).normalize();
            Vector3f s2 = new Vector3f(t).cross(s1).normalize();
            Vector3f[] ring = new Vector3f[sides];
            Vector3f[] norm = new Vector3f[sides];
            for (int k = 0; k < sides; k++) {
                double ang = k * Math.PI * 2.0 / sides;
                Vector3f d = new Vector3f(s1).mul((float) Math.cos(ang)).add(new Vector3f(s2).mul((float) Math.sin(ang)));
                norm[k] = d;
                ring[k] = new Vector3f(d).mul(rad).add(pts[i]);
            }
            if (prevRing != null) {
                for (int k = 0; k < sides; k++) {
                    int k1 = (k + 1) % sides;
                    v(m, n, prevRing[k].x, prevRing[k].y, prevRing[k].z, cr, cg, cb, a, prevNorm[k].x, prevNorm[k].y, prevNorm[k].z, 0, 0);
                    v(m, n, ring[k].x, ring[k].y, ring[k].z, cr, cg, cb, a, norm[k].x, norm[k].y, norm[k].z, 0, 1);
                    v(m, n, ring[k1].x, ring[k1].y, ring[k1].z, cr, cg, cb, a, norm[k1].x, norm[k1].y, norm[k1].z, 1, 1);
                    v(m, n, prevRing[k1].x, prevRing[k1].y, prevRing[k1].z, cr, cg, cb, a, prevNorm[k1].x, prevNorm[k1].y, prevNorm[k1].z, 1, 0);
                }
            }
            prevRing = ring;
            prevNorm = norm;
        }
    }


    // ------------------------------------------------------------------------------------------------ smooth forms

    private static final int LAT = 10, LON = 16;
    private static final float[][] UNIT = new float[(LAT + 1) * (LON + 1)][];

    static {
        for (int i = 0; i <= LAT; i++) {
            double th = Math.PI * i / LAT - Math.PI / 2;
            for (int j = 0; j <= LON; j++) {
                double ph = Math.PI * 2 * j / LON;
                UNIT[i * (LON + 1) + j] = new float[]{(float) (Math.cos(th) * Math.cos(ph)), (float) Math.sin(th), (float) (Math.cos(th) * Math.sin(ph))};
            }
        }
    }

    /**
     * A smooth ellipsoid with true normals, so the Stand reads as sculpted rather than built from blocks. {@code rim} > 0
     * also lays a fresnel aura round it: brightest at the silhouette, clear where it faces the camera.
     */
    public void ellipsoid(PoseStack ps, float cx, float cy, float cz, float rx, float ry, float rz, int rgb, float rim) {
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        float cr = r(rgb), cg = gr(rgb), cb = b(rgb), a = alpha;
        if (a > 0.004F) {
            for (int i = 0; i < LAT; i++) {
                for (int j = 0; j < LON; j++) {
                    int[] idx = {i * (LON + 1) + j, (i + 1) * (LON + 1) + j, (i + 1) * (LON + 1) + j + 1, i * (LON + 1) + j + 1};
                    for (int k : idx) {
                        float[] u = UNIT[k];
                        float nx = u[0] / rx, ny = u[1] / ry, nz = u[2] / rz;
                        float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                        // two-tone cel shading baked in: lit from above and in front, a soft core shadow below
                        float key = 0.55F * u[1] + 0.35F * u[2] + 0.1F * u[0];
                        float lit = key > -0.05F ? 1.0F : 0.74F;
                        lit *= 0.9F + 0.1F * u[1];
                        v(m, n, cx + u[0] * rx, cy + u[1] * ry, cz + u[2] * rz, cr * lit, cg * lit, cb * lit, a, nx / l, ny / l, nz / l, 0.5F, 0.5F);
                    }
                }
            }
        }
        if (ink > 0.0F && alpha > 0.9F && Math.min(rx, Math.min(ry, rz)) > 0.02F) {
            Vector3f p = new Vector3f(), nn = new Vector3f();
            for (int i = 0; i < LAT; i++) {
                for (int j = 0; j < LON; j++) {
                    int[] idx = {i * (LON + 1) + j + 1, (i + 1) * (LON + 1) + j + 1, (i + 1) * (LON + 1) + j, i * (LON + 1) + j};
                    for (int k : idx) {
                        float[] u = UNIT[k];
                        m.transformPosition(cx + u[0] * (rx + ink), cy + u[1] * (ry + ink), cz + u[2] * (rz + ink), p);
                        n.transform(u[0], u[1], u[2], nn).normalize();
                        if (on + 6 > o.length) {
                            o = Arrays.copyOf(o, o.length * 2);
                        }
                        o[on++] = p.x;
                        o[on++] = p.y;
                        o[on++] = p.z;
                        o[on++] = nn.x;
                        o[on++] = nn.y;
                        o[on++] = nn.z;
                    }
                }
            }
        }
        if (rim > 0.0F) {
            float ga = rim * glow * alpha;
            if (ga <= 0.003F) {
                return;
            }
            Vector3f p = new Vector3f(), nn = new Vector3f();
            float s = 1.12F;
            float gr0 = 0.49F, gg0 = 1.0F, gb0 = 0.83F;
            for (int i = 0; i < LAT; i++) {
                for (int j = 0; j < LON; j++) {
                    int[] idx = {i * (LON + 1) + j, (i + 1) * (LON + 1) + j, (i + 1) * (LON + 1) + j + 1, i * (LON + 1) + j + 1};
                    for (int k : idx) {
                        float[] u = UNIT[k];
                        m.transformPosition(cx + u[0] * rx * s, cy + u[1] * ry * s, cz + u[2] * rz * s, p);
                        n.transform(u[0] / rx, u[1] / ry, u[2] / rz, nn).normalize();
                        float facing = Math.abs(nn.dot(new Vector3f(p).normalize()));
                        float al = ga * (float) Math.pow(1.0F - facing, 2.2);
                        raw(p.x, p.y, p.z, gr0, gg0, gb0, al);
                    }
                }
            }
        }
    }

    /** A smooth limb from a to b (local space) with end radii ra, rb: an ellipsoid stretched along the bone. */
    public void limb(PoseStack ps, float ax, float ay, float az, float bx, float by, float bz, float ra, float rb, int rgb, float rim) {
        float dx = bx - ax, dy = by - ay, dz = bz - az;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        ps.pushPose();
        ps.translate((ax + bx) / 2, (ay + by) / 2, (az + bz) / 2);
        Vector3f dir = new Vector3f(dx, dy, dz).normalize();
        ps.mulPose(new org.joml.Quaternionf().rotationTo(new Vector3f(0, 1, 0), dir));
        float r = (ra + rb) / 2;
        ellipsoid(ps, 0, 0, 0, r, len / 2 + r * 0.6F, r, rgb, rim);
        ps.popPose();
    }

    private void raw(float x, float y, float z, float cr, float cg, float cb, float a) {
        if (gn + 7 > g.length) {
            g = Arrays.copyOf(g, g.length * 2);
        }
        g[gn++] = x;
        g[gn++] = y;
        g[gn++] = z;
        g[gn++] = cr;
        g[gn++] = cg;
        g[gn++] = cb;
        g[gn++] = a;
    }

    // ------------------------------------------------------------------------------------------------ glow layer

    private void gv(Matrix4f m, float x, float y, float z, float cr, float cg, float cb, float a) {
        if (gn + 7 > g.length) {
            g = Arrays.copyOf(g, g.length * 2);
        }
        Vector3f p = m.transformPosition(x, y, z, new Vector3f());
        g[gn++] = p.x;
        g[gn++] = p.y;
        g[gn++] = p.z;
        g[gn++] = cr;
        g[gn++] = cg;
        g[gn++] = cb;
        g[gn++] = a;
    }

    public void glowBox(PoseStack ps, float x0, float y0, float z0, float x1, float y1, float z1, int rgb, float a) {
        a *= glow * alpha;
        if (a <= 0.003F) {
            return;
        }
        Matrix4f m = ps.last().pose();
        float cr = r(rgb), cg = gr(rgb), cb = b(rgb);
        float[][] f = {
                {x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0}, {x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1},
                {x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1}, {x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0},
                {x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1}, {x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0}};
        for (float[] q : f) {
            for (int i = 0; i < 12; i += 3) {
                gv(m, q[i], q[i + 1], q[i + 2], cr, cg, cb, a);
            }
        }
    }

    public void glowDisc(PoseStack ps, float cx, float cy, float z, float rad, int rgb, float a) {
        a *= glow * alpha;
        if (a <= 0.003F) {
            return;
        }
        Matrix4f m = ps.last().pose();
        float cr = r(rgb), cg = gr(rgb), cb = b(rgb);
        int segs = 16;
        for (int i = 0; i < segs; i++) {
            double a0 = i * Math.PI * 2.0 / segs, a1 = (i + 1) * Math.PI * 2.0 / segs;
            gv(m, cx, cy, z, cr, cg, cb, a);
            gv(m, cx + (float) Math.cos(a0) * rad, cy + (float) Math.sin(a0) * rad, z, cr, cg, cb, 0.0F);
            gv(m, cx + (float) Math.cos(a1) * rad, cy + (float) Math.sin(a1) * rad, z, cr, cg, cb, 0.0F);
            gv(m, cx, cy, z, cr, cg, cb, a);
        }
    }

    /** A camera-space quad strip point (already transformed) for streaks: used by afterimage arms and trails. */
    public void glowQuadRaw(Vector3f a0, Vector3f a1, Vector3f b1, Vector3f b0, int rgb, float alphaA, float alphaB) {
        float cr = r(rgb), cg = gr(rgb), cb = b(rgb);
        Matrix4f id = new Matrix4f();
        float k = glow * alpha;
        gv(id, a0.x, a0.y, a0.z, cr, cg, cb, alphaA * k);
        gv(id, a1.x, a1.y, a1.z, cr, cg, cb, alphaA * k);
        gv(id, b1.x, b1.y, b1.z, cr, cg, cb, alphaB * k);
        gv(id, b0.x, b0.y, b0.z, cr, cg, cb, alphaB * k);
    }

    /** Writes the glow layer (camera-space, additive) with an identity pose; colour is pre-multiplied by alpha. */
    public void flushGlow(PoseStack identity, VertexConsumer lightning) {
        Matrix4f m = identity.last().pose();
        for (int i = 0; i < gn; i += 7) {
            float a = Math.min(1.0F, g[i + 6]);
            lightning.vertex(m, g[i], g[i + 1], g[i + 2]).color(g[i + 3] * a, g[i + 4] * a, g[i + 5] * a, a).endVertex();
        }
        gn = 0;
    }

    /** Writes the ink outline (camera-space, back faces only through a culled entity type). */
    public void flushInk(VertexConsumer cutout) {
        for (int i = 0; i < on; i += 6) {
            cutout.vertex(o[i], o[i + 1], o[i + 2]).color(0.06F, 0.05F, 0.08F, 1.0F).uv(0.5F, 0.5F).overlayCoords(OverlayTexture.NO_OVERLAY)
                    .uv2(light).normal(o[i + 3], o[i + 4], o[i + 5]).endVertex();
        }
        on = 0;
    }

    public boolean hasInk() {
        return on > 0;
    }

    public boolean hasGlow() {
        return gn > 0;
    }
}
