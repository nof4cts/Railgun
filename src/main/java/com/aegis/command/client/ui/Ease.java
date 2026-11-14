package com.aegis.command.client.ui;

/** Easing curves used across every transition. Input and output are 0..1. */
public final class Ease {
    private Ease() {}

    public static float clamp01(float t) { return t < 0 ? 0 : (t > 1 ? 1 : t); }

    public static float outCubic(float t) { t = clamp01(t); float f = 1 - t; return 1 - f * f * f; }

    public static float inCubic(float t) { t = clamp01(t); return t * t * t; }

    public static float inOutCubic(float t) {
        t = clamp01(t);
        return t < 0.5f ? 4 * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 3) / 2;
    }

    public static float outExpo(float t) { t = clamp01(t); return t >= 1 ? 1 : 1 - (float) Math.pow(2, -10 * t); }

    public static float outBack(float t) {
        t = clamp01(t);
        float c1 = 1.70158f, c3 = c1 + 1;
        return 1 + c3 * (float) Math.pow(t - 1, 3) + c1 * (float) Math.pow(t - 1, 2);
    }

    public static float outElastic(float t) {
        t = clamp01(t);
        if (t == 0 || t == 1) return t;
        return (float) (Math.pow(2, -10 * t) * Math.sin((t * 10 - 0.75) * (2 * Math.PI / 3)) + 1);
    }

    /** Frame-rate independent exponential approach. */
    public static float approach(float current, float target, float rate, float dt) {
        return current + (target - current) * (1 - (float) Math.exp(-rate * dt));
    }

    public static float lerp(float a, float b, float t) { return a + (b - a) * t; }
}
