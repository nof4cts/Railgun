package com.firestar.mih.client.cinematic;

import com.firestar.mih.client.ClientCast;
import com.firestar.mih.client.render.Galaxy;
import com.firestar.mih.common.Beats;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;

/**
 * The shot lists. Every position is built from the cast itself (the user's feet at the cast, his facing, the target,
 * where he ends up), so the same film plays wherever and however the move is used.
 */
public final class Films {
    private Films() {}

    private static double l(float k, double a, double b) {
        return a + (b - a) * k;
    }

    private static CameraPose at(Vec3 pos, Vec3 look, double fov, double roll) {
        return new CameraPose(pos, look, (float) fov, (float) roll);
    }

    private static Vec3 rel(ClientCast c, Vec3 base, double r, double u, double f) {
        return base.add(c.right.scale(r)).add(0, u, 0).add(c.fwd.scale(f));
    }

    /** Orbit round {@code centre}: angle 0 is in front of him, 90 on his right. */
    private static Vec3 orbit(ClientCast c, Vec3 centre, double radius, double degrees, double up) {
        double a = Math.toRadians(degrees);
        return centre.add(c.fwd.scale(Math.cos(a) * radius)).add(c.right.scale(Math.sin(a) * radius)).add(0, up, 0);
    }

    /** From the target, looking back the way the user came. */
    private static Vec3 facing(ClientCast c, Vec3 target, double dist, double up) {
        Vec3 back = c.feet.subtract(target).multiply(1, 0, 1);
        back = back.lengthSqr() < 1e-4 ? c.fwd.scale(-1) : back.normalize();
        return target.add(back.scale(dist)).add(0, up, 0);
    }

    public static Film of(ClientCast c) {
        List<Shot> s = new ArrayList<>();
        Vec3 F = c.feet;
        Vec3 E = c.local(0, 1.62, 0);
        Vec3 P = c.point;
        Vec3 X = c.exit;
        Vec3 M = P.lerp(X.add(0, 1.0, 0), 0.5);
        switch (c.move) {
            case SPEED_SLICE -> {
                // his eyes, unreadable
                s.add(Shot.cut(0, 10, (k, t) -> at(rel(c, F, l(k, 0.25, 0.18), 1.64, l(k, 1.1, 0.85)), E, l(k, 30, 26), 0)));
                // over his shoulder: the Stand rears, the target small and far
                s.add(Shot.cut(10, Beats.SLICE_VANISH, (k, t) -> at(rel(c, F, l(k, -1.0, -0.8), 2.0, l(k, -2.8, -2.3)), P, l(k, 52, 40), 0)));
                // the target alone in still time, as a line of white goes past
                s.add(Shot.cut(Beats.SLICE_VANISH, Beats.SLICE_HIT, (k, t) -> at(facing(c, P, l(k, 2.8, 2.4), 0.25), P.add(0, 0.25, 0), 44, -6)));
                // side on: the cut lands, him already behind
                s.add(Shot.cut(Beats.SLICE_HIT, 38, (k, t) -> at(rel(c, M, l(k, 5.0, 5.6), 1.2, 0), M, l(k, 52, 58), 0)));
                // low in front of him, the knife flicked clean
                s.add(Shot.cut(38, c.move.length, (k, t) -> at(rel(c, X, -1.0, l(k, 0.7, 1.0), l(k, -2.4, -2.9)), X.add(0, 1.4, 0), 46, 0)));
            }
            case INFINITE_SPEED -> {
                s.add(Shot.cut(0, Beats.INF_VANISH, (k, t) -> at(rel(c, F, 0.7, 0.55, l(k, 3.3, 2.8)), E.add(0, 0.6, 0), l(k, 58, 50), 0)));
                s.add(Shot.cut(Beats.INF_VANISH, Beats.INF_HIT, (k, t) -> at(facing(c, P, 2.6, 0.3), P.add(0, 0.3, 0), 46, 5)));
                s.add(Shot.cut(Beats.INF_HIT, 32, (k, t) -> at(rel(c, M, l(k, -5.0, -5.8), l(k, 1.0, 1.6), 0.4), M.subtract(0, 0.3, 0), l(k, 54, 60), 0)));
                s.add(Shot.cut(32, c.move.length, (k, t) -> at(rel(c, X, 1.2, 1.8, l(k, 2.6, 3.2)), P, 50, 0)));
            }
            case JUDGEMENT -> {
                // low and wide: the Stand rears against the sky
                s.add(Shot.cut(0, Beats.JUDGE_LUNGE, (k, t) -> at(rel(c, F, l(k, 2.8, 2.4), 0.35, l(k, -3.0, -2.6)), F.add(0, 2.4, 0).add(c.fwd.scale(-0.4)), 64, 0)));
                // riding beside the charge
                s.add(Shot.cut(Beats.JUDGE_LUNGE, Beats.JUDGE_ARRIVE + 2, (k, t) -> {
                    Vec3 m = F.lerp(X, Math.min(1.0, k * 1.3));
                    return at(rel(c, m, 3.6, 1.2, -0.8), rel(c, m, 0, 1.1, 2.0), 80, -6);
                }));
                // round and round the target as the blows land from everywhere
                s.add(Shot.cut(Beats.JUDGE_ARRIVE + 2, Beats.JUDGE_FINAL - 5, (k, t) -> at(orbit(c, P.subtract(0, 0.4, 0), 4.2, l(k, 40, 330), l(k, 1.0, 2.2)), P, 62, 0)));
                // the wind-up, close behind his shoulder
                s.add(Shot.sweep(Beats.JUDGE_FINAL - 5, Beats.JUDGE_FINAL, 3, (k, t) -> at(rel(c, X, -0.8, 1.9, l(k, -1.8, -1.4)), P, l(k, 46, 38), 0)));
                // the blow: wide, side on, the target sent flying
                s.add(Shot.cut(Beats.JUDGE_FINAL, 80, (k, t) -> at(rel(c, P, l(k, 7.0, 9.0), l(k, 1.5, 3.0), l(k, -1.0, 1.5)), c.targetCentre(0), l(k, 62, 70), 0)));
                s.add(Shot.cut(80, c.move.length, (k, t) -> at(rel(c, X, 0.8, 1.6, l(k, 2.4, 2.9)), X.add(0, 1.5, 0), 44, 0)));
            }
            case DOUBLE_ACCEL -> {
                // all the way round him as the clocks wind up
                s.add(Shot.cut(0, Beats.DA_BURST - 4, (k, t) -> at(orbit(c, F, l(k, 4.2, 3.2), l(k, -30, 300), l(k, 0.6, 1.6)), F.add(0, 1.4, 0), 58, 0)));
                // the speedometer face, its needle pinned
                s.add(Shot.cut(Beats.DA_BURST - 4, Beats.DA_BURST + 6, (k, t) -> at(rel(c, F, 0.75, 2.45, l(k, 0.4, 0.15)), rel(c, F, 0.75, 2.35, -0.95), l(k, 34, 26), 0)));
                s.add(Shot.cut(Beats.DA_BURST + 6, c.move.length, (k, t) -> at(rel(c, F, 0.3, 1.2, l(k, 3.6, 4.4)), E, 56, 0)));
            }
            case TIME_ACCEL -> {
                // up from the ground at him and the sky beyond
                s.add(Shot.cut(0, 16, (k, t) -> at(rel(c, F, 0.5, 0.25, l(k, 1.6, 1.4)), F.add(0, 8, 0), l(k, 70, 64), 0)));
                // the Stand rears, wide and frontal
                s.add(Shot.cut(16, 36, (k, t) -> at(rel(c, F, 0, l(k, 1.2, 1.8), l(k, 6.0, 5.2)), F.add(0, 2.0, 0), 60, 0)));
                // crane up and back as the sky starts to run
                s.add(Shot.cut(36, Beats.TA_START, (k, t) -> at(rel(c, F, 0, l(k, 2.0, 9.0), l(k, 7.0, 14.0)), F.add(0, 1.6, 0), 62, 0)));
                // the sky: the sun beginning to streak
                s.add(Shot.cut(Beats.TA_START, 74, (k, t) -> at(rel(c, F, 0, 2.5, -4.0), F.add(l(k, 6, 14), 16, 0), l(k, 72, 82), 0)));
                s.add(Shot.cut(74, c.move.length, (k, t) -> at(rel(c, F, 0.6, 1.5, l(k, 3.0, 3.6)), E, 50, 0)));
            }
            case UNIVERSE_RESET -> {
                Vec3 G = c.point.add(0, Galaxy.LIFT, 0);
                Vec3 Fr = c.point;
                // the end of the world: under him, looking up into the burning sky
                s.add(Shot.cut(0, Beats.RESET_WHITE, (k, t) -> at(rel(c, Fr, 1.0, 0.4, l(k, 2.4, 2.0)), Fr.add(0, l(k, 3.0, 9.0), 0), l(k, 64, 80), 0)));
                // his face as it cracks
                s.add(Shot.cut(Beats.RESET_WHITE, Beats.RESET_LIFT, (k, t) -> at(rel(c, Fr, 0.1, 1.62, 1.05), Fr.add(0, 1.62, 0), 30, 0)));
                s.add(Shot.cut(Beats.RESET_LIFT, Beats.RESET_GALAXY, (k, t) -> at(rel(c, Fr, 0, 3.0, 10.0), Fr.add(0, 2.0, 0), 60, 0)));
                // the world torn upward into space
                s.add(Shot.cut(Beats.RESET_GALAXY, 92, (k, t) -> at(rel(c, Fr, l(k, 0, 6), l(k, 7, 20), l(k, 18, 34)), Fr.add(0, l(k, 4, 20), 0), l(k, 66, 74), 0)));
                // round the galaxy as everything wheels into it
                s.add(Shot.sweep(92, Beats.RESET_PLUNGE, 10, (k, t) -> at(orbit(c, G, l(k, 50, 62), l(k, 20, 170), l(k, 22, 30)), G, l(k, 74, 70), 0)));
                // falling into the core
                s.add(Shot.sweep(Beats.RESET_PLUNGE, Beats.RESET_SINGULARITY + 4, 8, (k, t) -> {
                    float e = k * k * k;
                    return at(orbit(c, G, l(e, 62, 3), l(k, 170, 260), l(e, 30, 1.0)), G, l(k, 70, 105), l(k, 0, 25));
                }));
                s.add(Shot.cut(Beats.RESET_SINGULARITY + 4, Beats.RESET_NEW, (k, t) -> at(G.add(0, 1, 2), G, 90, 0)));
                // the new universe: dawn, and him
                s.add(Shot.cut(Beats.RESET_NEW, 252, (k, t) -> at(rel(c, Fr, 0.4, l(k, 0.9, 1.2), l(k, 3.0, 2.5)), Fr.add(0, 1.55, 0), l(k, 48, 40), 0)));
                s.add(Shot.cut(252, c.move.length, (k, t) -> at(rel(c, Fr, l(k, -1.0, -3.0), l(k, 2.0, 7.0), l(k, 4.0, 11.0)), Fr.add(0, l(k, 1.6, 4.0), 0), 62, 0)));
            }
            default -> s.add(Shot.cut(0, c.move.length, (k, t) -> at(rel(c, F, 1.5, 2.0, -3.5), P, 60, 0)));
        }
        return new Film(c, s);
    }
}
