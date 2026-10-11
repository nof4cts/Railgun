package com.firestar.silver.client.cinematic;

import com.firestar.silver.client.ClientCast;
import com.firestar.silver.common.Beats;
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
        switch (c.move) {
            case WAVE -> {
                // the gathering: hands at his side, the light between them
                s.add(Shot.cut(0, 16, (k, t) -> at(rel(c, F, l(k, 2.2, 1.9), 1.1, l(k, 2.0, 1.7)), rel(c, F, 0.3, 1.0, 0.0), l(k, 48, 44), 0)));
                s.add(Shot.cut(16, Beats.WAVE_FIRE, (k, t) -> at(rel(c, F, l(k, -2.0, -1.4), l(k, 0.6, 0.9), l(k, 3.6, 3.0)), E.subtract(0, 0.3, 0), l(k, 54, 46), 0)));
                // the release: behind his shoulder, down the length of the beam
                s.add(Shot.cut(Beats.WAVE_FIRE, 50, (k, t) -> at(rel(c, F, l(k, -1.4, -1.8), 2.0, l(k, -2.6, -3.4)), P, l(k, 62, 74), 0)));
                // side on, the whole beam across the frame
                s.add(Shot.cut(50, c.move.length, (k, t) -> {
                    Vec3 mid = E.lerp(P, 0.45);
                    return at(rel(c, mid, l(k, 12.0, 14.0), 2.5, 0), mid, 72, 0);
                }));
            }
            case FLURRY -> {
                s.add(Shot.cut(0, Beats.FLURRY_DASH, (k, t) -> at(rel(c, F, 0.6, 1.5, l(k, 1.9, 1.6)), E, 38, 0)));
                // round them as the fists blur
                s.add(Shot.cut(Beats.FLURRY_DASH, Beats.FLURRY_FINAL, (k, t) -> at(orbit(c, P.subtract(0, 0.6, 0), l(k, 4.0, 3.4), l(k, 60, 200), l(k, 1.0, 1.6)), P, 60, 0)));
                s.add(Shot.cut(Beats.FLURRY_FINAL, c.move.length, (k, t) -> at(rel(c, X, l(k, 4.5, 6.0), 1.4, 1.0), c.targetCentre(0), 66, 0)));
            }
            case METEOR -> {
                s.add(Shot.cut(0, Beats.METEOR_LAUNCH, (k, t) -> at(rel(c, F, 1.5, 0.4, -2.0), P, 60, 0)));
                // worm's-eye as it goes up
                s.add(Shot.cut(Beats.METEOR_LAUNCH, Beats.METEOR_SPIKE, (k, t) -> {
                    Vec3 tc = c.targetCentre(0);
                    return at(new Vec3(tc.x, c.feet.y + 0.4, tc.z).add(c.right.scale(3.0)).add(c.fwd.scale(-2.0)), tc, 72, 0);
                }));
                // from above as he drives it down
                s.add(Shot.cut(Beats.METEOR_SPIKE, Beats.METEOR_SLAM, (k, t) -> {
                    Vec3 tc = c.targetCentre(0);
                    return at(tc.add(c.right.scale(2.5)).add(0, 4.5, 0), tc.subtract(0, 1.5, 0), 70, 0);
                }));
                s.add(Shot.cut(Beats.METEOR_SLAM, c.move.length, (k, t) -> at(rel(c, P, l(k, 7.0, 9.0), l(k, 1.5, 3.0), -2.0), c.targetCentre(0), 66, 0)));
            }
            case RUSH -> {
                Vec3 T = c.exit;
                s.add(Shot.cut(0, Beats.RUSH_DASHES[0], (k, t) -> at(rel(c, F, 0.2, 1.6, l(k, 1.8, 1.5)), E, 34, 0)));
                s.add(Shot.cut(Beats.RUSH_DASHES[0], Beats.RUSH_FINAL - 4, (k, t) -> at(orbit(c, T, l(k, 7.5, 6.5), l(k, 90, 220), l(k, 3.5, 2.5)), T.add(0, 1.0, 0), 64, 0)));
                s.add(Shot.cut(Beats.RUSH_FINAL - 4, c.move.length, (k, t) -> at(rel(c, T, l(k, 6.0, 8.0), 1.6, l(k, -1.0, 1.0)), c.targetCentre(0), 64, 0)));
            }
            case SPHERE -> {
                Vec3 S = F.add(0, Beats.SPHERE_HEIGHT, 0);
                // arms raised to the sky: low and wide, the sphere growing above
                s.add(Shot.cut(0, 30, (k, t) -> at(rel(c, F, l(k, 2.0, 2.4), 0.3, l(k, 4.0, 5.0)), F.add(0, l(k, 2.0, 6.0), 0), l(k, 62, 70), 0)));
                // high and far, energy pouring in from everywhere
                s.add(Shot.cut(30, 64, (k, t) -> at(rel(c, F, l(k, 10, 14), l(k, 6, 12), l(k, 14, 18)), S.subtract(0, 3, 0), 70, 0)));
                // under the sphere looking up past him
                s.add(Shot.cut(64, Beats.SPHERE_THROW, (k, t) -> at(rel(c, F, 0.8, 0.5, l(k, 1.6, 1.2)), S, l(k, 74, 84), 0)));
                // it falls on them
                s.add(Shot.cut(Beats.SPHERE_THROW, Beats.SPHERE_BOOM + 6, (k, t) -> at(facing(c, P, 9.0, 3.0).add(c.right.scale(6.0)), P.add(0, l(k, 5, 1), 0), 66, 0)));
                s.add(Shot.cut(Beats.SPHERE_BOOM + 6, c.move.length, (k, t) -> at(rel(c, F, l(k, -3.0, -4.0), 2.5, l(k, -4.0, -5.0)), P, 60, 0)));
            }
            case FINAL -> {
                // still eyes in a rising storm
                s.add(Shot.cut(0, 26, (k, t) -> at(rel(c, F, 0.25, 1.6, l(k, 2.2, 1.8)), E.subtract(0, 0.2, 0), l(k, 42, 38), 0)));
                s.add(Shot.cut(26, 50, (k, t) -> at(orbit(c, F, l(k, 4.6, 3.8), l(k, -40, 120), l(k, 0.6, 1.4)), F.add(0, 1.3, 0), 58, 0)));
                // the charge, hands drawn back: crane up and away
                s.add(Shot.cut(50, Beats.FINAL_FIRE, (k, t) -> at(rel(c, F, l(k, 3.0, 5.0), l(k, 1.0, 5.0), l(k, 3.0, 6.0)), F.add(0, 1.4, 0), 64, 0)));
                // down the beam
                s.add(Shot.cut(Beats.FINAL_FIRE, 92, (k, t) -> at(rel(c, F, l(k, -1.6, -2.4), 2.2, l(k, -2.6, -3.8)), P, l(k, 70, 84), 0)));
                // the world it crosses
                s.add(Shot.cut(92, Beats.FINAL_END + 2, (k, t) -> {
                    Vec3 mid = E.lerp(P, 0.6);
                    return at(rel(c, mid, l(k, 16.0, 20.0), l(k, 3, 6), -2), mid, 76, 0);
                }));
                s.add(Shot.cut(Beats.FINAL_END + 2, c.move.length, (k, t) -> at(rel(c, F, 0.6, 1.4, l(k, 2.6, 3.2)), E, 46, 0)));
            }
            case AWAKEN_OMEN -> {
                // a fist closing, his eyes, his breath
                s.add(Shot.cut(0, 22, (k, t) -> at(rel(c, F, l(k, 1.9, 1.6), 1.0, l(k, 1.7, 1.4)), rel(c, F, 0.3, 0.9, 0.1), 44, 0)));
                s.add(Shot.cut(22, 46, (k, t) -> at(rel(c, F, 0.2, 1.6, l(k, 2.1, 1.7)), E.subtract(0, 0.2, 0), l(k, 42, 38), 0)));
                s.add(Shot.cut(46, Beats.OMEN_SURGE, (k, t) -> at(orbit(c, F, l(k, 3.2, 2.6), l(k, 20, 160), 1.2), F.add(0, 1.2, 0), 52, 0)));
                // the surge: low hero angle
                s.add(Shot.cut(Beats.OMEN_SURGE, c.move.length, (k, t) -> at(rel(c, F, l(k, 1.2, 1.6), l(k, 0.3, 0.6), l(k, 3.4, 4.4)), F.add(0, 1.7, 0), l(k, 58, 52), 0)));
            }
            case AWAKEN_ZENITH -> {
                s.add(Shot.cut(0, 24, (k, t) -> at(rel(c, F, 0.15, 1.6, l(k, 2.1, 1.7)), E.subtract(0, 0.2, 0), l(k, 40, 36), 0)));
                s.add(Shot.cut(24, 56, (k, t) -> at(rel(c, F, l(k, -1.6, -1.2), 1.2, l(k, 3.0, 2.4)), E, 46, 0)));
                // the light gathering: crane up as it rises from the ground
                s.add(Shot.cut(56, 90, (k, t) -> at(rel(c, F, 2.0, l(k, 0.4, 5.0), l(k, 5.0, 7.5)), F.add(0, l(k, 0.8, 1.6), 0), 62, 0)));
                s.add(Shot.cut(90, Beats.ZENITH_SURGE, (k, t) -> at(rel(c, F, 0.1, 1.6, l(k, 1.8, 1.5)), E.subtract(0, 0.15, 0), 36, 0)));
                // the summit: wide, then the calm close-up
                s.add(Shot.cut(Beats.ZENITH_SURGE, 124, (k, t) -> at(rel(c, F, l(k, 4.6, 3.6), l(k, 2.2, 1.3), l(k, 3.6, 3.0)), F.add(0, 1.2, 0), 58, 0)));
                s.add(Shot.cut(124, c.move.length, (k, t) -> at(rel(c, F, 0.35, 1.6, l(k, 1.5, 1.2)), E, 34, 0)));
            }
            default -> s.add(Shot.cut(0, c.move.length, (k, t) -> at(rel(c, F, 1.5, 2.0, -3.5), P, 60, 0)));
        }
        return new Film(c, s);
    }
}
