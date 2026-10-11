package com.firestar.mih.common;

/** The timing sheet. Every number is a tick from the cast; server and client both read it. */
public final class Beats {
    private Beats() {}

    /** Ticks the camera takes to leave the player's eyes, and to come back. */
    public static final int BLEND_IN = 6;
    public static final int BLEND_OUT = 12;

    public static final int PUNCH_HIT = 3;

    public static final int BARRAGE_START = 4;
    public static final int BARRAGE_END = 46;

    public static final int FINISH_HIT = 10;

    public static final int KNIFE_THROW = 7;
    public static final double KNIFE_SPEED = 3.2;

    public static final int SLICE_VANISH = 18;
    public static final int SLICE_HIT = 25;
    public static final double SLICE_RANGE = 22.0;

    public static final int INF_VANISH = 13;
    public static final int INF_HIT = 17;
    public static final double INF_RANGE = 26.0;

    public static final int JUDGE_LUNGE = 16;
    public static final int JUDGE_ARRIVE = 19;
    public static final int[] JUDGE_HITS = {22, 26, 30, 34, 38, 42, 46};
    public static final int JUDGE_FINAL = 60;
    public static final double JUDGE_RANGE = 16.0;

    public static final int DA_BURST = 28;
    public static final int DA_DURATION = 240;

    public static final int TA_START = 50;

    public static final int RESET_WHITE = 36;
    public static final int RESET_LIFT = 44;
    public static final int RESET_GALAXY = 56;
    public static final int RESET_PLUNGE = 168;
    public static final int RESET_SINGULARITY = 204;
    public static final int RESET_NEW = 214;

    /** When each cinematic shows its name card, and for how long. */
    public static int cardStart(Move m) {
        return switch (m) {
            case SPEED_SLICE -> 3;
            case JUDGEMENT -> 2;
            case DOUBLE_ACCEL -> 6;
            case INFINITE_SPEED -> 2;
            case TIME_ACCEL -> 14;
            case UNIVERSE_RESET -> 8;
            default -> -1;
        };
    }

    public static int cardLength(Move m) {
        return switch (m) {
            case SPEED_SLICE -> 15;
            case JUDGEMENT -> 14;
            case DOUBLE_ACCEL -> 22;
            case INFINITE_SPEED -> 11;
            case TIME_ACCEL -> 36;
            case UNIVERSE_RESET -> 26;
            default -> 0;
        };
    }

    /** Time acceleration: how many day-ticks pass per game tick, {@code k} seconds 0..1 into the run. */
    public static double accelRate(double k) {
        k = Math.max(0.0, Math.min(1.0, k));
        return 1.0 + (MAX_RATE - 1.0) * Math.pow(k, 2.1);
    }

    public static final double MAX_RATE = 1300.0;

    /** Day-ticks gone by {@code ticks} into a run of {@code length} (the integral of the rate, summed per tick). */
    public static long accelOffset(int ticks, int length) {
        long sum = 0;
        for (int i = 0; i < ticks; i++) {
            sum += (long) accelRate(i / (double) length);
        }
        return sum;
    }
}
