package com.firestar.sage.client.fx;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The sage's brush: everything he puts into the world is painted as plain coloured triangles, camera-relative, in two
 * layers. LIGHT is added onto the picture (gold, corona, fire: it glows); DARK is laid over it (the null orbs, the
 * black sun, the colossus's shadowed body). No textures and no post-processing: cheap, crisp, and steady at any frame
 * rate. Soft glows are built from geometry whose alpha falls to nothing at the edge.
 */
public final class Ink {
    private final BufferBuilder light = new BufferBuilder(1 << 16);
    private final BufferBuilder dark = new BufferBuilder(1 << 15);
    private Vec3 cam = Vec3.ZERO;
    /** Unit vectors of the camera: right, up, toward the viewer. */
    private Vec3 cr = new Vec3(1, 0, 0), cu = new Vec3(0, 1, 0), cb = new Vec3(0, 0, 1);
    private int lightTris, darkTris;

    public void begin(Vec3 camPos, Vec3 right, Vec3 up, Vec3 back) {
        cam = camPos;
        cr = right;
        cu = up;
        cb = back;
        light.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        dark.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        lightTris = darkTris = 0;
    }

    public Vec3 cam() {
        return cam;
    }

    public Vec3 camRight() {
        return cr;
    }

    public Vec3 camUp() {
        return cu;
    }

    /** Draws both layers: dark first (alpha-blended), then light (added). */
    public void end(PoseStack pose) {
        PoseStack mv = RenderSystem.getModelViewStack();
        mv.pushPose();
        mv.mulPoseMatrix(pose.last().pose());
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.depthMask(false);
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        BufferUploader.drawWithShader(dark.end());
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE, GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ONE);
        BufferUploader.drawWithShader(light.end());
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        mv.popPose();
        RenderSystem.applyModelViewMatrix();
    }

    public int triangles() {
        return lightTris + darkTris;
    }

    // ------------------------------------------------------------------------------------------------ colour

    public static int argb(float a, float r, float g, float b) {
        return (int) (Mth.clamp(a, 0, 1) * 255) << 24 | (int) (Mth.clamp(r, 0, 1) * 255) << 16 | (int) (Mth.clamp(g, 0, 1) * 255) << 8
                | (int) (Mth.clamp(b, 0, 1) * 255);
    }

    public static int alpha(int c, float a) {
        int A = (int) ((c >>> 24) * Mth.clamp(a, 0, 1));
        return A << 24 | (c & 0xFFFFFF);
    }

    /** The sage's palette. */
    public static final int GOLD = 0xFFF2C14E, PALE = 0xFFFFF1C8, WHITE = 0xFFFFFFFF, EMBER = 0xFFFF8A2A, VOID = 0xFF050308, DUSK = 0xFF2A1B3D;

    private void v(BufferBuilder b, double x, double y, double z, int c) {
        b.vertex(x - cam.x, y - cam.y, z - cam.z).color((c >> 16) & 255, (c >> 8) & 255, c & 255, c >>> 24).endVertex();
    }

    public void tri(boolean glow, Vec3 a, int ca, Vec3 b, int cb_, Vec3 c, int cc) {
        BufferBuilder buf = glow ? light : dark;
        v(buf, a.x, a.y, a.z, ca);
        v(buf, b.x, b.y, b.z, cb_);
        v(buf, c.x, c.y, c.z, cc);
        if (glow) {
            lightTris++;
        } else {
            darkTris++;
        }
    }

    public void quad(boolean glow, Vec3 a, int ca, Vec3 b, int cb_, Vec3 c, int cc, Vec3 d, int cd) {
        tri(glow, a, ca, b, cb_, c, cc);
        tri(glow, a, ca, c, cc, d, cd);
    }

    // ------------------------------------------------------------------------------------------------ shapes

    /**
     * Darkens everything already drawn (the world) by laying a sheet of {@code col} right in front of the eye. Call it
     * before any other shape, so every glow drawn after it burns against the dark.
     */
    public void veil(int col) {
        if ((col >>> 24) == 0) {
            return;
        }
        Vec3 c = cam.subtract(cb.scale(0.12));
        Vec3 r = cr.scale(3), u = cu.scale(3);
        quad(false, c.subtract(r).subtract(u), col, c.add(r).subtract(u), col, c.add(r).add(u), col, c.subtract(r).add(u), col);
    }

    /** A ring of light facing the eye: full {@code col} at radius {@code r0}, fading to nothing at {@code r1}. */
    public void corona(Vec3 c, double r0, double r1, int col) {
        int n = 48;
        int edge = col & 0xFFFFFF;
        for (int i = 0; i < n; i++) {
            double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
            Vec3 d0 = cr.scale(Math.cos(a0)).add(cu.scale(Math.sin(a0))), d1 = cr.scale(Math.cos(a1)).add(cu.scale(Math.sin(a1)));
            quad(true, c.add(d0.scale(r0)), col, c.add(d1.scale(r0)), col, c.add(d1.scale(r1)), edge, c.add(d0.scale(r1)), edge);
        }
    }

    /** A soft round glow facing the camera: full colour at the centre, nothing at radius {@code r}. */
    public void glow(Vec3 c, double r, int col) {
        if (r <= 0 || (col >>> 24) == 0) {
            return;
        }
        int n = 20;
        int edge = col & 0xFFFFFF;
        Vec3 prev = c.add(cr.scale(r));
        for (int i = 1; i <= n; i++) {
            double a = Math.PI * 2 * i / n;
            Vec3 p = c.add(cr.scale(Math.cos(a) * r)).add(cu.scale(Math.sin(a) * r));
            tri(true, c, col, prev, edge, p, edge);
            prev = p;
        }
    }

    /** A thin star flare: two crossed spikes through {@code c}. */
    public void flare(Vec3 c, double len, double width, int col, double angle) {
        int edge = col & 0xFFFFFF;
        for (int k = 0; k < 2; k++) {
            double a = angle + k * Math.PI / 2;
            Vec3 d = cr.scale(Math.cos(a)).add(cu.scale(Math.sin(a)));
            Vec3 s = cr.scale(-Math.sin(a)).add(cu.scale(Math.cos(a))).scale(width);
            Vec3 tip0 = c.add(d.scale(len)), tip1 = c.subtract(d.scale(len));
            tri(true, c.add(s), col, c.subtract(s), col, tip0, edge);
            tri(true, c.add(s), col, c.subtract(s), col, tip1, edge);
        }
    }

    /**
     * A sphere. Dark spheres (the null orbs) are drawn black with a gold rim where they turn from the eye; glowing
     * ones are bright at the heart and fade at the limb.
     */
    public void sphere(boolean glow, Vec3 c, double r, int core, int rim, double rimPower) {
        int lat = r > 3 ? 18 : 12, lon = r > 3 ? 28 : 18;
        Vec3[][] p = new Vec3[lat + 1][lon + 1];
        int[][] col = new int[lat + 1][lon + 1];
        for (int i = 0; i <= lat; i++) {
            double th = Math.PI * i / lat;
            for (int j = 0; j <= lon; j++) {
                double ph = Math.PI * 2 * j / lon;
                Vec3 n = new Vec3(Math.sin(th) * Math.cos(ph), Math.cos(th), Math.sin(th) * Math.sin(ph));
                p[i][j] = c.add(n.scale(r));
                Vec3 view = cam.subtract(p[i][j]).normalize();
                double facing = Math.abs(n.dot(view));
                double edge = Math.pow(1 - facing, rimPower);
                col[i][j] = mix(core, rim, (float) edge);
            }
        }
        for (int i = 0; i < lat; i++) {
            for (int j = 0; j < lon; j++) {
                quad(glow, p[i][j], col[i][j], p[i + 1][j], col[i + 1][j], p[i + 1][j + 1], col[i + 1][j + 1], p[i][j + 1], col[i][j + 1]);
            }
        }
    }

    public static int mix(int a, int b, float k) {
        k = Mth.clamp(k, 0, 1);
        int aa = (int) Mth.lerp(k, a >>> 24, b >>> 24), ar = (int) Mth.lerp(k, (a >> 16) & 255, (b >> 16) & 255),
                ag = (int) Mth.lerp(k, (a >> 8) & 255, (b >> 8) & 255), ab = (int) Mth.lerp(k, a & 255, b & 255);
        return aa << 24 | ar << 16 | ag << 8 | ab;
    }

    /** A ring in the plane normal to {@code n}: radius {@code r}, width {@code w}, crisp core fading to both edges. */
    public void ring(Vec3 c, Vec3 n, double r, double w, int col, int segments) {
        Vec3[] b = basis(n);
        int edge = col & 0xFFFFFF;
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2 * i / segments, a1 = Math.PI * 2 * (i + 1) / segments;
            Vec3 d0 = b[0].scale(Math.cos(a0)).add(b[1].scale(Math.sin(a0))), d1 = b[0].scale(Math.cos(a1)).add(b[1].scale(Math.sin(a1)));
            Vec3 m0 = c.add(d0.scale(r)), m1 = c.add(d1.scale(r));
            Vec3 i0 = c.add(d0.scale(r - w)), i1 = c.add(d1.scale(r - w)), o0 = c.add(d0.scale(r + w)), o1 = c.add(d1.scale(r + w));
            quad(true, i0, edge, m0, col, m1, col, i1, edge);
            quad(true, m0, col, o0, edge, o1, edge, m1, col);
        }
    }

    /** A ring of arcs (a halo with gaps): {@code from}..{@code to} radians around. */
    public void arc(Vec3 c, Vec3 n, double r, double w, double from, double to, int col, int segments, boolean glow) {
        Vec3[] b = basis(n);
        int edge = col & 0xFFFFFF;
        for (int i = 0; i < segments; i++) {
            double a0 = from + (to - from) * i / segments, a1 = from + (to - from) * (i + 1) / segments;
            double taper0 = Math.sin(Math.PI * i / segments), taper1 = Math.sin(Math.PI * (i + 1) / segments);
            Vec3 d0 = b[0].scale(Math.cos(a0)).add(b[1].scale(Math.sin(a0))), d1 = b[0].scale(Math.cos(a1)).add(b[1].scale(Math.sin(a1)));
            Vec3 i0 = c.add(d0.scale(r - w * taper0)), i1 = c.add(d1.scale(r - w * taper1)), o0 = c.add(d0.scale(r + w * taper0)),
                    o1 = c.add(d1.scale(r + w * taper1));
            Vec3 m0 = c.add(d0.scale(r)), m1 = c.add(d1.scale(r));
            quad(glow, i0, edge, m0, col, m1, col, i1, edge);
            quad(glow, m0, col, o0, edge, o1, edge, m1, col);
        }
    }

    /** A flat disc (the black sun): solid colour {@code col} to {@code r}. */
    public void disc(boolean glow, Vec3 c, Vec3 n, double r, int col, int edgeCol) {
        Vec3[] b = basis(n);
        int segs = 64;
        Vec3 prev = c.add(b[0].scale(r));
        for (int i = 1; i <= segs; i++) {
            double a = Math.PI * 2 * i / segs;
            Vec3 p = c.add(b[0].scale(Math.cos(a) * r)).add(b[1].scale(Math.sin(a) * r));
            tri(glow, c, col, prev, edgeCol, p, edgeCol);
            prev = p;
        }
    }

    /**
     * A ribbon through {@code pts} facing the camera, widths and colours per point. The edges fade to nothing so the
     * line reads soft but its spine stays sharp.
     */
    public void ribbon(Vec3[] pts, double[] widths, int[] cols) {
        for (int i = 0; i + 1 < pts.length; i++) {
            Vec3 a = pts[i], b = pts[i + 1];
            Vec3 d = b.subtract(a);
            Vec3 view = cam.subtract(a);
            Vec3 s = d.cross(view);
            if (s.lengthSqr() < 1e-10) {
                continue;
            }
            s = s.normalize();
            Vec3 sa = s.scale(widths[i]), sb = s.scale(widths[i + 1]);
            int ea = cols[i] & 0xFFFFFF, eb = cols[i + 1] & 0xFFFFFF;
            quad(true, a.subtract(sa), ea, a, cols[i], b, cols[i + 1], b.subtract(sb), eb);
            quad(true, a, cols[i], a.add(sa), ea, b.add(sb), eb, b, cols[i + 1]);
        }
    }

    /** A straight beam of light from {@code a} to {@code b}. */
    public void beam(Vec3 a, Vec3 b, double w, int col) {
        ribbon(new Vec3[]{a, a.lerp(b, 0.5), b}, new double[]{w * 0.6, w, w * 0.6}, new int[]{col, col, col});
    }

    /** A solid block-shaped box (the colossus's limbs), dark, with a lit rim drawn separately. */
    public void box(boolean glow, Vec3 c, Vec3 ax, Vec3 ay, Vec3 az, int col, int shade) {
        Vec3[] corner = new Vec3[8];
        for (int i = 0; i < 8; i++) {
            corner[i] = c.add(ax.scale((i & 1) == 0 ? -1 : 1)).add(ay.scale((i & 2) == 0 ? -1 : 1)).add(az.scale((i & 4) == 0 ? -1 : 1));
        }
        int[][] faces = {{0, 1, 3, 2}, {4, 6, 7, 5}, {0, 4, 5, 1}, {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 5, 7, 3}};
        for (int f = 0; f < faces.length; f++) {
            int cc = f == 3 ? col : shade;
            int[] q = faces[f];
            quad(glow, corner[q[0]], cc, corner[q[1]], cc, corner[q[2]], cc, corner[q[3]], cc);
        }
    }

    /** Two unit vectors perpendicular to {@code n} and to each other. */
    public static Vec3[] basis(Vec3 n) {
        Vec3 nn = n.normalize();
        Vec3 a = Math.abs(nn.y) < 0.95 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 u = a.cross(nn).normalize();
        Vec3 v = nn.cross(u).normalize();
        return new Vec3[]{u, v};
    }

    // ------------------------------------------------------------------------------------------------ noise

    private static float hash(int x, int y, int z) {
        int h = x * 374761393 + y * 668265263 + z * 1274126177;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFFFF) / (float) 0xFFFFFF;
    }

    /** Smooth value noise, 0..1. */
    public static float noise(double x, double y, double z) {
        int ix = Mth.floor(x), iy = Mth.floor(y), iz = Mth.floor(z);
        float fx = (float) (x - ix), fy = (float) (y - iy), fz = (float) (z - iz);
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        fz = fz * fz * (3 - 2 * fz);
        float a = Mth.lerp(fx, hash(ix, iy, iz), hash(ix + 1, iy, iz));
        float b = Mth.lerp(fx, hash(ix, iy + 1, iz), hash(ix + 1, iy + 1, iz));
        float c = Mth.lerp(fx, hash(ix, iy, iz + 1), hash(ix + 1, iy, iz + 1));
        float d = Mth.lerp(fx, hash(ix, iy + 1, iz + 1), hash(ix + 1, iy + 1, iz + 1));
        return Mth.lerp(fz, Mth.lerp(fy, a, b), Mth.lerp(fy, c, d));
    }
}
