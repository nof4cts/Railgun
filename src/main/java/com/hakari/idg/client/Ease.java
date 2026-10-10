package com.hakari.idg.client;

import net.minecraft.util.Mth;

public enum Ease {
    LINEAR, IN_OUT, OUT_EXPO, IN_EXPO, OUT_CUBIC, OUT_BACK;

    public float apply(float t) {
        t = Mth.clamp(t, 0f, 1f);
        return switch (this) {
            case LINEAR -> t;
            case IN_OUT -> t * t * t * (t * (t * 6 - 15) + 10);
            case OUT_EXPO -> t >= 1f ? 1f : 1f - (float) Math.pow(2, -10 * t);
            case IN_EXPO -> t <= 0f ? 0f : (float) Math.pow(2, 10 * t - 10);
            case OUT_CUBIC -> 1f - (1f - t) * (1f - t) * (1f - t);
            case OUT_BACK -> {
                float c1 = 1.70158f, c3 = c1 + 1f;
                yield 1f + c3 * (float) Math.pow(t - 1, 3) + c1 * (float) Math.pow(t - 1, 2);
            }
        };
    }

    public static float outExpo(float t) { return OUT_EXPO.apply(t); }
    public static float inExpo(float t) { return IN_EXPO.apply(t); }
    public static float inOut(float t) { return IN_OUT.apply(t); }
    public static float outBack(float t) { return OUT_BACK.apply(t); }
    public static float outCubic(float t) { return OUT_CUBIC.apply(t); }
}
