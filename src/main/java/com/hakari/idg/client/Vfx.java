package com.hakari.idg.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * World-space visual effects. Everything here is procedural geometry streamed into one
 * solid batch and one additive batch per frame. Time is wall-clock milliseconds, so
 * animation is as smooth as the monitor allows (not locked to 20 tps).
 */
public final class Vfx {
    public static final List<Effect> EFFECTS = new ArrayList<>();

    public static long now() {
        return Util.getMillis();
    }

    public static void add(Effect e) {
        EFFECTS.add(e);
    }

    public static void prune() {
        long t = now();
        EFFECTS.removeIf(e -> e.dead(t));
    }

    public static void clear() {
        EFFECTS.clear();
    }

    /** Per-frame render context. Positions handed to effects are absolute world coords. */
    public static final class Ctx {
        public VertexConsumer vc;
        public PoseStack ps;
        public Camera cam;
        public Vec3 camPos;
        public long t;
        public float partial;

        public Matrix4f m() {
            return ps.last().pose();
        }

        /** Push a pose centred on an absolute world position (keeps float precision far from origin). */
        public void at(Vec3 world) {
            ps.pushPose();
            ps.translate(world.x - camPos.x, world.y - camPos.y, world.z - camPos.z);
        }

        public void pop() {
            ps.popPose();
        }

        /** Camera position relative to the current pose origin (for ribbons). */
        public Vec3 camRel(Vec3 origin) {
            return camPos.subtract(origin);
        }
    }

    public static abstract class Effect {
        public final long start = now();
        public final long life;

        protected Effect(long life) {
            this.life = life;
        }

        public float p(long t) {
            return Mth.clamp((t - start) / (float) life, 0f, 1f);
        }

        public long age(long t) {
            return t - start;
        }

        public boolean dead(long t) {
            return t - start > life;
        }

        public void solid(Ctx c) {}

        public void glow(Ctx c) {}
    }

    public static void renderSolid(Ctx c) {
        for (Effect e : EFFECTS) if (!e.dead(c.t) && c.t >= e.start) e.solid(c);
    }

    public static void renderGlow(Ctx c) {
        for (Effect e : EFFECTS) if (!e.dead(c.t) && c.t >= e.start) e.glow(c);
    }

    static Vec3 v3(org.joml.Vector3f f) {
        return new Vec3(f.x(), f.y(), f.z());
    }

    static Entity entity(int id) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null || id < 0 ? null : mc.level.getEntity(id);
    }

    /** Colour of the ground under a point, so debris looks like the block it was ripped from. */
    static int groundColor(Vec3 p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return 0xFF6B5A4A;
        BlockPos bp = BlockPos.containing(p.x, p.y - 0.5, p.z);
        for (int i = 0; i < 4; i++) {
            BlockState s = mc.level.getBlockState(bp);
            if (!s.isAir()) {
                int col = s.getMapColor(mc.level, bp).col;
                return col == 0 ? 0xFF6B5A4A : 0xFF000000 | col;
            }
            bp = bp.below();
        }
        return 0xFF6B5A4A;
    }

    // ═══════════════════════════════ EFFECTS ═══════════════════════════════

    /** Expanding glowing burst with an anime cross-flare. */
    public static final class Flash extends Effect {
        final Vec3 pos; final double r; final int col;

        public Flash(Vec3 pos, double r, int col, long life) {
            super(life); this.pos = pos; this.r = r; this.col = col;
        }

        @Override
        public void glow(Ctx c) {
            float p = p(c.t);
            float a = (1 - p) * (1 - p);
            double s = r * (0.35 + 0.65 * Ease.outExpo(p));
            c.at(pos);
            Matrix4f m = c.m();
            Draw.sphere(c.vc, m, 0, 0, 0, s * 0.4, 8, 12, Draw.alpha(0xFFFFFFFF, a));
            Draw.sphere(c.vc, m, 0, 0, 0, s, 10, 14, Draw.alpha(col, a * 0.5f));
            Draw.sphere(c.vc, m, 0, 0, 0, s * 1.8, 10, 14, Draw.alpha(col, a * 0.15f));
            Vec3 cam = c.camRel(pos);
            Vec3 l = v3(c.cam.getLeftVector()), u = v3(c.cam.getUpVector());
            double fl = s * 4.5 * (1 - p * 0.5);
            Draw.ribbon(c.vc, m, l.scale(-fl), l.scale(fl), 0.0, 0.0, 0, 0, cam);
            Draw.ribbon(c.vc, m, Vec3.ZERO, l.scale(fl), s * 0.12, 0.0, Draw.alpha(0xFFFFFFFF, a), Draw.alpha(col, 0), cam);
            Draw.ribbon(c.vc, m, Vec3.ZERO, l.scale(-fl), s * 0.12, 0.0, Draw.alpha(0xFFFFFFFF, a), Draw.alpha(col, 0), cam);
            Draw.ribbon(c.vc, m, Vec3.ZERO, u.scale(fl * 0.6), s * 0.08, 0.0, Draw.alpha(0xFFFFFFFF, a), Draw.alpha(col, 0), cam);
            Draw.ribbon(c.vc, m, Vec3.ZERO, u.scale(-fl * 0.6), s * 0.08, 0.0, Draw.alpha(0xFFFFFFFF, a), Draw.alpha(col, 0), cam);
            c.pop();
        }
    }

    /** Expanding shock ring in an arbitrary plane. */
    public static final class Shockwave extends Effect {
        final Vec3 pos, u, v; final double maxR, width; final int col;

        public Shockwave(Vec3 pos, Vec3 u, Vec3 v, double maxR, double width, int col, long life) {
            super(life); this.pos = pos; this.u = u; this.v = v; this.maxR = maxR; this.width = width; this.col = col;
        }

        public static Shockwave ground(Vec3 pos, double r, int col, long life) {
            return new Shockwave(pos.add(0, 0.08, 0), Draw.X, Draw.Z, r, Math.max(0.4, r * 0.18), col, life);
        }

        public static Shockwave facing(Vec3 pos, Vec3 dir, double r, int col, long life) {
            Vec3 d = dir.normalize();
            Vec3 a = Draw.perp(d);
            return new Shockwave(pos, a, d.cross(a), r, Math.max(0.25, r * 0.22), col, life);
        }

        @Override
        public void glow(Ctx c) {
            float p = p(c.t);
            double r = maxR * Ease.outExpo(p);
            double w = width * (1 - p) + 0.04;
            float a = 1 - p;
            c.at(pos);
            Matrix4f m = c.m();
            Draw.ring(c.vc, m, Vec3.ZERO, u, v, Math.max(0, r - w), r, Draw.alpha(col, 0), Draw.alpha(col, a * 0.9f), 48);
            Draw.ring(c.vc, m, Vec3.ZERO, u, v, r, r + w * 0.25, Draw.alpha(0xFFFFFFFF, a), Draw.alpha(0xFFFFFFFF, 0), 48);
            c.pop();
        }
    }

    /** Impact lines shooting outward in 3D — the manga "speed burst" made solid. */
    public static final class Spikes extends Effect {
        final Vec3 pos; final int n; final double len; final int col; final long seed; final Vec3 bias; final double biasAmt;

        public Spikes(Vec3 pos, int n, double len, int col, long life, Vec3 bias, double biasAmt) {
            super(life); this.pos = pos; this.n = n; this.len = len; this.col = col;
            this.seed = (long) (pos.x * 7349 + pos.z * 1931 + start); this.bias = bias; this.biasAmt = biasAmt;
        }

        public Spikes(Vec3 pos, int n, double len, int col, long life) {
            this(pos, n, len, col, life, Vec3.ZERO, 0);
        }

        @Override
        public void glow(Ctx c) {
            float p = p(c.t);
            c.at(pos);
            Matrix4f m = c.m();
            Vec3 cam = c.camRel(pos);
            for (int i = 0; i < n; i++) {
                Vec3 d = Draw.randDir(seed + i * 31L).add(bias.scale(biasAmt)).normalize();
                double l = len * (0.5 + Draw.hash(seed + i * 17L));
                double outer = l * Ease.outExpo(Math.min(1f, p * 1.7f));
                double inner = l * Ease.outCubic(p) * 0.85;
                double w = 0.07 * (1 - p) * (0.6 + Draw.hash(seed + i));
                Draw.ribbon(c.vc, m, d.scale(inner), d.scale(outer), w, 0.0,
                        Draw.alpha(0xFFFFFFFF, 1 - p), Draw.alpha(col, (1 - p) * 0.6f), cam);
            }
            c.pop();
        }
    }

    /** Chunks of ground ripped up and tumbling with gravity. */
    public static final class Debris extends Effect {
        final Vec3 pos; final int n; final int col; final long seed; final double power;

        public Debris(Vec3 pos, int n, double power, long life) {
            super(life); this.pos = pos; this.n = n; this.power = power;
            this.col = groundColor(pos); this.seed = (long) (pos.x * 911 + pos.z * 577 + start);
        }

        @Override
        public void solid(Ctx c) {
            float p = p(c.t);
            double t = age(c.t) / 1000.0;
            float fade = p > 0.7f ? 1 - (p - 0.7f) / 0.3f : 1f;
            for (int i = 0; i < n; i++) {
                long s = seed + i * 97L;
                double ang = Draw.hash(s) * Math.PI * 2;
                double out = (2 + Draw.hash(s + 1) * 5) * power;
                double up = (4 + Draw.hash(s + 2) * 6) * power;
                double x = Math.cos(ang) * out * t, z = Math.sin(ang) * out * t;
                double y = up * t - 9.8 * t * t;
                if (y < 0) y = 0;
                double size = 0.07 + Draw.hash(s + 3) * 0.2;
                int col = Draw.scaleRgb(this.col, 0.6f + (float) Draw.hash(s + 4) * 0.6f);
                c.at(pos.add(x, y + size, z));
                c.ps.mulPose(Axis.XP.rotation((float) (t * 9 * (Draw.hash(s + 5) - 0.5) * 2)));
                c.ps.mulPose(Axis.ZP.rotation((float) (t * 7 * (Draw.hash(s + 6) - 0.5) * 2)));
                Draw.cube(c.vc, c.m(), size, Draw.alpha(col, fade));
                c.pop();
            }
        }
    }

    /** Hakari's sliding shutter doors: two slatted steel panels slam shut on a point. */
    public static final class Doors extends Effect {
        final Vec3 pos; final float yaw; final long closeMs; final boolean ghost;

        public Doors(Vec3 pos, float yaw, boolean fast, boolean ghost, long life) {
            super(life); this.pos = pos; this.yaw = yaw; this.closeMs = fast ? 200 : 450; this.ghost = ghost;
        }

        double offset(long t) {
            long a = age(t);
            if (ghost) return 1.25 + 0.15 * Math.sin(a / 60.0);
            if (a < closeMs) return 1.0 + 3.4 * (1 - Ease.inExpo(a / (float) closeMs));
            double k = (a - closeMs) / 200.0;
            return 1.0 + 0.12 * Math.exp(-k * 3) * Math.cos(k * 14);
        }

        float fade(long t) {
            float p = p(t);
            if (ghost) return ((t / 50) % 3 == 0) ? 0.35f : 0.75f;
            return p > 0.72f ? 1 - (p - 0.72f) / 0.28f : 1f;
        }

        void panel(Ctx c, double sign, long t, boolean glow) {
            double off = offset(t);
            float f = fade(t);
            c.ps.pushPose();
            c.ps.translate(sign * off, 0, 0);
            Matrix4f m = c.m();
            double h = 3.4, d = 0.07;
            double x0 = -1.0, x1 = 1.0;
            if (!glow) {
                int steel = Draw.alpha(ghost ? 0xFF6E5A80 : 0xFF8E929E, f * (ghost ? 0.55f : 1f));
                Draw.box(c.vc, m, x0, 0, -d, x1, h, d, steel);
                for (int i = 0; i < 12; i++) {
                    double y = 0.2 + i * 0.26;
                    int slat = Draw.alpha(i % 2 == 0 ? 0xFFB4B8C4 : 0xFF6A6E7A, f);
                    Draw.box(c.vc, m, x0 + 0.06, y, -d - 0.035, x1 - 0.06, y + 0.12, d + 0.035, slat);
                }
                int frame = Draw.alpha(0xFF2A2630, f);
                Draw.box(c.vc, m, x0, 0, -d - 0.05, x0 + 0.1, h, d + 0.05, frame);
                Draw.box(c.vc, m, x1 - 0.1, 0, -d - 0.05, x1, h, d + 0.05, frame);
                Draw.box(c.vc, m, x0, h - 0.12, -d - 0.06, x1, h + 0.05, d + 0.06, frame);
                Draw.box(c.vc, m, x0, 0, -d - 0.06, x1, 0.1, d + 0.06, frame);
                // handle
                double hx = sign > 0 ? x0 + 0.25 : x1 - 0.25;
                Draw.box(c.vc, m, hx - 0.04, 1.4, -d - 0.12, hx + 0.04, 2.0, d + 0.12, Draw.alpha(0xFFE8D27A, f));
            } else {
                int edge = Draw.alpha(0xFFFF4FB8, f * 0.8f);
                Vec3 cam = c.camRel(pos);
                double ex = sign > 0 ? x0 : x1;
                Draw.ribbon(c.vc, m, new Vec3(ex, 0, 0), new Vec3(ex, h, 0), 0.06, 0.06, edge, edge, cam);
                Draw.ribbon(c.vc, m, new Vec3(x0, h, 0), new Vec3(x1, h, 0), 0.05, 0.05, edge, edge, cam);
            }
            c.ps.popPose();
        }

        @Override
        public void solid(Ctx c) {
            c.at(pos);
            c.ps.mulPose(Axis.YP.rotationDegrees(-yaw));
            panel(c, -1, c.t, false);
            panel(c, 1, c.t, false);
            c.pop();
        }

        @Override
        public void glow(Ctx c) {
            c.at(pos);
            c.ps.mulPose(Axis.YP.rotationDegrees(-yaw));
            panel(c, -1, c.t, true);
            panel(c, 1, c.t, true);
            long a = age(c.t);
            if (!ghost && a > closeMs && a < closeMs + 180) {
                float k = 1 - (a - closeMs) / 180f;
                Vec3 cam = c.camRel(pos);
                Draw.ribbon(c.vc, c.m(), new Vec3(0, -0.4, 0), new Vec3(0, 3.9, 0), 0.25 * k, 0.25 * k,
                        Draw.alpha(0xFFFFFFFF, k), Draw.alpha(0xFFFFFFFF, k), cam);
                Draw.ribbon(c.vc, c.m(), new Vec3(0, -0.4, 0), new Vec3(0, 3.9, 0), 0.9 * k, 0.9 * k,
                        Draw.alpha(0xFFFF4FB8, k * 0.4f), Draw.alpha(0xFFFF4FB8, k * 0.4f), cam);
            }
            c.pop();
        }
    }

    /** Rough-energy charge: spiralling streaks and jagged sandpaper sparks converging on the fist. */
    public static final class Charge extends Effect {
        final int entityId;

        public Charge(int entityId, long life) {
            super(life); this.entityId = entityId;
        }

        @Override
        public void glow(Ctx c) {
            Entity e = entity(entityId);
            if (e == null) return;
            float p = p(c.t);
            Vec3 base = e.getPosition(c.partial);
            Vec3 fwd = Draw.forward(e.getYRot(), 0);
            Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
            Vec3 fist = base.add(0, 1.25, 0).add(right.scale(-0.45)).add(fwd.scale(-0.25));
            c.at(fist);
            Matrix4f m = c.m();
            Vec3 cam = c.camRel(fist);
            double time = age(c.t) / 1000.0;
            for (int i = 0; i < 22; i++) {
                double ph = (time * 1.9 + i * 0.137) % 1.0;
                double rad = 2.6 * (1 - ph);
                double th = i * 2.399 + time * 7;
                double y = (Draw.hash(i) - 0.5) * 2.4 * (1 - ph);
                Vec3 a = new Vec3(Math.cos(th) * rad, y, Math.sin(th) * rad);
                Vec3 b = new Vec3(Math.cos(th - 0.5) * rad * 0.82, y * 0.82, Math.sin(th - 0.5) * rad * 0.82);
                int col = i % 3 == 0 ? 0xFFFFFFFF : 0xFFFF3D9A;
                Draw.ribbon(c.vc, m, a, b, 0.0, 0.05, Draw.alpha(col, 0), Draw.alpha(col, (float) ph * 0.9f), cam);
            }
            long frame = c.t / 40;
            for (int k = 0; k < 6; k++) {
                Vec3 prev = Vec3.ZERO;
                Vec3 dir = Draw.randDir(frame * 13 + k * 101);
                for (int s = 1; s <= 5; s++) {
                    Vec3 next = dir.scale(s * 0.22 * (0.4 + p)).add(Draw.randDir(frame * 7 + k * 31 + s).scale(0.12));
                    Draw.ribbon(c.vc, m, prev, next, 0.025, 0.02, 0xFFFFE0F0, 0xCCFF2D7A, cam);
                    prev = next;
                }
            }
            Draw.glowOrb(c.vc, m, 0, 0, 0, 0.18 + 0.35 * p, 0xFFFF2D7A, 0.8f);
            c.pop();
        }
    }

    /** Barrage after-images: dozens of fists punching forward at once. */
    public static final class Fists extends Effect {
        final int entityId; final int col;

        public Fists(int entityId, int col, long life) {
            super(life); this.entityId = entityId; this.col = col;
        }

        @Override
        public void glow(Ctx c) {
            Entity e = entity(entityId);
            if (e == null) return;
            Vec3 base = e.getPosition(c.partial).add(0, 1.3, 0);
            Vec3 fwd = Draw.forward(e.getYRot(), 0);
            Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
            c.at(base);
            Matrix4f m = c.m();
            Vec3 cam = c.camRel(base);
            double time = age(c.t) / 1000.0;
            for (int j = 0; j < 14; j++) {
                double ph = (time * 13 + j * 0.37) % 1.0;
                long cycle = (long) (time * 13 + j * 0.37);
                double lx = (Draw.hash(cycle * 31 + j) - 0.5) * 1.5;
                double ly = (Draw.hash(cycle * 17 + j) - 0.5) * 0.9;
                Vec3 origin = right.scale(lx).add(0, ly, 0).add(fwd.scale(0.3));
                Vec3 tip = origin.add(fwd.scale(0.4 + ph * 2.0));
                float a = (float) (1 - ph);
                Draw.ribbon(c.vc, m, origin, tip, 0.02, 0.17, Draw.alpha(col, 0), Draw.alpha(col, a * 0.7f), cam);
                Draw.glowOrb(c.vc, m, tip.x, tip.y, tip.z, 0.14, col, a * 0.8f);
            }
            c.pop();
        }
    }

    /** Dash after-image ribbon following an entity. */
    public static final class Trail extends Effect {
        final int entityId; final int col;
        final Deque<Vec3> points = new ArrayDeque<>();

        public Trail(int entityId, int col, long life) {
            super(life); this.entityId = entityId; this.col = col;
        }

        @Override
        public void glow(Ctx c) {
            Entity e = entity(entityId);
            if (e != null && p(c.t) < 0.85f) {
                Vec3 now = e.getPosition(c.partial);
                if (points.isEmpty() || points.peekLast().distanceToSqr(now) > 0.04) points.addLast(now);
            } else if (!points.isEmpty()) {
                points.pollFirst();
            }
            while (points.size() > 28) points.pollFirst();
            if (points.size() < 2) return;
            Vec3 origin = points.peekLast();
            c.at(origin);
            Matrix4f m = c.m();
            Vec3[] arr = points.toArray(new Vec3[0]);
            for (int i = 1; i < arr.length; i++) {
                float a0 = (i - 1) / (float) arr.length, a1 = i / (float) arr.length;
                Vec3 p0 = arr[i - 1].subtract(origin), p1 = arr[i].subtract(origin);
                Draw.quad(c.vc, m, p0.add(0, 0.1, 0), p1.add(0, 0.1, 0), p1.add(0, 1.9, 0), p0.add(0, 1.9, 0),
                        Draw.alpha(col, a0 * 0.18f), Draw.alpha(col, a1 * 0.18f), Draw.alpha(col, a1 * 0.4f), Draw.alpha(col, a0 * 0.4f));
            }
            Vec3 cam = c.camRel(origin);
            for (int k = 0; k < 6; k++) {
                double y = 0.2 + Draw.hash(k * 13 + start) * 1.6;
                Vec3 from = arr[0].subtract(origin).add(0, y, 0);
                Draw.ribbon(c.vc, m, from, new Vec3(0, y, 0), 0.0, 0.03, 0, Draw.alpha(0xFFFFFFFF, 0.7f), cam);
            }
            c.pop();
        }
    }

    /** Gold coins bursting out and flipping (jackpot payout). */
    public static final class Coins extends Effect {
        final Vec3 pos; final int n; final long seed;

        public Coins(Vec3 pos, int n, long life) {
            super(life); this.pos = pos; this.n = n; this.seed = start;
        }

        @Override
        public void solid(Ctx c) {
            double t = age(c.t) / 1000.0;
            float fade = 1 - Math.max(0, (p(c.t) - 0.75f) / 0.25f);
            for (int i = 0; i < n; i++) {
                long s = seed + i * 53L;
                double ang = Draw.hash(s) * Math.PI * 2, out = 2 + Draw.hash(s + 1) * 6, up = 6 + Draw.hash(s + 2) * 9;
                double y = up * t - 9.8 * t * t;
                if (y < -0.9) y = -0.9;
                c.at(pos.add(Math.cos(ang) * out * t, 1 + y, Math.sin(ang) * out * t));
                c.ps.mulPose(Axis.YP.rotation((float) (t * 12 + i)));
                c.ps.mulPose(Axis.XP.rotation((float) (t * 17 + i * 0.7)));
                Draw.ring(c.vc, c.m(), Vec3.ZERO, Draw.X, Draw.Y, 0, 0.17, Draw.alpha(0xFFFFE68A, fade), Draw.alpha(0xFFD9A400, fade), 10);
                c.pop();
            }
        }

        @Override
        public void glow(Ctx c) {
            double t = age(c.t) / 1000.0;
            float fade = 1 - p(c.t);
            for (int i = 0; i < n; i += 3) {
                long s = seed + i * 53L;
                double ang = Draw.hash(s) * Math.PI * 2, out = 2 + Draw.hash(s + 1) * 6, up = 6 + Draw.hash(s + 2) * 9;
                double y = Math.max(-0.9, up * t - 9.8 * t * t);
                Vec3 p = pos.add(Math.cos(ang) * out * t, 1 + y, Math.sin(ang) * out * t);
                c.at(p);
                if ((c.t / 60 + i) % 4 == 0) {
                    Draw.billboard(c.vc, c.m(), Vec3.ZERO, 0.35, Draw.alpha(0xFFFFF4C0, fade), c.cam.getLeftVector(), c.cam.getUpVector());
                }
                c.pop();
            }
        }
    }

    /** Vertical pillar of light. */
    public static final class Pillar extends Effect {
        final Vec3 pos; final double h, r; final int col;

        public Pillar(Vec3 pos, double h, double r, int col, long life) {
            super(life); this.pos = pos; this.h = h; this.r = r; this.col = col;
        }

        @Override
        public void glow(Ctx c) {
            float p = p(c.t);
            double rr = r * (p < 0.15f ? Ease.outExpo(p / 0.15f) : 1 - 0.85 * Ease.inOut((p - 0.15f) / 0.85f));
            float a = 1 - p * 0.6f;
            c.at(pos);
            Draw.beam(c.vc, c.m(), Vec3.ZERO, h, rr, Draw.alpha(0xFFFFFFFF, a), Draw.alpha(col, 0), 20);
            Draw.beam(c.vc, c.m(), Vec3.ZERO, h * 0.8, rr * 2.2, Draw.alpha(col, a * 0.35f), Draw.alpha(col, 0), 20);
            c.pop();
        }
    }

    /** Rhythm: beats pulsing out from the feet. */
    public static final class RhythmPulse extends Effect {
        final int entityId;

        public RhythmPulse(int entityId, long life) {
            super(life); this.entityId = entityId;
        }

        @Override
        public void glow(Ctx c) {
            Entity e = entity(entityId);
            if (e == null) return;
            Vec3 base = e.getPosition(c.partial).add(0, 0.06, 0);
            float beat = (age(c.t) % 500) / 500f;
            c.at(base);
            double r = 0.5 + 3.5 * Ease.outExpo(beat);
            Draw.ringXZ(c.vc, c.m(), Vec3.ZERO, r - 0.25, r, Draw.alpha(0xFF5CF2FF, 0), Draw.alpha(0xFF5CF2FF, (1 - beat) * 0.8f), 40);
            Draw.ringXZ(c.vc, c.m(), Vec3.ZERO, 0.7, 0.85, Draw.alpha(0xFFFFD34D, 0.6f), Draw.alpha(0xFFFFD34D, 0.6f), 40);
            c.pop();
        }
    }

    /** The domain barrier breaking into black glass. */
    public static final class Shatter extends Effect {
        final Vec3 c0; final double R; final int edge; final long seed;

        public Shatter(Vec3 center, double R, int edge, long life) {
            super(life); this.c0 = center; this.R = R; this.edge = edge; this.seed = start;
        }

        @Override
        public void solid(Ctx c) {
            double t = age(c.t) / 1000.0;
            float fade = 1 - p(c.t);
            for (int i = 0; i < 170; i++) {
                long s = seed + i * 41L;
                Vec3 d = Draw.randDir(s);
                double sp = 4 + Draw.hash(s + 3) * 9;
                Vec3 p = c0.add(d.scale(R + sp * t)).add(0, -4.9 * t * t, 0);
                double size = 1.2 + Draw.hash(s + 4) * 2.4;
                c.at(p);
                float yaw = (float) Math.atan2(d.x, d.z);
                c.ps.mulPose(Axis.YP.rotation(yaw));
                c.ps.mulPose(Axis.XP.rotation((float) (Math.asin(-d.y) + t * 3 * (Draw.hash(s + 5) - 0.5))));
                c.ps.mulPose(Axis.ZP.rotation((float) (t * 4 * (Draw.hash(s + 6) - 0.5))));
                Draw.box(c.vc, c.m(), -size / 2, -size / 2, -0.04, size / 2, size / 2, 0.04, Draw.alpha(0xFF0E0010, fade));
                c.pop();
            }
        }

        @Override
        public void glow(Ctx c) {
            double t = age(c.t) / 1000.0;
            float fade = 1 - p(c.t);
            for (int i = 0; i < 170; i += 2) {
                long s = seed + i * 41L;
                Vec3 d = Draw.randDir(s);
                double sp = 4 + Draw.hash(s + 3) * 9;
                Vec3 p = c0.add(d.scale(R + sp * t)).add(0, -4.9 * t * t, 0);
                c.at(p);
                Draw.billboard(c.vc, c.m(), Vec3.ZERO, 0.6, Draw.alpha(edge, fade * 0.7f), c.cam.getLeftVector(), c.cam.getUpVector());
                c.pop();
            }
        }
    }

    /** Jagged cursed-energy bolt between two points, re-rolled every 50 ms. */
    public static final class Bolt extends Effect {
        final Vec3 a, b; final int col;

        public Bolt(Vec3 a, Vec3 b, int col, long life) {
            super(life); this.a = a; this.b = b; this.col = col;
        }

        @Override
        public void glow(Ctx c) {
            float fade = 1 - p(c.t);
            long frame = c.t / 50;
            c.at(a);
            Vec3 cam = c.camRel(a);
            Vec3 d = b.subtract(a);
            Vec3 prev = Vec3.ZERO;
            for (int i = 1; i <= 8; i++) {
                Vec3 next = d.scale(i / 8.0);
                if (i < 8) next = next.add(Draw.randDir(frame * 37 + i).scale(d.length() * 0.08));
                Draw.ribbon(c.vc, c.m(), prev, next, 0.05, 0.05, Draw.alpha(0xFFFFFFFF, fade), Draw.alpha(col, fade), cam);
                prev = next;
            }
            c.pop();
        }
    }

    private Vfx() {}
}
