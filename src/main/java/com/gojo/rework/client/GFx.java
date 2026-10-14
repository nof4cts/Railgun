package com.gojo.rework.client;

import com.gojo.rework.client.fx.Ink;
import com.gojo.rework.client.fx.GShade;
import com.gojo.rework.client.fx.Look;
import com.gojo.rework.common.GBeats;
import com.gojo.rework.common.GMove;
import com.gojo.rework.server.GRites;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Everything Gojo's techniques paint: the orbs (Blue that drinks the light in, Red that bursts with rays, Purple that
 * crackles), what they do to the world (craters, trenches, the furrow, the erased tunnel, the nuke), the domain's void,
 * and what they do to the picture (flashes, impact frames, shake). Driven by the same timings as the server.
 */
public final class GFx {
    public static final int BLUE = 0xFF3D8BFF, BLUE_CORE = 0xFFDDF1FF, DEEP_BLUE = 0xFF1636C8;
    public static final int RED = 0xFFFF2B2B, RED_CORE = 0xFFFFE1D6, CRIMSON = 0xFFB0101E, ORANGE = 0xFFFF7A2A;
    public static final int PURPLE = 0xFFA24BFF, MAGENTA = 0xFFFF3DD8, VIOLET_CORE = 0xFFF6EAFF, DARK_VIOLET = 0xFF2A0846;
    public static final int WHITE = 0xFFFFFFFF, NIGHT = 0xFF04050C, SPACE = 0xFF0A1024;
    static final Vec3 UP = new Vec3(0, 1, 0);

    private GFx() {}

    // ------------------------------------------------------------------------------------------------ helpers

    static float span(float t, float a, float b) {
        return Mth.clamp((t - a) / (b - a), 0, 1);
    }

    static float smooth(float x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }

    static float window(float t, float a, float b, float c, float d) {
        return Math.min(span(t, a, b), 1 - span(t, c, d));
    }

    static float hit(float t, float at, float len) {
        return t < at ? 0 : (float) Math.exp(-(t - at) / Math.max(0.5F, len) * 2.2F);
    }

    static int a(int c, float k) {
        return Ink.alpha(c, k);
    }

    /** His right (or left) hand, in front of him at chest height. */
    static Vec3 hand(GCasts.Play c, float partial, boolean right) {
        Entity e = c.caster();
        float yaw = e != null ? e.getViewYRot(partial) : (float) (Mth.atan2(-c.dir().x, c.dir().z) * Mth.RAD_TO_DEG);
        Vec3 f = Vec3.directionFromRotation(0, yaw);
        Vec3 r = new Vec3(-f.z, 0, f.x);
        return c.feet(partial).add(0, 1.25, 0).add(f.scale(0.45)).add(r.scale(right ? 0.42 : -0.42));
    }

    static Vec3 ground(Vec3 at) {
        ClientLevel l = Minecraft.getInstance().level;
        if (l == null) {
            return at;
        }
        BlockPos.MutableBlockPos m = BlockPos.containing(at).mutable();
        for (int i = 0; i < 40 && m.getY() > l.getMinBuildHeight(); i++) {
            if (!l.getBlockState(m.below()).isAir()) {
                return new Vec3(at.x, m.getY(), at.z);
            }
            m.move(0, -1, 0);
        }
        return at;
    }

    // ------------------------------------------------------------------------------------------------ the orbs

    /** Blue: a deep blue sphere drinking the light in, with spirals falling into a white heart. */
    static void blueOrb(Ink ink, Vec3 c, double r, float t, float k, long seed) {
        if (r <= 0.02 || k <= 0.01F) {
            return;
        }
        // the body: the original orb shader's inward spiral
        com.gojo.rework.client.fx.GShade.orb(c, r, 0, com.gojo.rework.client.fx.GShade.BLUE_A, com.gojo.rework.client.fx.GShade.BLUE_B, 1.3F * k, seed);
        Random rnd = new Random(seed);
        // spiral arms falling inward
        for (int s = 0; s < 6; s++) {
            Vec3 axis = new Vec3(rnd.nextDouble() - 0.5, rnd.nextDouble() - 0.5, rnd.nextDouble() - 0.5).normalize();
            Vec3[] b = Ink.basis(axis);
            int n = 10;
            Vec3[] pts = new Vec3[n];
            double[] w = new double[n];
            int[] cols = new int[n];
            double phase = t * (0.25 + rnd.nextDouble() * 0.2) + s;
            for (int i = 0; i < n; i++) {
                double u = i / (double) (n - 1);
                double rad = r * (2.4 - 1.6 * u);
                double ang = phase + u * 4.0;
                pts[i] = c.add(b[0].scale(Math.cos(ang) * rad)).add(b[1].scale(Math.sin(ang) * rad));
                w[i] = r * 0.07 * (0.3 + u);
                cols[i] = a(i < n / 2 ? BLUE : BLUE_CORE, k * (float) (0.2 + 0.7 * u));
            }
            ink.ribbon(pts, w, cols);
        }
        // motes streaming in from all round
        for (int i = 0; i < 18; i++) {
            Vec3 d = new Vec3(rnd.nextDouble() - 0.5, rnd.nextDouble() - 0.5, rnd.nextDouble() - 0.5).normalize();
            double u = ((t * 0.07 + rnd.nextDouble()) % 1.0);
            Vec3 p = c.add(d.scale(r * (4.5 - 3.5 * u)));
            ink.beam(p, p.add(c.subtract(p).normalize().scale(r * 0.6)), r * 0.04, a(BLUE_CORE, k * (float) u * 0.8F));
        }
        com.gojo.rework.client.fx.GShade.glow(c, r * 1.25, com.gojo.rework.client.fx.GShade.BLUE_B, 0.6F * k, 2);
    }

    /** Red: a blazing point that bursts with long straight rays. */
    static void redOrb(Ink ink, Vec3 c, double r, float t, float k, long seed) {
        if (r <= 0.02 || k <= 0.01F) {
            return;
        }
        // the body: the original orb shader's turbulent white-hot core; long straight rays bursting out of it
        com.gojo.rework.client.fx.GShade.orb(c, r, 1, com.gojo.rework.client.fx.GShade.RED_A, com.gojo.rework.client.fx.GShade.RED_B, 1.5F * k, seed);
        Random rnd = new Random(seed ^ 0x5EED);
        for (int i = 0; i < 8; i++) {
            Vec3 d = new Vec3(rnd.nextDouble() - 0.5, rnd.nextDouble() - 0.5, rnd.nextDouble() - 0.5).normalize();
            double len = r * (4 + 6 * rnd.nextDouble()) * (0.7 + 0.3 * Math.sin(t * 0.5 + i));
            com.gojo.rework.client.fx.GShade.ribbon(new Vec3[]{c.add(d.scale(r * 0.8)), c.add(d.scale(r + len))}, new float[]{(float) (r * 0.1), 0.01F},
                    com.gojo.rework.client.fx.GShade.RED_A, 0.9F * k, 1);
        }
        com.gojo.rework.client.fx.GShade.glow(c, r * 3.5, com.gojo.rework.client.fx.GShade.RED_B, 0.5F * k, 4);
        com.gojo.rework.client.fx.GShade.glow(c, r * 1.7 + Math.sin(t * 0.6) * r * 0.2, com.gojo.rework.client.fx.GShade.RED_A, 0.7F * k, 2);
    }

    /** Purple: a violet mass with a white heart, its shell churning, lightning crawling over it. */
    static void purpleOrb(Ink ink, Vec3 c, double r, float t, float k, long seed) {
        if (r <= 0.02 || k <= 0.01F) {
            return;
        }
        // the body: the original orb shader's inky violet mass, white veins, blazing shell
        com.gojo.rework.client.fx.GShade.orb(c, r, 2, com.gojo.rework.client.fx.GShade.PUR_A, com.gojo.rework.client.fx.GShade.PUR_B, 1.0F * k, seed);
        com.gojo.rework.client.fx.GShade.glow(c, r * 1.15, com.gojo.rework.client.fx.GShade.PUR_B, 0.7F * k, 2);
        com.gojo.rework.client.fx.GShade.glow(c, r * (1.45 + 0.1 * Math.sin(t * 0.7)), com.gojo.rework.client.fx.GShade.MAGENTA, 0.45F * k, 2);
        lightning(ink, c, r, t, k, seed, 7, r * 2.0);
    }

    /** Jagged bolts of light crawling over and off a sphere, re-struck every couple of ticks. */
    static void lightning(Ink ink, Vec3 c, double r, float t, float k, long seed, int bolts, double reach) {
        int strike = (int) (t / 2);
        Random rnd = new Random(seed * 31 + strike);
        for (int b = 0; b < bolts; b++) {
            Vec3 d = new Vec3(rnd.nextDouble() - 0.5, rnd.nextDouble() - 0.5, rnd.nextDouble() - 0.5).normalize();
            Vec3 side = Ink.basis(d)[0];
            int n = 7;
            Vec3[] pts = new Vec3[n];
            float[] w = new float[n];
            double len = reach * (0.6 + rnd.nextDouble() * 0.6);
            for (int i = 0; i < n; i++) {
                double u = i / (double) (n - 1);
                Vec3 p = c.add(d.scale(r * 0.9 + (len - r * 0.9) * u));
                Vec3 j = side.scale((rnd.nextDouble() - 0.5) * r * 0.5 * Math.sin(Math.PI * u))
                        .add(Ink.basis(d)[1].scale((rnd.nextDouble() - 0.5) * r * 0.5 * Math.sin(Math.PI * u)));
                pts[i] = p.add(j);
                w[i] = (float) (r * 0.09 * (1 - u * 0.7));
            }
            // the crackling-arc shader of the original technique
            com.gojo.rework.client.fx.GShade.ribbon(pts, w, rnd.nextBoolean() ? com.gojo.rework.client.fx.GShade.MAGENTA : com.gojo.rework.client.fx.GShade.PUR_B, k, 3);
        }
    }

    /** A burst: core flash, a dome of light, a shock ring racing out, rays and flung sparks. */
    static void burst(Ink ink, Vec3 g, double big, float age, long seed, int hot, int mid, int cool) {
        if (age < 0 || age > 60) {
            return;
        }
        float core = (float) Math.exp(-age / 2.5);
        float k = age / 60F;
        ink.glow(g, big * (1.0 + 1.2 * (1 - core)), a(hot, 0.55F * core));
        ink.sphere(true, g, big * (0.4 + 1.1 * Math.sqrt(Math.min(1, age / 10F))), a(mid, 0.35F * (1 - k) * (1 - k)), a(mid, 0.5F * (1 - k)), 1.3);
        float sk = Mth.clamp(age / 12F, 0, 1);
        if (sk < 1) {
            ink.ring(g.add(0, 0.3, 0), UP, big * (0.5 + 3.5 * Math.sqrt(sk)), big * 0.06 + 0.3, a(WHITE, 0.9F * (1 - sk)), 96);
            ink.ring(g.add(0, 0.5, 0), UP, big * (0.4 + 2.8 * Math.sqrt(sk)), big * 0.18, a(mid, 0.45F * (1 - sk)), 96);
        }
        Random rnd = new Random(seed);
        for (int i = 0; i < 14; i++) {
            ink.flare(g, big * (2 + 3 * rnd.nextDouble()) * core, big * 0.03, a(mid, 0.7F * core), rnd.nextDouble() * Math.PI);
        }
        for (int i = 0; i < 26; i++) {
            double th = rnd.nextDouble() * Math.PI * 2;
            double v = big * (0.3 + 0.4 * rnd.nextDouble()), up = big * (0.3 + 0.5 * rnd.nextDouble());
            double tt = age / 20.0;
            Vec3 p = g.add(Math.cos(th) * v * tt * 4, up * tt * 4 - 9.8 * tt * tt, Math.sin(th) * v * tt * 4);
            Vec3 q = g.add(Math.cos(th) * v * (tt - 0.07) * 4, up * (tt - 0.07) * 4 - 9.8 * (tt - 0.07) * (tt - 0.07), Math.sin(th) * v * (tt - 0.07) * 4);
            if (age > 1 && p.y > g.y - 2) {
                ink.ribbon(new Vec3[]{q, p}, new double[]{0.05, 0.3}, new int[]{a(cool, 0), a(hot, 0.9F * (1 - k))});
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ beats (sounds are the server's)

    static void begin(GCasts.Play c) {
    }

    static void beat(GCasts.Play c, int t) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel l = mc.level;
        if (l == null) {
            return;
        }
        switch (c.move()) {
            case RED -> {
                int at = GBeats.arrive(GBeats.RED_FIRE, GBeats.RED_SPEED, c.msg.origin().distanceTo(c.msg.point()));
                if (t == at) {
                    debris(l, c.msg.point(), 6, 60);
                }
            }
            case MAX_BLUE -> {
                if (t >= GBeats.MB_LAUNCH && t < GBeats.MB_END && t % 2 == 0) {
                    debris(l, GRites.maxBlueAt(c.msg.origin(), c.dir(), t), 4, 10);
                }
            }
            case MAX_RED -> {
                if (t > GBeats.MR_FIRE && t <= GBeats.MR_FIRE + GBeats.MR_TRAVEL) {
                    double d = GBeats.MR_LENGTH * (t - GBeats.MR_FIRE) / (double) GBeats.MR_TRAVEL;
                    debris(l, c.msg.origin().add(c.dir().scale(d)), 5, 16);
                }
            }
            case NUKE -> {
                if (t == GBeats.NK_BLAST) {
                    debris(l, c.msg.point(), 20, 160);
                }
            }
            default -> {
            }
        }
    }

    /** Chunks of whatever the ground there is made of, flung up. */
    static void debris(ClientLevel l, Vec3 at, double spread, int n) {
        BlockState s = l.getBlockState(BlockPos.containing(ground(at)).below());
        if (s.isAir()) {
            s = net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
        }
        Random r = new Random();
        for (int i = 0; i < n; i++) {
            l.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, s), at.x + (r.nextDouble() - 0.5) * spread, at.y + r.nextDouble() * 2,
                    at.z + (r.nextDouble() - 0.5) * spread, (r.nextDouble() - 0.5) * 1.2, r.nextDouble() * 1.4, (r.nextDouble() - 0.5) * 1.2);
            if (i % 4 == 0) {
                l.addParticle(ParticleTypes.LARGE_SMOKE, at.x + (r.nextDouble() - 0.5) * spread, at.y + r.nextDouble() * 2,
                        at.z + (r.nextDouble() - 0.5) * spread, 0, 0.08, 0);
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ the picture

    static void screen(Look look, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        for (GCasts.Play c : GCasts.all()) {
            float t = c.time(partial);
            double d = mc.player.position().distanceTo(c.msg.origin());
            float near = (float) Mth.clamp(1 - d / 160, 0, 1);
            if (near <= 0 && !c.mine) {
                continue;
            }
            switch (c.move()) {
                case RED -> {
                    int at = GBeats.arrive(GBeats.RED_FIRE, GBeats.RED_SPEED, c.msg.origin().distanceTo(c.msg.point()));
                    look.flash(0.6F * near * hit(t, at, 4), 0xFFE0D0);
                    look.shake(0.9F * near * hit(t, at, 14));
                }
                case MAX_BLUE -> {
                    look.shake(0.35F * near * window(t, GBeats.MB_LAUNCH, GBeats.MB_LAUNCH + 4, GBeats.MB_END - 4, GBeats.MB_END));
                    look.flash(0.6F * near * hit(t, GBeats.MB_END, 4), 0xDDF1FF);
                }
                case MAX_RED -> {
                    impactFrames(look, t, GBeats.MR_FIRE, near);
                    look.flash(0.8F * near * hit(t, GBeats.MR_FIRE + 2, 5), 0xFFD6C8);
                    look.shake(1.4F * near * hit(t, GBeats.MR_FIRE, 18));
                }
                case PURPLE -> {
                    impactFrames(look, t, GBeats.HP_FIRE, near);
                    look.shake(1.0F * near * hit(t, GBeats.HP_FIRE, 14));
                }
                case IMAGINARY -> {
                    impactFrames(look, t, GBeats.IM_FIRE, near);
                    look.flash(0.7F * near * hit(t, GBeats.IM_FIRE + 2, 6), 0xF6EAFF);
                    look.shake(1.5F * near * hit(t, GBeats.IM_FIRE, 24));
                    look.vignette(0.6F * window(t, 4, 20, GBeats.IM_FIRE, GBeats.IM_FIRE + 30));
                }
                case PURPLE_200 -> {
                    impactFrames(look, t, GBeats.P2_FIRE, near);
                    look.flash(0.9F * near * hit(t, GBeats.P2_FIRE + 2, 8), 0xF6EAFF);
                    look.shake(2.0F * near * hit(t, GBeats.P2_FIRE, 30));
                    look.vignette(0.7F * window(t, 4, 20, GBeats.P2_FIRE, GBeats.P2_FIRE + 30));
                }
                case NUKE -> {
                    float far = (float) Mth.clamp(1 - mc.player.position().distanceTo(c.msg.point()) / 300, 0, 1);
                    impactFrames(look, t, GBeats.NK_COLLIDE, Math.max(near, far));
                    look.flash(0.75F * far * window(t, GBeats.NK_BLAST, GBeats.NK_BLAST + 1, GBeats.NK_BLAST + 4, GBeats.NK_BLAST + 18), 0xFFF0FF);
                    look.shake(2.4F * far * hit(t, GBeats.NK_BLAST, 50));
                    look.shake(0.4F * far * window(t, GBeats.NK_CONVERGE, GBeats.NK_COLLIDE, GBeats.NK_COLLIDE, GBeats.NK_BLAST));
                }
                case DOMAIN_02 -> {
                    float dd = (float) mc.player.position().distanceTo(c.msg.origin());
                    if (dd < GBeats.D2_RADIUS && t >= GBeats.D2_OPEN - 1 && t < GBeats.D2_CLOSE + 2) {
                        look.flash(0.25F, 0xFFFFFF);
                    }
                }
                default -> {
                }
            }
        }
    }

    /** The two frames of an impact: blacked out for a tick, then burnt white for a tick. */
    static void impactFrames(Look look, float t, int at, float k) {
        if (k < 0.15F) {
            return;
        }
        if (t >= at && t < at + 1) {
            look.flash(0.92F * k, 0x000000);
        } else if (t >= at + 1 && t < at + 2) {
            look.flash(k, 0xFFFFFF);
        }
    }

    // ------------------------------------------------------------------------------------------------ the world

    static void draw(Ink ink, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        GInfinity.draw(ink, partial);
        for (GCasts.Play c : GCasts.all()) {
            float t = c.time(partial);
            switch (c.move()) {
                case RED -> drawRed(ink, c, t, partial);
                case MAX_BLUE -> drawMaxBlue(ink, c, t, partial);
                case MAX_RED -> drawMaxRed(ink, c, t, partial);
                case PURPLE -> drawHanami(ink, c, t, partial);
                case IMAGINARY -> drawChanted(ink, c, t, partial, false);
                case PURPLE_200 -> drawChanted(ink, c, t, partial, true);
                case NUKE -> drawNuke(ink, c, t, partial);
                case DOMAIN -> GVoid.drawDomain(ink, c, t, partial);
                case DOMAIN_02 -> GVoid.drawFlash(ink, c, t, partial);
            }
        }
    }

    // ---- Reversal: Red

    static void drawRed(Ink ink, GCasts.Play c, float t, float partial) {
        Vec3 o = c.msg.origin(), p = c.msg.point();
        double dist = o.distanceTo(p);
        int at = GBeats.arrive(GBeats.RED_FIRE, GBeats.RED_SPEED, dist);
        if (t < GBeats.RED_FIRE) {
            float k = span(t, 0, GBeats.RED_FIRE - 2);
            redOrb(ink, hand(c, partial, true).add(c.dir().scale(0.3)), 0.08 + 0.32 * k, t, Math.min(1, k * 3), c.msg.seed());
            // the light around him pulls toward the point: a red tint ring closing in
            ink.ring(hand(c, partial, true), ink.cam().subtract(hand(c, partial, true)), 3.5 * (1 - k) + 0.4, 0.06, a(RED, 0.5F * k), 40);
        } else if (t < at) {
            Vec3 pos = o.add(c.dir().scale(Math.min(dist, (t - GBeats.RED_FIRE) * GBeats.RED_SPEED)));
            Vec3 tail = o.add(c.dir().scale(Math.max(0, (t - GBeats.RED_FIRE) * GBeats.RED_SPEED - 7)));
            ink.ribbon(new Vec3[]{tail, tail.lerp(pos, 0.7), pos}, new double[]{0.02, 0.35, 0.55},
                    new int[]{a(RED, 0), a(RED, 0.7F), a(RED_CORE, 0.95F)});
            redOrb(ink, pos, 0.5, t, 1, c.msg.seed());
        }
        if (t >= at) {
            float age = t - at;
            burst(ink, p, GBeats.RED_CRATER, age, c.msg.seed(), RED_CORE, RED, CRIMSON);
            // the red bloom: a flat disc of light spreading over the ground
            float bk = span(age, 0, 18);
            if (bk < 1) {
                Vec3 g = ground(p).add(0, 0.15, 0);
                ink.disc(true, g, UP, GBeats.RED_CRATER * (0.6 + 1.8 * bk), a(RED, 0.55F * (1 - bk)), a(ORANGE, 0));
            }
        }
    }

    // ---- Maximum Output: Blue

    static void drawMaxBlue(Ink ink, GCasts.Play c, float t, float partial) {
        if (t > GBeats.MB_END + 30) {
            return;
        }
        Vec3 o = GRites.maxBlueAt(c.msg.origin(), c.dir(), t);
        double r;
        float k = 1;
        if (t < GBeats.MB_LAUNCH) {
            float u = span(t, 0, GBeats.MB_LAUNCH);
            r = GBeats.MB_RADIUS * (0.15 + 0.85 * smooth(u));
            // it forms above his raised hand, then drifts out in front
            Vec3 hand = hand(c, partial, true).add(0, 0.9, 0);
            o = hand.lerp(o, smooth(span(t, GBeats.MB_LAUNCH - 10, GBeats.MB_LAUNCH)));
        } else if (t < GBeats.MB_END) {
            r = GBeats.MB_RADIUS * (1 + 0.05 * Math.sin(t * 0.8));
        } else {
            float u = span(t, GBeats.MB_END, GBeats.MB_END + 6);
            r = GBeats.MB_RADIUS * (1 - u);
            k = 1 - u;
            burst(ink, o, 5, t - GBeats.MB_END, c.msg.seed(), BLUE_CORE, BLUE, DEEP_BLUE);
        }
        blueOrb(ink, o, r, t, k, c.msg.seed());
        if (t >= GBeats.MB_LAUNCH && t < GBeats.MB_END) {
            // the world is dragged in: rocks orbiting and falling into it, a whirl of dust below
            Random rnd = new Random(c.msg.seed());
            for (int i = 0; i < 22; i++) {
                double ang = rnd.nextDouble() * Math.PI * 2 + t * (0.15 + rnd.nextDouble() * 0.2);
                double rad = r * (1.3 + 1.6 * ((rnd.nextDouble() + t * 0.01) % 1.0));
                double y = (rnd.nextDouble() - 0.5) * r * 1.6;
                Vec3 pos = o.add(Math.cos(ang) * rad, y, Math.sin(ang) * rad);
                double s = 0.25 + rnd.nextDouble() * 0.45;
                Vec3 ax = new Vec3(Math.cos(ang + t * 0.2), 0.3, Math.sin(ang)).normalize().scale(s);
                Vec3 ay = new Vec3(0, 1, 0.2).normalize().scale(s * 0.8);
                Vec3 az = ax.cross(ay).normalize().scale(s * 0.9);
                ink.box(false, pos, ax, ay, az, i % 3 == 0 ? 0xFF6B5A48 : 0xFF7E7E80, i % 3 == 0 ? 0xFF4A3E32 : 0xFF5A5A5E);
            }
            Vec3 g = ground(o).add(0, 0.1, 0);
            for (int i = 0; i < 3; i++) {
                ink.arc(g, UP, r * (1.6 + i * 0.6), 0.25, t * 0.3 + i, t * 0.3 + i + 3.6, a(BLUE, 0.35F), 32, true);
            }
            // the trench behind it glows faintly blue for a moment
            Vec3 from = GRites.maxBlueAt(c.msg.origin(), c.dir(), GBeats.MB_LAUNCH);
            ink.beam(ground(from).add(0, 0.3, 0), ground(o).add(0, 0.3, 0), r * 0.9, a(BLUE, 0.12F));
        }
    }

    // ---- Maximum Output: Red

    static void drawMaxRed(Ink ink, GCasts.Play c, float t, float partial) {
        Vec3 o = c.msg.origin();
        if (t < GBeats.MR_FIRE + 2) {
            float k = span(t, 0, GBeats.MR_FIRE);
            Vec3 h = hand(c, partial, true).add(c.dir().scale(0.4));
            redOrb(ink, h, 0.15 + 0.55 * k, t, Math.min(1, k * 2), c.msg.seed());
            // long red rays reaching far out of the point (the charge)
            Random rnd = new Random(c.msg.seed());
            for (int i = 0; i < 8; i++) {
                Vec3 d = new Vec3(rnd.nextDouble() - 0.5, rnd.nextDouble() - 0.5, rnd.nextDouble() - 0.5).normalize();
                ink.beam(h, h.add(d.scale(6 + 14 * k)), 0.05 + 0.08 * k, a(RED, 0.6F * k));
            }
            ink.glow(h, 6 * k, a(RED, 0.25F * k));
        }
        if (t >= GBeats.MR_FIRE) {
            float age = t - GBeats.MR_FIRE;
            float travel = span(age, 0, GBeats.MR_TRAVEL);
            float fade = 1 - span(age, GBeats.MR_TRAVEL, GBeats.MR_TRAVEL + 24);
            double d = GBeats.MR_LENGTH * travel;
            Vec3 tip = o.add(c.dir().scale(d));
            // the blast: a cone of light from his hand to the wave front, the front a burning disc
            ink.ribbon(new Vec3[]{o.add(c.dir().scale(1.2)), o.add(c.dir().scale(d * 0.5)), tip}, new double[]{1.2, GBeats.MR_R1 * 0.9, GBeats.MR_R1 * 1.2},
                    new int[]{a(WHITE, 0.95F * fade), a(RED_CORE, 0.8F * fade), a(RED, 0.7F * fade)});
            ink.ribbon(new Vec3[]{o.add(c.dir().scale(1.2)), tip}, new double[]{2.4, GBeats.MR_R1 * 2.0}, new int[]{a(RED, 0.4F * fade), a(CRIMSON, 0.3F * fade)});
            if (travel < 1) {
                ink.disc(true, tip, c.dir(), GBeats.MR_R1 * 1.3, a(RED_CORE, 0.8F), a(RED, 0));
                ink.ring(tip, c.dir(), GBeats.MR_R1 * 1.5, 0.6, a(WHITE, 0.9F), 64);
            }
            for (int i = 0; i < 6; i++) {
                double s = d * (i + 0.5) / 6;
                float rk = 1 - span(age, i * 2, i * 2 + 18);
                ink.ring(o.add(c.dir().scale(s)), c.dir(), (GBeats.MR_R0 + 3) * (1 + (1 - rk) * 0.8), 0.4, a(RED, 0.7F * rk * fade), 48);
            }
            burst(ink, tip, 6, age - GBeats.MR_TRAVEL, c.msg.seed(), RED_CORE, RED, CRIMSON);
        }
    }

    // ---- Hollow Purple (Hanami)

    static void drawHanami(Ink ink, GCasts.Play c, float t, float partial) {
        Vec3 dir = c.dir();
        if (t < GBeats.HP_FIRE + 1) {
            // as it is choreographed: he squares up, Red blooms at his left and Blue at his right, each as big as he
            // is, then both are pulled into one point before his outstretched hand and become Purple, which he holds
            // there, crackling, before it goes
            Vec3 chest = c.feet(partial).add(0, 1.3, 0);
            Vec3 r = c.right();
            Vec3 centre = c.eye(partial).add(dir.scale(2.4)).add(0, -0.3, 0);
            float m = smooth(span(t, GBeats.HP_MERGE - 5, GBeats.HP_MERGE));
            float rk = smooth(span(t, GBeats.HP_BLUE, GBeats.HP_BLUE + 7)), bk = smooth(span(t, GBeats.HP_RED, GBeats.HP_RED + 7));
            Vec3 rp = chest.add(r.scale(-2.6)).add(dir.scale(0.6)).lerp(centre, m), bp = chest.add(r.scale(2.6)).add(dir.scale(0.6)).lerp(centre, m);
            if (t < GBeats.HP_MERGE + 1) {
                redOrb(ink, rp, 1.0 * rk * (1 - 0.4 * m), t, rk, c.msg.seed());
                blueOrb(ink, bp, 1.1 * bk * (1 - 0.4 * m), t, bk, c.msg.seed());
                if (m > 0.2F) {
                    lightning(ink, rp.lerp(bp, 0.5), 0.6, t, m, c.msg.seed() + 5, 4, rp.distanceTo(bp) * 0.6);
                }
            }
            if (t >= GBeats.HP_MERGE - 1) {
                float pk = span(t, GBeats.HP_MERGE - 1, GBeats.HP_MERGE + 5);
                double size = 0.5 + 1.3 * smooth(span(t, GBeats.HP_MERGE, GBeats.HP_FIRE - 2));
                purpleOrb(ink, centre, size * pk, t, pk, c.msg.seed());
                // a thread of violet from his fingertips to it
                Vec3 h = hand(c, partial, true).add(dir.scale(0.4));
                GShade.ribbon(new Vec3[]{h, centre}, new float[]{0.04F, 0.12F}, GShade.PUR_B, 0.8F * pk, 1);
            }
        }
        flight(ink, c, t, GBeats.HP_FIRE, GBeats.HP_SPEED, GBeats.HP_RANGE, GBeats.HP_RADIUS, purpleStart(c, partial, 2.2));
    }

    static Vec3 purpleStart(GCasts.Play c, float partial, double ahead) {
        double lift = switch (c.move()) {
            case IMAGINARY -> GBeats.IM_RISE;
            case PURPLE_200 -> GBeats.P2_RISE;
            default -> 0;
        };
        return c.msg.origin().add(0, lift, 0).add(c.dir().scale(ahead));
    }

    /** The sphere in flight, the erased wake behind it, and the shock where it left. */
    static void flight(Ink ink, GCasts.Play c, float t, int fire, double speed, double range, double radius, Vec3 from) {
        if (t < fire) {
            return;
        }
        float age = t - fire;
        double d = Math.min(range, age * speed);
        Vec3 pos = from.add(c.dir().scale(d));
        float gone = span(age, (float) (range / speed), (float) (range / speed) + 8);
        if (gone < 1) {
            purpleOrb(ink, pos, radius * (1 - 0.5 * gone), t, 1 - gone, c.msg.seed());
            // a ring of distorted air around it, facing its way
            ink.ring(pos, c.dir(), radius * 1.5, radius * 0.08, a(MAGENTA, 0.6F * (1 - gone)), 64);
            ink.ring(pos.subtract(c.dir().scale(radius)), c.dir(), radius * 1.2, radius * 0.05, a(WHITE, 0.5F * (1 - gone)), 64);
        }
        // the wake: everything it touched is simply gone; the hollow glows violet and fades
        float wake = 1 - span(age, (float) (range / speed), (float) (range / speed) + 30);
        if (wake > 0.01F) {
            ink.ribbon(new Vec3[]{from, from.lerp(pos, 0.5), pos}, new double[]{radius * 0.3, radius * 0.75, radius * 0.9},
                    new int[]{a(PURPLE, 0.1F * wake), a(PURPLE, 0.3F * wake), a(VIOLET_CORE, 0.5F * wake)});
        }
        // where it left: a shock that blows outward
        burst(ink, from, radius * 0.4, age, c.msg.seed(), VIOLET_CORE, PURPLE, DARK_VIOLET);
    }

    // ---- Imaginary Technique: Purple (Toji) and 200%

    static void drawChanted(Ink ink, GCasts.Play c, float t, float partial, boolean p200) {
        int red = p200 ? GBeats.P2_RED : GBeats.IM_RED, blue = p200 ? GBeats.P2_BLUE : GBeats.IM_BLUE;
        int merge = p200 ? GBeats.P2_MERGE : GBeats.IM_MERGE, fire = p200 ? GBeats.P2_FIRE : GBeats.IM_FIRE;
        double radius = p200 ? GBeats.P2_RADIUS : GBeats.IM_RADIUS;
        double scale = p200 ? 1.6 : 1.0;
        Vec3 eye = c.eye(partial);
        Vec3 dir = c.dir();
        Vec3 r = c.right();
        // the incantation: each word sends a ring of light out through the air around him
        int shift = p200 ? GBeats.P2_SHIFT : 0;
        for (int i = 0; i < GBeats.CHANT.length; i++) {
            float w = t - (GBeats.CHANT[i] + shift);
            if (w >= 0 && w < 30) {
                float k = w / 30F;
                ink.ring(c.feet(partial).add(0, 0.2, 0), UP, 1.5 + 14 * k * scale, 0.1 + 0.3 * (1 - k), a(i % 2 == 0 ? BLUE_CORE : RED_CORE, 0.7F * (1 - k)), 96);
                ink.ring(eye, dir, 0.8 + 3 * k, 0.05, a(WHITE, 0.6F * (1 - k)), 64);
            }
        }
        if (t < fire + 1) {
            Vec3 centre = eye.add(dir.scale(2.2 * scale)).add(0, -0.2, 0);
            float m = smooth(span(t, merge - 10, merge));
            Vec3 rp = eye.add(r.scale(-2.8 * scale)).add(dir.scale(1.2)).add(0, 0.8, 0).lerp(centre, m);
            Vec3 bp = eye.add(r.scale(2.8 * scale)).add(dir.scale(1.2)).add(0, 0.8, 0).lerp(centre, m);
            float rk = span(t, red, red + 8), bk = span(t, blue, blue + 8);
            if (t < merge + 2) {
                redOrb(ink, rp, 1.3 * scale * rk, t, rk, c.msg.seed());
                blueOrb(ink, bp, 1.4 * scale * bk, t, bk, c.msg.seed());
                if (m > 0.3F) {
                    // the two pull at each other: a crackling bridge
                    lightning(ink, rp.lerp(bp, 0.5), rp.distanceTo(bp) * 0.25, t, m, c.msg.seed() + 7, 6, rp.distanceTo(bp) * 0.6);
                }
            }
            if (t >= merge - 1) {
                float pk = span(t, merge - 1, merge + 10);
                double grow = radius * (0.25 + 0.75 * smooth(span(t, merge, fire - 4)));
                purpleOrb(ink, centre, grow * pk, t, pk, c.msg.seed());
                // the air is sucked into it before it goes
                for (int i = 0; i < 3; i++) {
                    float u = ((t * 0.05F + i / 3F) % 1F);
                    ink.ring(centre, ink.cam().subtract(centre), grow * (4 - 3 * u), 0.08 * scale, a(MAGENTA, 0.5F * u * pk), 64);
                }
                if (p200) {
                    ink.flare(centre, grow * 6, grow * 0.06, a(WHITE, 0.8F * pk), t * 0.01);
                    ink.flare(centre, grow * 4, grow * 0.05, a(MAGENTA, 0.6F * pk), t * 0.01 + 0.4);
                }
            }
        }
        // the aim: a ring of light and a four-pointed star at his fingertip, pointed down the line it will go
        float aim = window(t, fire - 14, fire - 8, fire, fire + 3);
        if (aim > 0.01F) {
            Vec3 tip = hand(c, partial, true).add(dir.scale(0.6)).add(0, 0.25, 0);
            GShade.glow(tip, 1.6 * scale * (1.4 - 0.4 * aim), GShade.MAGENTA, 0.8F * aim, 2);
            GShade.glow(tip, 0.9 * scale, GShade.PUR_B, 0.9F * aim, 2);
            GShade.glow(tip, 4.0 * scale, GShade.WHITE, 0.7F * aim, 4);
            ink.ring(tip, dir, 2.4 * scale * (1.5 - 0.5 * aim), 0.03, a(MAGENTA, 0.6F * aim), 64);
        }
        flight(ink, c, t, fire, p200 ? GBeats.P2_SPEED : GBeats.IM_SPEED, p200 ? GBeats.P2_RANGE : GBeats.IM_RANGE, radius,
                purpleStart(c, partial, 4.0));
        if (p200 && t >= fire) {
            // twice the strength: a beam of white rides inside the wake
            float age = t - fire;
            float f = 1 - span(age, 30, 70);
            Vec3 from = purpleStart(c, partial, 4.0);
            Vec3 to = from.add(dir.scale(Math.min(GBeats.P2_RANGE, age * GBeats.P2_SPEED)));
            ink.beam(from, to, radius * 0.35, a(WHITE, 0.6F * f));
            ink.beam(from, to, radius * 1.1, a(MAGENTA, 0.25F * f));
        }
    }

    // ---- Hollow Nuke

    static Vec3 nukeBlue(GCasts.Play c) {
        return GRites.nukeTarget(c.msg.point()).add(c.right().scale(-34)).add(0, 22, 0);
    }

    static Vec3 nukeRed(GCasts.Play c) {
        return GRites.nukeTarget(c.msg.point()).add(c.right().scale(34)).add(0, 22, 0);
    }

    static void drawNuke(Ink ink, GCasts.Play c, float t, float partial) {
        Vec3 T = GRites.nukeTarget(c.msg.point());
        Vec3 g = c.msg.point();
        if (t < GBeats.NK_COLLIDE + 2) {
            float conv = smooth(span(t, GBeats.NK_CONVERGE, GBeats.NK_COLLIDE));
            float bk = span(t, GBeats.NK_BLUE, GBeats.NK_BLUE + 30), rk = span(t, GBeats.NK_RED, GBeats.NK_RED + 30);
            Vec3 bp = nukeBlue(c).lerp(T, conv), rp = nukeRed(c).lerp(T, conv);
            blueOrb(ink, bp, 6.5 * bk, t, bk, c.msg.seed());
            redOrb(ink, rp, 5.5 * rk, t, rk, c.msg.seed());
            // each drags a tail of its colour as it comes in
            if (conv > 0.02F) {
                ink.ribbon(new Vec3[]{nukeBlue(c), bp}, new double[]{0.5, 4}, new int[]{a(BLUE, 0), a(BLUE, 0.5F)});
                ink.ribbon(new Vec3[]{nukeRed(c), rp}, new double[]{0.5, 3.5}, new int[]{a(RED, 0), a(RED, 0.5F)});
            }
            // a thread of light from his hands to each
            Vec3 h = c.eye(partial);
            ink.beam(h, bp, 0.08, a(BLUE_CORE, 0.5F * bk));
            ink.beam(h, rp, 0.08, a(RED_CORE, 0.5F * rk));
        }
        if (t >= GBeats.NK_COLLIDE - 2 && t < GBeats.NK_BLAST + 2) {
            float pk = span(t, GBeats.NK_COLLIDE - 2, GBeats.NK_BLAST);
            purpleOrb(ink, T, 4 + 8 * pk, t, 1, c.msg.seed());
            lightning(ink, T, 6 + 8 * pk, t, 1, c.msg.seed() + 11, 12, 40 * pk + 10);
        }
        if (t >= GBeats.NK_BLAST) {
            float age = t - GBeats.NK_BLAST;
            float k = Mth.clamp(age / 110F, 0, 1);
            double R = GBeats.NK_CRATER * 1.6;
            // the detonation: a white sun that swells into a violet dome, a pillar, rings racing over the land
            float core = (float) Math.exp(-age / 5.0);
            ink.glow(T, R * (2 + 3 * (1 - core)), a(WHITE, core));
            ink.sphere(true, g, R * (0.4 + 0.9 * Math.sqrt(Math.min(1, age / 16F))), a(PURPLE, 0.45F * (1 - k) * (1 - k)), a(MAGENTA, 0.55F * (1 - k)), 1.2);
            ink.sphere(false, g.add(0, R * 0.2, 0), R * (0.6 + 0.9 * k), a(DARK_VIOLET, 0.45F * window(age, 6, 20, 60, 110)), a(NIGHT, 0), 0.7);
            ink.beam(g, g.add(0, R * 5 * Math.sqrt(Math.min(1, age / 20F)), 0), R * 0.5 * (1 - k), a(VIOLET_CORE, 0.7F * (1 - k)));
            ink.beam(g, g.add(0, R * 5 * Math.sqrt(Math.min(1, age / 20F)), 0), R * 1.4 * (1 - k), a(MAGENTA, 0.25F * (1 - k)));
            float sk = Mth.clamp(age / 30F, 0, 1);
            if (sk < 1) {
                ink.ring(g.add(0, 0.5, 0), UP, R * (0.5 + 4.5 * Math.sqrt(sk)), 1 + R * 0.04, a(WHITE, 0.95F * (1 - sk)), 160);
                ink.ring(g.add(0, 1.5, 0), UP, R * (0.4 + 3.6 * Math.sqrt(sk)), R * 0.15, a(MAGENTA, 0.4F * (1 - sk)), 160);
                ink.ring(g.add(0, R * 0.8, 0), UP, R * (0.3 + 3.0 * Math.sqrt(sk)), R * 0.08, a(PURPLE, 0.4F * (1 - sk)), 160);
            }
            lightning(ink, g.add(0, R * 0.5, 0), R * 0.8, t, 1 - k, c.msg.seed() + 21, 16, R * 2.5);
            burst(ink, g, R * 0.4, age, c.msg.seed(), VIOLET_CORE, PURPLE, DARK_VIOLET);
        }
    }
}
