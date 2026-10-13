package com.firestar.rose.client.cam;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * A cutscene's shot list. Each shot runs from start to end (ticks on the technique's clock) along a path; k goes 0..1
 * across the shot, eased like an operator's hands. A shot either cuts in hard or sweeps in from the last one. Every
 * shot breathes with a little handheld drift unless told to lock off.
 */
public final class Reel {
    @FunctionalInterface
    public interface Path {
        Lens.Pose at(float k, float t);
    }

    record Shot(int start, int end, int sweep, float handheld, Path path) {}

    final List<Shot> shots = new ArrayList<>();
    public final int end;

    public Reel(int end) {
        this.end = end;
    }

    /** A hard cut to this shot. */
    public Reel cut(int start, int stop, Path p) {
        shots.add(new Shot(start, stop, 0, 1.0F, p));
        return this;
    }

    /** This shot, swept into from the last one over {@code sweep} ticks. */
    public Reel sweep(int start, int stop, int sweep, Path p) {
        shots.add(new Shot(start, stop, sweep, 1.0F, p));
        return this;
    }

    /** A locked-off shot: no handheld drift. */
    public Reel still(int start, int stop, Path p) {
        shots.add(new Shot(start, stop, 0, 0.0F, p));
        return this;
    }

    Lens.Pose pose(float t) {
        int idx = 0;
        for (int i = 0; i < shots.size(); i++) {
            if (t >= shots.get(i).start) {
                idx = i;
            }
        }
        Shot s = shots.get(idx);
        Lens.Pose p = eval(s, t);
        if (idx > 0 && s.sweep > 0 && t - s.start < s.sweep) {
            Shot prev = shots.get(idx - 1);
            Lens.Pose from = eval(prev, Math.min(t, prev.end));
            p = from.lerp(p, Lens.smooth((t - s.start) / s.sweep));
        }
        return p;
    }

    private static Lens.Pose eval(Shot s, float t) {
        float k = Mth.clamp((t - s.start) / Math.max(1.0F, s.end - s.start), 0, 1);
        Lens.Pose p = s.path.at(ease(k), t);
        if (s.handheld > 0) {
            double a = t * 0.11, b = t * 0.07;
            Vec3 drift = new Vec3(Math.sin(a) * 0.05 + Math.sin(a * 2.3) * 0.02, Math.sin(b * 1.7) * 0.035, Math.cos(a * 1.3) * 0.04).scale(s.handheld);
            p = new Lens.Pose(p.pos().add(drift), p.look().add(drift.scale(0.4)), p.fov(), p.roll() + (float) Math.sin(t * 0.05) * 0.4F * s.handheld);
        }
        return p;
    }

    static float ease(float x) {
        return x < 0.5F ? 4 * x * x * x : 1 - (float) Math.pow(-2 * x + 2, 3) / 2;
    }

    // ------------------------------------------------------------------------------------------------ helpers

    public static Lens.Pose at(Vec3 pos, Vec3 look, double fov, double roll) {
        return new Lens.Pose(pos, look, (float) fov, (float) roll);
    }

    public static double l(float k, double a, double b) {
        return a + (b - a) * k;
    }

    /** A Catmull-Rom curve through the points, k 0..1 over the whole curve. */
    public static Vec3 spline(float k, Vec3... pts) {
        if (pts.length == 1) {
            return pts[0];
        }
        float f = Mth.clamp(k, 0, 1) * (pts.length - 1);
        int i = Math.min(pts.length - 2, (int) Math.floor(f));
        float u = f - i;
        Vec3 p0 = pts[Math.max(0, i - 1)], p1 = pts[i], p2 = pts[i + 1], p3 = pts[Math.min(pts.length - 1, i + 2)];
        double u2 = u * u, u3 = u2 * u;
        return p0.scale(-0.5 * u3 + u2 - 0.5 * u).add(p1.scale(1.5 * u3 - 2.5 * u2 + 1)).add(p2.scale(-1.5 * u3 + 2 * u2 + 0.5 * u))
                .add(p3.scale(0.5 * u3 - 0.5 * u2));
    }
}
