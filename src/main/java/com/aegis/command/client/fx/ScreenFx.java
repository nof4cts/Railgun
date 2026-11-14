package com.aegis.command.client.fx;

import com.aegis.command.AegisConfig;
import net.minecraft.util.Mth;

/** Camera shake (trauma model) and full-screen flash, shared by gameplay and cinematics. */
public final class ScreenFx {
    private static float trauma;
    private static float flash;
    private static int flashColor = 0xFFF2D8;
    private static long lastNanos = System.nanoTime();

    private ScreenFx() {}

    public static void addTrauma(float amount) {
        trauma = Math.min(1f, trauma + amount);
    }

    public static void flash(float intensity, int rgb) {
        if (intensity > flash) {
            flash = Math.min(1f, intensity);
            flashColor = rgb;
        }
    }

    /** Called once per rendered frame. */
    public static void frame() {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastNanos) / 1e9f);
        lastNanos = now;
        trauma = Math.max(0, trauma - dt * 0.9f);
        flash = Math.max(0, flash - dt * 2.2f);
    }

    public static float shakeAmount() {
        float s = AegisConfig.CLIENT.shake.get() / 100f;
        return trauma * trauma * s;
    }

    private static float noise(float t, float seed) {
        return (float) (Math.sin(t * 1.7 + seed) * 0.5 + Math.sin(t * 3.1 + seed * 2.3) * 0.3 + Math.sin(t * 7.3 + seed * 0.7) * 0.2);
    }

    public static float shakeYaw() { float t = System.nanoTime() / 1e9f * 18f; return noise(t, 1.3f) * 6f * shakeAmount(); }
    public static float shakePitch() { float t = System.nanoTime() / 1e9f * 18f; return noise(t, 4.7f) * 4.5f * shakeAmount(); }
    public static float shakeRoll() { float t = System.nanoTime() / 1e9f * 14f; return noise(t, 9.1f) * 5f * shakeAmount(); }

    public static float flashAlpha() { return Mth.clamp(flash, 0, 1); }
    public static int flashColor() { return flashColor; }
}
