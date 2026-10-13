package com.firestar.rose.client.gfx;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.List;
import net.minecraft.world.phys.Vec3;

/**
 * Turns {@link Frame} shapes into vertices (camera-relative, colour channels carrying each shape's parameters, UVs
 * describing where on the shape a point lies). The look of each shape lives in its fragment shader.
 */
final class Geometry {
    private Geometry() {}

    private static BufferBuilder begin() {
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
        return b;
    }

    private static void v(BufferBuilder b, Vec3 p, float r, float g, float bl, float a, float u, float vv) {
        b.vertex(p.x, p.y, p.z).color(clamp(r), clamp(g), clamp(bl), clamp(a)).uv(u, vv).endVertex();
    }

    private static float clamp(float x) {
        return Math.max(0F, Math.min(1F, x));
    }

    private static BufferBuilder.RenderedBuffer end(BufferBuilder b, int quads) {
        BufferBuilder.RenderedBuffer r = b.end();
        if (quads == 0) {
            r.release();
            return null;
        }
        return r;
    }

    // ------------------------------------------------------------------------------------------------ cuts

    static BufferBuilder.RenderedBuffer arcs(Frame f, List<Frame.Arc> list) {
        if (list.isEmpty()) {
            return null;
        }
        BufferBuilder b = begin();
        int q = 0;
        for (Frame.Arc a : list) {
            q += arc(b, f.cam, a, a.fade(), a.heat(), a.seed(), a.blade() ? 1.0F : 0.0F);
        }
        return end(b, q);
    }

    private static int arc(BufferBuilder b, Vec3 cam, Frame.Arc a, float r, float g, float bl, float al) {
        double span = a.a1() - a.a0();
        int n = Math.max(8, Math.min(72, (int) (Math.abs(span) / 4.0) + 8));
        double rin = Math.max(0.0, a.r() - a.w());
        Vec3 c = a.c().subtract(cam);
        Vec3 pi = null, po = null;
        float px = 0;
        for (int i = 0; i <= n; i++) {
            float x = i / (float) n;
            double ang = Math.toRadians(a.a0() + span * x);
            Vec3 radial = a.u().scale(Math.cos(ang)).add(a.v().scale(Math.sin(ang)));
            Vec3 in = c.add(radial.scale(rin)), out = c.add(radial.scale(a.r()));
            if (pi != null) {
                v(b, pi, r, g, bl, al, px, 0);
                v(b, po, r, g, bl, al, px, 1);
                v(b, out, r, g, bl, al, x, 1);
                v(b, in, r, g, bl, al, x, 0);
            }
            pi = in;
            po = out;
            px = x;
        }
        return n;
    }

    // ------------------------------------------------------------------------------------------------ rays

    static BufferBuilder.RenderedBuffer beams(Frame f, List<Frame.Beam> list) {
        if (list.isEmpty()) {
            return null;
        }
        BufferBuilder b = begin();
        int q = 0;
        for (Frame.Beam s : list) {
            Vec3 a = s.a().subtract(f.cam), e = s.b().subtract(f.cam);
            Vec3 d = e.subtract(a);
            double len = d.length();
            int n = Math.max(2, Math.min(64, (int) (len / 1.5) + 2));
            float lenK = (float) Math.min(1.0, len / 64.0);
            Vec3 pl = null, pr = null;
            float pu = 0;
            for (int i = 0; i <= n; i++) {
                float u = i / (float) n;
                Vec3 p = a.add(d.scale(u));
                Vec3 side = d.cross(p);
                side = side.lengthSqr() < 1.0E-9 ? f.up : side.normalize();
                Vec3 l = p.add(side.scale(s.radius())), r = p.subtract(side.scale(s.radius()));
                if (pl != null) {
                    v(b, pl, s.fade(), s.heat(), s.seed(), lenK, pu, -1);
                    v(b, pr, s.fade(), s.heat(), s.seed(), lenK, pu, 1);
                    v(b, r, s.fade(), s.heat(), s.seed(), lenK, u, 1);
                    v(b, l, s.fade(), s.heat(), s.seed(), lenK, u, -1);
                    q++;
                }
                pl = l;
                pr = r;
                pu = u;
            }
        }
        return end(b, q);
    }

    // ------------------------------------------------------------------------------------------------ shockwaves

    static BufferBuilder.RenderedBuffer rings(Frame f, List<Frame.Ring> list) {
        if (list.isEmpty()) {
            return null;
        }
        BufferBuilder b = begin();
        int q = 0;
        for (Frame.Ring s : list) {
            q += ring(b, f.cam, s, s.fade(), s.heat(), s.seed(), s.sharp());
        }
        return end(b, q);
    }

    private static int ring(BufferBuilder b, Vec3 cam, Frame.Ring s, float r, float g, float bl, float al) {
        Vec3 n = s.n();
        Vec3 u = Math.abs(n.y) > 0.9 ? new Vec3(1, 0, 0).cross(n).normalize() : new Vec3(0, 1, 0).cross(n).normalize();
        Vec3 w = n.cross(u);
        int segs = Math.max(24, Math.min(160, (int) (s.r() * 6) + 24));
        Vec3 c = s.c().subtract(cam);
        double rin = Math.max(0, s.r() - s.w());
        Vec3 pi = null, po = null;
        float px = 0;
        for (int i = 0; i <= segs; i++) {
            float x = i / (float) segs;
            double ang = x * Math.PI * 2;
            Vec3 d = u.scale(Math.cos(ang)).add(w.scale(Math.sin(ang)));
            Vec3 in = c.add(d.scale(rin)), out = c.add(d.scale(s.r()));
            if (pi != null) {
                v(b, pi, r, g, bl, al, px, 0);
                v(b, po, r, g, bl, al, px, 1);
                v(b, out, r, g, bl, al, x, 1);
                v(b, in, r, g, bl, al, x, 0);
            }
            pi = in;
            po = out;
            px = x;
        }
        return segs;
    }

    // ------------------------------------------------------------------------------------------------ points of light

    static BufferBuilder.RenderedBuffer dots(Frame f, List<Frame.Dot> list) {
        if (list.isEmpty()) {
            return null;
        }
        BufferBuilder b = begin();
        Vec3 right = f.left.scale(-1), up = f.up;
        for (Frame.Dot d : list) {
            Vec3 p = d.p().subtract(f.cam);
            float kind = d.kind() == 0 ? 0.0F : d.kind() == 1 ? 0.33F : d.kind() == 2 ? 0.66F : 1.0F;
            Vec3 ax, ay;
            if (d.kind() == 1 && d.dir() != null && d.dir().lengthSqr() > 1.0E-8) {
                Vec3 dir = d.dir().normalize();
                Vec3 side = dir.cross(p);
                side = side.lengthSqr() < 1.0E-9 ? up : side.normalize();
                ax = dir.scale(d.length() * 0.5);
                ay = side.scale(d.size() * 0.5);
                p = p.subtract(dir.scale(d.length() * 0.3));
            } else {
                double s = d.size() * 0.5;
                // flares get a wide quad so their streak has room
                double sx = d.kind() == 0 ? s * 3.0 : s;
                ax = right.scale(sx);
                ay = up.scale(s);
            }
            float sx = d.kind() == 0 ? 1.0F : 1.0F;
            v(b, p.subtract(ax).subtract(ay), d.fade(), d.heat(), d.seed(), kind, 0, 0);
            v(b, p.add(ax).subtract(ay), d.fade(), d.heat(), d.seed(), kind, sx, 0);
            v(b, p.add(ax).add(ay), d.fade(), d.heat(), d.seed(), kind, sx, 1);
            v(b, p.subtract(ax).add(ay), d.fade(), d.heat(), d.seed(), kind, 0, 1);
        }
        return end(b, list.size());
    }

    // ------------------------------------------------------------------------------------------------ rifts

    static BufferBuilder.RenderedBuffer rifts(Frame f, List<Frame.Rift> list) {
        if (list.isEmpty()) {
            return null;
        }
        BufferBuilder b = begin();
        for (Frame.Rift r : list) {
            Vec3 c = r.c().subtract(f.cam);
            Vec3 ax = r.axis().scale(r.length() * 0.5);
            Vec3 ay = r.across().scale(r.width() / 0.3 * 0.5);
            v(b, c.subtract(ax).subtract(ay), r.open(), r.fade(), r.seed(), 1, 0, 0);
            v(b, c.add(ax).subtract(ay), r.open(), r.fade(), r.seed(), 1, 1, 0);
            v(b, c.add(ax).add(ay), r.open(), r.fade(), r.seed(), 1, 1, 1);
            v(b, c.subtract(ax).add(ay), r.open(), r.fade(), r.seed(), 1, 0, 1);
        }
        return end(b, list.size());
    }

    // ------------------------------------------------------------------------------------------------ space bending

    static BufferBuilder.RenderedBuffer warp(Frame f) {
        BufferBuilder b = begin();
        int q = 0;
        for (Frame.Arc a : f.warpArcs) {
            q += arc(b, f.cam, a, a.fade(), 0, 0, 0);
        }
        for (Frame.Ring r : f.warpRings) {
            q += ring(b, f.cam, r, r.fade(), 0, 0, 1);
        }
        for (Frame.Rift r : f.rifts) {
            Vec3 c = r.c().subtract(f.cam);
            Vec3 ax = r.axis().scale(r.length() * 0.6);
            Vec3 ay = r.across().scale(r.width() * 2.2 * Math.max(0.2, r.open()));
            float amt = r.fade() * r.open() * 1.6F;
            v(b, c.subtract(ax).subtract(ay), amt, 0, 0, 1, 0, 0);
            v(b, c.add(ax).subtract(ay), amt, 0, 0, 1, 1, 0);
            v(b, c.add(ax).add(ay), amt, 0, 0, 1, 1, 1);
            v(b, c.subtract(ax).add(ay), amt, 0, 0, 1, 0, 1);
            q++;
        }
        return end(b, q);
    }
}
