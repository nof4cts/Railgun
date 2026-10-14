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

    /** How far this player is from where it happens, as 1 (on top of it) .. 0 (out of reach). */
    static float near(GCasts.Play c, Vec3 at, double reach) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return 0;
        }
        return c.mine ? 1 : (float) Mth.clamp(1 - mc.player.position().distanceTo(at) / reach, 0, 1);
    }

    /** An anime impact frame (the original technique's ink-and-speed-lines shader). */
    static void impact(int style, int hold, int fade) {
        com.gojo.mod.cine.client.Impact.hit(style, hold, fade, 0.5F, 0.5F);
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
                    if (near(c, c.msg.point(), 90) > 0.2F) {
                        impact(2, 2, 4);
                    }
                }
            }
            case MAX_BLUE -> {
                Vec3 o = c.pos(0);
                if (o != null && t >= GBeats.MB_LAUNCH && t < GBeats.MB_END && t % 2 == 0) {
                    debris(l, o, 4, 10);
                }
                if (t == GBeats.MB_END && o != null && near(c, o, 90) > 0.2F) {
                    impact(3, 2, 5);
                }
            }
            case MAX_RED -> {
                if (t == GBeats.MR_FIRE && near(c, c.msg.origin(), 120) > 0.2F) {
                    impact(2, 2, 6);
                }
                if (t > GBeats.MR_FIRE && t <= GBeats.MR_FIRE + GBeats.MR_TRAVEL) {
                    double d = GBeats.MR_LENGTH * (t - GBeats.MR_FIRE) / (double) GBeats.MR_TRAVEL;
                    debris(l, c.msg.origin().add(c.dir().scale(d)), 5, 16);
                }
            }
            case IMAGINARY -> {
                // out of the drops cutaway, the Purple simply there
                if (t == GBeats.IM_MERGE && near(c, c.msg.origin(), 120) > 0.2F) {
                    impact(1, 2, 6);
                }
            }
            case NUKE -> {
                if (t == GBeats.NK_MERGE && near(c, c.msg.origin(), 300) > 0.15F) {
                    impact(1, 2, 4);
                }
                if (c.fireAt >= 0 && t == c.fireAt + GBeats.NK_DROP) {
                    debris(l, c.target, 20, 160);
                    if (near(c, c.target, 320) > 0.1F) {
                        impact(0, 2, 2);
                    }
                }
                if (c.fireAt >= 0 && t == c.fireAt + GBeats.NK_DROP + 2 && near(c, c.target, 320) > 0.1F) {
                    impact(1, 3, 12);
                }
            }
            default -> {
            }
        }
    }

    /** A held Purple let go: the impact frames of its release. */
    static void fired(GCasts.Play c) {
        if (near(c, c.fireFrom, 160) < 0.15F) {
            return;
        }
        switch (c.move()) {
            case PURPLE -> impact(1, 2, 5);
            case IMAGINARY -> impact(1, 3, 8);
            case PURPLE_200 -> {
                impact(0, 1, 1);
                impact(1, 4, 10);
            }
            case NUKE -> impact(1, 2, 3);
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
            float near = near(c, c.msg.origin(), 160);
            if (near <= 0) {
                continue;
            }
            float since = c.fireAt >= 0 ? t - c.fireAt : -1;
            switch (c.move()) {
                case RED -> {
                    int at = GBeats.arrive(GBeats.RED_FIRE, GBeats.RED_SPEED, c.msg.origin().distanceTo(c.msg.point()));
                    look.shake(0.9F * near * hit(t, at, 14));
                }
                case MAX_BLUE -> look.shake(0.35F * near * window(t, GBeats.MB_LAUNCH, GBeats.MB_LAUNCH + 4, GBeats.MB_END - 4, GBeats.MB_END));
                case MAX_RED -> look.shake(1.4F * near * hit(t, GBeats.MR_FIRE, 18));
                case PURPLE, IMAGINARY, PURPLE_200 -> {
                    float big = c.move() == GMove.PURPLE_200 ? 2.0F : c.move() == GMove.IMAGINARY ? 1.5F : 1.0F;
                    if (since >= 0) {
                        look.shake(big * near * hit(since, 0, 22));
                    }
                    int[] b = GBeats.purpleBeats(c.move());
                    look.shake(0.25F * big * near * window(t, b[4], b[6], b[7], b[7] + 4));
                    look.vignette(0.5F * window(t, 4, 20, b[7], b[7] + 10));
                }
                case NUKE -> {
                    Vec3 g = c.target != null ? c.target : c.msg.point();
                    float far = near(c, g, 320);
                    look.shake(0.4F * far * window(t, GBeats.NK_JOIN, GBeats.NK_MERGE, GBeats.NK_MERGE + 6, GBeats.NK_READY));
                    if (since >= 0) {
                        float blast = since - GBeats.NK_DROP;
                        look.flash(0.55F * far * window(blast, 0, 1, 4, 16), 0xFFF0FF);
                        look.shake(2.4F * far * hit(blast, 0, 50));
                    }
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
                case PURPLE, IMAGINARY, PURPLE_200 -> drawPurple(ink, c, t, partial);
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
        Vec3 o = c.pos(partial);
        if (o == null) {
            o = hand(c, partial, true).add(0, 0.9, 0);
        }
        double r;
        float k = 1;
        if (t < GBeats.MB_LAUNCH) {
            float u = span(t, 0, GBeats.MB_LAUNCH);
            r = GBeats.MB_RADIUS * (0.15 + 0.85 * smooth(u));
            // it forms above his raised hand, then drifts out in front
            Vec3 hand = hand(c, partial, true).add(0, 0.9, 0);
            o = hand.lerp(o, smooth(span(t, GBeats.MB_LAUNCH - 10, GBeats.MB_LAUNCH)));
            // the light round him bends into it as it gathers
            ink.ring(o, ink.cam().subtract(o), r * (3.5 - 2.5 * u), 0.08, a(BLUE_CORE, 0.5F * u), 64);
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
            // a thread of blue from his hand to it: he is steering it
            ink.beam(hand(c, partial, true), o, 0.06, a(BLUE_CORE, 0.45F));
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

    // ---- the Purples

    /**
     * Every Purple, risen into the air: Red appears at his left as he names it, Blue at his right as he names it; the two
     * are drawn together before him and pressed into one; it is held there, crackling and swelling, pointed wherever he
     * looks, until he lets it go; then it erases everything down its line.
     */
    static void drawPurple(Ink ink, GCasts.Play c, float t, float partial) {
        GMove m = c.move();
        int[] b = GBeats.purpleBeats(m);
        double scale = m == GMove.PURPLE_200 ? 2.0 : m == GMove.IMAGINARY ? 1.4 : 1.0;
        double radius = m == GMove.PURPLE_200 ? GBeats.P2_RADIUS : m == GMove.IMAGINARY ? GBeats.IM_RADIUS : GBeats.HP_RADIUS;
        long seed = c.msg.seed();
        Vec3 eye = c.eye(partial);
        Vec3 aim = c.aimDir(partial);
        Vec3 f = c.flat(), r = c.right();
        Vec3 centre = eye.add(aim.scale(GBeats.purpleAhead(m))).add(0, -0.2, 0);
        boolean toji = m == GMove.IMAGINARY;
        // held while he aims: drawn off to his right and a little low, out of the line of sight, smaller in his own eyes
        float hold = c.fireAt < 0 ? smooth(span(t, b[7], b[7] + 6)) : 0;
        if (hold > 0) {
            Vec3 side = new Vec3(-aim.z, 0, aim.x);
            side = side.lengthSqr() < 1e-6 ? r : side.normalize();
            double off = toji ? 0.8 : 1.6 * scale;
            centre = centre.add(side.scale(off * hold)).add(0, -(toji ? 0.4 : 0.7 * scale) * hold, 0);
        }
        double heldScale = 1 - (c.mine ? 0.35 : 0.0) * hold;
        if (m == GMove.PURPLE_200) {
            // the incantation: each word sends a ring of light out through the air around him
            int shift = m == GMove.PURPLE_200 ? GBeats.P2_SHIFT : 0;
            for (int i = 0; i < GBeats.CHANT.length; i++) {
                float w = t - (GBeats.CHANT[i] + shift);
                if (w >= 0 && w < 30) {
                    float k = w / 30F;
                    ink.ring(c.feet(partial).add(0, 0.2, 0), UP, 1.5 + 14 * k * scale, 0.1 + 0.3 * (1 - k), a(i % 2 == 0 ? BLUE_CORE : RED_CORE, 0.7F * (1 - k)), 96);
                    ink.ring(eye, f, 0.8 + 3 * k, 0.05, a(WHITE, 0.6F * (1 - k)), 64);
                }
            }
        }
        if (c.fireAt < 0 || t < c.fireAt + 2) {
            // the two colours, each blooming at its side as it is named, then drawn in
            float join = smooth(span(t, b[4], b[6]));
            float rk = smooth(span(t, b[1], b[1] + 8)), bk = smooth(span(t, b[3], b[3] + 8));
            Vec3 side = eye.add(f.scale(0.9)).add(0, 0.3, 0);
            Vec3 rp = side.add(r.scale(-2.6 * scale)).lerp(centre, join);
            Vec3 bp = side.add(r.scale(2.6 * scale)).lerp(centre, join);
            // as each is named, a pulse of its colour gathers where it will appear
            float nr = window(t, b[0], b[0] + 6, b[1] - 4, b[1]), nb = window(t, b[2], b[2] + 6, b[3] - 4, b[3]);
            if (nr > 0.01F && !toji) {
                ink.ring(rp, ink.cam().subtract(rp), 1.4 * scale * (1.4 - 0.4 * nr), 0.05, a(RED, 0.6F * nr), 48);
                GShade.glow(rp, 0.8 * scale, GShade.RED_A, 0.5F * nr, 0);
            }
            if (nb > 0.01F && !toji) {
                ink.ring(bp, ink.cam().subtract(bp), 1.4 * scale * (1.4 - 0.4 * nb), 0.05, a(BLUE, 0.6F * nb), 48);
                GShade.glow(bp, 0.8 * scale, GShade.BLUE_A, 0.5F * nb, 0);
            }
            if (t < b[6] + 1 && !toji) {
                double shrink = 1 - 0.45 * join;
                redOrb(ink, rp, 1.0 * scale * rk * shrink, t, rk, seed);
                blueOrb(ink, bp, 1.1 * scale * bk * shrink, t, bk, seed);
                if (join > 0.05F) {
                    // they pull at each other: a crackling bridge, tighter as they close
                    lightning(ink, rp.lerp(bp, 0.5), 0.4 * scale, t, join, seed + 5, 5, rp.distanceTo(bp) * 0.55 + 0.5);
                    Vec3 hl = hand(c, partial, false), hr = hand(c, partial, true);
                    GShade.ribbon(new Vec3[]{hr, rp}, new float[]{0.03F, 0.1F}, GShade.RED_A, 0.6F * join, 1);
                    GShade.ribbon(new Vec3[]{hl, bp}, new float[]{0.03F, 0.1F}, GShade.BLUE_A, 0.6F * join, 1);
                }
            }
            if (t >= b[6] - 1) {
                float pk = span(t, b[6] - 1, b[6] + 6);
                // it swells while it is held, breathing
                double size = radius * (0.35 + 0.25 * smooth(span(t, b[6], b[7] + 20))) * (1 + 0.04 * Math.sin(t * 0.5)) * heldScale;
                if (toji) {
                    // as against Toji: a small sphere just past his fingertips
                    size = 0.55 * (1 + 0.2 * smooth(span(t, b[6], b[7] + 20))) * (1 + 0.04 * Math.sin(t * 0.5)) * heldScale;
                }
                purpleOrb(ink, centre, size * pk, t, pk, seed);
                // the air is sucked into it
                for (int i = 0; i < 3; i++) {
                    float u = ((t * 0.05F + i / 3F) % 1F);
                    ink.ring(centre, ink.cam().subtract(centre), size * (4 - 3 * u), 0.05 * scale, a(MAGENTA, 0.4F * u * pk), 64);
                }
                if (toji) {
                    halo(ink, centre, size * pk, t, pk, seed);
                }
                Vec3 h = hand(c, partial, true);
                GShade.ribbon(new Vec3[]{h, centre}, new float[]{0.04F, (float) (0.12 * scale)}, GShade.PUR_B, 0.7F * pk, 1);
                if (c.aiming(partial)) {
                    // the aim: a ring and a four-pointed star down its line
                    Vec3 tip = centre.add(aim.scale(size * 1.6));
                    GShade.glow(tip, 1.2 * scale, GShade.MAGENTA, 0.7F, 2);
                    GShade.glow(tip, 3.0 * scale, GShade.WHITE, 0.5F, 4);
                }
            }
        }
        if (c.fireAt >= 0) {
            flight(ink, c, t - c.fireAt, m == GMove.PURPLE_200 ? GBeats.P2_SPEED : m == GMove.IMAGINARY ? GBeats.IM_SPEED : GBeats.HP_SPEED,
                    m == GMove.PURPLE_200 ? GBeats.P2_RANGE : m == GMove.IMAGINARY ? GBeats.IM_RANGE : GBeats.HP_RANGE, toji ? 1.2 : radius, c.fireFrom, c.fireDir, t);
            if (m == GMove.PURPLE_200) {
                // twice the strength: a beam of white rides inside the wake
                float age = t - c.fireAt;
                float fade = 1 - span(age, 30, 70);
                Vec3 to = c.fireFrom.add(c.fireDir.scale(Math.min(GBeats.P2_RANGE, age * GBeats.P2_SPEED)));
                ink.beam(c.fireFrom, to, radius * 0.25, a(VIOLET_CORE, 0.35F * fade));
                GShade.ribbon(new Vec3[]{c.fireFrom, to}, new float[]{(float) (radius * 0.6), (float) (radius * 0.9)}, GShade.PUR_A, 0.4F * fade, 1);
            }
        }
    }

    /**
     * The Purple of the Toji fight, held: a white-hot core, rings of pink light standing round it facing the eye, a
     * thin pillar of light straight through it, and violet crackle at its edge.
     */
    static void halo(Ink ink, Vec3 centre, double size, float t, float k, long seed) {
        if (k <= 0.01F) {
            return;
        }
        Vec3 toCam = ink.cam().subtract(centre);
        GShade.glow(centre, size * 1.3, GShade.WHITE, 0.85F * k, 0);
        GShade.glow(centre, size * 2.6, GShade.MAGENTA, 0.45F * k, 2);
        // the rings: one tight round the core, two wider, breathing slowly out of step
        double[] rr = {1.35, 2.3, 3.6};
        float[] al = {0.9F, 0.75F, 0.55F};
        for (int i = 0; i < rr.length; i++) {
            double r = size * rr[i] * (1 + 0.03 * Math.sin(t * 0.25 + i * 1.9));
            ink.ring(centre, toCam, r, size * (0.06 - 0.012 * i), a(MAGENTA, al[i] * k), 96);
            ink.ring(centre, toCam, r, size * 0.02, a(WHITE, al[i] * 0.7F * k), 96);
        }
        // the pillar
        double hgt = size * 9 * k;
        ink.beam(centre.add(0, -hgt, 0), centre.add(0, hgt, 0), size * 0.045, a(WHITE, 0.55F * k));
        GShade.ribbon(new Vec3[]{centre.add(0, -hgt, 0), centre, centre.add(0, hgt, 0)}, new float[]{0.0F, (float) (size * 0.25), 0.0F},
                GShade.PUR_A, 0.45F * k, 1);
        lightning(ink, centre, size * 1.1, t, k, seed + 9, 4, size * 2.4);
    }

    /** The sphere in flight, the erased wake behind it, and the shock where it left. */
    static void flight(Ink ink, GCasts.Play c, float age, double speed, double range, double radius, Vec3 from, Vec3 dir, float t) {
        if (age < 0) {
            return;
        }
        double d = Math.min(range, age * speed);
        Vec3 pos = from.add(dir.scale(d));
        float gone = span(age, (float) (range / speed), (float) (range / speed) + 8);
        if (gone < 1) {
            purpleOrb(ink, pos, radius * (1 - 0.5 * gone), t, 1 - gone, c.msg.seed());
            ink.ring(pos, dir, radius * 1.5, radius * 0.08, a(MAGENTA, 0.6F * (1 - gone)), 64);
            ink.ring(pos.subtract(dir.scale(radius)), dir, radius * 1.2, radius * 0.05, a(WHITE, 0.5F * (1 - gone)), 64);
            // rings of distorted air peeling off behind it
            for (int i = 1; i <= 3; i++) {
                Vec3 q = pos.subtract(dir.scale(radius * (1.5 + i * 1.6)));
                ink.ring(q, dir, radius * (1.0 + 0.25 * i), radius * 0.03, a(PURPLE, 0.35F * (1 - gone) / i), 48);
            }
        }
        float wake = 1 - span(age, (float) (range / speed), (float) (range / speed) + 30);
        if (wake > 0.01F) {
            ink.ribbon(new Vec3[]{from, from.lerp(pos, 0.5), pos}, new double[]{radius * 0.3, radius * 0.75, radius * 0.9},
                    new int[]{a(PURPLE, 0.1F * wake), a(PURPLE, 0.3F * wake), a(VIOLET_CORE, 0.45F * wake)});
        }
        burst(ink, from, radius * 0.18, age, c.msg.seed(), VIOLET_CORE, PURPLE, DARK_VIOLET);
    }

    // ---- Hollow Nuke

    static void drawNuke(Ink ink, GCasts.Play c, float t, float partial) {
        Vec3 o = c.msg.origin(), d = c.msg.dir();
        long seed = c.msg.seed();
        // as he names it, Blue gathers in his right hand; then it is shot out and swung into an orbit round him
        float nb = window(t, GBeats.NK_V_BLUE, GBeats.NK_V_BLUE + 8, GBeats.NK_BLUE - 2, GBeats.NK_BLUE + 2);
        if (nb > 0.01F) {
            blueOrb(ink, hand(c, partial, true).add(0, 0.2, 0), 0.4 * nb, t, nb, seed);
        }
        if (t >= GBeats.NK_BLUE && t < GBeats.NK_MERGE + 1) {
            float bk = span(t, GBeats.NK_BLUE, GBeats.NK_BLUE + 4);
            Vec3 bp = GBeats.nukeBlue(o, d, t);
            blueOrb(ink, bp, 1.6 * bk, t, bk, seed);
            Vec3[] tail = new Vec3[8];
            float[] w = new float[8];
            for (int i = 0; i < 8; i++) {
                tail[i] = GBeats.nukeBlue(o, d, Math.max(GBeats.NK_BLUE, t - i * 0.8F));
                w[i] = 0.6F * (1 - i / 8F);
            }
            GShade.ribbon(tail, w, GShade.BLUE_A, 0.8F * bk, 1);
        }
        // as he names it, Red gathers in his left; then it is fired up into the sky, where it hangs, burning
        float nr = window(t, GBeats.NK_V_RED, GBeats.NK_V_RED + 8, GBeats.NK_RED - 2, GBeats.NK_RED + 2);
        if (nr > 0.01F) {
            redOrb(ink, hand(c, partial, false).add(0, 0.2, 0), 0.35 * nr, t, nr, seed);
        }
        if (t >= GBeats.NK_RED && t < GBeats.NK_MERGE + 1) {
            float rk = span(t, GBeats.NK_RED, GBeats.NK_RED + 3);
            Vec3 rp = GBeats.nukeRed(o, d, t);
            double size = 1.4 + 0.6 * smooth(span(t, GBeats.NK_RED_STOP, GBeats.NK_JOIN));
            redOrb(ink, rp, size * rk, t, rk, seed);
            if (t < GBeats.NK_RED_STOP + 4) {
                Vec3 from = GBeats.nukeRed(o, d, GBeats.NK_RED);
                GShade.ribbon(new Vec3[]{from, rp}, new float[]{0.1F, 0.7F}, GShade.RED_A, 0.7F * (1 - span(t, GBeats.NK_RED_STOP, GBeats.NK_RED_STOP + 4)), 1);
            }
        }
        if (t >= GBeats.NK_JOIN && t < GBeats.NK_MERGE) {
            Vec3 bp = GBeats.nukeBlue(o, d, t), rp = GBeats.nukeRed(o, d, t);
            lightning(ink, bp.lerp(rp, 0.5), 0.8, t, span(t, GBeats.NK_JOIN, GBeats.NK_MERGE), seed + 9, 6, bp.distanceTo(rp) * 0.6 + 2);
        }
        int fire = c.fireAt;
        Vec3 target = c.target != null ? c.target : c.msg.point();
        if (t >= GBeats.NK_MERGE - 1 && (fire < 0 || t < fire + GBeats.NK_DROP + 1)) {
            float pk = span(t, GBeats.NK_MERGE - 1, GBeats.NK_MERGE + 4);
            double size = 2.5 + 7.5 * smooth(span(t, GBeats.NK_MERGE, GBeats.NK_READY + 10));
            Vec3 pp = GBeats.nukePurple(o, d, target, t, fire);
            purpleOrb(ink, pp, size * pk, t, pk, seed);
            lightning(ink, pp, size, t, pk, seed + 11, 10, size * 3);
            if (fire >= 0) {
                Vec3 sky = GBeats.nukeRedPoint(o, d);
                GShade.ribbon(new Vec3[]{sky, pp}, new float[]{(float) (size * 0.3), (float) (size * 0.9)}, GShade.PUR_A, 0.6F, 1);
            } else if (c.mine && c.aiming(partial)) {
                // the aim: a line of violet from it down to where he is looking
                Vec3 where = lookedAt(c, partial);
                if (where != null) {
                    GShade.ribbon(new Vec3[]{pp, pp.lerp(where, 0.5), where}, new float[]{0.3F, 0.2F, 0.1F}, GShade.MAGENTA, 0.6F, 1);
                    ink.ring(where.add(0, 0.2, 0), UP, GBeats.NK_CRATER * (0.9 + 0.1 * Math.sin(t * 0.3)), 0.25, a(MAGENTA, 0.7F), 96);
                    ink.ring(where.add(0, 0.2, 0), UP, GBeats.NK_CRATER * 0.3, 0.15, a(VIOLET_CORE, 0.6F), 64);
                }
            }
        }
        if (fire >= 0 && t >= fire + GBeats.NK_DROP) {
            nukeBlast(ink, target, t - fire - GBeats.NK_DROP, t, seed);
        }
    }

    /** Where the local player's gaze meets the land (for aiming the nuke). */
    static Vec3 lookedAt(GCasts.Play c, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return null;
        }
        Vec3 eye = mc.player.getEyePosition(partial), look = mc.player.getViewVector(partial);
        net.minecraft.world.phys.BlockHitResult h = mc.level.clip(new net.minecraft.world.level.ClipContext(eye, eye.add(look.scale(GBeats.NK_RANGE)),
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, mc.player));
        return ground(h.getLocation());
    }

    /**
     * The detonation: a violet sun at the heart, a churning fireball of billows that climbs into a mushroom column,
     * shock rings racing out over the land, lightning, flung debris, and the dark cloud left behind.
     */
    static void nukeBlast(Ink ink, Vec3 g, float age, float t, long seed) {
        if (age > 160) {
            return;
        }
        double R = GBeats.NK_CRATER * 1.6;
        float k = Mth.clamp(age / 140F, 0, 1);
        float core = (float) Math.exp(-age / 6.0);
        // the heart: the imaginary mass, swelling and fading
        GShade.orb(g.add(0, R * 0.25, 0), R * (0.25 + 0.35 * Math.sqrt(Math.min(1, age / 10F))) * (1 - k * 0.8), 2, GShade.PUR_A, GShade.PUR_B, 1.0F * (1 - k), seed);
        ink.glow(g.add(0, R * 0.3, 0), R * (1.0 + 1.0 * (1 - core)), a(VIOLET_CORE, 0.45F * core));
        // shock rings over the land
        for (int i = 0; i < 3; i++) {
            float sk = Mth.clamp((age - i * 5) / 32F, 0, 1);
            if (sk > 0 && sk < 1) {
                ink.ring(g.add(0, 0.5 + i, 0), UP, R * (0.5 + 4.5 * Math.sqrt(sk)), 0.6 + R * 0.03, a(i == 0 ? WHITE : MAGENTA, 0.85F * (1 - sk)), 160);
            }
        }
        // a dome of pressure, a thin shell racing out
        float dk = Mth.clamp(age / 18F, 0, 1);
        if (dk < 1) {
            ink.sphere(true, g, R * (0.4 + 2.2 * Math.sqrt(dk)), a(PURPLE, 0.0F), a(VIOLET_CORE, 0.45F * (1 - dk)), 3.0);
        }
        // the fireball: billows rolling outward and climbing into a column, cooling to smoke
        Random rnd = new Random(seed);
        float grow = (float) (1 - Math.exp(-age / 8.0));
        for (int i = 0; i < 60; i++) {
            double th = rnd.nextDouble() * Math.PI * 2, ph = Math.acos(rnd.nextDouble() * 1.6 - 0.6);
            double speed = 0.6 + rnd.nextDouble() * 0.6;
            boolean climb = i % 3 == 0;
            double rad = R * (0.3 + 0.8 * grow * speed);
            double lift = climb ? R * (0.4 + 3.6 * Math.pow(k, 0.7) * speed) : R * 0.5 * grow;
            double roll = age * 0.02 * (rnd.nextBoolean() ? 1 : -1);
            Vec3 dir = new Vec3(Math.cos(th + roll) * Math.sin(ph), Math.cos(ph), Math.sin(th + roll) * Math.sin(ph));
            Vec3 p = g.add(dir.x * rad * (climb ? 0.3 : 1), Math.max(0, dir.y) * rad * 0.5 + lift, dir.z * rad * (climb ? 0.3 : 1));
            double size = R * (0.25 + 0.2 * rnd.nextDouble()) * (0.5 + grow) * (1 + 0.6 * k);
            float heat = (float) Math.exp(-age / (12.0 + 16 * rnd.nextDouble()));
            float life = 1 - Mth.clamp((age - 50 - rnd.nextFloat() * 60) / 40F, 0, 1);
            if (life <= 0.01F) {
                continue;
            }
            int hot = heat > 0.6F ? VIOLET_CORE : heat > 0.3F ? MAGENTA : PURPLE;
            ink.glow(p, size * 1.1, a(hot, (0.06F + 0.2F * heat) * life));
            float smoke = (1 - heat) * life * Mth.clamp((age - 8) / 14F, 0, 1);
            if (smoke > 0.02F) {
                ink.sphere(false, p.add(0, size * 0.4, 0), size * 1.1, a(DARK_VIOLET, 0.6F * smoke), a(NIGHT, 0.0F), 0.8);
            }
        }
        // the column of light standing on it, thinning away
        float col = 1 - span(age, 6, 70);
        if (col > 0.01F) {
            Vec3 top = g.add(0, R * 4 * Math.sqrt(Math.min(1, age / 20F)), 0);
            GShade.ribbon(new Vec3[]{g, top}, new float[]{(float) (R * 0.5), (float) (R * 0.2)}, GShade.PUR_B, 0.6F * col, 1);
            GShade.ribbon(new Vec3[]{g, top}, new float[]{(float) (R * 1.2), (float) (R * 0.6)}, GShade.MAGENTA, 0.3F * col, 1);
        }
        lightning(ink, g.add(0, R * 0.4, 0), R * 0.5, t, 1 - k, seed + 21, 14, R * 2.2);
        burst(ink, g, R * 0.3, age, seed, VIOLET_CORE, PURPLE, DARK_VIOLET);
    }
}
