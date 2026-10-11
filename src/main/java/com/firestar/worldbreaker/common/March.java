package com.firestar.worldbreaker.common;

import net.minecraft.world.phys.Vec3;

/**
 * The march, as pure arithmetic both sides play from: where every giant is, how it walks, when each foot lands. Given
 * the same origin, heading, start tick and seed, client and server agree to the tick without any further syncing.
 *
 * <p>The giants form two staggered ranks across the heading, start {@link #START_DIST} blocks ahead of the caller and
 * walk toward (and over) them.
 */
public final class March {
    private March() {}

    /** Ticks of the caller's cinematic. */
    public static final int FILM = 300;
    public static final int BLEND_IN = 8, BLEND_OUT = 14;
    /** How far ahead the ranks rise. */
    public static final double START_DIST = 112;
    /** Base walking speed, blocks per tick. */
    public static final double SPEED = 0.45;
    /** Ticks for one foot to land after the other. */
    public static final int HALF = 15;
    /** Base height of a giant, blocks. */
    public static final double HEIGHT = 48;
    /** Ticks the giants rise out of the ground at the start. */
    public static final int RISE = 40;
    /** Ticks they take to sink back into dust at the end. */
    public static final int SINK = 80;

    /** One giant's place in the formation. */
    public record Giant(int index, double lateral, double back, double scale, int phase, double sway) {}

    public static Giant[] formation(int count, long seed) {
        java.util.Random r = new java.util.Random(seed);
        Giant[] out = new Giant[count];
        int front = (count + 1) / 2;
        for (int i = 0; i < count; i++) {
            boolean rear = i >= front;
            int k = rear ? i - front : i;
            int n = rear ? count - front : front;
            double spacing = 17.0;
            double lateral = (k - (n - 1) / 2.0) * spacing + (rear ? spacing / 2 : 0) + (r.nextDouble() - 0.5) * 3;
            // the centre of the front rank walks straight over the caller
            if (!rear && k == (n - 1) / 2) {
                lateral = 0;
            }
            double back = (rear ? 24 : 0) + r.nextDouble() * 4;
            double scale = 0.86 + r.nextDouble() * 0.28;
            if (!rear && Math.abs(lateral) < 1) {
                scale = 1.12;
            }
            int phase = r.nextInt(HALF);
            out[i] = new Giant(i, lateral, back, scale, phase, (r.nextDouble() - 0.5) * 0.08);
        }
        return out;
    }

    /** Distance walked after t ticks: a lurching gait, quick mid-stride and heavy on each landing. */
    public static double walked(double t, int phase) {
        if (t <= 0) {
            return 0;
        }
        double w = 2 * Math.PI / HALF;
        return SPEED * (t - Math.sin(w * (t + phase)) / w * 0.55 + Math.sin(w * phase) / w * 0.55);
    }

    /** Horizontal right vector for a heading. */
    public static Vec3 side(Vec3 dir) {
        return new Vec3(-dir.z, 0, dir.x);
    }

    /** Where a giant's body centre stands at time t (y = the origin's ground; terrain is applied separately). */
    public static Vec3 position(Vec3 origin, Vec3 dir, Giant g, double t) {
        Vec3 start = origin.add(dir.scale(START_DIST + g.back)).add(side(dir).scale(g.lateral));
        return start.subtract(dir.scale(walked(t, g.phase)));
    }

    /** Walking direction (toward the caller), with each giant's slight drift. */
    public static Vec3 heading(Vec3 dir, Giant g) {
        Vec3 w = dir.scale(-1);
        double c = Math.cos(g.sway), s = Math.sin(g.sway);
        return new Vec3(w.x * c - w.z * s, 0, w.x * s + w.z * c);
    }

    /** Gait phase 0..1 over a full stride (two steps). */
    public static double gait(double t, Giant g) {
        double p = (t + g.phase) / (2.0 * HALF);
        return p - Math.floor(p);
    }

    /** True if a foot lands on tick t; {@code left} tells which. */
    public static boolean footfall(int t, Giant g) {
        return t > RISE && (t + g.phase) % HALF == 0;
    }

    public static boolean leftFoot(int t, Giant g) {
        return ((t + g.phase) / HALF) % 2 == 0;
    }

    /** Where the landing foot is planted (relative to the body centre at that tick). */
    public static Vec3 foot(Vec3 centre, Vec3 heading, Giant g, boolean left) {
        double h = HEIGHT * g.scale;
        Vec3 side = side(heading);
        return centre.add(heading.scale(h * 0.1)).add(side.scale((left ? -1 : 1) * h * 0.11));
    }

    /** 0..1: how far out of the ground a giant has risen (start) or how far it has not yet sunk (end). */
    public static double presence(double t, int life) {
        double rise = Math.min(1, Math.max(0, t / RISE));
        double sink = Math.min(1, Math.max(0, (life - t) / SINK));
        return Math.min(rise, sink);
    }
}
