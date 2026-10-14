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

    // Hollow Purple (Hanami): Blue in the left hand, Red in the right, brought together and flicked
    public static final int HP_BLUE = 2, HP_RED = 8, HP_MERGE = 20, HP_FIRE = 30;
    public static final double HP_SPEED = 3.0, HP_RANGE = 84, HP_RADIUS = 3.0;

    // Imaginary Technique: Purple (Toji) and 200%: he rises, speaks the incantation, then Purple
    public static final int[] CHANT = {14, 36, 58, 80};
    public static final String[] CHANT_LINES = {"Nine Ropes.", "Polarized Light.", "Crow and Declaration.", "Between Front and Back."};
    public static final String[] CHANT_KANJI = {"九綱", "偏光", "烏と声明", "表裏の間"};
    public static final int IM_RED = 96, IM_BLUE = 102, IM_MERGE = 114, IM_FIRE = 134;
    public static final double IM_SPEED = 3.2, IM_RANGE = 120, IM_RADIUS = 6.0, IM_RISE = 2.5;
    public static final int P2_SHIFT = 8;
    public static final int P2_RED = 106, P2_BLUE = 112, P2_MERGE = 126, P2_FIRE = 150;
    public static final double P2_SPEED = 3.6, P2_RANGE = 170, P2_RADIUS = 9.0, P2_RISE = 5;

    // Hollow Nuke: Maximum Blue and Maximum Red in the sky, crashing together over the target
    public static final int NK_RISE = 30, NK_BLUE = 34, NK_RED = 44, NK_CONVERGE = 82, NK_COLLIDE = 122, NK_BLAST = 134;
    public static final double NK_RANGE = 150, NK_HEIGHT = 16, NK_CRATER = 26, NK_REACH = 36, NK_FLY = 14;

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
