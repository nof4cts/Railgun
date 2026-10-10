package com.geto.csm.client;

import com.geto.csm.client.Mesh.Frame;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Effects specific to curse manipulation: rifts, orbs, Uzumaki, foxfire, the Killing Stone… */
public final class CsmFx {

    /** Glowing tube between two points (any orientation). */
    static void glowTube(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, double r, int ca, int cb, int segs) {
        Vec3 d = b.subtract(a);
        if (d.lengthSqr() < 1.0e-8) return;
        Vec3 u = Draw.perp(d.normalize());
        Vec3 v = d.normalize().cross(u);
        for (int i = 0; i < segs; i++) {
            double a0 = 2 * Math.PI * i / segs, a1 = 2 * Math.PI * (i + 1) / segs;
            Vec3 o0 = u.scale(Math.cos(a0) * r).add(v.scale(Math.sin(a0) * r));
            Vec3 o1 = u.scale(Math.cos(a1) * r).add(v.scale(Math.sin(a1) * r));
            Draw.quad(vc, m, a.add(o0), a.add(o1), b.add(o1), b.add(o0), ca, ca, cb, cb);
        }
    }

    // ─────────────── summon rift ───────────────

    public static final class Rift extends Vfx.Effect {
        final Vec3 pos; final int col; final double R;

        public Rift(Vec3 pos, int col, double R, long life) {
            super(life); this.pos = pos; this.col = col; this.R = R;
        }

        double radius(long t) {
            float p = p(t);
            if (p < 0.2f) return R * Ease.outBack(p / 0.2f);
            if (p > 0.7f) return R * (1 - Ease.inExpo((p - 0.7f) / 0.3f));
            return R;
        }

        @Override
        public void solid(Vfx.Ctx c) {
            double r = radius(c.t);
            c.at(pos.add(0, 0.05, 0));
            Draw.ringXZ(c.vc, c.m(), Vec3.ZERO, 0, r, 0xF0050008, 0xE00A0012, 40);
            Vec3 cam = c.camRel(pos);
            double time = age(c.t) / 1000.0;
            for (int i = 0; i < 9; i++) {
                double a = i * 0.7 + time * 1.5;
                Vec3 b = new Vec3(Math.cos(a) * r * 0.7, 0, Math.sin(a) * r * 0.7);
                Vec3 tip = b.add(Math.sin(time * 4 + i) * 0.4, r * (0.6 + 0.4 * Math.sin(time * 3 + i)), Math.cos(time * 3 + i) * 0.4);
                Draw.ribbon(c.vc, c.m(), b, tip, 0.18, 0.0, 0xE0050008, 0x00050008, cam);
            }
            c.pop();
        }

        @Override
        public void glow(Vfx.Ctx c) {
            double r = radius(c.t);
            double time = age(c.t) / 1000.0;
            c.at(pos.add(0, 0.07, 0));
            Draw.ringXZ(c.vc, c.m(), Vec3.ZERO, r * 0.92, r * 1.08, Draw.alpha(col, 0.9f), Draw.alpha(col, 0f), 48);
            Draw.ringXZ(c.vc, c.m(), Vec3.ZERO, r * 0.97, r * 1.0, 0xFFFFFFFF, 0xFFFFFFFF, 48);
            Vec3 cam = c.camRel(pos);
            for (int i = 0; i < 10; i++) {
                double a0 = i * Math.PI / 5 + time * 3;
                Vec3 out = new Vec3(Math.cos(a0) * r, 0.02, Math.sin(a0) * r);
                Vec3 in = new Vec3(Math.cos(a0 + 1.2) * r * 0.2, 0.02, Math.sin(a0 + 1.2) * r * 0.2);
                Draw.ribbon(c.vc, c.m(), out, in, 0.06, 0.0, Draw.alpha(0xFF9B5CFF, 0.7f), 0, cam);
            }
            Draw.beam(c.vc, c.m(), Vec3.ZERO, 5 * (1 - p(c.t)), r * 0.5, Draw.alpha(col, 0.35f), 0, 20);
            c.pop();
        }
    }

    // ─────────────── black orb condensing out of a dead curse ───────────────

    public static final class OrbForm extends Vfx.Effect {
        final Vec3 pos;

        public OrbForm(Vec3 pos, long life) {
            super(life); this.pos = pos;
        }

        @Override
        public void solid(Vfx.Ctx c) {
            float p = p(c.t);
            double r = 0.28 * Ease.outBack(Math.min(1f, p * 2f)) * (p > 0.85f ? (1 - p) / 0.15 : 1);
            c.at(pos);
            Draw.sphere(c.vc, c.m(), 0, 0, 0, r, 10, 14, (x, y, z) -> Draw.lerp(0xFF050008, 0xFF3A1458, (float) Math.max(0, y)));
            c.pop();
        }

        @Override
        public void glow(Vfx.Ctx c) {
            float p = p(c.t);
            c.at(pos);
            Vec3 cam = c.camRel(pos);
            for (int i = 0; i < 16; i++) {
                Vec3 d = Draw.randDir(start + i * 31L);
                double k = 1.6 * (1 - Ease.outCubic(Math.min(1f, p * 1.6f)));
                Draw.ribbon(c.vc, c.m(), d.scale(k + 0.6), d.scale(k), 0.0, 0.04, 0, Draw.alpha(0xFF9B5CFF, 1 - p), cam);
            }
            Draw.glowOrb(c.vc, c.m(), 0, 0, 0, 0.35, 0xFF7A3CFF, 0.5f * (1 - p));
            c.pop();
        }
    }

    // ─────────────── Maximum: Uzumaki ───────────────

    public static final class UzuCore extends Vfx.Effect {
        final Vec3 pos; final int n;

        public UzuCore(Vec3 pos, int n, long life) {
            super(life); this.pos = pos; this.n = n;
        }

        double radius(long t) {
            double s = age(t) / 1000.0;
            double grow = Math.min(1.0, n / 12.0) * 0.8 + 0.6;
            if (s < 3.3) return grow * Ease.outCubic((float) Math.min(1, s / 3.0));
            if (s < 4.2) return Mth.lerp((s - 3.3) / 0.9, grow, grow * 0.35);
            return grow * 0.35 * Math.max(0, 1 - (s - 4.2) / 0.2);
        }

        @Override
        public void solid(Vfx.Ctx c) {
            double r = radius(c.t);
            if (r <= 0.01) return;
            c.at(pos);
            Draw.sphere(c.vc, c.m(), 0, 0, 0, r, 16, 22, (x, y, z) -> Draw.lerp(0xFF020004, 0xFF1A0830, (float) Math.abs(y) * 0.5f));
            c.pop();
        }

        @Override
        public void glow(Vfx.Ctx c) {
            double r = radius(c.t);
            double s = age(c.t) / 1000.0;
            c.at(pos);
            Vec3 cam = c.camRel(pos);
            int[] cols = {0xFF5A2CFF, 0xFF9B5CFF, 0xFFFF4FD8, 0xFF3A1CC0};
            double spin = s * (6 + s * 4);
            for (int band = 0; band < 8; band++) {
                Vec3 axis = Draw.randDir(band * 977L + 5);
                Vec3 u = Draw.perp(axis), v = axis.cross(u);
                Vec3 prev = null;
                double rr = r * (1.35 + band * 0.08);
                for (int k = 0; k <= 30; k++) {
                    double a = spin * (band % 2 == 0 ? 1 : -1) + k * 0.2;
                    double wob = Math.sin(k * 0.4 + s * 3) * 0.12 * r;
                    Vec3 p = u.scale(Math.cos(a) * (rr + wob)).add(v.scale(Math.sin(a) * (rr + wob)));
                    if (prev != null) {
                        float al = (float) (k / 30.0);
                        Draw.ribbon(c.vc, c.m(), prev, p, 0.04 + r * 0.04, 0.04 + r * 0.04, Draw.alpha(cols[band % 4], al * 0.2f), Draw.alpha(cols[band % 4], al * 0.9f), cam);
                    }
                    prev = p;
                }
            }
            Vec3 tilt = new Vec3(0.3, 1, 0.2).normalize();
            Vec3 tu = Draw.perp(tilt), tv = tilt.cross(tu);
            Draw.ring(c.vc, c.m(), Vec3.ZERO, tu, tv, r * 1.6, r * 3.2, Draw.alpha(0xFF9B5CFF, 0.55f), 0, 64);
            Draw.ring(c.vc, c.m(), Vec3.ZERO, tu, tv, r * 1.2, r * 1.6, 0xFFFFFFFF, Draw.alpha(0xFFFF4FD8, 0.6f), 64);
            if (s > 2.2) {
                long frame = c.t / 50;
                for (int i = 0; i < 6; i++) {
                    Vec3 a = Draw.randDir(frame * 7 + i).scale(r * 1.05);
                    Vec3 b = a.add(Draw.randDir(frame * 13 + i).scale(r * 1.6));
                    Draw.ribbon(c.vc, c.m(), a, b, 0.05, 0.01, 0xFFFFFFFF, Draw.alpha(0xFF9B5CFF, 0.6f), cam);
                }
            }
            Draw.glowOrb(c.vc, c.m(), 0, 0, 0, r * 2.2, 0xFF7A3CFF, 0.25f);
            c.pop();
        }
    }

    /** A summoned curse unravelling into a spiral stream that pours into the core. */
    public static final class UzuStream extends Vfx.Effect {
        final Vec3 from, to; final int col;

        public UzuStream(Vec3 from, Vec3 to, int col, long life) {
            super(life); this.from = from; this.to = to; this.col = col;
        }

        @Override
        public void glow(Vfx.Ctx c) {
            float p = p(c.t);
            c.at(to);
            Vec3 a = from.subtract(to);
            Vec3 mid = a.scale(0.5).add(0, a.length() * 0.35 + 1, 0);
            Vec3 cam = c.camRel(to);
            double head = Ease.inOut(Math.min(1f, p * 1.3f));
            double tail = Ease.inOut(Math.max(0f, p * 1.3f - 0.35f));
            Vec3 prev = null;
            for (int k = 0; k <= 40; k++) {
                double u = tail + (head - tail) * k / 40.0;
                double iu = 1 - u;
                Vec3 base = a.scale(iu * iu).add(mid.scale(2 * iu * u));
                Vec3 tan = a.scale(-2 * iu).add(mid.scale(2 * (iu - u))).normalize();
                Vec3 pu = Draw.perp(tan), pv = tan.cross(pu);
                double ang = u * 18 + age(c.t) / 80.0;
                double rad = 0.5 * (1 - u) + 0.05;
                Vec3 pt = base.add(pu.scale(Math.cos(ang) * rad)).add(pv.scale(Math.sin(ang) * rad));
                if (prev != null) Draw.ribbon(c.vc, c.m(), prev, pt, 0.1, 0.1, Draw.alpha(col, 0.7f), Draw.alpha(0xFF9B5CFF, 0.9f), cam);
                prev = pt;
            }
            if (p < 0.35f) {
                Vec3 f = a;
                Draw.glowOrb(c.vc, c.m(), f.x, f.y, f.z, 1.6 * (1 - p / 0.35f), col, 0.6f * (1 - p / 0.35f));
            }
            c.pop();
        }
    }

    public static final class UzuBeam extends Vfx.Effect {
        final Vec3 origin, dir; final double length = 52;

        public UzuBeam(Vec3 origin, Vec3 dir, long life) {
            super(life); this.origin = origin; this.dir = dir.normalize();
        }

        double reach(long t) {
            return length * Ease.outExpo(Math.min(1f, age(t) / 260f));
        }

        double width(long t) {
            float p = p(t);
            return 2.6 * (p < 0.08f ? Ease.outBack(p / 0.08f) : 1 - Ease.inOut(Math.max(0, (p - 0.45f) / 0.55f)));
        }

        @Override
        public void solid(Vfx.Ctx c) {
            double L = reach(c.t), w = width(c.t);
            if (w < 0.02) return;
            c.at(origin);
            glowTube(c.vc, c.m(), Vec3.ZERO, dir.scale(L), w * 0.45, 0xFF030006, 0xFF050010, 18);
            c.pop();
        }

        @Override
        public void glow(Vfx.Ctx c) {
            double L = reach(c.t), w = width(c.t);
            if (w < 0.02) return;
            double s = age(c.t) / 1000.0;
            c.at(origin);
            Vec3 cam = c.camRel(origin);
            Vec3 u = Draw.perp(dir), v = dir.cross(u);
            int[] cols = {0xFF9B5CFF, 0xFFFF4FD8, 0xFF5A2CFF};
            for (int strand = 0; strand < 3; strand++) {
                Vec3 prev = null;
                for (int k = 0; k <= 90; k++) {
                    double d = L * k / 90.0;
                    double a = d * 0.9 - s * 22 + strand * 2.094;
                    double rad = w * (0.7 + 0.15 * Math.sin(d * 0.5 + s * 10));
                    Vec3 p = dir.scale(d).add(u.scale(Math.cos(a) * rad)).add(v.scale(Math.sin(a) * rad));
                    if (prev != null) Draw.ribbon(c.vc, c.m(), prev, p, 0.18, 0.18, Draw.alpha(cols[strand], 0.85f), Draw.alpha(cols[strand], 0.85f), cam);
                    prev = p;
                }
            }
            glowTube(c.vc, c.m(), Vec3.ZERO, dir.scale(L), w * 1.1, Draw.alpha(0xFF7A3CFF, 0.35f), Draw.alpha(0xFF7A3CFF, 0.1f), 20);
            glowTube(c.vc, c.m(), Vec3.ZERO, dir.scale(L), w * 0.55, Draw.alpha(0xFFFFFFFF, 0.5f), Draw.alpha(0xFFFF4FD8, 0.2f), 16);
            for (int i = 0; i < 6; i++) {
                double d = ((s * 40 + i * 9) % L);
                Draw.ring(c.vc, c.m(), dir.scale(d), u, v, w * 1.2, w * 2.0, Draw.alpha(0xFFFFFFFF, 0.5f), 0, 32);
            }
            Vec3 tip = dir.scale(L);
            Draw.glowOrb(c.vc, c.m(), tip.x, tip.y, tip.z, w * 2.2, 0xFFFF4FD8, 0.6f);
            c.pop();
        }
    }

    // ─────────────── Tamamo ───────────────

    public static final class Foxfire extends Vfx.Effect {
        final Vec3 from; final int targetId; final Vec3 bend;
        boolean landed;

        public Foxfire(Vec3 from, int targetId, int index, long life) {
            super(life); this.from = from; this.targetId = targetId;
            this.bend = Draw.randDir(start + index * 101L).add(0, 1.2, 0).scale(3.5);
        }

        Vec3 target(float partial) {
            Entity t = Vfx.entity(targetId);
            return t == null ? from : t.getPosition(partial).add(0, t.getBbHeight() * 0.55, 0);
        }

        @Override
        public void glow(Vfx.Ctx c) {
            float p = Math.min(1f, age(c.t) / 700f);
            Vec3 to = target(c.partial);
            Vec3 ctrl = from.add(to).scale(0.5).add(bend);
            c.at(from);
            Vec3 cam = c.camRel(from);
            Vec3 prev = null;
            for (int k = 0; k <= 10; k++) {
                double u = Math.max(0, p - 0.25 + 0.25 * k / 10.0);
                double iu = 1 - u;
                Vec3 pt = from.scale(iu * iu).add(ctrl.scale(2 * iu * u)).add(to.scale(u * u)).subtract(from);
                if (prev != null) Draw.ribbon(c.vc, c.m(), prev, pt, 0.02 + 0.02 * k, 0.04 + 0.02 * k, Draw.alpha(0xFF9FE6FF, k / 10f * 0.5f), Draw.alpha(0xFFDFF8FF, k / 10f), cam);
                prev = pt;
            }
            if (prev != null && p < 1f) Draw.glowOrb(c.vc, c.m(), prev.x, prev.y, prev.z, 0.3, 0xFF9FE6FF, 1f);
            c.pop();
            if (p >= 1f && !landed) {
                landed = true;
                Vec3 hit = to;
                Sfx.later(0, () -> {
                    Vfx.add(new Vfx.Flash(hit, 1.2, 0xFF9FE6FF, 300));
                    Vfx.add(new Vfx.Spikes(hit, 14, 2.2, 0xFF9FE6FF, 280));
                });
            }
        }
    }

    public static final class KillingStone extends Vfx.Effect {
        final Vec3 at;
        boolean impacted;

        public KillingStone(Vec3 at, long life) {
            super(life); this.at = at;
        }

        double height(long t) {
            long a = age(t);
            if (a < 1000) return 18 * (1 - Ease.inExpo(a / 1000f));
            float p = p(t);
            return p > 0.8f ? -3.5 * Ease.inOut((p - 0.8f) / 0.2f) : 0;
        }

        @Override
        public void solid(Vfx.Ctx c) {
            Vec3 pos = at.add(0, height(c.t) + 1.4, 0);
            c.at(pos);
            Mesh.begin(Mesh.FILL, c.vc, c.m(), c.camRel(pos), 0.9f, 0f);
            Frame f = Frame.of(new Vec3(0.3, 0, 1), Draw.Y);
            Mesh.blob(Vec3.ZERO, f, 1.4, 2.0, 1.2, (u, n) -> ((int) ((n.x * 3 + n.y * 5 + n.z * 4) * 2)) % 2 == 0 ? 0xFF3A3440 : 0xFF2A2430);
            Mesh.blob(new Vec3(0.6, 1.2, 0.2), f, 0.8, 1.1, 0.7, 0xFF332D3A);
            Mesh.blob(new Vec3(-0.5, -0.9, -0.3), f, 1.0, 0.8, 0.9, 0xFF2E2834);
            Mesh.torus(new Vec3(0, 0.8, 0), Draw.X, Draw.Z, 1.25, 0.08, 24, 0xFFF2F0E8);
            c.pop();
            if (age(c.t) >= 1000 && !impacted) {
                impacted = true;
                Vec3 g = at;
                Sfx.later(0, () -> {
                    Vfx.add(Vfx.Shockwave.ground(g, 9, 0xFF9B5CFF, 700));
                    Vfx.add(new Vfx.Debris(g, 30, 1.4, 1800));
                    Vfx.add(new Vfx.Flash(g.add(0, 1, 0), 3, 0xFF9B5CFF, 400));
                    if (FxDispatcher.feels(g, 26)) {
                        ScreenFx.impact(ScreenFx.INK, 200, g);
                        CameraDirector.shake(2.4f, 700);
                    }
                });
            }
        }

        @Override
        public void glow(Vfx.Ctx c) {
            if (age(c.t) > 1000) return;
            Vec3 pos = at.add(0, height(c.t) + 1.4, 0);
            c.at(pos);
            Draw.glowOrb(c.vc, c.m(), 0, 0, 0, 2.2, 0xFF7A3CFF, 0.35f);
            Draw.beam(c.vc, c.m(), Vec3.ZERO, 8, 0.8, Draw.alpha(0xFF9B5CFF, 0.4f), 0, 16);
            c.pop();
        }
    }

    public static final class Miasma extends Vfx.Effect {
        final Vec3 at; final double r;

        public Miasma(Vec3 at, double r, long life) {
            super(life); this.at = at; this.r = r;
        }

        @Override
        public void solid(Vfx.Ctx c) {
            float p = p(c.t);
            float a = (p < 0.1f ? p / 0.1f : p > 0.8f ? (1 - p) / 0.2f : 1f) * 0.28f;
            double s = age(c.t) / 1000.0;
            c.at(at);
            for (int i = 0; i < 9; i++) {
                double ang = i * 0.7 + s * (0.3 + i * 0.05);
                double rr = r * (0.3 + 0.6 * Draw.hash(i * 7L));
                double y = 0.4 + Draw.hash(i * 3L) * 1.4 + Math.sin(s + i) * 0.2;
                Draw.sphere(c.vc, c.m(), Math.cos(ang) * rr, y, Math.sin(ang) * rr, 0.9 + Draw.hash(i) * 0.9, 8, 12, Draw.alpha(0xFF1A0624, a));
            }
            c.pop();
        }

        @Override
        public void glow(Vfx.Ctx c) {
            float p = p(c.t);
            c.at(at.add(0, 0.06, 0));
            Draw.ringXZ(c.vc, c.m(), Vec3.ZERO, r - 0.4, r, 0, Draw.alpha(0xFF9B5CFF, 0.5f * (1 - p)), 48);
            c.pop();
        }
    }

    // ─────────────── Kuchisake ───────────────

    public static final class VowDome extends Vfx.Effect {
        final Vec3 at; final double r;

        public VowDome(Vec3 at, double r, long life) {
            super(life); this.at = at; this.r = r;
        }

        @Override
        public void solid(Vfx.Ctx c) {
            float p = p(c.t);
            float a = p < 0.05f ? p / 0.05f : p > 0.9f ? (1 - p) / 0.1f : 1f;
            c.at(at);
            Draw.sphere(c.vc, c.m(), 0, 0, 0, r * Ease.outBack(Math.min(1f, p * 12f)), 14, 24, (x, y, z) -> y < 0 ? 0 : Draw.alpha(0xFF3A0010, 0.22f * a));
            c.pop();
        }

        @Override
        public void glow(Vfx.Ctx c) {
            float p = p(c.t);
            float a = p > 0.9f ? (1 - p) / 0.1f : 1f;
            double rr = r * Ease.outBack(Math.min(1f, p * 12f));
            c.at(at);
            for (double ny : new double[]{0.05, 0.35, 0.65}) {
                double ring = rr * Math.sqrt(1 - ny * ny);
                Draw.ring(c.vc, c.m(), new Vec3(0, rr * ny, 0), Draw.X, Draw.Z, ring - 0.06, ring, 0, Draw.alpha(0xFFE0283C, 0.7f * a), 48);
            }
            for (int k = 0; k < 8; k++) {
                double ang = k * Math.PI / 4;
                Vec3 prev = null;
                for (int i = 0; i <= 10; i++) {
                    double th = Math.PI / 2 * i / 10.0;
                    Vec3 pt = new Vec3(Math.cos(ang) * Math.cos(th) * rr, Math.sin(th) * rr, Math.sin(ang) * Math.cos(th) * rr);
                    if (prev != null) Draw.ribbon(c.vc, c.m(), prev, pt, 0.03, 0.03, Draw.alpha(0xFFE0283C, 0.5f * a), Draw.alpha(0xFFE0283C, 0.5f * a), c.camRel(at));
                    prev = pt;
                }
            }
            c.pop();
        }
    }

    /** Giant shears appearing around a target's limbs and snapping shut. */
    public static final class ShearsFx extends Vfx.Effect {
        final int targetId; final boolean big; final long snapAt;
        final Vec3 fallback;
        boolean snapped;

        public ShearsFx(int targetId, Vec3 fallback, boolean big, long life) {
            super(life); this.targetId = targetId; this.big = big; this.fallback = fallback;
            this.snapAt = big ? 450 : 120;
        }

        Vec3 center(float partial) {
            Entity t = Vfx.entity(targetId);
            return t == null ? fallback : t.getPosition(partial).add(0, t.getBbHeight() * 0.5, 0);
        }

        double open(long t) {
            long a = age(t);
            if (a < snapAt) return 0.75 * Ease.outCubic(Math.min(1f, a / 150f));
            return 0.75 * Math.max(0, 1 - (a - snapAt) / 60.0);
        }

        @Override
        public void solid(Vfx.Ctx c) {
            float p = p(c.t);
            if (p > 0.85f) return;
            Vec3 cen = center(c.partial);
            c.at(cen);
            Mesh.begin(Mesh.FILL, c.vc, c.m(), c.camRel(cen), 1f, 0f);
            double op = open(c.t);
            int pairs = big ? 3 : 1;
            for (int i = 0; i < pairs; i++) {
                double ang = i * 2.1 + 0.4;
                Vec3 radial = new Vec3(Math.cos(ang), (i - 1) * 0.35, Math.sin(ang)).normalize();
                Vec3 hand = radial.scale(1.9);
                CurseModels.shears(hand, radial.scale(-1), Draw.Y.cross(radial).normalize(), op, big ? 1.4 : 1.0);
            }
            c.pop();
            if (age(c.t) >= snapAt && !snapped) {
                snapped = true;
                Vec3 at = cen;
                Sfx.later(0, () -> {
                    Vfx.add(new Vfx.Flash(at, 1.4, 0xFFE0283C, 260));
                    Vfx.add(new Vfx.Spikes(at, 20, 3.0, 0xFFFFFFFF, 260));
                    if (FxDispatcher.feels(at, 18)) {
                        ScreenFx.impact(big ? ScreenFx.RED : ScreenFx.INVERT, big ? 220 : 90, at);
                        CameraDirector.shake(big ? 1.8f : 0.6f, 400);
                    }
                });
            }
        }
    }

    // ─────────────── breath / flame cones ───────────────

    public static final class BreathCone extends Vfx.Effect {
        final Vec3 from, dir; final double len; final int core, edge;

        public BreathCone(Vec3 from, Vec3 dir, double len, int core, int edge, long life) {
            super(life); this.from = from; this.dir = dir.normalize(); this.len = len; this.core = core; this.edge = edge;
        }

        @Override
        public void glow(Vfx.Ctx c) {
            float p = p(c.t);
            double s = age(c.t) / 1000.0;
            c.at(from);
            Vec3 cam = c.camRel(from);
            Vec3 u = Draw.perp(dir), v = dir.cross(u);
            long frame = c.t / 40;
            for (int i = 0; i < 46; i++) {
                double ph = (s * 2.4 + Draw.hash(i * 3L)) % 1.0;
                double d = len * ph * Math.min(1, s * 4);
                double spread = 0.4 + d * 0.16;
                double a = Draw.hash(i * 7L + frame) * Math.PI * 2;
                Vec3 p0 = dir.scale(d).add(u.scale(Math.cos(a) * spread * Draw.hash(i))).add(v.scale(Math.sin(a) * spread * Draw.hash(i)));
                Vec3 p1 = p0.add(dir.scale(1.2 + d * 0.06));
                float al = (float) ((1 - ph) * (1 - p));
                Draw.ribbon(c.vc, c.m(), p0, p1, 0.2 + d * 0.05, 0.05, Draw.alpha(core, al), Draw.alpha(edge, al * 0.5f), cam);
            }
            Draw.glowOrb(c.vc, c.m(), 0, 0, 0, 0.8, core, 0.6f * (1 - p));
            c.pop();
        }
    }

    private CsmFx() {}
}
