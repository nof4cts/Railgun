package com.sixpaths.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.sixpaths.client.Mesh.Frame;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Effects for the Six Paths techniques. Everything is procedural geometry: force shells,
 * gravity cores, soul wisps, summoning circles, smoke. No vanilla particles anywhere.
 */
public final class PainFx {

    /** Effects that draw cel-shaded Mesh geometry (ink outline + fill) instead of plain quads. */
    public interface Modeled {
        Vec3 origin(long t);

        void model(long t);

        default boolean visible(long t) {
            return true;
        }
    }

    static Vec3 hand(Entity e, float partial) {
        Vec3 eye = e.getEyePosition(partial);
        float yaw = (e instanceof LivingEntity le ? Mth.rotLerp(partial, le.yBodyRotO, le.yBodyRot) : e.getYRot()) * Mth.DEG_TO_RAD;
        Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
        return eye.add(e.getViewVector(partial).scale(0.7)).add(right.scale(0.35)).add(0, -0.45, 0);
    }

    // ═══════════════════════ DEVA: ALMIGHTY PUSH ═══════════════════════

    /** The repulsion: nested refracting shells, a pressure disc skimming the ground and radial streaks. */
    public static final class ForcePush extends Vfx.Effect {
        final Vec3 at; final double R; final boolean big;

        public ForcePush(Vec3 at, double R, boolean big, long life) {
            super(life); this.at = at; this.R = R; this.big = big;
        }

        @Override
        public void glow(Vfx.Ctx c) {
            float p = p(c.t);
            if (age(c.t) < 380) ScreenFx.lens(at, R * 0.6 * Ease.outExpo(age(c.t) / 380f));
            PostPipeline.keep(0.7f * (1 - p));
            c.at(at);
            Matrix4f m = c.m();
            Vec3 cam = c.camRel(at);
            for (int s = 0; s < 3; s++) {
                float ps = Mth.clamp(p * 1.25f - s * 0.1f, 0, 1);
                double r = R * Ease.outExpo(ps) * (1 - s * 0.12);
                float a = (1 - ps) * (s == 0 ? 0.9f : 0.5f);
                if (a <= 0.01f) continue;
                Draw.sphere(c.vc, m, 0, 0, 0, r, 18, 28, (x, y, z) -> {
                    Vec3 view = new Vec3(x * r, y * r, z * r).subtract(cam).normalize();
                    float edge = (float) Math.pow(1 - Math.abs(x * view.x + y * view.y + z * view.z), 3);
                    return Draw.alpha(Draw.lerp(0xFFD8D0FF, 0xFFFFFFFF, edge), a * (0.03f + 0.75f * edge));
                });
            }
            // ground pressure disc
            double gr = R * 1.3 * Ease.outExpo(p);
            Draw.ringXZ(c.vc, m, new Vec3(0, -0.9, 0), gr * 0.55, gr, Draw.alpha(0xFFFFFFFF, 0), Draw.alpha(0xFFE8E0FF, (1 - p) * 0.8f), 64);
            // radial pressure streaks — the air itself being shoved outward
            for (int i = 0; i < 40; i++) {
                double ang = i * Math.PI * 2 / 40 + Draw.hash(i) * 0.15;
                double y = (Draw.hash(i * 3L) - 0.4) * R * 0.7;
                double r0 = R * (0.2 + 0.9 * Ease.outCubic(p)), r1 = r0 + R * (0.25 + Draw.hash(i * 5L) * 0.35) * (1 - p);
                Vec3 d = new Vec3(Math.cos(ang), 0, Math.sin(ang));
                Draw.ribbon(c.vc, m, d.scale(r0).add(0, y, 0), d.scale(r1).add(0, y, 0), 0.0, 0.09, Draw.alpha(0xFFFFFFFF, 0), Draw.alpha(0xFFFFFFFF, (1 - p) * 0.9f), cam);
            }
            if (p < 0.25f) Draw.glowOrb(c.vc, m, 0, 0, 0, 1.6 + R * 0.15 * p, 0xFFFFFFFF, (0.25f - p) * 4f);
            c.pop();
        }
    }

    // ═══════════════════════ DEVA: UNIVERSAL PULL ═══════════════════════

    /** Twisting gravity strands winding from the victim into the caster's palm. */
    public static final class PullStream extends Vfx.Effect {
        final int src, tgt;

        public PullStream(int src, int tgt, long life) {
            super(life); this.src = src; this.tgt = tgt;
        }

        @Override
        public void glow(Vfx.Ctx c) {
            Entity s = Vfx.entity(src), t = Vfx.entity(tgt);
            if (s == null || t == null) return;
            float p = p(c.t);
            Vec3 a = hand(s, c.partial);
            Vec3 b = t.getPosition(c.partial).add(0, t.getBbHeight() * 0.55, 0);
            Vec3 d = b.subtract(a);
            double len = d.length();
            if (len < 0.1) return;
            Vec3 dn = d.normalize(), u = Draw.perp(dn), v = dn.cross(u);
            double time = age(c.t) / 1000.0;
            c.at(a);
            Vec3 cam = c.camRel(a);
            float fade = 1 - p * p;
            for (int strand = 0; strand < 5; strand++) {
                Vec3 prev = null;
                for (int i = 0; i <= 24; i++) {
                    double k = i / 24.0;
                    double rad = 0.35 * Math.sin(Math.PI * k) * (1 + strand * 0.2);
                    double ang = strand * 1.2566 + k * 9 - time * 14;
                    Vec3 q = dn.scale(len * k).add(u.scale(Math.cos(ang) * rad)).add(v.scale(Math.sin(ang) * rad));
                    if (prev != null) Draw.ribbon(c.vc, c.m(), prev, q, 0.05, 0.05, Draw.alpha(0xFFB9A8FF, fade * 0.8f), Draw.alpha(0xFFFFFFFF, fade * 0.9f), cam);
                    prev = q;
                }
            }
            for (int i = 0; i < 6; i++) {
                double ph = (time * 2.5 + i / 6.0) % 1.0;
                Vec3 q = dn.scale(len * (1 - ph));
                Draw.ring(c.vc, c.m(), q, u, v, 0.2 + 0.5 * (1 - ph), 0.28 + 0.55 * (1 - ph), Draw.alpha(0xFFB9A8FF, 0), Draw.alpha(0xFFE0D8FF, fade * (float) ph), 24);
            }
            Draw.glowOrb(c.vc, c.m(), 0, 0, 0, 0.45, 0xFFB9A8FF, fade);
            c.pop();
            PostPipeline.light(a, 0.4f * fade, 0xFFB9A8FF);
        }
    }

    // ═══════════════════════ PLANETARY DEVASTATION ═══════════════════════

    /** The black core flung from the caster's hand up to its station in the sky. */
    public static final class CoreThrow extends Vfx.Effect {
        final int src; final Vec3 to; Vec3 from;

        public CoreThrow(int src, Vec3 to, long life) {
            super(life); this.src = src; this.to = to;
        }

        Vec3 pos(Vfx.Ctx c) {
            if (from == null) {
                Entity s = Vfx.entity(src);
                from = s == null ? to.subtract(0, 10, 0) : hand(s, c.partial);
            }
            float k = Ease.inOut(p(c.t));
            return from.lerp(to, k).add(0, Math.sin(Math.PI * k) * 3, 0);
        }

        @Override
        public void solid(Vfx.Ctx c) {
            Vec3 p = pos(c);
            c.at(p);
            Draw.sphere(c.vc, c.m(), 0, 0, 0, 0.35, 10, 14, 0xFF000000);
            c.pop();
        }

        @Override
        public void glow(Vfx.Ctx c) {
            Vec3 p = pos(c);
            Vec3 back = from.subtract(p);
            c.at(p);
            Draw.glowOrb(c.vc, c.m(), 0, 0, 0, 0.9, 0xFF9B6CFF, 0.8f);
            if (back.lengthSqr() > 0.01)
                Draw.ribbon(c.vc, c.m(), Vec3.ZERO, back.normalize().scale(Math.min(6, back.length())), 0.25, 0.0, 0xFFE0D0FF, Draw.alpha(0xFF6A3AFF, 0), c.camRel(p));
            c.pop();
        }
    }

    /** The gravity core hanging in the sky: an ink-black singularity in a violet halo, swallowing light. */
    public static final class GravityCore extends Vfx.Effect {
        final Vec3 at;

        public GravityCore(Vec3 at, long life) {
            super(life); this.at = at;
        }

        double r(long t) {
            double s = age(t) / 1000.0, end = life / 1000.0;
            if (s < 0.5) return 1.4 * Ease.outBack((float) (s / 0.5));
            if (s > end - 0.3) return 1.4 + 2.4 * ((s - (end - 0.3)) / 0.3);
            return 1.4 + 0.25 * Math.sin(s * 6);
        }

        @Override
        public void solid(Vfx.Ctx c) {
            c.at(at);
            Draw.sphere(c.vc, c.m(), 0, 0, 0, r(c.t), 16, 24, 0xFF000000);
            c.pop();
        }

        @Override
        public void glow(Vfx.Ctx c) {
            double r = r(c.t), s = age(c.t) / 1000.0;
            ScreenFx.lens(at, r * 2.2);
            PostPipeline.keep(0.8f);
            PostPipeline.light(at, 0.5f, 0xFF9B6CFF);
            c.at(at);
            Matrix4f m = c.m();
            VertexConsumer vc = c.vc;
            Vec3 cl = Vfx.v3(c.cam.getLeftVector()), cu = Vfx.v3(c.cam.getUpVector());
            Draw.ring(vc, m, Vec3.ZERO, cl, cu, r * 1.02, r * 1.25, 0xFFFFFFFF, Draw.alpha(0xFFB9A8FF, 0), 64);
            Draw.ring(vc, m, Vec3.ZERO, cl, cu, r * 1.25, r * 3.2, Draw.alpha(0xFF9B6CFF, 0.45f), 0, 64);
            for (int k = 0; k < 3; k++) {
                Vec3 ax = Draw.randDir(k * 91L);
                Vec3 u = Draw.perp(ax), v = ax.cross(u);
                double a = s * (1.2 + k * 0.4);
                Vec3 ru = u.scale(Math.cos(a)).add(v.scale(Math.sin(a))), rv = ax;
                Draw.ring(vc, m, Vec3.ZERO, ru, rv.cross(ru), r * (1.9 + k * 0.5), r * (1.96 + k * 0.5), Draw.alpha(0xFFE0D8FF, 0.7f), Draw.alpha(0xFFE0D8FF, 0.7f), 72);
            }
            // light and dust being dragged in
            Vec3 cam = c.camRel(at);
            for (int i = 0; i < 40; i++) {
                double ph = (s * 0.6 + Draw.hash(i * 5L)) % 1.0;
                Vec3 d = Draw.randDir(i * 13L);
                double rad = r * (1.4 + 14 * (1 - ph));
                Vec3 a = d.scale(rad), b = d.scale(rad * 0.82);
                Draw.ribbon(vc, m, a, b, 0.0, 0.08, 0, Draw.alpha(0xFFD8C8FF, (float) ph * 0.9f), cam);
            }
            c.pop();
        }
    }

    /** Boulders torn from the ground, spiralling up into the forming moon (on top of the real ripped blocks). */
    public static final class RockSpiral extends Vfx.Effect {
        final Vec3 core, ground; final int n; final int col;

        public RockSpiral(Vec3 core, Vec3 ground, int n, long life) {
            super(life); this.core = core; this.ground = ground; this.n = n;
            this.col = Vfx.groundColor(ground);
        }

        Vec3 rock(int i, double s) {
            double delay = Draw.hash(i * 7L) * (life / 1000.0) * 0.75;
            double k = Mth.clamp((s - delay) / 1.6, 0, 1);
            double ang0 = Draw.hash(i) * Math.PI * 2, r0 = 4 + Draw.hash(i * 3L) * 16;
            Vec3 start = ground.add(Math.cos(ang0) * r0, 0, Math.sin(ang0) * r0);
            if (k <= 0) return null;
            double e = Ease.inOut((float) k);
            Vec3 shell = Draw.randDir(i * 29L).scale(6.6 + Draw.hash(i * 31L) * 0.8);
            double swirl = (1 - e) * 2.5 + s * 0.25;
            Vec3 sw = new Vec3(shell.x * Math.cos(swirl) - shell.z * Math.sin(swirl), shell.y, shell.x * Math.sin(swirl) + shell.z * Math.cos(swirl));
            return start.lerp(core.add(sw), e).add(0, Math.sin(Math.PI * e) * 3, 0);
        }

        @Override
        public void solid(Vfx.Ctx c) {
            double s = age(c.t) / 1000.0;
            for (int i = 0; i < n; i++) {
                Vec3 p = rock(i, s);
                if (p == null) continue;
                double sz = 0.35 + Draw.hash(i * 11L) * 0.9;
                c.at(p);
                int cc = Draw.scaleRgb(col, (float) (0.7 + Draw.hash(i * 2L) * 0.4));
                Draw.sphere(c.vc, c.m(), 0, 0, 0, sz, 5, 7, (x, y, z) -> Draw.scaleRgb(cc, (float) (0.7 + 0.3 * y)));
                c.pop();
            }
        }

        @Override
        public void glow(Vfx.Ctx c) {
            double s = age(c.t) / 1000.0;
            c.at(core);
            Vec3 cam = c.camRel(core);
            for (int i = 0; i < n; i += 3) {
                Vec3 p = rock(i, s), q = rock(i, s - 0.12);
                if (p == null || q == null) continue;
                Draw.ribbon(c.vc, c.m(), q.subtract(core), p.subtract(core), 0.0, 0.18, 0, Draw.alpha(0xFFB9A8FF, 0.35f), cam);
            }
            c.pop();
        }
    }

    /** The finished moon (visual shell over the real block sphere), cracking away after a beat. */
    public static final class Moon extends Vfx.Effect implements Modeled {
        final Vec3 at; final double R; final int rock, rock2; final long seed;

        public Moon(Vec3 at, double R, long life) {
            super(life); this.at = at; this.R = R;
            int g = Vfx.groundColor(at.subtract(0, 14, 0));
            this.rock = Draw.scaleRgb(g, 0.8f);
            this.rock2 = Draw.lerp(g, 0xFF3A3436, 0.5f);
            this.seed = Vfx.now() % 1000;
        }

        public Vec3 origin(long t) { return at; }

        public void model(long t) {
            float k = Ease.outBack(Math.min(1f, age(t) / 260f));
            double crack = age(t) > life - 500 ? (age(t) - (life - 500)) / 500.0 : 0;
            double r = R * (0.2 + 0.8 * k) * (1 - crack * 0.15);
            Models.moon(r, seed, rock, rock2);
        }

        @Override
        public void glow(Vfx.Ctx c) {
            float a = 1 - p(c.t);
            c.at(at);
            Vec3 cl = Vfx.v3(c.cam.getLeftVector()), cu = Vfx.v3(c.cam.getUpVector());
            Draw.ring(c.vc, c.m(), Vec3.ZERO, cl, cu, R * 1.02, R * 1.6, Draw.alpha(0xFF9B6CFF, 0.35f * a), 0, 64);
            c.pop();
        }
    }

    // ═══════════════════════ HEAVENLY DESCENT ═══════════════════════

    /** Light gathering under the caster as they rise: a halo disc and rising motes. */
    public static final class RiseHalo extends Vfx.Effect {
        final int id;

        public RiseHalo(int id, long life) {
            super(life); this.id = id;
        }

        @Override
        public void glow(Vfx.Ctx c) {
            Entity e = Vfx.entity(id);
            if (e == null) return;
            Vec3 p = e.getPosition(c.partial);
            float a = Math.min(1f, age(c.t) / 300f) * (1 - p(c.t));
            double s = age(c.t) / 1000.0;
            c.at(p);
            Matrix4f m = c.m();
            Draw.ringXZ(c.vc, m, new Vec3(0, -0.2, 0), 0.6, 2.4, Draw.alpha(0xFFFFFFFF, a * 0.8f), Draw.alpha(0xFFE0E8FF, 0), 48);
            Draw.ringXZ(c.vc, m, new Vec3(0, -0.25, 0), 2.6 + Math.sin(s * 4) * 0.2, 2.8 + Math.sin(s * 4) * 0.2, Draw.alpha(0xFFFFFFFF, a), Draw.alpha(0xFFFFFFFF, a), 48);
            Draw.beam(c.vc, m, new Vec3(0, -12, 0), 12, 0.6, Draw.alpha(0xFFE0E8FF, 0), Draw.alpha(0xFFFFFFFF, a * 0.4f), 16);
            Vec3 cam = c.camRel(p);
            for (int i = 0; i < 24; i++) {
                double ph = (s * 0.8 + Draw.hash(i)) % 1.0;
                double ang = Draw.hash(i * 3L) * Math.PI * 2, rr = 0.5 + Draw.hash(i * 5L) * 2.5;
                Vec3 q = new Vec3(Math.cos(ang) * rr, -6 + ph * 8, Math.sin(ang) * rr);
                Draw.ribbon(c.vc, m, q, q.add(0, 0.8, 0), 0.04, 0.0, Draw.alpha(0xFFFFFFFF, a * (float) (1 - ph)), 0, cam);
            }
            c.pop();
            PostPipeline.light(p, 0.6f * a, 0xFFFFFFFF);
        }
    }

    /** The descending force: a pillar of compressed air slamming from the caster to the ground. */
    public static final class PressureColumn extends Vfx.Effect {
        final Vec3 ground; final double h;

        public PressureColumn(Vec3 ground, double h, long life) {
            super(life); this.ground = ground; this.h = h;
        }

        @Override
        public void glow(Vfx.Ctx c) {
            float p = p(c.t);
            float a = (1 - p) * (1 - p);
            double r = 2 + 10 * Ease.outExpo(p);
            c.at(ground);
            Matrix4f m = c.m();
            Draw.beam(c.vc, m, Vec3.ZERO, h, r, Draw.alpha(0xFFFFFFFF, a * 0.55f), Draw.alpha(0xFFE8E4FF, a * 0.1f), 32);
            Draw.beam(c.vc, m, Vec3.ZERO, h, r * 0.35, Draw.alpha(0xFFFFFFFF, a), Draw.alpha(0xFFFFFFFF, a * 0.4f), 20);
            for (int i = 0; i < 6; i++) {
                double y = h * ((age(c.t) / 300.0 + i / 6.0) % 1.0);
                Draw.ringXZ(c.vc, m, new Vec3(0, h - y, 0), r * 0.9, r * 1.25, Draw.alpha(0xFFFFFFFF, a * 0.7f), Draw.alpha(0xFFFFFFFF, 0), 48);
            }
            c.pop();
        }
    }

    // ═══════════════════════ ASURA ═══════════════════════

    /** Grey exhaust puff left behind by missiles. */
    public static final class Puff extends Vfx.Effect {
        final Vec3 at; final double r; final int col;

        public Puff(Vec3 at, double r, int col, long life) {
            super(life); this.at = at; this.r = r; this.col = col;
        }

        @Override
        public void solid(Vfx.Ctx c) {
            float p = p(c.t);
            c.at(at.add(0, p * 0.6, 0));
            Draw.sphere(c.vc, c.m(), 0, 0, 0, r * (0.5 + p), 6, 8, Draw.alpha(col, (1 - p) * 0.75f));
            c.pop();
        }
    }

    /** Energy pooling in the Asura arm before the cannon fires. */
    public static final class CannonCharge extends Vfx.Effect {
        final int id;

        public CannonCharge(int id, long life) {
            super(life); this.id = id;
        }

        @Override
        public void glow(Vfx.Ctx c) {
            Entity e = Vfx.entity(id);
            if (e == null) return;
            float p = p(c.t);
            Vec3 h = hand(e, c.partial).add(e.getViewVector(c.partial).scale(0.6));
            Plasma.sun(h, 0.3 + 0.9 * p, 0xFF5CE1FF, 0.6f + p);
            PostPipeline.keep(0.8f);
            PostPipeline.light(h, 0.8f * p, 0xFF5CE1FF);
            c.at(h);
            Vec3 cam = c.camRel(h);
            double s = age(c.t) / 1000.0;
            for (int i = 0; i < 26; i++) {
                double ph = (s * 2.2 + Draw.hash(i)) % 1.0;
                Vec3 d = Draw.randDir(i * 19L);
                Draw.ribbon(c.vc, c.m(), d.scale(4.5 * (1 - ph)), d.scale(4.5 * (1 - ph) * 0.7), 0.0, 0.05, 0, Draw.alpha(0xFFBFF4FF, (float) ph), cam);
            }
            Vec3 cl = Vfx.v3(c.cam.getLeftVector()), cu = Vfx.v3(c.cam.getUpVector());
            Draw.ring(c.vc, c.m(), Vec3.ZERO, cl, cu, 0.4 + 2.6 * (1 - p), 0.46 + 2.7 * (1 - p), Draw.alpha(0xFFFFFFFF, p), Draw.alpha(0xFF5CE1FF, p), 48);
            c.pop();
        }
    }

    /** The cannon beam: a plasma core in a spiralling sheath, from the arm to the impact. */
    public static final class CannonBeam extends Vfx.Effect {
        final int id; final Vec3 end; Vec3 start;

        public CannonBeam(int id, Vec3 end, long life) {
            super(life); this.id = id; this.end = end;
        }

        @Override
        public void glow(Vfx.Ctx c) {
            if (start == null) {
                Entity e = Vfx.entity(id);
                start = e == null ? end.subtract(0, 0, 20) : hand(e, c.partial).add(e.getViewVector(c.partial).scale(0.6));
            }
            float p = p(c.t);
            Vec3 d = end.subtract(start);
            double len = d.length();
            if (len < 0.1) return;
            Vec3 dn = d.normalize();
            float w = p < 0.1f ? p / 0.1f : (1 - p) / 0.9f;
            double hw = 0.9 * w + 0.05;
            Plasma.beam(start, dn, len, hw, 0xFF5CE1FF, 1.2f * w);
            PostPipeline.keep(1f);
            PostPipeline.light(start.lerp(end, 0.3), 1.2f * w, 0xFF5CE1FF);
            c.at(start);
            Vec3 cam = c.camRel(start);
            SpellFx.glowTube(c.vc, c.m(), Vec3.ZERO, d, hw * 1.6, Draw.alpha(0xFF5CE1FF, 0.35f * w), Draw.alpha(0xFF5CE1FF, 0.25f * w), 18);
            Vec3 u = Draw.perp(dn), v = dn.cross(u);
            double s = age(c.t) / 1000.0;
            Vec3 prev = null;
            for (int i = 0; i <= 80; i++) {
                double k = i / 80.0;
                double ang = k * len * 1.6 - s * 30;
                Vec3 q = dn.scale(len * k).add(u.scale(Math.cos(ang) * hw * 2.2)).add(v.scale(Math.sin(ang) * hw * 2.2));
                if (prev != null) Draw.ribbon(c.vc, c.m(), prev, q, 0.06 * w, 0.06 * w, Draw.alpha(0xFFFFFFFF, w), Draw.alpha(0xFFBFF4FF, w), cam);
                prev = q;
            }
            for (int i = 0; i < 6; i++) {
                double k = ((s * 3 + i / 6.0) % 1.0);
                Draw.ring(c.vc, c.m(), dn.scale(len * k), u, v, hw * 2.6, hw * 3.4, Draw.alpha(0xFFFFFFFF, w * 0.6f), Draw.alpha(0xFF5CE1FF, 0), 32);
            }
            c.pop();
        }
    }

    // ═══════════════════════ HUMAN PATH ═══════════════════════

    /** A soul being pulled out of a body: a pale, stretching ghost-form drawn toward the caster's hand. */
    public static final class SoulPull extends Vfx.Effect {
        final int src, tgt; final boolean tear;

        public SoulPull(int src, int tgt, boolean tear, long life) {
            super(life); this.src = src; this.tgt = tgt; this.tear = tear;
        }

        @Override
        public void glow(Vfx.Ctx c) {
            Entity s = Vfx.entity(src), t = Vfx.entity(tgt);
            if (s == null) return;
            float p = p(c.t);
            Vec3 h = hand(s, c.partial);
            Vec3 body = t != null ? t.getPosition(c.partial).add(0, t.getBbHeight() * 0.55, 0) : h.add(s.getViewVector(c.partial).scale(1.5));
            double stretch = tear ? Math.min(1, p * 1.4) : 0.35 + 0.15 * Math.sin(age(c.t) / 120.0);
            Vec3 headPos = body.lerp(h, stretch * 0.9);
            float a = tear ? (1 - p) : Math.min(1f, age(c.t) / 300f);
            double time = age(c.t) / 1000.0;
            c.at(body);
            Matrix4f m = c.m();
            Vec3 cam = c.camRel(body);
            Vec3 d = headPos.subtract(body);
            // the ghost's body: a stack of glowing ellipses from torso (in the victim) to head (in the hand)
            for (int i = 0; i <= 10; i++) {
                double k = i / 10.0;
                Vec3 q = d.scale(k).add(0, Math.sin(k * Math.PI) * 0.3, 0).add(Math.sin(time * 9 + k * 5) * 0.06, 0, 0);
                double r = (0.32 + 0.12 * Math.sin(k * Math.PI)) * (1 - k * 0.3);
                Draw.glowOrb(c.vc, m, q.x, q.y, q.z, r, 0xFF9FFFE8, a * 0.55f);
            }
            Draw.glowOrb(c.vc, m, d.x, d.y + 0.1, d.z, 0.42, 0xFFE8FFFA, a * 0.9f);
            // wailing streamers trailing back into the body
            for (int i = 0; i < 9; i++) {
                double ang = i * 0.7 + time * 3;
                Vec3 off = new Vec3(Math.cos(ang) * 0.4, Math.sin(ang * 1.3) * 0.5, Math.sin(ang) * 0.4);
                Draw.ribbon(c.vc, m, d.scale(0.9), off, 0.12, 0.0, Draw.alpha(0xFFFFFFFF, a * 0.7f), Draw.alpha(0xFF9FFFE8, 0), cam);
            }
            c.pop();
        }
    }

    // ═══════════════════════ ANIMAL PATH ═══════════════════════

    /** Original summoning circle: three rings, radial spokes and orbiting glyph ticks, inked into the ground. */
    public static final class SummonCircle extends Vfx.Effect {
        final Vec3 at; final double R; final int col;

        public SummonCircle(Vec3 at, double R, int col, long life) {
            super(life); this.at = at.add(0, 0.06, 0); this.R = R; this.col = col;
        }

        @Override
        public void glow(Vfx.Ctx c) {
            float p = p(c.t);
            float draw = Ease.outCubic(Math.min(1f, age(c.t) / 380f));
            float a = p > 0.6f ? (1 - p) / 0.4f : 1f;
            double s = age(c.t) / 1000.0;
            c.at(at);
            Matrix4f m = c.m();
            int ink = Draw.alpha(0xFFFFFFFF, a), tint = Draw.alpha(col, a * 0.8f);
            Draw.ringXZ(c.vc, m, Vec3.ZERO, R * draw - 0.12, R * draw, tint, ink, 64);
            Draw.ringXZ(c.vc, m, Vec3.ZERO, R * 0.72 * draw - 0.06, R * 0.72 * draw, ink, ink, 64);
            Draw.ringXZ(c.vc, m, Vec3.ZERO, R * 0.3 * draw - 0.05, R * 0.3 * draw, ink, ink, 48);
            Draw.ringXZ(c.vc, m, Vec3.ZERO, 0, R * draw, Draw.alpha(col, a * 0.25f), Draw.alpha(col, 0), 48);
            for (int i = 0; i < 12; i++) {
                double ang = i * Math.PI / 6 + s * 0.4;
                Vec3 dd = new Vec3(Math.cos(ang), 0, Math.sin(ang));
                Vec3 side = new Vec3(-dd.z, 0, dd.x).scale(0.05);
                Vec3 i0 = dd.scale(R * 0.3 * draw), o0 = dd.scale(R * 0.72 * draw);
                Draw.quad(c.vc, m, i0.add(side), o0.add(side), o0.subtract(side), i0.subtract(side), ink);
                double ga = ang + Math.PI / 12;
                Vec3 g = new Vec3(Math.cos(ga), 0, Math.sin(ga)).scale(R * 0.86 * draw);
                Vec3 gs = new Vec3(-Math.sin(ga), 0, Math.cos(ga)).scale(0.18);
                Vec3 gr = new Vec3(Math.cos(ga), 0, Math.sin(ga)).scale(0.22);
                Draw.quad(c.vc, m, g.add(gs).subtract(gr), g.add(gs).add(gr), g.subtract(gs).add(gr), g.subtract(gs).subtract(gr), Draw.alpha(col, a));
            }
            c.pop();
        }
    }

    /** A dense burst of white smoke — the summon / dismiss poof. */
    public static final class Smoke extends Vfx.Effect {
        final Vec3 at; final double R; final long seed = Vfx.now();

        public Smoke(Vec3 at, double R, long life) {
            super(life); this.at = at; this.R = R;
        }

        @Override
        public void solid(Vfx.Ctx c) {
            float p = p(c.t);
            float a = (p < 0.08f ? p / 0.08f : 1f) * (1 - p) * 0.95f;
            c.at(at);
            int n = 26;
            for (int i = 0; i < n; i++) {
                Vec3 d = Draw.randDir(seed + i * 5L);
                d = new Vec3(d.x, Math.abs(d.y) * 0.8 + 0.1, d.z);
                double out = R * (0.2 + 0.9 * Ease.outExpo(p)) * (0.6 + Draw.hash(seed + i) * 0.5);
                double r = R * (0.35 + 0.3 * Draw.hash(seed + i * 3L)) * (0.7 + p * 0.6);
                int col = Draw.lerp(0xFFFFFFFF, 0xFFB8B4C0, (float) Draw.hash(seed + i * 7L) * 0.6f + p * 0.3f);
                Draw.sphere(c.vc, c.m(), d.x * out, d.y * out + R * 0.3, d.z * out, r, 7, 10, (x, y, z) -> Draw.alpha(Draw.scaleRgb(col, (float) (0.82 + 0.18 * y)), a));
            }
            c.pop();
        }
    }

    // ═══════════════════════ PRETA PATH ═══════════════════════

    /** The absorption seal: a mint shell of latitude bands around the caster, rippling as it feeds. */
    public static final class PretaShell extends Vfx.Effect {
        final int id;

        public PretaShell(int id, long life) {
            super(life); this.id = id;
        }

        @Override
        public void glow(Vfx.Ctx c) {
            Entity e = Vfx.entity(id);
            if (e == null) return;
            Vec3 p = e.getPosition(c.partial).add(0, e.getBbHeight() / 2, 0);
            float in = Math.min(1f, age(c.t) / 220f), out = Math.min(1f, (life - age(c.t)) / 300f);
            float a = in * out;
            double s = age(c.t) / 1000.0;
            double r = 1.7 + 0.05 * Math.sin(s * 8);
            c.at(p);
            Vec3 cam = c.camRel(p);
            Draw.sphere(c.vc, c.m(), 0, 0, 0, r, 16, 24, (x, y, z) -> {
                double band = Math.abs(Math.sin(y * 9 - s * 4));
                double hex = Math.abs(Math.sin(Math.atan2(z, x) * 6 + y * 3));
                Vec3 view = new Vec3(x * r, y * r, z * r).subtract(cam).normalize();
                float edge = (float) Math.pow(1 - Math.abs(x * view.x + y * view.y + z * view.z), 2);
                float line = (band > 0.92 || hex > 0.97) ? 0.6f : 0f;
                return Draw.alpha(0xFF7AFFB0, a * (0.04f + 0.4f * edge + line));
            });
            Draw.ringXZ(c.vc, c.m(), new Vec3(0, -e.getBbHeight() / 2 + 0.05, 0), r * 0.9, r * 1.15, Draw.alpha(0xFF7AFFB0, a), Draw.alpha(0xFF7AFFB0, 0), 40);
            c.pop();
        }
    }

    /** Energy streaming into the caster's body (Preta feeding, drain). */
    public static final class Drain extends Vfx.Effect {
        final Vec3 from; final int to; final int col;

        public Drain(Vec3 from, int to, int col, long life) {
            super(life); this.from = from; this.to = to; this.col = col;
        }

        @Override
        public void glow(Vfx.Ctx c) {
            Entity e = Vfx.entity(to);
            if (e == null) return;
            Vec3 dst = e.getPosition(c.partial).add(0, e.getBbHeight() * 0.6, 0);
            float p = p(c.t);
            Vec3 d = dst.subtract(from);
            c.at(from);
            Vec3 cam = c.camRel(from);
            for (int i = 0; i < 8; i++) {
                double k0 = Mth.clamp(p * 1.4 - i * 0.05, 0, 1), k1 = Mth.clamp(k0 - 0.25, 0, 1);
                Vec3 off = Draw.randDir(i * 7L).scale(0.4 * Math.sin(Math.PI * k0));
                Draw.ribbon(c.vc, c.m(), d.scale(k1), d.scale(k0).add(off), 0.0, 0.08, 0, Draw.alpha(col, 1 - p), cam);
            }
            Draw.glowOrb(c.vc, c.m(), 0, 0, 0, 0.5 * (1 - p), col, 1 - p);
            c.pop();
        }
    }

    // ═══════════════════════ NARAKA PATH ═══════════════════════

    /** The King of Hell rising out of the earth, opening its jaw, then sinking back. */
    public static final class King extends Vfx.Effect implements Modeled {
        final Vec3 at; final float yaw; final double scale; final int skin, metal, eye, flame;
        final long riseMs, openAt, sinkAt;

        public King(Vec3 at, float yaw, double scale, int skin, int metal, int eye, int flame, long life, long riseMs, long openAt, long sinkAt) {
            super(life); this.at = at; this.yaw = yaw; this.scale = scale; this.skin = skin; this.metal = metal; this.eye = eye; this.flame = flame;
            this.riseMs = riseMs; this.openAt = openAt; this.sinkAt = sinkAt;
        }

        double lift(long t) {
            long a = age(t);
            if (a < riseMs) return -6.5 * scale * (1 - Ease.outCubic(a / (float) riseMs));
            if (a > sinkAt) return -6.5 * scale * Ease.inExpo(Math.min(1f, (a - sinkAt) / (float) (life - sinkAt)));
            return Math.sin(a / 400.0) * 0.1 * scale;
        }

        double jaw(long t) {
            long a = age(t);
            if (a < openAt) return 0.05;
            double k = Math.min(1, (a - openAt) / 300.0);
            double close = a > sinkAt - 300 ? Math.min(1, (a - (sinkAt - 300)) / 300.0) : 0;
            return 0.05 + 0.75 * Ease.outBack((float) k) * (1 - close);
        }

        public Vec3 origin(long t) { return at.add(0, lift(t), 0); }

        public void model(long t) {
            Models.king(Frame.yaw(yaw), scale, jaw(t), skin, metal, eye);
        }

        @Override
        public void glow(Vfx.Ctx c) {
            Vec3 o = origin(c.t);
            float a = Math.min(1f, age(c.t) / 300f) * Math.min(1f, (life - age(c.t)) / 400f);
            double s = age(c.t) / 1000.0;
            PostPipeline.keep(0.6f * a);
            PostPipeline.light(o.add(0, 2.4 * scale, 0), 0.7f * a, flame);
            c.at(at);
            Matrix4f m = c.m();
            Vec3 cam = c.camRel(at);
            // a ring of ghost-fire at the ground where it breaches
            for (int i = 0; i < 30; i++) {
                double ang = i * Math.PI * 2 / 30;
                double rr = 3.2 * scale + Math.sin(i * 3.7) * 0.4 * scale;
                double hgt = (1.2 + Math.abs(Math.sin(s * 5 + i * 1.7)) * 2.2) * scale;
                Vec3 base = new Vec3(Math.cos(ang) * rr, 0.1, Math.sin(ang) * rr);
                Draw.ribbon(c.vc, m, base, base.add(0, hgt, 0), 0.35 * scale, 0.0, Draw.alpha(flame, a), Draw.alpha(0xFFFFE0A0, 0), cam);
            }
            Draw.ringXZ(c.vc, m, new Vec3(0, 0.08, 0), 0, 3.6 * scale, Draw.alpha(0xFF000000, a * 0.6f), Draw.alpha(flame, a * 0.4f), 48);
            c.pop();
        }
    }

    // ═══════════════════════ OUTER PATH: SAMSARA ═══════════════════════

    /** Hundreds of green-white souls spiralling up out of the King's mouth and streaming out across the land. */
    public static final class SoulRiver extends Vfx.Effect {
        final Vec3 at; final double R;

        public SoulRiver(Vec3 at, double R, long life) {
            super(life); this.at = at; this.R = R;
        }

        @Override
        public void glow(Vfx.Ctx c) {
            double s = age(c.t) / 1000.0;
            float a = Math.min(1f, age(c.t) / 1500f) * Math.min(1f, (life - age(c.t)) / 1500f);
            PostPipeline.keep(0.9f * a);
            PostPipeline.light(at.add(0, 10, 0), 1.0f * a, 0xFF8CFFB4);
            c.at(at);
            Matrix4f m = c.m();
            Vec3 cam = c.camRel(at);
            for (int i = 0; i < 160; i++) {
                double ph = (s * (0.12 + Draw.hash(i * 3L) * 0.08) + Draw.hash(i)) % 1.0;
                double ang = Draw.hash(i * 7L) * Math.PI * 2 + ph * 6;
                double up = 26 * Math.sin(Math.min(1, ph * 1.5) * Math.PI / 2);
                double out = ph < 0.5 ? 2 + ph * 6 : 5 + (ph - 0.5) * 2 * R * (0.4 + Draw.hash(i * 11L) * 0.6);
                double fall = ph > 0.6 ? (ph - 0.6) / 0.4 * 24 : 0;
                Vec3 q = new Vec3(Math.cos(ang) * out, 6 + up - fall, Math.sin(ang) * out);
                Vec3 q2 = new Vec3(Math.cos(ang - 0.05) * out * 0.97, 6 + up - fall - 0.6, Math.sin(ang - 0.05) * out * 0.97);
                float fa = a * (float) Math.sin(Math.PI * ph);
                Draw.billboard(c.vc, m, q, 0.35, Draw.alpha(0xFFE8FFF0, fa), c.cam.getLeftVector(), c.cam.getUpVector());
                Draw.billboard(c.vc, m, q, 0.9, Draw.alpha(0xFF8CFFB4, fa * 0.35f), c.cam.getLeftVector(), c.cam.getUpVector());
                Draw.ribbon(c.vc, m, q2, q, 0.0, 0.2, 0, Draw.alpha(0xFF8CFFB4, fa * 0.6f), cam);
            }
            Draw.beam(c.vc, m, new Vec3(0, 4, 0), 40, 2.4, Draw.alpha(0xFF8CFFB4, a * 0.35f), Draw.alpha(0xFFFFFFFF, 0), 24);
            Draw.ringXZ(c.vc, m, new Vec3(0, 0.1, 0), R * 0.12, R * 0.125, Draw.alpha(0xFF8CFFB4, a), Draw.alpha(0xFF8CFFB4, a), 96);
            c.pop();
        }
    }

    // ═══════════════════════ ASCENSION ═══════════════════════

    /** A halo of six black receivers fanned behind the back, with a pale gold aura, following the caster. */
    public static final class Halo extends Vfx.Effect implements Modeled {
        final int id;

        public Halo(int id, long life) {
            super(life); this.id = id;
        }

        Entity e() { return Vfx.entity(id); }

        public boolean visible(long t) {
            Entity e = e();
            return e != null && !(e == net.minecraft.client.Minecraft.getInstance().player && !CameraDirector.active()
                    && net.minecraft.client.Minecraft.getInstance().options.getCameraType().isFirstPerson());
        }

        public Vec3 origin(long t) {
            Entity e = e();
            return e == null ? Vec3.ZERO : e.getPosition(1f);
        }

        public void model(long t) {
            Entity e = e();
            if (e == null) return;
            float yaw = e instanceof LivingEntity le ? le.yBodyRot : e.getYRot();
            Frame f = Frame.yaw(yaw);
            double s = age(t) / 1000.0;
            Vec3 c = new Vec3(0, e.getBbHeight() * 0.8, 0).add(f.fwd.scale(-0.55));
            for (int i = 0; i < 6; i++) {
                double ang = Math.PI * (0.15 + 0.7 * i / 5.0);
                Vec3 dir = f.side.scale(Math.cos(ang)).add(f.up.scale(Math.sin(ang)));
                Vec3 base = c.add(dir.scale(0.35)).add(0, Math.sin(s * 2 + i) * 0.04, 0);
                Mesh.spike(base, base.add(dir.scale(1.1 + (i % 2) * 0.25)), 0.06, 0xFF141418);
            }
        }

        @Override
        public void glow(Vfx.Ctx c) {
            Entity e = e();
            if (e == null) return;
            float a = Math.min(1f, age(c.t) / 500f) * Math.min(1f, (life - age(c.t)) / 800f);
            Vec3 p = e.getPosition(c.partial);
            double s = age(c.t) / 1000.0;
            float yaw = e instanceof LivingEntity le ? Mth.rotLerp(c.partial, le.yBodyRotO, le.yBodyRot) : e.getYRot();
            Frame f = Frame.yaw(yaw);
            c.at(p);
            Matrix4f m = c.m();
            Vec3 hc = new Vec3(0, e.getBbHeight() * 0.8, 0).add(f.fwd.scale(-0.6));
            Draw.ring(c.vc, m, hc, f.side, f.up, 1.35, 1.45, Draw.alpha(0xFFFFF0C0, a * 0.9f), Draw.alpha(0xFFFFF0C0, a * 0.9f), 64);
            Draw.ring(c.vc, m, hc, f.side, f.up, 0.2, 1.35, Draw.alpha(0xFFFFF0C0, 0), Draw.alpha(0xFFFFE8A0, a * 0.25f), 64);
            Draw.glowOrb(c.vc, m, 0, e.getBbHeight() * 0.5, 0, 1.4, 0xFFFFF0C0, a * (0.25f + 0.08f * (float) Math.sin(s * 3)));
            Vec3 cam = c.camRel(p);
            for (int i = 0; i < 10; i++) {
                double ph = (s * 0.7 + Draw.hash(i)) % 1.0;
                double ang = Draw.hash(i * 3L) * Math.PI * 2;
                Vec3 q = new Vec3(Math.cos(ang) * 0.6, ph * 2.4, Math.sin(ang) * 0.6);
                Draw.ribbon(c.vc, m, q, q.add(0, 0.35, 0), 0.03, 0.0, Draw.alpha(0xFFFFF0C0, a * (float) (1 - ph)), 0, cam);
            }
            c.pop();
        }
    }

    // ═══════════════════════ SHARED SIGHT ═══════════════════════

    public static final class Scan extends Vfx.Effect {
        final Vec3 at; final double R;

        public Scan(Vec3 at, double R, long life) {
            super(life); this.at = at; this.R = R;
        }

        @Override
        public void glow(Vfx.Ctx c) {
            float p = p(c.t);
            double r = R * Ease.outCubic(p);
            float a = 1 - p;
            c.at(at);
            Matrix4f m = c.m();
            for (int k = 0; k < 3; k++) {
                double rr = r * (1 - k * 0.08);
                Draw.ringXZ(c.vc, m, new Vec3(0, -1.4 + k * 0.6, 0), Math.max(0, rr - 0.6), rr, Draw.alpha(0xFFB48CFF, 0), Draw.alpha(0xFFB48CFF, a * (0.8f - k * 0.2f)), 96);
            }
            Draw.ring(c.vc, m, Vec3.ZERO, Draw.X, Draw.Y, Math.max(0, r * 0.4 - 0.3), r * 0.4, 0, Draw.alpha(0xFFFFFFFF, a * 0.4f), 72);
            Draw.ring(c.vc, m, Vec3.ZERO, Draw.Z, Draw.Y, Math.max(0, r * 0.4 - 0.3), r * 0.4, 0, Draw.alpha(0xFFFFFFFF, a * 0.4f), 72);
            c.pop();
        }
    }

    private PainFx() {}
}
