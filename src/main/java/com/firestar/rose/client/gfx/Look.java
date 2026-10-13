package com.firestar.rose.client.gfx;

import com.firestar.rose.RoseConfig;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * What a frame does to the picture, gathered from every technique playing: the lens (shake, punch-in, roll), the grade
 * (flash, fringing, zoom blur, rose tint, the rose-black sky), impact frames, speed lines, a screen torn along a cut,
 * letterbox bars.
 */
public final class Look {
    /** Impact frame styles, matching grade.fsh. */
    public static final int NEGATIVE = 1, INK = 2, VOID = 3, BLOWOUT = 4, TORN = 5;

    public float shake, punch, roll;
    public float flashR = 1, flashG = 1, flashB = 1, flash;
    public float chroma;
    public float zoomX = 0.5F, zoomY = 0.5F, zoom;
    public float vigR, vigG, vigB, vignette;
    public float bars;
    public float grain;
    public float desat;
    public float tint;
    public float sky, skyDim;
    public int impactMode;
    public float impact, impactX = 0.5F, impactY = 0.5F;
    public float accR = 1.0F, accG = 0.3F, accB = 0.62F;
    public float splitNX = 1, splitNY, splitOff, split;
    public float lines, linesX = 0.5F, linesY = 0.5F;

    void reset() {
        shake = punch = roll = flash = chroma = zoom = vignette = bars = grain = desat = tint = sky = skyDim = impact = split = lines = 0;
        impactMode = 0;
    }

    public boolean idle() {
        return flash < 0.002F && chroma < 0.002F && zoom < 0.002F && vignette < 0.002F && bars < 0.002F && grain < 0.002F && desat < 0.002F
                && tint < 0.002F && sky < 0.002F && skyDim < 0.002F && impact < 0.002F && split < 0.002F && lines < 0.002F;
    }

    public void shake(float v) {
        shake = Math.max(shake, v);
    }

    public void punch(float v) {
        punch = Math.max(punch, v);
    }

    public void flash(int rgb, float v) {
        v *= RoseConfig.CLIENT.flash.get().floatValue();
        if (v > flash) {
            flash = v;
            flashR = (rgb >> 16 & 255) / 255F;
            flashG = (rgb >> 8 & 255) / 255F;
            flashB = (rgb & 255) / 255F;
        }
    }

    public void chroma(float v) {
        chroma = Math.max(chroma, v);
    }

    public void zoom(float[] at, float v) {
        if (v > zoom && at != null) {
            zoom = v;
            zoomX = at[0];
            zoomY = at[1];
        }
    }

    public void vignette(int rgb, float v) {
        if (v > vignette) {
            vignette = v;
            vigR = (rgb >> 16 & 255) / 255F;
            vigG = (rgb >> 8 & 255) / 255F;
            vigB = (rgb & 255) / 255F;
        }
    }

    public void bars(float v) {
        bars = Math.max(bars, v);
    }

    public void grain(float v) {
        grain = Math.max(grain, v);
    }

    public void desat(float v) {
        desat = Math.max(desat, v);
    }

    public void tint(float v) {
        tint = Math.max(tint, v);
    }

    public void sky(float v, float dim) {
        sky = Math.max(sky, v);
        skyDim = Math.max(skyDim, dim);
    }

    public void lines(float[] at, float v) {
        if (v > lines && at != null) {
            lines = v;
            linesX = at[0];
            linesY = at[1];
        }
    }

    /** Tear the screen along a line through the centre with normal (nx, ny), sliding the halves apart by {@code v}. */
    public void split(float nx, float ny, float off, float v) {
        if (v > split) {
            split = v;
            splitNX = nx;
            splitNY = ny;
            splitOff = off;
        }
    }

    /**
     * Play an impact frame sequence: each entry a style held for one tick (anime "on ones"), {@code since} ticks after
     * the blow, centred on where it landed on the screen.
     */
    public void impact(int[] seq, float since, float[] at, int accent) {
        if (!RoseConfig.CLIENT.impactFrames.get() || since < 0 || seq.length == 0 || since >= seq.length) {
            return;
        }
        int mode = seq[Mth.clamp((int) Math.floor(since), 0, seq.length - 1)];
        if (mode == 0) {
            return;
        }
        impactMode = mode;
        impact = 1.0F;
        if (at != null) {
            impactX = at[0];
            impactY = at[1];
        }
        accR = (accent >> 16 & 255) / 255F;
        accG = (accent >> 8 & 255) / 255F;
        accB = (accent & 255) / 255F;
    }

    // ------------------------------------------------------------------------------------------------ envelopes

    /** 0 before in0, rising to 1 by in1, held, falling to 0 between out0 and out1 (smooth). */
    public static float window(float t, float in0, float in1, float out0, float out1) {
        if (t <= in0 || t >= out1) {
            return 0;
        }
        float a = t < in1 ? smooth((t - in0) / Math.max(0.001F, in1 - in0)) : 1;
        float b = t > out0 ? 1 - smooth((t - out0) / Math.max(0.001F, out1 - out0)) : 1;
        return a * b;
    }

    /** A blow at {@code at}: 1 at the moment, decaying with time constant tau (ticks). */
    public static float hit(float t, float at, float tau) {
        return t < at ? 0 : (float) Math.exp(-(t - at) / tau);
    }

    public static float smooth(float x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }

    public static Vec3 none() {
        return Vec3.ZERO;
    }
}
