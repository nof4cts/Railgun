package com.gojo.rework.common;

import net.minecraft.world.phys.Vec3;

/** Every technique's timing (ticks from the cast) and size. The server and every client read the same numbers. */
public final class GBeats {
    private GBeats() {}

    // Reversal: Red - a point of red at the fingertip, rays bursting from it, then flicked at the target
    public static final int RED_FIRE = 14;
    public static final double RED_SPEED = 3.6;
    public static final double RED_RANGE = 44;
    public static final double RED_CRATER = 6.5;
    public static final double RED_REACH = 9;

    // Maximum Output: Blue - a huge Blue that crawls forward, dragging everything in and gouging a trench
    public static final int MB_LAUNCH = 36;
    public static final int MB_END = 160;
    public static final double MB_SPEED = 0.85;
    public static final double MB_RADIUS = 3.4;
    public static final double MB_PULL = 15;

    // Maximum Output: Red - a blast wave that ploughs a widening furrow
    public static final int MR_FIRE = 40;
    public static final int MR_TRAVEL = 14;
    public static final double MR_LENGTH = 64;
    public static final double MR_R0 = 3, MR_R1 = 6.5;

    // ---- the Purples, paced as in the anime: each colour is named before it appears, the two are pressed together,
    // "Hollow..." as they become one, then he aims (the player turns to where it should go) and fires: "Purple!"
    // Hollow Purple (Hanami)
    public static final int HP_RISEN = 20, HP_V_RED = 22, HP_RED = 58, HP_V_BLUE = 70, HP_BLUE = 110, HP_JOIN = 122, HP_V_HOLLOW = 130,
            HP_MERGE = 140, HP_READY = 150;
    public static final double HP_SPEED = 3.0, HP_RANGE = 90, HP_RADIUS = 3.0, HP_RISE = 6;

    // Imaginary Technique: Purple (Toji) and 200%: he rises, speaks the incantation, then the two colours
    public static final int[] CHANT = {14, 36, 58, 80};
    public static final String[] CHANT_LINES = {"Nine Ropes.", "Polarized Light.", "Crow and Declaration.", "Between Front and Back."};
    public static final String[] CHANT_KANJI = {"九綱", "偏光", "烏と声明", "表裏の間"};
    // Imaginary Technique: Purple (Toji): he ascends into a golden sky over the piano while he speaks (the player's
    // own recording, from IM_V0): "...Throughout Heaven and Earth, I alone am the honoured one" (IM_TENJO), the
    // technique laid open, then lapse (blue drop) and reversal (red drop) collide - the cutaways show it in water
    // drops - and the Purple is simply there (IM_MERGE); "Hollow..." at IM_READY, he aims, "Purple" as it goes
    public static final int IM_V0 = 20, IM_TENJO = 239, IM_TENJO_END = 290, IM_RISEN = 420;
    public static final int IM_V_RED = 609, IM_RED = 615, IM_V_BLUE = 596, IM_BLUE = 602, IM_JOIN = 647, IM_V_HOLLOW = 728, IM_MERGE = 690,
            IM_READY = 728;
    /** The rendered cutaways, as [start, end) ticks of the cast: golden sky, leaves with drops, the two drops. */
    public static final int[] IM_FILM_SKY = {132, 196}, IM_FILM_LEAVES = {290, 390}, IM_FILM_DROPS = {592, 690};
    public static final double IM_SPEED = 3.2, IM_RANGE = 120, IM_RADIUS = 6.0, IM_RISE = 16;
    public static final int P2_SHIFT = 8;
    public static final int P2_V_RED = 104, P2_RED = 140, P2_V_BLUE = 150, P2_BLUE = 186, P2_JOIN = 198, P2_V_HOLLOW = 206, P2_MERGE = 218,
            P2_READY = 230;
    public static final double P2_SPEED = 3.6, P2_RANGE = 170, P2_RADIUS = 9.0, P2_RISE = 10;

    /** How long he may hold a finished Purple while aiming before it goes by itself. */
    public static final int AIM_MAX = 100;

    // Hollow Nuke: he rises; "Maximum Output: Blue" and shoots Blue, which circles him; "Maximum Output: Red" and
    // fires Red up into the sky; Blue breaks orbit and crashes into Red; the Purple they make is aimed at the land and
    // hurled down, and detonates
    public static final int NK_RISE = 30, NK_V_BLUE = 30, NK_BLUE = 64, NK_ORBIT = 72, NK_V_RED = 80, NK_RED = 114, NK_RED_STOP = 128,
            NK_JOIN = 140, NK_V_HOLLOW = 150, NK_MERGE = 158, NK_READY = 172, NK_DROP = 16;
    public static final double NK_RANGE = 150, NK_CRATER = 30, NK_REACH = 40, NK_FLY = 16;

    /** Each Purple's moments: named Red, Red appears, named Blue, Blue appears, they join, "Hollow", merged, ready. */
    public static int[] purpleBeats(GMove m) {
        return switch (m) {
            case IMAGINARY -> new int[]{IM_V_RED, IM_RED, IM_V_BLUE, IM_BLUE, IM_JOIN, IM_V_HOLLOW, IM_MERGE, IM_READY};
            case PURPLE_200 -> new int[]{P2_V_RED, P2_RED, P2_V_BLUE, P2_BLUE, P2_JOIN, P2_V_HOLLOW, P2_MERGE, P2_READY};
            case NUKE -> new int[]{NK_V_RED, NK_RED, NK_V_BLUE, NK_BLUE, NK_JOIN, NK_V_HOLLOW, NK_MERGE, NK_READY};
            default -> new int[]{HP_V_RED, HP_RED, HP_V_BLUE, HP_BLUE, HP_JOIN, HP_V_HOLLOW, HP_MERGE, HP_READY};
        };
    }

    public static int ready(GMove m) {
        return purpleBeats(m)[7];
    }

    public static boolean aims(GMove m) {
        return m == GMove.PURPLE || m == GMove.IMAGINARY || m == GMove.PURPLE_200 || m == GMove.NUKE;
    }

    /** How long a Purple flies once fired (ticks), and how long its cast lasts after that. */
    public static int flight(GMove m) {
        return switch (m) {
            case IMAGINARY -> (int) Math.ceil(IM_RANGE / IM_SPEED);
            case PURPLE_200 -> (int) Math.ceil(P2_RANGE / P2_SPEED);
            case NUKE -> NK_DROP;
            default -> (int) Math.ceil(HP_RANGE / HP_SPEED);
        };
    }

    public static int after(GMove m) {
        return m == GMove.NUKE ? NK_DROP + 110 : flight(m) + 40;
    }

    static Vec3 flat(Vec3 dir) {
        Vec3 f = new Vec3(dir.x, 0, dir.z);
        return f.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : f.normalize();
    }

    static double ease(double x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * (3 - 2 * x);
    }

    /** His eye once risen for the nuke. */
    public static Vec3 nukeEye(Vec3 origin) {
        return origin.add(0, NK_FLY, 0);
    }

    /** Where Red stops, high in the sky in front of him. */
    public static Vec3 nukeRedPoint(Vec3 origin, Vec3 dir) {
        return nukeEye(origin).add(flat(dir).scale(9)).add(0, 9, 0);
    }

    static Vec3 orbit(Vec3 origin, Vec3 dir, float t) {
        Vec3 f = flat(dir), r = new Vec3(-f.z, 0, f.x);
        double a = (t - NK_ORBIT) * 0.22;
        return nukeEye(origin).add(f.scale(Math.cos(a) * 4.5)).add(r.scale(Math.sin(a) * 4.5)).add(0, -0.4 + Math.sin(a * 2) * 0.6, 0);
    }

    /** Blue: shot from his hand, swung into an orbit round him, then sent into Red. */
    public static Vec3 nukeBlue(Vec3 origin, Vec3 dir, float t) {
        Vec3 f = flat(dir), r = new Vec3(-f.z, 0, f.x);
        Vec3 hand = nukeEye(origin).add(r.scale(0.5)).add(f.scale(0.8)).add(0, -0.4, 0);
        if (t < NK_ORBIT) {
            return hand.lerp(orbit(origin, dir, NK_ORBIT), ease((t - NK_BLUE) / (double) (NK_ORBIT - NK_BLUE)));
        }
        if (t < NK_JOIN) {
            return orbit(origin, dir, t);
        }
        return orbit(origin, dir, NK_JOIN).lerp(nukeRedPoint(origin, dir), Math.pow(Math.min(1, (t - NK_JOIN) / (double) (NK_MERGE - NK_JOIN)), 1.6));
    }

    /** Red: fired up out of his other hand to its point in the sky. */
    public static Vec3 nukeRed(Vec3 origin, Vec3 dir, float t) {
        Vec3 f = flat(dir), r = new Vec3(-f.z, 0, f.x);
        Vec3 hand = nukeEye(origin).add(r.scale(-0.5)).add(f.scale(0.8)).add(0, -0.2, 0);
        double k = 1 - Math.pow(1 - Math.max(0, Math.min(1, (t - NK_RED) / (double) (NK_RED_STOP - NK_RED))), 3);
        return hand.lerp(nukeRedPoint(origin, dir), k);
    }

    /** The Purple: held in the sky; once fired (at {@code fire}) it falls on the target. */
    public static Vec3 nukePurple(Vec3 origin, Vec3 dir, Vec3 target, float t, int fire) {
        Vec3 sky = nukeRedPoint(origin, dir);
        if (fire < 0 || t < fire) {
            return sky;
        }
        double k = Math.min(1, (t - fire) / (double) NK_DROP);
        return sky.lerp(target, k * k);
    }

    // ---- where each Purple is held once he has risen, in front of him, wherever he looks

    public static double purpleLift(GMove m) {
        return switch (m) {
            case PURPLE -> HP_RISE;
            case IMAGINARY -> IM_RISE;
            case PURPLE_200 -> P2_RISE;
            default -> 0;
        };
    }

    public static double purpleAhead(GMove m) {
        return switch (m) {
            case IMAGINARY -> 4.0;
            case PURPLE_200 -> 5.5;
            default -> 2.4;
        };
    }

    // Unlimited Void
    public static final int DV_BANNER = 8, DV_FLASH1 = 48, DV_LINES = 50, DV_FLASH2 = 80, DV_HOLE = 82, DV_VOID = 118, DV_CUT = 152;
    public static final int DV_HOLD = 420;
    public static final double DV_RADIUS = 26;

    // 0.2-second domain
    public static final int D2_OPEN = 6, D2_CLOSE = 10, D2_STUN = 140;
    public static final double D2_RADIUS = 32;

    /** Where a flying sphere is at tick t (null before it leaves). */
    public static Vec3 along(Vec3 from, Vec3 dir, int fire, double speed, double range, float t) {
        if (t < fire) {
            return null;
        }
        double d = Math.min(range, (t - fire) * speed);
        return from.add(dir.scale(d));
    }

    public static int arrive(int fire, double speed, double dist) {
        return fire + (int) Math.ceil(dist / speed);
    }
}
