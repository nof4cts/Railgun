package com.naoya.ps.client.fx;

import net.minecraft.world.phys.Vec3;

/**
 * What the final picture should look like this frame. Rebuilt from scratch every frame from the cast clocks, so it is
 * always exactly in step with the cinematic: every contribution takes the strongest value asked for.
 */
public final class ScreenFx {
    public float shake;
    public float flash;
    public float flashR = 1, flashG = 1, flashB = 1;
    public int impactMode;
    public float impactAmount;
    public float impactSeed;
    public Vec3 impactAt;
    public float accentR = 1, accentG = 0.15F, accentB = 0.1F;
    public float punch;
    public float aberration;
    public float desaturate;
    public float vignette;
    public float vignetteR, vignetteG, vignetteB;
    public float grain;
    public float haki;
    public float bloom;
    public float contrast;

    public void reset() {
        shake = flash = punch = aberration = desaturate = vignette = grain = haki = bloom = contrast = 0.0F;
        flashR = flashG = flashB = 1.0F;
        impactMode = 0;
        impactAmount = 0.0F;
        impactSeed = 0.0F;
        impactAt = null;
        vignetteR = vignetteG = vignetteB = 0.0F;
    }

    public boolean idle() {
        return shake < 1.0E-3F && flash < 1.0E-3F && impactMode == 0 && punch < 1.0E-3F && aberration < 1.0E-3F
                && desaturate < 1.0E-3F && vignette < 1.0E-3F && grain < 1.0E-3F && haki < 1.0E-3F && bloom < 1.0E-3F
                && contrast < 1.0E-3F;
    }

    public void shake(float v) {
        shake = Math.max(shake, v);
    }

    public void flash(int rgb, float v) {
        if (v > flash) {
            flash = v;
            flashR = (rgb >> 16 & 255) / 255.0F;
            flashG = (rgb >> 8 & 255) / 255.0F;
            flashB = (rgb & 255) / 255.0F;
        }
    }

    public void vignette(int rgb, float v) {
        if (v > vignette) {
            vignette = v;
            vignetteR = (rgb >> 16 & 255) / 255.0F;
            vignetteG = (rgb >> 8 & 255) / 255.0F;
            vignetteB = (rgb & 255) / 255.0F;
        }
    }

    public void punch(float v) {
        punch = Math.max(punch, v);
    }

    public void aberration(float v) {
        aberration = Math.max(aberration, v);
    }

    public void desaturate(float v) {
        desaturate = Math.max(desaturate, v);
    }

    public void grain(float v) {
        grain = Math.max(grain, v);
    }

    public void haki(float v) {
        haki = Math.max(haki, v);
    }

    public void bloom(float v) {
        bloom = Math.max(bloom, v);
    }

    public void contrast(float v) {
        contrast = Math.max(contrast, v);
    }

    /**
     * Plays an impact sequence that began {@code since} ticks ago. Frames are held whole ticks: the step is picked by
     * the integer tick, so it changes exactly on the tick, like an animator's timing sheet.
     */
    public void impact(int[][] seq, float since, Vec3 at, int accent) {
        if (since < 0.0F) {
            return;
        }
        int tick = (int) Math.floor(since);
        int acc = 0;
        for (int i = 0; i < seq.length; i++) {
            if (tick < acc + seq[i][1]) {
                impactMode = seq[i][0];
                impactAmount = 1.0F;
                impactSeed = i * 17.31F + acc;
                impactAt = at;
                accentR = (accent >> 16 & 255) / 255.0F;
                accentG = (accent >> 8 & 255) / 255.0F;
                accentB = (accent & 255) / 255.0F;
                return;
            }
            acc += seq[i][1];
        }
    }

    /** 0 → 1 → 0 over [in0, in1] … [out0, out1]. */
    public static float window(float t, float in0, float in1, float out0, float out1) {
        float a = Math.max(0.0F, Math.min(1.0F, (t - in0) / Math.max(in1 - in0, 0.001F)));
        float b = 1.0F - Math.max(0.0F, Math.min(1.0F, (t - out0) / Math.max(out1 - out0, 0.001F)));
        return Math.min(a, b);
    }

    /** A spike at {@code at} that decays with time constant {@code tau} ticks. */
    public static float hit(float t, float at, float tau) {
        return t < at ? 0.0F : (float) Math.exp(-(t - at) / tau);
    }

    public static float smooth(float x) {
        x = Math.max(0.0F, Math.min(1.0F, x));
        return x * x * (3.0F - 2.0F * x);
    }
}
