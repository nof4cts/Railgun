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
    public static final int MB_LAUNCH = 26;
    public static final int MB_END = 98;
    public static final double MB_SPEED = 0.85;
    public static final double MB_RADIUS = 3.4;
    public static final double MB_PULL = 15;

    // Maximum Output: Red - a blast wave that ploughs a widening furrow
    public static final int MR_FIRE = 28;
    public static final int MR_TRAVEL = 14;
    public static final double MR_LENGTH = 64;
    public static final double MR_R0 = 3, MR_R1 = 6.5;

    // Hollow Purple (Hanami): he rises, Red blooms at his left and Blue at his right, they become one, it is driven down
    public static final int HP_RISEN = 20, HP_BLUE = 24, HP_RED = 30, HP_MERGE = 42, HP_FIRE = 54;
    public static final double HP_SPEED = 3.0, HP_RANGE = 90, HP_RADIUS = 3.0, HP_RISE = 6;

    // Imaginary Technique: Purple (Toji) and 200%: he rises, speaks the incantation, then Purple
    public static final int[] CHANT = {14, 36, 58, 80};
    public static final String[] CHANT_LINES = {"Nine Ropes.", "Polarized Light.", "Crow and Declaration.", "Between Front and Back."};
    public static final String[] CHANT_KANJI = {"九綱", "偏光", "烏と声明", "表裏の間"};
    public static final int IM_RED = 96, IM_BLUE = 102, IM_MERGE = 114, IM_FIRE = 134;
    public static final double IM_SPEED = 3.2, IM_RANGE = 120, IM_RADIUS = 6.0, IM_RISE = 8;
    public static final int P2_SHIFT = 8;
    public static final int P2_RED = 106, P2_BLUE = 112, P2_MERGE = 126, P2_FIRE = 150;
    public static final double P2_SPEED = 3.6, P2_RANGE = 170, P2_RADIUS = 9.0, P2_RISE = 10;

    // Hollow Nuke: he rises; shoots Blue, which circles him; fires Red up into the sky; Blue breaks orbit and crashes
    // into Red; the Purple they make is hurled down at the target and detonates
    public static final int NK_RISE = 30, NK_BLUE = 36, NK_ORBIT = 44, NK_RED = 66, NK_RED_STOP = 80, NK_JOIN = 96, NK_MERGE = 112,
            NK_LAUNCH = 138, NK_BLAST = 154;
    public static final double NK_RANGE = 150, NK_CRATER = 30, NK_REACH = 40, NK_FLY = 16;

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
        double a = (t - NK_ORBIT) * 0.28;
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

    /** The Purple: held in the sky, then hurled down onto the target. */
    public static Vec3 nukePurple(Vec3 origin, Vec3 dir, Vec3 target, float t) {
        Vec3 sky = nukeRedPoint(origin, dir);
        if (t < NK_LAUNCH) {
            return sky;
        }
        double k = Math.min(1, (t - NK_LAUNCH) / (double) (NK_BLAST - NK_LAUNCH));
        return sky.lerp(target, k * k);
    }

    // ---- where each Purple leaves from once he has risen, and the way it goes: at whatever he aimed at

    public static double purpleLift(GMove m) {
        return switch (m) {
            case PURPLE -> HP_RISE;
            case IMAGINARY -> IM_RISE;
            case PURPLE_200 -> P2_RISE;
            default -> 0;
        };
    }

    public static Vec3 purpleStart(GMove m, Vec3 origin, Vec3 dir) {
        return origin.add(0, purpleLift(m), 0).add(flat(dir).scale(m == GMove.PURPLE ? 2.4 : 4.0));
    }

    public static Vec3 purpleDir(GMove m, Vec3 origin, Vec3 dir, Vec3 aim) {
        Vec3 d = aim.subtract(purpleStart(m, origin, dir));
        return d.lengthSqr() < 4 ? dir : d.normalize();
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
