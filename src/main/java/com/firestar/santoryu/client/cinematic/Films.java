package com.firestar.santoryu.client.cinematic;

import com.firestar.santoryu.client.ClientCast;
import com.firestar.santoryu.common.Beats;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;

/**
 * The shot lists. Every position is built from the cast itself: the swordsman's held feet (local right/up/forward),
 * the target point and where the dash puts him, so the same film plays wherever and however the technique is cast.
 */
public final class Films {
    private Films() {}

    private static double l(float k, double a, double b) {
        return a + (b - a) * k;
    }

    private static CameraPose at(Vec3 pos, Vec3 look, double fov, double roll) {
        return new CameraPose(pos, look, (float) fov, (float) roll);
    }

    /** A point at (right, up, forward) from {@code base} in the swordsman's frame. */
    private static Vec3 rel(ClientCast c, Vec3 base, double r, double u, double f) {
        return base.add(c.right.scale(r)).add(0, u, 0).add(c.fwd.scale(f));
    }

    /** Orbit round {@code centre}: angle 0 is straight in front of him, 90 on his right. */
    private static Vec3 orbit(ClientCast c, Vec3 centre, double radius, double degrees, double up) {
        double a = Math.toRadians(degrees);
        return centre.add(c.fwd.scale(Math.cos(a) * radius)).add(c.right.scale(Math.sin(a) * radius)).add(0, up, 0);
    }

    private static Vec3 flatTo(ClientCast c, Vec3 p) {
        return new Vec3(p.x, c.feet.y, p.z);
    }

    public static Film of(ClientCast c) {
        List<Shot> s = new ArrayList<>();
        Vec3 F = c.feet;
        Vec3 E = c.local(0, 1.62, 0);
        Vec3 P = c.point;
        Vec3 X = c.exit;
        Vec3 M = flatTo(c, F.lerp(X, 0.5));
        switch (c.move) {
            case SHISHI_SONSON -> {
                // the hand on the hilt
                s.add(Shot.cut(0, 10, (k, t) -> at(rel(c, F, l(k, 0.75, 0.62), 1.15, l(k, 0.85, 0.7)), rel(c, F, 0.32, 1.0, 0.05), l(k, 30, 26), 0)));
                // low behind him: the stance, the target small and far, the push in
                s.add(Shot.cut(10, Beats.SHISHI_VANISH, (k, t) -> at(rel(c, F, -1.3, 0.45, l(k, -3.4, -2.7)), P, l(k, 50, 42), 0)));
                // he is gone: the target, and the streak that passed it
                s.add(Shot.cut(Beats.SHISHI_VANISH, Beats.SHISHI_VANISH + 6, (k, t) -> at(rel(c, P, 3.6, -0.2, -1.2), P, 62, -4)));
                // in front of him, his back to the target, the slow sheathe
                s.add(Shot.cut(Beats.SHISHI_VANISH + 6, Beats.SHISHI_CLICK, (k, t) -> at(rel(c, X, 0.7, 1.45, l(k, 3.2, 2.5)), rel(c, X, 0.1, 1.3, 0), l(k, 40, 34), 0)));
                // the click: wide, side on, the cut lands
                s.add(Shot.cut(Beats.SHISHI_CLICK, c.move.length, (k, t) -> at(rel(c, M, l(k, 8.0, 9.0), 2.2, 0), rel(c, M, 0, 1.2, 0), l(k, 68, 72), 0)));
            }
            case HYAKUHACHI -> {
                s.add(Shot.cut(0, 14, (k, t) -> at(rel(c, F, 0.2, 1.4, l(k, 2.2, 1.9)), E.subtract(0, 0.2, 0), 44, 0)));
                // the windup: orbit as the blades spin up
                s.add(Shot.cut(14, Beats.HYAKU_FIRE - 8, (k, t) -> at(orbit(c, F, 3.4, l(k, 30, 200), 1.4), F.add(0, 1.1, 0), 55, 0)));
                s.add(Shot.sweep(Beats.HYAKU_FIRE - 8, Beats.HYAKU_FIRE, 4, (k, t) -> at(rel(c, F, 0.9, 1.8, -2.4), E.add(c.dir.scale(10)), l(k, 50, 44), 0)));
                // ride behind the cannon
                s.add(Shot.cut(Beats.HYAKU_FIRE, Beats.HYAKU_FIRE + 16, (k, t) -> {
                    Vec3 p = c.eye.add(c.dir.scale(0.8 + Beats.HYAKU_SPEED * Math.max(0, t - Beats.HYAKU_FIRE)));
                    return at(p.subtract(c.dir.scale(5.5)).add(0, 1.2, 0).add(c.right.scale(1.0)), p, 78, -3);
                }));
                s.add(Shot.cut(Beats.HYAKU_FIRE + 16, c.move.length, (k, t) -> {
                    Vec3 end = c.eye.add(c.dir.scale(0.8 + Beats.HYAKU_SPEED * Beats.HYAKU_FLIGHT));
                    return at(rel(c, end, 9, 2.5, -4), end, 64, 0);
                }));
            }
            case KOKUJO -> {
                s.add(Shot.cut(0, 12, (k, t) -> at(rel(c, F, 0.9, 1.0, 1.0), rel(c, F, 0.3, 1.0, 0.2), 34, 0)));
                s.add(Shot.cut(12, Beats.KOKUJO_FORM, (k, t) -> at(rel(c, F, -0.4, 0.3, l(k, 2.6, 2.1)), E, l(k, 52, 46), 3)));
                // the dragon tornado rises: crane up with it
                s.add(Shot.cut(Beats.KOKUJO_FORM, 70, (k, t) -> at(rel(c, P, 16, l(k, 2, 11), -4), P.add(0, l(k, 3, 8), 0), 70, 0)));
                // in the eye of the storm, looking up, the world spinning
                s.add(Shot.cut(70, Beats.KOKUJO_BURST, (k, t) -> at(P.add(0, 1.2, 0), P.add(c.fwd.scale(0.6)).add(0, 20, 0), 88, t * 6.0)));
                s.add(Shot.cut(Beats.KOKUJO_BURST, c.move.length, (k, t) -> at(rel(c, P, 6, 3, -18), P.add(0, 4, 0), 72, 0)));
            }
            case SANZEN_SEKAI -> {
                s.add(Shot.cut(0, 20, (k, t) -> at(rel(c, F, 0, 0.6, l(k, 2.6, 2.2)), E, 50, 0)));
                // the third blade between his teeth
                s.add(Shot.cut(20, 40, (k, t) -> at(rel(c, F, 0.45, 1.5, l(k, 1.5, 1.25)), rel(c, F, 0.0, 1.52, 0), 32, 0)));
                // the windmills: half an orbit round him
                s.add(Shot.cut(40, 72, (k, t) -> at(orbit(c, F, 3.2, l(k, -60, 120), 1.2), F.add(0, 1.2, 0), 56, 0)));
                s.add(Shot.sweep(72, Beats.SANZEN_DASH, 6, (k, t) -> at(rel(c, F, 0.6, 0.35, -2.6), P, l(k, 40, 30), 0)));
                // tracking beside the charge
                s.add(Shot.cut(Beats.SANZEN_DASH, Beats.SANZEN_HIT, (k, t) -> {
                    Vec3 m = F.lerp(X, k);
                    return at(rel(c, m, 4.2, 1.2, -0.5), rel(c, m, 0, 1.1, 2.0), 76, -5);
                }));
                // from beyond the target: him past it, the worlds burst
                s.add(Shot.cut(Beats.SANZEN_HIT, c.move.length, (k, t) -> at(rel(c, P, -3.5, 1.6, l(k, -5.5, -6.5)), rel(c, M, 0, 1.2, 1.0), 66, 2)));
            }
            case ASURA -> {
                // the eyes
                s.add(Shot.cut(0, Beats.ASURA_AURA, (k, t) -> at(rel(c, F, 0.1, 1.6, l(k, 1.75, 1.45)), E, 34, 0)));
                // the aura rises: crane up from his feet
                s.add(Shot.cut(Beats.ASURA_AURA, Beats.ASURA_MANIFEST, (k, t) -> at(rel(c, F, 1.6, l(k, 0.2, 2.4), 3.4), F.add(0, l(k, 0.8, 1.4), 0), 55, 0)));
                // the demon appears: wide and frontal
                s.add(Shot.cut(Beats.ASURA_MANIFEST, 110, (k, t) -> at(rel(c, F, 0, l(k, 1.6, 2.2), l(k, 6.5, 5.5)), F.add(0, 1.6, 0), 62, 0)));
                // three faces in profile
                s.add(Shot.cut(110, 130, (k, t) -> at(rel(c, F, l(k, 4.2, 3.8), 1.7, 1.4), E, 42, -4)));
                s.add(Shot.sweep(130, Beats.ASURA_DASH, 6, (k, t) -> at(rel(c, F, 0.9, 2.0, -2.3), P, l(k, 45, 34), 0)));
                // the target, as nine blades pass it
                s.add(Shot.cut(Beats.ASURA_DASH, Beats.ASURA_HIT, (k, t) -> at(rel(c, P, 3.6, 0.0, -1.6), P, 70, -6)));
                // in front of him, the target behind his back, as it lands
                s.add(Shot.cut(Beats.ASURA_HIT, c.move.length, (k, t) -> at(rel(c, X, 1.4, l(k, 1.9, 2.3), l(k, 4.2, 5.0)), rel(c, X.lerp(flatTo(c, P), 0.5), 0, 1.4, 0), 64, 0)));
            }
            case RENGOKU -> {
                // Enma in his hand
                s.add(Shot.cut(0, Beats.RENGOKU_DRAIN, (k, t) -> at(rel(c, F, 0.85, 1.1, 0.9), rel(c, F, 0.4, 1.05, 0.3), 30, 0)));
                // the drain: low, pushing in
                s.add(Shot.cut(Beats.RENGOKU_DRAIN, Beats.RENGOKU_CONQUER, (k, t) -> at(rel(c, F, -1.0, 0.3, l(k, 2.4, 1.8)), E, l(k, 48, 40), 4)));
                // the conqueror's wave: high and wide
                s.add(Shot.cut(Beats.RENGOKU_CONQUER, Beats.RENGOKU_CONQUER + 14, (k, t) -> at(rel(c, F, 2, l(k, 8, 9), 11), F, 72, 0)));
                // black lightning on his face
                s.add(Shot.cut(Beats.RENGOKU_CONQUER + 14, 100, (k, t) -> at(rel(c, F, 0.15, 1.6, l(k, 1.8, 1.5)), E, 32, 0)));
                s.add(Shot.sweep(100, Beats.RENGOKU_DASH, 6, (k, t) -> at(rel(c, F, -0.8, 0.4, -2.8), P, l(k, 42, 32), -2)));
                s.add(Shot.cut(Beats.RENGOKU_DASH, Beats.RENGOKU_HIT, (k, t) -> {
                    Vec3 m = F.lerp(X, k);
                    return at(rel(c, m, -4.2, 1.0, -0.6), rel(c, m, 0, 1.1, 2.0), 76, 5);
                }));
                s.add(Shot.cut(Beats.RENGOKU_HIT, c.move.length, (k, t) -> at(rel(c, P, 4.5, l(k, 1.5, 2.5), -5.0), rel(c, M, 0, 1.4, 1.0), 66, 0)));
            }
            case DRAGON -> {
                // from behind and above, sinking: the gathering
                s.add(Shot.cut(0, Beats.DRAGON_CONQUER, (k, t) -> at(rel(c, F, 1.0, l(k, 3.2, 1.2), l(k, -4.2, -3.0)), E, 50, 0)));
                // the king's haki: top-down, everything dropping round him
                s.add(Shot.cut(Beats.DRAGON_CONQUER, 70, (k, t) -> at(rel(c, F, 1.0, l(k, 14, 16), 5), F, 76, 0)));
                // three swords, a full circle round him
                s.add(Shot.cut(70, 112, (k, t) -> at(orbit(c, F, 3.6, l(k, 0, 300), 1.3), F.add(0, 1.3, 0), 52, 0)));
                // Wado in his teeth
                s.add(Shot.cut(112, 158, (k, t) -> at(rel(c, F, 0.45, 1.5, l(k, 1.5, 1.25)), rel(c, F, 0.0, 1.52, 0), 32, 0)));
                // worm's-eye, up at the target
                s.add(Shot.cut(158, Beats.DRAGON_LEAP, (k, t) -> at(rel(c, F, 0.4, 0.25, 1.4), P.add(0, 3, 0), l(k, 80, 72), 0)));
                // ride the leap
                s.add(Shot.cut(Beats.DRAGON_LEAP, Beats.DRAGON_HIT, (k, t) -> {
                    Vec3 m = F.add(0, 1, 0).lerp(P, k).add(0, Math.sin(Math.PI * k) * 6.0, 0);
                    return at(m.subtract(c.fwd.scale(4.0)).add(c.right.scale(1.2)).add(0, 1.2, 0), P.add(0, 2, 0), 84, -4);
                }));
                // the serpent closes: wide, side on
                s.add(Shot.cut(Beats.DRAGON_HIT, Beats.DRAGON_HIT + 30, (k, t) -> at(rel(c, P, l(k, 13, 15), 4, -2), P.add(0, 1, 0), 70, 0)));
                // aftermath: low on him as he lands and sheathes
                s.add(Shot.cut(Beats.DRAGON_HIT + 30, c.move.length, (k, t) -> at(rel(c, X, 0.5, 0.5, l(k, 2.6, 2.2)), X.add(0, 1.3, 0), 40, 0)));
            }
            default -> s.add(Shot.cut(0, c.move.length, (k, t) -> at(rel(c, F, 1.5, 2.0, -3.5), P, 60, 0)));
        }
        return new Film(c, s);
    }
}
