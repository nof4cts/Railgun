package com.firestar.mih.client.stand;

import com.firestar.mih.common.Beats;
import com.firestar.mih.common.Move;
import net.minecraft.util.Mth;

/**
 * Made in Heaven's body language: named key poses (every joint, plus where it stands relative to its user, its scale,
 * alpha and how hard its clocks run) and, for every move, a key-framed timeline on the move's own beats. Poses blend
 * joint by joint with an easing per key, like an animator's in-betweens.
 */
public final class StandPose {
    public static final int OFF_R = 0, OFF_U = 1, OFF_F = 2, YAW = 3, BODY_PITCH = 4, NECK = 5, HEAD_H = 6, FL_HIP = 7, FL_KNEE = 8,
            FR_HIP = 9, FR_KNEE = 10, TORSO_PITCH = 11, TORSO_YAW = 12, TORSO_ROLL = 13, HEAD_PITCH = 14, HEAD_YAW = 15,
            RA_PITCH = 16, RA_YAW = 17, RA_ROLL = 18, R_ELBOW = 19, LA_PITCH = 20, LA_YAW = 21, LA_ROLL = 22, L_ELBOW = 23,
            SCALE = 24, ALPHA = 25, CLOCK = 26, DIAL = 27, BARRAGE = 28, GLOW = 29, REINS = 30, ROLL = 31;
    public static final int N = 32;

    private StandPose() {}

    public interface Ease {
        float apply(float x);
    }

    public static final Ease LINEAR = x -> x;
    public static final Ease IN_OUT = x -> x < 0.5F ? 4 * x * x * x : 1 - (float) Math.pow(-2 * x + 2, 3) / 2;
    public static final Ease OUT = x -> 1 - (float) Math.pow(1 - x, 3);
    public static final Ease OUT_EXPO = x -> x >= 1 ? 1 : 1 - (float) Math.pow(2, -10 * x);
    public static final Ease IN = x -> x * x * x;
    public static final Ease OUT_BACK = x -> {
        float c1 = 1.70158F, c3 = c1 + 1;
        return 1 + c3 * (float) Math.pow(x - 1, 3) + c1 * (float) Math.pow(x - 1, 2);
    };
    /** Snap: hold, then jump at the very end; anime "on ones" for strikes. */
    public static final Ease SNAP = x -> x < 0.999F ? 0.0F : 1.0F;

    public static float[] of(float... kv) {
        float[] p = IDLE.clone();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            p[(int) kv[i]] = kv[i + 1];
        }
        return p;
    }

    public static float[] with(float[] base, float... kv) {
        float[] p = base.clone();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            p[(int) kv[i]] = kv[i + 1];
        }
        return p;
    }

    public static float[] mirror(float[] p) {
        float[] m = p.clone();
        m[OFF_R] = -p[OFF_R];
        m[YAW] = -p[YAW];
        m[TORSO_YAW] = -p[TORSO_YAW];
        m[TORSO_ROLL] = -p[TORSO_ROLL];
        m[HEAD_YAW] = -p[HEAD_YAW];
        m[ROLL] = -p[ROLL];
        m[RA_PITCH] = p[LA_PITCH];
        m[RA_YAW] = p[LA_YAW];
        m[RA_ROLL] = p[LA_ROLL];
        m[R_ELBOW] = p[L_ELBOW];
        m[LA_PITCH] = p[RA_PITCH];
        m[LA_YAW] = p[RA_YAW];
        m[LA_ROLL] = p[RA_ROLL];
        m[L_ELBOW] = p[R_ELBOW];
        m[FL_HIP] = p[FR_HIP];
        m[FL_KNEE] = p[FR_KNEE];
        m[FR_HIP] = p[FL_HIP];
        m[FR_KNEE] = p[FL_KNEE];
        return m;
    }

    // ------------------------------------------------------------------------------------------------ key poses

    /** Behind his right shoulder, hovering, hands on the reins, legs folded under. */
    public static final float[] IDLE = new float[N];

    static {
        float[] p = IDLE;
        p[OFF_R] = 0.85F;
        p[OFF_U] = 0.3F;
        p[OFF_F] = -0.95F;
        p[FL_HIP] = 18;
        p[FL_KNEE] = 48;
        p[FR_HIP] = 10;
        p[FR_KNEE] = 38;
        p[TORSO_PITCH] = 4;
        p[HEAD_PITCH] = 4;
        p[RA_PITCH] = -38;
        p[RA_YAW] = 14;
        p[R_ELBOW] = 58;
        p[LA_PITCH] = -38;
        p[LA_YAW] = 14;
        p[L_ELBOW] = 58;
        p[SCALE] = 1.0F;
        p[ALPHA] = 1.0F;
        p[CLOCK] = 1.0F;
        p[DIAL] = 0.25F;
        p[GLOW] = 0.3F;
        p[REINS] = 1.0F;
    }

    public static final float[] FRONT = of(OFF_R, 0.45F, OFF_U, 0.1F, OFF_F, 0.95F, REINS, 0, GLOW, 0.45F, DIAL, 0.55F,
            FL_HIP, 30, FL_KNEE, 45, FR_HIP, 6, FR_KNEE, 30);
    public static final float[] JAB_R = with(FRONT, TORSO_YAW, -24, TORSO_PITCH, 12, RA_PITCH, -92, RA_YAW, 14, R_ELBOW, 0,
            LA_PITCH, -55, LA_YAW, 20, L_ELBOW, 95, HEAD_PITCH, 6, DIAL, 0.6F, OFF_F, 1.25F);
    public static final float[] JAB_L = mirror(JAB_R);
    public static final float[] GUARD = with(FRONT, RA_PITCH, -60, RA_YAW, 22, R_ELBOW, 100, LA_PITCH, -60, LA_YAW, 22, L_ELBOW, 100,
            TORSO_PITCH, 10);
    public static final float[] BARRAGE_P = with(FRONT, TORSO_PITCH, 16, HEAD_PITCH, 8, RA_PITCH, -88, RA_YAW, 10, R_ELBOW, 25,
            LA_PITCH, -88, LA_YAW, 10, L_ELBOW, 25, BARRAGE, 1, GLOW, 0.7F, DIAL, 0.9F, CLOCK, 5, OFF_F, 1.2F, BODY_PITCH, 6,
            FL_HIP, 45, FL_KNEE, 30, FR_HIP, 15, FR_KNEE, 60);
    public static final float[] WINDUP = with(FRONT, TORSO_YAW, 42, TORSO_PITCH, -4, RA_PITCH, -25, RA_YAW, -25, RA_ROLL, 45, R_ELBOW, 115,
            LA_PITCH, -82, LA_YAW, 18, L_ELBOW, 10, BODY_PITCH, -14, DIAL, 0.85F, CLOCK, 4, GLOW, 0.7F, OFF_F, 0.85F,
            FL_HIP, 55, FL_KNEE, 85, FR_HIP, 40, FR_KNEE, 70);
    public static final float[] FINISH = with(FRONT, TORSO_YAW, -38, TORSO_PITCH, 22, RA_PITCH, -96, RA_YAW, 18, R_ELBOW, 0,
            LA_PITCH, -25, LA_YAW, 10, L_ELBOW, 70, BODY_PITCH, 10, DIAL, 1, GLOW, 1, OFF_F, 1.6F, HEAD_PITCH, 8,
            FL_HIP, -10, FL_KNEE, 20, FR_HIP, 35, FR_KNEE, 15);
    public static final float[] THROW_BACK = with(FRONT, OFF_R, 0.55F, OFF_F, 0.35F, TORSO_YAW, 35, TORSO_PITCH, -10,
            RA_PITCH, -160, RA_YAW, -10, RA_ROLL, 28, R_ELBOW, 85, LA_PITCH, -70, LA_YAW, 25, L_ELBOW, 20, DIAL, 0.8F, GLOW, 0.6F);
    public static final float[] THROW = with(FRONT, OFF_R, 0.4F, OFF_F, 0.6F, TORSO_YAW, -32, TORSO_PITCH, 16, RA_PITCH, -78,
            RA_YAW, 28, R_ELBOW, 0, LA_PITCH, -30, LA_YAW, 10, L_ELBOW, 60, DIAL, 0.95F, GLOW, 0.7F);
    public static final float[] REAR = with(IDLE, OFF_R, 0.2F, OFF_U, 0.55F, OFF_F, -1.25F, BODY_PITCH, -36, NECK, -10, HEAD_H, -10,
            FL_HIP, 75, FL_KNEE, 85, FR_HIP, 55, FR_KNEE, 100, TORSO_PITCH, 10, HEAD_PITCH, -18, RA_PITCH, -150, RA_ROLL, 22,
            R_ELBOW, 20, LA_PITCH, -150, LA_ROLL, 22, L_ELBOW, 20, DIAL, 1, CLOCK, 12, GLOW, 1, REINS, 0, SCALE, 1.1F);
    public static final float[] CHARGE = with(FRONT, OFF_F, 1.5F, BODY_PITCH, 16, NECK, 8, TORSO_PITCH, 24, HEAD_PITCH, 4,
            RA_PITCH, -100, RA_YAW, 12, R_ELBOW, 20, LA_PITCH, -40, LA_YAW, 10, L_ELBOW, 70, FL_HIP, 60, FL_KNEE, 20,
            FR_HIP, -25, FR_KNEE, 60, DIAL, 1, CLOCK, 10, GLOW, 1);
    public static final float[] CHOP_UP = with(FRONT, OFF_R, 0.3F, OFF_F, 0.7F, RA_PITCH, -172, RA_ROLL, 8, R_ELBOW, 35,
            LA_PITCH, -40, L_ELBOW, 50, TORSO_PITCH, -12, TORSO_YAW, 12, HEAD_PITCH, -10, BODY_PITCH, -16, DIAL, 1, GLOW, 1,
            FL_HIP, 60, FL_KNEE, 80);
    public static final float[] CHOP_DOWN = with(FRONT, OFF_R, 0.3F, OFF_F, 1.0F, RA_PITCH, -38, RA_YAW, 6, R_ELBOW, 0,
            LA_PITCH, -20, L_ELBOW, 40, TORSO_PITCH, 30, TORSO_YAW, -18, HEAD_PITCH, 18, BODY_PITCH, 14, DIAL, 1, GLOW, 1);
    public static final float[] SLICE_P = with(FRONT, OFF_R, 0.5F, OFF_F, 0.6F, RA_PITCH, -88, RA_YAW, -78, R_ELBOW, 6,
            LA_PITCH, -30, L_ELBOW, 50, TORSO_YAW, -48, TORSO_PITCH, 10, HEAD_YAW, -20, DIAL, 1, GLOW, 1);
    public static final float[] SPREAD = with(IDLE, OFF_R, 0.0F, OFF_U, 1.15F, OFF_F, -1.5F, BODY_PITCH, -24, FL_HIP, 50, FL_KNEE, 95,
            FR_HIP, 40, FR_KNEE, 100, TORSO_PITCH, -10, HEAD_PITCH, -28, RA_PITCH, -40, RA_ROLL, 80, R_ELBOW, 10, LA_PITCH, -40,
            LA_ROLL, 80, L_ELBOW, 10, CLOCK, 30, DIAL, 1, GLOW, 1, REINS, 0, SCALE, 1.25F);
    public static final float[] HIDDEN = with(IDLE, ALPHA, 0.0F, SCALE, 0.6F, OFF_U, 0.0F, OFF_F, -0.4F, OFF_R, 0.3F);

    // ------------------------------------------------------------------------------------------------ timelines

    private record Key(float t, float[] pose, Ease ease) {}

    private static Key k(float t, float[] pose, Ease ease) {
        return new Key(t, pose, ease);
    }

    private static float[] play(float t, Key... keys) {
        if (t <= keys[0].t) {
            return keys[0].pose.clone();
        }
        for (int i = 1; i < keys.length; i++) {
            if (t < keys[i].t) {
                Key a = keys[i - 1], b = keys[i];
                float x = Mth.clamp((t - a.t) / Math.max(0.001F, b.t - a.t), 0.0F, 1.0F);
                return lerp(a.pose, b.pose, b.ease.apply(x));
            }
        }
        return keys[keys.length - 1].pose.clone();
    }

    public static float[] lerp(float[] a, float[] b, float x) {
        float[] o = new float[N];
        for (int i = 0; i < N; i++) {
            o[i] = a[i] + (b[i] - a[i]) * x;
        }
        return o;
    }

    /** The pose for a move {@code t} ticks in; the caller blends it back into {@link #IDLE} after {@code move.length}. */
    public static float[] during(Move move, float t, int seed) {
        return switch (move) {
            case SUMMON -> play(t, k(0, HIDDEN, LINEAR), k(5, with(REAR, SCALE, 1.15F, ALPHA, 1.0F), OUT_BACK), k(12, REAR, IN_OUT),
                    k(22, IDLE, IN_OUT));
            case PUNCH -> {
                float[] jab = (seed & 1) == 0 ? JAB_R : JAB_L;
                yield play(t, k(0, IDLE, LINEAR), k(1.5F, GUARD, OUT), k(Beats.PUNCH_HIT, jab, OUT_EXPO), k(6, jab, LINEAR), k(9, IDLE, IN_OUT));
            }
            case BARRAGE -> play(t, k(0, IDLE, LINEAR), k(Beats.BARRAGE_START, BARRAGE_P, OUT_EXPO), k(Beats.BARRAGE_END, BARRAGE_P, LINEAR),
                    k(52, IDLE, IN_OUT));
            case FINISHER -> play(t, k(0, IDLE, LINEAR), k(Beats.FINISH_HIT - 2, WINDUP, OUT), k(Beats.FINISH_HIT, FINISH, OUT_EXPO),
                    k(16, FINISH, LINEAR), k(24, IDLE, IN_OUT));
            case KNIVES -> play(t, k(0, IDLE, LINEAR), k(Beats.KNIFE_THROW - 1, THROW_BACK, OUT), k(Beats.KNIFE_THROW + 1, THROW, OUT_EXPO),
                    k(12, THROW, LINEAR), k(18, IDLE, IN_OUT));
            case SPEED_SLICE -> play(t, k(0, IDLE, LINEAR), k(10, with(REAR, SCALE, 1.0F, OFF_U, 0.3F), IN_OUT),
                    k(Beats.SLICE_VANISH - 1, with(SLICE_P, RA_YAW, 40, TORSO_YAW, 30), IN_OUT),
                    k(Beats.SLICE_VANISH, with(SLICE_P, ALPHA, 0.0F), SNAP),
                    k(Beats.SLICE_HIT - 1, with(SLICE_P, ALPHA, 0.0F), LINEAR),
                    k(Beats.SLICE_HIT, SLICE_P, SNAP), k(40, SLICE_P, LINEAR), k(58, IDLE, IN_OUT));
            case INFINITE_SPEED -> play(t, k(0, IDLE, LINEAR), k(Beats.INF_VANISH - 2, CHOP_UP, IN_OUT),
                    k(Beats.INF_VANISH, with(CHOP_UP, ALPHA, 0.0F), SNAP), k(Beats.INF_HIT - 2, with(CHOP_UP, ALPHA, 1.0F), SNAP),
                    k(Beats.INF_HIT, CHOP_DOWN, OUT_EXPO), k(32, CHOP_DOWN, LINEAR), k(48, IDLE, IN_OUT));
            case JUDGEMENT -> {
                if (t < Beats.JUDGE_ARRIVE + 1) {
                    yield play(t, k(0, IDLE, LINEAR), k(12, REAR, OUT_BACK), k(Beats.JUDGE_LUNGE, REAR, LINEAR),
                            k(Beats.JUDGE_ARRIVE, CHARGE, OUT_EXPO), k(Beats.JUDGE_ARRIVE + 1, CHARGE, LINEAR));
                }
                if (t < Beats.JUDGE_FINAL - 6) {
                    // the lightspeed flurry: alternate fists snapping in from every angle, every few ticks
                    int i = (int) ((t - Beats.JUDGE_ARRIVE) / 4.0F);
                    float local = ((t - Beats.JUDGE_ARRIVE) % 4.0F) / 4.0F;
                    float[] a = (i & 1) == 0 ? JAB_R : JAB_L;
                    float[] b = (i & 1) == 0 ? JAB_L : JAB_R;
                    float[] base = with(lerp(a, b, OUT_EXPO.apply(local)), BARRAGE, 0.6F, GLOW, 1, DIAL, 1, CLOCK, 14, OFF_F, 1.3F,
                            YAW, (i % 3 - 1) * 14.0F, ROLL, (i % 2 == 0 ? 6 : -6));
                    yield base;
                }
                yield play(t, k(Beats.JUDGE_FINAL - 6, with(WINDUP, SCALE, 1.15F, CLOCK, 20), OUT),
                        k(Beats.JUDGE_FINAL, with(FINISH, SCALE, 1.2F, CLOCK, 20, OFF_F, 1.9F), OUT_EXPO),
                        k(76, with(FINISH, SCALE, 1.1F), LINEAR), k(100, IDLE, IN_OUT));
            }
            case DOUBLE_ACCEL -> play(t, k(0, IDLE, LINEAR), k(10, with(IDLE, CLOCK, 4, DIAL, 0.6F, GLOW, 0.6F), IN_OUT),
                    k(Beats.DA_BURST - 2, with(REAR, CLOCK, 25, SCALE, 1.05F), OUT_BACK), k(Beats.DA_BURST, with(REAR, CLOCK, 40, SCALE, 1.18F), OUT_EXPO),
                    k(40, with(IDLE, CLOCK, 6, GLOW, 0.8F, DIAL, 1), IN_OUT), k(50, with(IDLE, CLOCK, 3, GLOW, 0.6F, DIAL, 0.8F), IN_OUT));
            case TIME_ACCEL -> play(t, k(0, IDLE, LINEAR), k(14, with(REAR, CLOCK, 6), OUT_BACK), k(34, with(SPREAD, CLOCK, 14), IN_OUT),
                    k(Beats.TA_START, with(SPREAD, CLOCK, 60, SCALE, 1.35F), OUT_EXPO), k(78, with(SPREAD, CLOCK, 40), LINEAR),
                    k(92, with(IDLE, CLOCK, 20, GLOW, 0.8F, DIAL, 1), IN_OUT));
            case UNIVERSE_RESET -> with(SPREAD, CLOCK, 80, SCALE, 1.3F);
        };
    }

    /** Standing poses while time is accelerated, or while Double Acceleration lasts. */
    public static float[] idle(boolean accel, boolean da) {
        if (accel) {
            return with(IDLE, CLOCK, 40, DIAL, 1, GLOW, 0.75F, OFF_U, 0.45F);
        }
        if (da) {
            return with(IDLE, CLOCK, 8, DIAL, 0.9F, GLOW, 0.6F);
        }
        return IDLE;
    }
}
