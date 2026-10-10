package com.cataclysm.spells.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** World effects for the three cataclysm spells. All procedural geometry. */
public final class SpellFx {
    public static final Vec3 STAR_OFFSET = new Vec3(-45, 150, -30);
    public static final long STAR_FALL_MS = 4100;

    static double groundY(double x, double z, double fallback) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return fallback;
        return mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
    }

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

    // ═══════════════ FALLING STAR ═══════════════

    public static Vec3 starPos(Vec3 target, double ms) {
        double u = Math.pow(Mth.clamp(ms / STAR_FALL_MS, 0, 1), 2.3);
        return target.add(STAR_OFFSET.scale(1 - u));
    }

    public static final class FallingStar extends Vfx.Effect {
        final Vec3 target;

        public FallingStar(Vec3 target) {
            super(STAR_FALL_MS);
            this.target = target;
        }

        @Override
        public void solid(Vfx.Ctx c) {
            Vec3 p = starPos(target, age(c.t));
            c.at(p);
            Draw.sphere(c.vc, c.m(), 0, 0, 0, 1.7, 12, 16, (x, y, z) -> Draw.lerp(0xFF2A1408, 0xFFFF8A2A, (float) Math.max(0, -(x * 0.3 + y * 0.8))));
            c.pop();
        }

        @Override
        public void glow(Vfx.Ctx c) {
            long a = age(c.t);
            Vec3 p = starPos(target, a);
            Vec3 d = STAR_OFFSET.scale(-1).normalize();
            double heat = Math.min(1, a / 2500.0);
            c.at(p);
            Matrix4f m = c.m();
            Vec3 cam = c.camRel(p);
            VertexConsumer vc = c.vc;
            Draw.glowOrb(vc, m, 0, 0, 0, 3.2 + heat * 1.5, 0xFFFFC870, 1f);
            Draw.sphere(vc, m, 0, 0, 0, 7 + heat * 5, 10, 14, Draw.alpha(0xFFFF6A20, 0.12f));
            Draw.cone(vc, m, d.scale(2.6), d.scale(-1), 9, 5 + heat * 2, Draw.alpha(0xFFFFFFFF, 0.7f), Draw.alpha(0xFFFF6A20, 0f), 28);
            Vec3 u = Draw.perp(d), v = d.cross(u);
            double time = a / 1000.0;
            for (int s = 0; s < 6; s++) {
                double ang = s * Math.PI / 3 + time * 2;
                Vec3 off = u.scale(Math.cos(ang) * 1.2).add(v.scale(Math.sin(ang) * 1.2));
                Vec3 tail = d.scale(-(70 + s * 6)).add(off.scale(4));
                int col = s % 2 == 0 ? 0xFFFFE0A0 : 0xFFFF5A20;
                Draw.ribbon(vc, m, off, tail, 2.4 - s * 0.2, 0.0, Draw.alpha(col, 0.95f), Draw.alpha(0xFF6A1A40, 0f), cam);
            }
            for (int k = 0; k < 7; k++) {
                double back = ((time * 40 + k * 9) % 63);
                float al = (float) (1 - back / 63);
                Draw.ring(vc, m, d.scale(-back), u, v, 2.5 + back * 0.12, 3.2 + back * 0.15, Draw.alpha(0xFFFFFFFF, al * 0.6f), 0, 36);
            }
            long frame = c.t / 40;
            for (int k = 0; k < 14; k++) {
                Vec3 sd = Draw.randDir(frame * 31 + k).add(d.scale(-1.6)).normalize();
                Draw.ribbon(vc, m, sd.scale(2.5), sd.scale(6 + Draw.hash(frame + k) * 8), 0.25, 0.0, 0xFFFFFFFF, Draw.alpha(0xFFFF8A2A, 0f), cam);
            }
            c.pop();
            // ground glow as it approaches
            c.at(target.add(0, 0.1, 0));
            float g = (float) Math.pow(a / (double) STAR_FALL_MS, 3);
            Draw.ringXZ(vc, c.m(), Vec3.ZERO, 0, 12 * g + 2, Draw.alpha(0xFFFFC870, 0.5f * g), 0, 48);
            c.pop();
        }
    }

    /** Expanding fireball that cools from white-hot through orange to smoke. */
    public static final class Fireball extends Vfx.Effect {
        final Vec3 at; final double R;

        public Fireball(Vec3 at, double R, long life) {
            super(life); this.at = at; this.R = R;
        }

        double r(long t) {
            return R * Ease.outExpo(Math.min(1f, age(t) / 650f)) * (1 + 0.15 * p(t));
        }

        @Override
        public void solid(Vfx.Ctx c) {
            float p = p(c.t);
            double r = r(c.t);
            int hot = p < 0.15f ? Draw.lerp(0xFFFFF6D0, 0xFFFF9A30, p / 0.15f) : p < 0.45f ? Draw.lerp(0xFFFF9A30, 0xFF7A2410, (p - 0.15f) / 0.3f) : Draw.lerp(0xFF7A2410, 0xFF2E2828, (p - 0.45f) / 0.55f);
            float a = p > 0.7f ? (1 - p) / 0.3f : 1f;
            double time = age(c.t) / 1000.0;
            c.at(at);
            Draw.sphere(c.vc, c.m(), 0, 0, 0, r, 18, 26, (x, y, z) -> {
                float n = (float) (0.75 + 0.25 * Math.sin(x * 7 + time * 3) * Math.sin(z * 6 - time * 2 + y * 5));
                return Draw.alpha(Draw.scaleRgb(hot, n), a * (y < -0.2 ? 0 : 1));
            });
            c.pop();
        }

        @Override
        public void glow(Vfx.Ctx c) {
            float p = p(c.t);
            if (p > 0.5f) return;
            c.at(at);
            Draw.glowOrb(c.vc, c.m(), 0, 0, 0, r(c.t) * 1.05, 0xFFFF8A2A, (0.5f - p) * 1.6f);
            c.pop();
        }
    }

    /** Rising mushroom cloud: a churning stem of smoke spheres with a rolling cap. */
    public static final class Mushroom extends Vfx.Effect {
        final Vec3 at;

        public Mushroom(Vec3 at, long life) {
            super(life); this.at = at;
        }

        @Override
        public void solid(Vfx.Ctx c) {
            float p = p(c.t);
            double s = age(c.t) / 1000.0;
            double h = 32 * Ease.outCubic((float) Math.min(1, s / 3.5));
            float a = p > 0.75f ? (1 - p) / 0.25f : 1f;
            c.at(at);
            for (int i = 0; i < 14; i++) {
                double y = h * i / 14.0;
                double r = 3.2 + Math.sin(i * 1.3 + s * 2) * 0.6 - i * 0.05;
                double wob = Math.sin(s * 1.5 + i) * 0.6;
                int col = Draw.lerp(0xFF4A3028, 0xFF5A5458, (float) i / 14f);
                Draw.sphere(c.vc, c.m(), wob, y, Math.cos(s + i) * 0.5, r, 8, 12, Draw.alpha(col, 0.9f * a));
            }
            double capR = 4 + 10 * Ease.outCubic((float) Math.min(1, s / 4.0));
            for (int k = 0; k < 18; k++) {
                double ang = k * Math.PI * 2 / 18 + s * 0.4;
                double roll = Math.sin(s * 2 + k) * 1.2;
                Draw.sphere(c.vc, c.m(), Math.cos(ang) * capR, h + roll, Math.sin(ang) * capR, 4.2 + Math.sin(k) * 0.8, 8, 12, Draw.alpha(0xFF5E585C, 0.92f * a));
            }
            Draw.sphere(c.vc, c.m(), 0, h + 2.5, 0, capR * 0.85, 10, 14, Draw.alpha(0xFF6A6266, 0.9f * a));
            c.pop();
        }

        @Override
        public void glow(Vfx.Ctx c) {
            float p = p(c.t);
            double s = age(c.t) / 1000.0;
            double h = 32 * Ease.outCubic((float) Math.min(1, s / 3.5));
            c.at(at);
            float a = Math.max(0, 1 - p * 1.6f);
            Draw.glowOrb(c.vc, c.m(), 0, 2, 0, 6, 0xFFFF6A20, a * 0.7f);
            Draw.beam(c.vc, c.m(), Vec3.ZERO, h, 2.5, Draw.alpha(0xFFFF8A2A, a * 0.6f), Draw.alpha(0xFFFF4A10, 0f), 18);
            Draw.glowOrb(c.vc, c.m(), 0, h, 0, 8, 0xFFFF6A20, a * 0.35f);
            c.pop();
        }
    }

    /** A translucent shock dome racing outward. */
    public static final class ShockDome extends Vfx.Effect {
        final Vec3 at; final double R; final int col;

        public ShockDome(Vec3 at, double R, int col, long life) {
            super(life); this.at = at; this.R = R; this.col = col;
        }

        @Override
        public void glow(Vfx.Ctx c) {
            float p = p(c.t);
            double r = R * Ease.outExpo(p);
            c.at(at);
            Draw.sphere(c.vc, c.m(), 0, 0, 0, r, 18, 28, (x, y, z) -> {
                Vec3 view = new Vec3(x * r, y * r, z * r).subtract(c.camRel(at)).normalize();
                float edge = (float) Math.pow(1 - Math.abs(x * view.x + y * view.y + z * view.z), 2);
                return Draw.alpha(col, (1 - p) * (0.08f + 0.6f * edge));
            });
            c.pop();
        }
    }

    // ═══════════════ EVENT HORIZON ═══════════════

    public static final long HOLE_MS = 4200;

    public static final class BlackHole extends Vfx.Effect {
        final Vec3 at;
        final Vec3 axis = new Vec3(0.25, 1, 0.15).normalize();

        public BlackHole(Vec3 at) {
            super(HOLE_MS);
            this.at = at;
        }

        double r(long t) {
            double s = age(t) / 1000.0;
            if (s < 0.8) return 1.2 * Ease.outBack((float) (s / 0.8));
            if (s < 4.0) return Mth.lerp((s - 0.8) / 3.2, 1.2, 2.7);
            return 2.7 * Math.max(0, 1 - (s - 4.0) / 0.2);
        }

        @Override
        public void solid(Vfx.Ctx c) {
            double r = r(c.t);
            if (r < 0.02) return;
            c.at(at);
            Draw.sphere(c.vc, c.m(), 0, 0, 0, r, 16, 24, 0xFF000000);
            Draw.sphere(c.vc, c.m(), 0, 0, 0, r * 1.7, 14, 20, 0x55000000);
            c.pop();
        }

        @Override
        public void glow(Vfx.Ctx c) {
            double r = r(c.t);
            if (r < 0.02) return;
            double s = age(c.t) / 1000.0;
            c.at(at);
            Matrix4f m = c.m();
            VertexConsumer vc = c.vc;
            Vec3 cam = c.camRel(at);
            Vec3 u = Draw.perp(axis), v = axis.cross(u);
            int[] bands = {0xFFFFFFFF, 0xFFFFF0B0, 0xFFFFC040, 0xFFFF8020, 0xFFE04018, 0xFF901830};
            int segs = 72;
            for (int b = 0; b < bands.length; b++) {
                double r0 = r * (1.5 + b * 0.7), r1 = r * (1.5 + (b + 1) * 0.7);
                for (int j = 0; j < segs; j++) {
                    double a0 = 2 * Math.PI * j / segs, a1 = 2 * Math.PI * (j + 1) / segs;
                    float br = (float) (0.45 + 0.55 * Math.max(0, Math.sin(a0 * 3 - s * (9 - b) + b)));
                    float al = br * (1 - b / 7f);
                    Vec3 d0 = u.scale(Math.cos(a0)).add(v.scale(Math.sin(a0)));
                    Vec3 d1 = u.scale(Math.cos(a1)).add(v.scale(Math.sin(a1)));
                    Draw.quad(vc, m, d0.scale(r0), d0.scale(r1), d1.scale(r1), d1.scale(r0),
                            Draw.alpha(bands[b], al), Draw.alpha(bands[b], al * 0.6f), Draw.alpha(bands[b], al * 0.6f), Draw.alpha(bands[b], al));
                }
            }
            Vec3 cl = Vfx.v3(c.cam.getLeftVector()), cu = Vfx.v3(c.cam.getUpVector());
            Draw.ring(vc, m, Vec3.ZERO, cl, cu, r * 1.05, r * 1.32, 0xFFFFFFFF, Draw.alpha(0xFFFFE0A0, 0f), 64);
            Draw.ring(vc, m, Vec3.ZERO, cl, cu, r * 1.32, r * 2.2, Draw.alpha(0xFFFFC070, 0.35f), 0, 64);
            float jet = (float) (0.6 + 0.4 * Math.sin(s * 30));
            glowTube(vc, m, Vec3.ZERO, axis.scale(22), r * 0.35, Draw.alpha(0xFFBFE6FF, jet), Draw.alpha(0xFF6FA8FF, 0f), 14);
            glowTube(vc, m, Vec3.ZERO, axis.scale(-22), r * 0.35, Draw.alpha(0xFFBFE6FF, jet), Draw.alpha(0xFF6FA8FF, 0f), 14);
            for (int k = 0; k < 34; k++) {
                double ph = (s * 0.7 + Draw.hash(k * 5L)) % 1.0;
                double ang = k * 2.399 + ph * 9;
                double rad = r * (2 + 9 * (1 - ph));
                Vec3 a = u.scale(Math.cos(ang) * rad).add(v.scale(Math.sin(ang) * rad)).add(axis.scale((Draw.hash(k) - 0.5) * 4 * (1 - ph)));
                Vec3 b = u.scale(Math.cos(ang + 0.5) * rad * 0.88).add(v.scale(Math.sin(ang + 0.5) * rad * 0.88));
                Draw.ribbon(vc, m, a, b, 0.0, 0.12, 0, Draw.alpha(0xFFFFC070, (float) ph), cam);
            }
            c.pop();
        }
    }

    // ═══════════════ SKYFALL LANCE ═══════════════

    public static final class Sigil extends Vfx.Effect {
        final Vec3 center; final double R; final Vec3 dir;

        public Sigil(Vec3 center, Vec3 dir, double R, long life) {
            super(life); this.center = center; this.R = R; this.dir = dir;
        }

        @Override
        public void glow(Vfx.Ctx c) {
            float p = p(c.t);
            double s = age(c.t) / 1000.0;
            float a = (p < 0.15f ? p / 0.15f : p > 0.85f ? (1 - p) / 0.15f : 1f) * (0.75f + 0.25f * (float) Math.sin(s * 9));
            for (Vec3 at : new Vec3[]{center.add(0, 0.15, 0), center.add(0, 70, 0)}) {
                c.at(at);
                Matrix4f m = c.m();
                VertexConsumer vc = c.vc;
                int gold = 0xFFFFD86A;
                double grow = Ease.outBack(Math.min(1f, p * 5f));
                double RR = R * grow;
                for (double k : new double[]{1.0, 0.93, 0.62, 0.34}) {
                    Draw.ringXZ(vc, m, Vec3.ZERO, RR * k - 0.25, RR * k, Draw.alpha(gold, a), Draw.alpha(0xFFFFFFFF, a), 96);
                }
                for (int i = 0; i < 72; i++) {
                    double ang = i * Math.PI * 2 / 72 + s * 0.6;
                    double len = i % 6 == 0 ? 1.6 : 0.7;
                    Vec3 o = new Vec3(Math.cos(ang) * RR * 0.93, 0.02, Math.sin(ang) * RR * 0.93);
                    Vec3 in = new Vec3(Math.cos(ang) * (RR * 0.93 - len), 0.02, Math.sin(ang) * (RR * 0.93 - len));
                    flatLine(vc, m, o, in, 0.12, Draw.alpha(gold, a));
                }
                for (int star = 0; star < 2; star++) {
                    for (int i = 0; i < 6; i++) {
                        double a0 = (i * 2 + star) * Math.PI / 6 - s * 0.4 * (star == 0 ? 1 : -1);
                        double a1 = a0 + Math.PI * 4 / 6;
                        Vec3 p0 = new Vec3(Math.cos(a0) * RR * 0.62, 0.03, Math.sin(a0) * RR * 0.62);
                        Vec3 p1 = new Vec3(Math.cos(a1) * RR * 0.62, 0.03, Math.sin(a1) * RR * 0.62);
                        flatLine(vc, m, p0, p1, 0.18, Draw.alpha(star == 0 ? gold : 0xFFFFF4D0, a));
                    }
                }
                for (int i = 0; i < 24; i++) {
                    double ang = i * Math.PI * 2 / 24 - s * 0.3;
                    Vec3 rc = new Vec3(Math.cos(ang) * RR * 0.78, 0.03, Math.sin(ang) * RR * 0.78);
                    Vec3 t = new Vec3(-Math.sin(ang), 0, Math.cos(ang));
                    flatLine(vc, m, rc.subtract(t.scale(0.6)), rc.add(t.scale(0.6)), 0.35 + (i % 3) * 0.1, Draw.alpha(gold, a * 0.8f));
                }
                c.pop();
            }
        }

        static void flatLine(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, double w, int col) {
            Vec3 d = b.subtract(a);
            Vec3 side = new Vec3(-d.z, 0, d.x);
            if (side.lengthSqr() < 1.0e-8) return;
            side = side.normalize().scale(w / 2);
            Draw.quad(vc, m, a.add(side), b.add(side), b.subtract(side), a.subtract(side), col);
        }
    }

    public static final class LanceBeam extends Vfx.Effect {
        final Vec3 start, dir; final double length; final long sweepMs = 1550;

        public LanceBeam(Vec3 start, Vec3 dir, double length, long life) {
            super(life); this.start = start; this.dir = dir.normalize(); this.length = length;
        }

        Vec3 contact(long t) {
            double k = Mth.clamp((age(t) - 100) / (double) sweepMs, 0, 1);
            Vec3 p = start.add(dir.scale(length * k));
            return new Vec3(p.x, groundY(p.x, p.z, start.y), p.z);
        }

        @Override
        public void glow(Vfx.Ctx c) {
            float p = p(c.t);
            double s = age(c.t) / 1000.0;
            float a = p > 0.8f ? (1 - p) / 0.2f : 1f;
            double w = (age(c.t) < 120 ? Ease.outBack(age(c.t) / 120f) : 1) * (p > 0.8f ? (1 - p) / 0.2 : 1);
            Vec3 cp = contact(c.t);
            c.at(cp);
            Matrix4f m = c.m();
            VertexConsumer vc = c.vc;
            Vec3 top = new Vec3(0, 170, 0);
            glowTube(vc, m, Vec3.ZERO, top, 3.6 * w, Draw.alpha(0xFFFFC040, 0.45f * a), Draw.alpha(0xFFFFC040, 0.15f * a), 24);
            glowTube(vc, m, Vec3.ZERO, top, 1.5 * w, Draw.alpha(0xFFFFFFFF, 0.95f * a), Draw.alpha(0xFFFFF4D0, 0.6f * a), 18);
            for (int i = 0; i < 9; i++) {
                double y = ((140 - s * 140 + i * 16) % 140 + 140) % 140;
                Draw.ringXZ(vc, m, new Vec3(0, y, 0), 3.8 * w, 6.5 * w, Draw.alpha(0xFFFFFFFF, 0.55f * a), 0, 40);
            }
            float pulse = (float) (0.6 + 0.4 * Math.sin(s * 40));
            Draw.ringXZ(vc, m, new Vec3(0, 0.15, 0), 0, 9 * w, Draw.alpha(0xFFFFF4D0, 0.7f * a * pulse), 0, 48);
            Draw.glowOrb(vc, m, 0, 0.5, 0, 6 * w, 0xFFFFC040, 0.6f * a);
            c.pop();
            // molten trench left behind, cooling from yellow to deep red
            Vec3 side = new Vec3(-dir.z, 0, dir.x).scale(2.6);
            int steps = 40;
            double reached = Mth.clamp((age(c.t) - 100) / (double) sweepMs, 0, 1);
            c.at(start);
            for (int i = 0; i < steps; i++) {
                double k0 = reached * i / steps, k1 = reached * (i + 1) / steps;
                Vec3 p0 = dir.scale(length * k0), p1 = dir.scale(length * k1);
                double y0 = groundY(start.x + p0.x, start.z + p0.z, start.y) - start.y - 3.5;
                double y1 = groundY(start.x + p1.x, start.z + p1.z, start.y) - start.y - 3.5;
                p0 = p0.add(0, y0 + 0.05, 0);
                p1 = p1.add(0, y1 + 0.05, 0);
                float age01 = (float) Math.min(1, (reached - k0) * 2.5);
                int col = Draw.lerp(0xFFFFE070, 0xFF801808, age01);
                Draw.quad(vc, c.m(), p0.add(side), p1.add(side), p1.subtract(side), p0.subtract(side), Draw.alpha(col, a * (1 - age01 * 0.5f)));
            }
            c.pop();
        }
    }

    private SpellFx() {}
}
