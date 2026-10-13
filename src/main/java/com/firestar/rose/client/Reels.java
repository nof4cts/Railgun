package com.firestar.rose.client;

import static com.firestar.rose.client.cam.Reel.at;
import static com.firestar.rose.client.cam.Reel.l;
import static com.firestar.rose.client.cam.Reel.spline;

import com.firestar.rose.client.cam.Reel;
import com.firestar.rose.common.RBeats;
import net.minecraft.world.phys.Vec3;

/**
 * The shot lists of Requiem Rose's cutscenes. Every position is built from the cast (his feet, his facing, the target,
 * where the rift opens), so each film plays wherever it is used. Coverage is cut like an action anime: a close detail,
 * the wide, the reverse, a crash zoom on the blow.
 */
final class Reels {
    private Reels() {}

    private static Vec3 rel(RoseCast c, Vec3 base, double r, double u, double f) {
        return base.add(c.right.scale(r)).add(0, u, 0).add(c.fwd.scale(f));
    }

    /** Round {@code centre}: 0 degrees in front of him, 90 on his right. */
    private static Vec3 orbit(RoseCast c, Vec3 centre, double radius, double deg, double up) {
        double a = Math.toRadians(deg);
        return centre.add(c.fwd.scale(Math.cos(a) * radius)).add(c.right.scale(Math.sin(a) * radius)).add(0, up, 0);
    }

    static Reel of(RoseCast c) {
        Vec3 F = c.feet;
        Vec3 E = F.add(0, 1.62, 0);
        Vec3 C = F.add(0, 1.1, 0);
        return switch (c.move) {
            case ASCEND -> new Reel(c.move.length)
                    // his hand, the first black curling off the ground
                    .cut(0, 20, (k, t) -> at(rel(c, F, l(k, 1.3, 1.1), 0.7, l(k, 1.2, 1.0)), rel(c, F, 0.35, 0.8, 0.2), 40, l(k, -4, -2)))
                    // his eyes, very close, as the line is spoken
                    .cut(20, 44, (k, t) -> at(rel(c, F, 0.25, 1.7, l(k, 1.9, 1.6)), E.subtract(0, 0.05, 0), l(k, 38, 34), 0))
                    // low and wide: the black crescents wheeling round him
                    .cut(44, 66, (k, t) -> at(spline(k, rel(c, F, -4.5, 0.3, 3.0), rel(c, F, -1.5, 0.5, 4.8), rel(c, F, 2.5, 0.7, 4.2)), C, 66, l(k, 6, -4)))
                    // behind his shoulder, the cracks running out across the ground
                    .cut(66, 86, (k, t) -> at(rel(c, F, l(k, -0.9, -1.2), l(k, 2.1, 2.6), l(k, -1.6, -2.4)), rel(c, F, 0, 0.2, 5.0), 60, 0))
                    // the breath in: crash in on his face
                    .still(86, RBeats.ASCEND_IGNITE, (k, t) -> at(rel(c, F, 0.1, 1.62, l(k, 3.2, 1.45)), E, l(k, 52, 30), 0))
                    // ignition: thrown back, wide, the pillar going up
                    .cut(RBeats.ASCEND_IGNITE, 122, (k, t) -> at(rel(c, F, l(k, 3.0, 5.5), l(k, 0.6, 1.4), l(k, 4.0, 7.0)), F.add(0, l(k, 1.4, 2.0), 0),
                            l(k, 70, 64), l(k, -8, 0)))
                    // the hero shot: low, the aura and the scythe against the sky
                    .sweep(122, c.move.length, 10, (k, t) -> at(rel(c, F, l(k, 2.6, 3.2), 0.35, l(k, 5.2, 6.0)), F.add(0, 1.6, 0), 58, 0));
            case RAY -> new Reel(c.move.length)
                    .cut(0, 24, (k, t) -> at(rel(c, F, l(k, 3.4, 3.0), 1.3, l(k, 3.2, 2.8)), RoseChoreo.rayOrigin(c, 0), 52, 0))
                    .cut(24, 50, (k, t) -> at(rel(c, F, l(k, 2.6, 2.3), 2.1, l(k, 3.8, 3.5)), E.add(0, 0.2, 0), 42, 0))
                    // the orb from below, the sky behind it
                    .cut(50, 76, (k, t) -> at(spline(k, rel(c, F, 1.6, 0.3, 3.4), rel(c, F, 0.4, 0.2, 3.9), rel(c, F, -1.4, 0.3, 3.5)),
                            RoseChoreo.rayOrigin(c, 0), l(k, 62, 52), l(k, -6, 6)))
                    .still(76, RBeats.RAY_FIRE, (k, t) -> at(rel(c, F, l(k, -4.5, -2.6), l(k, 1.6, 1.9), l(k, -3.0, -1.6)), RoseChoreo.rayOrigin(c, 0),
                            l(k, 55, 40), 0))
                    // fire: over the shoulder, straight down the ray
                    .cut(RBeats.RAY_FIRE, 112, (k, t) -> at(rel(c, F, l(k, -1.3, -1.9), 2.1, l(k, -2.2, -3.4)), c.point, l(k, 70, 86), 0))
                    // the length of it across the land
                    .cut(112, c.move.length, (k, t) -> {
                        Vec3 mid = E.lerp(c.point, 0.45);
                        return at(rel(c, mid, l(k, 14, 17), l(k, 3, 5), -1), mid, 74, 0);
                    });
            case REQUIEM -> {
                Vec3 T = c.exit.add(0, 1.0, 0);
                yield new Reel(c.move.length)
                        .cut(0, RBeats.REQUIEM_CUTS[0], (k, t) -> at(rel(c, F, l(k, 2.8, 2.5), 1.9, l(k, 3.8, 3.5)), E, 42, 0))
                        // round the target as the cuts hang in the air
                        .cut(RBeats.REQUIEM_CUTS[0], RBeats.REQUIEM_GATHER, (k, t) -> at(orbit(c, T, l(k, 7.0, 6.0), l(k, 80, 250), l(k, 2.5, 1.5)), T, 62,
                                l(k, -5, 5)))
                        // his profile close, the hanging cuts flaring beyond him
                        .still(RBeats.REQUIEM_GATHER, RBeats.REQUIEM_BURST, (k, t) -> {
                            Vec3 me = c.casterFeet(0);
                            Vec3 toT = T.subtract(me).multiply(1, 0, 1);
                            Vec3 f = toT.lengthSqr() < 1e-4 ? c.fwd : toT.normalize();
                            Vec3 r = new Vec3(-f.z, 0, f.x);
                            return at(me.add(r.scale(l(k, -4.0, -3.6))).add(f.scale(-0.8)).add(0, 1.8, 0), me.add(f.scale(2.5)).add(0, 1.4, 0), 46, 0);
                        })
                        .cut(RBeats.REQUIEM_BURST, c.move.length, (k, t) -> at(rel(c, T, l(k, 7.0, 9.5), l(k, 1.5, 3.0), l(k, -3, -1)), T, 66, 0));
            }
            case RIFT -> {
                Vec3 RC = RoseChoreo.riftCentre(c);
                yield new Reel(c.move.length)
                        // the blade raised, from beneath
                        .cut(0, 16, (k, t) -> at(rel(c, F, 2.8, 0.4, l(k, 3.8, 3.5)), F.add(0, 3.2, 0), 72, 0))
                        .cut(16, RBeats.RIFT_CUT, (k, t) -> at(rel(c, F, l(k, -2.6, -2.3), 2.4, l(k, 3.8, 3.5)), E.add(0, 0.8, 0), 46, 0))
                        // the cut, from far behind him, whole
                        .cut(RBeats.RIFT_CUT, RBeats.RIFT_OPEN + 8, (k, t) -> at(rel(c, F, l(k, -2.5, -3.2), l(k, 2.0, 2.6), l(k, -6.5, -8.0)), RC, 68, 0))
                        // the tear itself, side on, the world pouring into it
                        .cut(RBeats.RIFT_OPEN + 8, RBeats.RIFT_CLOSE, (k, t) -> at(spline(k, rel(c, RC, 9, 1, -2), rel(c, RC, 7, 2, 3), rel(c, RC, 3, 1.5, 7)), RC,
                                68, l(k, 4, -4)))
                        .cut(RBeats.RIFT_CLOSE, c.move.length, (k, t) -> at(rel(c, F, l(k, 2.0, 3.0), 1.2, l(k, -2.0, -3.0)), RC, 60, 0));
            }
            case LAST_BLOOM -> {
                Vec3 SEAM = F.add(c.fwd.scale(RBeats.BLOOM_REACH * 0.45));
                yield new Reel(c.move.length)
                        // the rise: from the ground looking up as he leaves it
                        .cut(0, 30, (k, t) -> at(rel(c, F, 1.6, 0.2, 2.6), c.casterFeet(0).add(0, 1.3, 0), 64, 0))
                        // his face as the sky turns
                        .cut(30, 60, (k, t) -> at(c.casterFeet(0).add(0, 2.5, 0).add(c.fwd.scale(l(k, 4.2, 3.8))).add(c.right.scale(1.9)),
                                c.casterFeet(0).add(0, 1.7, 0), 42, 0))
                        // very far and low: the sky black and rose, a speck of light in it
                        .cut(60, 90, (k, t) -> at(rel(c, F, l(k, 10, 12), 1.0, l(k, 26, 22)), c.casterFeet(0).add(0, l(k, 3, 8), 0), 54, 0))
                        // the blade growing, the halo behind him: from below, wide
                        .cut(90, 118, (k, t) -> at(spline(k, rel(c, F, -6, 1, 9), rel(c, F, 0, 0.5, 12), rel(c, F, 6, 1, 9)),
                                c.casterFeet(0).add(0, 7, 0), 80, 0))
                        // stillness before the cut
                        .still(118, RBeats.BLOOM_CUT, (k, t) -> at(c.casterFeet(0).add(0, -1.0, 0).add(c.fwd.scale(l(k, 8.5, 7.5))).add(c.right.scale(-4.5)),
                                c.casterFeet(0).add(0, 3.0, 0), l(k, 52, 46), l(k, -3, 3)))
                        // the cut, from far off to the side: the arc the size of the sky
                        .cut(RBeats.BLOOM_CUT, RBeats.BLOOM_IMPACT + 12, (k, t) -> at(rel(c, F, l(k, 46, 52), l(k, 10, 14), RBeats.BLOOM_REACH * 0.4),
                                rel(c, F, 0, 6, RBeats.BLOOM_REACH * 0.4), 72, l(k, -4, 4)))
                        // the wave going out to the horizon, from behind him, high
                        .cut(RBeats.BLOOM_IMPACT + 12, 185, (k, t) -> at(rel(c, F, l(k, -3, -5), l(k, 16, 24), l(k, -10, -16)), SEAM, 76, 0))
                        .sweep(185, c.move.length, 12, (k, t) -> at(rel(c, F, l(k, 3.6, 3.0), 1.6, l(k, 5.0, 4.4)), c.casterFeet(0).add(0, 1.4, 0), 52, 0));
            }
            default -> new Reel(c.move.length).cut(0, c.move.length, (k, t) -> at(rel(c, F, 1.5, 2.0, -3.5), c.point, 60, 0));
        };
    }
}
